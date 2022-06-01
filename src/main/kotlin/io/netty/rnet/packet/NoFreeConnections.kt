package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import io.netty.rnet.RakNet.Magic
import io.netty.rnet.config.DefaultMagic

class NoFreeConnections : SimplePacket, Packet {
    var magic: Magic? = null
    var serverId: Long = 0

    constructor() {}
    constructor(magic: Magic?, serverId: Long) {
        this.magic = magic
        this.serverId = serverId
    }

    override fun encode(buf: ByteBuf) {
        magic!!.write(buf)
        buf.writeLong(serverId)
    }

    override fun decode(buf: ByteBuf) {
        magic = DefaultMagic.decode(buf)
        serverId = buf.readLong()
    }
}