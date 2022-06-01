package com.github.tagwan.server.channel

import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.socket.DatagramPacket
import io.netty.rnet.METRICS
import io.netty.rnet.*
import io.netty.rnet.config.DefaultConfig
import io.netty.rnet.pipeline.AbstractConnectionInitializer
import com.github.tagwan.server.RakNetServer
import com.github.tagwan.server.pipeline.ConnectionInitializer
import java.net.InetSocketAddress
import java.net.SocketAddress

class RakNetChildChannel(parent: Channel, protected val remoteAddress: InetSocketAddress) : AbstractChannel(parent) {
    protected val connectPromise: ChannelPromise
    protected val config: RakNet.Config

    @Volatile
    protected var open = true

    init {
        config = DefaultConfig(this)
        connectPromise = newPromise()
        config.metrics = parent.config().getOption(METRICS)
        config.serverId = parent.config().getOption(SERVER_ID)
        pipeline().addLast(WriteHandler())
        addDefaultPipeline()
    }

    protected fun addDefaultPipeline() {
        pipeline().addLast(RakNetServer.DefaultChildInitializer.INSTANCE)
        connectPromise.addListener { x2 ->
            if (!x2.isSuccess) {
                this@RakNetChildChannel.close()
            }
        }
        pipeline().addLast(object : ChannelInitializer<RakNetChildChannel>() {
            override fun initChannel(ch: RakNetChildChannel) {
                pipeline().replace(
                    AbstractConnectionInitializer.NAME, AbstractConnectionInitializer.NAME,
                    ConnectionInitializer(connectPromise)
                )
            }
        })
    }

    fun connectFuture(): ChannelFuture {
        return connectPromise
    }

    override fun isWritable(): Boolean {
        val result = attr(WRITABLE).get()
        return (result == null || result) && parent().isWritable
    }

    override fun bytesBeforeUnwritable(): Long {
        return parent().bytesBeforeUnwritable()
    }

    override fun bytesBeforeWritable(): Long {
        return parent().bytesBeforeWritable()
    }

    override fun parent(): RakNetServerChannel {
        return super.parent() as RakNetServerChannel
    }

    override fun newUnsafe(): AbstractUnsafe {
        return object : AbstractUnsafe() {
            override fun connect(addr1: SocketAddress, addr2: SocketAddress, pr: ChannelPromise) {
                throw UnsupportedOperationException()
            }
        }
    }

    override fun isCompatible(eventloop: EventLoop): Boolean {
        return true
    }

    override fun localAddress0(): SocketAddress {
        return parent().localAddress()
    }

    override fun remoteAddress0(): SocketAddress {
        return remoteAddress
    }

    override fun doBind(addr: SocketAddress) {
        throw UnsupportedOperationException()
    }

    override fun doDisconnect() {
        close()
    }

    override fun doClose() {
        open = false
    }

    override fun doBeginRead() {
        // NOOP
    }

    override fun doWrite(buffer: ChannelOutboundBuffer) {
        throw UnsupportedOperationException()
    }

    override fun config(): RakNet.Config {
        return config
    }

    override fun isOpen(): Boolean {
        return open
    }

    override fun isActive(): Boolean {
        return isOpen && parent().isActive() && connectPromise.isSuccess
    }

    override fun metadata(): ChannelMetadata {
        return metadata
    }

    protected inner class WriteHandler : ChannelOutboundHandlerAdapter() {
        protected var needsFlush = false
        override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
            if (msg is ByteBuf) {
                needsFlush = true
                promise.trySuccess()
                parent().write(DatagramPacket(msg, remoteAddress))
                    .addListener(RakNet.INTERNAL_WRITE_LISTENER)
            } else {
                ctx.write(msg, promise)
            }
        }

        override fun flush(ctx: ChannelHandlerContext) {
            if (needsFlush) {
                needsFlush = false
                parent().flush()
            }
        }

        override fun read(ctx: ChannelHandlerContext) {
            // NOOP
        }
    }

    companion object {
        private val metadata = ChannelMetadata(false)
    }
}