package io.netty.rnet.pipeline

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.CorruptedFrameException
import io.netty.handler.codec.MessageToMessageCodec
import io.netty.rnet.RakNet.config
import io.netty.rnet.RakNet.metrics
import io.netty.rnet.packet.Packet

@Sharable
class RawPacketCodec : MessageToMessageCodec<ByteBuf, Packet?>() {
    override fun encode(ctx: ChannelHandlerContext, `in`: Packet?, out: MutableList<Any>) {
        val encoded = config(ctx).codec.produceEncoded(`in`, ctx.alloc())
        metrics(ctx).bytesOut(encoded!!.readableBytes())
        out.add(encoded)
    }

    override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
        if (`in`.readableBytes() != 0) {
            metrics(ctx).bytesIn(`in`.readableBytes())
            try {
                out.add(config(ctx).codec.decode(`in`)!!)
            } catch (e: CorruptedFrameException) {
                metrics(ctx).frameError(1) //tolerate frame errors, they'll get resent.
            }
        }
    }

    companion object {
        const val NAME = "rn-raw-codec"
        val INSTANCE = RawPacketCodec()
    }
}