package com.github.tagwan.server.pipeline

import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.socket.DatagramPacket
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import io.netty.rnet.RakNet.config
import io.netty.rnet.packet.ConnectionRequest1
import io.netty.rnet.packet.InvalidVersion
import io.netty.rnet.packet.Packet
import java.net.InetSocketAddress

class ConnectionListener : UdpPacketHandler<ConnectionRequest1>(ConnectionRequest1::class.java) {
    override fun handle(ctx: ChannelHandlerContext, sender: InetSocketAddress, request: ConnectionRequest1) {
        val config = config(ctx)
        if (config.containsProtocolVersion(request.protocolVersion)) {
            ReferenceCountUtil.retain(request)
            //use connect to create a new child for this remote address
            ctx.channel().connect(sender).addListeners(
                ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE,
                GenericFutureListener { future: Future<in Void?> ->
                    if (future.isSuccess) {
                        resendRequest(ctx, sender, request)
                    } else {
                        ReferenceCountUtil.safeRelease(request)
                    }
                }
            )
        } else {
            sendResponse(ctx, sender, InvalidVersion(config.magic, config.serverId))
        }
    }

    protected fun sendResponse(ctx: ChannelHandlerContext, sender: InetSocketAddress, packet: Packet) {
        val config = config(ctx)
        val buf = ctx.alloc().ioBuffer(packet.sizeHint())
        try {
            config.codec.encode(packet, buf)
            ctx.writeAndFlush(DatagramPacket(buf.retain(), sender))
                .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)
        } finally {
            ReferenceCountUtil.safeRelease(packet)
            buf.release()
        }
    }

    protected fun resendRequest(ctx: ChannelHandlerContext, sender: InetSocketAddress, request: ConnectionRequest1) {
        val config = config(ctx)
        val buf = ctx.alloc().ioBuffer(request.sizeHint())
        try {
            config.codec.encode(request, buf)
            ctx.pipeline().fireChannelRead(
                DatagramPacket(
                    buf.retain(),
                    null, sender
                )
            ).fireChannelReadComplete()
        } finally {
            ReferenceCountUtil.safeRelease(request)
            buf.release()
        }
    }

    companion object {
        const val NAME = "rn-connect-init"
    }
}