package com.iab.omid.sampleapp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.extractor.text.Subtitle;
import androidx.media3.ui.PlayerView;


import com.iab.omid.library.criteo.adsession.AdEvents;
import com.iab.omid.library.criteo.adsession.AdSession;
import com.iab.omid.library.criteo.adsession.media.MediaEvents;
import com.iab.omid.library.criteo.adsession.media.Position;
import com.iab.omid.library.criteo.adsession.media.VastProperties;
import com.iab.omid.sampleapp.util.AdSessionUtil;
import com.iab.omid.sampleapp.util.VastParser;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.util.Collections;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * A fragment representing a single ad detail screen.
 *

 *
 * This sample shows loading a Native ad, passing a url to the Omid js, and marking the impression
 */
public class VideoAdNativeActivity extends Activity implements Player.Listener, View.OnClickListener {

	private static final String TAG = "CriteoVideoAd";

	// link to vast, 10.0.2.2 in android emulator points to localhost of the pc
	// http://10.0.2.2:8000/vast_omid.xml
	private static final String VAST_URL = "http://10.0.2.2:8000/vast_omid.xml";

	private static final int PLAYER_UNMUTE = 1;
	private static final int PLAYER_MUTE = 0;

	private AdSession adSession;
	private MediaEvents mediaEvents;
	private AdEvents adEvents;

	private static final int PROGRESS_INTERVAL_MS = 100;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private Quartile lastSentQuartile = Quartile.UNKNOWN;

	private boolean complete;
	private boolean loaded;

	private PlayerView playerView;
	private ExoPlayer player;
	private TextView muteTextView;

	private static final XPathFactory xPathFactory = XPathFactory.newInstance();

