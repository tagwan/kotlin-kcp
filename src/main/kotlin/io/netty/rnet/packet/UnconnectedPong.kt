package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import io.netty.rnet.RakNet.Magic
import io.netty.rnet.config.DefaultMagic

class UnconnectedPong : SimplePacket, Packet {
    var clientTime = 0L
    var serverId = 0L
    var magic: Magic? = null
    var info = ""

    constructor()

    constructor(clientTime: Long, serverId: Long, magic: Magic, info: String) {
        this.clientTime = clientTime
        this.serverId = serverId
        this.magic = magic
        this.info = info
    }

    override fun encode(buf: ByteBuf) {
        buf.writeLong(clientTime)
        buf.writeLong(serverId)
        magic?.write(buf)
        writeString(buf, info)
    }

    override fun decode(buf: ByteBuf) {
        clientTime = buf.readLong()
        serverId = buf.readLong()
        magic = DefaultMagic.decode(buf)
        info = readString(buf)
    }
}