package com.imagefolders.photoorganizer.mediagallery

import android.content.Context
import com.google.android.gms.ads.AdSize

object AdsUtils {
    const val maxAdContentRating = "G"

    fun isConnected(context: Context): Boolean {
        return Util.isNetworkAvailable(context)
    }

    fun getBannerAdSize(context: Context): AdSize {
        val widthDp = (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
        return AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(context, widthDp)
    }
}

fun Context.getBannerAdSize(): AdSize {
    return AdsUtils.getBannerAdSize(this)
}