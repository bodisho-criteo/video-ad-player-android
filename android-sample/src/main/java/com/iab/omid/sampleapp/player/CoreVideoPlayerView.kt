package com.iab.omid.sampleapp.player

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val PLAYER_MUTE = 0f
private const val PLAYER_UNMUTE = 1f
private const val PROGRESS_INTERVAL_MS = 100L // Parity with legacy implementation

/**
 * Core reusable video player view (media-only). No VAST / OMID logic here.
 * Provides state & effect streams plus imperative controls.
 */
class CoreVideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), Player.Listener {

    sealed class PlaybackState(
        open val positionMs: Long = 0L,
        open val durationMs: Long = 0L,
        open val bufferedMs: Long = 0L,
        open val isMuted: Boolean = false,
        val quartile: Quartile = Quartile.from(positionMs, durationMs)
    ) {

        data object Idle : PlaybackState()

        data object Loading : PlaybackState()

        data class Ready(
            override val positionMs: Long,
            override val durationMs: Long,
            override val bufferedMs: Long,
            override val isMuted: Boolean,
        ) : PlaybackState(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedMs = bufferedMs,
            isMuted = isMuted
        )

        data class Playing(
            override val positionMs: Long,
            override val durationMs: Long,
            override val bufferedMs: Long,
            override val isMuted: Boolean
        ) : PlaybackState(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedMs = bufferedMs,
            isMuted = isMuted
        )

        data class Paused(
            override val positionMs: Long,
            override val durationMs: Long,
            override val bufferedMs: Long,
            override val isMuted: Boolean
        ) : PlaybackState(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedMs = bufferedMs,
            isMuted = isMuted
        )

        data class Buffering(
            override val positionMs: Long,
            override val durationMs: Long,
            override val bufferedMs: Long,
            override val isMuted: Boolean
        ) : PlaybackState(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedMs = bufferedMs,
            isMuted = isMuted
        )

        data class Ended(
            override val positionMs: Long,
            override val durationMs: Long,
            override val bufferedMs: Long,
            override val isMuted: Boolean
        ) : PlaybackState(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedMs = bufferedMs,
            isMuted = isMuted
        )

        data class Error(val throwable: Throwable) : PlaybackState()
    }

    sealed class PlayerEffect {
        data class FatalError(val throwable: Throwable) : PlayerEffect()
        data class VolumeChanged(val isMuted: Boolean) : PlayerEffect()
        data class QuartileAdvanced(val quartile: Quartile) : PlayerEffect()
    }

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<PlayerEffect>(extraBufferCapacity = 8) // TODO why 8?
    val effects: SharedFlow<PlayerEffect> = _effects.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val playerView: PlayerView = PlayerView(context)

    private var player: ExoPlayer? = null
    private var progressJobStarted = false
    private var currentQuartile: Quartile = Quartile.UNKNOWN

    private val isMuted: Boolean
        get() = player?.volume == PLAYER_MUTE

    init {
        playerView.layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        playerView.useController = false
        addView(playerView)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }

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
            }

        scope.launch {
            effects.collect { effect ->
                when (effect) {
                    is PlayerEffect.VolumeChanged -> {
                        Log.d(TAG, "Volume changed, isMuted=${effect.isMuted}")
                        _state.value = when (val s = _state.value) {
                            is PlaybackState.Ready -> s.copy(isMuted = effect.isMuted)
                            is PlaybackState.Playing -> s.copy(isMuted = effect.isMuted)
                            is PlaybackState.Paused -> s.copy(isMuted = effect.isMuted)
                            is PlaybackState.Buffering -> s.copy(isMuted = effect.isMuted)
                            else -> s
                        }
                    }
                    is PlayerEffect.QuartileAdvanced -> {
                        Log.d(TAG, "Quartile advanced: ${effect.quartile}")
                    }
                    is PlayerEffect.FatalError -> {
                        Log.e(TAG, "Fatal error: ${effect.throwable.message}")
                        _state.value = PlaybackState.Error(effect.throwable)
                    }
                }
            }
        }

        _state.value = PlaybackState.Loading
    }

    fun release() {
        player?.removeListener(this)
        player?.release()
        player = null

        progressJobStarted = false
        currentQuartile = Quartile.UNKNOWN

        scope.cancel()

        _state.value = PlaybackState.Idle
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun toggleMute() {
        player?.volume = if (isMuted) PLAYER_UNMUTE else PLAYER_MUTE
        _effects.tryEmit(PlayerEffect.VolumeChanged(isMuted))
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val p = player ?: return
        when (playbackState) {
            Player.STATE_READY -> {
                if (!progressJobStarted) startProgressLoop()
                if (_state.value is PlaybackState.Loading || _state.value is PlaybackState.Idle) {
                    _state.value = PlaybackState.Ready(
                        positionMs = p.currentPosition,
                        durationMs = if (p.duration > 0) p.duration else 0L,
                        bufferedMs = p.bufferedPosition,
                        isMuted = isMuted
                    )
                }
            }
            Player.STATE_BUFFERING -> {
                _state.value = PlaybackState.Buffering(
                    positionMs = p.currentPosition,
                    durationMs = if (p.duration > 0) p.duration else 0L,
                    bufferedMs = p.bufferedPosition,
                    isMuted = isMuted
                )
            }
            Player.STATE_ENDED -> {
                _state.value = PlaybackState.Ended(
                    positionMs = p.currentPosition,
                    durationMs = if (p.duration > 0) p.duration else 0L,
                    bufferedMs = p.bufferedPosition,
                    isMuted = isMuted
                )
            }
            Player.STATE_IDLE -> {
                _state.value = PlaybackState.Idle
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val p = player ?: return
        _state.value = if (isPlaying) {
            PlaybackState.Playing(
                positionMs = p.currentPosition,
                durationMs = if (p.duration > 0) p.duration else 0L,
                bufferedMs = p.bufferedPosition,
                isMuted = isMuted
            )
        } else {
            // Only switch to Paused if not buffering/ended
            when (_state.value) {
                is PlaybackState.Buffering,
                is PlaybackState.Ended -> _state.value
                else -> PlaybackState.Paused(
                    positionMs = p.currentPosition,
                    durationMs = if (p.duration > 0) p.duration else 0L,
                    bufferedMs = p.bufferedPosition,
                    isMuted = isMuted
                )
            }
        }
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
                    if (newQuartile != currentQuartile  && newQuartile.ordinal > currentQuartile.ordinal) {
                        _effects.tryEmit(PlayerEffect.QuartileAdvanced(newQuartile))
                    }
                }

                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        _effects.tryEmit(PlayerEffect.FatalError(error))
    }

    companion object { private const val TAG = "CoreVideoPlayer" }
}
