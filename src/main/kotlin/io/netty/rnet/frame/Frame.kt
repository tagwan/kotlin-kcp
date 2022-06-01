package io.netty.rnet.frame

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.CompositeByteBuf
import io.netty.channel.ChannelPromise
import io.netty.util.*
import io.netty.rnet.packet.ReliabilityType
import io.netty.rnet.utils.UINT.B3.minusWrap
import io.netty.rnet.utils.UINT.B3.plus
import kotlin.math.min

class Frame private constructor(
    private val handle: Recycler.Handle<Frame>
) : AbstractReferenceCounted() {

    private var hasSplit = false
    private var reliableIndex = 0

    var sequenceIndex = 0
        private set
    var orderIndex = 0
        private set
    var splitCount = 0
        private set
    var splitId = 0
        private set
    var splitIndex = 0
        private set

    private var frameData: FrameData? = null
    private var tracker: ResourceLeakTracker<Frame>? = null
    var promise: ChannelPromise? = null

    init {
        setRefCnt(0)
    }

    override fun touch(hint: Any): ReferenceCounted {
        tracker?.record(hint)
        frameData?.touch(hint)
        return this
    }

    fun completeFragment(fullData: ByteBuf): Frame {
        assert(frameData!!.isFragment)
        val out = createRaw()
        out.reliableIndex = reliableIndex
        out.sequenceIndex = sequenceIndex
        out.orderIndex = orderIndex
        out.frameData = FrameData.read(fullData, fullData.readableBytes(), false)
        out.frameData?.orderChannel = orderChannel
        out.frameData?.reliability = reliability
        return out
    }

    fun fragment(splitID: Int, splitSize: Int, reliableIndex: Int, outList: MutableList<Any?>): Int {
        var reliableIndex_ = reliableIndex
        val data = requireNotNull(frameData?.createData())
        return try {
            val dataSplitSize = splitSize - HEADER_SIZE
            val splitCountTotal = (data.readableBytes() + dataSplitSize - 1) / dataSplitSize //round up
            for (splitIndexIterator in 0 until splitCountTotal) {
                val length = min(dataSplitSize, data.readableBytes())
                val out = createRaw()
                out.reliableIndex = reliableIndex_
                out.sequenceIndex = sequenceIndex
                out.orderIndex = orderIndex
                out.splitCount = splitCountTotal
                out.splitId = splitID
                out.splitIndex = splitIndexIterator
                out.hasSplit = true
                out.frameData = FrameData.read(data, length, true)
                out.frameData!!.orderChannel = orderChannel
                out.frameData!!.reliability = reliability?.makeReliable() //reliable form only
                assert(out.frameData!!.isFragment)
                check(out.roughPacketSize <= splitSize) { "mtu fragment mismatch" }
                reliableIndex_ = plus(reliableIndex, 1)
                outList.add(out)
            }
            assert(!data.isReadable)
            splitCountTotal
        } finally {
            data!!.release()
        }
    }

    fun retainedFragmentData(): ByteBuf? {
        assert(frameData!!.isFragment)
        return frameData!!.createData()
    }

    override fun retain(): Frame {
        return super.retain() as Frame
    }

    override fun deallocate() {
        if (frameData != null) {
            frameData!!.release()
            frameData = null
        }
        if (tracker != null) {
            tracker!!.close(this)
            tracker = null
        }
        promise = null
        handle.recycle(this)
    }

    fun retainedFrameData(): FrameData {
        return frameData!!.retain()
    }

    fun produce(alloc: ByteBufAllocator, out: CompositeByteBuf) {
        val header = alloc.ioBuffer(HEADER_SIZE, HEADER_SIZE)
        try {
            writeHeader(header)
            out.addComponent(true, header.retain())
            out.addComponent(true, frameData!!.createData())
        } finally {
            header.release()
        }
    }

    fun write(out: ByteBuf) {
        writeHeader(out)
        frameData?.write(out)
    }

    protected fun writeHeader(out: ByteBuf) {
        val dataSize = requireNotNull(frameData?.dataSize)
        out.writeByte(reliability.code() shl 5 or if (hasSplit) SPLIT_FLAG else 0)
        out.writeShort(dataSize * java.lang.Byte.SIZE)
        assert(!(hasSplit && !reliability.isReliable))
        if (reliability.isReliable) {
            out.writeMediumLE(reliableIndex)
        }
        if (reliability.isSequenced) {
            out.writeMediumLE(sequenceIndex)
        }
        if (reliability.isOrdered) {
            out.writeMediumLE(orderIndex)
            out.writeByte(orderChannel)
        }
        if (hasSplit) {
            out.writeInt(splitCount)
            out.writeShort(splitId)
            out.writeInt(splitIndex)
        }
    }

    val reliability: ReliabilityType
        get() = requireNotNull(frameData?.reliability)

    val orderChannel: Int
        get() = requireNotNull(frameData?.orderChannel)

    fun hasSplit(): Boolean {
        return hasSplit
    }

    val dataSize: Int
        get() = frameData!!.dataSize
    val roughPacketSize: Int
        get() = dataSize + HEADER_SIZE

    fun setReliableIndex(reliableIndex: Int) {
        this.reliableIndex = reliableIndex
    }

    class FrameComparator : Comparator<Frame> {
        override fun compare(a: Frame, b: Frame): Int {
            if (a == b) {
                return 0
            } else if (!a.reliability.isReliable) {
                return -1
            } else if (!b.reliability.isReliable) {
                return 1
            }
            return if (minusWrap(a.reliableIndex, b.reliableIndex) < 0) -1 else 1
        }
    }

    companion object {
        val COMPARATOR = FrameComparator()
        const val HEADER_SIZE = 24
        protected const val SPLIT_FLAG = 0x10
        private val leakDetector = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(
            Frame::class.java
        )
        private val recycler: Recycler<Frame> = object : Recycler<Frame>() {
            override fun newObject(handle: Handle<Frame>): Frame {
                return Frame(handle)
            }
        }

        fun read(buf: ByteBuf): Frame {
            val out = createRaw()
            return try {
                val flags = buf.readUnsignedByte().toInt()
                val bitLength = buf.readUnsignedShort()
                val length = (bitLength + java.lang.Byte.SIZE - 1) / java.lang.Byte.SIZE //round up
                val hasSplit = flags and SPLIT_FLAG != 0
                val reliability: ReliabilityType = ReliabilityType[flags shr 5]
                var orderChannel = 0
                if (reliability.isReliable) {
                    out.reliableIndex = buf.readUnsignedMediumLE()
                }
                if (reliability.isSequenced) {
                    out.sequenceIndex = buf.readUnsignedMediumLE()
                }
                if (reliability.isOrdered) {
                    out.orderIndex = buf.readUnsignedMediumLE()
                    orderChannel = buf.readUnsignedByte().toInt()
                }
                if (hasSplit) {
                    out.splitCount = buf.readInt()
                    out.splitId = buf.readUnsignedShort()
                    out.splitIndex = buf.readInt()
                    out.hasSplit = true
                }
                out.frameData = FrameData.read(buf, length, hasSplit)
                out.frameData!!.reliability = reliability
                out.frameData!!.orderChannel = orderChannel
                out.retain()
            } finally {
                out.release()
            }
        }

        fun create(packet: FrameData): Frame {
            require(!packet.reliability!!.isOrdered) { "Must provided indices for ordered data." }
            val out = createRaw()
            out.frameData = packet.retain()
            return out
        }

        fun createOrdered(packet: FrameData, orderIndex: Int, sequenceIndex: Int): Frame {
            require(packet.reliability!!.isOrdered) { "No indices needed for non-ordered data." }
            val out = createRaw()
            out.frameData = packet.retain()
            out.orderIndex = orderIndex
            out.sequenceIndex = sequenceIndex
            return out
        }

        private fun createRaw(): Frame {
            val out = recycler.get()
            assert(out.refCnt() == 0)
            assert(out.tracker == null)
            assert(out.frameData == null)
            assert(out.promise == null)
            out.hasSplit = false
            out.splitIndex = 0
            out.splitId = out.splitIndex
            out.splitCount = out.splitId
            out.orderIndex = out.splitCount
            out.sequenceIndex = out.orderIndex
            out.reliableIndex = out.sequenceIndex
            out.setRefCnt(1)
            out.tracker = leakDetector.track(out)
            return out
        }
    }
}