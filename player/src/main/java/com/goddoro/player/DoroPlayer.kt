import android.content.Context
import android.media.*
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.lifecycle.MutableLiveData
import com.goddoro.player.extensions.debugE
import com.goddoro.player.extensions.firstAudioTrack
import com.goddoro.player.extensions.firstVideoTrack
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import kotlin.math.max
import kotlin.math.min

/**
 * Created by goddoro on 2021-03-24.
 */

class DoroPlayer ( extractorSupplier: () -> MediaExtractor, surface : Surface) {

    private val audioExtractor: MediaExtractor
    private val videoExtractor: MediaExtractor
    private val audioTrackIndex: Int
    private val videoTrackIndex: Int
    private val videoDecoder: MediaCodec
    private val audioDecoder: MediaCodec
    private val audioTrack: AudioTrack
    private var audioInEos = false
    private var audioOutEos = false
    private var videoInEos = false
    private var videoOutEos = false
    private val audioBufferInfo = MediaCodec.BufferInfo()
    private val videoBufferInfo = MediaCodec.BufferInfo()


    var demuxCount = 0
    var decodeCount = 0
    var renderCount = 0

    init {
        val extractor = extractorSupplier()
        val audioTrackIndex = extractor.firstAudioTrack
        val videoTrackIndex = extractor.firstVideoTrack
        extractor.release()

        if (audioTrackIndex == null || videoTrackIndex == null) {
            error("We need both audio and video")
        }

        audioExtractor = extractorSupplier().apply { selectTrack(audioTrackIndex) }
        videoExtractor = extractorSupplier().apply { selectTrack(videoTrackIndex) }
        this.audioTrackIndex = audioTrackIndex
        this.videoTrackIndex = videoTrackIndex

        audioDecoder = createDecoder(audioExtractor, audioTrackIndex)
        videoDecoder = createDecoder(videoExtractor, videoTrackIndex, surface)

        val format = audioExtractor.getTrackFormat(audioTrackIndex)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val channelMask = when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> error("AudioTrack doesn't support $channels channels")
        }


        audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build())
                .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
    }

    private val demuxThread = HandlerThread("DemuxThread").apply { start() }
    private val audioDecodeThread = HandlerThread("AudioDecodeThread").apply { start() }
    private val videoDecodeThread = HandlerThread("VideoDecodeThread").apply { start() }
    private val syncThread = HandlerThread("SyncThread").apply { start() }
    private val audioRenderThread = HandlerThread("AudioRenderThread").apply { start() }
    private val videoRenderThread = HandlerThread("VideoRenderThread").apply { start() }
    private val demuxHandler = Handler(demuxThread.looper)
    private val audioDecodeHandler = Handler(audioDecodeThread.looper)
    private val videoDecodeHandler = Handler(videoDecodeThread.looper)
    private val syncHandler = Handler(syncThread.looper)
    private val audioRenderHandler = Handler(audioRenderThread.looper)
    private val videoRenderHandler = Handler(videoRenderThread.looper)

    private val audioFrameQueue: Queue<AudioFrame> = ConcurrentLinkedQueue<AudioFrame>()
    private val videoFrameQueue: Queue<VideoFrame> = ConcurrentLinkedQueue<VideoFrame>()
    private var startTimeMs = -1L

    /**
     * in milliseconds
     */
    val position: Long
        get() = if (startTimeMs < 0) {
            startTimeMs
        } else {
            SystemClock.uptimeMillis() - startTimeMs
        }

    /**
     * in milliseconds
     */
    val duration: Long = kotlin.run {
        val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
        val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
        val audioDurationUs = audioFormat.getLong(MediaFormat.KEY_DURATION)
        val videoDurationUs = videoFormat.getLong(MediaFormat.KEY_DURATION)
        max(audioDurationUs, videoDurationUs) / 1000L
    }

    private data class AudioFrame(
            val data: ByteBuffer,
            val bufferId: Int,
            val ptsUs: Long
    ) {
        override fun toString(): String = "AudioFrame(bufferId=$bufferId, ptsUs=$ptsUs)"
    }

    private data class VideoFrame(
            val bufferId: Int,
            val ptsUs: Long
    )

    private fun createDecoder(extractor: MediaExtractor, trackIndex: Int, surface: Surface? = null): MediaCodec {
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        return MediaCodec.createDecoderByType(mime).apply {
            configure(format, surface, null, 0)
        }
    }

    fun play() = synchronized(this) {

        audioInEos = false
        audioOutEos = false
        videoInEos = false
        videoOutEos = false
        startTimeMs = -1L



        audioDecoder.start()
        videoDecoder.start()
        audioFrameQueue.clear()
        videoFrameQueue.clear()

        postExtractAudio(0)
        postExtractVideo(0)
        postDecodeAudio(0)
        postDecodeVideo(0)
    }

    private fun postExtractAudio(delayMillis: Long) {
        demuxHandler.postDelayed({
            if (!audioInEos) {
                when (val inputIndex = audioDecoder.dequeueInputBuffer(0)) {
                    in 0..Int.MAX_VALUE -> {

                        val inputBuffer = audioDecoder.getInputBuffer(inputIndex)!!
                        val chunkSize = audioExtractor.readSampleData(inputBuffer, 0)
                        if (chunkSize < 0) {
                            audioDecoder.queueInputBuffer(inputIndex, 0, 0, -1,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            audioInEos = true
                        } else {
                            val sampleTimeUs = audioExtractor.sampleTime
                            audioDecoder.queueInputBuffer(inputIndex, 0, chunkSize,
                                    sampleTimeUs, 0)
                            audioExtractor.advance()
                        }

                        postExtractAudio(0)
                    }
                    else -> postExtractAudio(10)
                }
            }
        }, delayMillis)
    }

    private fun postDecodeAudio(delayMillis: Long) {
        audioDecodeHandler.postDelayed({
            if (!audioOutEos) {
                when (val outputIndex = audioDecoder.dequeueOutputBuffer(audioBufferInfo, 0)) {
                    in 0..Int.MAX_VALUE -> {
                        if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            audioDecoder.releaseOutputBuffer(outputIndex, false)
                            audioOutEos = true
                        } else {
                            val outputBuffer = audioDecoder.getOutputBuffer(outputIndex)!!
                            outputBuffer.position(audioBufferInfo.offset)
                            outputBuffer.limit(audioBufferInfo.offset + audioBufferInfo.size)

                            queueAudio(outputBuffer, outputIndex,
                                    audioBufferInfo.presentationTimeUs)
                        }

                        postDecodeAudio(0)
                        return@postDelayed
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> error("unexpected result from " +
                            "decoder.dequeueOutputBuffer: $outputIndex")
                }

                postDecodeAudio(10)
            }
        }, delayMillis)
    }

    private fun postExtractVideo(delayMillis: Long) {
        demuxHandler.postDelayed({
            if (!videoInEos) {
                when (val inputIndex = videoDecoder.dequeueInputBuffer(0)) {
                    in 0..Int.MAX_VALUE -> {
                        demuxCount++
                        val inputBuffer = videoDecoder.getInputBuffer(inputIndex)!!
                        val chunkSize = videoExtractor.readSampleData(inputBuffer, 0)
                        if (chunkSize < 0) {
                            videoDecoder.queueInputBuffer(inputIndex, 0, 0, -1,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            videoInEos = true
                        } else {
                            val sampleTimeUs = videoExtractor.sampleTime
                            videoDecoder.queueInputBuffer(inputIndex, 0, chunkSize,
                                    sampleTimeUs, 0)
                            videoExtractor.advance()
                        }

                        postExtractVideo(0)
                    }
                    else -> postExtractVideo(10)
                }
            }
        }, delayMillis)
    }

    private fun postDecodeVideo(delayMillis: Long) {
        videoDecodeHandler.postDelayed({
            if (!videoOutEos) {
                when (val outputIndex = videoDecoder.dequeueOutputBuffer(videoBufferInfo, 0)) {
                    in 0..Int.MAX_VALUE -> {
                        if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            videoDecoder.releaseOutputBuffer(outputIndex, false)
                            videoOutEos = true
                        } else {
                            decodeCount++
                            queueVideo(outputIndex, videoBufferInfo.presentationTimeUs)
                        }

                        postDecodeVideo(0)
                        return@postDelayed
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> error("unexpected result from " +
                            "decoder.dequeueOutputBuffer: $outputIndex")
                }

                postDecodeVideo(10)
            }
        }, delayMillis)
    }


    private fun postRenderAudioAtTime(audioFrame: AudioFrame, uptimeMillis: Long) {
        audioRenderHandler.postAtTime({
            if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.play()
            }

            val size = audioFrame.data.remaining()
            audioTrack.write(audioFrame.data, size, AudioTrack.WRITE_NON_BLOCKING)

            audioDecoder.releaseOutputBuffer(audioFrame.bufferId, false)
        }, uptimeMillis)
    }


    private fun postRenderVideoAtTime(videoFrame: VideoFrame, uptimeMillis: Long) {
        videoRenderHandler.postAtTime({
            renderCount++
            videoDecoder.releaseOutputBuffer(videoFrame.bufferId, true)
        }, uptimeMillis)
    }

    private fun queueAudio(data: ByteBuffer, bufferId: Int, ptsUs: Long) {
        audioFrameQueue.add(AudioFrame(data, bufferId, ptsUs))
        postSyncAudioVideo(0)
    }

    private fun queueVideo(bufferId: Int, ptsUs: Long) {
        videoFrameQueue.add(VideoFrame(bufferId, ptsUs))
        postSyncAudioVideo(0)
    }

    private fun postSyncAudioVideo(delayMillis: Long) {
        syncHandler.postDelayed({
            val curTimeMs = SystemClock.uptimeMillis()

            val audioFrame: AudioFrame? = audioFrameQueue.peek()
            val videoFrame: VideoFrame? = videoFrameQueue.peek()

            if (audioFrame == null && videoFrame == null) {
                return@postDelayed
            }

            if (startTimeMs < 0) {
                if (audioFrame == null || videoFrame == null) {
                    return@postDelayed
                }

                val startPtsUs = min(audioFrame.ptsUs, videoFrame.ptsUs)
                startTimeMs = curTimeMs - startPtsUs / 1000L
                val audioDelayMs = audioFrame.ptsUs / 1000L - (curTimeMs - startTimeMs)
                val videoDelayMs = videoFrame.ptsUs / 1000L - (curTimeMs - startTimeMs)
            }

            if (audioFrame != null) {
                val renderTimeMs = startTimeMs + audioFrame.ptsUs / 1000L
                postRenderAudioAtTime(audioFrame, renderTimeMs)
                audioFrameQueue.remove()
            }

            if (videoFrame != null) {
                val renderTimeMs = startTimeMs + videoFrame.ptsUs / 1000L
                if (renderTimeMs >= curTimeMs) {
                    postRenderVideoAtTime(videoFrame, renderTimeMs)
                } else {
                    videoDecoder.releaseOutputBuffer(videoFrame.bufferId, false)
                }

                videoFrameQueue.remove()
            }

            if (!audioFrameQueue.isEmpty() || !videoFrameQueue.isEmpty()) {
                postSyncAudioVideo(0)
            } else {
                postSyncAudioVideo(10)
            }
        }, delayMillis)
    }

    fun pause() = synchronized(this) {
        demuxHandler.removeCallbacksAndMessages(null)
        audioDecodeHandler.removeCallbacksAndMessages(null)
        videoDecodeHandler.removeCallbacksAndMessages(null)
    }

    fun restart() = synchronized(this) {

        startTimeMs = -1L

        postExtractAudio(0)
        postExtractVideo(0)
        postDecodeAudio(0)
        postDecodeVideo(0)

    }

}

