package com.developer.manali.galleryapp

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.developer.manali.galleryapp.databinding.ActivitySplashBinding

class SplashActivity : BaseActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var isProceeding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootSplash) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        startSplashProgressAnimation()
    }

    private fun startSplashProgressAnimation() {
        val animator = ObjectAnimator.ofInt(binding.splashProgress, "progress", 0, 100).apply {
            duration = 1800
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onSplashProgressComplete()
                }
            })
        }
        animator.start()
    }

    private fun onSplashProgressComplete() {
        if (isProceeding || isFinishing || isDestroyed) return
        isProceeding = true

        val prefs = PreferencesUtility.getInstance(this)
        if (!prefs.isLanguageSelectionShown()) {
            val intent = Intent(this, LanguageSelectActivity::class.java)
            intent.putExtra(Constant.IS_FROM_SPLASH, true)
            startActivity(intent)
            finish()
        } else {
            AppOpenAdsCall.loadAndShowAppOpenAd(this, object : AppOpenAdsCall.AppOpenAdCallback {
                override fun onAdDismissed() {
                    if (hasRequiredPermissions()) {
                        navigateToMain()
                    } else {
                        navigateToPermission()
                    }
                }
            })
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImages = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED

            val hasVideos = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED

            hasImages && hasVideos
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToPermission() {
        val intent = Intent(this, PermissionActivity::class.java)
        startActivity(intent)
        finish()
    }
}
