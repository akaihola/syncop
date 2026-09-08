---
name: zenfone8-test-device
description: "ASUS Zenfone 8 (ASUS_I006D, Android 13) test phone has dead USB data; reach it via wireless adb, and run adb outside the sandbox"
metadata: 
  node_type: memory
  type: project
  originSessionId: 64a2ce1b-5f3e-4730-8d39-0ef289e79a01
  modified: 2026-09-08T18:36:44.300Z
---

The user's second test phone is an ASUS Zenfone 8 (model ASUS_I006D, Android 13, patch 2023-11). Its USB-C port only charges: the laptop sees a Type-C sink partner but no USB device enumerates, and the phone shows no USB notification. As of 2026-09-08 it is reached with Android Wireless debugging at 192.168.1.13 (port changes per pairing).

**Why:** The Fairphone 4 works on the same cable and port, so the fault is the Zenfone's port, a known Zenfone 8 failure.

**How to apply:** Do not troubleshoot the cable again. Run adb with the sandbox disabled, because the sandboxed shell starts its own adb server and cannot see the wireless connection. Set ANDROID_SERIAL when a stale offline entry is also listed. Findings from the 2026-09-08 audio investigation are in [[zenfone8-calibration-findings]].
