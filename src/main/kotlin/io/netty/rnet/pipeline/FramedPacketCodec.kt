package io.netty.rnet.pipeline

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import io.netty.rnet.RakNet.config
import io.netty.rnet.frame.FrameData
import io.netty.rnet.packet.FramedPacket

@Sharable
class FramedPacketCodec : MessageToMessageCodec<FrameData?, FramedPacket?>() {

    override fun encode(ctx: ChannelHandlerContext, inMsg: FramedPacket?, out: MutableList<Any>) {
        val msg = requireNotNull(inMsg)
        out.add(config(ctx).codec.encode(msg, ctx.alloc()))
    }

    override fun decode(ctx: ChannelHandlerContext, inMsg: FrameData?, out: MutableList<Any>) {
        val codec = config(ctx).codec
        val packet = codec.decode(requireNotNull(inMsg))
        out.add(requireNotNull(packet))
    }

    companion object {
        const val NAME = "rn-framed-codec"
        val INSTANCE = FramedPacketCodec()
    }

}