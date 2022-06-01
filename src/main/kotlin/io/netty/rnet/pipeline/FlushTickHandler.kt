package io.netty.rnet.pipeline

import io.netty.channel.Channel
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * This handler produces an automatic flush cycle that is driven by the channel IO itself. The ping
 * produced in [AbstractConnectionInitializer] serves as a timed driver if no IO is present.
 * The channel write signal is driven by [ReliabilityHandler] using [ ][FlushTickHandler.checkFlushTick].
 */
class FlushTickHandler : ChannelDuplexHandler() {
    protected var tickAccum: Long = 0
    protected var lastTickAccum = System.nanoTime()
    protected var flushTask: ScheduledFuture<*>? = null
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        assert(flushTask == null)
        flushTask = ctx.channel().eventLoop().scheduleAtFixedRate(
            { checkFlushTick(ctx.channel()) },
            0, 50, TimeUnit.MILLISECONDS
        )
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        flushTask!!.cancel(false)
        flushTask = null
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.fireChannelReadComplete()
        maybeFlush(ctx.channel())
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt === FLUSH_CHECK_SIGNAL) {
            maybeFlush(ctx.channel())
        } else {
            ctx.fireUserEventTriggered(evt)
        }
    }

    override fun flush(ctx: ChannelHandlerContext) {
        if (tickAccum >= TICK_RESOLUTION) {
            tickAccum -= TICK_RESOLUTION
        } else {
            tickAccum = 0
        }
        ctx.flush()
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        maybeFlush(ctx.channel())
        ctx.fireChannelWritabilityChanged()
    }

    protected fun maybeFlush(channel: Channel) {
        val curTime = System.nanoTime()
        tickAccum += curTime - lastTickAccum
        lastTickAccum = curTime
        if (tickAccum >= TICK_RESOLUTION) {
            channel.flush()
            val nFlushes = (tickAccum / TICK_RESOLUTION).toInt()
            if (nFlushes > 0) {
                tickAccum -= nFlushes * TICK_RESOLUTION
                channel.pipeline().fireUserEventTriggered(MissedFlushes(nFlushes))
            }
        }
    }

    /** Fired from this handler to alert handlers of a missed flush tick.  */
    inner class MissedFlushes(val nFlushes: Int)
    companion object {
        const val NAME = "rn-flush-tick"
        val TICK_RESOLUTION = TimeUnit.NANOSECONDS.convert(5, TimeUnit.MILLISECONDS)

        /** Fired near the end of a pipeline to trigger a flush check.  */
        protected val FLUSH_CHECK_SIGNAL = Any()

        /** Helper method to trigger a flush check.  */
        fun checkFlushTick(channel: Channel) {
            channel.pipeline().fireUserEventTriggered(FLUSH_CHECK_SIGNAL)
        }
    }
}