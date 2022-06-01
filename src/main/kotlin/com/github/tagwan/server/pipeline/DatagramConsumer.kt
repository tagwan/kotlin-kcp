package com.github.tagwan.server.pipeline

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.DatagramPacket
import io.netty.util.ReferenceCountUtil

@Sharable
object DatagramConsumer : ChannelInboundHandlerAdapter() {

    val NAME = "rn-datagram-consumer"

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is DatagramPacket) {
            ReferenceCountUtil.safeRelease(msg)
        } else {
            ctx.fireChannelRead(msg)
        }
    }
}