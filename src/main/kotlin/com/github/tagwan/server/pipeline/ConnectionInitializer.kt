package com.github.tagwan.server.pipeline

import io.netty.channel.*
import io.netty.util.ReferenceCountUtil
import io.netty.rnet.RakNet
import io.netty.rnet.RakNet.config
import io.netty.rnet.packet.*
import io.netty.rnet.packet.Packet.ClientIdConnection
import io.netty.rnet.pipeline.AbstractConnectionInitializer
import java.net.InetSocketAddress

class ConnectionInitializer(
    connectPromise: ChannelPromise
) : AbstractConnectionInitializer(connectPromise) {

    protected var clientIdSet = false
    protected var mtuFixed = false
    protected var seenFirst = false

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Packet?) {
        val config = config(ctx)
        if (msg is ClientIdConnection) {
            processClientId(ctx, msg.clientId)
        } else check(msg !is ConnectionFailed) { "Connection failed" }
        when (state) {
            State.CR1 -> if (msg is ConnectionRequest1) {
                val cr1 = msg
                cr1.magic?.verify(config.magic)
                if (!mtuFixed) {
                    config.mTU = cr1.mtu
                }
                seenFirst = true
                if (!config.containsProtocolVersion(cr1.protocolVersion)) {
                    val packet = InvalidVersion(config.magic, config.serverId)
                    ctx.writeAndFlush(packet).addListener(ChannelFutureListener.CLOSE)
                    return
                }
                config.protocolVersion = cr1.protocolVersion
            } else if (msg is ConnectionRequest2) {
                val cr2 = msg
                cr2.magic?.verify(config.magic)
                if (!mtuFixed) {
                    config.mTU = cr2.mtu
                }
                state = State.CR2
            }
            State.CR2 -> {
                if (msg is ConnectionRequest) {
                    val packet: Packet = ServerHandshake(
                        ctx.channel().remoteAddress() as InetSocketAddress,
                        msg.timestamp
                    )
                    ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER)
                    state = State.CR3
                    startPing(ctx)
                }
            }
            State.CR3 -> {
                if (msg is ClientHandshake) {
                    finish(ctx)
                    return
                }
            }
            else -> throw IllegalStateException("Unknown state $state")
        }
        sendRequest(ctx)
    }

    public override fun sendRequest(ctx: ChannelHandlerContext) {
        assert(ctx.channel().eventLoop().inEventLoop())
        val config = config(ctx)
        when (state) {
            State.CR1 -> {
                if (seenFirst) {
                    val packet: Packet = ConnectionReply1(
                        config.magic,
                        config.mTU, config.serverId
                    )
                    ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER)
                }
            }
            State.CR2 -> {
                val packet: Packet = ConnectionReply2(
                    config.magic, config.mTU,
                    config.serverId, ctx.channel().remoteAddress() as InetSocketAddress
                )
                ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER)
            }
            State.CR3 -> {}
            else -> throw IllegalStateException("Unknown state $state")
        }
    }

    override fun removeHandler(ctx: ChannelHandlerContext) {
        ctx.channel().pipeline().replace(NAME, NAME, RestartConnectionHandler())
    }

    protected fun processClientId(ctx: ChannelHandlerContext, clientId: Long) {
        val config = config(ctx)
        if (!clientIdSet) {
            config.clientId = clientId
            clientIdSet = true
        } else check(config.clientId == clientId) { "Connection sequence restarted" }
    }

    protected inner class RestartConnectionHandler : ChannelInboundHandlerAdapter() {
        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            if (msg is ClientIdConnection || msg is ConnectionRequest1) {
                val config = config(ctx)
                ctx.writeAndFlush(ConnectionFailed(config.magic)).addListener(ChannelFutureListener.CLOSE)
                ReferenceCountUtil.safeRelease(msg)
            } else if (msg is ConnectionFailed) {
                ReferenceCountUtil.safeRelease(msg)
                ctx.close()
            } else {
                ctx.fireChannelRead(msg)
            }
        }
    }

    companion object {

        fun setFixedMTU(channel: Channel, mtu: Int) {
            channel.eventLoop().execute {
                channel.pipeline()[ConnectionInitializer::class.java].mtuFixed = true
                config(channel).mTU = mtu
            }
        }

    }
}