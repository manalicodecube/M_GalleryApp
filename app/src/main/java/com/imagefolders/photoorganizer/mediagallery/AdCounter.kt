package com.imagefolders.photoorganizer.mediagallery

object AdCounter {
    var clickCount = 0
    var hasShownAd = false
    var isProcessingAd = false

    @Synchronized
    fun shouldShowAd(): Boolean {
        if (!hasShownAd) {
            clickCount++
            if (clickCount == 2) {
                hasShownAd = true
                isProcessingAd = true
                return true
            }
        }
        return false
    }

    fun onAdFinished() {
        isProcessingAd = false
    }
}
