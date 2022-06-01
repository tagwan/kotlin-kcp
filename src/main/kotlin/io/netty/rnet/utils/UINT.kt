package io.netty.rnet.utils

import it.unimi.dsi.fastutil.ints.IntComparator

class UINT private constructor() {

    object B2 {
        const val MAX_VALUE = 1 shl java.lang.Byte.SIZE * 2 - 1
        fun plus(value: Int, add: Int): Int {
            return value + add and MAX_VALUE
        }

        fun minus(value: Int, minus: Int): Int {
            return value - minus and MAX_VALUE
        }
    }

    object B3 {
        const val MAX_VALUE = 1 shl java.lang.Byte.SIZE * 3 - 1
        const val HALF_MAX = MAX_VALUE / 2
        val COMPARATOR: IntComparator = Comparator()
        fun plus(value: Int, add: Int): Int {
            return value + add and MAX_VALUE
        }

        fun minus(value: Int, minus: Int): Int {
            return value - minus and MAX_VALUE
        }

        fun minusWrap(value: Int, minus: Int): Int {
            val dist = value - minus
            return if (dist < 0) {
                -minusWrap(minus, value)
            } else if (dist > HALF_MAX) {
                value - (minus + MAX_VALUE + 1)
            } else {
                dist
            }
        }

        class Comparator : IntComparator {
            override fun compare(a: Int, b: Int): Int {
                val d = minusWrap(a, b)
                return if (d < 0) -1 else if (d == 0) 0 else 1
            }
        }
    }
}