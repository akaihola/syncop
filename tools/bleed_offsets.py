#!/usr/bin/env python3
"""Measure where the click bleed sits relative to each click in a pulled session.

Usage: uv run --with numpy --with scipy tools/bleed_offsets.py session.pcm session.json

The PCM is the aligned input as SynCop stores it (16-bit mono, 48 kHz). The JSON
is the session metadata with the click frames. For each click the script finds
the 4 ms segment with the most 4 kHz energy between 150 ms before and 250 ms
after the click and prints its offset in ms. Offsets must agree within a run.
"""

import json
import sys
from pathlib import Path

import numpy as np
from scipy.signal import iirpeak, lfilter

SAMPLE_RATE = 48000
CLICK_HZ = 4000.0
CLICK_FRAMES = 192  # 4 ms


def main(pcm_path: str, json_path: str) -> None:
    pcm = np.frombuffer(Path(pcm_path).read_bytes(), dtype="<i2").astype(float) / 32768
    clicks = json.loads(Path(json_path).read_text())["clicks"]
    b, a = iirpeak(CLICK_HZ / (SAMPLE_RATE / 2), 8)
    envelope = np.abs(lfilter(b, a, pcm))
    energy = np.convolve(envelope, np.ones(CLICK_FRAMES), "valid")
    offsets = []
    for click in clicks:
        lo = click - int(0.150 * SAMPLE_RATE)
        hi = click + int(0.250 * SAMPLE_RATE)
        if lo < 0 or hi > len(energy):
            continue
        peak = lo + int(np.argmax(energy[lo:hi]))
        offsets.append((peak - click) * 1000 / SAMPLE_RATE)
    print("offset ms per click:", [round(o, 1) for o in offsets])
    if offsets:
        print(f"median {np.median(offsets):.1f} ms, spread {max(offsets) - min(offsets):.1f} ms")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
