package com.iab.omid.sampleapp.player

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.iab.omid.sampleapp.manager.BeaconManager
import com.iab.omid.sampleapp.manager.CreativeDownloader
import com.iab.omid.sampleapp.manager.NetworkManager
import com.iab.omid.sampleapp.manager.vast.VastAd
import com.iab.omid.sampleapp.manager.vast.VastManager
import com.iab.omid.sampleapp.player.CriteoVideoAdError.AssetDownloadFailed
import com.iab.omid.sampleapp.player.CriteoVideoAdError.VastParsingFailed
import com.iab.omid.sampleapp.util.CriteoLogger
import com.iab.omid.sampleapp.util.CriteoLogger.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.net.URL
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt
import com.iab.omid.sampleapp.player.CriteoVideoAdError.InvalidURL
import kotlinx.coroutines.isActive

/**
 * CriteoVideoAdWrapper - Production-ready wrapper for video ad integration
 *
 * A comprehensive Android view component that handles the complete video ad lifecycle:
 * - VAST XML parsing and validation (URL or raw XML)
 * - Automatic asset downloading and caching
 * - OMID (Open Measurement) integration for viewability tracking
 * - VAST beacon tracking (impressions, quartiles, complete, etc.)
 * - Built-in loading and error UI with customizable styling
 * - Video player lifecycle management with state observation
 * - Closed captions support (VTT/WebVTT)
 * - Smart media file selection based on aspect ratio
 * - Click-through URL handling
 * - Automatic cleanup on detachment
 *
 * FEATURES:
 * - Multiple initialization methods: constructor, fromUrl(), fromXml()
 * - Configurable auto-load and auto-play behavior
 * - Mute state management
 * - Playback position preservation
 * - Progress tracking callbacks
 * - Comprehensive error handling with retry capability
 * - Granular logging control per category (VAST, Network, Video, Beacon, OMID, UI)
 *
 * PUBLIC API:
 * Properties:
 *   - isPlaying: Boolean - Current playback state
 *   - isMuted: Boolean - Current mute state
 *   - currentPositionMs: Long - Playback position in milliseconds
 *   - state: CriteoVideoAdState - Current wrapper state (NotLoaded, Loading, Ready, Error)
 *   - enableLogs: Set<CriteoVideoAdLogCategory> - Enable specific log categories
 *
 * Callbacks:
 *   - onVideoLoaded: () -> Unit - Video assets downloaded and ready
 *   - onVideoStarted: () -> Unit - Playback started
 *   - onVideoPaused: () -> Unit - Playback paused
 *   - onVideoTapped: () -> Unit - User tapped on video
 *   - onVideoError: (Throwable) -> Unit - Error occurred
 *   - onPlaybackProgress: (currentMs: Long, durationMs: Long) -> Unit - Progress updates
 *
 * Methods:
 *   - loadVideoAd(source: VASTSource) - Load video from URL or XML
 *   - play() - Resume playback
 *   - pause() - Pause playback
 *   - seekTo(timeMs: Long) - Seek to position
 *   - toggleMute() - Toggle mute state
 *   - retry() - Retry loading after error
 *
 * USAGE EXAMPLES:
 *
 * 1. Quick Start with URL:
 * ```kotlin
 * val videoAd = CriteoVideoAdWrapper.fromUrl(
 *     context = this,
 *     vastURL = "https://example.com/vast.xml"
 * )
 * parentView.addView(videoAd)
 * ```
 *
 * 2. With Callbacks and Configuration:
 * ```kotlin
 * val config = CriteoVideoAdConfiguration(
 *     autoLoad = true,
 *     startsMuted = true,
 *     loadingText = "Preparing your video...",
 *     retryButtonText = "Try Again"
 * )
 *
 * val videoAd = CriteoVideoAdWrapper(context, configuration = config)
 *
 * // Enable specific log categories
 * videoAd.enableLogs = setOf(
 *     CriteoVideoAdLogCategory.VAST,
 *     CriteoVideoAdLogCategory.NETWORK
 * )
 * videoAd.onVideoLoaded = {
 *     println("Video ready! Duration: ${videoAd.currentPositionMs}ms")
 * }
 * videoAd.onVideoStarted = {
 *     println("Playback started")
 * }
 * videoAd.onVideoError = { error ->
 *     println("Error: ${error.message}")
 * }
 * videoAd.onPlaybackProgress = { current, total ->
 *     val progress = (current.toFloat() / total * 100).toInt()
 *     println("Progress: $progress%")
 * }
 *
 * parentView.addView(videoAd)
 * videoAd.loadVideoAd(VASTSource.Url("https://example.com/vast.xml"))
 * ```
 *
 * 4. From Raw VAST XML:
 * ```kotlin
 * val vastXml = """<?xml version="1.0"?>
 *     <VAST version="3.0">
 *         <!-- Your VAST XML -->
 *     </VAST>"""
 *
 * val videoAd = CriteoVideoAdWrapper.fromXml(
 *     context = this,
 *     vastXML = vastXml
 * )
 * parentView.addView(videoAd)
 * ```
 *
 * @param context Android context
 * @param attrs Optional XML attributes
 * @param defStyleAttr Default style attribute
 * @param configuration Configuration options for behavior and styling
 */

