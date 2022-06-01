package io.netty.rnet.config

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.rnet.RakNet.Magic
import io.netty.rnet.RakNet.Magic.MagicMismatchException

class DefaultMagic(  //TODO: static cache?
    protected val magicData: ByteArray
) : Magic {
    override fun write(buf: ByteBuf?) {
        buf?.writeBytes(magicData)
    }

    override fun read(buf: ByteBuf?) {
        for (b in magicData) {
            if (buf?.readByte() != b) {
                throw MagicMismatchException()
            }
        }
    }

    override fun verify(other: Magic?) {
        val tmp = Unpooled.buffer(16)
        write(tmp)
        other?.read(tmp)
    }

    companion object {
        fun decode(buf: ByteBuf): DefaultMagic {
            val magicData = ByteArray(16)
            buf.readBytes(magicData)
            return DefaultMagic(magicData)
        }
    }
}