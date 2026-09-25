package com.example.adssdkxml.nativead

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.ads.util.AdsLogger
import com.example.adssdkxml.MainActivity
import com.example.adssdkxml.databinding.ActivityNativeFlowBinding


class NativeAdFlowActivity : AppCompatActivity() {


    private lateinit var binding: ActivityNativeFlowBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNativeFlowBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.nativeAdBig.loadAd()
    }
}
