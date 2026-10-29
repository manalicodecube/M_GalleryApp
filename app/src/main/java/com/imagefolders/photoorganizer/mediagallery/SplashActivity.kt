package com.imagefolders.photoorganizer.mediagallery

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.imagefolders.photoorganizer.mediagallery.databinding.ActivitySplashBinding

class SplashActivity : BaseActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var isProceeding = false
    private var isProceedingToNextScreen = false

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

        val prefs = PreferencesUtility.getInstance(this)
        if (prefs.isLanguageSelectionShown()) {
            AppOpenAdsCall.preload(this)
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
                    proceedToNextScreen()
                }
            })
        }
    }

    private fun proceedToNextScreen() {
        if (isProceedingToNextScreen || isFinishing || isDestroyed) return
        isProceedingToNextScreen = true

        if (!Util.hasAllPermissions(this)) {
            navigateToPermission()
        } else {
            navigateToMain()
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
