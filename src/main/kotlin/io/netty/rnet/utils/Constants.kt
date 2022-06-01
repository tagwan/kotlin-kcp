package io.netty.rnet.utils

import io.netty.handler.codec.DecoderException
import io.netty.util.internal.SystemPropertyUtil

object Constants {

    //TODO: get rid of this
    val MAX_PACKET_LOSS = SystemPropertyUtil
        .getInt("raknetserver.maxPacketLoss", 8192)

    fun packetLossCheck(n: Int, location: String) {
        if (n > MAX_PACKET_LOSS) {
            throw DecoderException("Too big packet loss: $location")
        }
    }
}