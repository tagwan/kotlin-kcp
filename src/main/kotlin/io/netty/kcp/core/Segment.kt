package io.netty.kcp.core

import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator

/**
 * @author jdg
 */
class Segment(size: Int): java.io.Serializable {
    var conv = 0        // conv为一个表示会话编号的整数, 通信双方需保证 conv相同
    var cmd: Byte = 0   // cmd，用来获取区分分片的作用
    var frg = 0         // 用户数据可能会被分成多个KCP包发送，frag标识segment分片ID（在message中的索引，由大到小，0表示最后一个分片）
    var wnd = 0         // 剩余接收窗口大小（接收窗口大小-接收队列大小），发送方的发送窗口不能超过接收方给出的数值
    var ts = 0          // 发送数据时刻的时间戳
    var sn = 0          // message分片segment的序号，按1累次递增
    var una = 0         // 接收方在发送应答的时候，我们告诉对方，我们已经收到了rcv_nxt之前的数据，所以为待接收消息序号(接收滑动窗口左端);对于发送端，表示我们缓存的窗口数据最左侧的数据包,即缓存的最小数据包序号
    var resendts = 0    // 下次超时重传的时间戳
    var rto = 0
    var fastack = 0     // 收到ack时计算的该分片被跳过的累计次数，此字段用于快速重传，自定义需要几次确认开始快速重传
    var xmit = 0        // 发送分片的次数，每发送一次加一。发送的次数对RTO的计算有影响，但是比TCP来说，影响会小一些，计算思想类似
    var data: ByteBuf? = null

    init {
        if (size > 0) {
            data = PooledByteBufAllocator.DEFAULT.buffer(size)
        }
    }

    /**
     * encode a segment into buffer
     */
    fun encode(buf: ByteBuf): Int {
        val off = buf.writerIndex()
        buf.writeIntLE(conv)
        buf.writeByte(cmd.toInt())
        buf.writeByte(frg)
        buf.writeShortLE(wnd)
        buf.writeIntLE(ts)
        buf.writeIntLE(sn)
        buf.writeIntLE(una)
        buf.writeIntLE(data?.readableBytes() ?: 0)
        return buf.writerIndex() - off
    }

    /**
     * 释放内存
     */
    fun release() {
        if (data != null && data!!.refCnt() > 0) {
            data!!.release(data!!.refCnt())
        }
    }
}