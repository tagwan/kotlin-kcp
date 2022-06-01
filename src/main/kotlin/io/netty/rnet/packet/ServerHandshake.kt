package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import java.net.InetSocketAddress

class ServerHandshake() : SimpleFramedPacket() {
    var clientAddr: InetSocketAddress? = null
    var timestamp: Long = 0
    private var nExtraAddresses = 0

    init {
        reliability = ReliabilityType.RELIABLE
    }

    @JvmOverloads
    constructor(clientAddr: InetSocketAddress?, timestamp: Long, nExtraAddresses: Int = 20) : this() {
        this.clientAddr = clientAddr
        this.timestamp = timestamp
        this.nExtraAddresses = nExtraAddresses
    }

    override fun encode(buf: ByteBuf) {
        writeAddress(buf, clientAddr!!)
        buf.writeShort(0)
        for (i in 0 until nExtraAddresses) {
            writeAddress(buf)
        }
        buf.writeLong(timestamp)
        buf.writeLong(System.currentTimeMillis())
    }

    override fun decode(buf: ByteBuf) {
        clientAddr = readAddress(buf)
        buf.readShort()
        nExtraAddresses = 0
        while (buf.readableBytes() > 16) {
            readAddress(buf)
            nExtraAddresses++
        }
        timestamp = buf.readLong()
        timestamp = buf.readLong()
    }

    fun getnExtraAddresses(): Int {
        return nExtraAddresses
    }

    fun setnExtraAddresses(nExtraAddresses: Int) {
        this.nExtraAddresses = nExtraAddresses
    }
}