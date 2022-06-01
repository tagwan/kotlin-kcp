package com.github.tagwan.server

import com.github.tagwan.server.channel.RakNetServerChannel
import com.github.tagwan.server.pipeline.ConnectionListener
import com.github.tagwan.server.pipeline.DatagramConsumer
import io.netty.channel.Channel
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.rnet.RakNet.PacketHandling
import io.netty.rnet.RakNet.ReliableFrameHandling
import io.netty.rnet.pipeline.AbstractConnectionInitializer
import io.netty.rnet.pipeline.FlushTickHandler
import io.netty.rnet.pipeline.RawPacketCodec

object RakNetServer {

    val CHANNEL = RakNetServerChannel::class.java

    class DefaultDatagramInitializer : ChannelInitializer<Channel>() {
        override fun initChannel(channel: Channel) {
            channel.pipeline().addLast(ConnectionListener.NAME, ConnectionListener())
            //must defer so we can add it before the ServerBootstrap acceptor
            channel.eventLoop().execute {
                channel.pipeline().addLast(DatagramConsumer.NAME, DatagramConsumer)
            }
        }

        companion object {
            val INSTANCE: ChannelInitializer<Channel> = DefaultDatagramInitializer()
        }
    }

    class DefaultChildInitializer : ChannelInitializer<Channel>() {
        override fun initChannel(channel: Channel) {
            channel.pipeline()
                .addLast(FlushTickHandler.NAME, FlushTickHandler())
                .addLast(RawPacketCodec.NAME, RawPacketCodec.INSTANCE)
                .addLast(ReliableFrameHandling.INSTANCE)
                .addLast(PacketHandling.INSTANCE)
                .addLast(
                    AbstractConnectionInitializer.NAME,
                    ChannelInboundHandlerAdapter()
                ) //will be replaced
        }

        companion object {
            val INSTANCE: ChannelInitializer<Channel> = DefaultChildInitializer()
        }
    }
}