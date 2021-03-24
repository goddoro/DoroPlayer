package com.example.goddoro_android

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import com.goddoro.player.view.PlayerView
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        initPlayer()
    }

    private fun initPlayer() {

        val playerView : com.google.android.exoplayer2.ui.PlayerView = findViewById(R.id.playerView)

        val player = SimpleExoPlayer.Builder(this).build()

        playerView.player = player
        val mediaItem = MediaItem.fromUri("https://cdn.onesongtwoshows.com/video/ty2h2kqfgl8_1615804303890.mp4")
        player.setMediaItem(mediaItem)

        player.prepare()
        player.play()

    }

}