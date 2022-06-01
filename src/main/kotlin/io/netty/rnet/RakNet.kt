package io.netty.rnet

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.*
import io.netty.handler.codec.CorruptedFrameException
import io.netty.util.AttributeKey
import io.netty.rnet.frame.FrameData
import io.netty.rnet.packet.FramedPacket
import io.netty.rnet.packet.Packet
import io.netty.rnet.pipeline.*
import java.nio.channels.ClosedChannelException

object RakNet {

    val INTERNAL_WRITE_LISTENER =
        ChannelFutureListener { future: ChannelFuture ->
            if (!future.isSuccess && future.cause() !is ClosedChannelException) {
                future.channel().pipeline().fireExceptionCaught(future.cause())
                future.channel().close()
            }
        }

    fun config(ctx: ChannelHandlerContext): Config {
        return config(ctx.channel())
    }

    fun config(channel: Channel): Config {
        return channel.config() as Config
    }

    fun metrics(ctx: ChannelHandlerContext): MetricsLogger {
        return config(ctx).metrics!!
    }

    /**
     * Channel specific metrics logging interface.
     */
    interface MetricsLogger {
        fun packetsIn(delta: Int) {}
        fun framesIn(delta: Int) {}
        fun frameError(delta: Int) {}
        fun bytesIn(delta: Int) {}
        fun packetsOut(delta: Int) {}
        fun framesOut(delta: Int) {}
        fun bytesOut(delta: Int) {}
        fun bytesRecalled(delta: Int) {}
        fun bytesACKd(delta: Int) {}
        fun bytesNACKd(delta: Int) {}
        fun acksSent(delta: Int) {}
        fun nacksSent(delta: Int) {}
        fun measureRTTns(n: Long) {}
        fun measureRTTnsStdDev(n: Long) {}
        fun measureBurstTokens(n: Int) {}
    }

    interface Config : ChannelConfig {
        var metrics: MetricsLogger

        /**
         * @return Server ID used during handshake.
         */
        var serverId: Long

        /**
         * @return Client ID used during handshake.
         */
        var clientId: Long

        /**
         * @return MTU in bytes, negotiated during handshake.
         */
        var mTU: Int

        /**
         * @return Offset used while calculating retry period.
         */
        var retryDelayNanos: Long
        var rTTNanos: Long
        val rTTStdDevNanos: Long

        fun updateRTTNanos(rttSample: Long)
        var maxPendingFrameSets: Int
        var defaultPendingFrameSets: Int
        var maxQueuedBytes: Int
        var magic: Magic
        var codec: Codec
        val protocolVersions: IntArray?

        fun setprotocolVersions(protocolVersions: IntArray?)
        fun containsProtocolVersion(protocolVersion: Int): Boolean
        var protocolVersion: Int
        var maxConnections: Int
    }

    interface Codec {
        fun encode(packet: FramedPacket?, alloc: ByteBufAllocator?): FrameData?
        fun encode(packet: Packet?, out: ByteBuf?)
        fun produceEncoded(packet: Packet?, alloc: ByteBufAllocator?): ByteBuf?
        fun decode(buf: ByteBuf?): Packet?
        fun decode(data: FrameData?): FramedPacket?
        fun packetIdFor(type: Class<out Packet?>?): Int
    }

    interface Magic {
        fun write(buf: ByteBuf?)
        fun read(buf: ByteBuf?)
        fun verify(other: Magic?)
        class MagicMismatchException : CorruptedFrameException("Incorrect RakNet magic value") {
            @Synchronized
            override fun fillInStackTrace(): Throwable {
                return this
            }

            companion object {
                const val serialVersionUID = 590681756L
            }
        }
    }

    class ReliableFrameHandling : ChannelInitializer<Channel>() {
        override fun initChannel(channel: Channel) {
            channel.pipeline()
                .addLast(ReliabilityHandler.NAME, ReliabilityHandler())
                .addLast(FrameJoiner.NAME, FrameJoiner())
                .addLast(FrameSplitter.NAME, FrameSplitter())
                .addLast(FrameOrderIn.NAME, FrameOrderIn())
                .addLast(FrameOrderOut.NAME, FrameOrderOut())
                .addLast(FramedPacketCodec.NAME, FramedPacketCodec())
        }

        companion object {
            val INSTANCE = ReliableFrameHandling()
        }
    }

    class PacketHandling : ChannelInitializer<Channel>() {
        override fun initChannel(channel: Channel) {
            channel.pipeline()
                .addLast(DisconnectHandler.NAME, DisconnectHandler.INSTANCE)
                .addLast(PingHandler.NAME, PingHandler.INSTANCE)
                .addLast(PongHandler.NAME, PongHandler.INSTANCE)
        }

        companion object {
            val INSTANCE = PacketHandling()
        }
    }
}

val WRITABLE = AttributeKey.valueOf<Boolean>("RN_WRITABLE")
val SERVER_ID = ChannelOption.valueOf<Long>("RN_SERVER_ID")
val CLIENT_ID = ChannelOption.valueOf<Long>("RN_CLIENT_ID")
val METRICS: ChannelOption<RakNet.MetricsLogger> = ChannelOption.valueOf("RN_METRICS")
val MTU = ChannelOption.valueOf<Int>("RN_MTU")
val RTT = ChannelOption.valueOf<Long>("RN_RTT")
val PROTOCOL_VERSION = ChannelOption.valueOf<Int>("RN_PROTOCOL_VERSION")
val MAGIC: ChannelOption<RakNet.Magic> = ChannelOption.valueOf("RN_MAGIC")
val RETRY_DELAY_NANOS = ChannelOption.valueOf<Long>("RN_RETRY_DELAY_NANOS")
val MAX_CONNECTIONS = ChannelOption.valueOf<Int>("RN_MAX_CONNECTIONS")