package com.iab.omid.sampleapp

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.iab.omid.library.criteo.adsession.AdEvents
import com.iab.omid.library.criteo.adsession.AdSession
import com.iab.omid.library.criteo.adsession.media.MediaEvents
import com.iab.omid.library.criteo.adsession.media.Position
import com.iab.omid.library.criteo.adsession.media.VastProperties
import com.iab.omid.sampleapp.util.AdSessionUtil
import com.iab.omid.sampleapp.util.VastParser
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory
import androidx.core.net.toUri

/**
 * An activity representing a single ad detail screen.
 *
 * This sample shows loading a Native ad, passing a url to the Omid js, and marking the impression
 */
class VideoAdNativeActivity : Activity(), Player.Listener {

    private val vastParser: VastParser = VastParser()
    private val httpClient = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = Runnable {
        if (adSession == null || isComplete) return@Runnable
        updateQuartile()
        postProgress()
    }

    private var adSession: AdSession? = null
    private var mediaEvents: MediaEvents? = null
    private var adEvents: AdEvents? = null
    private var player: ExoPlayer? = null
    private var document: Document? = null

    private var lastSentQuartile = Quartile.UNKNOWN
    private var isComplete = false
    private var isLoaded = false
    private var isPlayingWhen50Visible = true

    private lateinit var playerView: PlayerView
    private lateinit var muteTextView: TextView

