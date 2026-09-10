package com.developer.manali.galleryapp


import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import java.util.Locale

object Util {

    private val arrLanguageList: MutableList<LanguagesModel> = ArrayList()

    fun changeLanguage(activity: Activity) {
        try {
            val languageToLoad = PreferencesUtility.getInstance(activity).getSelectedLanguageCode()
            val locale = Locale(languageToLoad)
            Locale.setDefault(locale)
            val config = Configuration().apply { setLocale(locale) }
            activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected
    }

    private fun getFlag(unicode: String): String {
        val modifiedUnicode = unicode.replace("U+", "0x")
        val codeArr = modifiedUnicode.split(" ")
        val hex1 = Integer.parseInt(codeArr[0].substring(2), 16)
        val hex2 = Integer.parseInt(codeArr[1].substring(2), 16)
        return String(Character.toChars(hex1)) + String(Character.toChars(hex2))
    }

    fun getAllLanguagesList(activity: Activity): List<LanguagesModel> {
        return if (arrLanguageList.isEmpty()) {
            try {
                arrLanguageList.apply {
                    add(
                        LanguagesModel(
                            "en",
                            getFlag("U+1F1EC U+1F1E7"),
                            activity.getString(R.string.English)
                        )
                    )
                    add(
                        LanguagesModel(
                            "es",
                            getFlag("U+1F1EA U+1F1F8"),
                            activity.getString(R.string.Spanish)
                        )
                    )
                    add(
                        LanguagesModel(
                            "hi",
                            getFlag("U+1F1EC U+1F1E7"),
                            activity.getString(R.string.Hindi)
                        )
                    )
                    add(
                        LanguagesModel(
                            "fr",
                            getFlag("U+1F1EB U+1F1F7"),
                            activity.getString(R.string.French)
                        )
                    )
                    add(
                        LanguagesModel(
                            "pt",
                            getFlag("U+1F1F5 U+1F1F9"),
                            activity.getString(R.string.Portuguese)
                        )
                    )
                    add(
                        LanguagesModel(
                            "it",
                            getFlag("U+1F1EE U+1F1F9"),
                            activity.getString(R.string.Italian)
                        )
                    )
                    add(
                        LanguagesModel(
                            "de",
                            getFlag("U+1F1E9 U+1F1EA"),
                            activity.getString(R.string.German)
                        )
                    )
                    add(
                        LanguagesModel(
                            "ur",
                            getFlag("U+1F1E9 U+1F1EA"),
                            activity.getString(R.string.Urdu)
                        )
                    )
                    add(
                        LanguagesModel(
                            "ko",
                            getFlag("U+1F1E9 U+1F1EA"),
                            activity.getString(R.string.Korean)
                        )
                    )
                    add(
                        LanguagesModel(
                            "fa",
                            getFlag("U+1F1E9 U+1F1EA"),
                            activity.getString(R.string.Persian)
                        )
                    )
                    add(
                        LanguagesModel(
                            "ms",
                            getFlag("U+1F1E9 U+1F1EA"),
                            activity.getString(R.string.Malaysian)
                        )
                    )
                    add(
                        LanguagesModel(
                            "ja",
                            getFlag("U+1F1E9 U+1F1EA"),
                            activity.getString(R.string.Japanese)
                        )
                    )
                    add(
                        LanguagesModel(
                            "ru",
                            getFlag("U+1F1F7 U+1F1FA"),
                            activity.getString(R.string.Russian)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            arrLanguageList
        } else {
            arrLanguageList
        }
    }

    fun getPointerIndex(action: Int): Int {
        return (action and MotionEvent.ACTION_POINTER_INDEX_MASK) shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
    }

    fun hasDrawable(imageView: ImageView): Boolean {
        return imageView.drawable != null
    }
    fun checkZoomLevels(
        minZoom: Float, midZoom: Float,
        maxZoom: Float
    ) {
        require(!(minZoom >= midZoom)) { "Minimum zoom has to be less than Medium zoom. Call setMinimumZoom() with a more appropriate value" }
        require(!(midZoom >= maxZoom)) { "Medium zoom has to be less than Maximum zoom. Call setMaximumZoom() with a more appropriate value" }
    }

    fun isSupportedScaleType(scaleType: ScaleType?): Boolean {
        if (scaleType == null) {
            return false
        }
        when (scaleType) {
            ScaleType.MATRIX -> throw IllegalStateException("Matrix scale type is not supported")
            else -> {}
        }
        return true
    }

}
