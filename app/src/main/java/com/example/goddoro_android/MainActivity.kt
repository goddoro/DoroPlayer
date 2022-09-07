package com.example.goddoro_android

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.example.goddoro_android.databinding.ActivityMainBinding
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.RawResourceDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import java.io.File

class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(LayoutInflater.from(this))
    }

    private lateinit var exoPlayer: ExoPlayer

    private lateinit var cacheDataSourceFactory : CacheDataSource.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initCacheSetting()
        initPlayer()

    }

    private fun initPlayer() {
        exoPlayer = ExoPlayer.Builder(this@MainActivity).setMediaSourceFactory(DefaultMediaSourceFactory(this@MainActivity).setDataSourceFactory(cacheDataSourceFactory)).build()
        with(exoPlayer) {
            binding.playerView.player = this
            setMediaItem(MediaItem.fromUri("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"))
            playWhenReady = true
            prepare()
        }

    }


    private fun initCacheSetting(){
        val cacheEvictor = LeastRecentlyUsedCacheEvictor((100 * 1024 * 1024).toLong())
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        val dataBaseProvider = StandaloneDatabaseProvider(this@MainActivity)
        val simpleCache = SimpleCache(File(cacheDir, "media"), cacheEvictor, dataBaseProvider)
        cacheDataSourceFactory = CacheDataSource.Factory().setCache(simpleCache).setUpstreamDataSourceFactory(httpDataSourceFactory)
    }

}