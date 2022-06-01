package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.EncoderException
import java.net.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

abstract class SimplePacket : Packet {
    abstract fun encode(buf: ByteBuf)
    abstract fun decode(buf: ByteBuf)

    override fun toString(): String {
        return javaClass.simpleName + "()"
    }

    companion object {
        fun writeString(buf: ByteBuf, str: String) {
            val bytes = str.toByteArray(StandardCharsets.UTF_8)
            buf.writeShort(bytes.size)
            buf.writeBytes(bytes)
        }

        fun readString(buf: ByteBuf): String {
            val bytes = ByteArray(buf.readShort().toInt())
            buf.readBytes(bytes)
            return String(bytes, StandardCharsets.UTF_8)
        }

        fun readAddress(buf: ByteBuf): InetSocketAddress {
            val type = buf.readByte().toInt()
            val addr: ByteArray
            val port: Int
            if (type == 4) {
                val addri = buf.readInt().inv()
                addr = ByteBuffer.allocate(4).putInt(addri).array()
                port = buf.readUnsignedShort()
            } else if (type == 6) {
                //sockaddr_in6 structure
                buf.skipBytes(2) //family
                port = buf.readUnsignedShort()
                buf.skipBytes(4) //flow info
                addr = ByteArray(16)
                buf.readBytes(addr)
                buf.skipBytes(4) //scope id
            } else {
                throw DecoderException("Unknown inet addr version: $type")
            }
            return try {
                InetSocketAddress(InetAddress.getByAddress(addr), port)
            } catch (e: UnknownHostException) {
                throw DecoderException("Unexpected error", e)
            }
        }

        @JvmOverloads
        fun writeAddress(buf: ByteBuf, address: InetSocketAddress = NULL_ADDR) {
            val addr = address.address
            if (addr is Inet4Address) {
                buf.writeByte(4.toByte().toInt())
                val addri = ByteBuffer.wrap(addr.getAddress()).int
                buf.writeInt(addri.inv())
                buf.writeShort(address.port)
            } else if (addr is Inet6Address) {
                buf.writeByte(6.toByte().toInt())
                //socaddr_in6 structure
                buf.writeShort(10) //family AF_INET6
                buf.writeShort(address.port)
                buf.writeInt(0) //flow info
                buf.writeBytes(addr.getAddress())
                buf.writeInt(0) //scope id
            } else {
                throw EncoderException("Unknown inet addr version: " + addr.javaClass.name)
            }
        }
    }
}

val NULL_ADDR = InetSocketAddress(0)