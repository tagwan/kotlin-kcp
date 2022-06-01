package io.netty.rnet.pipeline

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.ScheduledFuture
import io.netty.rnet.packet.Disconnect
import java.util.concurrent.TimeUnit

@Sharable
class DisconnectHandler : ChannelDuplexHandler() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is Disconnect) {
            ReferenceCountUtil.release(msg)
            ctx.pipeline().remove(this)
            ctx.channel().flush().close() //send ACKs
        } else {
            ctx.fireChannelRead(msg)
        }
    }

    override fun close(ctx: ChannelHandlerContext, promise: ChannelPromise) {
        if (ctx.channel().isActive) {
            val disconnectPromise = ctx.newPromise()
            val timeout: ScheduledFuture<*> = ctx.channel().eventLoop().schedule<Boolean>(
                { disconnectPromise.trySuccess() }, 1, TimeUnit.SECONDS
            ) //TODO: config
            ctx.channel().writeAndFlush(Disconnect())
                .addListener { f: Future<in Void?>? -> disconnectPromise.trySuccess() }
            disconnectPromise.addListener { f: Future<in Void?>? ->
                timeout.cancel(false)
                ctx.close(promise)
            }
        } else {
            ctx.close(promise)
        }
    }

    companion object {
        const val NAME = "rn-disconnect"
        val INSTANCE = DisconnectHandler()
    }
}