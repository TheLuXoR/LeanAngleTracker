package de.hasselmeyer.leanangle.privacy

import android.content.Context
import androidx.core.content.edit

internal enum class AdSupportedAccessState {
    OPEN,
    CHOOSE_ACCESS,
    CHECKING_AD_ELIGIBILITY,
    AD_ACCESS_UNAVAILABLE
}

internal fun resolveAdSupportedAccessState(
    isPremiumSubscribed: Boolean,
    hasChosenAdSupportedAccess: Boolean,
    consentRequestStarted: Boolean,
    consentState: AdsConsentState
): AdSupportedAccessState = when {
    isPremiumSubscribed -> AdSupportedAccessState.OPEN
    !hasChosenAdSupportedAccess -> AdSupportedAccessState.CHOOSE_ACCESS
    consentState.canRequestAds -> AdSupportedAccessState.OPEN
    !consentRequestStarted || !consentState.requestCompleted ->
        AdSupportedAccessState.CHECKING_AD_ELIGIBILITY
    else -> AdSupportedAccessState.AD_ACCESS_UNAVAILABLE
}

/**
 * Stores the user's product-mode choice, not a GDPR or TDDDG consent.
 *
 * Advertising privacy choices remain exclusively managed by Google's UMP SDK.
 */
internal class AdSupportedAccessStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun hasChosenAdSupportedAccess(): Boolean =
        preferences.getBoolean(KEY_AD_SUPPORTED_ACCESS, false)

    fun chooseAdSupportedAccess() {
        preferences.edit {
            putBoolean(KEY_AD_SUPPORTED_ACCESS, true)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "ad_supported_access"
        const val KEY_AD_SUPPORTED_ACCESS = "chosen"
    }
}
