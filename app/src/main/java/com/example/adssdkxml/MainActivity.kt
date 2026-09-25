package com.example.adssdkxml

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ads.AdsManager
import com.example.ads.util.AdsLogger
import com.example.adssdkxml.banner.ComposeMediumRectangleActivity
import com.example.adssdkxml.banner.MediumRectangleFlowActivity
import com.example.ads.config.AdsRemoteConfigManager
import com.example.adssdkxml.databinding.ActivityMainBinding
import com.example.adssdkxml.nativead.NativeAdFlowActivity
import com.example.ads.rewarded.CoinManager


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateRemoteConfigStatus()
        updateCoinBalanceUI()

        // 1. Medium Rectangle (300x250) Banner Flow
        binding.btnFlowMrec.setOnClickListener {
            val intent = Intent(this, MediumRectangleFlowActivity::class.java)
            startActivity(intent)
        }

        // 2. Interstitial Screen Transition
        binding.btnFlowInterstitial.setOnClickListener {
            AdsManager.showInterstitial(this) {


            }
        }

        // 3. Native Ads Flow
        binding.btnFlowNative.setOnClickListener {
            val intent = Intent(this, NativeAdFlowActivity::class.java)
            startActivity(intent)
        }

        // 4. Rewarded Ad (On-Demand / Preloaded -> Adds 10 Coins to SharedPreferences)
        binding.btnShowRewarded.setOnClickListener {
            AdsLogger.i(AdsLogger.TAG_REWARDED, "MainActivity: User clicked 'Watch Rewarded Ad'")
            AdsManager.showRewarded(
                activity = this,
                onRewardEarned = {
                    // Add 10 coins into SharedPreferences
                    val newBalance = CoinManager.addCoins(this, 10)
                    updateCoinBalanceUI()
                    Toast.makeText(this, "🎉 Reward Granted! +10 Coins (Total: $newBalance)", Toast.LENGTH_LONG).show()
                },
                onComplete = { rewardEarned ->
                    if (rewardEarned) {
                        AdsLogger.i(AdsLogger.TAG_REWARDED, "MainActivity: Rewarded flow completed with reward.")
                    } else {
                        Toast.makeText(this, "Rewarded ad closed (no reward or not available)", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // 5. Use 4 Coins (Demonstrating useCoins(4) method)
        binding.btnUseCoins.setOnClickListener {
            val isSuccess = CoinManager.useCoins(this, 4)
            if (isSuccess) {
                updateCoinBalanceUI()
                val currentCoins = CoinManager.getCoins(this)
                Toast.makeText(this, "🪙 4 Coins Used! (Remaining: $currentCoins)", Toast.LENGTH_SHORT).show()
            } else {
                val currentCoins = CoinManager.getCoins(this)
                Toast.makeText(this, "❌ Insufficient Coins! You have $currentCoins (Need 4)", Toast.LENGTH_SHORT).show()
            }
        }

        // 6. Compose Medium Rectangle Ad Screen
        binding.btnComposeMrec.setOnClickListener {
            val intent = Intent(this, ComposeMediumRectangleActivity::class.java)
            startActivity(intent)
        }

        // Premium Switch Toggle
        binding.switchPremium.setOnCheckedChangeListener { _, isChecked ->
            val adsEnabled = !isChecked
            AdsManager.setAdsEnabled(adsEnabled)
            if (adsEnabled) {
                Toast.makeText(this, "Ads Enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Premium Mode: Ads Disabled Globally", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateRemoteConfigStatus()
        updateCoinBalanceUI()
    }

    private fun updateCoinBalanceUI() {
        val currentCoins = CoinManager.getCoins(this)
        binding.tvCoinBalance.text = "💰 Coins Balance: $currentCoins"
    }

    private fun updateRemoteConfigStatus() {
        val config = AdsManager.config
        val isRemote = AdsRemoteConfigManager.isFetchedFromRemote
        val statusText = if (isRemote) {
            "✓ Firebase Remote Config: Active (TestAds=${config?.useTestAds})"
        } else {
            "● Firebase Config: Local Defaults Active (TestAds=${config?.useTestAds})"
        }
        binding.tvRemoteConfigStatus.text = statusText
    }

}