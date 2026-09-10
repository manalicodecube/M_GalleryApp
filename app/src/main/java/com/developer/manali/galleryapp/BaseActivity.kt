package com.developer.manali.galleryapp

import android.net.ConnectivityManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PreferencesUtility.getInstance(this)
        if (prefs.isNightMode()) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }
        
        super.onCreate(savedInstanceState)
        hideNav()
        Util.changeLanguage(this)
    }

    fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager?
        if (cm != null) {
            val activeNetwork = cm.getActiveNetworkInfo()
            return activeNetwork != null && activeNetwork.isConnected()
        }
        return false
    }

    companion object {
    }

    fun hideNav() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController =
            WindowInsetsControllerCompat(window, window.decorView)
        val isNight = PreferencesUtility.getInstance(this).isNightMode()
        insetsController.isAppearanceLightStatusBars = !isNight
        insetsController.isAppearanceLightNavigationBars = !isNight
        insetsController.hide(WindowInsetsCompat.Type.navigationBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.white))
    }

    fun applyPaddingForSystemViews(targetView: View? = null) {
        val mainView = targetView ?: findViewById<View>(R.id.main) ?: findViewById<View>(android.R.id.content) ?: return

        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v: View, insets: WindowInsetsCompat ->
            val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.displayCutout

            val leftPadding: Int = systemBars.left + (cutout?.safeInsetLeft ?: 0)
            val rightPadding: Int = systemBars.right + (cutout?.safeInsetRight ?: 0)

            v.setPadding(
                leftPadding,
                systemBars.top,
                rightPadding,
                systemBars.bottom
            )
            insets
        }
    }
}