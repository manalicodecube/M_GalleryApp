package com.imagefolders.photoorganizer.mediagallery

import android.app.Activity
import android.os.Handler
import android.os.Looper
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
    private var isLoadingAd = false
    private var pendingCallback: AppOpenAdCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    fun preload(activity: Activity) {
        if (!AdsUtils.isConnected(activity) || appOpenAd != null || isLoadingAd) {
            return
        }

        isLoadingAd = true
        val adUnitId = activity.getString(R.string.admob_appopen)
        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            activity, adUnitId, request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isLoadingAd = false
                    appOpenAd = ad
                    val cb = pendingCallback
                    if (cb != null) {
                        pendingCallback = null
                        cancelTimeout()
                        showAdIfAvailable(activity, cb)
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    appOpenAd = null
                    val cb = pendingCallback
                    if (cb != null) {
                        pendingCallback = null
                        cancelTimeout()
                        cb.onAdDismissed()
                    }
                }
            }
        )
    }

    fun loadAndShowAppOpenAd(activity: Activity, callback: AppOpenAdCallback) {
        if (!AdsUtils.isConnected(activity)) {
            callback.onAdDismissed()
            return
        }

        if (appOpenAd != null) {
            cancelTimeout()
            showAdIfAvailable(activity, callback)
            return
        }

        if (isLoadingAd) {
            pendingCallback = callback
            cancelTimeout()
            timeoutRunnable = Runnable {
                if (pendingCallback != null && !isShowingAd) {
                    pendingCallback = null
                    callback.onAdDismissed()
                }
            }
            mainHandler.postDelayed(timeoutRunnable!!, 5000)
            return
        }

        isLoadingAd = true
        pendingCallback = callback
        cancelTimeout()
        timeoutRunnable = Runnable {
            if (pendingCallback != null && !isShowingAd) {
                pendingCallback = null
                callback.onAdDismissed()
            }
        }
        mainHandler.postDelayed(timeoutRunnable!!, 5000)

        val adUnitId = activity.getString(R.string.admob_appopen)
        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            activity, adUnitId, request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isLoadingAd = false
                    appOpenAd = ad
                    val cb = pendingCallback
                    if (cb != null) {
                        pendingCallback = null
                        cancelTimeout()
                        showAdIfAvailable(activity, cb)
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    appOpenAd = null
                    val cb = pendingCallback
                    if (cb != null) {
                        pendingCallback = null
                        cancelTimeout()
                        cb.onAdDismissed()
                    }
                }
            }
        )
    }

    private fun showAdIfAvailable(activity: Activity, callback: AppOpenAdCallback) {
        cancelTimeout()

        if (activity.isFinishing || activity.isDestroyed) {
            callback.onAdDismissed()
            return
        }

        val ad = appOpenAd
        if (!isShowingAd && ad != null) {
            appOpenAd = null
            isShowingAd = true
            AppOpenManager.isShowingAd = true

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    isShowingAd = false
                    AppOpenManager.isShowingAd = false
                    callback.onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    isShowingAd = false
                    AppOpenManager.isShowingAd = false
                    callback.onAdDismissed()
                }

                override fun onAdShowedFullScreenContent() {
                    isShowingAd = true
                    AppOpenManager.isShowingAd = true
                }
            }
            ad.show(activity)
        } else {
            callback.onAdDismissed()
        }
    }
}
