package com.iab.omid.sampleapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.iab.omid.sampleapp.MainActivity.Companion.VAST_DEMO_URL
import com.iab.omid.sampleapp.player.CriteoVideoAdConfiguration
import com.iab.omid.sampleapp.player.CriteoVideoAdLogCategory
import com.iab.omid.sampleapp.player.CriteoVideoAdWrapper

class BasicVideoPlayerFragment : Fragment() {

    private var videoAdWrapper: CriteoVideoAdWrapper? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_basic_video_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = "Basic Video Ad Example"

        setupVideoAd()
    }

    private fun setupVideoAd() {
        // 1. Configure the video ad wrapper
        val config = CriteoVideoAdConfiguration(
            autoLoad = true,
            startsMuted = true
        )

        // 2. Create the wrapper using the factory method
        videoAdWrapper = CriteoVideoAdWrapper.fromUrl(
            context = requireContext(),
            vastURL = VAST_DEMO_URL,
            configuration = config
        ).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 3. Enable all log categories for debugging
        videoAdWrapper?.enableLogs = setOf(
            CriteoVideoAdLogCategory.VAST,
            CriteoVideoAdLogCategory.NETWORK,
            CriteoVideoAdLogCategory.VIDEO,
            CriteoVideoAdLogCategory.BEACON,
            CriteoVideoAdLogCategory.OMID,
            CriteoVideoAdLogCategory.UI
        )

        // 4. Set up optional callbacks
        videoAdWrapper?.onVideoLoaded = {
            Log.d(TAG, "Video loaded successfully and ready to play")
        }

        videoAdWrapper?.onVideoStarted = {
            Log.d(TAG, "Video playback started")
        }

        videoAdWrapper?.onVideoPaused = {
            Log.d(TAG, "Video playback paused")
        }

        videoAdWrapper?.onVideoTapped = {
            Log.d(TAG, "User tapped on video")
        }

        videoAdWrapper?.onVideoError = { error ->
            Log.e(TAG, "Video error: ${error.message}", error)
        }

        // 5. Add the wrapper to the layout
        view?.findViewById<FrameLayout>(R.id.videoAdContainer)?.addView(videoAdWrapper)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        videoAdWrapper?.release()
        videoAdWrapper = null
    }

    companion object {
        private const val TAG = "BasicVideoPlayerFragment"
    }
}