class CriteoVideoAdWrapper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    val configuration: CriteoVideoAdConfiguration = CriteoVideoAdConfiguration.DEFAULT
) : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * Whether the video is currently playing
     */
    val isPlaying: Boolean
        get() = videoPlayer?.state?.value?.playbackState == CriteoVideoPlayer.PlaybackState.PLAYING

    /**
     * Whether the video is muted
     */
    val isMuted: Boolean
        get() = videoPlayer?.state?.value?.isMuted ?: true

    /**
     * Current playback time in milliseconds
     */
    val currentPositionMs: Long
        get() = videoPlayer?.currentPositionMs ?: 0

    /**
     * Current loading/playback state
     */
    val state: CriteoVideoAdState
        get() = currentState

    /**
     * Enable specific log categories for this wrapper instance
     */
    var enableLogs: Set<CriteoVideoAdLogCategory> = emptySet()
        set(value) {
            field = value
            CriteoLogger.setEnabledCategories(*value.map { it.toCategory() }.toTypedArray())
        }

    /**
     * Called when video assets are downloaded and ready to play
     */
    var onVideoLoaded: (() -> Unit)? = null

    /**
     * Called when video playback starts
     */
    var onVideoStarted: (() -> Unit)? = null

    /**
     * Called when video playback is paused (user or programmatic)
     */
    var onVideoPaused: (() -> Unit)? = null

    /**
     * Called when user taps on the video
     */
    var onVideoTapped: (() -> Unit)? = null

    /**
     * Called when an error occurs
     */
    var onVideoError: ((Throwable) -> Unit)? = null

    /**
     * Called with playback progress updates
     */
    var onPlaybackProgress: ((currentMs: Long, durationMs: Long) -> Unit)? = null

    // Coroutine scope for async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Manager instances
    private val httpClient: OkHttpClient = OkHttpClient()
    private val vastManager: VastManager = VastManager()
    private val networkManager: NetworkManager = NetworkManager(httpClient, vastManager)
    private val creativeDownloader: CreativeDownloader = CreativeDownloader(httpClient)
    private val beaconManager: BeaconManager = BeaconManager(httpClient, scope)

    // UI Components
    private val loadingContainerView: FrameLayout
    private val loadingIndicator: ProgressBar
    private val loadingLabel: TextView
    private val errorContainerView: FrameLayout
    private val errorLabel: TextView
    private val retryButton: Button

    private var currentState: CriteoVideoAdState = CriteoVideoAdState.NotLoaded
        set(value) {
            field = value
            updateUIForState()
        }

    private var vastSource: VASTSource? = null
    private var videoPlayer: CriteoVideoPlayer? = null
    private var vastAd: VastAd? = null
    private var videoAssetFile: File? = null
    private var closedCaptionsAssetFile: File? = null
    private var lastPlaybackPosition: Long = 0L
    private var isUserPaused: Boolean = false

    private var preloadJob: Job? = null

    init {
        // Setup UI components
        loadingContainerView = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(configuration.loadingBackgroundColor)
            visibility = GONE
        }

        loadingIndicator = ProgressBar(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            indeterminateDrawable?.setTint(configuration.loadingIndicatorColor)
        }

        loadingLabel = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                topMargin = dpToPx(12)
            }
            text = configuration.loadingText
            setTextColor(configuration.loadingTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
        }

        errorContainerView = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(configuration.errorBackgroundColor)
            visibility = GONE
        }

        errorLabel = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setTextColor(configuration.errorTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
        }

        retryButton = Button(context).apply {
            layoutParams = LayoutParams(dpToPx(120), dpToPx(44)).apply {
                gravity = Gravity.CENTER
                topMargin = dpToPx(16)
            }
            text = configuration.retryButtonText
            setTextColor(configuration.retryButtonColor)
            setBackgroundColor(configuration.retryButtonBackgroundColor)
            setOnClickListener { retry() }
        }

        setupUI()
    }

    // LIFECYCLE METHODS
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        preloadJob?.cancel()
        videoPlayer?.release()
        scope.cancel()
    }

    // PUBLIC API
    fun loadVideoAd(source: VASTSource) {
        synchronized(this) {
            if (currentState != CriteoVideoAdState.NotLoaded) return
            vastSource = source
            currentState = CriteoVideoAdState.Loading
        }
        preloadJob?.cancel()
        preloadJob = scope.launch {
            performAssetDownload(source)
        }
    }

    fun play() {
        videoPlayer?.let { player ->
            isUserPaused = false
            player.play(fromUserInteraction = false)
            onVideoStarted?.invoke()
        }
    }

    fun pause() {
        videoPlayer?.let { player ->
            isUserPaused = true
            lastPlaybackPosition = player.currentPositionMs
            player.pause(fromUserInteraction = false)
            onVideoPaused?.invoke()
        }
    }

    fun seekTo(timeMs: Long) {
        videoPlayer?.seekTo(timeMs)
    }

    fun toggleMute() {
        videoPlayer?.toggleMute()
    }

    /**
     * Retry loading after an error
     */
    fun retry() {
        val source = vastSource ?: return
        wrapperLog("Retry button tapped", Category.UI)
        currentState = CriteoVideoAdState.NotLoaded
        loadVideoAd(source)
    }

    // PRIVATE HELPERS
    private fun setupUI() {
        setBackgroundColor(configuration.backgroundColor)

        // Add loading UI
        addView(loadingContainerView)
        loadingContainerView.addView(loadingIndicator)

        val loadingLabelContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setPadding(dpToPx(16), dpToPx(60), dpToPx(16), 0)
        }
        loadingLabelContainer.addView(loadingLabel)
        loadingContainerView.addView(loadingLabelContainer)

        // Add error UI
        addView(errorContainerView)

        val errorContentContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
        }

        val errorLabelContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }
        errorLabelContainer.addView(errorLabel)
        errorContentContainer.addView(errorLabelContainer)

        val retryButtonContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setPadding(0, dpToPx(72), 0, 0)
        }
        retryButtonContainer.addView(retryButton)
        errorContentContainer.addView(retryButtonContainer)

        errorContainerView.addView(errorContentContainer)
    }

    private fun updateUIForState() {
        post {
            loadingContainerView.visibility = if (currentState is CriteoVideoAdState.Loading) VISIBLE else GONE
            errorContainerView.visibility = if (currentState is CriteoVideoAdState.Error) VISIBLE else GONE

            (currentState as? CriteoVideoAdState.Error)?.let { errorState ->
                errorLabel.text = errorState.error.message ?: "An error occurred"
            }
        }
    }

    private suspend fun performAssetDownload(source: VASTSource) {
        try {
            // Step 1: Parse VAST XML
            wrapperLog("Starting VAST parsing", Category.VAST)

            val vastAdResult = when (source) {
                is VASTSource.Url -> networkManager
                    .fetchAndParseVast(source.url)
                    .getOrElse { error -> throw VastParsingFailed(error.message ?: "Unknown error") }
                is VASTSource.Xml -> vastManager.parseVast(source.xml)
            }

            wrapperLog("VAST parsed successfully", Category.VAST)

            vastAd = vastAdResult

            // Step 2: Select and download video asset
            val selectedURL = selectBestMediaURL(ad = vastAdResult)
                ?: vastAdResult.videoUrl
                ?: throw InvalidURL("No valid media file urls found in VAST")

            wrapperLog("Starting video download: $selectedURL", Category.NETWORK)

            videoAssetFile = creativeDownloader
                .fetchCreative(selectedURL.toString())
                .getOrElse { error -> throw AssetDownloadFailed(error.message ?: "Video download failed") }

            wrapperLog("Video downloaded successfully", Category.NETWORK)

            // Step 3: Download closed captions (if available)
            val ccURL = vastAdResult.mediaFiles.firstOrNull {
                it.url.toString() == selectedURL.toString()
            }?.captionUrl ?: vastAdResult.closedCaptionUrl
            if (ccURL != null) {
                wrapperLog("Starting closed captions download: $ccURL", Category.NETWORK)

                closedCaptionsAssetFile = creativeDownloader.fetchCreative(ccURL.toString()).getOrNull()

                if (closedCaptionsAssetFile != null) {
                    wrapperLog("Closed Captions downloaded successfully", Category.NETWORK)
                }
            }

            // Step 4: Update state and notify
            currentState = CriteoVideoAdState.Ready
            onVideoLoaded?.invoke()

            // Auto-setup player if ready
            if (configuration.autoLoad) {
                setupVideoPlayer()
            }
        } catch (error: Throwable) {
            wrapperLog("Asset download failed: $error", Category.NETWORK)
            currentState = CriteoVideoAdState.Error(error)
            onVideoError?.invoke(error)
        }
    }

    private fun setupVideoPlayer() {
        val ad = vastAd ?: return
        val videoFile = videoAssetFile ?: return

        if (currentState !is CriteoVideoAdState.Ready) return

        wrapperLog("Setting up video player", Category.VIDEO)

        // Remove existing player if any
        videoPlayer?.let { removeView(it) }

        // Create new player
        val player = CriteoVideoPlayer(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }

        // Configure player
        player.setBeaconManager(beaconManager)
        player.setVastAd(ad)

        // Set up state observation
        scope.launch {
            player.state.collect { state ->
                when (state.playbackState) {
                    CriteoVideoPlayer.PlaybackState.PLAYING -> {
                        if (!isUserPaused) {
                            onVideoStarted?.invoke()
                        }
                    }
                    CriteoVideoPlayer.PlaybackState.PAUSED -> {
                        onVideoPaused?.invoke()
                    }
                    CriteoVideoPlayer.PlaybackState.ERROR -> {
                        val error = CriteoVideoAdError.PlayerError("Playback error")
                        currentState = CriteoVideoAdState.Error(error)
                        onVideoError?.invoke(error)
                    }
                    else -> {
                        wrapperLog("state changed: ${state.playbackState}", Category.VIDEO)
                    }
                }
            }
        }

        // Set up progress tracking - poll position periodically
        scope.launch {
            while (isActive) {
                delay(100) // Update every 100ms
                if (player.state.value.playbackState == CriteoVideoPlayer.PlaybackState.PLAYING) {
                    lastPlaybackPosition = player.currentPositionMs
                    onPlaybackProgress?.invoke(player.currentPositionMs, player.durationMs)
                }
            }
        }

        // Add to view
        addView(player, 0) // Add at index 0 to be behind loading/error views

        // Load video
        val videoUri = Uri.fromFile(videoFile)
        val subtitleUri = closedCaptionsAssetFile?.let { Uri.fromFile(it) }
        player.load(videoUri, subtitleUri)

        // Set mute state after loading
        if (configuration.startsMuted != player.state.value.isMuted) {
            player.toggleMute()
        }

        // Handle click for click-through URL
        player.setOnClickListener {
            onVideoTapped?.invoke()
            handleVideoClick(ad)
        }

        // Seek to saved position if any
        if (lastPlaybackPosition > 0) {
            player.seekTo(lastPlaybackPosition)
        }

        // Auto-play if not user-paused
        if (!isUserPaused) {
            player.play(fromUserInteraction = false)
        }

        videoPlayer = player
    }

    private fun handleVideoClick(ad: VastAd) {
        ad.clickThroughUrl?.let { url ->
            // Open URL in external browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, url.toString().toUri())
                context.startActivity(intent)
                wrapperLog("Opening click-through URL: $url", Category.VIDEO)
            } catch (e: Exception) {
                wrapperLog("Failed to open click-through URL: ${e.message}", Category.VIDEO)
            }
        } ?: run {
            // No click-through URL, toggle play/pause
            videoPlayer?.togglePlayPause(fromUserInteraction = true)
            wrapperLog("No click-through URL found, toggling play/pause instead", Category.VIDEO)
        }
    }

    /**
     * Select the best media file URL based on aspect ratio
     */
    private fun selectBestMediaURL(ad: VastAd): URL? {
        if (ad.mediaFiles.isEmpty()) return null

        // Use current dimensions to determine target ratio; default to 16:9
        val targetRatio: Float = if (height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            16f / 9f
        }

        // Consider only MP4 media files; pick closest by aspect ratio
        val candidates = ad.mediaFiles.filter { it.type?.contains("mp4", ignoreCase = true) == true }
        if (candidates.isEmpty()) return null

        val selected = candidates.minByOrNull { media ->
            val mediaRatio = if (media.width != null && media.height != null && media.height > 0) {
                media.width.toFloat() / media.height.toFloat()
            } else {
                Float.MAX_VALUE
            }
            kotlin.math.abs(mediaRatio - targetRatio)
        }

        return selected?.url
    }

    private fun wrapperLog(message: String, category: Category) {
        if (enableLogs.isEmpty() || enableLogs.any { it.toCategory() == category }) {
            CriteoLogger.info("[Wrapper] $message", category)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    companion object {

        /**
         * Create wrapper with VAST URL
         */
        fun fromUrl(
            context: Context,
            vastURL: String,
            configuration: CriteoVideoAdConfiguration = CriteoVideoAdConfiguration.DEFAULT
        ): CriteoVideoAdWrapper = CriteoVideoAdWrapper(
            context = context,
            configuration = configuration
        ).apply { loadVideoAd(source = VASTSource.Url(vastURL)) }

        /**
         * Create wrapper with raw VAST XML
         */
        fun fromXml(
            context: Context,
            vastXML: String,
            configuration: CriteoVideoAdConfiguration = CriteoVideoAdConfiguration.DEFAULT
        ): CriteoVideoAdWrapper = CriteoVideoAdWrapper(
            context = context,
            configuration = configuration
        ).apply { loadVideoAd(source = VASTSource.Xml(vastXML)) }
    }
}

/**
 * Source of the VAST ad: either a URL or raw XML
 */
sealed interface VASTSource {
    data class Url(val url: String) : VASTSource
    data class Xml(val xml: String) : VASTSource
}

/**
 * Log categories available for the video ad wrapper
 */
enum class CriteoVideoAdLogCategory {
    VAST,
    NETWORK,
    VIDEO,
    BEACON,
    OMID,
    UI;

    internal fun toCategory(): Category {
        return when (this) {
            VAST -> Category.VAST
            NETWORK -> Category.NETWORK
            VIDEO -> Category.VIDEO
            BEACON -> Category.BEACON
            OMID -> Category.OMID
            UI -> Category.UI
        }
    }
}

/**
 * Configuration options for the video ad wrapper
 */
data class CriteoVideoAdConfiguration(
    val autoLoad: Boolean = true,
    val startsMuted: Boolean = false,
    val backgroundColor: Int = Color.WHITE,
    val loadingBackgroundColor: Int = "#F2F2F7".toColorInt(),
    val loadingIndicatorColor: Int = "#AEAEB2".toColorInt(),
    val loadingText: String = "Loading video ad...",
    val loadingTextColor: Int = "#AEAEB2".toColorInt(),
    val errorBackgroundColor: Int = "#F2F2F7".toColorInt(),
    val errorTextColor: Int = Color.RED,
    val retryButtonText: String = "Retry",
    val retryButtonColor: Int = "#007AFF".toColorInt(),
    val retryButtonBackgroundColor: Int = "#E5E5EA".toColorInt()
) {
    companion object {
        val DEFAULT = CriteoVideoAdConfiguration()
    }
}

/**
 * Current state of the video ad
 */
sealed interface CriteoVideoAdState {
    data object NotLoaded : CriteoVideoAdState
    data object Loading : CriteoVideoAdState
    data object Ready : CriteoVideoAdState
    data class Error(val error: Throwable) : CriteoVideoAdState
}

/**
 * Errors specific to video ad loading
 */
sealed class CriteoVideoAdError(message: String) : Exception(message) {
    class InvalidURL(url: String) : CriteoVideoAdError("Invalid VAST URL: $url")
    class VastParsingFailed(message: String) : CriteoVideoAdError("VAST parsing failed: $message")
    class AssetDownloadFailed(message: String) : CriteoVideoAdError("Asset download failed: $message")
    class PlayerError(message: String) : CriteoVideoAdError("Player error: $message")
}

