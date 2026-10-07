package com.imagefolders.photoorganizer.mediagallery

import android.app.Activity
import android.content.res.Resources
import android.widget.RelativeLayout
import android.widget.TextView
import com.imagefolders.photoorganizer.mediagallery.databinding.ShimmerCiAdaptiveBannerLayoutBinding

fun Activity.updateUIMainLayout(rlMainGoogleBanner: RelativeLayout, tvSpaceAds: TextView) {
    rlMainGoogleBanner.apply {
        this@apply.setBackgroundColor(getColor(R.color.callerAdBackground))
    }
    tvSpaceAds.apply {
        setTextColor(getColor(R.color.callerAdBodyColor))
    }
}

val defaultBannerHeightPx: Int = 60.dpToPx
fun Activity.updateUIShimmerLayout(shimmerBannerLayoutBinding: ShimmerCiAdaptiveBannerLayoutBinding) {
    shimmerBannerLayoutBinding.apply {
        val views = listOf(tv1, iv1)
        views.forEach { view ->
            view.setBackgroundColor(getColor(R.color.callerShimmerAnimationColor))
        }
    }
}

val Int.dpToPx: Int get() = (toFloat() * Resources.getSystem().displayMetrics.density + 0.5f).toInt()
