package com.imagefolders.photoorganizer.mediagallery.data

object MediaDataHolder {
    var mediaList: List<MediaItem>? = null
    var videoList: List<MediaItem>? = null

    fun clear() {
        mediaList = null
        videoList = null
    }
}
