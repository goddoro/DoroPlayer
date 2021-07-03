package com.goddoro.player.extensions

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.goddoro.player.BuildConfig

/**
 * Created by goddoro on 2021-03-25.
 */


fun MediaExtractor.findFirstTrackFor(type: String): Int? {
    for (i in 0 until trackCount) {
        val mediaFormat = getTrackFormat(i)
        if (mediaFormat.getString(MediaFormat.KEY_MIME)!!.startsWith(type)) {
            return i
        }
    }

    return null
}

val MediaExtractor.firstVideoTrack: Int? get() = findFirstTrackFor("video/")
val MediaExtractor.firstAudioTrack: Int? get() = findFirstTrackFor("audio/")



fun debugE(tag: String, message: Any?) {
    if (BuildConfig.DEBUG)
        Log.e(tag, "🧩" + message.toString() + "🧩")
}

fun debugE(message: Any?) {
    debugE("DEBUG", message)
}