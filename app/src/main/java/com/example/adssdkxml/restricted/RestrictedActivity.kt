package com.example.adssdkxml.restricted

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ads.appopen.ExcludedFromAppOpen
import com.example.ads.appopen.NoAdsScreen
import com.example.adssdkxml.databinding.ActivityRestrictedBinding

/**
 * Screen demonstrating ad exclusion / restricted screen behavior using View Binding.
 * App Open ads will NOT appear when returning to the app from this screen.
 */
@ExcludedFromAppOpen
class RestrictedActivity : AppCompatActivity(), NoAdsScreen {

    private lateinit var binding: ActivityRestrictedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRestrictedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
