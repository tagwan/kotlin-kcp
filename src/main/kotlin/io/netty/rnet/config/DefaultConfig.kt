package io.netty.rnet.config

import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.DefaultChannelConfig
import io.netty.rnet.RakNet
import io.netty.rnet.RakNet.Magic
import io.netty.rnet.RakNet.MetricsLogger
import io.netty.rnet.*
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max

open class DefaultConfig(channel: Channel?) : DefaultChannelConfig(channel), RakNet.Config {
    protected val rttStats = DescriptiveStatistics(16)

    //TODO: add rest of ChannelOptions
    @Volatile
    override var serverId = rnd.nextLong()

    @Volatile
    override var clientId = rnd.nextLong()

    @Volatile
    override var metrics = DEFAULT_METRICS

    @Volatile
    override var mTU = DEFAULT_MTU

    @Volatile
    override var retryDelayNanos = TimeUnit.NANOSECONDS.convert(15, TimeUnit.MILLISECONDS)

    @Volatile
    override var maxPendingFrameSets = 1024

    @Volatile
    override var defaultPendingFrameSets = 32

    @Volatile
    override var maxQueuedBytes = 3 * 1024 * 1024

    @Volatile
    override var magic = DEFAULT_MAGIC

    @Volatile
    override var codec: RakNet.Codec = DefaultCodec.INSTANCE

    @Volatile
    override var protocolVersions: IntArray? = intArrayOf(9, 10)

    @Volatile
    override var maxConnections = 2048

    @Volatile
    override var protocolVersion = 9

    override var rTTNanos: Long
        get() {
            val rtt = rttStats.mean.toLong()
            return max(rtt, 1)
        }
        set(rtt) {
            rttStats.clear()
            rttStats.addValue(rtt.toDouble())
        }

    init {
        rTTNanos = TimeUnit.NANOSECONDS.convert(100, TimeUnit.MILLISECONDS)
    }



    override fun getOptions(): Map<ChannelOption<*>, Any> {
        return getOptions(
            super.getOptions(),
            SERVER_ID, CLIENT_ID, METRICS, MTU,
            RTT, PROTOCOL_VERSION, MAGIC, RETRY_DELAY_NANOS
        )
    }

    override fun <T> getOption(option: ChannelOption<T>): T {
        if (option == SERVER_ID) {
            return serverId as T
        } else if (option == CLIENT_ID) {
            return clientId as T
        } else if (option == METRICS) {
            return metrics as T
        } else if (option == MTU) {
            return mTU as T
        } else if (option == RTT) {
            return rTTNanos as T
        } else if (option == PROTOCOL_VERSION) {
            return protocolVersion as T
        } else if (option == MAGIC) {
            return magic as T
        } else if (option == RETRY_DELAY_NANOS) {
            return retryDelayNanos as T
        } else if (option == MAX_CONNECTIONS) {
            return maxConnections as T
        }
        return super.getOption(option)
    }

    override fun <T> setOption(option: ChannelOption<T>, value: T): Boolean {
        if (option == SERVER_ID) {
            serverId = value as Long
        } else if (option == CLIENT_ID) {
            clientId = value as Long
        } else if (option === METRICS) {
            metrics = value as MetricsLogger
        } else if (option === MTU) {
            mTU = value as Int
        } else if (option === RTT) {
            rTTNanos = value as Long
        } else if (option === PROTOCOL_VERSION) {
            protocolVersion = value as Int
        } else if (option === MAGIC) {
            magic = value as Magic
        } else if (option === RETRY_DELAY_NANOS) {
            retryDelayNanos = value as Long
        } else if (option === MAX_CONNECTIONS) {
            maxConnections = value as Int
        } else {
            return super.setOption(option, value)
        }
        return true
    }

    override fun containsProtocolVersion(protocolVersion: Int): Boolean {
        for (version in protocolVersions!!) {
            if (version == protocolVersion) return true
        }
        return false
    }

    //ns
    override val rTTStdDevNanos: Long
        get() = rttStats.standardDeviation.toLong() //ns

    override fun updateRTTNanos(rttSample: Long) {
        rttStats.addValue(rttSample.toDouble())
        metrics.measureRTTns(rTTNanos)
        metrics.measureRTTnsStdDev(rTTStdDevNanos)
    }

    override fun setprotocolVersions(protocolVersions: IntArray?) {
        this.protocolVersions = protocolVersions
    }

    companion object {
        val DEFAULT_MAGIC: Magic = DefaultMagic(
            byteArrayOf(
                0x00.toByte(),
                0xff.toByte(),
                0xff.toByte(),
                0x00.toByte(),
                0xfe.toByte(),
                0xfe.toByte(),
                0xfe.toByte(),
                0xfe.toByte(),
                0xfd.toByte(),
                0xfd.toByte(),
                0xfd.toByte(),
                0xfd.toByte(),
                0x12.toByte(),
                0x34.toByte(),
                0x56.toByte(),
                0x78.toByte()
            )
        )
        const val DEFAULT_MTU = 8192
        private val DEFAULT_METRICS: MetricsLogger = object : MetricsLogger {}
        private val rnd = Random()
    }
}