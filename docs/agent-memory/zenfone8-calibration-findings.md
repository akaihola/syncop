---
name: zenfone8-calibration-findings
description: "2026-09-08 device investigation of unstable auto-calibration on the Zenfone 8; audio path is clean, the calibration is polluted by the player's own claps"
metadata: 
  node_type: memory
  type: project
  originSessionId: 64a2ce1b-5f3e-4730-8d39-0ef289e79a01
  modified: 2026-09-08T18:59:34.223Z
---

On 2026-09-08 the Zenfone 8 ([[zenfone8-test-device]]) was probed with dumpsys while the app recorded. Facts not visible in the repo:

- UNPROCESSED source is honoured, no input effects, input timestamps jitter under 1 ms, no overruns. Input HAL buffer 20 ms (not fast capture).
- Output uses the FAST primary path, 4 ms mix period. AudioFlinger reports 117 to 139 ms track latency, varying per run. HAL start latency after standby 107 to 332 ms.
- Raw PCM pulled from a debug build (run-as) over six Erase+Record cycles, phone lying still, applied latency 0: the click burst sat at +30, -28, -17, -41, -33, +30 ms relative to the click. Within a run it is constant to 0.1 ms, so no jitter or drift. The whole error is the one-shot start alignment from the first AudioTrack timestamp.
- Bursts more than 20 ms ahead fall outside the Calibration search window, so those runs report "no click bleed detected". The Settings value keeps its old number unless Erase was pressed, which hid this at first.
- Click bleed at the mic peaks about -25 dBFS. The clap-pollution theory from earlier that day was wrong for the user's symptom; keep it only as a secondary robustness concern.
- The share sheet route to get a WAV off the phone is unsafe with adb taps: one attempt uploaded a file to the user's Dropbox.

**Why:** Instrumented runs showed the timestamps are accurate and stable. When the output HAL is still warm, output starts instantly and the input starts about 80 to 90 ms later. alignmentSkipFrames then computes a negative value and clamps it to 0 ("input that started late is not padded"), so the input is misaligned by exactly that amount. Predicted offsets from the timestamps matched the measured burst positions within 0.5 ms. When the output is cold (150 ms start latency) the input starts first, the positive skip is applied, and the run is correct.

**How to apply:** Fixed in commit e25fe4d (pad silence for negative alignment). Verified 2026-09-08 on the Zenfone over six Erase+Record cycles with mixed pauses: burst at 30.2 to 30.4 ms in every run. The Zenfone's true residual is about 30 ms. The Zenfone still runs the debug build; the signed release must be reinstalled from GitHub. The auto estimate is also persisted in shared preferences and survives Erase (commit after e25fe4d), verified: bursts at 0.0 to 0.4 ms after Erase and after an app restart. Do not chase the bleed detector for this bug.
