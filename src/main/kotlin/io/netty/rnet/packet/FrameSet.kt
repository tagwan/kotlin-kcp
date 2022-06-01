package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.handler.codec.CorruptedFrameException
import io.netty.util.*
import io.netty.rnet.config.CodecType
import io.netty.rnet.config.DefaultCodec
import io.netty.rnet.frame.Frame
import java.util.function.Consumer

class FrameSet private constructor(
    protected val handle: Recycler.Handle<FrameSet>
) : AbstractReferenceCounted(), Packet {

    protected val frames = ArrayList<Frame>(8)
    var seqId = 0
    var sentTime: Long = 0
    protected var tracker: ResourceLeakTracker<FrameSet>? = null

    init {
        setRefCnt(0)
    }

    override fun sizeHint(): Int {
        return roughSize
    }

    override fun retain(): FrameSet {
        super.retain()
        return this
    }

    override fun deallocate() {
        frames.forEach(Consumer { obj: Frame -> obj.release() })
        frames.clear()
        if (tracker != null) {
            tracker!!.close(this)
            tracker = null
        }
        handle.recycle(this)
    }

    fun produce(alloc: ByteBufAllocator): ByteBuf {
        val header = alloc.ioBuffer(HEADER_SIZE, HEADER_SIZE)
        val out = alloc.compositeDirectBuffer(1 + frames.size * 2)
        return try {
            writeHeader(header)
            out.addComponent(true, header.retain())
            frames.forEach(Consumer { frame: Frame ->
                frame.produce(
                    alloc,
                    out
                )
            })
            out.retain()
        } finally {
            header.release()
            out.release()
        }
    }

    fun write(out: ByteBuf) {
        writeHeader(out)
        frames.forEach(Consumer { frame: Frame ->
            frame.write(
                out
            )
        })
    }

    protected fun writeHeader(out: ByteBuf) {
        out.writeByte(CodecType.FRAME_DATA_START.v) //TODO: erm... ?
        out.writeMediumLE(seqId)
    }

    fun succeed() {
        frames.forEach(Consumer { frame: Frame ->
            val promise = frame.promise
            if (promise != null) {
                promise.trySuccess()
                frame.promise = null
            }
        })
    }

    fun fail(e: Throwable?) {
        frames.forEach(Consumer { frame: Frame ->
            val promise = frame.promise
            if (promise != null) {
                promise.tryFailure(e)
                frame.promise = null
            }
        })
    }

    override fun touch(hint: Any): ReferenceCounted {
        if (tracker != null) {
            tracker!!.record(hint)
        }
        frames.forEach(Consumer { packet: Frame ->
            packet.touch(
                hint
            )
        })
        return this
    }

    val numPackets: Int
        get() = frames.size

    fun addPacket(packet: Frame) {
        frames.add(packet)
    }

    fun createFrames(consumer: Consumer<Frame?>) {
        frames.forEach(Consumer { frame: Frame ->
            consumer.accept(
                frame.retain()
            )
        })
    }

    val roughSize: Int
        get() {
            var out = HEADER_SIZE
            for (packet in frames) {
                out += packet.roughPacketSize
            }
            return out
        }
    val isEmpty: Boolean
        get() = frames.isEmpty()

    override fun toString(): String {
        return String.format("FramedData(frames: %s, seq: %s)", frames.size, seqId)
    }

    companion object {
        const val HEADER_SIZE = 4
        private val leakDetector = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(
            FrameSet::class.java
        )
        private val recycler: Recycler<FrameSet> = object : Recycler<FrameSet>() {
            override fun newObject(handle: Handle<FrameSet>): FrameSet {
                return FrameSet(handle)
            }
        }

        fun create(): FrameSet {
            val out = recycler.get()
            assert(out.refCnt() == 0)
            assert(out.tracker == null)
            out.sentTime = System.nanoTime()
            out.seqId = 0
            out.tracker = leakDetector.track(out)
            out.setRefCnt(1)
            return out
        }

        fun read(buf: ByteBuf): FrameSet {
            val out = create()
            return try {
                buf.skipBytes(1)
                out.seqId = buf.readUnsignedMediumLE()
                while (buf.isReadable) {
                    out.frames.add(Frame.read(buf))
                }
                out.retain()
            } catch (e: IndexOutOfBoundsException) {
                throw CorruptedFrameException("Failed to parse Frame", e)
            } finally {
                out.release()
            }
        }
    }
}