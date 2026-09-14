package com.developer.manali.galleryapp.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaStats(
    val photoCount: Int = 0,
    val photoSize: Long = 0L,
    val videoCount: Int = 0,
    val videoSize: Long = 0L,
    val albumCount: Int = 0
) {
    val totalCount: Int get() = photoCount + videoCount
    val totalSize: Long get() = photoSize + videoSize
}

class MediaRepository {

    sealed class RenameResult {
        data class Success(val updatedItem: MediaItem) : RenameResult()
        data class PermissionRequired(val intentSender: IntentSender) : RenameResult()
        object Failed : RenameResult()
    }

    fun hasCachedPhotos(): Boolean = Companion.hasCachedPhotos()
    fun hasCachedVideos(): Boolean = Companion.hasCachedVideos()
    fun hasCachedAlbums(): Boolean = Companion.hasCachedAlbums()

    suspend fun getMediaStats(context: Context): MediaStats = withContext(Dispatchers.IO) {
        val photos = getPhotos(context)
        val videos = getVideos(context)
        val albums = getAlbums(context)

        val photoSize = photos.sumOf { it.size }
        val videoSize = videos.sumOf { it.size }

        MediaStats(
            photoCount = photos.size,
            photoSize = photoSize,
            videoCount = videos.size,
            videoSize = videoSize,
            albumCount = albums.size
        )
    }

