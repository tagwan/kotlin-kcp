package io.netty.rnet.pipeline

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.CodecException
import io.netty.handler.codec.CorruptedFrameException
import io.netty.util.ReferenceCountUtil
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntRBTreeSet
import it.unimi.dsi.fastutil.ints.IntSortedSet
import it.unimi.dsi.fastutil.objects.ObjectIterator
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet
import io.netty.rnet.RakNet
import io.netty.rnet.RakNet.config
import io.netty.rnet.WRITABLE
import io.netty.rnet.frame.Frame
import io.netty.rnet.packet.FrameSet
import io.netty.rnet.packet.Reliability.*
import io.netty.rnet.pipeline.FlushTickHandler.MissedFlushes
import io.netty.rnet.utils.Constants.packetLossCheck
import io.netty.rnet.utils.UINT
import io.netty.rnet.utils.UINT.B3.minusWrap
import io.netty.rnet.utils.UINT.B3.plus
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min

/**
 * This handler handles the bulk of reliable (framed) transport.
 */
class ReliabilityHandler : ChannelDuplexHandler() {
    protected val nackSet: IntSortedSet = IntRBTreeSet(UINT.B3.COMPARATOR)
    protected val ackSet: IntSortedSet = IntRBTreeSet(UINT.B3.COMPARATOR)
    protected val frameQueue = ObjectRBTreeSet(Frame.COMPARATOR)
    protected val pendingFrameSets: Int2ObjectMap<FrameSet> = Int2ObjectOpenHashMap()
    protected var lastReceivedSeqId = 0
    protected var nextSendSeqId = 0
    protected var resendGauge = 0
    protected var burstTokens = 0
    protected var config: RakNet.Config? = null //TODO: not really needed anymore
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        config = config(ctx)
        ctx.channel().attr(WRITABLE).set(true)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        clearQueue(null)
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is Frame) {
            val frame = msg
            queueFrame(frame)
            frame.promise = promise
        } else {
            ctx.write(msg, promise)
        }
        packetLossCheck(pendingFrameSets.size, "unconfirmed sent packets")
        FlushTickHandler.checkFlushTick(ctx.channel())
    }

    override fun flush(ctx: ChannelHandlerContext) {
        if (!ctx.channel().isOpen) {
            ctx.flush()
            return
        }
        //all data sent in order of priority
        sendResponses(ctx)
        recallExpiredFrameSets()
        updateBurstTokens(1)
        produceFrameSets(ctx)
        updateBackPressure(ctx)
        packetLossCheck(pendingFrameSets.size, "resend queue")
        ctx.flush()
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        //missed some flush ticks, lets catch up on a few things
        if (evt is MissedFlushes) {
            updateBurstTokens(evt.nFlushes)
        }
        ctx.fireUserEventTriggered(evt)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        try {
            if (msg is ACK) {
                readAck(msg)
            } else if (msg is NACK) {
                readNack(msg)
            } else if (msg is FrameSet) {
                readFrameSet(ctx, msg)
            } else {
                ctx.fireChannelRead(ReferenceCountUtil.retain(msg))
            }
        } finally {
            ReferenceCountUtil.release(msg)
        }
    }

    protected fun clearQueue(t: Throwable?) {
        if (t != null) {
            frameQueue.forEach(Consumer { frame: Frame? ->
                if (frame!!.promise != null) {
                    frame.promise!!.tryFailure(t)
                }
            })
            pendingFrameSets.values.forEach(Consumer { set: FrameSet -> set.fail(t) })
        }
        frameQueue.forEach(Consumer { obj: Frame? -> obj!!.release() })
        frameQueue.clear()
        pendingFrameSets.values.forEach(Consumer { obj: FrameSet -> obj.release() })
        pendingFrameSets.clear()
    }

    protected fun readFrameSet(ctx: ChannelHandlerContext, frameSet: FrameSet) {
        val packetSeqId = frameSet.seqId
        ackSet.add(packetSeqId)
        nackSet.remove(packetSeqId)
        if (minusWrap(packetSeqId, lastReceivedSeqId) > 0) {
            lastReceivedSeqId = plus(lastReceivedSeqId, 1)
            while (lastReceivedSeqId != packetSeqId) { //nack any missed packets before this one
                nackSet.add(lastReceivedSeqId) //add missing packets to nack set
                lastReceivedSeqId = plus(lastReceivedSeqId, 1)
            }
        }
        config!!.metrics.packetsIn(1)
        config!!.metrics.framesIn(frameSet.numPackets)
        frameSet.createFrames { msg: Frame? ->
            ctx.fireChannelRead(
                msg
            )
        }
        ctx.fireChannelReadComplete()
    }

    protected fun readAck(ack: ACK) {
        var ackdBytes = 0
        var nIterations = 0
        for (entry in ack.entries) {
            val max = plus(entry!!.idFinish, 1)
            var id = entry.idStart
            while (id != max) {
                val frameSet = pendingFrameSets.remove(id)
                if (frameSet != null) {
                    ackdBytes += frameSet.roughSize
                    adjustResendGauge(1)
                    frameSet.succeed()
                    frameSet.release()
                }
                packetLossCheck(nIterations++, "ack confirm range")
                id = plus(id, 1)
            }
        }
        config!!.metrics.bytesACKd(ackdBytes)
    }

    protected fun readNack(nack: NACK) {
        var bytesNACKd = 0
        var nIterations = 0
        for (entry in nack.entries) {
            val max = plus(entry!!.idFinish, 1)
            var id = entry.idStart
            while (id != max) {
                val frameSet = pendingFrameSets.remove(id)
                if (frameSet != null) {
                    bytesNACKd += frameSet.roughSize
                    recallFrameSet(frameSet)
                }
                packetLossCheck(nIterations++, "nack confirm range")
                id = plus(id, 1)
            }
        }
        config?.metrics?.bytesNACKd(bytesNACKd)
    }

    protected fun queueFrame(frame: Frame) {
        val mtu = requireNotNull(config?.mTU)
        if (frame.roughPacketSize > mtu) {
            throw CorruptedFrameException(
                "Finished frame larger than the MTU by " + (frame.roughPacketSize - mtu)
            )
        }
        frameQueue.add(frame)
    }

    protected fun adjustResendGauge(n: Int) {
        //clamped gauge, can rebound more easily
        val defaultPendingFrameSets = requireNotNull(config?.defaultPendingFrameSets)
        resendGauge = max(
            -defaultPendingFrameSets,
            min(defaultPendingFrameSets, resendGauge + n)
        )
    }

    protected fun updateBurstTokens(nTicks: Int) {
        //gradual increment or decrement for burst tokens, unless unused
        val burstUnused: Boolean = pendingFrameSets.size < burstTokens / 2
        if (resendGauge > 1 && !burstUnused) {
            burstTokens += 1 * nTicks
        } else if (resendGauge < -1 || burstUnused) {
            burstTokens -= 3 * nTicks
        }
        burstTokens = max(min(burstTokens, config!!.maxPendingFrameSets), 0)
        config!!.metrics.measureBurstTokens(burstTokens)
    }

    protected fun sendResponses(ctx: ChannelHandlerContext) {
        if (!ackSet.isEmpty()) {
            ctx.write(ACK(ackSet)).addListener(RakNet.INTERNAL_WRITE_LISTENER)
            config!!.metrics.acksSent(ackSet.size)
            ackSet.clear()
        }
        if (!nackSet.isEmpty() && config!!.isAutoRead) { //only nack if we can read
            ctx.write(NACK(nackSet)).addListener(RakNet.INTERNAL_WRITE_LISTENER)
            config!!.metrics.nacksSent(nackSet.size)
            nackSet.clear()
        }
    }

    protected fun recallExpiredFrameSets() {
        val packetItr = pendingFrameSets.values.iterator()
        //2 sd from mean RTT is about 97% coverage
        val deadline = System.nanoTime() -
                (config!!.rTTNanos + 2 * config!!.rTTStdDevNanos + config!!.retryDelayNanos)
        while (packetItr.hasNext()) {
            val frameSet = packetItr.next()
            if (frameSet.sentTime < deadline) {
                packetItr.remove()
                recallFrameSet(frameSet)
            } else {
                //break; //TODO: FrameSets should be ordered by send time ultimately
            }
        }
    }

    protected fun produceFrameSet(ctx: ChannelHandlerContext, maxSize: Int) {
        val itr: ObjectIterator<Frame?> = frameQueue.iterator()
        val frameSet = FrameSet.create()
        while (itr.hasNext()) {
            val frame = itr.next()
            assert(frame!!.refCnt() > 0) { "Frame has lost reference" }
            if (frameSet.roughSize + frame.roughPacketSize > maxSize) {
                if (frameSet.isEmpty) {
                    throw CorruptedFrameException(
                        "Finished frame larger than the MTU by " + (frame.roughPacketSize - maxSize)
                    )
                }
                break
            }
            itr.remove()
            frameSet.addPacket(frame)
        }
        if (!frameSet.isEmpty) {
            frameSet.seqId = nextSendSeqId
            nextSendSeqId = plus(nextSendSeqId, 1)
            pendingFrameSets.put(frameSet.seqId, frameSet)
            frameSet.touch("Added to pending FrameSet list")
            ctx.write(frameSet.retain()).addListener(RakNet.INTERNAL_WRITE_LISTENER)
            config!!.metrics.packetsOut(1)
            config!!.metrics.framesOut(frameSet.numPackets)
            assert(frameSet.refCnt() > 0)
        } else {
            frameSet.release()
        }
    }

    protected fun produceFrameSets(ctx: ChannelHandlerContext) {
        val mtu = config!!.mTU
        val maxSize = mtu - FrameSet.HEADER_SIZE - Frame.HEADER_SIZE
        val maxPendingFrameSets = config!!.defaultPendingFrameSets + burstTokens
        while (pendingFrameSets.size < maxPendingFrameSets && !frameQueue.isEmpty()) {
            produceFrameSet(ctx, maxSize)
        }
    }

    //TODO: instead of immediate recall, mark framesets as 'recalled', and flush at flush cycle
    protected fun recallFrameSet(frameSet: FrameSet) {
        try {
            adjustResendGauge(-1)
            config!!.metrics.bytesRecalled(frameSet.roughSize)
            frameSet.touch("Recalled")
            frameSet.createFrames { frame: Frame? ->
                if (frame!!.reliability.isReliable) {
                    queueFrame(frame)
                } else {
                    frame.promise!!.trySuccess() //TODO: maybe need a fail here
                    frame.release()
                }
            }
        } finally {
            frameSet.release()
        }
    }

    protected fun updateBackPressure(ctx: ChannelHandlerContext) {
        val queuedBytes = queuedBytes
        val oldWritable = ctx.channel().attr(WRITABLE).get()
        var newWritable = oldWritable
        val cfg = requireNotNull(config)
        if (queuedBytes > cfg.maxQueuedBytes) {
            val t = CodecException("Frame queue is too large")
            clearQueue(t)
            ctx.close()
            throw t
        } else if (queuedBytes > cfg.writeBufferHighWaterMark) {
            newWritable = false
        } else if (queuedBytes < cfg.writeBufferLowWaterMark) {
            newWritable = true
        }
        if (newWritable != oldWritable) {
            ctx.channel()
                .attr(WRITABLE)
                .set(newWritable)
            ctx.fireChannelWritabilityChanged()
        }
    }

    protected val queuedBytes: Int
        protected get() {
            var byteSize = 0
            for (frame in frameQueue) {
                byteSize += frame!!.roughPacketSize
            }
            return byteSize
        }

    companion object {
        const val NAME = "rn-reliability"
    }
}