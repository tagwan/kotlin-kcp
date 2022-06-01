package io.netty.rnet.channel

import io.netty.buffer.ByteBufAllocator
import io.netty.channel.*
import io.netty.channel.socket.DatagramChannel
import io.netty.handler.flush.FlushConsolidationHandler
import io.netty.util.Attribute
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.Future
import io.netty.rnet.RakNet
import io.netty.rnet.config.DefaultConfig
import java.lang.reflect.InvocationTargetException
import java.net.PortUnreachableException
import java.net.SocketAddress
import java.nio.channels.ClosedChannelException
import java.util.function.Supplier

open class DatagramChannelProxy(
    ioChannelSupplier: Supplier<out DatagramChannel>
) : Channel {
    protected val pipeline: DefaultChannelPipeline
    protected val listener: DatagramChannel
    protected val config: Config

    init {
        listener = ioChannelSupplier.get()
        pipeline = newChannelPipeline()
        listener.pipeline().addLast(LISTENER_HANDLER_NAME, ListenerInboundProxy())
        pipeline()
            .addLast(LISTENER_HANDLER_NAME, ListnerOutboundProxy())
            .addLast(
                FlushConsolidationHandler(
                    FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES, true
                )
            )
        config = Config()
    }

    constructor(ioChannelType: Class<out DatagramChannel?>) : this(
        Supplier<DatagramChannel> {
            try {
                ioChannelType.getDeclaredConstructor().newInstance()
            } catch (e: InstantiationException) {
                throw IllegalArgumentException("Failed to create instance", e)
            } catch (e: IllegalAccessException) {
                throw IllegalArgumentException("Failed to create instance", e)
            } catch (e: NoSuchMethodException) {
                throw IllegalArgumentException("Failed to create instance", e)
            } catch (e: InvocationTargetException) {
                throw IllegalArgumentException("Failed to create instance", e)
            }
        }
    )

    override fun id(): ChannelId {
        return listener.id()
    }

    override fun eventLoop(): EventLoop {
        return listener.eventLoop()
    }

    override fun parent(): Channel {
        return listener
    }

    override fun config(): RakNet.Config {
        return config
    }

    override fun isOpen(): Boolean {
        return listener.isOpen
    }

    override fun isRegistered(): Boolean {
        return listener.isRegistered
    }

    override fun isActive(): Boolean {
        return listener.isActive
    }

    override fun metadata(): ChannelMetadata {
        return listener.metadata()
    }

    override fun localAddress(): SocketAddress {
        return listener.localAddress()
    }

    override fun remoteAddress(): SocketAddress {
        return listener.remoteAddress()
    }

    override fun closeFuture(): ChannelFuture {
        return listener.closeFuture()
    }

    override fun isWritable(): Boolean {
        return listener.isWritable
    }

    override fun bytesBeforeUnwritable(): Long {
        return listener.bytesBeforeUnwritable()
    }

    override fun bytesBeforeWritable(): Long {
        return listener.bytesBeforeWritable()
    }

    override fun unsafe(): Channel.Unsafe {
        return listener.unsafe()
    }

    override fun pipeline(): ChannelPipeline {
        return pipeline
    }

    override fun alloc(): ByteBufAllocator {
        return config().allocator
    }

    override fun read(): Channel {
        // NOOP
        return this
    }

    override fun flush(): Channel {
        pipeline.flush()
        return this
    }

    override fun bind(localAddress: SocketAddress): ChannelFuture {
        return pipeline.bind(localAddress)
    }

    override fun connect(remoteAddress: SocketAddress): ChannelFuture {
        return pipeline.connect(remoteAddress)
    }

    override fun connect(remoteAddress: SocketAddress, localAddress: SocketAddress): ChannelFuture {
        return pipeline.connect(remoteAddress, localAddress)
    }

    override fun disconnect(): ChannelFuture {
        return pipeline.disconnect()
    }

    override fun close(): ChannelFuture {
        return pipeline.close()
    }

    override fun deregister(): ChannelFuture {
        return pipeline.deregister()
    }

    override fun bind(localAddress: SocketAddress, promise: ChannelPromise): ChannelFuture {
        return pipeline.bind(localAddress, promise)
    }

    override fun connect(remoteAddress: SocketAddress, promise: ChannelPromise): ChannelFuture {
        return pipeline.connect(remoteAddress, promise)
    }

    override fun connect(
        remoteAddress: SocketAddress, localAddress: SocketAddress,
        promise: ChannelPromise
    ): ChannelFuture {
        return pipeline.connect(remoteAddress, localAddress, promise)
    }

    override fun disconnect(promise: ChannelPromise): ChannelFuture {
        return pipeline.disconnect(promise)
    }

    override fun close(promise: ChannelPromise): ChannelFuture {
        return pipeline.close(promise)
    }

    override fun deregister(promise: ChannelPromise): ChannelFuture {
        return pipeline.deregister(promise)
    }

    override fun write(msg: Any): ChannelFuture {
        return pipeline.write(msg)
    }

    override fun write(msg: Any, promise: ChannelPromise): ChannelFuture {
        return pipeline.write(msg, promise)
    }

    override fun writeAndFlush(msg: Any, promise: ChannelPromise): ChannelFuture {
        return pipeline.writeAndFlush(msg, promise)
    }

    override fun writeAndFlush(msg: Any): ChannelFuture {
        return pipeline.writeAndFlush(msg)
    }

    override fun newPromise(): ChannelPromise {
        return pipeline.newPromise()
    }

    override fun newProgressivePromise(): ChannelProgressivePromise {
        return pipeline.newProgressivePromise()
    }

    override fun newSucceededFuture(): ChannelFuture {
        return pipeline.newSucceededFuture()
    }

    override fun newFailedFuture(cause: Throwable): ChannelFuture {
        return pipeline.newFailedFuture(cause)
    }

    override fun voidPromise(): ChannelPromise {
        return pipeline.voidPromise()
    }

    override fun <T> attr(key: AttributeKey<T>): Attribute<T> {
        return listener.attr(key)
    }

    override fun <T> hasAttr(key: AttributeKey<T>): Boolean {
        return listener.hasAttr(key)
    }

    override fun compareTo(o: Channel): Int {
        return listener.compareTo(o)
    }

    protected open fun gracefulClose(promise: ChannelPromise) {
        listener.close(wrapPromise(promise))
    }

    protected fun newChannelPipeline(): DefaultChannelPipeline {
        return object : DefaultChannelPipeline(this) {
            override fun onUnhandledInboundException(cause: Throwable) {
                if (cause is ClosedChannelException) {
                    ReferenceCountUtil.safeRelease(cause)
                    return
                }
                super.onUnhandledInboundException(cause)
            }
        }
    }

    protected fun wrapPromise(`in`: ChannelPromise): ChannelPromise {
        val out = listener.newPromise()
        out.addListener { res: Future<in Void?> ->
            if (res.isSuccess) {
                `in`.trySuccess()
            } else {
                `in`.tryFailure(res.cause())
            }
        }
        return out
    }

    protected inner class Config() : DefaultConfig(this@DatagramChannelProxy) {
        override fun <T> getOption(option: ChannelOption<T>): T {
            return super.getOption(option) ?: return listener.config().getOption(option)
        }

        override fun <T> setOption(option: ChannelOption<T>, value: T): Boolean {
            val thisOption = super.setOption(option, value)
            val listenOption = listener.config().setOption(option, value)
            return thisOption || listenOption
        }
    }

    protected inner class ListnerOutboundProxy : ChannelOutboundHandler {
        override fun handlerAdded(ctx: ChannelHandlerContext) {
            assert(listener.eventLoop().inEventLoop())
        }

        override fun bind(
            ctx: ChannelHandlerContext, localAddress: SocketAddress,
            promise: ChannelPromise
        ) {
            listener.bind(localAddress, wrapPromise(promise))
        }

        override fun connect(
            ctx: ChannelHandlerContext, remoteAddress: SocketAddress,
            localAddress: SocketAddress, promise: ChannelPromise
        ) {
            listener.connect(remoteAddress, localAddress, wrapPromise(promise))
        }

        override fun handlerRemoved(ctx: ChannelHandlerContext) {
            // NOOP
        }

        override fun disconnect(ctx: ChannelHandlerContext, promise: ChannelPromise) {
            listener.disconnect(wrapPromise(promise))
        }

        override fun close(ctx: ChannelHandlerContext, promise: ChannelPromise) {
            gracefulClose(promise)
        }

        override fun deregister(ctx: ChannelHandlerContext, promise: ChannelPromise) {
            listener.deregister(wrapPromise(promise))
        }

        override fun read(ctx: ChannelHandlerContext) {
            // NOOP
        }

        override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
            listener.write(msg, wrapPromise(promise))
        }

        override fun flush(ctx: ChannelHandlerContext) {
            listener.flush()
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            if (cause is PortUnreachableException) {
                return
            }
            ctx.fireExceptionCaught(cause)
        }
    }

    protected inner class ListenerInboundProxy : ChannelInboundHandler {
        override fun channelRegistered(ctx: ChannelHandlerContext) {
            pipeline.fireChannelRegistered()
        }

        override fun channelUnregistered(ctx: ChannelHandlerContext) {
            pipeline.fireChannelUnregistered()
        }

        override fun handlerAdded(ctx: ChannelHandlerContext) {
            assert(listener.eventLoop().inEventLoop())
        }

        override fun channelActive(ctx: ChannelHandlerContext) {
            // NOOP - active status managed by connection sequence
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            pipeline.fireChannelInactive()
        }

        override fun handlerRemoved(ctx: ChannelHandlerContext) {
            // NOOP
        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            pipeline.fireChannelRead(msg)
        }

        override fun channelReadComplete(ctx: ChannelHandlerContext) {
            pipeline.fireChannelReadComplete()
        }

        override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
            // NOOP
        }

        override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
            pipeline.fireChannelWritabilityChanged()
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            if (cause is ClosedChannelException) {
                return
            }
            pipeline.fireExceptionCaught(cause)
        }
    }

    companion object {
        const val LISTENER_HANDLER_NAME = "rn-udp-listener-handler"
    }
}