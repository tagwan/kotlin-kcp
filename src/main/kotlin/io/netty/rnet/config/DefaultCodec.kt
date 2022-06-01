package io.netty.rnet.config

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import io.netty.rnet.RakNet
import io.netty.rnet.frame.FrameData
import io.netty.rnet.packet.*
import io.netty.rnet.packet.Reliability.ACK
import io.netty.rnet.packet.Reliability.NACK
import java.util.function.BiConsumer
import java.util.function.Function
import java.util.function.Supplier

class DefaultCodec : RakNet.Codec {

    protected val decoders = Int2ObjectOpenHashMap<Function<ByteBuf, out Packet>>()
    protected val encoders = Int2ObjectOpenHashMap<BiConsumer<out Packet, ByteBuf>>()
    protected val idFromClass = Object2IntOpenHashMap<Class<*>>()
    protected val framedPacketIds = IntOpenHashSet()

    init {
        //  ID                              Decoder                     Encoder
        register(PING,                      ::Ping)
        register(UNCONNECTED_PING,          ::UnconnectedPing)
        register(PONG,                      ::Pong)
        register(OPEN_CONNECTION_REQUEST_1, ::ConnectionRequest1)
        register(OPEN_CONNECTION_REPLY_1,   ::ConnectionReply1)
        register(OPEN_CONNECTION_REQUEST_2, ::ConnectionRequest2)
        register(OPEN_CONNECTION_REPLY_2,   ::ConnectionReply2)
        register(CONNECTION_REQUEST,        ::ConnectionRequest)
        register(SERVER_HANDSHAKE,          ::ServerHandshake)
        register(CONNECTION_FAILED,         ::ConnectionFailed)
        register(ALREADY_CONNECTED,         ::AlreadyConnected)
        register(CLIENT_HANDSHAKE,          ::ClientHandshake)
        register(NO_FREE_CONNECTIONS,       ::NoFreeConnections)
        register(CLIENT_DISCONNECT,         ::Disconnect)
        register(CONNECTION_BANNED,         ::ConnectionBanned)
        register(INVALID_VERSION,           ::InvalidVersion)
        register(UNCONNECTED_PONG,          ::UnconnectedPong)

        for (i in FRAME_DATA_START..FRAME_DATA_END)
            register(i,                     FrameSet::read,         FrameSet::write)

        register(NACK,                      ::NACK)
        register(ACK,                       ::ACK)
        idFromClass.defaultReturnValue(-1)
    }

    override fun encode(packet: FramedPacket, alloc: ByteBufAllocator): FrameData {
        if (packet is FrameData) {
            return packet.retain()
        }
        val out = alloc.ioBuffer(packet.sizeHint())
        return try {
            encode(packet, out)
            val frameData = FrameData.read(out, out.readableBytes(), false)
            frameData.reliability = packet.reliability
            frameData.orderChannel = packet.orderChannel
            frameData
        } finally {
            out.release()
        }
    }

    override fun encode(packet: Packet, out: ByteBuf) {
        require(idFromClass.containsKey(packet.javaClass)) { "Unknown encoder for " + packet.javaClass }
        val packetId = packetIdFor(packet.javaClass)
        val encoder = encoders[packetId] as BiConsumer<Packet?, ByteBuf?>
        encoder.accept(packet, out)
    }

    override fun produceEncoded(packet: Packet?, alloc: ByteBufAllocator?): ByteBuf? {
        if (packet is FrameSet && packet.roughSize >= 128) {
            return packet.produce(alloc!!)
        }
        val buf = alloc!!.ioBuffer(packet!!.sizeHint())
        return try {
            encode(packet, buf)
            buf.retain()
        } finally {
            buf.release()
        }
    }

    override fun decode(buf: ByteBuf?): Packet {
        val packetId = buf!!.getUnsignedByte(buf.readerIndex()).toInt()
        val decoder = decoders[packetId] ?: throw IllegalArgumentException("Unknown decoder for packet ID $packetId")
        return decoder.apply(buf)
    }

