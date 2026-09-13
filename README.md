# Syncop

Android metronome timing trainer. Syncop plays clicks at a chosen tempo while recording
your playing, then shows the recording on a scrolling timeline with each attack marked
by a colour: green exactly on the beat, shading through yellow and orange to red at
±50 ms or more.

## Features

- Tempo 1–300 BPM; drag the tempo strip for fast changes, tap − / + for single steps
- Record, stop, resume; scroll back and play any part; pinch to zoom the timeline
- Optional clicks during playback (settings)
- Attack detection ignores the app's own click, and speaker bleed of the click is used
  to auto-calibrate input latency; a manual offset is also available
- Recording persists across launches; erase to start over; export as WAV via share

## Download

Get the latest signed APK from the [releases page][releases]. On the phone, allow
installation from unknown sources, then open the downloaded file.

## Building

Requires JDK 17, Gradle and Android SDK 35. On NixOS everything is provided by the
dev shell:

```sh
NIXPKGS_ALLOW_UNFREE=1 nix-shell --run 'gradle testDebugUnitTest assembleDebug assembleRelease'
adb install app/build/outputs/apk/debug/app-debug.apk
```

The release APK is written to `app/build/outputs/apk/release/app-release-unsigned.apk`.
It is optimized but unsigned. To get a signed APK, set the `SYNCOP_*` variables
described in the [development guide][dev].

## Documentation

- [Architecture and design decisions][arch]
- [Development guide][dev] (build environment, tests, tuning knobs)
- [Device testing][devtest] (connecting phones, pulling raw recordings, measured latencies)
- Issues: `TASKS.md`

## How timing works

All times are frames at 48 kHz on the output clock. The metronome writes clicks into the
output stream at beat frames and records those frames. Microphone input is appended to the
session shifted by the latency offset (auto + manual). The auto offset is measured from
the speaker bleed of the click, saved on the device and applied from the next recording
on, also after erasing. Attacks are found from the RMS
envelope of the 100 Hz – 2.5 kHz band and scored against the nearest click; the click sits
at 4 kHz so it stays out of that band.

[releases]: https://github.com/akaihola/syncop/releases/latest
[arch]: docs/architecture.md
[dev]: docs/development.md
[devtest]: docs/device-testing.md
