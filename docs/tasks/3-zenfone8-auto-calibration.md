# Zenfone 8 auto-calibration

## Problem and cause

Auto-calibration on the Zenfone 8 varied between runs. Click bleed arrived before
or after the beat. The code clamped a negative timestamp alignment to zero.

## Recorded fix and expected behavior

- Pad the input with silence to handle negative timestamp alignment.
- Preserve the auto-calibration estimate across Erase and app restart.
- Apply the calibration shift from the first run after calibration.

Commits `e25fe4d` and `9978bfc` contain the alignment and persistence changes.
See the [device findings](../agent-memory/zenfone8-calibration-findings.md)
for the investigation and verification record.

The [stable click timing task](4-stable-click-timing.md) concerns the broader
variation between recording runs. This task records the specific Zenfone 8
alignment fault and calibration persistence requirements.
