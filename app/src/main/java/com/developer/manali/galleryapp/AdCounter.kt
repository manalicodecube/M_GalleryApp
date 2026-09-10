package com.developer.manali.galleryapp

object AdCounter {
    var clickCount = 0
    var hasShownAd = false

    fun shouldShowAd(): Boolean {
        if (!hasShownAd) {
            clickCount++
            if (clickCount == 2) {
                hasShownAd = true
                return true
            }
        }
        return false
    }
}
