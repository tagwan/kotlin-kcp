package com.github.tagwan.server

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollChannelOption
import io.netty.channel.epoll.EpollDatagramChannel
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * SimpleUdpServer
 *
 * @data 2022/5/31 15:39
 */
class SimpleUdpServer(
    private val port: Int
) : IServer {

    private val epoll = Epoll.isAvailable()
    private var channel: Channel? = null
    private var localAddress: InetSocketAddress? = null
    private val logger = LoggerFactory.getLogger(SimpleUdpServer::class.java)

    init {
        require(port in 0..0xFFFF) { "port out of range:$port" }
    }

    override fun start() {
        val cpuNum = Runtime.getRuntime().availableProcessors()
        val bootstrap = Bootstrap()
        bootstrap.group(if (epoll) EpollEventLoopGroup(cpuNum) else NioEventLoopGroup(1))
            .channel(if (epoll) EpollDatagramChannel::class.java else NioDatagramChannel::class.java)
            .option(ChannelOption.SO_REUSEADDR, true)

        if (epoll) {
            bootstrap.option(EpollChannelOption.SO_REUSEPORT, true)
        }

        logger.info("udp server start at port:{}", port)

        // bind
        val channelFuture = bootstrap.bind(port)
        channel = channelFuture.channel()
        localAddress = channel?.localAddress() as InetSocketAddress

        Runtime.getRuntime().addShutdownHook(Thread { dispose() })
    }

    override fun dispose() {
        channel?.close()
    }
}