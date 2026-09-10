package com.developer.manali.galleryapp.data

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val isVideo: Boolean = false,
    val duration: Long = 0L,
    val bucketId: String = "",
    val bucketName: String = "",
    val dateHeader: String = ""
) : Parcelable

data class AlbumItem(
    val bucketId: String,
    val bucketName: String,
    val coverUri: Uri?,
    val itemCount: Int,
    val totalSizeBytes: Long = 0L,
    val isFavorites: Boolean = false,
    val isHidden: Boolean = false
)

sealed class PhotoListItem {
    data class Header(val title: String) : PhotoListItem()
    data class Media(val item: MediaItem) : PhotoListItem()
}

sealed class VideoListItem {
    data class Featured(val item: MediaItem) : VideoListItem()
    data class Header(val title: String) : VideoListItem()
    data class Video(val item: MediaItem) : VideoListItem()
}
