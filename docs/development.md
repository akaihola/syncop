# Development guide

## Build environment

The project needs JDK 17, Gradle and Android SDK 35 with Build-Tools 35.0.0.
On NixOS all of these come from `shell.nix`:

```sh
NIXPKGS_ALLOW_UNFREE=1 nix-shell --run 'gradle testDebugUnitTest assembleDebug assembleRelease'
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it
with `adb install`. The optimized release APK is written to
`app/build/outputs/apk/release/app-release-unsigned.apk`. It is unsigned.

Notes:

- There is no Gradle wrapper. Use the Gradle from the shell.
- The SDK in the Nix store is read only. Gradle cannot install SDK components,
  so `buildToolsVersion` in `app/build.gradle.kts` must name a version that
  `shell.nix` provides. Both list 35.0.0; the shell also has 34.0.0 because the
  Android Gradle plugin asks for it by default.
- For coding agents in the sandbox: `nix-shell` needs the Nix daemon socket,
  which the sandbox blocks, so run it with the sandbox disabled and in the
  background. `$TMPDIR` is not set outside the sandbox; use explicit log paths.

## Tests

Unit tests are in `app/src/test/`. They run on the JVM and do not need a
device. They cover `OnsetDetector`, `Calibration`, `Session` and the colour
ramp. Audio engines and UI are not covered; test them on a device.

## Tuning knobs

| Parameter | Location | Default | Effect |
| --- | --- | --- | --- |
| `RED_AT_MS` | `model/BeatColor.kt` | 50 | Deviation at which a marker is fully red |
| `riseRatio` | `OnsetDetector` constructor | 3.0 | How much above the background an attack must be |
| `minLevel` | `OnsetDetector` constructor | 0.02 | Absolute RMS floor for attacks |
| `refractoryMs` | `OnsetDetector` constructor | 80 | Minimum gap between two attacks |
| Low pass corner and stage count | `OnsetDetector` | 2 kHz, 3 | Click rejection versus sensitivity |
| `FREQUENCY_HZ`, `DURATION_MS` | `ClickSynth` | 4000, 4 | Click sound. Keep it above the low pass corner |
| `searchMs` | `Calibration` constructor | 250 | How long after a click the bleed is searched |
| `PLAYHEAD_FRACTION` | `ui/Timeline.kt` | 0.72 | Horizontal position of the playhead line |

## Workflow

Issues live in `TASKS.md`. Follow the rules at the end of that file. Write
documentation and comments in Simplified Technical English for a junior
engineer who is new to the project.
