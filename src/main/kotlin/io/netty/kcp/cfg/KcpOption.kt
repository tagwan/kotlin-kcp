package io.netty.kcp.cfg

import io.netty.util.AbstractConstant
import io.netty.util.ConstantPool


/**
 * @author jdg
 */
class KcpOption<T> private constructor(id: Int, name: String) :
    AbstractConstant<KcpOption<T>?>(id, name) {
    @Deprecated("")
    protected constructor(name: String) : this(pool.nextId(), name)

    fun validate(value: T) {
        requireNotNull(value)
    }

    companion object {
        private val pool: ConstantPool<KcpOption<Any>> = object : ConstantPool<KcpOption<Any>>() {
            override fun newConstant(id: Int, name: String): KcpOption<Any> {
                return KcpOption(id, name)
            }
        }

        /**
         * Returns the [KcpOption] of the specified name.
         */
        fun <T> valueOf(name: String?): KcpOption<T> {
            return pool.valueOf(name) as KcpOption<T>
        }
    }
}

// 是否启用 nodelay模式，0不启用；1启用
val KCP_NODELAY = KcpOption.valueOf<Int>("KCP_NODELAY")

// 协议内部工作的 interval，单位毫秒，比如 10ms或者 20ms
val KCP_INTERVAL = KcpOption.valueOf<Int>("KCP_INTERVAL")

// 快速重传模式，默认0关闭，可以设置2（2次ACK跨越将会直接重传）
val KCP_RESEND = KcpOption.valueOf<Int>("KCP_RESEND")
val KCP_NOCWND = KcpOption.valueOf<Int>("KCP_NOCWND")

// 最大接收窗口大小 默认为32. 这个可以理解为 TCP的 SND_BUF 和 RCV_BUF，只不过单位不一样 SND/RCV_BUF 单位是字节，这个单位是包。
val KCP_RCV_WND = KcpOption.valueOf<Int>("KCP_RCV_WND")

// 设置协议的最大发送窗口
val KCP_SND_WND = KcpOption.valueOf<Int>("KCP_SND_WND")

// 最大传输单元， 默认 mtu是1400字节，该值将会影响数据包归并及分片时候的最大传输单元。
val KCP_MTU = KcpOption.valueOf<Int>("KCP_MTU")

// 不管是 TCP还是 KCP计算 RTO时都有最小 RTO的限制，即便计算出来RTO为40ms，由于默认的 RTO是100ms，协议只有在100ms后才能检测到丢包，快速模式下为30ms
val KCP_MIN_RTO = KcpOption.valueOf<Int>("KCP_MIN_RTO")
