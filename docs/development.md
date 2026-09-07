# Development guide

## Build environment

The project needs JDK 17, Gradle and Android SDK 35 with Build-Tools 35.0.0.
On NixOS all of these come from `shell.nix`:

```sh
NIXPKGS_ALLOW_UNFREE=1 nix-shell --run 'gradle testDebugUnitTest assembleDebug assembleRelease'
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it
with `adb install`. The optimized release APK is written to
`app/build/outputs/apk/release/app-release-unsigned.apk`. It is unsigned unless the
`SYNCOP_*` variables from the [Releases](#releases) section are set.

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

## Releases

The GitHub Actions workflow `.github/workflows/release.yml` builds a signed
release APK and attaches it to a GitHub Release when a tag that starts with `v`
is pushed. A manual run (`workflow_dispatch`) only uploads the APK as a workflow
artifact.

### One-time setup

1. Create the release keystore:

   ```sh
   keytool -genkeypair -v -keystore syncop-release.jks -alias syncop \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

   Keep the keystore file and its passwords safe and make a backup. If they
   are lost, installed apps cannot be updated with new releases. Never commit
   the keystore; `.gitignore` excludes `*.jks` and `*.keystore`.

2. Add these repository secrets on GitHub (Settings > Secrets and variables >
   Actions):

   | Secret | Value |
   | --- | --- |
   | `SYNCOP_KEYSTORE_BASE64` | Output of `base64 -w0 syncop-release.jks` |
   | `SYNCOP_KEYSTORE_PASSWORD` | Keystore password |
   | `SYNCOP_KEY_ALIAS` | `syncop` (or the alias you chose) |
   | `SYNCOP_KEY_PASSWORD` | Key password |

   The workflow fails with a clear message if the keystore secret is missing.
   It never publishes an unsigned APK.

### Making a release

1. Increase `versionCode` and set `versionName` in `app/build.gradle.kts`.
   Android refuses to install an APK whose `versionCode` is not higher than
   the installed one.
2. Commit, then tag and push:

   ```sh
   git tag v0.1.0
   git push origin v0.1.0
   ```

3. The workflow creates the release with `syncop-v0.1.0.apk` attached.

### Signed local builds

Set these environment variables before `gradle assembleRelease` to get
`app/build/outputs/apk/release/app-release.apk` signed with your key:
`SYNCOP_KEYSTORE_PATH`, `SYNCOP_KEYSTORE_PASSWORD`, `SYNCOP_KEY_ALIAS`,
`SYNCOP_KEY_PASSWORD`.

## Workflow

Issues live in `TASKS.md`. Follow the rules at the end of that file. Write
documentation and comments in Simplified Technical English for a junior
engineer who is new to the project.
