package io.netty.kcp.handler

import io.netty.channel.Channel
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramPacket
import io.netty.kcp.KcpBootstrap
import io.netty.kcp.cfg.*
import io.netty.kcp.core.Kcp
import io.netty.kcp.core.KcpProcessor
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue

@Sharable
class UdpServerHandler : SimpleChannelInboundHandler<DatagramPacket>() {

    private val inputList = LinkedBlockingQueue<DatagramPacket>()
    private val kcpMap: MutableMap<InetSocketAddress, Kcp> = HashMap()

    /**
     * DatagramPacket 是消息容器，这个消息容器被 DatagramChannel使用
     * <p>
     *     1.通过content()来获取消息内容
     *     2.通过sender();来获取发送者的消息
     *     3.通过recipient();来获取接收者的消息
     *
     * @param ctx
     * @param msg
     */
    @Throws(Exception::class)
    override fun channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket) {
        val sender = msg.sender()
        val content = msg.content()
        inputList.add(msg)
        var kcp = kcpMap[sender]
        if (kcp == null) {
            val conv = content.getIntLE(0)
            kcpMap[sender] = kcp(conv, ctx.channel(), sender)
            kcp = kcpMap[sender]
        }
        kcp?.input(content)
    }

    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
        cause.printStackTrace()
    }

    private fun kcp(conv: Int, channel: Channel, user: InetSocketAddress): Kcp {
        val kcpBootstrap = KcpBootstrap()
        kcpBootstrap
            .kcp(KcpProcessor(conv, channel, user))
            .option(KCP_NODELAY, 0)
            .option(KCP_INTERVAL, 40)
            .option(KCP_RESEND, 0)
            .option(KCP_NOCWND, 0)
            .option(KCP_SND_WND, 32)
            .option(KCP_RCV_WND, 32)
            .option(KCP_MTU, 1400)
            .option(KCP_MIN_RTO, 100)
            .start()
        return kcpBootstrap.kcp
            ?: throw IllegalArgumentException("kcp is null.")
    }
}
