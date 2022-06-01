package io.netty.rnet.packet

import io.netty.buffer.ByteBuf

class Pong(
    private var pingTimestamp: Long = 0L,
    private var pongTimestamp: Long = System.nanoTime(),
    override var reliability: ReliabilityType? = ReliabilityType.UNRELIABLE
) : SimpleFramedPacket() {

    val rTT: Long
        get() = System.nanoTime() - pingTimestamp

    override fun encode(buf: ByteBuf) {
        buf.writeLong(pingTimestamp)
        buf.writeLong(pongTimestamp)
    }

    override fun decode(buf: ByteBuf) {
        pingTimestamp = buf.readLong()
        if (buf.isReadable) {
            pongTimestamp = buf.readLong()
        }
    }
}