package io.netty.kcp.core

import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.socket.DatagramPacket
import java.net.InetSocketAddress

/**
 * kcp处理器
 *
 * @data 2022/5/27 18:56
 */
class KcpProcessor(
    conv: Int,
    private val channel: Channel,
    private val user: InetSocketAddress
) : AbstractKcp(conv) {

    override fun receive(buffer: ByteBuf?): Int {
        if (rcv_queue.isEmpty()) {
            return -1
        }
        val peekSize = peekSize()
        if (peekSize < 0) {
            return -2
        }
        val recover = rcv_queue.size >= rcv_wnd
        // merge fragment.
        var c = 0
        var len = 0
        for (seg in rcv_queue) {
            len += seg.data!!.readableBytes()
            buffer?.writeBytes(seg.data)
            c++
            if (seg.frg == 0) {
                break
            }
        }
        if (c > 0) {
            for (i in 0 until c) {
                rcv_queue.removeFirst().data!!.release()
            }
        }
        if (len != peekSize) {
            throw RuntimeException("数据异常.")
        }
        // move available data from rcv_buf -> rcv_queue
        c = 0
        for (seg in rcv_buf) {
            if (seg.sn == rcv_nxt && rcv_queue.size < rcv_wnd) {
                rcv_queue.add(seg)
                rcv_nxt++
                c++
            } else {
                break
            }
        }
        if (c > 0) {
            for (i in 0 until c) {
                rcv_buf.removeAt(0)
            }
        }
        // fast recover
        if (rcv_queue.size < rcv_wnd && recover) {
            // ready to send back IKCP_CMD_WINS in ikcp_flush
            // tell remote my window size
            probe = probe or IKCP_ASK_TELL
        }
        return len
    }

    override fun send(buffer: ByteBuf): Int {
        if (buffer.readableBytes() == 0) {
            return -1
        }
        // append to previous segment in streaming mode (if possible)
        if (stream && !snd_queue.isEmpty()) {
            val seg = snd_queue.last
            if (seg.data != null && seg.data!!.readableBytes() < mss) {
                val capacity = mss - seg.data!!.readableBytes()
                val extend = Math.min(buffer.readableBytes(), capacity)
                seg.data!!.writeBytes(buffer, extend)
                if (buffer.readableBytes() == 0) {
                    return 0
                }
            }
        }
        var count: Int
        // 根据mss大小分片
        count = if (buffer.readableBytes() <= mss) {
            1
        } else {
            (buffer.readableBytes() + mss - 1) / mss
        }
        if (count > 255) {
            return -2
        }
        if (count == 0) {
            count = 1
        }
        // fragment
        // 分片后加入到发送队列
        for (i in 0 until count) {
            // Math.min(buffer.readableBytes(), mss)
            val size = buffer.readableBytes().coerceAtMost(mss)
            val seg = Segment(size)
            seg.data?.writeBytes(buffer, size)
            seg.frg = if (stream) 0 else count - i - 1
            snd_queue.add(seg)
        }
        buffer.release()
        return 0
    }

    override fun update(current: Long) {
        this.current = current.toInt()

        // 首次调用Update
        if (updated == 0) {
            updated = 1
            ts_flush = this.current
        }

        // 两次更新间隔
        var slap = _itimediff(this.current, ts_flush)

        // interval设置过大或者Update调用间隔太久
        if (slap >= 10000 || slap < -10000) {
            ts_flush = this.current
            slap = 0
        }

        // flush同时设置下一次更新时间
        if (slap >= 0) {
            ts_flush += interval
            if (_itimediff(this.current, ts_flush) >= 0) {
                ts_flush = this.current + interval
            }
            this.flush()
        }
    }

    override fun input(data: ByteBuf?): Int {
        val una_temp = snd_una
        var flag = 0
        var maxack = 0
        if (data == null || data.readableBytes() < IKCP_OVERHEAD) {
            return -1
        }
        while (true) {
            var readed = false
            if (data.readableBytes() < IKCP_OVERHEAD) {
                break
            }
            val conv_: Int = data.readIntLE()
            if (conv != conv_) {
                return -1
            }
            val cmd: Byte = data.readByte() // cmd，用来获取区分分片的作用
            val frg: Byte = data.readByte() // 用户数据可能会被分成多个KCP包发送，frag标识segment分片ID（在message中的索引，由大到小，0表示最后一个分片）
            val wnd: Int = data.readShortLE().toInt() // 剩余接收窗口大小（接收窗口大小-接收队列大小），发送方的发送窗口不能超过接收方给出的数值
            val ts: Int = data.readIntLE() // 发送数据时刻的时间戳
            val sn: Int = data.readIntLE() // message分片segment的序号，按1累次递增。
            val una: Int = data.readIntLE() // 接收方在发送应答的时候，我们告诉对方，我们已经收到了rcv_nxt之前的数据，所以为待接收消息序号(接收滑动窗口左端);对于发送端，表示我们缓存的窗口数据最左侧的数据包,即缓存的最小数据包序号
            val len: Int = data.readIntLE() // 数据长度
            if (data.readableBytes() < len) {
                return -2
            }
            when (cmd) {
                IKCP_CMD_PUSH, IKCP_CMD_ACK, IKCP_CMD_WASK, IKCP_CMD_WINS -> {}
                else -> return -3
            }
            rmt_wnd = wnd and 0x0000ffff // 记录下对端的窗口大小
            parse_una(una) // 根据una删除snd_buf中的消息，即从队列中删除对方收到的消息
            shrink_buf() // 更新最新的kcp snd_una，即更新我们目前缓存中最小的缓存序号包为snd_una,snd_una之前的数据包，我们已经全部删除
            when (cmd) {
                IKCP_CMD_ACK -> {
                    if (_itimediff(current, ts) >= 0) {
                        // 根据两端的消息时间戳，我们来更新rrt,rto等信息
                        update_ack(_itimediff(current, ts))
                    }
                    // 根据ack中的sn来删除缓存队列对方确认收到的数据
                    parse_ack(sn)
                    shrink_buf() //更新最新的kcp snd_una
                    if (flag == 0) {
                        flag = 1
                        maxack = sn
                    } else if (_itimediff(sn, maxack) > 0) {
                        maxack = sn
                    }
                }
                IKCP_CMD_PUSH -> if (_itimediff(sn, rcv_nxt + rcv_wnd) < 0) {
                    // 将收到数据的sn,ts记录下来，放到acklist，等会一并发送应答
                    ack_push(sn, ts)
                    // 因为对于数据接收方,rcv_nxt表示我们接下来待接收的数据序号，所以只有需要>rcv_nxt的数据才能被接收，否则可以直接丢弃
                    if (_itimediff(sn, rcv_nxt) >= 0) {
                        val seg = Segment(len)
                        seg.conv = conv_
                        seg.cmd = cmd
                        seg.frg = frg.toInt() and 0x000000ff
                        seg.wnd = wnd
                        seg.ts = ts
                        seg.sn = sn
                        seg.una = una
                        if (len > 0) {
                            seg.data!!.writeBytes(data, len)
                            readed = true
                        }
                        // 将数据按序放入rcv_buf中
                        parse_data(seg)
                    }
                }
                IKCP_CMD_WASK ->                     // ready to send back IKCP_CMD_WINS in Ikcp_flush
                    // tell remote my window size
                    probe = probe or IKCP_ASK_TELL
                IKCP_CMD_WINS -> {}
                else -> return -3
            }
            if (!readed) {
                data.skipBytes(len)
            }
        }
        if (flag != 0) {
            parse_fastack(maxack)
        }
        // 网络比较好的时候，调整拥塞窗口大小的算法
        if (_itimediff(snd_una, una_temp) > 0) { // 如果我们发送数据缓存中最左侧的数据包序号>接收端确认的最左侧数据包序号
            if (cwnd < rmt_wnd) {  // 拥塞窗口大小<对端窗口大小
                if (cwnd < ssthresh) {  // 拥塞窗口阈值，以包为单位
                    cwnd++
                    incr += mss
                } else {
                    if (incr < mss) {
                        incr = mss
                    }
                    incr += mss * mss / incr + mss / 16
                    if ((cwnd + 1) * mss <= incr) {
                        cwnd++
                    }
                }
                if (cwnd > rmt_wnd) {
                    cwnd = rmt_wnd
                    incr = rmt_wnd * mss
                }
            }
        }
        return 0
    }

    override fun output(msg: ByteBuf?) {
        val dp = DatagramPacket(msg, user)
        channel.writeAndFlush(dp)
    }

    private fun flush() {
        val cur = current
        var change = 0
        var lost = 0
        val seg = Segment(0)
        seg.conv = conv
        seg.cmd = IKCP_CMD_ACK
        seg.wnd = wnd_unused()
        seg.una = rcv_nxt // 接收方在发送应答的时候，我们告诉对方，我们已经收到了rcv_nxt之前的数据
        // flush acknowledges
        var c = acklist.size / 2
        for (i in 0 until c) { // 如果是接收方的话,收到数据之后，就先发送ack告知发送方
            if (buffer.readableBytes() + IKCP_OVERHEAD > mtu) {
                output(buffer)
                buffer = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
            }
            // 从acklist中获取消息的sn和ts
            seg.sn = acklist[i * 2 + 0]
            seg.ts = acklist[i * 2 + 1]
            seg.encode(buffer)
        }
        acklist.clear()
        // probe window size (if remote window size equals zero)
        // rmt_wnd=0时，判断是否需要请求对端接收窗口
        if (rmt_wnd == 0) {
            if (probe_wait == 0) {
                probe_wait = IKCP_PROBE_INIT
                ts_probe = current + probe_wait
            } else if (_itimediff(current, ts_probe) >= 0) { // 逐步扩大请求时间间隔
                if (probe_wait < IKCP_PROBE_INIT) {
                    probe_wait = IKCP_PROBE_INIT
                }
                probe_wait += probe_wait / 2
                if (probe_wait > IKCP_PROBE_LIMIT) {
                    probe_wait = IKCP_PROBE_LIMIT
                }
                ts_probe = current + probe_wait
                probe = probe or IKCP_ASK_SEND
            }
        } else {
            ts_probe = 0
            probe_wait = 0
        }
        // flush window probing commands
        // 请求对端接收窗口
        if (probe and IKCP_ASK_SEND != 0) {
            seg.cmd = IKCP_CMD_WASK
            if (buffer.readableBytes() + IKCP_OVERHEAD > mtu) {
                output(buffer)
                buffer = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
            }
            seg.encode(buffer)
        }
        // flush window probing commands
        // 告诉对端自己的接收窗口
        if (probe and IKCP_ASK_TELL != 0) {
            seg.cmd = IKCP_CMD_WINS
            if (buffer.readableBytes() + IKCP_OVERHEAD > mtu) {
                output(buffer)
                buffer = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
            }
            seg.encode(buffer)
        }
        probe = 0
        // calculate window size,  Math.min(snd_wnd, rmt_wnd)
        var cwnd_temp = snd_wnd.coerceAtMost(rmt_wnd) //取发送窗口大小和对端接收窗口的最小值
        if (nocwnd == 0) { // 如果采用拥塞控制, Math.min(cwnd, cwnd_temp)
            cwnd_temp = cwnd.coerceAtMost(cwnd_temp)
        }
        // move data from snd_queue to snd_buf
        // 根据窗口大小，将snd_queue的数据移动到snd_buf中来
        c = 0
        for (item in snd_queue) {
            if (_itimediff(snd_nxt, snd_una + cwnd_temp) >= 0) {
                break
            }
            item.conv = conv
            item.cmd = IKCP_CMD_PUSH
            item.wnd = seg.wnd
            item.ts = cur
            item.sn = snd_nxt++
            item.una = rcv_nxt
            item.resendts = cur
            item.rto = rx_rto
            item.fastack = 0
            item.xmit = 0
            snd_buf.add(item)
            c++
        }
        if (c > 0) {
            for (i in 0 until c) {
                snd_queue.removeFirst()
            }
        }
        // calculate resent
        val resent = if (fastresend > 0) fastresend else Int.MAX_VALUE //计算触发快速重传的ack个数
        val rtomin = if (nodelay == 0) rx_rto shr 3 else 0

        // flush data segments
        // 发送snd_buf中的数据包
        for (segment in snd_buf) {
            var needsend = false
            if (segment.xmit == 0) { // 第一次传输
                needsend = true
                segment.xmit++
                segment.rto = rx_rto
                segment.resendts = cur + segment.rto + rtomin //计算超时重传时间
            } else if (_itimediff(cur, segment.resendts) >= 0) { //触发超时重传
                // 丢包重传
                needsend = true
                segment.xmit++
                xmit++
                if (nodelay == 0) {
                    segment.rto += rx_rto
                } else {
                    segment.rto += rx_rto / 2 // 正常情况下为1.5
                }
                segment.resendts = cur + segment.rto
                lost = 1
            } else if (segment.fastack >= resent) { //从ack知道被跳过了多少次，来确定这个消息是否需要重传
                needsend = true
                segment.xmit++
                segment.fastack = 0
                segment.resendts = cur + segment.rto
                change++
            }
            if (needsend) {
                segment.ts = cur
                segment.wnd = seg.wnd
                segment.una = rcv_nxt
                val need = IKCP_OVERHEAD + segment.data!!.readableBytes()
                if (buffer.readableBytes() + need > mtu) {
                    output(buffer)
                    buffer = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
                }
                segment.encode(buffer)
                if (segment.data!!.readableBytes() > 0) {
                    buffer.writeBytes(segment.data!!.duplicate())
                }
                if (segment.xmit >= dead_link) { //如果重传次数到达最大的次数瓶颈，就认为网络连接中断，将状态设置为-1
                    state = -1
                }
            }
        }
        // flash remain segments
        if (buffer.readableBytes() > 0) {
            output(buffer)
            buffer = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
        }

        // 即下次发送的时候，我们发送上次一半的数据量，来避免网络拥堵。
        // update ssthresh
        if (change != 0) { // 如果是因为ack被跳过一定次数认为的丢包的情况
            val inflight = snd_nxt - snd_una // 计算有多个消息数量在网络传输中
            ssthresh = inflight / 2 // 拥塞窗口大小阀值计算
            if (ssthresh < IKCP_THRESH_MIN) {
                ssthresh = IKCP_THRESH_MIN
            }
            cwnd = ssthresh + resent
            incr = cwnd * mss
        }

        // 超时丢包的情况，说明网络情况更加糟糕，需要进一步对拥塞窗口进行计算
        if (lost != 0) { // 数据发送超时，认为丢包的情况
            ssthresh = cwnd / 2
            if (ssthresh < IKCP_THRESH_MIN) {
                ssthresh = IKCP_THRESH_MIN
            }
            cwnd = 1
            incr = mss
        }
        if (cwnd < 1) {
            cwnd = 1
            incr = mss
        }
    }
}