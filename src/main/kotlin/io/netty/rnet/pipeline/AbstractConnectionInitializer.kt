package io.netty.rnet.pipeline

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.ConnectTimeoutException
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.ScheduledFuture
import io.netty.rnet.RakNet.config
import io.netty.rnet.packet.ConnectionFailed
import io.netty.rnet.packet.Packet
import java.util.concurrent.TimeUnit

abstract class AbstractConnectionInitializer(
    protected val connectPromise: ChannelPromise
) : SimpleChannelInboundHandler<Packet>() {

    protected var state = State.CR1
    protected var sendTimer: ScheduledFuture<*>? = null
    protected var connectTimer: ScheduledFuture<*>? = null

    protected abstract fun sendRequest(ctx: ChannelHandlerContext)

    protected abstract fun removeHandler(ctx: ChannelHandlerContext)

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        val eventLoop = channel.eventLoop()
        sendTimer = eventLoop.scheduleAtFixedRate(
            { this.sendRequest(ctx) },
            0, 200, TimeUnit.MILLISECONDS
        )
        connectTimer = eventLoop.schedule(
            ::doTimeout,
            channel.config().connectTimeoutMillis.toLong(), TimeUnit.MILLISECONDS
        )
        this.sendRequest(ctx)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        sendTimer?.cancel(false)
        connectTimer?.cancel(false)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val config = config(ctx)
        ctx.writeAndFlush(ConnectionFailed(config.magic)).addListener { _ ->
            cause.fail()
        }
    }

    protected fun startPing(ctx: ChannelHandlerContext) {
        ctx.channel().pipeline().addAfter(NAME, PingProducer.NAME, PingProducer())
    }

    protected fun finish(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        connectPromise.trySuccess()
        removeHandler(ctx)
        channel.pipeline().fireChannelActive()
    }

    protected fun Throwable.fail() {
        connectPromise.tryFailure(this@fail)
    }

    protected fun doTimeout() {
        ConnectTimeoutException().fail()
    }

    enum class State {
        CR1,  //Raw: ConnectionRequest1 -> ConnectionReply1, InvalidVersion
        CR2,  //Raw: ConnectionRequest2 -> ConnectionReply2, ConnectionFailed
        CR3,  //Framed: ConnectionRequest -> Handshake -> ClientHandshake
        ;
    }

    companion object {
        const val NAME = "rn-init-connect"
    }
}