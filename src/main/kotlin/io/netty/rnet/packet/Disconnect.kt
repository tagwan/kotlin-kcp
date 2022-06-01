package io.netty.rnet.packet

import io.netty.buffer.ByteBuf

class Disconnect : SimpleFramedPacket() {
    init {
        reliability = ReliabilityType.RELIABLE
    }

    override fun encode(buf: ByteBuf) {
        // NOOP
    }

    override fun decode(buf: ByteBuf) {
        // NOOP
    }
}