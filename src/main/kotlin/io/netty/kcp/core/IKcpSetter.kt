package io.netty.kcp.core

/**
 * KcpSetter
 *
 * @data 2022/5/27 18:41
 */
interface IKcpSetter {
    /**
     * fastest: ikcp_nodelay(kcp, 1, 20, 2, 1)
     * nodelay: 0:disable(default), 1:enable
     * interval: internal update timer interval in millisec, default is 100ms
     * resend: 0:disable fast resend(default), 1:enable fast resend
     * nc: 0:normal congestion control(default), 1:disable congestion control
     */
    fun noDelay(nodelay: Int, interval: Int, resend: Int, nc: Int): Int

    /**
     * set maximum window size: sndwnd=32, rcvwnd=32 by default
     */
    fun wndSize(sndwnd: Int, rcvwnd: Int): Int
    fun interval(interval: Int): Int

    /**
     * change MTU size, default is 1400
     */
    fun setMtu(mtu: Int): Int
    fun setMinRto(min: Int)
}