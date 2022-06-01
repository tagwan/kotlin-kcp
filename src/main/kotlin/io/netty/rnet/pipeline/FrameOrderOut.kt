package io.netty.rnet.pipeline

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageEncoder
import io.netty.rnet.RakNet.config
import io.netty.rnet.frame.Frame
import io.netty.rnet.packet.FramedPacket
import io.netty.rnet.utils.UINT.B3.plus

class FrameOrderOut : MessageToMessageEncoder<FramedPacket?>() {
    protected var nextOrderIndex = IntArray(8)
    protected var nextSequenceIndex = IntArray(8)
    override fun encode(ctx: ChannelHandlerContext, packet: FramedPacket?, list: MutableList<Any>) {
        val config = config(ctx)
        val data = config.codec.encode(packet, ctx.alloc())
        try {
            if (data!!.reliability!!.isOrdered) {
                val channel = data.orderChannel
                val sequenceIndex = if (data.reliability!!.isSequenced) getNextSequenceIndex(channel) else 0
                list.add(Frame.createOrdered(data, getNextOrderIndex(channel), sequenceIndex))
            } else {
                list.add(Frame.create(data))
            }
        } finally {
            data!!.release()
        }
    }

    protected fun getNextOrderIndex(channel: Int): Int {
        val orderIndex = nextOrderIndex[channel]
        nextOrderIndex[channel] = plus(nextOrderIndex[channel], 1)
        return orderIndex
    }

    protected fun getNextSequenceIndex(channel: Int): Int {
        val sequenceIndex = nextSequenceIndex[channel]
        nextSequenceIndex[channel] = plus(nextSequenceIndex[channel], 1)
        return sequenceIndex
    }

    companion object {
        const val NAME = "rn-order-out"
    }
}