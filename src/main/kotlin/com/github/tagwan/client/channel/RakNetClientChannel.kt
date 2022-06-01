package com.github.tagwan.client.channel

import com.github.tagwan.client.RakNetClient
import com.github.tagwan.client.pipeline.ConnectionInitializer
import io.netty.channel.*
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.PromiseCombiner
import io.netty.rnet.WRITABLE
import io.netty.rnet.channel.DatagramChannelProxy
import io.netty.rnet.pipeline.AbstractConnectionInitializer
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.function.Supplier

class RakNetClientChannel : DatagramChannelProxy {

    protected val connectPromise: ChannelPromise

    constructor(ioChannelSupplier: Supplier<out DatagramChannel>) : super(
        ioChannelSupplier
    ) {
        connectPromise = newPromise()
        addDefaultPipeline()
    }

    constructor(ioChannelType: Class<out DatagramChannel> = NioDatagramChannel::class.java) : super(ioChannelType) {
        connectPromise = newPromise()
        addDefaultPipeline()
    }

    fun connectFuture(): ChannelFuture {
        return connectPromise
    }

    override fun isActive(): Boolean {
        return super.isActive() && connectPromise.isSuccess
    }

    override fun isWritable(): Boolean {
        val result: Boolean? = attr(WRITABLE).get()
        return (result == null || result) && super.isWritable()
    }

    protected fun addDefaultPipeline() {
        pipeline()
            .addLast(newClientHandler())
            .addLast(RakNetClient.DefaultClientInitializer.INSTANCE)
        connectPromise.addListener { res: Future<in Void?> ->
            if (!res.isSuccess) {
                this@RakNetClientChannel.close()
            }
        }
    }

    protected fun newClientHandler(): ChannelHandler {
        return ClientHandler()
    }

    protected inner class ClientHandler : ChannelDuplexHandler() {
        override fun connect(
            ctx: ChannelHandlerContext, remoteAddress: SocketAddress,
            localAddress: SocketAddress, promise: ChannelPromise
        ) {
            try {
                require(remoteAddress is InetSocketAddress) { "Provided remote address is not an InetSocketAddress" }
                check(!listener.isActive) { "Channel connection already started" }
                val listenerConnect = listener.connect(remoteAddress, localAddress)
                listenerConnect.addListener { udpConnectResult: Future<in Void?> ->
                    if (udpConnectResult.isSuccess) {
                        //start connection process
                        pipeline().replace(
                            AbstractConnectionInitializer.NAME, AbstractConnectionInitializer.NAME,
                            ConnectionInitializer(connectPromise)
                        )
                    }
                }
                val combiner = PromiseCombiner(eventLoop())
                combiner.add(listenerConnect)
                combiner.add(connectPromise as ChannelFuture)
                combiner.finish(promise)
            } catch (t: Exception) {
                promise.tryFailure(t)
            }
        }

        override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
            listener.write(msg, wrapPromise(promise))
        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            if (msg is DatagramPacket) {
                try {
                    if (msg.sender() == null || msg.sender() == remoteAddress()) {
                        ctx.fireChannelRead(msg.content().retain())
                    }
                } finally {
                    ReferenceCountUtil.release(msg)
                }
            } else {
                ctx.fireChannelRead(msg)
            }
        }

        override fun read(ctx: ChannelHandlerContext) {
            // NOOP
        }
    }
}