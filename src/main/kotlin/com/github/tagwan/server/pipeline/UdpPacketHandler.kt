package com.github.tagwan.server.pipeline

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramPacket
import io.netty.util.ReferenceCountUtil
import io.netty.rnet.RakNet.config
import io.netty.rnet.packet.FramedPacket
import io.netty.rnet.packet.Packet
import java.net.InetSocketAddress

abstract class UdpPacketHandler<T : Packet>(
    val type: Class<T>
) : SimpleChannelInboundHandler<DatagramPacket>() {
    var packetId = 0

    init {
        require(!FramedPacket::class.java.isAssignableFrom(type)) {
            "Framed packet types cannot be directly handled by UdpPacketHandler"
        }
    }

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        val config = config(ctx)
        packetId = config.codec.packetIdFor(type)
        require(packetId != -1) { "Unknown packet ID for class $type" }
    }

    override fun acceptInboundMessage(msg: Any): Boolean {
        if (msg is DatagramPacket) {
            val content = msg.content()
            return content.getUnsignedByte(content.readerIndex()).toInt() == packetId
        }
        return false
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket) {
        val config = config(ctx)
        val packet = config.codec.decode(msg.content()) as T
        try {
            this.handle(ctx, msg.sender(), packet)
        } finally {
            ReferenceCountUtil.release(packet)
        }
    }

    protected abstract fun handle(ctx: ChannelHandlerContext, sender: InetSocketAddress, packet: T)
}