package io.netty.rnet.packet

import io.netty.buffer.ByteBuf
import it.unimi.dsi.fastutil.ints.IntSortedSet
import io.netty.rnet.utils.UINT.B3.plus

open class Reliability : SimplePacket, Packet {
    lateinit var entries: Array<REntry?>

    private constructor() {}
    private constructor(ids: IntSortedSet) {
        entries = arrayOfNulls(0)
        if (ids.isEmpty()) {
            return
        }
        val res = ArrayList<REntry>()
        var startId = -1
        var endId = -1
        for (i in ids) {
            if (startId == -1) {
                startId = i //new region
                endId = i
            } else if (i == plus(endId, 1)) {
                endId = i //continue region
            } else {
                res.add(REntry(startId, endId))
                startId = i //new region
                endId = i
            }
        }
        res.add(REntry(startId, endId))
        entries = res.toArray(entries)
    }

    override fun encode(buf: ByteBuf) {
        buf.writeShort(entries.size)
        for (entry in entries) {
            if (entry == null)
                continue
            if (entry.idStart == entry.idFinish) {
                buf.writeBoolean(true)
                buf.writeMediumLE(entry.idStart)
            } else {
                buf.writeBoolean(false)
                buf.writeMediumLE(entry.idStart)
                buf.writeMediumLE(entry.idFinish)
            }
        }
    }

    override fun decode(buf: ByteBuf) {
        entries = arrayOfNulls(buf.readUnsignedShort())
        for (i in entries.indices) {
            val single = buf.readBoolean()
            if (single) {
                entries[i] = REntry(buf.readUnsignedMediumLE())
            } else {
                entries[i] = REntry(buf.readUnsignedMediumLE(), buf.readUnsignedMediumLE())
            }
        }
    }

    //TODO: iterator
    class REntry(val idStart: Int, val idFinish: Int) {
        constructor(id: Int) : this(id, id) {}
    }

    class ACK : Reliability {
        constructor() {}
        constructor(ids: IntSortedSet) : super(ids) {}
    }

    class NACK : Reliability {
        constructor() {}
        constructor(ids: IntSortedSet) : super(ids) {}
    }
}