    suspend fun getPhotos(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        cachedPhotos?.let { return@withContext it }
        val photoList = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATA,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.BUCKET_ID else MediaStore.Images.Media.DATA,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.BUCKET_DISPLAY_NAME else MediaStore.Images.Media.DATA
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dataColumn = it.getColumnIndex(MediaStore.Images.Media.DATA)
            val bucketIdCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
            } else -1
            val bucketNameCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            } else -1

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn) ?: "IMG_$id"
                val mime = it.getString(mimeColumn) ?: "image/jpeg"
                var size = it.getLong(sizeColumn)
                var dateAdded = it.getLong(dateColumn)
                val data = if (dataColumn != -1) it.getString(dataColumn) ?: "" else ""
                val bucketId = if (bucketIdCol != -1) it.getString(bucketIdCol) ?: "" else ""
                val rawBucket = if (bucketNameCol != -1) it.getString(bucketNameCol) ?: "" else ""
                var bucketName = if (rawBucket.isNotEmpty()) rawBucket else ""
                if (bucketName.isEmpty() && data.isNotEmpty()) {
                    val lastSlash = data.lastIndexOf('/')
                    if (lastSlash > 0) {
                        val prevSlash = data.lastIndexOf('/', lastSlash - 1)
                        if (prevSlash >= 0) {
                            bucketName = data.substring(prevSlash + 1, lastSlash)
                        }
                    }
                }
                if (bucketName.isEmpty()) bucketName = "Camera"

                if (size <= 0L) size = 1L
                if (dateAdded <= 0L) dateAdded = System.currentTimeMillis() / 1000L

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val header = formatToDateHeader(dateAdded)

                photoList.add(
                    MediaItem(
                        id = id,
                        uri = contentUri,
                        path = if (data.isNotEmpty()) data else contentUri.toString(),
                        displayName = name,
                        mimeType = mime,
                        size = size,
                        dateAdded = dateAdded,
                        isVideo = false,
                        duration = 0L,
                        bucketId = bucketId.ifEmpty { bucketName },
                        bucketName = bucketName,
                        dateHeader = header
                    )
                )
            }
        }
        cachedPhotos = photoList
        photoList
    }

    suspend fun getVideos(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        cachedVideos?.let { return@withContext it }
        val videoList = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.BUCKET_ID else MediaStore.Video.Media.DATA,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Video.Media.DATA
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val cursor = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dataColumn = it.getColumnIndex(MediaStore.Video.Media.DATA)
            val bucketIdCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
            } else -1
            val bucketNameCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            } else -1

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn) ?: "VID_$id"
                val mime = it.getString(mimeColumn) ?: "video/mp4"
                var size = it.getLong(sizeColumn)
                var dateAdded = it.getLong(dateColumn)
                val duration = it.getLong(durationColumn)
                val data = if (dataColumn != -1) it.getString(dataColumn) ?: "" else ""
                val bucketId = if (bucketIdCol != -1) it.getString(bucketIdCol) ?: "" else ""
                val rawBucket = if (bucketNameCol != -1) it.getString(bucketNameCol) ?: "" else ""
                var bucketName = if (rawBucket.isNotEmpty()) rawBucket else ""
                if (bucketName.isEmpty() && data.isNotEmpty()) {
                    val lastSlash = data.lastIndexOf('/')
                    if (lastSlash > 0) {
                        val prevSlash = data.lastIndexOf('/', lastSlash - 1)
                        if (prevSlash >= 0) {
                            bucketName = data.substring(prevSlash + 1, lastSlash)
                        }
                    }
                }
                if (bucketName.isEmpty()) bucketName = "Videos"

                if (size <= 0L) size = 1L
                if (dateAdded <= 0L) dateAdded = System.currentTimeMillis() / 1000L

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val header = formatToDateHeader(dateAdded)

                videoList.add(
                    MediaItem(
                        id = id,
                        uri = contentUri,
                        path = if (data.isNotEmpty()) data else contentUri.toString(),
                        displayName = name,
                        mimeType = mime,
                        size = size,
                        dateAdded = dateAdded,
                        isVideo = true,
                        duration = duration,
                        bucketId = bucketId.ifEmpty { bucketName },
                        bucketName = bucketName,
                        dateHeader = header
                    )
                )
            }
        }
        cachedVideos = videoList
        videoList
    }

    suspend fun getAlbums(context: Context, excludeLocked: Boolean = true): List<AlbumItem> = withContext(Dispatchers.IO) {
        if (excludeLocked) {
            cachedAlbums?.let { return@withContext it }
        } else {
            cachedAlbumsWithLocked?.let { return@withContext it }
        }
        val albumsMap = LinkedHashMap<String, AlbumData>()
        val appPrefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(context)
        val lockedMedia = appPrefs.getLockedMediaIds()
        val lockedAlbums = appPrefs.getLockedAlbumIds()

        val imgProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE
        )
        val imgCursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imgProjection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )
        imgCursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketIdCol = it.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = it.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val dataCol = it.getColumnIndex(MediaStore.Images.Media.DATA)
            val sizeCol = it.getColumnIndex(MediaStore.Images.Media.SIZE)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                var bucketId = if (bucketIdCol != -1) it.getString(bucketIdCol) else null
                var bucketName = if (bucketNameCol != -1) it.getString(bucketNameCol) else null
                val data = if (dataCol != -1) it.getString(dataCol) else null
                var size = if (sizeCol != -1) it.getLong(sizeCol) else 0L
                if (size <= 0L) size = 1L

                if (bucketName.isNullOrEmpty() && !data.isNullOrEmpty()) {
                    val lastSlash = data.lastIndexOf('/')
                    if (lastSlash > 0) {
                        val prevSlash = data.lastIndexOf('/', lastSlash - 1)
                        if (prevSlash >= 0) {
                            bucketName = data.substring(prevSlash + 1, lastSlash)
                        }
                    }
                }
                val finalName = if (!bucketName.isNullOrEmpty()) bucketName else "Photos"
                if (bucketId.isNullOrEmpty()) bucketId = finalName
                
                if (excludeLocked && (lockedAlbums.contains(bucketId) || lockedMedia.contains(id.toString()))) {
                    continue
                }

                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                val album = albumsMap.getOrPut(bucketId) {
                    AlbumData(bucketId, finalName, uri, 0, 0L)
                }
                album.count++
                album.totalSizeBytes += size
            }
        }

        val vidProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE
        )
        val vidCursor = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            vidProjection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )
        vidCursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val bucketIdCol = it.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = it.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val dataCol = it.getColumnIndex(MediaStore.Video.Media.DATA)
            val sizeCol = it.getColumnIndex(MediaStore.Video.Media.SIZE)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                var bucketId = if (bucketIdCol != -1) it.getString(bucketIdCol) else null
                var bucketName = if (bucketNameCol != -1) it.getString(bucketNameCol) else null
                val data = if (dataCol != -1) it.getString(dataCol) else null
                var size = if (sizeCol != -1) it.getLong(sizeCol) else 0L
                if (size <= 0L) size = 1L

                if (bucketName.isNullOrEmpty() && !data.isNullOrEmpty()) {
                    val lastSlash = data.lastIndexOf('/')
                    if (lastSlash > 0) {
                        val prevSlash = data.lastIndexOf('/', lastSlash - 1)
                        if (prevSlash >= 0) {
                            bucketName = data.substring(prevSlash + 1, lastSlash)
                        }
                    }
                }
                val finalName = if (!bucketName.isNullOrEmpty()) bucketName else "Videos"
                if (bucketId.isNullOrEmpty()) bucketId = finalName
                
                if (excludeLocked && (lockedAlbums.contains(bucketId) || lockedMedia.contains(id.toString()))) {
                    continue
                }

                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                val album = albumsMap.getOrPut(bucketId) {
                    AlbumData(bucketId, finalName, uri, 0, 0L)
                }
                album.count++
                album.totalSizeBytes += size
            }
        }

        val result = mutableListOf<AlbumItem>()
        for ((_, data) in albumsMap) {
            result.add(
                AlbumItem(
                    bucketId = data.bucketId,
                    bucketName = data.name,
                    coverUri = data.coverUri,
                    itemCount = data.count,
                    totalSizeBytes = data.totalSizeBytes
                )
            )
        }

        val createdAlbums = appPrefs.getCreatedAlbums()
        for (createdName in createdAlbums) {
            val exists = result.any { it.bucketName.equals(createdName, ignoreCase = true) }
            if (!exists) {
                if (excludeLocked && (lockedAlbums.contains(createdName) || lockedAlbums.contains(createdName.lowercase()))) {
                    continue
                }
                result.add(
                    AlbumItem(
                        bucketId = createdName,
                        bucketName = createdName,
                        coverUri = null,
                        itemCount = 0,
                        totalSizeBytes = 0L
                    )
                )
            }
        }

        try {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val subDirs = picturesDir.listFiles { file -> file.isDirectory }
            subDirs?.forEach { dir ->
                val dirName = dir.name
                if (!dirName.startsWith(".") && !result.any { it.bucketName.equals(dirName, ignoreCase = true) }) {
                    if (!excludeLocked || (!lockedAlbums.contains(dirName) && !lockedAlbums.contains(dirName.lowercase()))) {
                        result.add(
                            AlbumItem(
                                bucketId = dirName,
                                bucketName = dirName,
                                coverUri = null,
                                itemCount = 0,
                                totalSizeBytes = 0L
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        result.sortByDescending { it.itemCount }
        if (excludeLocked) {
            cachedAlbums = result
        } else {
            cachedAlbumsWithLocked = result
        }
        result
    }

    suspend fun getMediaForAlbum(context: Context, bucketId: String, bucketName: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItem>()
        val appPrefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(context)
        val lockedMedia = appPrefs.getLockedMediaIds()
        val lockedAlbums = appPrefs.getLockedAlbumIds()

        val imgProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val imgSelection = "${MediaStore.Images.Media.BUCKET_ID} = ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
        val imgArgs = arrayOf(bucketId, bucketName)

        val imgCursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imgProjection,
            imgSelection,
            imgArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        imgCursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dataCol = it.getColumnIndex(MediaStore.Images.Media.DATA)
            val bIdCol = it.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
            val bNameCol = it.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol) ?: "IMG_$id"
                val mime = it.getString(mimeCol) ?: "image/jpeg"
                var size = it.getLong(sizeCol)
                var dateAdded = it.getLong(dateCol)
                val path = if (dataCol != -1) it.getString(dataCol) ?: "" else ""
                val bId = if (bIdCol != -1) it.getString(bIdCol) ?: "" else ""
                val bName = if (bNameCol != -1) it.getString(bNameCol) ?: "Album" else "Album"
 
                if (lockedAlbums.contains(bId) || lockedMedia.contains(id.toString())) {
                    continue
                }

                if (size <= 0L) size = 1L
                if (dateAdded <= 0L) dateAdded = System.currentTimeMillis() / 1000L

                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                mediaList.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        path = path.ifEmpty { uri.toString() },
                        displayName = name,
                        mimeType = mime,
                        size = size,
                        dateAdded = dateAdded,
                        isVideo = false,
                        duration = 0L,
                        bucketId = bId,
                        bucketName = bName,
                        dateHeader = formatToDateHeader(dateAdded)
                    )
                )
            }
        }

        val vidProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        val vidSelection = "${MediaStore.Video.Media.BUCKET_ID} = ? OR ${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} = ?"
        val vidArgs = arrayOf(bucketId, bucketName)

        val vidCursor = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            vidProjection,
            vidSelection,
            vidArgs,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )

        vidCursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dataCol = it.getColumnIndex(MediaStore.Video.Media.DATA)
            val bIdCol = it.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
            val bNameCol = it.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol) ?: "VID_$id"
                val mime = it.getString(mimeCol) ?: "video/mp4"
                var size = it.getLong(sizeCol)
                var dateAdded = it.getLong(dateCol)
                val duration = it.getLong(durCol)
                val path = if (dataCol != -1) it.getString(dataCol) ?: "" else ""
                val bId = if (bIdCol != -1) it.getString(bIdCol) ?: "" else ""
                val bName = if (bNameCol != -1) it.getString(bNameCol) ?: "Album" else "Album"

                if (lockedAlbums.contains(bId) || lockedMedia.contains(id.toString())) {
                    continue
                }

                if (size <= 0L) size = 1L
                if (dateAdded <= 0L) dateAdded = System.currentTimeMillis() / 1000L

                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                mediaList.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        path = path.ifEmpty { uri.toString() },
                        displayName = name,
                        mimeType = mime,
                        size = size,
                        dateAdded = dateAdded,
                        isVideo = true,
                        duration = duration,
                        bucketId = bId,
                        bucketName = bName,
                        dateHeader = formatToDateHeader(dateAdded)
                    )
                )
            }
        }

        val albumDir = getAlbumDirectory(context, bucketId, bucketName)

        if (albumDir.exists() && albumDir.isDirectory) {
            val existingPaths = mediaList.map { it.path }.toSet()
            val files = albumDir.listFiles()
            if (files != null) {
                for (f in files) {
                    if (f.isFile && !existingPaths.contains(f.absolutePath)) {
                        val name = f.name
                        val lower = name.lowercase(java.util.Locale.US)
                        val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")
                        val isVideo = lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.endsWith(".avi")
                        if (isImage || isVideo) {
                            val hashId = f.absolutePath.hashCode().toLong()
                            if (lockedMedia.contains(hashId.toString())) continue

                            val fileSize = f.length()
                            if (fileSize <= 0L) continue

                            val mime = if (isImage) "image/*" else "video/*"
                            mediaList.add(
                                MediaItem(
                                    id = f.absolutePath.hashCode().toLong(),
                                    uri = Uri.fromFile(f),
                                    path = f.absolutePath,
                                    displayName = name,
                                    mimeType = mime,
                                    size = f.length(),
                                    dateAdded = f.lastModified() / 1000L,
                                    isVideo = isVideo,
                                    duration = 0L,
                                    bucketId = bucketId,
                                    bucketName = bucketName,
                                    dateHeader = formatToDateHeader(f.lastModified() / 1000L)
                                )
                            )
                        }
                    }
                }
            }
        }

        mediaList.sortByDescending { it.dateAdded }
        mediaList
    }

    suspend fun getAlbumDirectory(context: Context, bucketId: String, bucketName: String): File = withContext(Dispatchers.IO) {
        var foundDir: File? = null
        try {
            val imgCursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.DATA),
                "${MediaStore.Images.Media.BUCKET_ID} = ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?",
                arrayOf(bucketId, bucketName),
                null
            )
            imgCursor?.use {
                if (it.moveToFirst()) {
                    val path = it.getString(0)
                    if (!path.isNullOrEmpty()) {
                        val p = File(path).parentFile
                        if (p != null && p.exists() && p.isDirectory) foundDir = p
                    }
                }
            }

            if (foundDir == null) {
                val vidCursor = context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media.DATA),
                    "${MediaStore.Video.Media.BUCKET_ID} = ? OR ${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} = ?",
                    arrayOf(bucketId, bucketName),
                    null
                )
                vidCursor?.use {
                    if (it.moveToFirst()) {
                        val path = it.getString(0)
                        if (!path.isNullOrEmpty()) {
                            val p = File(path).parentFile
                            if (p != null && p.exists() && p.isDirectory) foundDir = p
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (foundDir != null) {
            foundDir!!
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val picFolder = File(picturesDir, bucketName)
            if (picFolder.exists()) {
                picFolder
            } else {
                val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                val dcimFolder = File(dcimDir, bucketName)
                if (dcimFolder.exists()) dcimFolder else picFolder
            }
        }
    }

    suspend fun getAllMediaInAlbums(context: Context, albums: List<AlbumItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<MediaItem>()
        if (albums.isEmpty()) return@withContext resultList

        val bucketIds = albums.map { it.bucketId }.filter { it.isNotEmpty() }.toSet()
        val bucketNames = albums.map { it.bucketName.lowercase() }.filter { it.isNotEmpty() }.toSet()

        val extraBucketIds = mutableSetOf<String>()
        for (album in albums) {
            try {
                val dir = getAlbumDirectory(context, album.bucketId, album.bucketName)
                if (dir.exists()) {
                    extraBucketIds.add(dir.absolutePath.lowercase().hashCode().toString())
                }
            } catch (_: Exception) {}
        }
        val allBucketIds = bucketIds + extraBucketIds

        // Query Images
        try {
            val proj = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                proj,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                val bIdCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
                val bNameCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val bId = if (bIdCol != -1) cursor.getString(bIdCol) else null
                    val bName = if (bNameCol != -1) cursor.getString(bNameCol) else null
                    val path = if (dataCol != -1) cursor.getString(dataCol) else null
                    val parentName = path?.let { try { File(it).parentFile?.name?.lowercase() } catch(_: Exception) { null } }

                    val matches = (bId != null && allBucketIds.contains(bId)) ||
                                  (bName != null && bucketNames.contains(bName.lowercase())) ||
                                  (parentName != null && bucketNames.contains(parentName))
                    if (matches) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        resultList.add(MediaItem(id = id, uri = uri, path = path ?: "", displayName = "", mimeType = "image/*", size = 0L, dateAdded = 0L, isVideo = false))
                    }
                }
            }
        } catch (_: Exception) {}

        // Query Videos
        try {
            val proj = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME
            )
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                proj,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val bIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                val bNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val bId = if (bIdCol != -1) cursor.getString(bIdCol) else null
                    val bName = if (bNameCol != -1) cursor.getString(bNameCol) else null
                    val path = if (dataCol != -1) cursor.getString(dataCol) else null
                    val parentName = path?.let { try { File(it).parentFile?.name?.lowercase() } catch(_: Exception) { null } }

                    val matches = (bId != null && allBucketIds.contains(bId)) ||
                                  (bName != null && bucketNames.contains(bName.lowercase())) ||
                                  (parentName != null && bucketNames.contains(parentName))
                    if (matches) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        resultList.add(MediaItem(id = id, uri = uri, path = path ?: "", displayName = "", mimeType = "video/*", size = 0L, dateAdded = 0L, isVideo = true))
                    }
                }
            }
        } catch (_: Exception) {}

        resultList
    }

    suspend fun moveMediaItems(
        context: Context,
        items: List<MediaItem>,
        targetAlbum: AlbumItem?,
        targetAlbumName: String
    ): Pair<Int, List<Uri>> = withContext(Dispatchers.IO) {
        clearCache()
        val destDir = if (targetAlbum != null) {
            getAlbumDirectory(context, targetAlbum.bucketId, targetAlbum.bucketName)
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            File(picturesDir, targetAlbumName).apply {
                if (!exists()) mkdirs()
            }
        }

        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        var movedCount = 0
        val scannedPaths = mutableListOf<String>()
        val oldPaths = mutableListOf<String>()
        val pendingDeleteUris = mutableListOf<Uri>()

        for (item in items) {
            try {
                val oldPath = item.path
                val oldFile = if (oldPath.isNotEmpty()) File(oldPath) else null

                if (oldFile != null && oldFile.parentFile?.absolutePath == destDir.absolutePath) {
                    continue
                }

                val fileName = if (oldFile != null && oldFile.name.isNotEmpty()) {
                    oldFile.name
                } else {
                    item.displayName.ifEmpty { "MEDIA_${item.id}_${System.currentTimeMillis()}" }
                }

                var destFile = File(destDir, fileName)
                if (destFile.exists() && destFile.absolutePath != oldPath) {
                    val dotIdx = fileName.lastIndexOf('.')
                    val base = if (dotIdx != -1) fileName.substring(0, dotIdx) else fileName
                    val ext = if (dotIdx != -1) fileName.substring(dotIdx) else ""
                    destFile = File(destDir, "${base}_${System.currentTimeMillis()}$ext")
                }

                var success = false

                if (oldFile != null && oldFile.exists()) {
                    if (oldFile.renameTo(destFile)) {
                        success = true
                    }
                }

                if (!success) {
                    val inputStream = if (oldFile != null && oldFile.exists()) {
                        oldFile.inputStream()
                    } else {
                        context.contentResolver.openInputStream(item.uri)
                    }

                    inputStream?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (destFile.exists() && destFile.length() > 0) {
                        success = true
                        if (oldFile != null && oldFile.exists() && oldFile.absolutePath != destFile.absolutePath) {
                            try {
                                oldFile.delete()
                            } catch (_: Exception) {}
                        }
                    }
                }

                if (success) {
                    movedCount++
                    scannedPaths.add(destFile.absolutePath)

                    var deletedSilently = false
                    try {
                        val rows = context.contentResolver.delete(item.uri, null, null)
                        if (rows > 0) deletedSilently = true
                    } catch (_: Exception) {}

                    if (!deletedSilently && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pendingDeleteUris.add(item.uri)
                    }

                    if (item.path.isNotEmpty() && item.path != destFile.absolutePath) {
                        oldPaths.add(item.path)
                    }
                }
            } catch (_: Exception) {}
        }

        for (p in oldPaths) {
            try {
                val f = File(p)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }

        val allToScan = mutableListOf<String>()
        allToScan.addAll(oldPaths)
        allToScan.addAll(scannedPaths)

        if (allToScan.isNotEmpty()) {
            val latch = java.util.concurrent.CountDownLatch(allToScan.size)
            try {
                MediaScannerConnection.scanFile(
                    context,
                    allToScan.toTypedArray(),
                    null
                ) { _, _ ->
                    latch.countDown()
                }
                latch.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: Exception) {}
        }

        Pair(movedCount, pendingDeleteUris)
    }

    suspend fun copyMediaItems(
        context: Context,
        items: List<MediaItem>,
        targetAlbum: AlbumItem?,
        targetAlbumName: String
    ): Int = withContext(Dispatchers.IO) {
        clearCache()
        val destDir = if (targetAlbum != null) {
            getAlbumDirectory(context, targetAlbum.bucketId, targetAlbum.bucketName)
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            File(picturesDir, targetAlbumName).apply {
                if (!exists()) mkdirs()
            }
        }

        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        var copiedCount = 0
        val scannedPaths = mutableListOf<String>()

        for (item in items) {
            try {
                val oldPath = item.path
                val oldFile = if (oldPath.isNotEmpty()) File(oldPath) else null

                val fileName = if (oldFile != null && oldFile.name.isNotEmpty()) {
                    oldFile.name
                } else {
                    item.displayName.ifEmpty { "MEDIA_${item.id}_${System.currentTimeMillis()}" }
                }

                var destFile = File(destDir, fileName)
                if (destFile.exists()) {
                    val dotIdx = fileName.lastIndexOf('.')
                    val base = if (dotIdx != -1) fileName.substring(0, dotIdx) else fileName
                    val ext = if (dotIdx != -1) fileName.substring(dotIdx) else ""
                    destFile = File(destDir, "${base}_copy_${System.currentTimeMillis()}$ext")
                }

                val inputStream = if (oldFile != null && oldFile.exists()) {
                    oldFile.inputStream()
                } else {
                    context.contentResolver.openInputStream(item.uri)
                }

                inputStream?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (destFile.exists() && destFile.length() > 0) {
                    copiedCount++
                    scannedPaths.add(destFile.absolutePath)
                }
            } catch (_: Exception) {}
        }

        if (scannedPaths.isNotEmpty()) {
            val latch = java.util.concurrent.CountDownLatch(scannedPaths.size)
            try {
                MediaScannerConnection.scanFile(
                    context,
                    scannedPaths.toTypedArray(),
                    null
                ) { _, _ ->
                    latch.countDown()
                }
                latch.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: Exception) {}
        }

        copiedCount
    }

    suspend fun renameMediaItem(
        context: Context,
        item: MediaItem,
        newName: String
    ): RenameResult = withContext(Dispatchers.IO) {
        try {
            val originalExtension = when {
                item.displayName.contains(".") -> "." + item.displayName.substringAfterLast(".")
                item.path.contains(".") -> "." + item.path.substringAfterLast(".")
                item.isVideo -> ".mp4"
                else -> ".jpg"
            }

            val finalDisplayName = if (newName.contains(".")) {
                newName.trim()
            } else {
                "${newName.trim()}$originalExtension"
            }

            if (finalDisplayName.isEmpty() || finalDisplayName == item.displayName) {
                return@withContext RenameResult.Success(item)
            }

            val baseTitle = if (finalDisplayName.contains(".")) finalDisplayName.substringBeforeLast(".") else finalDisplayName
            val oldFile = if (item.path.isNotEmpty()) File(item.path) else null
            val parentDir = oldFile?.parentFile

            // 1. Direct file rename if permitted on disk (pre-Q or legacy external storage)
            if (oldFile != null && oldFile.exists() && parentDir != null && parentDir.exists()) {
                val destFile = File(parentDir, finalDisplayName)
                if (destFile.exists() && destFile.absolutePath != oldFile.absolutePath) {
                    return@withContext RenameResult.Failed
                }
                if (oldFile.renameTo(destFile)) {
                    clearCache()
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, finalDisplayName)
                            put(MediaStore.MediaColumns.TITLE, baseTitle)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                @Suppress("DEPRECATION")
                                put(MediaStore.MediaColumns.DATA, destFile.absolutePath)
                            }
                            put(MediaStore.MediaColumns.SIZE, destFile.length())
                        }
                        context.contentResolver.update(item.uri, values, null, null)
                    } catch (_: Exception) {}

                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(destFile.absolutePath, oldFile.absolutePath),
                        null,
                        null
                    )

                    val updatedItem = item.copy(
                        displayName = finalDisplayName,
                        path = destFile.absolutePath
                    )
                    return@withContext RenameResult.Success(updatedItem)
                }
            }

            // 2. Direct File.renameTo did not succeed (Scoped Storage on Android 10+).
            // Updating MediaStore DISPLAY_NAME renames the physical file in-place on Android 10+ without copying.
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, finalDisplayName)
                put(MediaStore.MediaColumns.TITLE, baseTitle)
            }

            try {
                val rows = context.contentResolver.update(item.uri, values, null, null)
                if (rows > 0) {
                    clearCache()
                    var updatedPath = item.path
                    try {
                        context.contentResolver.query(
                            item.uri,
                            arrayOf(MediaStore.MediaColumns.DATA),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                                if (idx != -1) {
                                    val p = cursor.getString(idx)
                                    if (!p.isNullOrEmpty()) {
                                        updatedPath = p
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    if (updatedPath.isNotEmpty()) {
                        MediaScannerConnection.scanFile(context, arrayOf(updatedPath), null, null)
                    }

                    val updatedItem = item.copy(
                        displayName = finalDisplayName,
                        path = updatedPath
                    )
                    return@withContext RenameResult.Success(updatedItem)
                }
            } catch (recoverable: android.app.RecoverableSecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return@withContext RenameResult.PermissionRequired(recoverable.userAction.actionIntent.intentSender)
                }
            } catch (sec: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val pi = MediaStore.createWriteRequest(context.contentResolver, listOf(item.uri))
                        return@withContext RenameResult.PermissionRequired(pi.intentSender)
                    } catch (_: Exception) {}
                }
            }

            RenameResult.Failed
        } catch (e: Exception) {
            e.printStackTrace()
            RenameResult.Failed
        }
    }

    suspend fun applyWallpaper(
        context: Context,
        item: MediaItem,
        whichFlag: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val wallpaperManager = android.app.WallpaperManager.getInstance(context)

            var streamSuccess = false
            try {
                val inputStream = if (item.path.isNotEmpty()) {
                    val f = File(item.path)
                    if (f.exists()) f.inputStream() else context.contentResolver.openInputStream(item.uri)
                } else {
                    context.contentResolver.openInputStream(item.uri)
                }

                inputStream?.use { stream ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val result = wallpaperManager.setStream(stream, null, true, whichFlag)
                        if (result > 0) streamSuccess = true
                    } else {
                        wallpaperManager.setStream(stream)
                        streamSuccess = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (streamSuccess) {
                return@withContext true
            }

            var bitmap: android.graphics.Bitmap? = null
            if (item.path.isNotEmpty()) {
                val f = File(item.path)
                if (f.exists()) {
                    bitmap = android.graphics.BitmapFactory.decodeFile(item.path)
                }
            }
            if (bitmap == null) {
                context.contentResolver.openInputStream(item.uri)?.use { stream ->
                    bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                }
            }

            if (bitmap != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val result = wallpaperManager.setBitmap(bitmap, null, true, whichFlag)
                    return@withContext result > 0
                } else {
                    wallpaperManager.setBitmap(bitmap)
                    return@withContext true
                }
            }

            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private data class AlbumData(
        val bucketId: String,
        val name: String,
        var coverUri: Uri?,
        var count: Int,
        var totalSizeBytes: Long = 0L
    )

    companion object {
        var databaseChanged = false
        private var cachedPhotos: List<MediaItem>? = null
        private var cachedVideos: List<MediaItem>? = null
        private var cachedAlbums: List<AlbumItem>? = null
        private var cachedAlbumsWithLocked: List<AlbumItem>? = null

        fun hasCachedPhotos(): Boolean = cachedPhotos != null
        fun hasCachedVideos(): Boolean = cachedVideos != null
        fun hasCachedAlbums(): Boolean = cachedAlbums != null

        fun clearCache() {
            cachedPhotos = null
            cachedVideos = null
            cachedAlbums = null
            cachedAlbumsWithLocked = null
        }

        fun albumExists(context: Context, name: String): Boolean {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return false
            val appPrefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(context)
            if (appPrefs.getCreatedAlbums().any { it.equals(trimmed, ignoreCase = true) }) {
                return true
            }
            if (cachedAlbums?.any { it.bucketName.equals(trimmed, ignoreCase = true) } == true) {
                return true
            }
            if (cachedAlbumsWithLocked?.any { it.bucketName.equals(trimmed, ignoreCase = true) } == true) {
                return true
            }

            try {
                val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val dir = File(baseDir, trimmed)
                if (dir.exists()) return true
                val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                val dirDcim = File(dcimDir, trimmed)
                if (dirDcim.exists()) return true
            } catch (_: Exception) {}

            try {
                val projection = arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val col = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    if (col != -1) {
                        while (cursor.moveToNext()) {
                            val bName = cursor.getString(col)
                            if (bName != null && bName.equals(trimmed, ignoreCase = true)) {
                                return true
                            }
                        }
                    }
                }
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val col = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    if (col != -1) {
                        while (cursor.moveToNext()) {
                            val bName = cursor.getString(col)
                            if (bName != null && bName.equals(trimmed, ignoreCase = true)) {
                                return true
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            return false
        }

        fun formatDuration(durationMs: Long): String {
            if (durationMs <= 0) return "0:00"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }

        fun formatExactDate(dateAddedSeconds: Long): String {
            if (dateAddedSeconds <= 0L) return ""
            val dateMillis = if (dateAddedSeconds > 100000000000L) dateAddedSeconds else dateAddedSeconds * 1000L
            return SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date(dateMillis))
        }

        fun formatToDateHeader(dateAddedSeconds: Long): String {
            if (dateAddedSeconds <= 0L) return ""
            val dateMillis = if (dateAddedSeconds > 100000000000L) dateAddedSeconds else dateAddedSeconds * 1000L
            return SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date(dateMillis))
        }

        fun formatFileSize(sizeBytes: Long): String {
            if (sizeBytes <= 0) return "0 B"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.getDefault(), "%.1f GB", gb)
                mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.getDefault(), "%.1f KB", kb)
                else -> "$sizeBytes B"
            }
        }
    }
}
