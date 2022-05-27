package io.netty.kcp.core

import io.netty.buffer.ByteBuf

/**
 * Kcp
 *
 * @data 2022/5/27 18:40
 */
interface Kcp: KcpGetter, KcpSetter {

    /**
     * user/upper level
     *
     * @param buffer
     * @return size, below zero for EAGAIN
     */
    fun receive(buffer: ByteBuf?): Int

    /**
     * user/upper level send
     *
     * @param buffer
     * @return below zero for error
     */
    fun send(buffer: ByteBuf?): Int

    /**
     * update state (call it repeatedly, every 10ms-100ms), or you can ask
     * ikcp_check when to call it again (without ikcp_input/_send calling).
     *
     * @param current current timestamp in millisec.
     */
    fun update(current: Long)

    /**
     * when you received a low level packet (eg. UDP packet), call it
     */
    fun input(data: ByteBuf?): Int

    /**
     * KCP的下层协议输出函数，KCP需要发送数据时会调用它
     */
    fun output(msg: ByteBuf?)
}


//=====================================================================
//
// KCP - A Better ARQ Protocol Implementation
// Features:
// + Average RTT reduce 30% - 40% vs traditional ARQ like tcp.
// + Maximum RTT reduce three times vs tcp.
// + Lightweight, distributed as a single source file.
//
//===================================================================================================================
//                              +--------------+
//                              |  Kcp::new |
//                              +--------------+
//                                     |
//                                    \|/
//                                     *
//     +------------------+     +------------------+     +-----------------------+     +-----------------+
//     |解包、更新确认发送包| <-- | Kcp::update(loop)| --> | 编包、发送 recv已确认包 | --> | 调用output,发送包|
//     +------------------+     +------------------+     +-----------------------+     +-----------------+
//              |                   /\         /\                                      /
//             \|/                  /           \                                     /
//              *                  /             \                      _____________/
//      +------------+       +------------+       +-----------+        /
//      | Kcp::recv  |       | Kcp::input |       | Kcp::send |  /____/
//      +------------+       +------------+       +-----------+  \
//             \                    /\                  /\
//              \                   U|                  /
//               \                  D|                 /
// kcp协议解包数据 \                 P|                /
//                 \               收|               /  发送应用数据
//                  \_________     数|       _______/
//                            \    据|      /
//                              +-----------+
//                              |   应用层   |
//                              +-----------+
//
//         数据接收逻辑                                    数据发送逻辑
//===================================================================================================================
