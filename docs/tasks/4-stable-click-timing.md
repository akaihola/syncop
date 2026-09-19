# Stable click timing

## Problem

Leaked clicks appeared at a different offset from the marked beat on each run.
Erasing the recording between runs did not prevent the variation.

## Requirements

- Keep the offset between leaked clicks and the marked beat stable between runs,
  including when the user erases the recording between runs.
- Do extensive online research into audio timing accuracy in Android apps.

See the [audio architecture](../architecture.md) for timestamp alignment and
calibration details. The [Zenfone 8 task](3-zenfone8-auto-calibration.md) records
a separate negative alignment fault and calibration persistence requirements.
