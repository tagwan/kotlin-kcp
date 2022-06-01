package io.netty.rnet.pipeline

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import io.netty.rnet.frame.FrameData

/**
 * Configure a user data packet ID that will be used for ByteBuf messages in the channel.
 */
@Sharable
class UserDataCodec(private val packetId: Int) : MessageToMessageCodec<FrameData, ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        if (buf.isReadable) {
            out.add(FrameData.create(ctx.alloc(), packetId, buf))
        }
    }

    override fun decode(ctx: ChannelHandlerContext, packet: FrameData, out: MutableList<Any>) {
        require(!packet.isFragment)
        if (packet.dataSize > 0) {
            if (packetId == packet.packetId) {
                out.add(packet.createData()!!.skipBytes(1))
            } else {
                out.add(packet.retain())
            }
        }
    }

    companion object {
        const val NAME = "rn-user-data-codec"
    }
}