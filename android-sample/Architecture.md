# Architecture Overview

This document explains the refactored video ad playback architecture, its layers, class responsibilities, data flows, parity constraints with the legacy implementation, and known improvement opportunities.

---
## Goals of the Refactor
- Isolate raw media playback from ad / VAST / OMID concerns.
- Make the playback component reusable outside ad contexts.
- Encapsulate quartile + visibility + mute behavior in a testable manner.
- Preserve ALL externally observable legacy behaviors ("parity first").
- Provide a clear path for future enhancements without re‑entangling concerns.

---
## Layered Structure (High-Level)

```
┌────────────────────────────────────────────────────────────────┐
│                    VideoAdNativeActivity (UI Shell)            │
│  - Inflates layout                                             │
│  - Wires visibility heuristic (>50% rule)                      │
│  - Forwards click + mute intents                               │
│                                                                │
│   ┌────────────────────────────────────────────────────────┐   │
│   │ AdVideoPlayerView (Convenience Composite View)         │   │
│   │  - Owns:                                               │   │
│   │     * CoreVideoPlayerView                              │   │
│   │     * AdPlaybackController                             │   │
│   │  - Simple initialize(vastUrl) API                      │   │
│   └────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
            │                ▲
            │                │ (delegated events / calls)
            ▼                │
┌───────────────────────────────────────────────────────────────┐
│ AdPlaybackController                                          │
│  - VAST fetch & parse (VastParser)                            │
│  - OMID AdSession + MediaEvents + AdEvents                    │
│  - Beacon emission (OkHttp inline)                            │
│  - Maps Core effects → VAST + OMID events                     │
│  - Handles clickthrough, pause/resume beacons, mute beacons   │
│  - Visibility-based play/pause (no beacons)                   │
└───────────────────────────────────────────────────────────────┘
            │ (state & effects subscription)
            ▼
┌───────────────────────────────────────────────────────────────┐
│ CoreVideoPlayerView                                           │
│  - Pure ExoPlayer wrapper                                     │
│  - StateFlow<PlaybackState>                                   │
│  - SharedFlow<PlayerEffect> (Quartile, Volume, FatalError)     │
│  - Looping, mute, progress loop (100ms)                       │
│  - Emits single-flight quartile effects                       │
└───────────────────────────────────────────────────────────────┘
            │
            ▼
┌───────────────────────────────────────────────────────────────┐
│ ExoPlayer                                                     │
└───────────────────────────────────────────────────────────────┘
```

Supporting Utilities:
- `Quartile` enum: Stateless quartile classification (+ 1% start threshold parity).
- `VastParser`: Background thread XML fetch/parse → DOM Document.
- `AdSessionUtil`: Creation & activation of OMID AdSession context.

---
## Core Components Detailed

### 1. CoreVideoPlayerView
**Purpose:** Media-only playback component.

**Key Responsibilities:**
- Instantiate & manage an `ExoPlayer` instance (looping enabled via `repeatMode = REPEAT_MODE_ONE`).
- Expose lifecycle-safe playback state through `StateFlow<PlaybackState>`.
- Emit discrete one-off events via `SharedFlow<PlayerEffect>`:
  - `VolumeChanged` (mute/unmute toggles)
  - `QuartileAdvanced` (START/FIRST/SECOND/THIRD once per media load)
  - `FatalError`
- Poll progress every 100ms (legacy parity interval) instead of using a Handler.
- Track quartile progression **once** per video (no reset on loop).
- Allow external imperative controls: `load`, `play`, `pause`, `seekTo`, `toggleMute`.

**PlaybackState (sealed):** Idle, Loading, Ready, Playing, Paused, Buffering, Ended, Error.

**Effects:** Emitted asynchronously; consumers (e.g., `AdPlaybackController`) react to them.

**Quartile Handling Parity:**
- Emission only after crossing thresholds: >1%, ≥25%, ≥50%, ≥75%.
- No re-emission after looping past completion.

### 2. Quartile (enum)
**Purpose:** Stateless computation of playback quartile bucket.

