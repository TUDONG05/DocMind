---
title: System Settings
status: completed
priority: P1
effort: medium
branch: feature/system-settings
tags: [settings, android]
created: 2026-11-21
---

# System Settings Implementation Plan

> For agentic workers: Use superpowers:executing-plans task-by-task.

Goal: implement user-approved docs/system-settings-design.md on feature/system-settings.
Architecture: Kotlin XML/Material 3, SharedPreferences via small testable storage interface, SettingsAwareActivity for local night mode and system-relative font scaling.
Tech: existing AppCompat and JUnit 4; no new dependencies. Final catalog uses AGP 9.5.1 after concurrent user environment correction; see scope log.

## Constraints
- Preserve pre-existing edits. No commit/push requested.
- Defaults SYSTEM/DEFAULT. Themes SYSTEM/LIGHT/DARK; sizes DEFAULT/LARGE/EXTRA_LARGE, scales 1/1.15/1.30.
- Vietnamese UI; reset only settings; no cloud/login/AI changes.

## Review focus
Invalid stored values fall back safely; saved choices survive repository recreation; reset preserves unrelated data; font scale must not compound; stale Activities refresh without loops; large text remains scrollable.

## Task 1: persistence
Interfaces in settings/: AppSettings(theme:ThemeMode=SYSTEM,textSize:TextSize=DEFAULT); SettingsStorage.read(key):String?, write(values:Map<String,String>); SettingsRepository(storage).load/save/reset; keys theme_mode/text_size. SharedPreferencesSettingsStorage(context) uses applicationContext preferences docmind_settings and one editor per write.
- [x] Write SettingsRepositoryTest for defaults, corrupt values, all choices, reload, reset isolation.
- [x] Run focused unit test before implementation (missing feature expected), implement domain/storage, rerun green.

## Task 2: UI
SettingsAwareActivity extends AppCompatActivity, exposes protected settingsRepository; wraps context font scale relative to unmodified base configuration; sets delegate.localNightMode before super.onCreate; compares creation snapshot in onResume and recreates stale Activities.
SettingsActivity reads current settings, uses Material single-choice dialogs for theme/size and confirmation for reset; persists and recreates after changes. Toolbar back, version from installed package. Scrollable XML rows with 48dp+ touch areas and Vietnamese resources. MainActivity opens internal SettingsActivity.
- [x] Add settings/AppSettings.kt, SettingsRepository.kt, SharedPreferencesSettingsStorage.kt, SettingsAwareActivity.kt, SettingsActivity.kt.
- [x] Modify MainActivity, activity_main.xml, strings.xml, AndroidManifest.xml; add activity_settings.xml and needed drawables.

## Task 3: verify
- [x] Run :app:testDebugUnitTest, :app:assembleDebug, :app:lintDebug sequentially.
- [x] Independent code review for lifecycle, persistence, accessibility, resources and accepted scope.
- [x] If emulator available, test theme/size, reopen, reset cancel/confirm, restart persistence.
- [x] Update this plan and docs with evidence and limitations.

User approved design with 'ok'; proceed through reversible local implementation and verification without another approval request.

## Completion evidence — 2026-11-21

8/8 checklist items complete. Task 1: focused missing-feature RED observed before production (compileDebugUnitTestKotlin unresolved settings symbols); 11 focused tests subsequently passed.
Final AGP 9.5.1: full testDebugUnitTest 12 passed, 0 failed/skipped; lintDebug passed; deployment assembled and launched successfully. Earlier assembleDebug also passed.
Independent review: no blocker; fake-storage tests do not prove Android process persistence.
Emulator closes that gap: Sáng/Rất lớn survived force-stop/relaunch; main screen refreshed; large text scrollable; Back canceled reset; confirmation restored SYSTEM/DEFAULT; installed version 1.0 shown.
Reset negative button reviewed, not separately runtime-confirmed. TalkBack/keyboard, system font 200%, rotation and device theme-change matrix not exercised; these are validation limits, not completed device scenarios.
Git audit: branch merge-base develop 927691811044f22e3c556d1bd9ef65d3489ade88; diff --check clean. No staging/commit/push.

## Scope and risk log

- Scope unchanged: local theme, text size, installed version, safe reset; no added dependency.
- Environment exception: updated Studio rejected develop AGP 9.5.0-alpha07. Latest catalog already changed concurrently by user to 9.5.1; respected latest buffer. Sync and final verification passed on 9.5.1.
- Initial app/build.gradle.kts testOptions and gradle.properties edits preserved; no rollback of local work.
- Resolved: Studio/AGP sync blocker, missing implementation, pending review/build/device gates.
- Remaining risk: untested accessibility/extreme-system-font/orientation matrix above. Owner: future QA pass; done when those device scenarios have recorded outcomes.
- No unresolved implementation blocker or user decision.

Feature guide: [system-settings.md](system-settings.md). Execution ledger: [system-settings-progress.md](system-settings-progress.md).
