package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import io.netty.rnet.RakNet.Magic
import io.netty.rnet.config.DefaultMagic
import io.netty.rnet.packet.Packet.ClientIdConnection
import java.net.InetSocketAddress

class ConnectionRequest2 : SimplePacket, Packet, ClientIdConnection {
    var magic: Magic? = null
    var mtu = 0
    override var clientId: Long = 0
    var address: InetSocketAddress? = null

    constructor()
    constructor(
        magic: Magic?, mtu: Int, clientId: Long,
        address: InetSocketAddress?
    ) {
        this.magic = magic
        this.mtu = mtu
        this.clientId = clientId
        this.address = address
    }

    override fun encode(buf: ByteBuf) {
        magic?.write(buf)
        writeAddress(buf, address!!)
        buf.writeShort(mtu)
        buf.writeLong(clientId)
    }

    override fun decode(buf: ByteBuf) {
        magic = DefaultMagic.decode(buf)
        address = readAddress(buf)
        mtu = buf.readUnsignedShort()
        clientId = buf.readLong()
    }
}