**Companion Method:** `from(positionMs, durationMs)` returning one of:
- UNKNOWN (fraction < 1%)
- START (1% ≤ fraction < 25%)
- FIRST (25% ≤ fraction < 50%)
- SECOND (50% ≤ fraction < 75%)
- THIRD (≥ 75%)

**Epsilon:** 1e-6 to prevent floating boundary flapping.

### 3. AdPlaybackController
**Purpose:** Orchestrates ad-specific concerns on top of the core player.

**Responsibilities:**
- Fetch VAST via `VastParser` → retain DOM.
- Extract Media File, ClosedCaptionFile, Impression, Tracking (start, quartiles, complete, mute/unmute, pause/resume), ClickThrough URLs via XPath.
- Initialize & start OMID AdSession (native video context) only once.
- Map lifecycle + quartile events → OMID `MediaEvents` + `AdEvents` and VAST tracking beacons.
- Handle user interactions:
  - ClickThrough open (if URL present) else toggling play/pause (emitting pause/resume beacons).
  - Mute/unmute (beacons + OMID `volumeChange`).
- Visibility gating: apply play/pause without emitting VAST pause/resume beacons (parity).
- Loop playback after completion **without** restarting quartile tracking.

**Internal Flags:**
- `isLoaded` – impression & loaded events fired.
- `isComplete` – completion & quartile exhaustion reached.
- `userWantsPlay` – user intent separate from visibility gating.

### 4. AdVideoPlayerView
**Purpose:** Simplifies integration by composing `CoreVideoPlayerView` + `AdPlaybackController` inside a single view.

**API:**
- `initialize(vastUrl)`
- `onMuteClicked(updateLabel: (String) -> Unit)`
- `onAdViewClicked()`
- `applyVisibility(isAtLeast50: Boolean)`
- `isUserIntendedPlaying()`
- `release()`

### 5. VideoAdNativeActivity
**Purpose:** Minimal host for the demo experience.

**Responsibilities after refactor:**
- Inflate layout containing `AdVideoPlayerView` + mute button.
- Pass VAST URL into `initialize`.
- Wire click → `onAdViewClicked()` and update `isPlayingWhen50Visible`.
- Wire mute button → `onMuteClicked()` (label toggled externally).
- Run visibility check (>50%) in an `OnPreDrawListener` (legacy parity) → call `applyVisibility`.

### 6. VastParser
**Purpose:** Asynchronous DOM fetch/parse on a single-thread executor, posting success/failure to main thread.

### 7. AdSessionUtil
**Purpose:** Set up OMID: activation, partner, verification script resource(s), raw JavaScript loader.

---
## Event & Beacon Ordering (Parity Contract)
1. Player ready → (first time only):
   - VAST Impression beacon
   - OMID: `adEvents.loaded(vastProps)` then `adEvents.impressionOccurred()`
   - OMID: `mediaEvents.bufferFinish()` (unconditional)
2. Playback crosses 1% → Start beacon + `mediaEvents.start(durationMs, volume)` (duration in **milliseconds** preserved as legacy quirk).
3. Quartiles:
   - 25% → firstQuartile beacon + `firstQuartile()`
   - 50% → midpoint beacon + `midpoint()`
   - 75% → thirdQuartile beacon + `thirdQuartile()`
4. Completion (STATE_ENDED): complete beacon + `mediaEvents.complete()`; playback loops silently, no more quartiles.
5. User pause/resume (when no ClickThrough URL): emit pause/resume tracking beacons + OMID `pause()`/`resume()`.
6. Mute/unmute: mute / unmute tracking beacon first, then `mediaEvents.volumeChange(0f|1f)`.
7. Visibility-based pause/resume: only OMID `pause` / `resume`; **no** pause/resume tracking beacon (parity).

---
## Mute Semantics
- Volume toggles between 1f and 0f.
- UI label displays the *action* ("Mute" when unmuted; "Unmute" when muted).
- `VolumeChanged` effect propagates mute state so additional observers could react.

---
## Looping Behavior
- Looping enabled through `ExoPlayer.REPEAT_MODE_ONE`.
- Quartile progression does **not** reset; second and subsequent loops do not emit any quartile or start/impression/complete events again.

