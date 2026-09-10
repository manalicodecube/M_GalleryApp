package com.developer.manali.galleryapp

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
object BannerIdAds {

    var isLoadingAds = false
    private var isWaterfallRunning = false

    fun loadCallerIdAds(context: Context) {
        if (isLoadingAds || isWaterfallRunning) return
        isLoadingAds = true
        isWaterfallRunning = true
        preLoadBanner(context)
    }

    private var mBannerAdView: AdView? = null
    fun clearBannerAdView() {
        try {
            val parentView = mBannerAdView?.parent
            if (parentView is ViewGroup) {
                parentView.removeView(mBannerAdView)
            }
        } catch (_: Exception) {
        }
        mBannerAdView?.destroy()
        mBannerAdView = null
    }

    fun hasBannerAdAvailable(): Boolean = mBannerAdView != null
    fun getBannerAd(): AdView? = mBannerAdView

    var isBannerPendingToShow = false
    private fun preLoadBanner(context: Context) {
        clearBannerAdView()
        AdView(context).apply {
            adUnitId = context.getString(R.string.admob_banner_big)
            Log.d("VVC", "preLoadBanner: " + context.getString(R.string.admob_banner_big))
            setAdSize(context.getBannerAdSize())
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    isLoadingAds = false
                    isWaterfallRunning = false
                    isBannerPendingToShow = true
                    mBannerAdView = this@apply
                    onCallerBannerAdsLoaded(this@apply)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    super.onAdFailedToLoad(adError)
                    isLoadingAds = false
                    isWaterfallRunning = false
                    isBannerPendingToShow = false
                    onCallerAdsFailedToLoad()
                }

                override fun onAdImpression() {
                    super.onAdImpression()
                    isBannerPendingToShow = false
                    onCallerAdsImpression()
                }
            }
            val adRequest = AdRequest.Builder().build()
            loadAd(adRequest)
            isLoadingAds = true
        }
    }

    interface BannerAdsListener {
        fun cAdsLoadedBanner(adView: AdView)
        fun cAdsImpression()
        fun cAdsFailedToLoad()
    }

    private var mCallerAdsListeners = arrayListOf<BannerAdsListener>()
    fun registerCallerAdsListener(listener: BannerAdsListener) {
        synchronized(mCallerAdsListeners) {
            mCallerAdsListeners.add(listener)
        }
    }

    fun unregisterCallerAdsListener(listener: BannerAdsListener) {
        synchronized(mCallerAdsListeners) {
            mCallerAdsListeners.remove(listener)
        }
    }

    private fun onCallerBannerAdsLoaded(adView: AdView) {
        val listenersCopy = synchronized(mCallerAdsListeners) {
            mCallerAdsListeners.toList()
        }
        listenersCopy.forEach { it.cAdsLoadedBanner(adView) }
    }


    private fun onCallerAdsImpression() {
        val listenersCopy = synchronized(mCallerAdsListeners) {
            mCallerAdsListeners.toList()
        }
        listenersCopy.forEach { it.cAdsImpression() }
    }

    private fun onCallerAdsFailedToLoad() {
        val listenersCopy = synchronized(mCallerAdsListeners) {
            mCallerAdsListeners.toList()
        }
        listenersCopy.forEach { it.cAdsFailedToLoad() }
    }

    fun isAnyAdsPendingToShow(): Boolean {
        return isLoadingAds || isWaterfallRunning || isBannerPendingToShow
    }

    fun preLoadCallerIdAds(context: Context) {
        if (BannerNetStatusManager.isConnected && !isAnyAdsPendingToShow() && !BannerAdActivityStateTracker.isCallerIdActivityOpen.value) {
            loadCallerIdAds(
                context
            )
        }
    }

}