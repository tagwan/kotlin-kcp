package io.netty.kcp

import io.netty.kcp.cfg.KcpConfig
import io.netty.kcp.cfg.KcpOption
import io.netty.kcp.config.DefaultKcpConfig
import io.netty.kcp.core.Kcp
import io.netty.util.internal.ObjectUtil

/**
 * @author jdg
 */
class KcpBootstrap {
    private val options: MutableMap<KcpOption<*>, Any> = LinkedHashMap<KcpOption<*>, Any>()

    var kcp: Kcp? = null
    private var kcpConfig: KcpConfig? = null
    fun kcp(kcp: Kcp?): KcpBootstrap {
        this.kcp = kcp
        return self()
    }

    fun kcpConfig(kcpConfig: KcpConfig?): KcpBootstrap {
        this.kcpConfig = kcpConfig
        return self()
    }

    fun <T> option(option: KcpOption<T>, value: T?): KcpBootstrap {
        ObjectUtil.checkNotNull<Any>(option, "option")
        synchronized(options) {
            if (value == null) {
                options.remove(option)
            } else {
                options.put(option, value)
            }
        }
        return self()
    }

    private fun self(): KcpBootstrap {
        return this
    }

    /**
     * run
     */
    fun start() {
        validate()
        kcpConfig?.configureProtocol(options)
    }

    private fun validate() {
        ObjectUtil.checkNotNull(kcp, "kcp")
        if (kcpConfig == null) {
            kcpConfig = DefaultKcpConfig(kcp!!)
        }
        ObjectUtil.checkNotNull<Any?>(kcpConfig, "kcpConfig")
    }

}

// val EMPTY_OPTION_ARRAY: Array<Map.Entry<KcpOption<*>, Any>> = arrayOfNulls<Map.Entry<*, *>>(0)