package io.netty.kcp.core

/**
 * KcpGetter
 *
 * @data 2022/5/27 18:41
 */
interface IKcpGetter {
    //---------------------------------------------------------------------
    // Determine when should you invoke ikcp_update:
    // returns when you should invoke ikcp_update in millisec, if there
    // is no ikcp_input/_send calling. you can call ikcp_update in that
    // time, instead of call update repeatly.
    // Important to reduce unnacessary ikcp_update invoking. use it to
    // schedule ikcp_update (eg. implementing an epoll-like mechanism,
    // or optimize ikcp_update when handling massive kcp connections)
    //---------------------------------------------------------------------
    fun check(current: Long): Long

    /**
     * get how many packet is waiting to be sent
     */
    fun waitSnd(): Int

    /**
     * 接收窗口可用大小
     */
    fun wnd_unused(): Int
}
