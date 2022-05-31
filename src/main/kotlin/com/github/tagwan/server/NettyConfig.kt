package com.github.tagwan.server

import com.github.tagwan.internal.AbstractProperties

/**
 * NettyConfig
 *
 * @data 2022/5/31 16:58
 */
class NettyConfig : AbstractProperties(CFG_NETTY, "server.") {
    val port: Int by prop
}

const val CFG_NETTY = "application-net.properties"