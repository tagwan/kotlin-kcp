import com.github.tagwan.client.RakNetClient
import com.github.tagwan.server.RakNetServer
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.rnet.MTU
import io.netty.rnet.SERVER_ID
import io.netty.rnet.pipeline.UserDataCodec
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * HelloWorld
 *
 * @data 2022/6/1 19:16
 */
class HelloWorld {

    val ioGroup: EventLoopGroup = NioEventLoopGroup()
    val childGroup: EventLoopGroup = DefaultEventLoopGroup()
    val localhost = InetSocketAddress("localhost", 31747)
    val helloWorld = "Hello world!"

    var resultStr: String = ""

    @Test
    @Throws(Throwable::class)
    fun helloWorld() {
        val serverChannel = ServerBootstrap()
            .group(ioGroup, childGroup)
            .channel(RakNetServer.CHANNEL)
            .option(
                SERVER_ID,
                1234567L
            ) //will be set randomly if not specified (optional)
            .childHandler(object : ChannelInitializer<Channel>() {
                override fun initChannel(ch: Channel) {
                    ch.pipeline().addLast(UserDataCodec.NAME, UserDataCodec(0xFE))
                    ch.pipeline().addLast(object : SimpleChannelInboundHandler<ByteBuf>() {
                        override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
                            resultStr = msg.readCharSequence(
                                msg.readableBytes(),
                                StandardCharsets.UTF_8
                            ).toString()
                            println(resultStr) //"Hello world!"
                        }
                    })
                }
            }).bind(localhost).sync().channel()
        val clientChannel = Bootstrap()
            .group(ioGroup)
            .channel(RakNetClient.CHANNEL)
            .option(MTU, 150) //can configure an initial MTU if desired (optional)
            .option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                5000
            ) //supports most normal netty ChannelOptions (optional)
            .option(ChannelOption.SO_REUSEADDR, true) //can also set socket options (optional)
            .handler(object : ChannelInitializer<Channel>() {
                override fun initChannel(ch: Channel) {
                    ch.pipeline().addLast(UserDataCodec.NAME, UserDataCodec(0xFE))
                }
            }).connect(localhost).sync().channel()
        val helloWorldBuf = Unpooled.buffer()
        helloWorldBuf.writeCharSequence(helloWorld, StandardCharsets.UTF_8)
        clientChannel.writeAndFlush(helloWorldBuf).sync()
        serverChannel.close().sync()
        clientChannel.close().sync()
        Assertions.assertEquals(resultStr, helloWorld)
    }
}