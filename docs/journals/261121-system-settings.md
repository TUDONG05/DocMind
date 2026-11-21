# System settings and the cost of build configuration drift

**Date**: 2026-11-21 16:30
**Severity**: Low
**Component**: System settings / Android build configuration
**Status**: Resolved — final AGP 9.5.1 unit tests, lint, sync and deployment passed

## Context

The user approved system settings on `feature/system-settings`, created from
`develop` at `9276918`. Scope: theme, text size, application version, persistent
preferences, and a confirmed reset. Login integration was outside this change.

## What Happened

The feature was implemented without adding a library. Initial verification passed:
11 focused tests, 12 tests in the full unit suite, debug assembly, and lint.
Device checks confirmed that Sáng / Rất lớn survived a force-stop and cold restart.
The main screen reapplied the font setting after returning from settings.
Dismissing reset with Back retained preferences; confirming reset restored defaults.

The updated Studio conflicted with the old `develop` AGP `9.5.0-alpha07`
configuration. The user's concurrent AGP `9.5.1` edit was preserved; IDE sync and
deployment succeeded. Final checks on that configuration also passed:
12 full-suite tests, zero failures/skips, and lintDebug. Final deployment assembled
and launched successfully; these results are distinct from the initial checks.

## The Brutal Truth

Build configuration drift consumed attention that should have gone to the feature.
A passing run on one configuration cannot prove a later edited configuration works.
Claiming completion from the earlier green checks would give the next developer
false confidence. That avoidable uncertainty is the frustrating part here.

## Root Cause and Decisions

The integration problem came from an older branch configuration meeting the
updated IDE, while the user was editing the same build settings. Treating the
branch baseline as automatically correct would overwrite an intentional user fix.

Keep theme values `SYSTEM`, `LIGHT`, and `DARK`, and text multipliers `1`, `1.15`,
and `1.3`. Keep reset limited to application settings and require confirmation.
Use the existing app architecture rather than importing the login branch or adding
a dependency for this small preference surface. Preserve AGP `9.5.1` and verify it
instead of reverting the user's configuration to obtain an earlier result.

## Lessons Learned

Record the exact configuration behind a green build. Persistence and safe reset
need real lifecycle checks: successful rendering alone proves neither behavior.
Keep concurrent user edits visible and distinguish initial evidence from final checks.

## Next Steps

Final AGP `9.5.1` verification completed and recorded in `docs/system-settings.md`.
Canonical implementation plan reconciled: 8/8 items complete; independent review
found no blocker. Optional QA matrix still excludes TalkBack/keyboard, system font
200%, rotation and system-theme transitions. Back cancellation was exercised;
the reset dialog negative button was reviewed but not reliably runtime-confirmed.
No commit or push has been requested or performed for this feature.
