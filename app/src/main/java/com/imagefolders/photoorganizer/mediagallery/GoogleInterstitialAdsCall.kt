package com.imagefolders.photoorganizer.mediagallery

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Window
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

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

        mainHandler.post {
            if (activity.isFinishing || activity.isDestroyed) {
                interstitialAdCallback.onAdClose()
                return@post
            }

            var callbackTriggered = false
            fun safeCallback() {
                if (!callbackTriggered) {
                    callbackTriggered = true
                    cancelTimeout()
                    dismissLoadingDialog()
                    interstitialAdCallback.onAdClose()
                }
            }

            showLoadingDialog(activity)

            // Setup timeout safety: 7 seconds max wait
            cancelTimeout()
            timeoutRunnable = Runnable {
                safeCallback()
            }
            mainHandler.postDelayed(timeoutRunnable!!, 7000)

            val cachedAd = admobInterstitial
            if (cachedAd != null) {
                admobInterstitial = null
                setupFullScreenCallback(activity, cachedAd, object : InterstitialAdCallback {
                    override fun onAdClose() {
                        safeCallback()
                    }
                })
                cachedAd.show(activity)
                AppOpenManager.isShowingAd = true
                return@post
            }

            val adRequest = getAdRequest()
            InterstitialAd.load(
                activity,
                activity.getString(R.string.admob_inter_language),
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        if (activity.isFinishing || activity.isDestroyed || callbackTriggered) {
                            dismissLoadingDialog()
                            return
                        }
                        setupFullScreenCallback(activity, interstitialAd, object : InterstitialAdCallback {
                            override fun onAdClose() {
                                safeCallback()
                            }
                        })
                        interstitialAd.show(activity)
                        AppOpenManager.isShowingAd = true
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        admobInterstitial = null
                        AppOpenManager.isShowingAd = false
                        safeCallback()
                    }
                }
            )
        }
    }

    private fun setupFullScreenCallback(
        activity: Activity,
        interstitialAd: InterstitialAd,
        callback: InterstitialAdCallback,
    ) {
        interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {

            override fun onAdDismissedFullScreenContent() {
                cancelTimeout()
                dismissLoadingDialog()
                AppOpenManager.isShowingAd = false
                admobInterstitial = null
                preloadInterstitial(activity.applicationContext)
                callback.onAdClose()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                cancelTimeout()
                dismissLoadingDialog()
                AppOpenManager.isShowingAd = false
                admobInterstitial = null
                callback.onAdClose()
            }

            override fun onAdShowedFullScreenContent() {
                cancelTimeout()
                dismissLoadingDialog()
                AppOpenManager.isShowingAd = true
                admobInterstitial = null
            }
        }
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun showLoadingDialog(activity: Activity) {
        try {
            if (activity.isFinishing || activity.isDestroyed) return
            dismissLoadingDialog()
            val dialog = Dialog(activity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_interstitial_loading)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
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