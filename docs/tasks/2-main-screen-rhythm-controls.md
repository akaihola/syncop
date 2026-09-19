# Main screen rhythm controls

## Problem

`Clicks: 1/1` was on the main screen, but `Measure claps: 1/1` was in Settings.

## Requirements and acceptance criteria

- Put both controls on the main screen.
- Use standard or well-established Android widgets for both controls.
- Use `1/4` as the default for both controls.
- Express the selected tempo in quarter notes per minute.
- At 120 BPM, `Clicks: 1/4` must produce 120 clicks per minute. This is
  2 clicks per second, or 500 ms per click.

The separate grid and click density issues in [TASKS.md](../../TASKS.md)
define the available values. This issue concerns control placement, defaults,
and tempo units.
