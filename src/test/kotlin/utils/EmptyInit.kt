package utils

import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer


object EmptyInit : ChannelInitializer<Channel>() {
    override fun initChannel(ch: Channel) {
        // pass
    }
}
