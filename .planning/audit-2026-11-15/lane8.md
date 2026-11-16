# Lane 8 — Health and tracking (run directly, 2026-11-15)

## Checklist coverage

- Gradle suite: **clean**. 1582 tests, 0 failures, 3 skipped, 1m27s. Slowest classes: FlameProbeTest 15.1 s,
  AudioEngineTest 13.2 s, BeatPhaseLockTest 10.3 s, PlayQueueManagerTest 4.5 s. No compiler warnings in the log.
- Playwright (default grep, no @long/@radio): 53 passed, 37 failed, 70 skipped, 5.8 min. 35 failures are webkit
  launch failures (missing system libs on Fedora; `playwright install-deps` targets apt) — environment, not code.
  2 real chromium failures → L8-1.
- open-items.md / app-checklist.md / memory notes: done at merge time (see report).

### L8-1 [bug] Embed catalog has no backgrounds
- Where: tests/web/embed.spec.ts:32 and :129 (chromium); `LSD.catalog()` in web/embed.js
- Evidence: `cat.backgrounds.length` is 0 (expects > 1); gallery entries 0 (expects > 0). Test last touched 55b8099.
- Impact: the Android VJ app / Spaz Radio embed host sees no backgrounds to pick. Suspect the fast-start change
  (TV v35/v37, first scene no longer waits for all presets) makes catalog() answer before backgrounds load, or a test
  that needs a wait.
- Fix: find whether catalog() is called too early (test) or backgrounds are never added (code).
- Verified:

### L8-2 [polish] WebKit project cannot run on this machine
- Where: playwright.config.ts `webkit` project
- Evidence: 35 webkit tests fail at `browserType.launch` (libicu74 etc. missing); every run reports them as failures.
- Fix: skip the webkit project unless `WEBKIT=1`, or document it as run elsewhere, so real failures are not buried.
- Verified:
