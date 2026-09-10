package com.developer.manali.galleryapp

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

class PreferencesUtility private constructor(context: Context) {

    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    companion object {
        private const val SHARED_PREFS_FILE_NAME = "ScreenMirrorig_shared_prefs"
        const val IS_ACCEPT_PRIVACY = "is_accept_privacy"

        @Volatile
        private var instance: PreferencesUtility? = null

        fun getInstance(context: Context): PreferencesUtility {
            return instance ?: synchronized(this) {
                instance ?: PreferencesUtility(context).also { instance = it }
            }
        }

        private fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(SHARED_PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }

        fun save(context: Context, key: String, value: Boolean) {
            getPrefs(context).edit().putBoolean(key, value).apply()
        }

        fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
            return getPrefs(context).getBoolean(key, defaultValue)
        }
    }

    fun setRateUs(showPlatform: Boolean) {
        preferences.edit().putBoolean(Constant.PREF_RATE_US, showPlatform).apply()
    }

    fun getRateUs(): Boolean {
        return preferences.getBoolean(Constant.PREF_RATE_US, false)
    }

    fun setIsShowIntro(showPlatform: Boolean) {
        preferences.edit().putBoolean(Constant.PREF_SHOW_INTRO, showPlatform).apply()
    }

    fun getIsShowIntro(): Boolean {
        return preferences.getBoolean(Constant.PREF_SHOW_INTRO, false)
    }

    fun setSelectedLanguagePosition(position: Int) {
        preferences.edit().putInt(Constant.PREF_LANGUAGE_POSITION, position).apply()
    }

    fun getSelectedLanguagePosition(): Int {
        return preferences.getInt(Constant.PREF_LANGUAGE_POSITION, 4)
    }

    fun setSelectedLanguageCode(code: String) {
        preferences.edit().putString(Constant.PREF_LANGUAGE_CODE, code).apply()
    }

    fun getSelectedLanguageCode(): String {
        return preferences.getString(Constant.PREF_LANGUAGE_CODE, "en") ?: "en"
    }

    fun setLanguageSelectionShown(isShown: Boolean) {
        preferences.edit().putBoolean(Constant.PREF_LANGUAGE_SELECTION_SHOWN, isShown).apply()
    }

    fun isLanguageSelectionShown(): Boolean {
        return preferences.getBoolean(Constant.PREF_LANGUAGE_SELECTION_SHOWN, false)
    }

    fun setNightMode(isNight: Boolean) {
        preferences.edit().putBoolean("pref_night_mode", isNight).apply()
    }

    fun isNightMode(): Boolean {
        return preferences.getBoolean("pref_night_mode", false)
    }

    fun setLastWallpaperTarget(target: Int) {
        preferences.edit().putInt("pref_wallpaper_target", target).apply()
    }

    fun getLastWallpaperTarget(): Int {
        return preferences.getInt("pref_wallpaper_target", 1) // default 1 (Home)
    }

    fun setShowPlatformAd(showPlatform: String) {
        preferences.edit().putString(Constant.PREF_SHOW_PLATEFORM_ID, showPlatform).apply()
    }

    fun getShowPlatformAd(): String {
        return preferences.getString(Constant.PREF_SHOW_PLATEFORM_ID, Constant.ADMOB_AD_KEY) ?: Constant.ADMOB_AD_KEY
    }

    fun setNativeAdId(nativeId: String) {
        preferences.edit().putString(Constant.PREF_NATIVE_AD_ID, nativeId).apply()
    }

    fun setInterstitialAdId(interstitialId: String) {
        preferences.edit().putString(Constant.PREF_INTERSIAL_AD_ID, interstitialId).apply()
    }

    fun setBannerAdId(bannerId: String) {
        preferences.edit().putString(Constant.PREF_BANNER_AD_ID, bannerId).apply()
    }

    fun setAppOpenAdId(appOpenId: String) {
        preferences.edit().putString(Constant.PREF_APP_OPEN_AD_ID, appOpenId).apply()
    }

    fun setAdMobUnitId(appId: String) {
        preferences.edit().putString(Constant.PREF_ADMOB_UNIT_ID, appId).apply()
    }

    fun setLastTimeShowInterstitial(time: Long) {
        preferences.edit().putLong(Constant.PREF_LAST_INTERSISIAL_INTERVAL, time).apply()
    }

    fun getLastTimeShowInterstitial(): Long {
        return preferences.getLong(Constant.PREF_LAST_INTERSISIAL_INTERVAL, 0)
    }

    fun getAdData(): String {
        return preferences.getString("AdData", "") ?: ""
    }

    fun setAdData(adData: String) {
        preferences.edit().putString("AdData", adData).apply()
    }
}
