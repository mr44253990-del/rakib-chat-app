package com.ebchat.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnticipateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.ebchat.auth.LoginActivity
import com.ebchat.auth.PermissionActivity
import com.ebchat.databinding.ActivitySplashBinding
import com.ebchat.home.MainActivity
import com.ebchat.utils.FirebaseUtils
import com.ebchat.utils.PrefsManager

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val prefs = PrefsManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        animateLogo()

        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, 2500)
    }

    private fun animateLogo() {
        // Logo scale animation
        val scaleX = ObjectAnimator.ofFloat(binding.logoImage, "scaleX", 0.5f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.logoImage, "scaleY", 0.5f, 1f)
        val alpha = ObjectAnimator.ofFloat(binding.logoImage, "alpha", 0f, 1f)

        // Text animations
        val titleAlpha = ObjectAnimator.ofFloat(binding.appTitle, "alpha", 0f, 1f).apply {
            startDelay = 500
        }
        val titleTranslation = ObjectAnimator.ofFloat(binding.appTitle, "translationY", 50f, 0f).apply {
            startDelay = 500
        }

        val taglineAlpha = ObjectAnimator.ofFloat(binding.appTagline, "alpha", 0f, 1f).apply {
            startDelay = 800
        }

        val shimmerAlpha = ObjectAnimator.ofFloat(binding.shimmerView, "alpha", 0.3f, 1f, 0.3f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
        }

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            playTogether(titleAlpha, titleTranslation)
            play(taglineAlpha).after(titleAlpha)
            interpolator = AnticipateInterpolator()
            duration = 800
            start()
        }

        shimmerAlpha.start()
    }

    private fun navigateToNextScreen() {
        val intent = when {
            !FirebaseUtils.isLoggedIn -> Intent(this, LoginActivity::class.java)
            prefs.isFirstLaunch -> Intent(this, PermissionActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
