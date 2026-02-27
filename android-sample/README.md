# Criteo Video Ad Player for Android

The Criteo Video Ad Player for Android is an open-source library that provides a ready-to-integrate video ad wrapper for rendering and tracking Onsite Video ads in native Android apps.

## Features

- **VAST Parsing** – Fetches VAST XML from a tag URL (or raw XML) and extracts media files, tracking beacons, click-through URLs, closed captions, and verification data via DOM/XPath.
- **ExoPlayer Playback** – Plays the selected MP4 rendition using `androidx.media3`, with subtitle (WebVTT) support, mute/unmute, and looping.
- **OMID SDK** – Creates ad sessions, fires ad/media lifecycle events (impression, quartiles, pause/resume, volume change, click), and registers UI controls as friendly obstructions.
- **Beacon Tracking** – Fires impression, quartile, click, pause/resume, and mute/unmute beacons with retry logic (up to 3 attempts, exponential backoff).
- **Viewability** – Quartile progress tracking (0 %, 25 %, 50 %, 75 %, 100 %) drives both VAST beacons and OMID media events.

## Project Structure

```
src/main/java/com/iab/omid/sampleapp/
├── MainActivity.kt / AdApplication.kt       # App entry & navigation
├── *Fragment.kt                             # Basic player, feed player, example selector
├── player/
│   ├── CriteoVideoPlayer.kt                # Core ExoPlayer wrapper + state tracking
│   ├── CriteoVideoAdWrapper.kt             # High-level ad lifecycle (load → play → cleanup)
│   └── tracking/Quartile.kt                # Progress quartile enum
├── manager/
│   ├── NetworkManager.kt                   # VAST fetch & parse orchestration
│   ├── BeaconManager.kt                    # Beacon firing with retry
│   ├── CreativeDownloader.kt               # Video/caption asset download
│   ├── vast/                               # VastManager, VastAd, VastMediaFile
│   └── omid/                               # OMIDSessionInteractor + factory + stub
└── util/CriteoLogger.kt                    # Structured logging
```

## Requirements

- Android SDK 24+ (target 30)
- OMSDK AAR in `libs/` (not bundled)

## Build Configuration

Key `buildConfigField` values in `build.gradle`:

| Field | Purpose |
|---|---|
| `PARTNER_NAME` | OMID partner identifier |
| `VENDOR_KEY` | Verification vendor key |
| `VERIFICATION_URL` | JS verification script URL |
| `VERIFICATION_PARAMETERS` | JSON beacon map passed to the verification script |
