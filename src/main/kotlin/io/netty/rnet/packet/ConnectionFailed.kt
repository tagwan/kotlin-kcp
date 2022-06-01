package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import io.netty.rnet.RakNet.Magic
import io.netty.rnet.config.DefaultMagic

class ConnectionFailed(
    var magic: Magic? = null,
    var code: Long = 0
) : SimplePacket(), Packet {

    override fun encode(buf: ByteBuf) {
        magic?.write(buf)
        buf.writeLong(code)
    }

    override fun decode(buf: ByteBuf) {
        magic = DefaultMagic.decode(buf)
        code = buf.readLong()
    }
}