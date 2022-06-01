package io.netty.rnet.pipeline

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.util.concurrent.ScheduledFuture
import io.netty.rnet.packet.Ping
import java.util.concurrent.TimeUnit

class PingProducer : ChannelHandler {
    var pingTask: ScheduledFuture<*>? = null
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        pingTask = ctx.channel().eventLoop().scheduleAtFixedRate(
            { ctx.writeAndFlush(Ping()) },
            0, 200, TimeUnit.MILLISECONDS
        )
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        if (pingTask != null) {
            pingTask!!.cancel(false)
            pingTask = null
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.fireExceptionCaught(cause)
    }

    companion object {
        const val NAME = "rn-ping-producer"
    }
}