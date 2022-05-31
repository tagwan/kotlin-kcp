package io.netty.kcp.config

import io.netty.kcp.cfg.*
import io.netty.kcp.core.Kcp
import io.netty.util.internal.ObjectUtil
import java.util.*

/**
 * @author jdg
 */
class DefaultKcpConfig(
    private val kcp: Kcp
) : KcpConfig {
    private var nodelay: Int = 0
    private var interval: Int = 0
    private var resend: Int = 0
    private var nc: Int = 0
    private var sndwnd: Int = 0
    private var rcvwnd: Int = 0
    private var mtu: Int = 0
    private var minrto: Int = 0

    override fun getOptions(): Map<KcpOption<*>, Any> {
        return this.getOptions(
            null,
            KCP_NODELAY, KCP_INTERVAL, KCP_RESEND,
            KCP_NOCWND, KCP_MIN_RTO,
            KCP_MTU, KCP_RCV_WND, KCP_SND_WND
        )
    }

    override fun configureProtocol(optionMap: Map<KcpOption<*>, *>) {
        this.setOptions(optionMap)
        kcp.noDelay(nodelay, interval, resend, nc)
        kcp.wndSize(sndwnd, rcvwnd)
        kcp.setMtu(mtu)
        kcp.setMinRto(minrto)
    }

    protected fun getOptions(
        resultParam: MutableMap<KcpOption<*>, Any>?,
        vararg options: KcpOption<*>
    ): Map<KcpOption<*>, Any> {
        var result = resultParam
        if (result == null) {
            result = IdentityHashMap()
        }
        for (o in options) {
            result[o] = getOption(o) as Any
        }
        return result
    }

    override fun setOptions(options: Map<KcpOption<*>, *>): Boolean {
        ObjectUtil.checkNotNull(options, "options")
        var setAllOptions = true
        for ((key, value) in options) {
            if (value == null) {
                continue
            }
            if (!setOption(key as KcpOption<Any>, value)) {
                setAllOptions = false
            }
        }
        return setAllOptions
    }

    override fun <T> setOption(option: KcpOption<T>, value: T): Boolean {
        this.validate(option, value)
        if (option === KCP_NODELAY) {
            nodelay = value as Int
        } else if (option === KCP_INTERVAL) {
            interval = value as Int
        } else if (option === KCP_RESEND) {
            resend = value as Int
        } else if (option === KCP_NOCWND) {
            nc = value as Int
        } else if (option === KCP_MIN_RTO) {
            minrto = value as Int
        } else if (option === KCP_MTU) {
            mtu = value as Int
        } else if (option === KCP_RCV_WND) {
            rcvwnd = value as Int
        } else if (option === KCP_SND_WND) {
            sndwnd = value as Int
        } else {
            return false
        }
        return true
    }

    override fun <T> getOption(option: KcpOption<T>): T? {
        return null
    }

    private fun <T> validate(option: KcpOption<T>?, value: T?) {
        requireNotNull(option) { "option" }
        requireNotNull(value) { "value" }
    }
}