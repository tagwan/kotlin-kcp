package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import io.netty.rnet.RakNet.Magic
import io.netty.rnet.config.DefaultMagic

class UnconnectedPing : SimplePacket(), Packet {
    var magic: Magic? = null
    var clientTime: Long = 0
    var clientId: Long = 0

    override fun encode(buf: ByteBuf) {
        buf.writeLong(clientTime)
        magic!!.write(buf)
        buf.writeLong(clientId)
    }

    override fun decode(buf: ByteBuf) {
        clientTime = buf.readLong()
        magic = DefaultMagic.decode(buf)
        clientId = buf.readLong()
    }
}