package io.netty.rnet.pipeline

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageEncoder
import io.netty.rnet.RakNet.config
import io.netty.rnet.frame.Frame
import io.netty.rnet.packet.FrameSet
import io.netty.rnet.utils.UINT

class FrameSplitter : MessageToMessageEncoder<Frame>() {

    protected var nextSplitId = 0
        protected get() {
            val splitId = field
            this.nextSplitId = UINT.B2.plus(field, 1)
            return splitId
        }
    protected var nextReliableId: Int = 0
        protected get() {
            val reliableIndex = field
            nextReliableId = UINT.B3.plus(field, 1)
            return reliableIndex
        }

    override fun encode(ctx: ChannelHandlerContext, packet: Frame, list: MutableList<Any?>) {
        val config = config(ctx)
        val maxSize = config.mTU - 2 * (FrameSet.HEADER_SIZE + Frame.HEADER_SIZE)
        if (packet.roughPacketSize > maxSize) {
            val splits = packet.fragment(nextSplitId, maxSize, nextReliableId, list)
            nextReliableId = UINT.B3.plus(nextReliableId, splits)
        } else {
            if (packet.reliability.isReliable) {
                packet.setReliableIndex(nextReliableId)
            }
            list.add(packet.retain())
        }
    }

    companion object {
        const val NAME = "rn-split"
    }
}