    override fun decode(data: FrameData?): FramedPacket? {
        val packetId = data!!.packetId
        val decoder = decoders[packetId]
        if (decoder == null || !framedPacketIds.contains(packetId)) {
            return data.retain()
        }
        val buf = requireNotNull(data.createData())
        return try {
            val out = decoder.apply(buf) as FramedPacket
            out.reliability = data.reliability
            out.orderChannel = data.orderChannel
            out
        } finally {
            buf.release()
        }
    }

    override fun packetIdFor(type: Class<out Packet?>?): Int {
        return idFromClass.getInt(type)
    }

    ///// REGISTRY /////
    protected inline fun <reified T : SimplePacket> register(id: Int, cons: Supplier<T>) {
        this.register(id, decodeSimple(cons), encodeSimple(id))
    }

    protected inline fun <reified T : Packet> register(
        id: Int, decoder: Function<ByteBuf, T>, encoder: BiConsumer<T, ByteBuf>
    ) {
        idFromClass.put(T::class.java, id)
        decoders.put(id, decoder)
        encoders.put(id, encoder)
        if (FramedPacket::class.java.isAssignableFrom(T::class.java)) {
            framedPacketIds.add(id)
        }
    }

    protected fun <T : SimplePacket> decodeSimple(cons: Supplier<T>): Function<ByteBuf, T> {
        return Function { buf ->
            val inst = cons.get()
            buf.skipBytes(1)
            inst.decode(buf)
            inst
        }
    }

    protected fun <T : SimplePacket> encodeSimple(id: Int): BiConsumer<T, ByteBuf> {
        return BiConsumer { packet: T, buf: ByteBuf ->
            buf.writeByte(id)
            packet.encode(buf)
        }
    }

    companion object {
        val INSTANCE = DefaultCodec()
    }
}

const val PING = 0x00
const val UNCONNECTED_PING = 0x01
const val PONG = 0x03
const val OPEN_CONNECTION_REQUEST_1 = 0x05
const val OPEN_CONNECTION_REPLY_1 = 0x06
const val OPEN_CONNECTION_REQUEST_2 = 0x07
const val OPEN_CONNECTION_REPLY_2 = 0x08
const val CONNECTION_REQUEST = 0x09
const val SND_RECEIPT_ACKED = 0x0E
const val SND_RECEIPT_LOSS = 0x0F
const val SERVER_HANDSHAKE = 0x10
const val CONNECTION_FAILED = 0x11
const val ALREADY_CONNECTED = 0x12
const val CLIENT_HANDSHAKE = 0x13
const val NO_FREE_CONNECTIONS = 0x14
const val CLIENT_DISCONNECT = 0x15
const val CONNECTION_BANNED = 0x17
const val INVALID_VERSION = 0x19
const val UNCONNECTED_PONG = 0x1C
const val FRAME_DATA_START = 0x80
const val FRAME_DATA_END = 0x8F
const val NACK = 0xA0
const val ACK = 0xC0

enum class CodecType(
    val v: Int
) {
    PING(0x00),
    UNCONNECTED_PING(0x01),
    PONG(0x03),
    OPEN_CONNECTION_REQUEST_1(0x05),
    OPEN_CONNECTION_REPLY_1(0x06),
    OPEN_CONNECTION_REQUEST_2(0x07),
    OPEN_CONNECTION_REPLY_2(0x08),
    CONNECTION_REQUEST(0x09),
    SND_RECEIPT_ACKED(0x0E),
    SND_RECEIPT_LOSS(0x0F),
    SERVER_HANDSHAKE(0x10),
    CONNECTION_FAILED(0x11),
    ALREADY_CONNECTED(0x12),
    CLIENT_HANDSHAKE(0x13),
    NO_FREE_CONNECTIONS(0x14),
    CLIENT_DISCONNECT(0x15),
    CONNECTION_BANNED(0x17),
    INVALID_VERSION(0x19),
    UNCONNECTED_PONG(0x1C),
    FRAME_DATA_START(0x80),
    FRAME_DATA_END(0x8F),
    NACK(0xA0),
    ACK(0xC0),
    ;
}