package com.developer.manali.galleryapp

import android.app.Activity
import android.app.Dialog
//import android.media.tv.AdRequest
import android.os.Bundle
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

    private fun getAdRequest(): AdRequest {
        val extras = Bundle().apply {
            putString("maxAdContentRating", AdsUtils.maxAdContentRating)
        }
        return AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
            .build()
    }

    fun loadAndShowInterstitial(
        activity: Activity,
        interstitialAdCallback: InterstitialAdCallback,
    ) {
        if (!AdsUtils.isConnected(activity)) {
            interstitialAdCallback.onAdClose()
            return
        }

        val adRequest = getAdRequest()

        InterstitialAd.load(
            activity,
            activity.getString(R.string.admob_inter_language),
            adRequest,
            object : InterstitialAdLoadCallback() {

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    dismissLoadingDialog()
                    admobInterstitial = interstitialAd
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

    private fun dismissLoadingDialog() {
        if (loadingDialog != null) {
            loadingDialog!!.dismiss()
            loadingDialog = null
        }
    }
}