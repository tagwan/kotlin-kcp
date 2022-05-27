package io.netty.kcp.cfg


interface KcpConfig {
    /**
     * Return all set [KcpOption]'s.
     */
    val options: Map<Any?, Any?>?

    /**
     * Sets the configuration properties from the specified [Map].
     */
    fun setOptions(options: Map<KcpOption<*>?, *>?): Boolean

    /**
     * Return the value of the given [KcpOption]
     */
    fun <T> getOption(option: KcpOption<T>?): T
    fun <T> setOption(option: KcpOption<T>?, value: T): Boolean
    fun configureProtocol(optionMap: Map<KcpOption<*>?, *>?)
}