---
## Visibility Handling
- Activity computes visible area with `getGlobalVisibleRect` each pre-draw.
- If `<50%`, playback is paused (OMID pause); if `>=50%` and user intends play, playback resumes (OMID resume).
- No tracking beacons emitted for visibility-driven state changes.

---
## Subtitles Handling
- External WebVTT (if `ClosedCaptionFile` present) added as default selected track.
- Preferred text language still set to `"fr"` while actual subtitle configuration uses `"en"` (legacy mismatch intentionally preserved).

---
## Known Quirks Preserved Intentionally
| Area | Quirk | Reason |
|------|-------|--------|
| Start Threshold | Start beacon waits for >1% duration | Legacy parity |
| Duration Units | `mediaEvents.start()` passed milliseconds (should be seconds per OMID spec) | Parity over spec correctness |
| Buffer Finish | `bufferFinish()` called every READY | Matches previous unconditional call |
| Looping | Quartiles not re-fired post first completion | Parity |
| Subtitles | Language preference mismatch (fr vs en) | Parity |
| Visibility | Uses `OnPreDrawListener` continuous polling | Parity (simplest transplant) |

---
## Current Technical Debt / Potential Improvements
1. **Quartile Emission Logic Duplication (Needs Cleanup)**  
   In `CoreVideoPlayerView.startProgressLoop()` there is a *duplicated* quartile check block and a suspicious condition: `if (!currentQuartile != Quartile.THIRD && duration > 0)` which is logically wrong (`!currentQuartile` is invalid intent). Only one emission block should remain. A corrected form would be: `if (currentQuartile != Quartile.THIRD && duration > 0) { ... }` and remove the earlier pre-`p.isPlaying` block.
2. **Effects Collection Inside load():** Collecting `effects` within `load()` recreates a collector each load; a separate persistent collector with its own job (cancelled on release) would be cleaner.
3. **State Mutation in VolumeChanged Handling:** Updating `_state` inside effect collection duplicates logic already in toggle path; could centralize.
4. **OnPreDraw Listener Removal:** Not explicitly removed on destroy (can accumulate if view reused); consider detaching listener explicitly.
5. **Lack of Error Surface for VAST Failures:** Errors only logged; no surfaced state or callback for UI.
6. **No Backoff / Retry for Beacon Failures:** All network beacons fire once fire-and-forget.
7. **Mixed Responsibility in AdPlaybackController:** Could separate beacon emission into a helper for testability later (intentionally deferred for demo simplicity).
8. **Missing Unit Tests:** Quartile logic & parity ordering not verified automatically.
9. **No Lifecycle Awareness Beyond View Detach:** If used in fragments, should tie to lifecycle or expose a LifecycleObserver.
10. **Hard-Coded Progress Interval:** Could be configurable for testing or low-power scenarios.

---
## Extension Points (Future)
| Goal | Suggestion |
|------|------------|
| Multi-loop quartile reporting | Add `resetQuartilesOnLoop` config + reset logic in controller/core |
| Analytics decoupling | Introduce interfaces: `BeaconEmitter`, `OmidBridge` |
| Rich UI | Provide playback controls overlay component subscribing to state/effects |
| Testability | Add JVM tests for `Quartile.from`, integration tests with fake player clock |
| Error handling | Surface VAST parse & beacon failures via `PlayerEffect` or extended `PlaybackState` |
| Energy optimization | Replace polling with ExoPlayer analytics listener + periodic sampling fallback |

---
## Data & Control Flows Summary

### Playback Lifecycle Flow
1. `initialize(vastUrl)` → VAST fetched.
2. Media & optional CC URL extracted and loaded into core.
3. Core emits: Loading → Ready.
4. Controller sends impression + OMID loaded/impression events.
5. Core enters Playing, progress loop starts.
6. Quartile boundaries reached → `QuartileAdvanced` effects → Controller beacons + OMID quartile events.
7. End reached → complete beacon + OMID complete → seek(0) & play (loop) → no new quartiles.

