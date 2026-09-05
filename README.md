# Syncop

Android metronome timing trainer. Syncop plays clicks at a chosen tempo while recording
your playing, then shows the recording on a scrolling timeline with each attack marked
by a colour: green exactly on the beat, shading through yellow and orange to red at
±50 ms or more.

## Features

- Tempo 40–240 BPM; drag the tempo strip for fast changes, tap − / + for single steps
- Record, stop, resume; scroll back and play any part; pinch to zoom the timeline
- Optional clicks during playback (settings)
- Attack detection ignores the app's own click, and speaker bleed of the click is used
  to auto-calibrate input latency; a manual offset is also available
- Recording persists across launches; erase to start over; export as WAV via share

## Building

Requires JDK 17, Gradle and Android SDK 35. On NixOS everything is provided by the
dev shell:

```sh
NIXPKGS_ALLOW_UNFREE=1 nix-shell --run 'gradle testDebugUnitTest assembleDebug'
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Documentation

- [Architecture and design decisions][arch]
- [Development guide][dev] (build environment, tests, tuning knobs)
- Issues: `TASKS.md`

## How timing works

All times are frames at 48 kHz on the output clock. The metronome writes clicks into the
output stream at beat frames and records those frames. Microphone input is appended to the
session shifted by the latency offset (auto + manual). Attacks are found from the RMS
envelope of the 100 Hz – 2.5 kHz band and scored against the nearest click; the click sits
at 4 kHz so it stays out of that band.

[arch]: docs/architecture.md
[dev]: docs/development.md
