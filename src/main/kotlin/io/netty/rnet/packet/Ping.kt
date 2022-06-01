package io.netty.rnet.packet

import io.netty.buffer.ByteBuf

class Ping : SimpleFramedPacket {
    var timestamp: Long
        protected set

    constructor() {
        timestamp = System.nanoTime()
        reliability = ReliabilityType.UNRELIABLE
    }

    constructor(timestamp: Long) : super() {
        this.timestamp = timestamp
    }

    override fun encode(buf: ByteBuf) {
        buf.writeLong(timestamp)
    }

    override fun decode(buf: ByteBuf) {
        timestamp = buf.readLong()
    }

    companion object {
        fun newReliablePing(): Ping {
            val out = Ping()
            out.reliability = ReliabilityType.RELIABLE
            return out
        }
    }
}