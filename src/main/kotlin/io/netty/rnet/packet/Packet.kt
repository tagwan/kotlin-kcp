package io.netty.rnet.packet

interface Packet {
    fun sizeHint(): Int {
        return 128
    }

    interface ClientIdConnection : Packet {
        val clientId: Long
    }
}