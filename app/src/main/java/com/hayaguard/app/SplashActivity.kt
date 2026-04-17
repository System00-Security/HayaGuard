package com.hayaguard.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_splash)
        
        val logoCard = findViewById<View>(R.id.logoCard)
        val title = findViewById<View>(R.id.appTitle)
        val tagline = findViewById<View>(R.id.tagline)
        val text = findViewById<View>(R.id.craftedByText)
        val ornamentTop = findViewById<View>(R.id.ornamentTop)
        val ornamentBottom = findViewById<View>(R.id.ornamentBottom)
        
        val scaleAnim = ScaleAnimation(
            0.6f, 1f, 0.6f, 1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        )
        scaleAnim.duration = 700
        scaleAnim.interpolator = AccelerateDecelerateInterpolator()
        
        val fadeAnim = AlphaAnimation(0f, 1f)
        fadeAnim.duration = 700
        
        val animSet = AnimationSet(true)
        animSet.addAnimation(scaleAnim)
        animSet.addAnimation(fadeAnim)
        
        logoCard.startAnimation(animSet)
        
        val titleFade = AlphaAnimation(0f, 1f)
        titleFade.duration = 500
        titleFade.startOffset = 300
        title.startAnimation(titleFade)
        
        val taglineFade = AlphaAnimation(0f, 1f)
        taglineFade.duration = 500
        taglineFade.startOffset = 500
        tagline.startAnimation(taglineFade)
        
        val textFade = AlphaAnimation(0f, 1f)
        textFade.duration = 600
        textFade.startOffset = 700
        text.startAnimation(textFade)
        
        val slideDown = TranslateAnimation(
            TranslateAnimation.RELATIVE_TO_SELF, 0f,
            TranslateAnimation.RELATIVE_TO_SELF, 0f,
            TranslateAnimation.RELATIVE_TO_SELF, -1f,
            TranslateAnimation.RELATIVE_TO_SELF, 0f
        )
        slideDown.duration = 800
        slideDown.interpolator = AccelerateDecelerateInterpolator()
        ornamentTop.startAnimation(slideDown)
        
        val slideUp = TranslateAnimation(
            TranslateAnimation.RELATIVE_TO_SELF, 0f,
            TranslateAnimation.RELATIVE_TO_SELF, 0f,
            TranslateAnimation.RELATIVE_TO_SELF, 1f,
            TranslateAnimation.RELATIVE_TO_SELF, 0f
        )
        slideUp.duration = 800
        slideUp.interpolator = AccelerateDecelerateInterpolator()
        ornamentBottom.startAnimation(slideUp)
        
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }, 2200)
    }
}
