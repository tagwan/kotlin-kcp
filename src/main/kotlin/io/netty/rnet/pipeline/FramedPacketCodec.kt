package io.netty.rnet.pipeline

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import io.netty.rnet.RakNet.config
import io.netty.rnet.frame.FrameData
import io.netty.rnet.packet.FramedPacket

@Sharable
class FramedPacketCodec : MessageToMessageCodec<FrameData?, FramedPacket?>() {
    override fun encode(ctx: ChannelHandlerContext, `in`: FramedPacket?, out: MutableList<Any>) {
        out.add(config(ctx).codec.encode(`in`, ctx.alloc())!!)
    }

    override fun decode(ctx: ChannelHandlerContext, `in`: FrameData?, out: MutableList<Any>) {
        out.add(config(ctx).codec.decode(`in`)!!)
    }

    companion object {
        const val NAME = "rn-framed-codec"
        val INSTANCE = FramedPacketCodec()
    }
}