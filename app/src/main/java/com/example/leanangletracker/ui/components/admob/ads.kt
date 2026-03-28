package com.example.leanangletracker.ui.components.admob

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.leanangletracker.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

private const val TAG = "AdMob"

@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier.fillMaxWidth(),
    adUnitId: String = BuildConfig.ADMOB_BANNER_ID
) {
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
                    Log.e(TAG, "Banner failed to load: ${error.message}")
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

    AnimatedVisibility(
        visible = isAdLoaded,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }) + expandVertically(expandFrom = Alignment.Bottom)
    ) {
        AndroidView(
            modifier = modifier,
            factory = { adView },
            update = { }
        )
    }
}

fun loadInterstitial(context: Context, onAdLoaded: (InterstitialAd?) -> Unit) {
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
                Log.e(TAG, "Interstitial failed to load: ${loadAdError.message}")
                onAdLoaded(null)
            }
        }
    )
}

fun showInterstitial(context: Context, interstitialAd: InterstitialAd?, onAdDismissed: () -> Unit) {
    val activity = context.findActivity()
    if (activity != null) {
        interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                Log.e(TAG, "Interstitial failed to show: ${adError.message}")
                onAdDismissed()
            }
        }
        interstitialAd?.show(activity)
    } else {
        onAdDismissed()
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
