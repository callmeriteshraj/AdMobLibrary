package com.example.ads.consent

import android.app.Activity
import android.content.Context
import com.example.ads.AdsConfig
import com.example.ads.util.AdsLogger
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * Manages Google User Messaging Platform (UMP) consent collection for GDPR, CPRA, and international privacy laws.
 */
class AdsConsentManager(private val context: Context) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    /**
     * Checks whether the SDK is permitted to request ads under the current user consent status.
     */
    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    /**
     * Checks whether the privacy options button/setting is required to be shown to the user.
     */
    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Requests consent information update and presents the consent form if required.
     *
     * @param activity The current foreground Activity.
     * @param config The current AdsConfig.
     * @param onConsentComplete Callback invoked when consent handling finishes, passing whether ads can now be requested.
     */
    fun requestConsentAndShowFormIfRequired(
        activity: Activity,
        config: AdsConfig,
        onConsentComplete: (canRequestAds: Boolean) -> Unit
    ) {
        val paramsBuilder = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(config.underAgeOfConsent == 1)

        // If test ads are enabled, configure debug settings for testing GDPR dialog in emulator/devices
        if (config.useTestAds) {
            val debugSettingsBuilder = ConsentDebugSettings.Builder(activity)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)

            for (deviceId in config.testDeviceHashedIds) {
                debugSettingsBuilder.addTestDeviceHashedId(deviceId)
            }
            paramsBuilder.setConsentDebugSettings(debugSettingsBuilder.build())
        }

        val consentRequestParameters = paramsBuilder.build()

        AdsLogger.i(AdsLogger.TAG_CONSENT, "Requesting UMP consent info update...")
        consentInformation.requestConsentInfoUpdate(
            activity,
            consentRequestParameters,
            {
                AdsLogger.i(AdsLogger.TAG_CONSENT, "UMP Consent info updated successfully. Loading form if required...")
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    activity
                ) { formError: FormError? ->
                    if (formError != null) {
                        AdsLogger.w(AdsLogger.TAG_CONSENT, "UMP Consent form error [${formError.errorCode}]: ${formError.message}")
                    } else {
                        AdsLogger.i(AdsLogger.TAG_CONSENT, "UMP Consent form dismissed or not required.")
                    }
                    val canRequest = consentInformation.canRequestAds()
                    AdsLogger.i(AdsLogger.TAG_CONSENT, "Can request ads after consent flow: $canRequest")
                    onConsentComplete(canRequest)
                }
            },
            { formError: FormError ->
                AdsLogger.e(AdsLogger.TAG_CONSENT, "Failed to request UMP consent info update [${formError.errorCode}]: ${formError.message}")
                // Fallback: Check if ads can still be requested despite the update failure
                onConsentComplete(consentInformation.canRequestAds())
            }
        )
    }

    /**
     * Shows the privacy options form (e.g. from the app's settings or privacy menu).
     */
    fun showPrivacyOptionsForm(
        activity: Activity,
        onDismissed: (formError: FormError?) -> Unit
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError: FormError? ->
            if (formError != null) {
                AdsLogger.w(AdsLogger.TAG_CONSENT, "Privacy options form error [${formError.errorCode}]: ${formError.message}")
            }
            onDismissed(formError)
        }
    }

    /**
     * Resets consent state (useful during development/testing).
     */
    fun reset() {
        consentInformation.reset()
        AdsLogger.i(AdsLogger.TAG_CONSENT, "UMP Consent information reset.")
    }
}
