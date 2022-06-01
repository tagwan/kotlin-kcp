package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderException
import io.netty.rnet.RakNet.Magic
import io.netty.rnet.config.DefaultMagic

class InvalidVersion : SimplePacket, Packet {
    var magic: Magic? = null
    var version = 0
    var serverId: Long = 0

    constructor() {}
    constructor(magic: Magic?, serverId: Long) {
        this.magic = magic
        this.serverId = serverId
    }

    override fun encode(buf: ByteBuf) {
        buf.writeByte(version)
        magic!!.write(buf)
        buf.writeLong(serverId)
    }

    override fun decode(buf: ByteBuf) {
        version = buf.readUnsignedByte().toInt()
        magic = DefaultMagic.decode(buf)
        serverId = buf.readLong()
    }

    object InvalidVersionException : DecoderException() {
        const val serialVersionUID = 590681756L
    }
}