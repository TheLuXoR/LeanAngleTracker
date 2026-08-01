package de.hasselmeyer.leanangle.privacy

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import de.hasselmeyer.leanangle.R
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class AdsConsentState(
    val canRequestAds: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val requestCompleted: Boolean = false,
    val refreshTimedOut: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Single source of truth for UMP consent and ad eligibility.
 *
 * The app never persists consent itself. After starting the mandatory per-launch
 * refresh, UMP's previous-session canRequestAds value may be used immediately.
 */
internal class GoogleMobileAdsConsentManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val consentInformation =
        UserMessagingPlatform.getConsentInformation(applicationContext)
    private val mobileAdsInitialized = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(AdsConsentState())

    private var refreshGeneration = 0
    private var refreshInFlight = false
    private var lastRefreshSucceeded = false
    private var showFormWhenReady = false
    private var formShowing = false

    val state: StateFlow<AdsConsentState> = _state.asStateFlow()

    fun refreshConsentInfo(activity: Activity) {
        if (refreshInFlight) {
            publishCurrentState(requestCompleted = false, errorMessage = null)
            return
        }

        refreshInFlight = true
        lastRefreshSucceeded = false
        val generation = ++refreshGeneration
        val parameters = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                if (generation != refreshGeneration) return@requestConsentInfoUpdate
                refreshInFlight = false
                lastRefreshSucceeded = true
                if (showFormWhenReady) {
                    showConsentFormIfRequired(activity)
                } else {
                    publishCurrentState(
                        requestCompleted = !isConsentFormRequired(),
                        errorMessage = null
                    )
                }
            },
            { requestError ->
                if (generation != refreshGeneration) return@requestConsentInfoUpdate
                refreshInFlight = false
                lastRefreshSucceeded = false
                publishCurrentState(
                    requestCompleted = true,
                    errorMessage = requestError.message
                )
            }
        )

        // UMP can expose a valid decision from the previous session immediately
        // after requestConsentInfoUpdate() has been invoked for this launch.
        publishCurrentState(requestCompleted = false, errorMessage = null)
        mainHandler.postDelayed(
            {
                if (generation == refreshGeneration && refreshInFlight) {
                    publishCurrentState(
                        requestCompleted = true,
                        refreshTimedOut = true,
                        errorMessage = applicationContext.getString(
                            R.string.access_privacy_check_timeout
                        )
                    )
                }
            },
            CONSENT_REFRESH_TIMEOUT_MS
        )
    }

    fun showConsentFormIfRequiredWhenReady(activity: Activity) {
        showFormWhenReady = true
        if (refreshInFlight) return
        if (lastRefreshSucceeded) {
            showConsentFormIfRequired(activity)
        }
    }

    fun showPrivacyOptions(activity: Activity, onComplete: (() -> Unit)? = null) {
        if (
            consentInformation.privacyOptionsRequirementStatus !=
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        ) {
            publishCurrentState(requestCompleted = true, errorMessage = null)
            onComplete?.invoke()
            return
        }

        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            publishCurrentState(
                requestCompleted = true,
                errorMessage = formError?.message
            )
            onComplete?.invoke()
        }
    }

    private fun showConsentFormIfRequired(activity: Activity) {
        if (formShowing) return
        formShowing = true
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
            formShowing = false
            showFormWhenReady = false
            publishCurrentState(
                requestCompleted = true,
                errorMessage = formError?.message
            )
        }
    }

    private fun isConsentFormRequired(): Boolean =
        consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED

    private fun publishCurrentState(
        requestCompleted: Boolean,
        errorMessage: String?,
        refreshTimedOut: Boolean = false
    ) {
        val canRequestAds = consentInformation.canRequestAds()
        AdsConsentGate.setCanRequestAds(canRequestAds)
        _state.value = AdsConsentState(
            canRequestAds = canRequestAds,
            privacyOptionsRequired =
                consentInformation.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
            requestCompleted = requestCompleted,
            refreshTimedOut = refreshTimedOut,
            errorMessage = errorMessage
        )
        if (canRequestAds && mobileAdsInitialized.compareAndSet(false, true)) {
            MobileAds.initialize(applicationContext)
        }
    }

    private companion object {
        const val CONSENT_REFRESH_TIMEOUT_MS = 5_000L
    }
}

internal object AdsConsentGate {
    @Volatile
    private var adsAllowed: Boolean = false

    fun canRequestAds(): Boolean = adsAllowed

    fun setCanRequestAds(allowed: Boolean) {
        adsAllowed = allowed
    }
}
