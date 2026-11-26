# Audit Blueprint

How we run a full audit of Liquid LSD. Run one about every two weeks, and always before a release.

## Goal

After an audit:

1. Every claim in the docs, the release notes, the tooltips and the UI text matches what the code does.
2. Every rule in `DECISIONS.md` and `ARCHITECTURE.md` holds in the code, or the rule has been changed on purpose.
3. Changes made since the last audit carry no known correctness bug: threading, real-time safety, undo, persistence.
4. The web TV, the licensing record and the test suite are in the same state as the desktop app.
5. Each audit is cheaper than the last. Any kind of finding seen in two audits becomes an automatic check.

An audit does not cover new features, redesigns or taste. Those findings go to `ROADMAP.md` or `.planning/open-items.md`.

## Baseline

Every audit diffs against the previous one. When an audit closes, tag the commit it finished on as `audit-YYYY-MM-DD`.
The next audit starts from `git describe --tags --match 'audit-*' --abbrev=0`.

| Audit | Baseline tag | Notes |
|---|---|---|
| 2026-10-07 | none | first roundup |
| 2026-10-29 | `audit-2026-10-29` → `fa4335e` | tagged afterwards; report is at `git show 4fa7646^:.planning/audit-2026-10-29.md` |
| 2026-11-15 | `audit-2026-11-15` | first audit run from this blueprint |

## Lanes

All eight lanes run in every audit for now. Each lane has a fixed checklist. A lane reviewer works through the whole
checklist and records the items that came up clean too, so we can tell "checked and clean" apart from "not looked at".

### 1. Correctness of changes since the baseline

Read every changed file under `src/main/kotlin`, in full where the change touches control flow. Check:

- **Thread 0 (GL/UI):** no file I/O, network, `Thread.sleep`, blocking joins or MIDI port open/close. File work goes
  to `presetIoExecutor` or a named thread.
- **Audio callback:** no allocation, no locks, no logging, no boxing in the JACK/PipeWire process path.
- **Per-frame work:** no allocation in the frame loop or in per-knob code (lists, lambdas, regex, string building, `ImString`).
- **Cross-thread fields:** anything written off Thread 0 and read on it (or the other way round) is `@Volatile`, atomic
  or handed over through a queue.
- **Ops paths:** deck, FX, transition and queue changes go through `DeckOps` / `FxOps` / `TransitionOps` / `QueueOps`,
  with undo where `DECISIONS.md` says there is undo.
- **Precedence:** the macro, Metaknob, Super Knob and MIDI precedence agrees with `MacroEngine` everywhere it is re-implemented.
- **Persistence:** writes are atomic (temp file + move), unknown JSON fields survive a round-trip, and versioned files bump their version.
- **Lifecycle:** native resources (GL objects, PipeWire/Syphon/Spout handles, MIDI ports, threads) are released on every exit path, including shutdown hooks.
- **Error paths:** failures reach the user (toast or log), not just `printStackTrace`. No swallowed `catch (e: Exception) {}`.

### 2. Architecture rules

- Go through `DECISIONS.md` and `ARCHITECTURE.md` claim by claim and check each against the code. Report each one as
  true, false, or stale (the claim was true once and has since been overtaken).
- Check the file counts, the thread table, the frame-loop steps, the persistence layout and the subsystem list in `ARCHITECTURE.md`.
- Re-check each item on the `ARCHITECTURE.md` "Unverified" list and either verify it or drop it.
- Look for new layering violations, and check the allow-list in `LayerDependencyTest`.
- If a rule can be checked mechanically, propose a test for it.

### 3. Developer docs

- Read every `docs/developer/*.md` page end to end against the code, not only the parts touched since the baseline.
- Check class, function and file names, defaults and status lines (planned / done / v1.1).
- Look for `file:///` or absolute links, and for links to files that no longer exist.
- Pages in `docs/archive/` must not be linked as if they were current.

### 4. User docs and release notes

- Read `docs/getting_started.md`, `docs/user_guide/*.md`, `docs/index.md` and `README.md` against the current UI.
- **Release notes:** `RELEASE_NOTES.md` and `docs/release_notes.md` must be identical in content. Every user-visible
  commit since the baseline needs an entry.
- **mkdocs:** every page under `docs/` is in `mkdocs.yml` nav or is archived on purpose. No unused images.
- **Bundled HTML:** `src/main/resources/docs/` must be rebuilt from the current Markdown.
- **Tours:** run `scripts/tour_staleness.py` and list the stale tours. Re-rendering them is a separate decision.

### 5. Tooltips and UI text

- Every interactive widget added or changed since the baseline has a tooltip.
- Tooltips go through `ui/TooltipHelper.kt` (`itemTooltip`, `controlTooltip`, `showTooltip`, `showCustomTooltip`),
  with no raw `ImGui.setTooltip`. Composite widgets wrap the tooltip target in `beginGroup`/`endGroup`.
- Vocabulary follows the glossary in `docs/user_guide/your_workspace.md` and the banned terms below.
- Menu items, OSC names and command names use the same wording for the same action.
- Tooltip text matches what the control does: its range, units and default.

Banned terms in UI text (grow this list):

| Banned | Use |
|---|---|
| play queue, PlayQueue | A/B Queue |
| Properties (meaning modulation) | Modulation |
| Alpha (fader) | Level |
| blend mode (transitions) | removed; do not mention |
| Library of Presets | Library |
| Flux (audio) | Hits |
| background queue | BG Queue |
| Trans queue | Transition Queue |
| Deep Edit | Edit |
| Bypass (on a modulator) | Mute (Bypass is for FX and shaders) |

