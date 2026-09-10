package com.developer.manali.galleryapp

import android.content.Context
import com.google.android.gms.ads.AdSize

object AdsUtils {
    const val maxAdContentRating = "G"

    fun isConnected(context: Context): Boolean {
        return Util.isNetworkAvailable(context)
    }
}

fun Context.getBannerAdSize(): AdSize {
    return AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(this, getScreenWidthDp())
}