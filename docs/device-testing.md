# Device testing

This document tells you how to test timing on a real phone and what we
learned on 2026-09-08 from the ASUS Zenfone 8. Read `architecture.md` first.

## Connect the phone

- USB is the normal way. If `lsusb` shows no new device and the phone shows
  no USB notification, the data lines of the cable or the phone port are dead.
  The Zenfone 8 used for testing charges over USB but never enumerates. Use
  Wireless debugging on it (Developer options), then:

  ```
  adb pair <ip>:<pair-port>
  adb connect <ip>:<port>
  ```

  The port changes each time Wireless debugging is switched on.
- Coding agents in the sandbox: the sandbox starts its own `adb` server and
  cannot see the user's connection. Run `adb` with the sandbox disabled.
- When `adb devices` lists a stale `offline` entry as well, set
  `ANDROID_SERIAL` to the live one.

## Drive the app without touching it

`adb shell input tap X Y` presses buttons. Get the coordinates from
`adb shell uiautomator dump /sdcard/ui.xml` and read the `bounds` of the
element whose `content-desc` is `Record`, `Settings`, `Erase` and so on.
Coordinates on the Zenfone 8 (1080 x 2400): Record (556, 2130), Settings
(147, 2125), Erase (933, 2125), Erase dialog confirm (833, 1358), Settings
Done (834, 1578).

Do not use the Export WAV share sheet from `adb` taps. The targets are the
user's cloud accounts, and one test uploaded a recording to the user's
Dropbox by mistake. Pull the raw data instead, as shown below.

## Pull the raw recording

Only a debug build allows this. Installing it replaces the release build
because the signatures differ, so ask first.

```
NIXPKGS_ALLOW_UNFREE=1 nix-shell --run 'gradle assembleDebug'
adb uninstall fi.kaihola.syncop
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant fi.kaihola.syncop android.permission.RECORD_AUDIO
```

After each recording stops, the session is on disk:

```
adb exec-out run-as fi.kaihola.syncop cat files/session.pcm > run1.pcm
adb shell run-as fi.kaihola.syncop cat files/session.json > run1.json
```

`session.pcm` is the aligned input (16-bit mono, 48 kHz). `session.json`
holds the click frames. Measure where the click bleed sits relative to each
click with `tools/bleed_offsets.py`:

```
uv run --with numpy --with scipy tools/bleed_offsets.py run1.pcm run1.json
```

The offsets must be the same for every click in a run (within 0.5 ms) and
near 0 ms once the auto latency has been applied. A value that is constant
within a run but different between runs points at the start alignment.

## Check the audio path

`adb shell dumpsys media.audio_flinger` while the app records shows:

- The output thread the click track uses, its `Timestamp stats` and the
  `Start latency ms` of the HAL stream.
- The `FastMixer` block. `underruns` for the click track means the output
  thread of the app did not keep up.
- The `Input thread` block: `Audio source: 9 (AUDIO_SOURCE_UNPROCESSED)`
  confirms the requested source was honoured, and its `Timestamp stats` show
  the input timestamp jitter.

`adb shell dumpsys media.audio_policy` lists the mix ports and their flags.
`/vendor/etc/audio_effects.xml` shows which audio sources get vendor
pre-processing.

## Results per device

| Device | Residual after alignment | Notes |
| --- | --- | --- |
| Fairphone 4 (Android 13) | 7 to 11 ms | Works without special care |
| ASUS Zenfone 8 (Android 13) | 30 ms | Warm output starts about 85 ms before the microphone |

Zenfone 8 facts from the 2026-09-08 investigation:

- Input: `UNPROCESSED` honoured, no effects, HAL buffer 20 ms, timestamp
  jitter under 1 ms, no overruns.
- Output: FAST primary path, 4 ms mix period, track latency 117 to 139 ms,
  HAL start latency 107 to 332 ms after standby. The output goes to standby
  3 s after a recording stops.
- Both timestamps are accurate. The bug was in the app: see the alignment
  notes in `architecture.md`.

## Lessons

- Check whether the app really applied a change. A constant number in
  Settings can be a stale value: the auto estimate only updates when a bleed
  is found. Erase before each test run, or better, measure the PCM.
- Simulations of one theory (for example claps polluting the calibration)
  are not evidence. The raw recording settled the question in minutes.
- Check the build log for `BUILD FAILED` before installing. A failed build
  leaves the old APK in place and `adb install` reports success.