	public Document doca;
	public VastParser vastFetcher;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.video_ad_native_detail);

		playerView = findViewById(R.id.videoView);
		muteTextView = findViewById(R.id.muteTextView);

		playerView.requestFocus();

		muteTextView.setOnClickListener(this);
		playerView.setOnClickListener(this);

		try {
			adSession = AdSessionUtil.getNativeAdSession(this);
		} catch (MalformedURLException e) {
			// todo: drop verificationNotExecuted beacon
			throw new UnsupportedOperationException(e);
		}
		mediaEvents = MediaEvents.createMediaEvents(adSession);
		adEvents = AdEvents.createAdEvents(adSession);
		adSession.registerAdView(playerView);
		adSession.start();

		vastFetcher = new VastParser();
		vastFetcher.fetchAndParseVast(VAST_URL, new VastParser.VASTFetchCallback() {
			@Override
			public void onSuccess(Document doc) {
				// Process the document and update the UI
				doca = doc;
				String creativeUrl = extractByXpath("//MediaFile");
				String ccUrl = extractByXpath("//ClosedCaptionFile");
				initializePlayer(creativeUrl, ccUrl);
			}

			@Override
			public void onFailure(Exception e) {
				// Handle the error
				Log.e("MainActivity", "Error fetching VAST XML", e);
			}
		});
	}

	private boolean isVideoViewAtLeast50Visible(View videoView) {
		// Get the visible rectangle of the view
		Rect visibleRect = new Rect();
		boolean isVisible = videoView.getGlobalVisibleRect(visibleRect);

		if (!isVisible) {
			return false; // Not visible at all
		}

		// Get total area of the video view
		int totalArea = videoView.getWidth() * videoView.getHeight();
		if (totalArea == 0) {
			return false; // If the view has no size yet
		}

		// Get the visible area
		int visibleArea = visibleRect.width() * visibleRect.height();

		// Check if visible area is at least 50% of the total area
		return (visibleArea >= totalArea / 2);
	}

	private String extractByXpath(String xpath) {
		try {
			NodeList nodes = (NodeList) xPathFactory.newXPath().compile(xpath).evaluate(doca, XPathConstants.NODESET);
			if (nodes.getLength() == 0 ) {
				Log.e(TAG, "Didn't find anything on the provided xpath " + xpath);
				return null;
			}
			if (nodes.getLength() > 1) {
				Log.w(TAG, "Several nodes found with the provided xpath " + xpath + ". Choosing the first one");
			}

			// ugly, to refactor
			if (!nodes.item(0).hasChildNodes())
				return nodes.item(0).getTextContent();
			else
				return nodes.item(0).getFirstChild().getTextContent();
		} catch (XPathExpressionException e) {
			Log.e(TAG, "Couldn't extract the data from the given xpath" + xpath, e);
			return null;
		}
	}

	private void emitBeacon(String url) {
		Request request = new Request.Builder().url(url).build();
		client.newCall(request).enqueue(emptyCallback);
	}

	private void emitVastBeacon(String xpath) {
		String url = extractByXpath(xpath);
		if (url != null) {
			emitBeacon(url);
		} else {
			Log.e(TAG, "Nothing found in the xpath " + xpath + ". Not emitting the beacon");
		}
	}

	@OptIn(markerClass = UnstableApi.class) private void initializePlayer(String videoUrl, String ccUrl) {
		// Create an ExoPlayer and set it as the player for content and ads.

		// add embeded subtitles
		DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);
		TrackSelectionParameters parameters = new TrackSelectionParameters.Builder(this)
				.setPreferredTextLanguage("fr") // Set preferred language, e.g., English
				.build();
		trackSelector.setParameters(parameters);

		player = new ExoPlayer.Builder(this)
				.setTrackSelector(trackSelector)
				.build();

		// Create the MediaItem to play
		MediaItem.Builder mediaItemBuilder =
				new MediaItem.Builder()
						.setUri(Uri.parse(videoUrl));

		// add external subtitles
		if (ccUrl != null) {
			// Set your VTT subtitle URI
			Uri subtitleUri = Uri.parse(ccUrl);

			// Prepare the subtitle configuration
			MediaItem.SubtitleConfiguration subtitleConfig = new MediaItem.SubtitleConfiguration.Builder(subtitleUri)
					.setMimeType(MimeTypes.TEXT_VTT) // The MIME type for WebVTT subtitles
					.setLanguage("en") // Optional: Specify the language
					.setSelectionFlags(C.SELECTION_FLAG_DEFAULT) // Optional: Set flags like default
					.build();

			mediaItemBuilder.setSubtitleConfigurations(Collections.singletonList(subtitleConfig));
		}

		MediaItem mediaItem = mediaItemBuilder.build();

		player.setMediaItem(mediaItem);
		// Add listener on player events
		player.addListener(this);

		player.setVolume(PLAYER_UNMUTE);
		playerView.setPlayer(player);

		// Disable default player controls (default controls allow seeking)
		playerView.setUseController(false);
		player.setPlayWhenReady(true);
		player.prepare();

		// Monitor visibility
		playerView.getViewTreeObserver().addOnPreDrawListener(() -> {
			// Check if at least 50% of the video is visible
			if (isPlayingWhen50Visible && isVideoViewAtLeast50Visible(playerView)) {
				playVideo();
			} else {
				pauseVideo();
			}
			return true;
		});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		adSession.finish();
		adSession = null;

		handler.removeCallbacksAndMessages(null);
		releasePlayer();
	}

	private void releasePlayer() {
		if (player != null) {
			player.release();
			player = null;
		}
	}

	@Override
	public void onPlaybackStateChanged(@Player.State int playbackState) {
		switch (playbackState) {
			case ExoPlayer.STATE_READY:
				onStateReady();
				break;
			case ExoPlayer.STATE_ENDED:
				onStateEnded();
				break;
			case ExoPlayer.STATE_BUFFERING:
				onStateBuffering();
				break;
			case ExoPlayer.STATE_IDLE:
				break;
			default:
				throw new IllegalStateException("Unknown playbackState: " + playbackState);
		}
	}

	private void onStateReady() {

		if (!loaded) {
			emitVastBeacon("//Impression");
			loaded = true;

			VastProperties vastProperties = VastProperties.createVastPropertiesForNonSkippableMedia(false, Position.STANDALONE);
			adEvents.loaded(vastProperties);
			adEvents.impressionOccurred();
		}

		mediaEvents.bufferFinish();

		postProgress();
	}

	private void onStateEnded() {
		if (!complete) {
			emitVastBeacon("//Tracking[@event='complete']");

			// forward event to OMID
			mediaEvents.complete();
			complete = true;
		}

		player.seekTo(0);
		player.play();
	}

	private void onStateBuffering() {
		mediaEvents.bufferStart();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.videoView:
				String clickThroughUrl = extractByXpath("//ClickThrough");
				if (clickThroughUrl != null) {
					clickThroughHandler(clickThroughUrl);
				} else {
					handlePlayPause();
				}
				break;
			case R.id.muteTextView:
				toggleMute();
				break;
		}
	}

	public void clickThroughHandler(String clickThroughUrl) {
		try {
			Intent externalIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(clickThroughUrl));
			this.startActivity(externalIntent);
		} catch (ActivityNotFoundException e) {
			e.printStackTrace();
		}
	}

	private boolean isPlayingWhen50Visible = true;
	public void handlePlayPause() {
		if (isPlayingWhen50Visible) {
			pauseVideo();
			emitVastBeacon("//Tracking[@event='pause']");
		} else {
			playVideo();
			emitVastBeacon("//Tracking[@event='resume']");
		}
		isPlayingWhen50Visible = !isPlayingWhen50Visible;
	}

	private void playVideo() {
		player.play();

		// forward event to OMID
		mediaEvents.resume();
	}

	private void pauseVideo() {
		player.pause();

		// forward event to OMID
		mediaEvents.pause();
	}

	public void toggleMute() {
		float currentVolume = player.getVolume();
		float targetVolume;
		if (currentVolume == PLAYER_MUTE) {
			targetVolume = PLAYER_UNMUTE;
			muteTextView.setText("Mute");

			emitVastBeacon("//Tracking[@event='unmute']");
		} else {
			targetVolume = PLAYER_MUTE;
			muteTextView.setText("Unmute");

			emitVastBeacon("//Tracking[@event='mute']");
		}
		player.setVolume(targetVolume);
		// forward event to OMID
		mediaEvents.volumeChange(targetVolume);
	}

	private static class ProgressRunnable implements Runnable {
		private final WeakReference<VideoAdNativeActivity> videoAdNativeActivityWeakReference;

		ProgressRunnable(VideoAdNativeActivity videoAdNativeActivity) {
			this.videoAdNativeActivityWeakReference = new WeakReference<>(videoAdNativeActivity);
		}

		@Override
		public void run() {
			VideoAdNativeActivity videoAdNativeActivity = videoAdNativeActivityWeakReference.get();
			if (videoAdNativeActivity == null) {
				return;
			}
			videoAdNativeActivity.onProgress();
		}
	}

	private void onProgress() {
		if (adSession == null) {
			return;
		}

		if (complete) {
			return;
		}

		updateQuartile();
		postProgress();
	}

	private void postProgress() {
		handler.removeCallbacks(progressRunnable);
		handler.postDelayed(progressRunnable, PROGRESS_INTERVAL_MS);
	}
	private final ProgressRunnable progressRunnable = new ProgressRunnable(this);

	private void updateQuartile() {
		final long duration = player.getDuration();
		final long currentPosition = player.getCurrentPosition();

		if (duration != 0) {
			final Quartile currentQuartile = getQuartile(currentPosition, duration);

			// Don't send old quartile stats that we have either already sent, or passed.
			if (currentQuartile != lastSentQuartile && currentQuartile.ordinal() > lastSentQuartile.ordinal()) {
				sendQuartile(currentQuartile);
				lastSentQuartile = currentQuartile;
			}
		}
	}

	private final OkHttpClient client = new OkHttpClient();
	private final Callback emptyCallback = new Callback() {
		@Override
		public void onFailure(Call call, IOException e) { }

		@Override
		public void onResponse(Call call, Response response) { }
	};


	private void sendQuartile(Quartile quartile) {
		switch (quartile) {
			case START:
				emitVastBeacon("//Tracking[@event='start']");
				mediaEvents.start(player.getDuration(), PLAYER_UNMUTE);
				break;
			case FIRST:
				emitVastBeacon("//Tracking[@event='firstQuartile']");
				mediaEvents.firstQuartile();
				break;
			case SECOND:
				emitVastBeacon("//Tracking[@event='midpoint']");
				mediaEvents.midpoint();
				break;
			case THIRD:
				emitVastBeacon("//Tracking[@event='thirdQuartile']");
				mediaEvents.thirdQuartile();
				break;
			case UNKNOWN:
			default:
				break;
		}
	}

	// 3. firing beacons
	public enum Quartile {
		UNKNOWN,
		START,
		FIRST,
		SECOND,
		THIRD,
	}

	private static Quartile getQuartile(long position, long duration) {
		final double completionFraction = position / (double) duration;
		if (lessThan(completionFraction, 0.01)) {
			return Quartile.UNKNOWN;
		}

		if (lessThan(completionFraction, 0.25)) {
			return Quartile.START;
		}

		if (lessThan(completionFraction, 0.5)) {
			return Quartile.FIRST;
		}

		if (lessThan(completionFraction, 0.75)) {
			return Quartile.SECOND;
		}

		// We report Quartile.THIRD when completionFraction > 1 on purpose
		// since track might technically report elapsed time after it's completion
		// and if Quartile.THIRD hasn't been reported already, it will be lost
		return Quartile.THIRD;
	}

	private static boolean lessThan(double a, double b){
		return b - a > .000001;
	}
}
