package io.netty.rnet.packet

import io.netty.rnet.RakNet.Magic

abstract class AbstractConnectionReply(
    var magic: Magic? = null,
    var mtu: Int = 0,
    var serverId: Long = 0
) : SimplePacket()

const val NEEDS_SECURITY = false
