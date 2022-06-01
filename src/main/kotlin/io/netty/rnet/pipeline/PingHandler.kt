package io.netty.rnet.pipeline

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.rnet.packet.Ping
import io.netty.rnet.packet.Pong

@Sharable
class PingHandler : SimpleChannelInboundHandler<Ping>() {
    override fun channelRead0(ctx: ChannelHandlerContext, ping: Ping) {
        ctx.write(Pong(ping.timestamp, System.nanoTime(), ping.reliability))
    }

    companion object {
        const val NAME = "rn-ping"
        val INSTANCE = PingHandler()
    }
}