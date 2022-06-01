package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import io.netty.rnet.RakNet.Magic
import io.netty.rnet.config.DefaultMagic
import java.net.InetSocketAddress

class ConnectionReply2 : AbstractConnectionReply, Packet {
    var address: InetSocketAddress? = null

    constructor() {}
    constructor(magic: Magic?, mtu: Int, serverId: Long, address: InetSocketAddress?) : super(magic, mtu, serverId) {
        this.address = address
    }

    override fun encode(buf: ByteBuf) {
        magic?.write(buf)
        buf.writeLong(serverId)
        if (address == null) {
            writeAddress(buf)
        } else {
            writeAddress(buf, address!!)
        }
        buf.writeShort(mtu)
        buf.writeBoolean(NEEDS_SECURITY)
    }

    override fun decode(buf: ByteBuf) {
        magic = DefaultMagic.decode(buf)
        serverId = buf.readLong()
        address = readAddress(buf)
        mtu = buf.readShort().toInt()
        require(!buf.readBoolean()) {
            "No security support yet" //TODO: security i guess?
        }
    }
}