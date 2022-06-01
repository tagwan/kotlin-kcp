package io.netty.rnet.frame

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.util.*
import io.netty.rnet.packet.FramedPacket
import io.netty.rnet.packet.ReliabilityType

class FrameData private constructor(
    private val handle: Recycler.Handle<FrameData>
) : AbstractReferenceCounted(), FramedPacket {

    private var tracker: ResourceLeakTracker<FrameData>? = null
    override var orderChannel = 0
    var isFragment = false
    private var data: ByteBuf? = null
    override var reliability: ReliabilityType? = null

    init {
        setRefCnt(0)
    }

    fun write(out: ByteBuf) {
        val d = requireNotNull(data)
        out.writeBytes(data, d.readerIndex(), d.readableBytes())
    }

    fun createData(): ByteBuf? {
        return data?.retainedDuplicate()
    }

    override fun retain(): FrameData {
        return super.retain() as FrameData
    }

    override fun deallocate() {
        if (data != null) {
            data?.release()
            data = null
        }
        if (tracker != null) {
            tracker?.close(this)
            tracker = null
        }
        handle.recycle(this)
    }

    override fun toString(): String {
        return java.lang.String.format(
            "PacketData(%s, length: %s, framed: %s, packetId: %s)",
            reliability, dataSize, isFragment,
            if (isFragment) "n/a" else String.format("%02x", packetId)
        )
    }

    override fun touch(hint: Any): ReferenceCounted {
        if (tracker != null) {
            tracker!!.record(hint)
        }
        data!!.touch(hint)
        return this
    }

    val packetId: Int
        get() {
            assert(!isFragment)
            return data!!.getUnsignedByte(data!!.readerIndex()).toInt()
        }
    val dataSize: Int
        get() = data!!.readableBytes()

    companion object {
        private val leakDetector = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(
            FrameData::class.java
        )
        private val recycler: Recycler<FrameData> = object : Recycler<FrameData>() {
            override fun newObject(handle: Handle<FrameData>): FrameData {
                return FrameData(handle)
            }
        }

        private fun createRaw(): FrameData {
            val out = recycler.get()
            assert(out.refCnt() == 0 && out.tracker == null) { "bad reuse" }
            out.orderChannel = 0
            out.isFragment = false
            out.data = null
            out.reliability = ReliabilityType.RELIABLE_ORDERED
            out.setRefCnt(1)
            out.tracker = leakDetector.track(out)
            return out
        }

        fun create(alloc: ByteBufAllocator, packetId: Int, buf: ByteBuf): FrameData {
            val out = alloc.compositeDirectBuffer(2)
            return try {
                out.addComponent(true, alloc.ioBuffer(1, 1).writeByte(packetId))
                out.addComponent(true, buf.retain())
                read(out, out.readableBytes(), false)
            } finally {
                out.release()
            }
        }

        fun read(buf: ByteBuf, length: Int, fragment: Boolean): FrameData {
            assert(length > 0)
            val packet = createRaw()
            return try {
                packet.data = buf.readRetainedSlice(length)
                packet.isFragment = fragment
                assert(packet.dataSize == length)
                packet.retain()
            } finally {
                packet.release()
            }
        }
    }
}