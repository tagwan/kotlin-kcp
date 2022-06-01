package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import java.net.InetSocketAddress

class ClientHandshake(
    var pongTimestamp: Long = 0L,
    private var address: InetSocketAddress = NULL_ADDR,
    var nExtraAddresses: Int = 0,
    var timestamp: Long = System.nanoTime()
) : SimpleFramedPacket() {

    init {
        reliability = ReliabilityType.RELIABLE_ORDERED
    }

    override fun encode(buf: ByteBuf) {
        writeAddress(buf, address)
        for (i in 0 until nExtraAddresses) {
            writeAddress(buf)
        }
        buf.writeLong(pongTimestamp)
        buf.writeLong(timestamp)
    }

    override fun decode(buf: ByteBuf) {
        address = readAddress(buf)
        nExtraAddresses = 0
        while (buf.readableBytes() > 16) {
            readAddress(buf)
            nExtraAddresses++
        }
        pongTimestamp = buf.readLong()
        timestamp = buf.readLong()
    }
}