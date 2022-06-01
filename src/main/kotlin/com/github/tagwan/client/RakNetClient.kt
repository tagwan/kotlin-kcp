package com.github.tagwan.client

import com.github.tagwan.client.channel.RakNetClientChannel
import io.netty.channel.Channel
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.rnet.RakNet.PacketHandling
import io.netty.rnet.RakNet.ReliableFrameHandling
import io.netty.rnet.pipeline.AbstractConnectionInitializer
import io.netty.rnet.pipeline.FlushTickHandler
import io.netty.rnet.pipeline.RawPacketCodec

object RakNetClient {
    val CHANNEL = RakNetClientChannel::class.java

    class DefaultClientInitializer : ChannelInitializer<Channel>() {
        override fun initChannel(channel: Channel) {
            channel.pipeline()
                .addLast(FlushTickHandler.NAME, FlushTickHandler())
                .addLast(RawPacketCodec.NAME, RawPacketCodec.INSTANCE)
                .addLast(ReliableFrameHandling.INSTANCE)
                .addLast(PacketHandling.INSTANCE)
                .addLast(AbstractConnectionInitializer.NAME, ChannelInboundHandlerAdapter()) //will be removed
        }

        companion object {
            val INSTANCE = DefaultClientInitializer()
        }
    }
}