    // ACTIVITY LIFECYCLE
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.video_ad_native_detail)

        // Initialize views
        playerView = findViewById(R.id.videoView)
        playerView.setOnClickListener { onPlayerViewClicked() }

        muteTextView = findViewById(R.id.muteTextView)
        muteTextView.setOnClickListener { onMuteClicked() }

        playerView.requestFocus()

        // Initialize AdSession and events
        adSession = AdSessionUtil.getNativeAdSession(this)
        mediaEvents = MediaEvents.createMediaEvents(adSession)
        adEvents = AdEvents.createAdEvents(adSession)

        adSession?.registerAdView(playerView)
        adSession?.start()

        // Fetch and parse the VAST XML
        vastParser.fetchAndParseVast(
            vastUrl = VAST_URL,
            onSuccess = { doc ->
                document = doc
                val creativeUrl = extractByXpath("//MediaFile")
                val ccUrl = extractByXpath("//ClosedCaptionFile")
                initializePlayer(creativeUrl, ccUrl)
            },
            onFailure = { e ->
                // Handle the error
                Log.e(TAG, "Error fetching VAST XML", e)
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        adSession?.finish()
        adSession = null

        handler.removeCallbacksAndMessages(null)
        releasePlayer()
    }

    // USER INTERACTIONS
    private fun onMuteClicked() {
        val isMuted = player?.volume == PLAYER_MUTE

        emitVastBeacon(xpath = if (isMuted) "//Tracking[@event='unmute']" else "//Tracking[@event='mute']")

        muteTextView.text = if (isMuted) "Mute" else "Unmute"

        val targetVolume = if (isMuted) PLAYER_UNMUTE else PLAYER_MUTE
        player?.volume = targetVolume

        // forward event to OMID
        mediaEvents?.volumeChange(targetVolume)
    }

    private fun onPlayerViewClicked() {
        val clickThroughUrl = extractByXpath("//ClickThrough")

        if (clickThroughUrl != null) {
            // Open the URL in an external browser
            try {
                val externalIntent = Intent(Intent.ACTION_VIEW, clickThroughUrl.toUri())
                startActivity(externalIntent)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        } else {
            // Toggle play/pause if no ClickThrough URL is found
            if (isPlayingWhen50Visible) {
                pauseVideo()
                emitVastBeacon("//Tracking[@event='pause']")
            } else {
                playVideo()
                emitVastBeacon("//Tracking[@event='resume']")
            }
            isPlayingWhen50Visible = !isPlayingWhen50Visible
        }
    }

    // PLAYER STATE
    override fun onPlaybackStateChanged(playbackState: @Player.State Int) {
        when (playbackState) {
            ExoPlayer.STATE_READY -> onStateReady()
            ExoPlayer.STATE_ENDED -> onStateEnded()
            ExoPlayer.STATE_BUFFERING -> onStateBuffering()
            ExoPlayer.STATE_IDLE -> Log.d(TAG, "Playback state changed to IDLE")
        }
    }

    private fun onStateReady() {
        if (!isLoaded) {
            emitVastBeacon("//Impression")
            isLoaded = true

            val vastProperties = VastProperties.createVastPropertiesForNonSkippableMedia(false, Position.STANDALONE)
            adEvents?.loaded(vastProperties)
            adEvents?.impressionOccurred()
        }

        mediaEvents?.bufferFinish()

        postProgress()
    }

    private fun onStateEnded() {
        if (!isComplete) {
            emitVastBeacon("//Tracking[@event='complete']")

            // forward event to OMID
            mediaEvents?.complete()
            isComplete = true
        }

        player?.seekTo(0)
        player?.play()
    }

    private fun onStateBuffering() {
        mediaEvents?.bufferStart()
    }

    // VISIBILITY & VIEWPORT
    private fun isVideoViewAtLeast50Visible(videoView: View): Boolean {
        // Get the visible rectangle of the view
        val visibleRect = Rect()
        val isVisible = videoView.getGlobalVisibleRect(visibleRect)
        if (!isVisible) return false // Not visible at all

        // Get total area of the video view
        val totalArea = videoView.width * videoView.height
        if (totalArea == 0) return false // Video view has no size yet

        // Check if visible area is at least 50% of the total video area
        val visibleArea = visibleRect.width() * visibleRect.height()
        return (visibleArea >= totalArea / 2)
    }

    // VAST & BEACON MANAGEMENT
    private fun extractByXpath(xpath: String?): String? {
        try {
            val nodes = xPathFactory.newXPath().compile(xpath)
                .evaluate(document, XPathConstants.NODESET) as NodeList
            if (nodes.length == 0) {
                Log.e(TAG, "Didn't find anything on the provided xpath $xpath")
                return null
            }
            if (nodes.length > 1) {
                Log.w(
                    TAG,
                    "Several nodes found with the provided xpath $xpath. Choosing the first one"
                )
            }

            // ugly, to refactor
            return if (!nodes.item(0).hasChildNodes()) {
                nodes.item(0).textContent
            } else nodes.item(0).firstChild.textContent

        } catch (e: XPathExpressionException) {
            Log.e(TAG, "Couldn't extract the data from the given xpath$xpath", e)
            return null
        }
    }

    private fun emitVastBeacon(xpath: String?) {
        val url = extractByXpath(xpath)

        if (url != null) {
            // Make async request to emit the beacon
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request)
                .enqueue(
                    responseCallback = object : Callback {
                        override fun onFailure(call: Call, e: okio.IOException) {
                            Log.e(TAG, "Failed to emit VAST beacon to url: $url", e)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            if (response.isSuccessful) {
                                Log.d(TAG, "Successfully emitted VAST beacon to url: $url")
                            } else {
                                Log.e(TAG, "Failed to emit VAST beacon to url: $url. Response code: ${response.code}")
                            }
                            response.close()
                        }
                    }
                )
        } else {
            Log.e(TAG, "Nothing found in the xpath $xpath. Not emitting the beacon")
        }
    }

    // PLAYER MANAGEMENT
    @OptIn(UnstableApi::class)
    private fun initializePlayer(videoUrl: String?, ccUrl: String?) {
        // Create exoplayer with track selector for content and ads
        val trackSelector = DefaultTrackSelector(this).also { selector ->
            val parameters = TrackSelectionParameters.Builder(this)
                .setPreferredTextLanguage("fr") // Set preferred language, e.g., English
                .build()
            selector.setParameters(parameters)
        }

        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()

        // Create the MediaItem to play
        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl?.toUri())
            .also { builder ->
                ccUrl?.let {
                    // Subtitle configuration for external WebVTT subtitles
                    val subtitleUri = ccUrl.toUri()
                    val subtitleConfig = SubtitleConfiguration.Builder(subtitleUri)
                        .setMimeType(MimeTypes.TEXT_VTT) // The MIME type for WebVTT subtitles
                        .setLanguage("en") // Optional: Specify the language
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT) // Optional: Set flags like default
                        .build()

                    builder.setSubtitleConfigurations(listOf(subtitleConfig))
                }
            }
            .build()

        // Configure the player
        playerView.setPlayer(player)
        playerView.setUseController(false) // Disable default player controls (prevents seeking)

        player?.setMediaItem(mediaItem)
        player?.addListener(this)
        player?.volume = PLAYER_UNMUTE
        player?.playWhenReady = true
        player?.prepare()

        // Monitor visibility
        playerView.getViewTreeObserver().addOnPreDrawListener {
            // Check if at least 50% of the video is visible
            val startPlaying = isPlayingWhen50Visible && isVideoViewAtLeast50Visible(playerView)
            if (startPlaying) playVideo() else pauseVideo()
            true
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    private fun playVideo() {
        player?.play()

        // forward event to OMID
        mediaEvents?.resume()
    }

    private fun pauseVideo() {
        player?.pause()

        // forward event to OMID
        mediaEvents?.pause()
    }

    // PROGRESS TRACKING
    private fun postProgress() {
        handler.removeCallbacks(progressRunnable)
        handler.postDelayed(progressRunnable, PROGRESS_INTERVAL_MS.toLong())
    }

    private fun updateQuartile() {
        val duration = player?.duration ?: 0L
        val currentPosition = player?.currentPosition

        if (duration != 0L && currentPosition != null) {
            val currentQuartile: Quartile = getQuartile(currentPosition, duration)

            // Don't send old quartile stats that we have either already sent, or passed.
            if (currentQuartile != lastSentQuartile && currentQuartile.ordinal > lastSentQuartile.ordinal) {
                sendQuartile(currentQuartile)
                lastSentQuartile = currentQuartile
            }
        }
    }

    private fun sendQuartile(quartile: Quartile) {
        when (quartile) {
            Quartile.START -> {
                emitVastBeacon("//Tracking[@event='start']")
                mediaEvents?.start(player!!.duration.toFloat(), PLAYER_UNMUTE)
            }
            Quartile.FIRST -> {
                emitVastBeacon("//Tracking[@event='firstQuartile']")
                mediaEvents?.firstQuartile()
            }
            Quartile.SECOND -> {
                emitVastBeacon("//Tracking[@event='midpoint']")
                mediaEvents?.midpoint()
            }
            Quartile.THIRD -> {
                emitVastBeacon("//Tracking[@event='thirdQuartile']")
                mediaEvents?.thirdQuartile()
            }
            Quartile.UNKNOWN -> {}
        }
    }

    // ENUMS & DATA CLASSES
    enum class Quartile {
        UNKNOWN,
        START,
        FIRST,
        SECOND,
        THIRD,
    }

    private companion object {
        private const val TAG = "CriteoVideoAd"

        private const val VAST_URL = "https://raw.githubusercontent.com/criteo/interview-ios/refs/heads/main/server/sample_vast_app.xml"

        private const val PLAYER_MUTE = 0f
        private const val PLAYER_UNMUTE = 1f
        private const val PROGRESS_INTERVAL_MS = 100

        private val xPathFactory: XPathFactory = XPathFactory.newInstance()

        private fun getQuartile(position: Long, duration: Long): Quartile {
            fun lessThan(a: Double, b: Double): Boolean {
                return b - a > .000001
            }

            val completionFraction = position / duration.toDouble()
            if (lessThan(completionFraction, 0.01)) {
                return Quartile.UNKNOWN
            }

            if (lessThan(completionFraction, 0.25)) {
                return Quartile.START
            }

            if (lessThan(completionFraction, 0.5)) {
                return Quartile.FIRST
            }

            if (lessThan(completionFraction, 0.75)) {
                return Quartile.SECOND
            }

            // We report Quartile.THIRD when completionFraction > 1 on purpose
            // since track might technically report elapsed time after it's completion
            // and if Quartile.THIRD hasn't been reported already, it will be lost
            return Quartile.THIRD
        }
    }
}
