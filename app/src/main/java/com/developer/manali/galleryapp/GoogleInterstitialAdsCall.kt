package com.developer.manali.galleryapp

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

interface InterstitialAdCallback {
    fun onAdClose()
}

object AppOpenManager {
    var isShowingAd: Boolean = false
}

object GoogleInterstitialAdsCall {

    private var admobInterstitial: InterstitialAd? = null
    private var loadingDialog: Dialog? = null
    private var isPreloading: Boolean = false

    private fun getAdRequest(): AdRequest {
        val extras = Bundle().apply {
            putString("maxAdContentRating", AdsUtils.maxAdContentRating)
        }
        return AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
            .build()
    }

    fun preloadInterstitial(context: Context) {
        if (admobInterstitial != null || isPreloading) return
        if (!AdsUtils.isConnected(context)) return

        isPreloading = true
        val adRequest = getAdRequest()
        InterstitialAd.load(
            context,
            context.getString(R.string.admob_inter_language),
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    isPreloading = false
                    admobInterstitial = interstitialAd
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isPreloading = false
                    admobInterstitial = null
                }
            }
        )
    }

    fun loadAndShowInterstitial(
        activity: Activity,
        interstitialAdCallback: InterstitialAdCallback,
    ) {
        if (!AdsUtils.isConnected(activity)) {
            interstitialAdCallback.onAdClose()
            return
        }

        val cachedAd = admobInterstitial
        if (cachedAd != null) {
            admobInterstitial = null
            setupFullScreenCallback(cachedAd, interstitialAdCallback)
            cachedAd.show(activity)
            AppOpenManager.isShowingAd = true
            return
        }

        showLoadingDialog(activity)
        val adRequest = getAdRequest()

        InterstitialAd.load(
            activity,
            activity.getString(R.string.admob_inter_language),
            adRequest,
            object : InterstitialAdLoadCallback() {

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    dismissLoadingDialog()
                    setupFullScreenCallback(interstitialAd, interstitialAdCallback)
                    interstitialAd.show(activity)
                    AppOpenManager.isShowingAd = true
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    dismissLoadingDialog()
                    admobInterstitial = null
                    AppOpenManager.isShowingAd = false
                    interstitialAdCallback.onAdClose()
                }
            }
        )
    }

    private fun setupFullScreenCallback(
        interstitialAd: InterstitialAd,
        interstitialAdCallback: InterstitialAdCallback,
    ) {
        interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {

            override fun onAdDismissedFullScreenContent() {
                AppOpenManager.isShowingAd = false
                admobInterstitial = null
                interstitialAdCallback.onAdClose()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                AppOpenManager.isShowingAd = false
                admobInterstitial = null
                interstitialAdCallback.onAdClose()
            }

            override fun onAdShowedFullScreenContent() {
                AppOpenManager.isShowingAd = true
                admobInterstitial = null
            }
        }
    }

    private fun showLoadingDialog(activity: Activity) {
        try {
            if (activity.isFinishing || activity.isDestroyed) return
            dismissLoadingDialog()
            val dialog = Dialog(activity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val progressBar = ProgressBar(activity).apply {
                indeterminateTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(activity, R.color.lumina_primary)
                )
            }
            dialog.setContentView(progressBar)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.setCancelable(false)
            dialog.show()
            loadingDialog = dialog
        } catch (_: Exception) {}
    }

    private fun dismissLoadingDialog() {
        try {
            if (loadingDialog?.isShowing == true) {
                loadingDialog?.dismiss()
            }
        } catch (_: Exception) {}
        loadingDialog = null
    }
}