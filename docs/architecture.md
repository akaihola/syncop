# Architecture and design decisions

This document explains how Syncop works and why it is built this way. Read it
before you change the audio or timeline code.

## Requirements and decisions

These decisions were agreed with the product owner on 2026-09-05.

| Topic | Decision |
| --- | --- |
| Stack | Kotlin, Jetpack Compose, single Activity, one ViewModel |
| Minimum Android | API 31 (Android 12) |
| Package and name | `fi.kaihola.syncop`, "Syncop" |
| Tempo | 40 to 240 BPM, default 120 |
| Beat scoring | Only main beats. No subdivisions, no accents |
| Marker colour | Green at 0 ms. Red at 50 ms or more from the nearest click. Yellow and orange between |
| Timeline zoom | Pinch gesture, 1 to 30 seconds visible |
| Playback start | From the time under the fixed playhead line |
| Clicks in playback | User toggle in settings, default off |
| Click bleed | Detect the app's own click in the microphone signal and ignore it |
| Latency | Auto-calibrate from click bleed, plus a manual offset in settings |
| Persistence | Save the session in app storage. Restore on launch. Export as WAV |

## Modules

All code is in `app/src/main/java/fi/kaihola/syncop/`.

| File | Responsibility |
| --- | --- |
| `model/Session.kt` | Mono PCM16 buffer at 48 kHz, list of click frames, list of attacks (`Onset`) |
| `model/BeatColor.kt` | Deviation in ms to ARGB colour (green to red) |
| `audio/ClickSynth.kt` | The 4 kHz, 4 ms click sample. Also the template for calibration |
| `audio/Biquad.kt` | Second-order IIR filters (low pass, high pass, band pass) |
| `audio/OnsetDetector.kt` | Streaming attack detector |
| `audio/Calibration.kt` | Finds the click bleed after each click and estimates latency |
| `audio/RecordEngine.kt` | Runs `AudioTrack` (clicks) and `AudioRecord` (microphone) together |
| `audio/PlaybackEngine.kt` | Streams the session to `AudioTrack`, optionally mixes clicks |
| `SessionStore.kt` | Save and load the session, write WAV for export |
| `SyncopViewModel.kt` | Transport state, tempo, playhead, latency, calls to the engines |
| `ui/Timeline.kt` | Canvas: envelope, click ticks, attack markers, playhead, gestures |
| `ui/TempoControl.kt` | BPM number, minus and plus buttons, draggable ruler |
| `ui/SyncopApp.kt` | Screen layout, transport buttons, erase and settings dialogs |
| `MainActivity.kt` | Permission request, edge to edge, WAV share intent |

## Time model

All positions are **frames at 48 kHz on the output clock**. One second is
48 000 frames.

1. When recording starts, both audio streams start at the current session
   length. The first click comes one beat later. `AudioRecord` and `AudioTrack`
   do not start at the same instant, and the difference changes on each run.
2. The output thread writes 10 ms chunks. It counts frames as it writes. When
   the frame of the next beat falls inside a chunk, it mixes the click into the
   chunk at that offset and records the frame in `Session.clicks`.
3. The beat interval is `60 * 48000 / tempo` frames. The tempo is read at each
   beat, so a tempo change applies from the next beat.
4. The input thread holds the microphone frames until both streams report an
   `AudioTimestamp`, or for at most 500 ms. From the two timestamps it computes
   how many input frames were captured before the first output frame was presented
   (`alignmentSkipFrames` in `audio/Calibration.kt`). It drops that many frames
   plus `latencyFrames`. This shifts the input so that a sound heard exactly on
   a click lands on the click frame. If no timestamp arrives, only
   `latencyFrames` are dropped, as before.
5. The list of clicks is the source of truth for scoring. Each attack gets
   `deviationMs` = distance to the nearest click. This is why tempo changes and
   resumed recordings do not break the scoring.

`latencyFrames` = (auto estimate + manual offset) in ms, converted to frames,
never negative. The auto estimate is the residual after the timestamp
alignment: mostly the acoustic path from speaker to microphone plus any delay
the HAL does not report.

Assumptions of the alignment (sources: the `AudioTimestamp`, `AudioTrack` and
`AudioRecord` reference documentation in AOSP, and `FullDuplexStream` in Oboe):

- Both timestamps use the `System.nanoTime` clock (`TIMEBASE_MONOTONIC`), so
  frames of the two streams can be placed on one time line.
- The input timestamp is the capture time at the earliest point of the input
  pipeline. The output timestamp is the time the frame was, or is committed to
  be, presented. Hardware delay unknown to the HAL is not included.
- Output timestamps can be missing or wrong while the audio clock stabilises
  after start. The code waits until the reported output position advances.
  Oboe's full-duplex helper discards the first input callbacks for the same
  reason.
