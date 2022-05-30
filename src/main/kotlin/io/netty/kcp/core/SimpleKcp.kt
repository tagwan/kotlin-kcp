package io.netty.kcp.core

import io.netty.buffer.ByteBuf

/**
 * SimpleKcp
 *
 * @data 2022/5/27 18:56
 */
class SimpleKcp : AbstractKcp(), Kcp {
    override fun receive(buffer: ByteBuf?): Int {
        return 0
    }

    override fun send(buffer: ByteBuf?): Int {
        return 0
    }

    override fun update(current: Long) {
        //pass
    }

    override fun input(data: ByteBuf?): Int {
        return 0
    }

    override fun output(msg: ByteBuf?) {
        //pass
    }
}