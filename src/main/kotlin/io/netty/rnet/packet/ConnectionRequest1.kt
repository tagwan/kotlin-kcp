package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import io.netty.rnet.RakNet.Magic
import io.netty.rnet.config.DefaultMagic

class ConnectionRequest1 : SimplePacket, Packet {
    var magic: Magic? = null
    var protocolVersion = 0
    var mtu = 0

    constructor() {}
    constructor(magic: Magic?, protocolVersion: Int, mtu: Int) {
        this.magic = magic
        this.protocolVersion = protocolVersion
        this.mtu = mtu
    }

    override fun encode(buf: ByteBuf) {
        magic!!.write(buf)
        buf.writeByte(protocolVersion)
        buf.writeZero(mtu - buf.readableBytes())
    }

    override fun decode(buf: ByteBuf) {
        mtu = buf.readableBytes()
        magic = DefaultMagic.decode(buf)
        protocolVersion = buf.readByte().toInt()
        buf.skipBytes(buf.readableBytes())
        //near hard limit set by 13 bit size in Frame
        require(mtu >= 128) { "ConnectionRequest1 MTU is too small" }
        if (mtu > 8192) {
            mtu = 8192 //near hard limit set by 13 bit size in Frame
        }
    }
}