package com.example.adssdkxml.banner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ads.AdsManager
import com.example.ads.banner.BannerAdSize
import com.example.adssdkxml.databinding.ActivityMediumRectangleFlowBinding


class MediumRectangleFlowActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediumRectangleFlowBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediumRectangleFlowBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AdsManager.showBannerAd(binding.bannerAdMrecFlow, BannerAdSize.MEDIUM_RECTANGLE)
    }
}