Menu items get a tooltip only when the label alone does not say what will happen.

### 6. Web TV

Covers `web/`, `server/`, `scripts/publish_web_tv.sh` and `tests/web/`. The checklist is its own page,
[Web TV Audit](audit_web_tv.md) (lanes W1 to W11), because the TV is also audited on its own between full audits.
In a full audit, run all of it and number the findings `L6-<n>`.

### 7. Licensing and provenance

- Run `python3 scripts/shipped_source_hashes.py --check` (writes nothing; exits 1 if the hash list is out of date).
- Every shader, font, library and image added since the baseline has an entry in `docs/licenses/` or
  `src/main/notices/`, with a compatible license and the attribution kept.
- New clean-room work is recorded in `docs/developer/clean_room_provenance.md`.
- Shaders with `To confirm` provenance are listed. Check the ISF headers (`CREDIT`, `LICENSE`).
- Check the state against `.planning/license-audit-*.md`.

### 8. Health and tracking

- Run the full Gradle test suite and the Playwright suite. Record the pass/fail counts, the slowest tests and anything flaky.
- **Build:** no new compiler warnings in changed files.
- **`.planning/open-items.md`:** strike the items that are done and re-check the copied ones.
- **`.planning/app-checklist.md`:** move done items out. Items that can be checked by script go to `scripts/verify`.
- **Memory notes:** flag notes that contradict the code. For example, a note that says something is "uncommitted"
  when it has since been committed.

## Method

1. **Scope.** Find the baseline tag and list the commits since it (`git log --oneline <tag>..HEAD`), split into desktop
   (`src/`), web (`web/`, `server/`) and docs. Run `python3 scripts/audit/checks.py` and hand its output to the lanes.
2. **Review (read-only).** One reviewer agent per lane. Lane 1 is split by package when the diff is large, and lane 8
   is run directly. Reviewers write findings to `.planning/audit-YYYY-MM-DD/laneN.md` and edit nothing else.
3. **Verify.** Every finding marked **bug** or **wrong** is checked by a second reader, who opens the code at the cited
   line before anything gets fixed. Findings that don't hold up are dropped and noted as false positives. The fixer can
   be the second reader: a fix agent confirms each bug before changing code, and the docs pass confirms each "wrong"
   doc line against the code before rewriting it.
4. **Triage.** Merge everything into `.planning/audit-YYYY-MM-DD.md` with sections A (bugs), B (rules/docs false),
   C (stale), D (polish), E (owner decisions needed) and F (new automatic checks).
5. **Fix.** Code fixes first, in worktrees whose file scopes don't overlap. Each fix gets a test where a test is
   practical. Fixes the user would notice get a release-notes entry and an app-checklist line.
6. **Docs pass.** Done last, because the fixes change what the docs must say. Covers `ARCHITECTURE.md`,
   `DECISIONS.md` (edit entries in place, no dated appends), `docs/developer`, the user guide, both release-notes
   files, the bundled HTML and tooltips.
7. **Turn findings into checks.** Every finding type seen in a second audit gets a script or test (see below).
8. **Close.** Tag `audit-YYYY-MM-DD`, then move anything left open into `.planning/open-items.md`. Delete the
   per-lane files and keep only the merged report until the next audit.

## Who runs what

Agent definitions live in `.claude/agents/`. Pick the cheapest model that fits the work:

| Work | Agent type | Model |
|---|---|---|
| Lanes 1, 2 (correctness, rules) | `general-purpose` | session model (Opus) |
| Lane 6 (web TV) | split as in [Web TV Audit](audit_web_tv.md#who-runs-what) | Opus and Sonnet |
| Lanes 3, 4, 5, 7 and the docs pass | `audit-docs` | Sonnet |
| Fix groups (step 5) | `audit-fixer` | Sonnet |
| Lane 8 | run directly | none |

## Finding format

```
### L<lane>-<n> [bug|wrong|stale|polish|decision] Short title
- Where: path/File.kt:123 (and any other sites)
- Evidence: what the code does, quoted or paraphrased, and what the doc/rule/user expects
- Impact: who notices and when
- Fix: the smallest change that resolves it
- Verified: (filled in during step 3) yes / no, false positive because ...
```

Severity:

- **bug:** wrong behaviour, a crash, a stall or data loss.
- **wrong:** a doc, rule or tooltip says something false.
- **stale:** something that was true once and is now incomplete.
- **polish:** consistency or wording.
- **decision:** the owner has to choose.

## Automatic checks

Findings that came up in two audits, and the checks they become. Status is kept up to date here.

| Check | Catches | Status |
|---|---|---|
| `LayerDependencyTest` | package layering (midi/control ↛ ui, ...) | exists |
| `TooltipHelperTest` | tooltip helper behaviour | exists |
| `checks.py release_notes_parity` | `RELEASE_NOTES.md` vs `docs/release_notes.md` drift | exists |
| `checks.py doc_links` | `file:///` or absolute links, links to missing files, docs not in mkdocs nav | exists |
| `checks.py ui_terms` | banned terms (table in lane 5) in Kotlin string literals | exists |
| `checks.py raw_tooltips` | `ImGui.setTooltip` / `beginTooltip` outside `TooltipHelper.kt` | exists |

`scripts/audit/checks.py` runs all of them (or one, by name) and exits 1 on any problem; run it in step 1 of every
audit. A line that is fine as it is opts out with a trailing `// audit-allow: <check>` comment that says why.
When a lane's banned-terms table grows, update `BANNED_TERMS` in the script too.