### User Interaction Flow
| Action | Core Effect | Controller Reaction | Beacons / OMID |
|--------|-------------|--------------------|----------------|
| Mute toggle | VolumeChanged | volumeChange | mute/unmute beacon + volumeChange |
| Click (no ClickThrough) | n/a | pause/resume logic | pause/resume beacon + OMID pause/resume |
| Click (ClickThrough) | n/a | Open intent | (No pause/resume beacon unless user toggles) |
| Visibility <50% | state change | pause() only | OMID pause (no beacon) |
| Visibility restored | state change | resume() only | OMID resume (no beacon) |

---
## Public APIs (Current)

### CoreVideoPlayerView
```
fun load(videoUri: Uri, subtitleUri: Uri? = null)
fun play()
fun pause()
fun toggleMute()
fun seekTo(positionMs: Long)
fun release()
val state: StateFlow<PlaybackState>
val effects: SharedFlow<PlayerEffect>
```

### AdVideoPlayerView
```
fun initialize(vastUrl: String)
fun onMuteClicked(updateLabel: (String) -> Unit)
fun onAdViewClicked()
fun applyVisibility(isAtLeast50: Boolean)
fun isUserIntendedPlaying(): Boolean
fun release()
fun coreView(): CoreVideoPlayerView
```

### AdPlaybackController (internal usage via wrapper)
```
fun initialize()
fun onMuteClicked(onMuteLabel: (String)->Unit)
fun onPlayerClicked()
fun applyVisibility(isAtLeast50: Boolean)
fun isUserIntendedPlaying(): Boolean
fun release()
```

---
## Parity Checklist (Maintained)
- Impression ordering (before start) ✅
- Start threshold >1% ✅
- Quartile thresholds 25/50/75 ✅
- Single-flight quartiles ✅
- Loop playback continues silently ✅
- Unconditional bufferFinish on Ready ✅
- Mute/unmute + beacons + OMID volumeChange ✅
- Pause/resume beacons only on user toggles ✅
- Visibility-based OMID pause/resume without VAST pause/resume ✅
- Duration passed to mediaEvents.start still milliseconds (legacy quirk) ✅
- Subtitle language mismatch preserved ✅

---
## Suggested Immediate Cleanup (Low Risk)
If desired, the following can be safely applied without changing observable behavior:
1. Fix quartile emission duplication + condition typo.
2. Move `effects.collect` coroutine launch out of `load()` into `init {}` with a dedicated Job.
3. Explicitly remove `OnPreDrawListener` in `onDestroy` (activity) or convert to a `ViewTreeObserver.OnGlobalLayoutListener` + removal.
4. Add simple logging guard / toggle.

These are intentionally deferred awaiting explicit approval to modify runtime code.

---
## FAQ
**Why not let Core reset quartiles on loop?**  
Legacy behavior emitted quartiles only once. Changing this could alter analytics / tracking assumptions.

**Why milliseconds to OMID start()?**  
Parity over correctness for now; can be toggled later with a simple division by 1000f.

**Why still use polling instead of analytics callbacks?**  
Simplicity + precise parity with original Handler-based timeline.

**Why no interfaces for beacon/OMID abstractions?**  
Demo-first approach to keep code approachable for copy/paste scenarios.

---
## Appendix: Class-to-Responsibility Mapping
| Class | Core Media | VAST | OMID | Beacons | UI | Quartiles | Visibility | Reuse Scope |
|-------|------------|------|------|---------|----|-----------|------------|-------------|
| CoreVideoPlayerView | ✅ | ❌ | ❌ | ❌ | Base View only | Emits effects | ❌ (external) | High (generic) |
| Quartile (enum) | ✅ (math only) | ❌ | ❌ | ❌ | ❌ | Threshold logic | ❌ | High |
| AdPlaybackController | ❌ | ✅ | ✅ | ✅ | ❌ | Consumes effects | ✅ (logic only) | Medium (ad contexts) |
| AdVideoPlayerView | Composite | Indirect | Indirect | Indirect | ✅ (embed) | Indirect | Indirect | Medium |
| VideoAdNativeActivity | ❌ | ❌ | ❌ | ❌ | Layout + wiring | ❌ | ✅ (delegates) | Low |
| VastParser | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | Medium |
| AdSessionUtil | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | Medium |

---
**End of Architecture.md**

