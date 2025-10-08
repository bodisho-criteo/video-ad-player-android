package com.iab.omid.sampleapp.player

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.iab.omid.sampleapp.player.tracking.Quartile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val PLAYER_MUTE = 0f
private const val PLAYER_UNMUTE = 1f
private const val PROGRESS_INTERVAL_MS = 100L // Parity with legacy implementation

/**
 * Core reusable video player view.
 */
class CoreVideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), Player.Listener {

    data class PlayerState(
        val playbackState: PlaybackState = PlaybackState.IDLE,
        val isMuted: Boolean = false,
        val quartile: Quartile = Quartile.UNKNOWN
    )

    enum class PlaybackState {
        IDLE,
        LOADING,
        PLAYING,
        PAUSED,
        FINISHED,
        ERROR;
    }

    private val _state = MutableStateFlow<PlayerState>(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val playerView: PlayerView = PlayerView(context)

    private var player: ExoPlayer? = null
    private var progressJobStarted = false
    private var currentQuartile: Quartile = Quartile.UNKNOWN

    init {
        playerView.layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        playerView.useController = false
        addView(playerView)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }

    // PLAYER LISTENER CALLBACKS
    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)

        val p = player ?: return
        _state.update { currentState ->
            currentState.copy(
                playbackState = when (playbackState) {
                    Player.STATE_READY -> {
                        if (!progressJobStarted) startProgressLoop()
                        if (p.isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
                    }
                    Player.STATE_BUFFERING -> PlaybackState.LOADING
                    Player.STATE_ENDED -> PlaybackState.FINISHED
                    Player.STATE_IDLE -> PlaybackState.IDLE
                    else -> PlaybackState.IDLE
                }
            )
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)

        _state.update { currentState ->
            currentState.copy(
                playbackState = if (isPlaying) {
                    PlaybackState.PLAYING
                } else {
                    // Only switch to Paused if not buffering/finished
                    when (currentState.playbackState) {
                        PlaybackState.LOADING, PlaybackState.FINISHED -> currentState.playbackState
                        else -> PlaybackState.PAUSED
                    }
                }
            )
        }
    }

    override fun onVolumeChanged(volume: Float) {
        super.onVolumeChanged(volume)
        _state.update { currentState -> currentState.copy(isMuted = volume == PLAYER_MUTE) }
    }

    override fun onPlayerError(error: PlaybackException) {
        _state.update { currentState -> currentState.copy(playbackState = PlaybackState.ERROR) }
    }

    // PUBLIC PLAYER API
    @OptIn(UnstableApi::class)
    fun load(videoUri: Uri, subtitleUri: Uri? = null) {
        release() // Release the player and reset state in case it was already initialized

        // Create the MediaItem to play
        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .also { builder ->
                subtitleUri?.let {
                    // Subtitle configuration for external WebVTT subtitles
                    val subtitleConfig = SubtitleConfiguration.Builder(subtitleUri)
                        .setMimeType(MimeTypes.TEXT_VTT) // The MIME type for WebVTT subtitles
                        .setLanguage("en") // Optional: Specify the language
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT) // Optional: Set flags like default
                        .build()

                    builder.setSubtitleConfigurations(listOf(subtitleConfig))
                }
            }
            .build()

        // Create exoplayer with track selector for content and ads
        val trackSelector = DefaultTrackSelector(context).also { selector ->
            val parameters = TrackSelectionParameters.Builder(context)
                .setPreferredTextLanguage("fr") // Set preferred language, e.g., English
                .build()
            selector.setParameters(parameters)
        }

        player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .also { exoPlayer ->
                // Configure the player
                playerView.player = exoPlayer
                playerView.useController = false // Disable default player controls (prevents seeking)

                exoPlayer.addListener(this)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.volume = PLAYER_UNMUTE
                exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()

                _state.update { currentState ->
                    currentState.copy(
                        playbackState = PlaybackState.LOADING,
                        isMuted = exoPlayer.volume == PLAYER_MUTE,
                        quartile = Quartile.from(exoPlayer.currentPosition, exoPlayer.duration)
                    )
                }
            }
    }

    fun release() {
        player?.removeListener(this)
        player?.release()
        player = null

        progressJobStarted = false
        currentQuartile = Quartile.UNKNOWN

        scope.cancel()

        _state.update { currentState ->
            currentState.copy(
                playbackState = PlaybackState.IDLE,
                quartile = Quartile.UNKNOWN
            )
        }
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun toggleMute() {
        player?.let {
            it.volume = if (it.volume == PLAYER_MUTE) PLAYER_UNMUTE else PLAYER_MUTE
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    private fun startProgressLoop() {
        progressJobStarted = true
        scope.launch {
            while (isActive) {
                val duration = player?.duration ?: 0L
                val currentPosition = player?.currentPosition

                if (duration != 0L && currentPosition != null) {
                    val newQuartile: Quartile = Quartile.from(currentPosition, duration)

                    // Don't send old quartile stats that we have either already sent, or passed.
                    if (newQuartile.ordinal > currentQuartile.ordinal) {
                        _state.update { currentState -> currentState.copy(quartile = newQuartile) }
                    }
                }

                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }
}
