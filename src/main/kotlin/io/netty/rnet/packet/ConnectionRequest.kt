package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import io.netty.rnet.packet.Packet.ClientIdConnection

class ConnectionRequest() : SimpleFramedPacket(), ClientIdConnection {
    override var clientId: Long = 0
    var timestamp: Long = 0

    init {
        reliability = ReliabilityType.RELIABLE
    }

    constructor(clientId: Long) : this() {
        this.clientId = clientId
        timestamp = System.nanoTime()
    }

    override fun encode(buf: ByteBuf) {
        buf.writeLong(clientId)
        buf.writeLong(timestamp)
        buf.writeBoolean(false)
    }

    override fun decode(buf: ByteBuf) {
        clientId = buf.readLong() //client id
        timestamp = buf.readLong()
        buf.readBoolean() //use security
    }
}