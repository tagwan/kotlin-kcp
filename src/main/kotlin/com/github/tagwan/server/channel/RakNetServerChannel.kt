package com.github.tagwan.server.channel

import io.netty.channel.*
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.PromiseCombiner
import io.netty.rnet.SERVER_ID
import io.netty.rnet.channel.DatagramChannelProxy
import io.netty.rnet.packet.NoFreeConnections
import io.netty.rnet.packet.Packet
import com.github.tagwan.server.RakNetServer
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.function.Consumer
import java.util.function.Supplier

class RakNetServerChannel : DatagramChannelProxy, ServerChannel {
    protected val childMap: MutableMap<SocketAddress, Channel> = HashMap()

    constructor(ioChannelSupplier: Supplier<out DatagramChannel?>?) : super(
        ioChannelSupplier!!
    ) {
        addDefaultPipeline()
    }

    @JvmOverloads
    constructor(ioChannelType: Class<out DatagramChannel?>? = NioDatagramChannel::class.java) : super(
        ioChannelType!!
    ) {
        addDefaultPipeline()
    }

    fun getChildChannel(addr: SocketAddress): Channel? {
        check(eventLoop().inEventLoop()) { "Method must be called from the server eventLoop!" }
        return childMap[addr]
    }

    override fun gracefulClose(promise: ChannelPromise) {
        val combined = PromiseCombiner(eventLoop())
        val childrenClosed: ChannelPromise = newPromise()
        childMap.values.forEach(Consumer { child: Channel ->
            combined.add(
                child.close()
            )
        })
        combined.finish(childrenClosed)
        childrenClosed.addListener { _ ->
            listener.close(
                wrapPromise(promise)
            )
        }
    }

    protected fun addDefaultPipeline() {
        pipeline()
            .addLast(newServerHandler())
            .addLast(RakNetServer.DefaultDatagramInitializer.INSTANCE)
    }

    protected fun newServerHandler(): ChannelHandler {
        return ServerHandler()
    }

    protected fun newChild(remoteAddress: InetSocketAddress?): Channel {
        return RakNetChildChannel(this, remoteAddress!!)
    }

    protected fun removeChild(remoteAddress: SocketAddress, child: Channel) {
        childMap.remove(remoteAddress, child)
    }

    protected fun addChild(remoteAddress: SocketAddress, child: Channel) {
        childMap[remoteAddress] = child
    }

    protected inner class ServerHandler : ChannelDuplexHandler() {
        override fun connect(
            ctx: ChannelHandlerContext, remoteAddress: SocketAddress,
            localAddress: SocketAddress, promise: ChannelPromise
        ) {
            try {
                require(!(localAddress != null && localAddress() != localAddress)) { "Bound localAddress does not match provided $localAddress" }
                require(remoteAddress is InetSocketAddress) { "Provided remote address is not an InetSocketAddress" }
                val existingChild = getChildChannel(remoteAddress)
                if (childMap.size > config.maxConnections && existingChild == null) {
                    val packet: Packet = NoFreeConnections(
                        config.magic, config.serverId
                    )
                    val buf = ctx.alloc().ioBuffer(packet.sizeHint())
                    try {
                        config.codec.encode(packet, buf)
                        ctx.writeAndFlush(
                            DatagramPacket(
                                buf.retain(),
                                remoteAddress
                            )
                        )
                    } finally {
                        ReferenceCountUtil.safeRelease(packet)
                        buf.release()
                    }
                    promise.tryFailure(IllegalStateException("Too many connections"))
                } else if (existingChild == null) {
                    val child = newChild(remoteAddress)
                    child.closeFuture().addListener { v: Future<in Void?>? ->
                        eventLoop().execute(
                            Runnable { removeChild(remoteAddress, child) })
                    }
                    child.config().setOption<Long>(SERVER_ID, config.serverId)
                    pipeline().fireChannelRead(child).fireChannelReadComplete() //register
                    addChild(remoteAddress, child)
                    promise.trySuccess()
                } else {
                    promise.trySuccess() //already connected
                }
            } catch (e: Exception) {
                promise.tryFailure(e)
                throw e
            }
        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            if (msg is DatagramPacket) {
                val datagram = msg
                try {
                    val child = childMap[datagram.sender()]
                    //a null recipient means this was locally forwarded, dont process again
                    if (child == null && datagram.recipient() != null) {
                        ctx.fireChannelRead(datagram.retain())
                    } else if (child != null && child.isOpen && child.config().isAutoRead) {
                        val retained = datagram.content().retain()
                        child.eventLoop().execute {
                            child.pipeline().fireChannelRead(retained).fireChannelReadComplete()
                        }
                    }
                } finally {
                    datagram.release()
                }
            } else {
                ctx.fireChannelRead(msg)
            }
        }

        override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
            childMap.values.forEach(Consumer { ch: Channel ->
                ch.pipeline().fireChannelWritabilityChanged()
            })
            ctx.fireChannelWritabilityChanged()
        }
    }
}