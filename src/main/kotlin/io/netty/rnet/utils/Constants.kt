package io.netty.rnet.utils

import io.netty.handler.codec.DecoderException
import io.netty.util.internal.SystemPropertyUtil

fun checkPacketLoss(n: Int, location: () -> String) {
    if (n > MAX_PACKET_LOSS) {
        throw DecoderException("Too big packet loss: ${location()}")
    }
}

//TODO: get rid of this
val MAX_PACKET_LOSS = SystemPropertyUtil.getInt("raknetserver.maxPacketLoss", 8192)