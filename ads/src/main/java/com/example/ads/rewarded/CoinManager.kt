package com.example.ads.rewarded

import android.content.Context
import android.content.SharedPreferences

/**
 * Built-in thread-safe Coin & Virtual Reward Manager for the Ads SDK.
 * Manages persisted user coin balance across app sessions using SharedPreferences.
 */
object CoinManager {

    private const val PREF_NAME = "ads_sdk_coins_pref"
    private const val KEY_COIN_BALANCE = "ads_coin_balance"
    private const val DEFAULT_INITIAL_COINS = 45

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns the current coin balance.
     *
     * @param context Application or Activity context.
     */
    fun getCoins(context: Context): Int {
        return getPrefs(context).getInt(KEY_COIN_BALANCE, DEFAULT_INITIAL_COINS)
    }

    /**
     * Adds coins to the balance (e.g., when a user completes a rewarded ad).
     *
     * @param context Application or Activity context.
     * @param amount Number of coins to add (default is 10).
     * @return The updated total coin balance.
     */
    @Synchronized
    fun addCoins(context: Context, amount: Int = 10): Int {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_COIN_BALANCE, DEFAULT_INITIAL_COINS)
        val updated = current + amount
        prefs.edit().putInt(KEY_COIN_BALANCE, updated).apply()
        return updated
    }

    /**
     * Deducts coins from the balance if sufficient funds are available.
     *
     * @param context Application or Activity context.
     * @param amount Number of coins to spend (e.g., `useCoins(context, 4)`).
     * @return `true` if deduction succeeded, `false` if balance was insufficient.
     */
    @Synchronized
    fun useCoins(context: Context, amount: Int): Boolean {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_COIN_BALANCE, DEFAULT_INITIAL_COINS)
        if (current >= amount) {
            val updated = current - amount
            prefs.edit().putInt(KEY_COIN_BALANCE, updated).apply()
            return true
        }
        return false
    }

    /**
     * Sets or resets the coin balance to a specific amount.
     */
    fun setCoins(context: Context, coins: Int) {
        getPrefs(context).edit().putInt(KEY_COIN_BALANCE, coins).apply()
    }
}
