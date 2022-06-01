package io.netty.rnet.pipeline

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.util.ReferenceCountUtil
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import io.netty.rnet.frame.Frame
import io.netty.rnet.packet.FramedPacket
import io.netty.rnet.utils.UINT.B3.minusWrap
import io.netty.rnet.utils.UINT.B3.plus
import io.netty.rnet.utils.checkPacketLoss
import java.util.*
import java.util.function.Consumer

class FrameOrderIn : MessageToMessageDecoder<Frame>() {
    protected val channels = arrayOfNulls<OrderedChannelPacketQueue>(8)

    init {
        for (i in channels.indices) {
            channels[i] = OrderedChannelPacketQueue()
        }
    }

    @Throws(Exception::class)
    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        super.handlerRemoved(ctx)
        Arrays.stream(channels).forEach { obj -> obj?.clear() }
    }

    override fun decode(ctx: ChannelHandlerContext, frame: Frame, list: MutableList<Any>) {
        if (frame.reliability.isSequenced) {
            frame.touch("Sequenced")
            channels[frame.orderChannel]!!.decodeSequenced(frame, list)
        } else if (frame.reliability.isOrdered) {
            frame.touch("Ordered")
            channels[frame.orderChannel]!!.decodeOrdered(frame, list)
        } else {
            frame.touch("No order")
            list.add(frame.retainedFrameData())
        }
    }

    protected class OrderedChannelPacketQueue {
        protected val queue = Int2ObjectOpenHashMap<FramedPacket>()
        protected var lastOrderIndex = -1
        protected var lastSequenceIndex = -1
        fun decodeSequenced(frame: Frame, list: MutableList<Any>) {
            if (minusWrap(frame.sequenceIndex, lastSequenceIndex) > 0) {
                lastSequenceIndex = frame.sequenceIndex
                //remove earlier packets from queue
                while (minusWrap(frame.orderIndex, lastOrderIndex) > 1) {
                    ReferenceCountUtil.release(queue.remove(lastOrderIndex))
                    lastOrderIndex = plus(lastOrderIndex, 1)
                }
            }
            decodeOrdered(frame, list) //register packet as normal
        }

        fun decodeOrdered(frame: Frame, list: MutableList<Any>) {
            val indexDiff = minusWrap(frame.orderIndex, lastOrderIndex)
            checkPacketLoss(indexDiff) {"ordered difference"}
            if (indexDiff == 1) { //got next packet in line
                var data: FramedPacket = frame.retainedFrameData()
                do { //process this packet, and any queued packets following in sequence
                    list.add(data)
                    lastOrderIndex = plus(lastOrderIndex, 1)
                    data = queue.remove(plus(lastOrderIndex, 1))
                } while (data != null)
            } else if (indexDiff > 1 && !queue.containsKey(frame.orderIndex)) {
                // only new future data goes in the queue
                queue.put(frame.orderIndex, frame.retainedFrameData())
            }
            checkPacketLoss(queue.size) {"missed ordered packets"}
        }

        fun clear() {
            queue.values.forEach(Consumer { msg: FramedPacket? ->
                ReferenceCountUtil.release(
                    msg
                )
            })
            queue.clear()
        }
    }

    companion object {
        const val NAME = "rn-order-in"
    }
}