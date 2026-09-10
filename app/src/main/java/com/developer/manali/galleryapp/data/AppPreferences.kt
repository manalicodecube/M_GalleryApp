package com.developer.manali.galleryapp.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var albumsGridColumns: Int
        get() = prefs.getInt(KEY_ALBUMS_COLUMNS, 2)
        set(value) = prefs.edit().putInt(KEY_ALBUMS_COLUMNS, value).apply()

    var gridColumns: Int
        get() = prefs.getInt(KEY_MEDIA_COLUMNS, prefs.getInt(KEY_PHOTOS_COLUMNS, 3))
        set(value) {
            prefs.edit()
                .putInt(KEY_MEDIA_COLUMNS, value)
                .putInt(KEY_PHOTOS_COLUMNS, value)
                .putInt(KEY_VIDEOS_COLUMNS, value)
                .apply()
        }

    var photosGridColumns: Int
        get() = gridColumns
        set(value) {
            gridColumns = value
        }

    var videosGridColumns: Int
        get() = gridColumns
        set(value) {
            gridColumns = value
        }

    var albumsViewType: String
        get() = prefs.getString(KEY_ALBUMS_VIEW_TYPE, VIEW_TYPE_GRID) ?: VIEW_TYPE_GRID
        set(value) = prefs.edit().putString(KEY_ALBUMS_VIEW_TYPE, value).apply()

    val isAlbumsListView: Boolean
        get() = albumsViewType == VIEW_TYPE_LIST

    var albumsSortBy: String
        get() = prefs.getString(KEY_ALBUMS_SORT_BY, SORT_NAME_ASC) ?: SORT_NAME_ASC
        set(value) = prefs.edit().putString(KEY_ALBUMS_SORT_BY, value).apply()

    var photosViewType: String
        get() = prefs.getString(KEY_PHOTOS_VIEW_TYPE, VIEW_TYPE_GRID) ?: VIEW_TYPE_GRID
        set(value) = prefs.edit().putString(KEY_PHOTOS_VIEW_TYPE, value).apply()

    val isPhotosListView: Boolean
        get() = photosViewType == VIEW_TYPE_LIST

    var photosSortBy: String
        get() = prefs.getString(KEY_PHOTOS_SORT_BY, SORT_NEWEST) ?: SORT_NEWEST
        set(value) = prefs.edit().putString(KEY_PHOTOS_SORT_BY, value).apply()

    var videosViewType: String
        get() = prefs.getString(KEY_VIDEOS_VIEW_TYPE, VIEW_TYPE_GRID) ?: VIEW_TYPE_GRID
        set(value) = prefs.edit().putString(KEY_VIDEOS_VIEW_TYPE, value).apply()

    val isVideosListView: Boolean
        get() = videosViewType == VIEW_TYPE_LIST

    var videosSortBy: String
        get() = prefs.getString(KEY_VIDEOS_SORT_BY, SORT_NEWEST) ?: SORT_NEWEST
        set(value) = prefs.edit().putString(KEY_VIDEOS_SORT_BY, value).apply()

    var viewType: String
        get() = photosViewType
        set(value) {
            photosViewType = value
            videosViewType = value
        }

    val isListView: Boolean
        get() = viewType == VIEW_TYPE_LIST

    var sortBy: String
        get() = photosSortBy
        set(value) {
            photosSortBy = value
            videosSortBy = value
            albumsSortBy = value
        }

    fun getFavoriteIds(): MutableSet<String> {
        return prefs.getStringSet(KEY_FAVORITE_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun isFavorite(id: Long): Boolean {
        return getFavoriteIds().contains(id.toString())
    }

    fun addFavorite(id: Long) {
        val set = getFavoriteIds()
        set.add(id.toString())
        prefs.edit().putStringSet(KEY_FAVORITE_IDS, set).apply()
    }

    fun removeFavorite(id: Long) {
        val set = getFavoriteIds()
        set.remove(id.toString())
        prefs.edit().putStringSet(KEY_FAVORITE_IDS, set).apply()
    }

    fun toggleFavorite(id: Long): Boolean {
        val set = getFavoriteIds()
        val isFav = if (set.contains(id.toString())) {
            set.remove(id.toString())
            false
        } else {
            set.add(id.toString())
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITE_IDS, set).apply()
        return isFav
    }

    fun setFavorites(ids: List<Long>, favorite: Boolean) {
        com.developer.manali.galleryapp.data.MediaRepository.clearCache()
        val set = getFavoriteIds()
        if (favorite) {
            set.addAll(ids.map { it.toString() })
        } else {
            set.removeAll(ids.map { it.toString() }.toSet())
        }
        prefs.edit().putStringSet(KEY_FAVORITE_IDS, set).apply()
    }

    fun getCreatedAlbums(): MutableSet<String> {
        return prefs.getStringSet(KEY_CREATED_ALBUMS, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun addCreatedAlbum(name: String) {
        com.developer.manali.galleryapp.data.MediaRepository.clearCache()
        val set = getCreatedAlbums()
        set.add(name)
        prefs.edit().putStringSet(KEY_CREATED_ALBUMS, set).apply()
    }

    var isEqualizerEnabled: Boolean
        get() = prefs.getBoolean(KEY_EQUALIZER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_EQUALIZER_ENABLED, value).apply()

    fun getEqualizerBandLevel(band: Short): Short {
        val level = prefs.getInt("${KEY_EQUALIZER_BAND_LEVEL}_$band", 0)
        return level.toShort()
    }

    fun setEqualizerBandLevel(band: Short, level: Short) {
        prefs.edit().putInt("${KEY_EQUALIZER_BAND_LEVEL}_$band", level.toInt()).apply()
    }

    var appLockPin: String
        get() = prefs.getString(KEY_APP_LOCK_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_APP_LOCK_PIN, value).apply()

    var securityQuestion: String
        get() = prefs.getString(KEY_SECURITY_QUESTION, "What is your favorite pet's name?") ?: "What is your favorite pet's name?"
        set(value) = prefs.edit().putString(KEY_SECURITY_QUESTION, value).apply()

    var securityAnswer: String
        get() = prefs.getString(KEY_SECURITY_ANSWER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SECURITY_ANSWER, value).apply()

    fun getLockedAlbumIds(): MutableSet<String> {
        return prefs.getStringSet(KEY_LOCKED_ALBUMS, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun setLockedAlbums(ids: List<String>, lock: Boolean) {
        com.developer.manali.galleryapp.data.MediaRepository.clearCache()
        val set = getLockedAlbumIds()
        if (lock) {
            set.addAll(ids)
        } else {
            set.removeAll(ids.toSet())
        }
        prefs.edit().putStringSet(KEY_LOCKED_ALBUMS, set).apply()
    }

    fun getLockedMediaIds(): MutableSet<String> {
        return prefs.getStringSet(KEY_LOCKED_MEDIA_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun setLockedMedia(ids: List<String>, lock: Boolean) {
        com.developer.manali.galleryapp.data.MediaRepository.clearCache()
        val set = getLockedMediaIds()
        if (lock) {
            set.addAll(ids)
        } else {
            set.removeAll(ids.toSet())
        }
        prefs.edit().putStringSet(KEY_LOCKED_MEDIA_IDS, set).apply()
    }

    companion object {
        const val VIEW_TYPE_GRID = "grid"
        const val VIEW_TYPE_LIST = "list"

        const val SORT_NEWEST = "newest"
        const val SORT_OLDEST = "oldest"
        const val SORT_NAME_ASC = "name_asc"
        const val SORT_NAME_DESC = "name_desc"

        private const val PREFS_NAME = "lumina_gallery_preferences"
        private const val KEY_MEDIA_COLUMNS = "key_media_grid_columns"
        private const val KEY_ALBUMS_COLUMNS = "key_albums_grid_columns"
        private const val KEY_PHOTOS_COLUMNS = "key_photos_grid_columns"
        private const val KEY_VIDEOS_COLUMNS = "key_videos_grid_columns"
        private const val KEY_ALBUMS_VIEW_TYPE = "key_albums_view_type"
        private const val KEY_ALBUMS_SORT_BY = "key_albums_sort_by"
        private const val KEY_PHOTOS_VIEW_TYPE = "key_photos_view_type"
        private const val KEY_PHOTOS_SORT_BY = "key_photos_sort_by"
        private const val KEY_VIDEOS_VIEW_TYPE = "key_videos_view_type"
        private const val KEY_VIDEOS_SORT_BY = "key_videos_sort_by"
        private const val KEY_VIEW_TYPE = "key_view_type"
        private const val KEY_SORT_BY = "key_sort_by"
        private const val KEY_FAVORITE_IDS = "key_favorite_ids"
        private const val KEY_CREATED_ALBUMS = "key_created_albums"
        private const val KEY_EQUALIZER_ENABLED = "key_equalizer_enabled"
        private const val KEY_EQUALIZER_BAND_LEVEL = "key_equalizer_band_level"
        private const val KEY_APP_LOCK_PIN = "key_app_lock_pin"
        private const val KEY_SECURITY_QUESTION = "key_security_question"
        private const val KEY_SECURITY_ANSWER = "key_security_answer"

    private const val KEY_LOCKED_ALBUMS = "key_locked_albums"
    private const val KEY_LOCKED_MEDIA_IDS = "key_locked_media_ids"

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
