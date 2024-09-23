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

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
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
				try {
					doca = doc;
					NodeList mediaFiles = (NodeList) xPathFactory.newXPath().compile("//MediaFile").evaluate(doc, XPathConstants.NODESET);
					initializePlayer(mediaFiles.item(0).getTextContent());
				} catch (XPathExpressionException e) {
					throw new RuntimeException(e);
				}
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

	private void emitBeacon(String url) {
		Request request = new Request.Builder().url(url).build();
		client.newCall(request).enqueue(emptyCallback);
	}

	private void initializePlayer(String videoUrl) {
		// Create an ExoPlayer and set it as the player for content and ads.
		player = new ExoPlayer.Builder(this)
				.build();

		// Create the MediaItem to play
		MediaItem mediaItem =
				new MediaItem.Builder()
						.setUri(Uri.parse(videoUrl))
						.build();
		player.setMediaItem(mediaItem);
		// Add listener on player events
		player.addListener(this);

		player.setVolume(PLAYER_UNMUTE);
		playerView.setPlayer(player);

		// Disable default player controls (default controls allow seeking)
		playerView.setUseController(false);
		player.setPlayWhenReady(true);
		player.prepare();

		player.addListener(new Player.Listener() {
			@Override
			public void onPlayerError(PlaybackException error) {
				Log.e("ExoPlayer", "Error occurred: " + error.getMessage());
			}
		});

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
			NodeList trackingNodes;
			try {
				trackingNodes = (NodeList) xPathFactory.newXPath().compile("//Impression").evaluate(doca, XPathConstants.NODESET);
			} catch (XPathExpressionException e) {
				throw new RuntimeException(e);
			}
			String adImpression = trackingNodes.item(0).getTextContent();
			Request request = new Request.Builder().url(adImpression).build();
			client.newCall(request).enqueue(emptyCallback);
			loaded = true;

			VastProperties vastProperties = VastProperties.createVastPropertiesForNonSkippableMedia(false, Position.STANDALONE);
			adEvents.loaded(vastProperties);
		}

		mediaEvents.bufferFinish();

		postProgress();
	}

	private void onStateEnded() {
		if (!complete) {

			NodeList trackingNodes;
			try {
				trackingNodes = (NodeList) xPathFactory.newXPath().compile("//Tracking[@event='complete']").evaluate(doca, XPathConstants.NODESET);
			} catch (XPathExpressionException e) {
				throw new RuntimeException(e);
			}
			String url = trackingNodes.item(0).getTextContent();
			Request request = new Request.Builder().url(url).build();
			client.newCall(request).enqueue(emptyCallback);

			// forward event to OMID (related to next section of the document)
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
				NodeList trackingNodes;
				try {
					trackingNodes = (NodeList) xPathFactory.newXPath().compile("//ClickThrough").evaluate(doca, XPathConstants.NODESET);
				} catch (XPathExpressionException e) {
					throw new RuntimeException(e);
				}
				String clickThroughUrl = trackingNodes.getLength() > 0 ? trackingNodes.item(0).getTextContent() : null;
				if (clickThroughUrl != null) {
					clickThroughHandler();
				} else {
					handlePlayPause();
				}
				break;
			case R.id.muteTextView:
				toggleMute();
				break;
		}
	}

	public void clickThroughHandler() {
		NodeList trackingNodes;
		try {
			trackingNodes = (NodeList) xPathFactory.newXPath().compile("//ClickThrough").evaluate(doca, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
		String clickThroughUrl = trackingNodes.getLength() > 0 ? trackingNodes.item(0).getTextContent() : null;
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
			NodeList trackingNodes;
			try {
				trackingNodes = (NodeList) xPathFactory.newXPath().compile("//Tracking[@event='pause']").evaluate(doca, XPathConstants.NODESET);
			} catch (XPathExpressionException e) {
				throw new RuntimeException(e);
			}
			Request request = new Request.Builder().url(trackingNodes.item(0).getTextContent()).build();
			client.newCall(request).enqueue(emptyCallback);
		} else {
			playVideo();
			NodeList trackingNodes;
			try {
				trackingNodes = (NodeList) xPathFactory.newXPath().compile("//Tracking[@event='resume']").evaluate(doca, XPathConstants.NODESET);
			} catch (XPathExpressionException e) {
				throw new RuntimeException(e);
			}
			Request request = new Request.Builder().url(trackingNodes.item(0).getTextContent()).build();
			client.newCall(request).enqueue(emptyCallback);
		}
		isPlayingWhen50Visible = !isPlayingWhen50Visible;
	}

	private void playVideo() {
		player.play();

		// forward event to OMID (related to next section of the document)
		mediaEvents.resume();
	}

	private void pauseVideo() {
		player.pause();

		// forward event to OMID (related to next section of the document)
		mediaEvents.pause();
	}

	public void toggleMute() {
		float currentVolume = player.getVolume();
		float targetVolume;
		if (currentVolume == PLAYER_MUTE) {
			targetVolume = PLAYER_UNMUTE;
			muteTextView.setText("Mute");

			NodeList trackingNodes;
			try {
				trackingNodes = (NodeList) xPathFactory.newXPath().compile("//Tracking[@event='unmute']").evaluate(doca, XPathConstants.NODESET);
			} catch (XPathExpressionException e) {
				throw new RuntimeException(e);
			}
			Request request = new Request.Builder().url(trackingNodes.item(0).getTextContent()).build();
			client.newCall(request).enqueue(emptyCallback);
		} else {
			targetVolume = PLAYER_MUTE;
			muteTextView.setText("Unmute");

			NodeList trackingNodes;
			try {
				trackingNodes = (NodeList) xPathFactory.newXPath().compile("//Tracking[@event='mute']").evaluate(doca, XPathConstants.NODESET);
			} catch (XPathExpressionException e) {
				throw new RuntimeException(e);
			}
			Request request = new Request.Builder().url(trackingNodes.item(0).getTextContent()).build();
			client.newCall(request).enqueue(emptyCallback);
		}
		player.setVolume(targetVolume);
		// forward event to OMID (related to next section of the document)
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
				NodeList trackingNodes;
				try {
					trackingNodes = (NodeList) xPathFactory.newXPath().compile("//Tracking[@event='start']").evaluate(doca, XPathConstants.NODESET);
				} catch (XPathExpressionException e) {
					throw new RuntimeException(e);
				}
				String url = trackingNodes.item(0).getTextContent();
				Request request = new Request.Builder().url(url).build();

				client.newCall(request).enqueue(emptyCallback);


				mediaEvents.start(player.getDuration(), PLAYER_UNMUTE);
				adEvents.impressionOccurred();
				break;
			case FIRST:
				mediaEvents.firstQuartile();
				break;
			case SECOND:
				mediaEvents.midpoint();
				break;
			case THIRD:
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
