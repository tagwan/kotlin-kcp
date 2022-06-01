package io.netty.rnet.pipeline

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.CompositeByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.handler.codec.TooLongFrameException
import io.netty.util.ReferenceCountUtil
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import io.netty.rnet.RakNet.config
import io.netty.rnet.frame.Frame
import io.netty.rnet.packet.ReliabilityType
import io.netty.rnet.utils.Constants.packetLossCheck
import java.util.function.Consumer

class FrameJoiner : MessageToMessageDecoder<Frame>() {
    protected val pendingPackets = Int2ObjectOpenHashMap<Builder>()

    @Throws(Exception::class)
    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        super.handlerRemoved(ctx)
        pendingPackets.values.forEach(Consumer { obj: Builder -> obj.release() })
        pendingPackets.clear()
    }

    override fun decode(ctx: ChannelHandlerContext, frame: Frame, list: MutableList<Any>) {
        if (!frame.hasSplit()) {
            frame.touch("Not split")
            list.add(frame.retain())
        } else {
            val splitID = frame.splitId
            val partial = pendingPackets[splitID]
            val totalSize = frame.splitCount * frame.roughPacketSize
            frame.touch("Is split")
            if (totalSize > config(ctx).maxQueuedBytes) {
                throw TooLongFrameException("Fragmented frame too large")
            } else if (partial == null) {
                packetLossCheck(frame.splitCount, "frame join elements")
                pendingPackets.put(splitID, Builder.create(ctx.alloc(), frame))
            } else {
                partial.add(frame)
                if (partial.isDone) {
                    pendingPackets.remove(splitID)
                    list.add(partial.finish())
                }
            }
            packetLossCheck(pendingPackets.size, "pending frame joins")
        }
    }

    protected class Builder private constructor(size: Int) {
        protected val queue: Int2ObjectOpenHashMap<ByteBuf?>
        protected var samplePacket: Frame? = null
        protected var data: CompositeByteBuf? = null
        protected var splitIdx = 0
        protected var orderId = 0
        protected var reliability: ReliabilityType? = null

        init {
            queue = Int2ObjectOpenHashMap(size)
        }

        fun init(alloc: ByteBufAllocator, packet: Frame) {
            assert(data == null)
            splitIdx = 0
            data = alloc.compositeDirectBuffer(packet.splitCount)
            orderId = packet.orderChannel
            reliability = packet.reliability
            samplePacket = packet.retain()
            add(packet)
        }

        fun add(packet: Frame) {
            assert(packet.reliability == samplePacket!!.reliability)
            assert(packet.orderChannel == samplePacket!!.orderChannel)
            assert(packet.orderIndex == samplePacket!!.orderIndex)
            if (!queue.containsKey(packet.splitIndex) && packet.splitIndex >= splitIdx) {
                queue.put(packet.splitIndex, packet.retainedFragmentData())
                update()
            }
            packetLossCheck(queue.size, "packet defragment queue")
        }

        fun update() {
            var fragment: ByteBuf?
            while (queue.remove(splitIdx).also { fragment = it } != null) {
                data!!.addComponent(true, fragment)
                splitIdx++
            }
        }

        fun finish(): Frame {
            assert(isDone)
            assert(queue.isEmpty())
            return try {
                samplePacket!!.completeFragment(data!!)
            } finally {
                release()
            }
        }

        val isDone: Boolean
            get() {
                assert(samplePacket!!.splitCount >= splitIdx)
                return samplePacket!!.splitCount == splitIdx
            }

        fun release() {
            if (data != null) {
                data!!.release()
                data = null
            }
            if (samplePacket != null) {
                samplePacket!!.release()
                samplePacket = null
            }
            queue.values.forEach(Consumer { msg: ByteBuf? -> ReferenceCountUtil.release(msg) })
            queue.clear()
        }

        companion object {
            fun create(alloc: ByteBufAllocator, frame: Frame): Builder {
                val out = Builder(frame.splitCount)
                out.init(alloc, frame)
                return out
            }
        }
    }

    companion object {
        const val NAME = "rn-join"
    }
}