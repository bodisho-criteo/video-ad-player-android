package com.iab.omid.sampleapp

import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.iab.omid.sampleapp.player.CriteoVideoAdConfiguration
import com.iab.omid.sampleapp.player.CriteoVideoAdLogCategory
import com.iab.omid.sampleapp.player.CriteoVideoAdWrapper

class MainActivity : AppCompatActivity() {

    private lateinit var videoAdWrapper: CriteoVideoAdWrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupVideoAd()
    }

    private fun setupVideoAd() {
        // 1. Configure the video ad wrapper
        val config = CriteoVideoAdConfiguration(
            autoLoad = true,
            startsMuted = false
        )

        // 2. Create the wrapper using the factory method
        videoAdWrapper = CriteoVideoAdWrapper.fromUrl(
            context = this,
            vastURL = "https://raw.githubusercontent.com/criteo/interview-ios/refs/heads/main/server/sample_vast_app.xml",
            configuration = config
        ).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 3. Enable all log categories for debugging
        videoAdWrapper.enableLogs = setOf(
            CriteoVideoAdLogCategory.VAST,
            CriteoVideoAdLogCategory.NETWORK,
            CriteoVideoAdLogCategory.VIDEO,
            CriteoVideoAdLogCategory.BEACON,
            CriteoVideoAdLogCategory.OMID,
            CriteoVideoAdLogCategory.UI
        )

        // 4. Set up optional callbacks
        videoAdWrapper.onVideoLoaded = {
            Log.d("MainActivity", "✅ Video loaded successfully and ready to play")
        }

        videoAdWrapper.onVideoStarted = {
            Log.d("MainActivity", "▶️ Video playback started")
        }

        videoAdWrapper.onVideoPaused = {
            Log.d("MainActivity", "⏸️ Video playback paused")
        }

        videoAdWrapper.onVideoTapped = {
            Log.d("MainActivity", "👆 User tapped on video")
        }

        videoAdWrapper.onVideoError = { error ->
            Log.e("MainActivity", "❌ Video error: ${error.message}", error)
        }

        // 5. Add the wrapper to the layout
        findViewById<FrameLayout>(R.id.mainContentLayout).addView(videoAdWrapper)
    }
}
