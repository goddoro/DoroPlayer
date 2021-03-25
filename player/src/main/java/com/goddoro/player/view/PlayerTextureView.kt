package com.goddoro.player.view

import DoroPlayer
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaExtractor
import android.media.MediaSync
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.TextureView
import com.goddoro.player.CommonConst.SAMPLE_FILE_URL
import com.goddoro.player.controller.DoroPlayController

/**
 * Created by goddoro on 2021-03-24.
 */

class PlayerTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : TextureView(context, attrs, defStyle), DoroPlayController {

    private var ratioWidth = 0
    private var ratioHeight = 0

    private var _surface: Surface? = null
    private val surface: Surface
        get() {
            return _surface!!
        }

    private var doroPlayer : DoroPlayer? = null



    init {


        this.setAspectRatio(9,16)
        this.surfaceTextureListener = object : SurfaceTextureListener{
            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) = Unit

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                _surface = Surface(surface)
                initPlayer()
            }


        }
    }

    private fun initPlayer( ) {
        doroPlayer = DoroPlayer({
            MediaExtractor().apply { setDataSource(SAMPLE_FILE_URL) }
        }, surface, context)

    }

    private fun playOrStop( ) {
        if (doroPlayer == null) {
            doroPlayer = DoroPlayer({
                MediaExtractor().apply { setDataSource(SAMPLE_FILE_URL) }
            }, surface, context)

            doroPlayer?.play()
        } else {
            //avPlayer?.stop()
            doroPlayer?.release()
            doroPlayer = null
        }
    }

    fun play () {
        doroPlayer?.play()
    }



    /**
     * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    fun setAspectRatio(width: Int, height: Int) {
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("Size cannot be negative.")
        }
        ratioWidth = width
        ratioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (ratioWidth == 0 || ratioHeight == 0) {
            setMeasuredDimension(width, height)
        } else {
            if (width < ((height * ratioWidth) / ratioHeight)) {
                setMeasuredDimension(width, (width * ratioHeight) / ratioWidth)
            } else {
                setMeasuredDimension((height * ratioWidth) / ratioHeight, height)
            }
        }
    }

    override fun prepare() {
        TODO("Not yet implemented")
    }

    override fun start() {
        playOrStop()
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun pause() {
        TODO("Not yet implemented")
    }

    override fun seekTo(seekTime: Int) {
        TODO("Not yet implemented")
    }

    override fun release() {
        TODO("Not yet implemented")
    }

}