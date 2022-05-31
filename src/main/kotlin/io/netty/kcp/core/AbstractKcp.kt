package io.netty.kcp.core

import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import java.util.*

/**
 * 初始化 kcp对象，conv为一个表示会话编号的整数，和tcp的 conv一样，通信双
 * 方需保证 conv相同，相互的数据包才能够被认可
 */
abstract class AbstractKcp(
    protected var conv: Int = 0 // 标识这个会话
) : Kcp {


    protected var mtu: Int          = 0 // 一个网络包最大的字节数。默认1400，最小50
    protected var mss: Int          = 0 // 最大分片大小
    protected var state: Int        = 0 // 链接状态（0xFFFFFFFF表示断开连接）
    protected var snd_una: Int      = 0 // 记录发送缓冲区snd_buf中第一个未确认的包
    protected var snd_nxt: Int      = 0 // 下一个待分配的包序号
    protected var rcv_nxt: Int      = 0 // 待接收消息序号。为了保证包的顺序，接收方会维护一个接收窗口，接收窗口有一个起始序号rcv_nxt（待接收消息序号）以及尾序号 rcv_nxt + rcv_wnd（接收窗口大小）；
    protected var ts_recent: Int    = 0
    protected var ts_lastack: Int   = 0
    protected var ssthresh: Int     = 0 // 拥塞窗口阈值，以包为单位（TCP以字节为单位
    protected var rx_rttval: Int    = 0 // RTT的变化量，代表连接的抖动情况；
    protected var rx_srtt: Int      = 0 // smoothed round trip time，平滑后的RTT(数据往返一周的毫秒时间)
    protected var rx_rto: Int       = 0 // 由ACK接收延迟计算出来的重传超时时间；
    protected var rx_minrto: Int    = 0
    protected var snd_wnd: Int      = 0
    protected var rcv_wnd: Int      = 0
    protected var rmt_wnd: Int      = 0 // 远端接收窗口大小
    protected var cwnd: Int         = 0 // 拥塞窗口大小
    protected var probe: Int        = 0 // 探查变量
    protected var current = 0
    protected var interval: Int     = 0 // 内部flush刷新间隔，对系统循环效率有非常重要影响
    protected var ts_flush: Int     = 0 // 下次flush刷新时间戳
    protected var xmit: Int         = 0 // 发送segment的次数，当segment的xmit增加时，xmit增加（第一次或重传除外）
    protected var nodelay: Int      = 0 // 是否启动无延迟模式。无延迟模式rtomin将设置为0，拥塞控制不启动；
    protected var updated           = 0
    protected var ts_probe: Int     = 0 // 下次探查窗口的时间戳
    protected var probe_wait: Int   = 0 // 探查窗口需要等待的时间
    protected var dead_link: Int    = 0 // 最大重传次数，被认为连接中断
    protected var incr: Int         = 0 // 可发送的最大数据量

    protected val snd_queue = ArrayDeque<Segment>()
    protected val rcv_queue = ArrayDeque<Segment>()
    protected val snd_buf = ArrayList<Segment>()
    protected val rcv_buf = ArrayList<Segment>()
    protected val acklist = ArrayList<Int>()
    protected var buffer: ByteBuf
    protected var fastresend: Int = 0 // 触发快速重传的重复ACK个数；
    protected var nocwnd: Int = 0 // 取消拥塞控制
    protected var stream:Boolean= false //流模式
    protected var nextUpdate: Long = 0L//the next update time.

    init {
        snd_wnd = IKCP_WND_SND
        rcv_wnd = IKCP_WND_RCV
        rmt_wnd = IKCP_WND_RCV
        mtu = IKCP_MTU_DEF
        mss = mtu - IKCP_OVERHEAD
        rx_rto = IKCP_RTO_DEF
        rx_minrto = IKCP_RTO_MIN
        interval = IKCP_INTERVAL
        ts_flush = IKCP_INTERVAL
        ssthresh = IKCP_THRESH_INIT
        dead_link = IKCP_DEADLINK
        buffer = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
    }

    /**
     * check the size of next message in the recv queue
     * 计算接收队列中有多少可用的数据
     *
     * @return
     */
    fun peekSize(): Int {
        if (rcv_queue.isEmpty()) {
            return -1
        }
        val seq = rcv_queue.first
        if (seq.frg == 0) {
            return seq.data!!.readableBytes()
        }
        if (rcv_queue.size < seq.frg + 1) {
            return -1
        }
        var length = 0
        for (item in rcv_queue) {
            length += item.data!!.readableBytes()
            if (item.frg == 0) {
                break
            }
        }
        return length
    }

    protected fun update_ack(rtt: Int) {
        if (rx_srtt == 0) {
            rx_srtt = rtt
            rx_rttval = rtt / 2
        } else {
            var delta = rtt - rx_srtt
            if (delta < 0) {
                delta = -delta
            }
            rx_rttval = (3 * rx_rttval + delta) / 4
            rx_srtt = (7 * rx_srtt + rtt) / 8
            if (rx_srtt < 1) {
                rx_srtt = 1
            }
        }
        val rto = rx_srtt + Math.max(interval, 4 * rx_rttval)
        rx_rto = _ibound_(rx_minrto, rto, IKCP_RTO_MAX)
    }

    /**
     * 计算本地真实snd_una
     */
    protected fun shrink_buf() {
        snd_una = if (snd_buf.size > 0) {
            snd_buf[0].sn
        } else {
            snd_nxt
        }
    }

    /**
     * 对端返回的ack, 确认发送成功时，对应包从发送缓存中移除
     *
     * @param sn
     */
    protected fun parse_ack(sn: Int) {
        if (_itimediff(sn, snd_una) < 0 || _itimediff(sn, snd_nxt) >= 0) {
            return
        }
        for (i in snd_buf.indices) {
            val seg = snd_buf[i]
            if (sn == seg.sn) {
                snd_buf.removeAt(i)
                seg.data!!.release(seg.data!!.refCnt())
                break
            }
            if (_itimediff(sn, seg.sn) < 0) {
                break
            }
        }
    }

    /**
     * 通过对端传回的una将已经确认发送成功包从发送缓存中移除
     *
     * @param una
     */
    protected fun parse_una(una: Int) {
        var c = 0
        for (seg in snd_buf) {
            if (_itimediff(una, seg.sn) > 0) {
                c++
            } else {
                break
            }
        }
        if (c > 0) {
            for (i in 0 until c) {
                val seg = snd_buf.removeAt(0)
                seg.data!!.release(seg.data!!.refCnt())
            }
        }
    }

    protected fun parse_fastack(sn: Int) {
        if (_itimediff(sn, snd_una) < 0 || _itimediff(sn, snd_nxt) >= 0) {
            return
        }
        for (seg in snd_buf) {
            if (_itimediff(sn, seg.sn) < 0) {
                break
            } else if (sn != seg.sn) {
                seg.fastack++
            }
        }
    }

    /**
     * ack append
     * 收数据包后需要给对端回ack，flush时发送出去
     *
     * @param sn
     * @param ts
     */
    protected fun ack_push(sn: Int, ts: Int) {
        acklist.add(sn)
        acklist.add(ts)
    }

    /**
     * 用户数据包解析
     *
     * @param newseg
     */
    protected fun parse_data(newseg: Segment) {
        val sn = newseg.sn
        if (_itimediff(sn, rcv_nxt + rcv_wnd) >= 0 || _itimediff(sn, rcv_nxt) < 0) {
            newseg.release()
            return
        }
        val n = rcv_buf.size - 1
        var temp = -1
        // 判断是否是重复包，并且计算插入位置
        var repeat = false
        for (i in n downTo 0) {
            val seg = rcv_buf[i]
            if (seg.sn == sn) {
                repeat = true
                break
            }
            if (_itimediff(sn, seg.sn) > 0) {
                temp = i
                break
            }
        }
        // 如果不是重复包，则插入
        if (!repeat) {
            rcv_buf.add(temp + 1, newseg)
        } else {
            newseg.release()
        }
        // move available data from rcv_buf -> rcv_queue
        // 将连续包加入到接收队列
        var count = 0
        for (seg in rcv_buf) {
            if (seg.sn == rcv_nxt && rcv_queue.size < rcv_wnd) {
                rcv_queue.add(seg)
                rcv_nxt++
                count++
            } else {
                break
            }
        }
        // 从接收缓存中移除
        if (0 < count) {
            for (i in 0 until count) {
                rcv_buf.removeAt(0)
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////
    override fun check(current: Long): Long {
        if (updated == 0) {
            return current
        }
        var ts_flush_temp = ts_flush.toLong()
        var tm_packet: Long = 0x7fffffff
        if (_itimediff(current, ts_flush_temp) >= 10000 || _itimediff(current, ts_flush_temp) < -10000) {
            ts_flush_temp = current
        }
        if (_itimediff(current, ts_flush_temp) >= 0) {
            return current
        }
        val tm_flush = _itimediff(ts_flush_temp, current)
        for (seg in snd_buf) {
            val diff = _itimediff(seg.resendts, current.toInt()).toLong()
            if (diff <= 0) {
                return current
            }
            if (diff < tm_packet) {
                tm_packet = diff
            }
        }
        var minimal = if (tm_packet < tm_flush) tm_packet else tm_flush
        if (minimal >= interval) {
            minimal = interval.toLong()
        }
        return current + minimal
    }

    override fun waitSnd(): Int {
        return snd_buf.size + snd_queue.size
    }

    override fun wnd_unused(): Int {
        return if (rcv_queue.size < rcv_wnd) {
            rcv_wnd - rcv_queue.size
        } else 0
    }

    override fun noDelay(nodelay: Int, itv: Int, resend: Int, nc: Int): Int {
        var interval = itv
        if (nodelay >= 0) {
            this.nodelay = nodelay
            rx_minrto = if (nodelay != 0) {
                IKCP_RTO_NDL
            } else {
                IKCP_RTO_MIN
            }
        }
        if (interval >= 0) {
            if (interval > 5000) {
                interval = 5000
            } else if (interval < 10) {
                interval = 10
            }
            this.interval = interval
        }
        if (resend >= 0) {
            fastresend = resend
        }
        if (nc >= 0) {
            nocwnd = nc
        }
        return 0
    }

    override fun wndSize(sndwnd: Int, rcvwnd: Int): Int {
        if (sndwnd > 0) {
            snd_wnd = sndwnd
        }
        if (rcvwnd > 0) {
            rcv_wnd = rcvwnd
        }
        return 0
    }

    override fun interval(itv: Int): Int {
        var interval = itv
        if (interval > 5000) {
            interval = 5000
        } else if (interval < 10) {
            interval = 10
        }
        this.interval = interval
        return 0
    }

    override fun setMtu(mtu: Int): Int {
        if (mtu < 50 || mtu < IKCP_OVERHEAD) {
            return -1
        }
        val buf = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
        this.mtu = mtu
        mss = mtu - IKCP_OVERHEAD
        buffer.release()
        buffer = buf
        return 0
    }

    override fun setMinRto(min: Int) {
        rx_minrto = min
    }

    override fun toString(): String {
        return """
            Kcp(conv=$conv)
            
            """.trimIndent()
    }

    /**
     * 释放内存
     */
    fun release() {
        if (buffer.refCnt() > 0) {
            buffer.release(buffer.refCnt())
        }
        for (seg in rcv_buf) {
            seg.release()
        }
        for (seg in rcv_queue) {
            seg.release()
        }
        for (seg in snd_buf) {
            seg.release()
        }
        for (seg in snd_queue) {
            seg.release()
        }
    }
}