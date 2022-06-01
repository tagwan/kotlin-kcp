package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import io.netty.rnet.RakNet.Magic
import io.netty.rnet.config.DefaultMagic

class ConnectionReply1 : AbstractConnectionReply, Packet {
    constructor()
    constructor(magic: Magic?, mtu: Int, serverId: Long) : super(magic, mtu, serverId)

    override fun encode(buf: ByteBuf) {
        magic?.write(buf)
        buf.writeLong(serverId)
        buf.writeBoolean(NEEDS_SECURITY)
        buf.writeShort(mtu)
    }

    override fun decode(buf: ByteBuf) {
        magic = DefaultMagic.decode(buf)
        serverId = buf.readLong()
        require(!buf.readBoolean()) { "No security support yet" }
        mtu = buf.readShort().toInt()
    }
}