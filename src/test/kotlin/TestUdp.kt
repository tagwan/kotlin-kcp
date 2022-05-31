import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.Test


class TestChannelInitializer : ChannelInitializer<NioDatagramChannel>() {

    @Throws(Exception::class)
    override fun initChannel(ch: NioDatagramChannel) {
        val pipeline = ch.pipeline()
        // 解码转String，注意调整自己的编码格式GBK、UTF-8
        //pipeline.addLast("stringDecoder", new StringDecoder(Charset.forName("GBK")));
        pipeline.addLast(TestServerHandler())
    }
}

class TestServerHandler : SimpleChannelInboundHandler<DatagramPacket>() {

    // 只会启动的时候一次
    override fun channelActive(ctx: ChannelHandlerContext) {
        println("channelActive-->${ctx.channel().remoteAddress()}")
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        println("channelInactive-->${ctx.channel().remoteAddress()}")
    }

    @Throws(java.lang.Exception::class)
    override fun channelRead0(ctx: ChannelHandlerContext, packet: DatagramPacket) {
        val msg: String = packet.content().toString()
        val sender = packet.sender()
        val recipient = packet.recipient()

        println("msg-->$msg, sender-->$sender, recipient-->$recipient")

        //向客户端发送消息
        val json = "通知：我已经收到你的消息\r\n"
        // 由于数据报的数据是以字符数组传的形式存储的，所以传转数据
        val bytes = json.toByteArray(Charset.forName("GBK"))
        val data = DatagramPacket(Unpooled.copiedBuffer(bytes), packet.sender())
        ctx.writeAndFlush(data) //向客户端发送消息
    }
}

class TestUdp {

    @Test
    fun test() {
        val group: EventLoopGroup = NioEventLoopGroup()
        try {
            val b = Bootstrap()
            b.group(group)
                .channel(NioDatagramChannel::class.java)
                .option(ChannelOption.SO_BROADCAST, true) //广播
                .option(ChannelOption.SO_RCVBUF, 2048 * 1024) // 设置UDP读缓冲区为2M
                .option(ChannelOption.SO_SNDBUF, 1024 * 1024) // 设置UDP写缓冲区为1M
                .handler(TestChannelInitializer())

            val f: ChannelFuture = b.bind(7397).sync()
            f.channel().closeFuture().sync()
        } finally {
            // 优雅的关闭释放内存
            group.shutdownGracefully()
        }

    }
}