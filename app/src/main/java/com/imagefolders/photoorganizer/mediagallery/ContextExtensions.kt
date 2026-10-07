package com.imagefolders.photoorganizer.mediagallery

import android.content.Context


fun Context.getScreenWidthPx(): Int {
    return resources.displayMetrics.widthPixels
}

fun Context.getScreenWidthDp(): Int {
    return (resources.displayMetrics.widthPixels / this.resources.displayMetrics.density).toInt()
}