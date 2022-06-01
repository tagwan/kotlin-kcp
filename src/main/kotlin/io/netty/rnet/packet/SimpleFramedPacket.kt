package io.netty.rnet.packet

abstract class SimpleFramedPacket : SimplePacket(), FramedPacket {
    override var reliability: ReliabilityType? = ReliabilityType.RELIABLE_ORDERED
    override var orderChannel = 0
}