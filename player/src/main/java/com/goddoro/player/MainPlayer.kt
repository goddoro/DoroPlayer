package com.goddoro.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Created by goddoro on 2021-03-24.
 */

class MainPlayer ( val context : Context){

    private val SAMPLE_FILE_URL = "https://cdn.onesongtwoshows.com/video/ty2h2kqfgl8_1615804303890.mp4"
    private val TIMEOUT_US = 10_000L

    var count = 0

    var beforePresentationTime : Long  = 0

    fun createVideoThread(surface: Surface) = thread {
        val extractor = MediaExtractor().apply { setDataSource(SAMPLE_FILE_URL) }


        val trackIndex = extractor.firstVideoTrack
            ?: error("This media file doesn't contain any video tracks")

        extractor.selectTrack(trackIndex)

        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: error("Video track must have the mime type")

        val decoder = MediaCodec.createDecoderByType(mime).apply {
            configure(format, surface, null, 0)
            start()
        }

        doExtract(extractor, decoder)

        decoder.stop()
        decoder.release()
        extractor.release()

    }

    private fun doExtract(extractor: MediaExtractor, decoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()

        var inEos = false
        var outEos = false

        while (!outEos) {
            if (!inEos) {
                when (val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)) {

                    in 0..Int.MAX_VALUE -> {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                        val chunkSize = extractor.readSampleData(inputBuffer, 0)

                        if (chunkSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, -1,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inEos = true
                        } else {
                            val sampleTimeUs = extractor.sampleTime

                            decoder.queueInputBuffer(inputIndex, 0, chunkSize, sampleTimeUs , 0)

                            extractor.advance()
                        }
                    }
                    else -> Unit
                }
            }

            if (!outEos) {
                when (val outputIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    in 0..Int.MAX_VALUE -> {
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decoder.releaseOutputBuffer(outputIndex, false)
                            outEos = true
                        } else {
                            val difference = ( info.presentationTimeUs - beforePresentationTime )

                            TimeUnit.MICROSECONDS.sleep(difference)
                            decoder.releaseOutputBuffer(outputIndex,true)
                            beforePresentationTime = info.presentationTimeUs

                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {

                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                    }
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {

                    }
                    else -> error("unexpected result from decoder.dequeueOutputBuffer: $outputIndex")
                }
            }
        }

        Log.d("TEST",count.toString())
    }

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



}

