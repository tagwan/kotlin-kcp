package com.github.tagwan.client.pipeline

import io.netty.channel.ChannelException
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.rnet.RakNet
import io.netty.rnet.RakNet.config
import io.netty.rnet.packet.*
import io.netty.rnet.pipeline.AbstractConnectionInitializer
import java.net.InetSocketAddress

class ConnectionInitializer(
    connectPromise: ChannelPromise
) : AbstractConnectionInitializer(connectPromise) {

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Packet?) {
        val config = config(ctx)
        check(msg !is ConnectionFailed) { "Connection failed" }
        when (state) {
            State.CR1 -> {
                if (msg is ConnectionReply1) {
                    val cr1 = msg
                    cr1.magic?.verify(config.magic)
                    config.mTU = cr1.mtu
                    config.serverId = cr1.serverId
                    state = State.CR2
                } else if (msg is InvalidVersion) {
                    fail(InvalidVersion.InvalidVersionException)
                }
            }
            State.CR2 -> {
                if (msg is ConnectionReply2) {
                    val cr2 = msg
                    cr2.magic?.verify(config.magic)
                    config.mTU = cr2.mtu
                    config.serverId = cr2.serverId
                    state = State.CR3
                    val packet: Packet = ConnectionRequest(config.clientId)
                    ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER)
                } else if (msg is ConnectionFailed) {
                    fail(ChannelException("RakNet connection failed"))
                }
            }
            State.CR3 -> {
                if (msg is ServerHandshake) {
                    val packet: Packet = ClientHandshake(
                        msg.timestamp,
                        (ctx.channel().remoteAddress() as InetSocketAddress),
                        msg.getnExtraAddresses()
                    )
                    ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER)
                    startPing(ctx)
                    finish(ctx)
                    return
                }
            }
            else -> throw IllegalStateException("Unknown state $state")
        }
        sendRequest(ctx)
    }

    public override fun sendRequest(ctx: ChannelHandlerContext) {
        val config = config(ctx)
        when (state) {
            State.CR1 -> {
                val packet: Packet = ConnectionRequest1(
                    config.magic,
                    config.protocolVersion, config.mTU
                )
                ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER)
            }
            State.CR2 -> {
                val packet: Packet = ConnectionRequest2(
                    config.magic, config.mTU,
                    config.clientId, ctx.channel().remoteAddress() as InetSocketAddress
                )
                ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER)
            }
            State.CR3 -> {}
            else -> throw IllegalStateException("Unknown state $state")
        }
    }

    override fun removeHandler(ctx: ChannelHandlerContext) {
        ctx.channel().pipeline().remove(this)
    }
}