- The audio clock and `System.nanoTime` can drift apart, so the timestamps are
  read once at start only. The Android reference asks for sparse polling.
- The skip is never negative. Input that started late is not padded.

## Attack detection

`OnsetDetector` reads the input stream in order and reports attack frames.

- The signal goes through a 100 Hz high pass and three cascaded 2 kHz low
  passes. This gives about 36 dB attenuation at the 4 kHz click.
- The RMS is computed for every 5 ms hop (240 frames).
- An attack fires when the hop RMS is above 1.5 times the previous hop, above 3
  times the slow background level, and above the absolute floor 0.02 (about
  -34 dBFS).
- After an attack, 80 ms are ignored (refractory period).
- The caller can mask a frame range with `maskUntil`. `RecordEngine` masks the
  frames where the calibration found the click bleed.

Why the floor is 0.02: a simulation of the click through the filter chain gave a
peak hop RMS of 0.0125 at digital gain 0.5, while a moderate 440 Hz hit gave
0.38. The click onset is broadband, so filtering alone cannot remove it. The
cost is that attacks softer than about -34 dBFS are not marked.

Implementation note: the "last onset" frame must not start at `Long.MIN_VALUE`.
Subtracting it overflows and no attack ever fires. It starts at `-1L shl 40`.

## Latency calibration

After each click, `RecordEngine` collects input from 20 ms before the click
frame to 250 ms after it and gives it to `Calibration.analyse`.

- The window is band passed at 4 kHz (Q = 8). The 4 ms segment with the highest
  mean amplitude is the candidate.
- The lag is measured from the click frame. It can be negative when the applied
  latency shift overshoots. This is why the window starts 20 ms early.
- The stored value is the applied latency minus the manual offset, plus the
  lag, so the estimate is a total. Without this, each run measured only the
  remaining lag and the estimate swung between the full latency and zero on
  alternate runs.
- `RecordEngine.start` resets the calibration, the pending click queue and the
  window. Old lags and clicks from a previous run (or from before an erase)
  cannot leak into the new estimate.
- The candidate counts as click bleed only if its mean is at least 6 times the
  window mean and above 0.002. Headphones give no bleed and no estimate.
- The estimate is the median of the last 32 lags, available after 3 lags.
- The filter adds about 6 frames of delay to the lag (0.1 ms). Tests allow 10
  frames.

## Timeline drawing

- The playhead line is fixed at 72 % of the width. Content moves under it.
- The envelope is one column per pixel: the peak absolute sample in that
  column's frame range, drawn mirrored around a midline.
- Click ticks are small triangles in a lane above the waveform with a faint
  vertical guide.
- Each attack is a filled circle just above the waveform peak near the attack,
  coloured by `deviationColor`.
- `revision` in the ViewModel increments on every session change. The canvas
  reads it so Compose redraws.

## Persistence and export

`SessionStore` writes `session.pcm` (raw little endian PCM16) and
`session.json` (clicks, onsets, tempo, manual latency) in `filesDir` when
recording stops. Erase deletes both. Export writes a 44 byte WAV header plus the
PCM into `cacheDir/export/` and shares it through `FileProvider` with authority
`fi.kaihola.syncop.files`.

## Verification status (2026-09-05)

- The JVM unit tests pass: attack detection within 10 ms, click bleed rejection,
  calibration lag, silence handling, nearest click deviation, colour ramp.
- The debug APK builds. No device test was done by the agent. The product owner
  tested on a device on 2026-09-06 and listed problems in `TASKS.md`.
- 2026-09-06: The click bleed offset fix (timestamp alignment, cumulative
  estimate, state reset) is covered by JVM tests only. The timestamp alignment
  needs a device test.

## Known problems and probable causes

These notes are for whoever works on the `TASKS.md` backlog.

- **Drag moves only a few pixels.** In `ui/Timeline.kt` the `pointerInput`
  lambda captures `playhead` and `secondsVisible` once. Each pan event computes
  the new position from the stale value, so the view returns to almost the same
  place. Use `rememberUpdatedState` or accumulate the pan inside the gesture.
- **Pinch zooms one step only.** Same cause: `secondsVisible` in the closure is
  stale, so every zoom event starts from the old value.
- **Recorded waveform looks weak next to the click bleed.** The click template
  peaks at 0.8 of full scale and the display is linear. Options: draw the
  envelope on a dB or square root scale, or normalise to the recent peak.
- **No colour coding on the waveform peaks.** Markers are drawn only where
  `OnsetDetector` fired. If a hit is below the 0.02 floor or inside the 80 ms
  refractory period, it gets no marker. Check the floor first.
- **Landscape.** The activity is locked to portrait in `AndroidManifest.xml`.
