package com.developer.manali.galleryapp.data

object MediaDataHolder {
    var mediaList: List<MediaItem>? = null
    var videoList: List<MediaItem>? = null

    fun clear() {
        mediaList = null
        videoList = null
    }
}
