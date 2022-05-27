package io.netty.kcp.core

fun _ibound_(lower: Int, middle: Int, upper: Int): Int {
    // Math.min(Math.max(lower, middle), upper)
    return lower.coerceAtLeast(middle).coerceAtMost(upper)
}

fun _itimediff(later: Int, earlier: Int): Int {
    return later - earlier
}

fun _itimediff(later: Long, earlier: Long): Long {
    return later - earlier
}