package com.goddoro.player.controller

/**
 * Created by goddoro on 2021-03-24.
 */

interface DoroPlayController {
    //prepare(), start(), stop(), pause(), seekTo(), release()
    //TODO add param videoSource
    fun prepare()
    fun start()
    fun stop()
    fun pause()

    //TODO add param seek second
    fun seekTo(seekTime: Int)
    fun release()
}