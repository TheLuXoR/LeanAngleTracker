package com.example.leanangletracker.ui.components.admob

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.leanangletracker.BuildConfig
import com.example.leanangletracker.privacy.AdsConsentGate
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier.fillMaxWidth(),
    adUnitId: String = BuildConfig.ADMOB_BANNER_ID
) {
    if (!AdsConsentGate.canRequestAds()) return

    val context = LocalContext.current
    var isAdLoaded by remember { mutableStateOf(false) }

    // Create the AdView once and keep it in memory
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = adUnitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    isAdLoaded = true
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    super.onAdFailedToLoad(error)
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    // Ensure the AdView is destroyed when the Composable is removed
    DisposableEffect(adView) {
        onDispose {
            adView.destroy()
        }
    }

    // Use graphicsLayer for alpha animation to avoid expensive layout passes and bitmap caching
    // that often cause lag with AndroidView (WebView) in Compose.
    // We avoid expandVertically/slideIn as they force relayout of the whole screen every frame.
    val alpha by animateFloatAsState(
        targetValue = if (isAdLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "AdAlpha"
    )

    if (isAdLoaded || alpha > 0f) {
        AndroidView(
            modifier = modifier.graphicsLayer { this.alpha = alpha },
            factory = { adView },
            update = { }
        )
    }
}

fun loadInterstitial(context: Context, onAdLoaded: (InterstitialAd?) -> Unit) {
    if (!AdsConsentGate.canRequestAds()) {
        onAdLoaded(null)
        return
    }

    val adRequest = AdRequest.Builder().build()
    InterstitialAd.load(
        context,
        BuildConfig.ADMOB_INTERSTITIAL_ID,
        adRequest,
        object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                onAdLoaded(interstitialAd)
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                onAdLoaded(null)
            }
        }
    )
}

fun showInterstitial(context: Context, interstitialAd: InterstitialAd?, onAdDismissed: () -> Unit) {
    if (interstitialAd == null) {
        onAdDismissed()
        return
    }
    val activity = context.findActivity()
    if (activity != null) {
        interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                onAdDismissed()
            }
        }
        interstitialAd.show(activity)
    } else {
        onAdDismissed()
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
