package io.netty.rnet.packet

interface FramedPacket : Packet {
    var reliability: ReliabilityType?
    var orderChannel: Int
}

enum class ReliabilityType(
    val isReliable: Boolean,
    val isOrdered: Boolean,
    val isSequenced: Boolean,
    val isAckd: Boolean
) {
    //						  REL    ORD    SEQ    ACK
    UNRELIABLE(false, false, false, false), UNRELIABLE_SEQUENCED(false, true, true, false), RELIABLE(
        true,
        false,
        false,
        false
    ),
    RELIABLE_ORDERED(true, true, false, false), RELIABLE_SEQUENCED(true, true, true, false), UNRELIABLE_ACK(
        false,
        false,
        false,
        true
    ),
    RELIABLE_ACK(true, false, false, true), RELIABLE_ORDERED_ACK(true, true, false, true);

    fun code(): Int {
        return ordinal
    }

    fun makeReliable(): ReliabilityType {
        return if (isReliable) {
            this
        } else when (this) {
            UNRELIABLE -> RELIABLE
            UNRELIABLE_SEQUENCED -> RELIABLE_SEQUENCED
            UNRELIABLE_ACK -> RELIABLE_ACK
            else -> throw IllegalArgumentException("No reliable form of $this")
        }
    }

    companion object {
        operator fun get(code: Int): ReliabilityType {
            require(code >= 0 && code < values().size)
            return values()[code]
        }
    }
}