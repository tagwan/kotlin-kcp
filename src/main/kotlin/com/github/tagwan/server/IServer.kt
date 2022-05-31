package com.github.tagwan.server

/**
 * @author jdg
 */
interface IServer {
    /**
     * start the server
     */
    @Throws(Exception::class)
    fun start()

    /**
     * dispose the server
     */
    @Throws(Exception::class)
    fun dispose()
}
