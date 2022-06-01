package utils

import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.DatagramChannelConfig
import io.netty.channel.socket.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketAddress
import java.util.function.Consumer

class MockDatagram(
    parent: Channel?, val localAddress: InetSocketAddress,
    val remoteAddress: InetSocketAddress
) : AbstractChannel(parent), DatagramChannel {
    //val config: DatagramChannelConfig = mock(DatagramChannelConfig::class.java)
    var writeOut: Consumer<DatagramPacket>? = null
    var connected = false
    var closed = false

    init {
        pipeline().addFirst(object : ChannelOutboundHandlerAdapter() {
            override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
                val buf: ByteBuf
                buf = if (msg is ByteBuf) {
                    msg
                } else {
                    (msg as DatagramPacket).content()
                }
                if (buf.readableBytes() > fixedMTU) {
                    writeOut!!.accept(
                        DatagramPacket(
                            buf.readSlice(fixedMTU),
                            remoteAddress,
                            localAddress
                        )
                    )
                } else {
                    writeOut!!.accept(DatagramPacket(buf, remoteAddress, localAddress))
                }
                promise.trySuccess()
            }
        })
    }

    override fun isWritable(): Boolean {
        return true
    }

    override fun localAddress(): InetSocketAddress {
        return localAddress0()
    }

    override fun remoteAddress(): InetSocketAddress {
        return remoteAddress0()
    }

    override fun newUnsafe(): AbstractUnsafe {
        return object : AbstractUnsafe() {
            override fun connect(
                remoteAddress: SocketAddress, localAddress: SocketAddress,
                promise: ChannelPromise
            ) {
                connected = true
                promise.trySuccess()
            }
        }
    }

    override fun isCompatible(loop: EventLoop): Boolean {
        return true
    }

    override fun localAddress0(): InetSocketAddress {
        return localAddress
    }

    override fun remoteAddress0(): InetSocketAddress {
        return remoteAddress
    }

    override fun doBind(localAddress: SocketAddress) {
        connected = true
    }

    override fun doDisconnect() {}
    override fun doClose() {
        closed = true
    }

    override fun doBeginRead() {}
    override fun doWrite(`in`: ChannelOutboundBuffer) {}

    override fun config(): DatagramChannelConfig? {
        return null//config TODO
    }

    override fun isOpen(): Boolean {
        return !closed
    }

    override fun isActive(): Boolean {
        return connected
    }

    override fun metadata(): ChannelMetadata {
        return METADATA
    }

    override fun isConnected(): Boolean {
        return isOpen
    }

    override fun joinGroup(multicastAddress: InetAddress): ChannelFuture? {
        return null
    }

    override fun joinGroup(multicastAddress: InetAddress, future: ChannelPromise): ChannelFuture? {
        return null
    }

    override fun joinGroup(
        multicastAddress: InetSocketAddress,
        networkInterface: NetworkInterface
    ): ChannelFuture? {
        return null
    }

    override fun joinGroup(
        multicastAddress: InetSocketAddress,
        networkInterface: NetworkInterface, future: ChannelPromise
    ): ChannelFuture? {
        return null
    }

    override fun joinGroup(
        multicastAddress: InetAddress, networkInterface: NetworkInterface,
        source: InetAddress
    ): ChannelFuture? {
        return null
    }

    override fun joinGroup(
        multicastAddress: InetAddress, networkInterface: NetworkInterface,
        source: InetAddress, future: ChannelPromise
    ): ChannelFuture? {
        return null
    }

    override fun leaveGroup(multicastAddress: InetAddress): ChannelFuture? {
        return null
    }

    override fun leaveGroup(multicastAddress: InetAddress, future: ChannelPromise): ChannelFuture? {
        return null
    }

    override fun leaveGroup(
        multicastAddress: InetSocketAddress,
        networkInterface: NetworkInterface
    ): ChannelFuture? {
        return null
    }

    override fun leaveGroup(
        multicastAddress: InetSocketAddress,
        networkInterface: NetworkInterface, future: ChannelPromise
    ): ChannelFuture? {
        return null
    }

    override fun leaveGroup(
        multicastAddress: InetAddress, networkInterface: NetworkInterface,
        source: InetAddress
    ): ChannelFuture? {
        return null
    }

    override fun leaveGroup(
        multicastAddress: InetAddress, networkInterface: NetworkInterface,
        source: InetAddress, future: ChannelPromise
    ): ChannelFuture? {
        return null
    }

    override fun block(
        multicastAddress: InetAddress, networkInterface: NetworkInterface,
        sourceToBlock: InetAddress
    ): ChannelFuture? {
        return null
    }

    override fun block(
        multicastAddress: InetAddress, networkInterface: NetworkInterface,
        sourceToBlock: InetAddress, future: ChannelPromise
    ): ChannelFuture? {
        return null
    }

    override fun block(multicastAddress: InetAddress, sourceToBlock: InetAddress): ChannelFuture? {
        return null
    }

    override fun block(
        multicastAddress: InetAddress, sourceToBlock: InetAddress,
        future: ChannelPromise
    ): ChannelFuture? {
        return null
    }

    companion object {
        const val fixedMTU = 700
        val METADATA = ChannelMetadata(false, 16)
    }
}