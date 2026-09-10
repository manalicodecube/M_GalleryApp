package com.developer.manali.galleryapp

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

object AppOpenAdsCall {

    interface AppOpenAdCallback {
        fun onAdDismissed()
    }

    private var appOpenAd: AppOpenAd? = null
    private var isShowingAd = false

    fun loadAndShowAppOpenAd(activity: Activity, callback: AppOpenAdCallback) {
        if (!AdsUtils.isConnected(activity)) {
            callback.onAdDismissed()
            return
        }

        val adUnitId = activity.getString(R.string.admob_appopen)
        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            activity, adUnitId, request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    showAdIfAvailable(activity, callback)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    callback.onAdDismissed()
                }
            }
        )
    }

    private fun showAdIfAvailable(activity: Activity, callback: AppOpenAdCallback) {
        if (!isShowingAd && appOpenAd != null) {
            appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    isShowingAd = false
                    callback.onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    appOpenAd = null
                    isShowingAd = false
                    callback.onAdDismissed()
                }

                override fun onAdShowedFullScreenContent() {
                    isShowingAd = true
                }
            }
            appOpenAd?.show(activity)
        } else {
            callback.onAdDismissed()
        }
    }
}
