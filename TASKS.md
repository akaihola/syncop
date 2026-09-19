# Issue tracking for SynCop

Rules for TASKS.md usage are at the bottom of the file.

## Unverified proposals

## Ordered backlog

## Scheduled

## In Progress

## Completed

- [1] Make the tempo dial move in the same direction as the drag.

- [*] Let the user select the grid to measure recorded claps against between 1/1, 1/2,
  1/4, 1/8, 1/16, and 1/32. In other words, claps will be judged against the nearest
  grid line.

- [*] Let the user select the click density between 1/1, 1/2, 1/4, 1/8, and 1/16.

- [*] Dragging the BPM tempo indicator doesn't work correctly. The BPM value jumps
  randomly by small amounts when dragging.

- [2] Put click and clap measurement controls on the main screen, with quarter-note
  defaults and tempo in quarter notes per minute.

- [*] The colored accuracy dots wobble up and down when scrolling.

- [*] Stretch the allowed range of the BPM tempo indicator to 1–300 BPM.

- [3] Correct negative timestamp alignment on the Zenfone 8 and preserve the
  auto-calibration estimate across Erase and restart.
- [*] Make the APK available for download from the repository.
- [*] Always compile both a debug version and an optimized release version.
- [*] Navigating the recording by dragging doesn't work. The timeline only moves a few
  pixels no matter how much I drag.
- [*] App must work in horizontal orientation, too.
- [4] Keep leaked click offsets stable across recording runs, including after Erase;
  research Android audio timing accuracy.

- [*] There is no accuracy color coding for peaks in the waveform.

- [*] Pinch zooming only zooms one small step at a time, not continuously as I continue
  to pinch more.

## Accepted

[1]: docs/tasks/1-tempo-dial-direction.md
[2]: docs/tasks/2-main-screen-rhythm-controls.md
[3]: docs/tasks/3-zenfone8-auto-calibration.md
[4]: docs/tasks/4-stable-click-timing.md
[*]: TASKS.md

---

## Rules

Here are the rules for TASKS.md usage:

### Invariant: one heading per issue

At any time, each issue's bullet must be under exactly one `##` heading (for example
`## Ordered backlog` or `## In progress`). It must never be under two headings at the
same time.

- Before you commit any change to TASKS.md, run:
  `python3 tools/check_tasks_md.py TASKS.md`
- The check must pass (exit code 0) before the commit.
- If the check fails, it prints the duplicated bullet. Remove the bullet from all but
  one heading before you commit.

### TASKS.md maintenance sessions

- Each issue bullet in every section must be prefixed with either
    - a numbered reference-style link (e.g. `[1]`) to a description file, or
    - `[*]` to indicate no description file is needed for a simple task.
- Link references are listed between `## Completed` and `## Rules`.
- If any issue is missing a link:
    - Create the first missing numbered description file in
      docs/tasks/<N-issue-description>.md and add the link
- Any completed tasks which haven't yet been moved from `## In Progress` to
  `## Completed` should be moved there.
- Any in progress tasks which haven't yet been moved from `## Ordered backlog` or
  `## Scheduled` to `## In Progress` should be moved there.
- Remove all issues the user has moved to the `## Accepted` section along with any
  related description files in `docs/tasks/` and the reference-style links pointing to
  them.
- Ensure there are no duplicate sections, and that they are in the correct order:
  `## Unverified proposals` -> `## Ordered backlog` -> `## Scheduled` ->
  `## In Progress` -> `## Completed` -> `## Accepted` -> `## Rules`.
- If there are numbered issues in `TASKS.md` without a reference-style link or a file
  pointed to by a reference-style link, create both. Keep just enough of the description
  in `TASKS.md` and move details to the issue description file.
- Commit all changes.

### Modifying issues

- Ensure dependencies between issues are correctly updated.
- State dependencies using
    - indented `- Depends on: [N]` bullets in TASKS.md, and
    - YAML frontmatter in description files.
- Ensure backlog order respects dependencies.
- When you move an issue to a different section, move its lines without a change. Keep
  the prefix, the bullet text and the line wrapping the same. Git can then see the move,
  and concurrent moves do not cause a conflict.

### Workflow for new issue completion

1. Choose issue and schedule work (typically by a heartbeat)

- Pick the first backlog issue with no dependency to any uncompleted issue.
- Move it under `## Scheduled` in `TASKS.md` and remove it from `## Ordered backlog` in
  the `main` branch and commit.

2. Work on the issue (typically by a task workflow)

- Rebase the worktree feature branch on `main` before moving the issue, and keep it
  rebased afterwards.
- Move the issue under `## In progress` in `TASKS.md` in the worktree branch, ensure
  it's not in `## Ordered backlog`, and commit.
- Create or update, review and refine a plan in docs/tasks/<N-issue-description>.md in
  `main` if more description is needed than nicely fits in a bullet point. If you
  created a plan document, link to it using a new `[N]` reference-style link.
- Commit the description file (if any) in `main`.
- Implement the plan, and lint, test, review and refine the implementation in the
  worktree feature branch.

3. Merge and deploy (typically by last steps of a task workflow)

- Merge the rebased branch on `main`, and remove the worktree and branch.
- Move the issue from `## In progress` to `## Completed` in TASKS.md and commit.
- Do any deployment steps if defined in the general development worklow.

When TASKS.md conflicts during a rebase or merge:

- Use `main`'s version of every section as the base.
- Apply again only the move of your own issue.
- Never restore, add again or re-word another issue's bullet from your side of the
  conflict.
- After resolving the conflict, read the whole file and ensure that each bullet is under
  exactly one `##` heading.
