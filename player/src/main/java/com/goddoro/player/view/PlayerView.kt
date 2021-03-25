package com.goddoro.player.view

import DoroPlayer
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.TextureView
import androidx.constraintlayout.widget.ConstraintLayout
import com.goddoro.player.R
import com.goddoro.player.databinding.LayoutPlayerViewBinding

/**
 * Created by goddoro on 2021-03-25.
 */

class PlayerView @JvmOverloads constructor(
        context: Context,
        val attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    private var mBinding : LayoutPlayerViewBinding
    init {

        val layoutInflateService = Context.LAYOUT_INFLATER_SERVICE
        val layoutInflater = context.getSystemService(layoutInflateService) as LayoutInflater

        mBinding = LayoutPlayerViewBinding.inflate(layoutInflater,this,false)
        addView(mBinding.root)

        initSetting()

    }

    private fun initSetting() {

        mBinding.btnPlay.setOnClickListener {
            mBinding.playerTextureView.start()
        }
    }




}