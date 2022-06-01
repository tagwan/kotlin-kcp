package io.netty.rnet.pipeline

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.rnet.RakNet.config
import io.netty.rnet.packet.Pong

@Sharable
class PongHandler : SimpleChannelInboundHandler<Pong>() {
    override fun channelRead0(ctx: ChannelHandlerContext, pong: Pong) {
        val reliability = requireNotNull(pong.reliability)
        if (!reliability.isReliable) {
            val config = config(ctx)
            config.updateRTTNanos(pong.rTT)
        }
    }

    companion object {
        const val NAME = "rn-pong"
        val INSTANCE = PongHandler()
    }
}