# Rewrite spec: replacing the Lawrence Norton contributions

**Role:** this file is the SPEC-WRITER output. The implementer reads only this file plus the *callers* of each function
(to keep signatures compatible). The implementer must not open `git log`/`git blame`/`git show` for the listed
hunks, nor the old bodies of the listed lines, and must not read the old versions of the listed tests.
Written 2026-10-13 from `git blame -w -M -C` on HEAD (about 500 surviving source/test lines of about 96k; the 2026-07-06
commits on the `codex/*` branches).

How to use: for each item, write new code from the behavior below, in this repo's style, then run the tests. Where a
value is marked **free**, pick your own. Where it is marked **interface**, it is a name or contract that other code
depends on and must be kept. Tests are rewritten from the "Test obligations" lists, not from the old test files.
After all items are done, re-run blame: no line may remain attributed to the contributor.

Files (current paths under `src/main/kotlin/llm/slop/liquidlsd/`):

| # | File | Surviving lines | Section |
|---|---|---|---|
| 1 | `ui/FileSystemManager.kt` | 66 | Managed paths, scan cache |
| 2 | `ui/MixerLayout.kt` | 42 (+7 in `ui/MixerPanel.kt`) | Mixer layout |
| 3 | `presets/PlaylistParser.kt` | 33 | Playlist parsing |
| 4 | `audio/JackClient.kt`, `audio/AudioEngine.kt` | 33 + 22 | JACK failure reporting |
| 5 | `rendering/GLDebug.kt` (+4 in `Main.kt`) | 28 | GL debug callback |
| 6 | `midi/MidiMappingManager.kt` | 24 | Profile filenames |
| 7 | `audio/SystemAudioVolume.kt` | 21 | Process timeouts |
| 8 | Small residue: `PresetManager.kt` (7), `PresetRepository.kt` (4), `SavePresetModal.kt` (4), `Main.kt` (4), `SessionSerializer.kt` (3), `AudioEnginePanel.kt`, `QueueEngine.kt`, `PlayQueueManager.kt` (1 each) | ~25 | Residue |
| 9 | Tests listed per section | about 190 | Test obligations |
| 10 | Docs: `.planning/codebase/*`, `.planning/debug/window-startup-layout-jitter.md`, `.planning/quick/260707-*`, `.planning/STATE.md` lines, 38 README lines | about 450 | Delete / rewrite (see end) |

---

## 1. FileSystemManager: managed paths and the scan cache

### 1a. Managed asset roots
- There is a fixed set of "managed roots": the presets root, playlists root, FX presets root, FX chains root, FX
  playlists root, transitions root, transition playlists root. (Each already has a `get...Root()` accessor; use them.
  Note generator-defaults and ISF-overrides roots exist as constants but are **not** managed roots: keep that.)
- `isManagedAssetPath(file): Boolean` (**interface**, `internal`): true when the file's *canonical* path (symlinks and
  `..` resolved) equals a managed root or lies beneath one. Comparison is on path components, not string prefixes: a
  sibling directory named like `presets_evil` must not count as inside `presets`.
- Every mutating operation must refuse an unmanaged path *before touching disk* and return `Result.failure` with an
  `IllegalArgumentException` whose message says the path is outside the managed asset roots and includes the path:
  - `renameFile(oldPath, newName)`: check the source, and also the computed destination (same directory, new name plus
    the old extension). Order: existence check, then source check, then destination check, then "already exists".
  - move/copy-style operations (the two with a source and a target directory or file): check the source, the target
    directory (when there is one) and the final target file.
  - `deleteFile(path)`: existence check, then managed check.
  - `createDirectory(parentPath, name)`: parent exists and is a directory, then parent managed check, then the new
    directory path managed check (this blocks `name = "../x"`), then "already exists".
- On a refusal the failure value's *type* is `Result<String>` or `Result<Unit>` as the function already declares;
  when a function returns a path on success it must not return a path on refusal.
- After a successful mutation (rename, move/copy, delete, create) the whole scan cache and the preset-meta cache are
  cleared (`clearScanCache()`, **interface**, `internal`). A refused or failed operation does not clear them.

### 1b. Directory scan cache
- `scanDirectory(directory)` (**interface**): returns an empty list for a missing or non-directory input.
- Results are cached per canonical directory path. A cache entry holds: a content signature, the time it was stored,
  and the item list.
- Signature = the directory's own modified time plus, for each direct child sorted by name: name, is-directory flag,
  length, modified time. A recursive variant (`getRecursiveDirectorySignature`) lists every file below, sorted by path,
  with name, length, modified time. Both are `internal`, both return an empty string for a missing directory.
  (`getDirectorySignature` and `getRecursiveDirectorySignature` are **interface**, called elsewhere.)
- Cache policy: if a cached entry exists and its signature equals the current one: when younger than the TTL return
  its items; when older than the TTL return its items **and** schedule one background rescan of that directory
  (existing `triggerAsyncScan`). If the signature differs, rescan synchronously and replace the entry.
- TTL: **free** (the old value was one second; keep it short, under 5 s).
- The cache is thread-safe (concurrent map). Multiple rescan requests for one directory must not queue duplicate
  background tasks (existing `pendingScanFutures` mechanism stays).
- Sorting of a scan result: folders first, then by name (existing behavior at the end of the scan; keep it).

### 1c. Test obligations (rewrite `FileSystemManagerTest` from these)
1. Adding then removing a file in a scanned directory changes `getDirectorySignature`.
2. `scanDirectory` returns fresh contents after a file is added (after the TTL, or by clearing the cache).
3. `isManagedAssetPath`: true for a file inside the presets root and inside the playlists root; false for a file in the
   temp dir; false for the sibling-prefix case (`<root>_other/x`); false for a `..` escape that resolves outside.
4. `renameFile` where the old path is managed but `newName` is `../../evil` is refused and the file did not move.
5. `deleteFile` of an unmanaged file is refused and the file still exists; of a managed file succeeds.
6. `createDirectory` with `name = "../escape"` under a managed parent is refused and nothing is created.
7. Existing tests for concurrent scans and default-library install stay as they are (they are not part of this spec;
   check blame, any lines still attributed to the contributor in them are rewritten in your own words).

---

## 2. MixerLayout: width/height solving for the mixer pane

### Interface (**interface**)
- `object DeckTileMetrics { const val IMAGE_INSET: Float; fun bottomBarHeight(frameHeight, textLineHeight, itemSpacingY): Float }`.
  Inset is 3 px. `bottomBarHeight` currently returns 0 (the toolbars no longer exist); keep returning 0, parameters
  default to 0.
- `data class MixerLayout(contentWidth, renderWidth, offsetX, masterHeight, deckChildHeight, deckPVHeight = deckChildHeight)`.
- `object MixerLayoutCalculator` with `calculate(...)` and `calculateMaxAllowedWindowWidth(...)`. Parameter lists are
  defined by the callers in `MixerPanel.kt` and the tests; keep them exactly:
  - `calculate(windowWidth, availableHeight, windowPaddingX, scrollbarWidth, textLineHeightWithSpacing,
    frameHeightWithSpacing, itemSpacingY, aspectRatio = 9/16, randomizationEnabled = true): MixerLayout`
  - `calculateMaxAllowedWindowWidth(availableHeight, windowPaddingX, textLineHeightWithSpacing,
    frameHeightWithSpacing, itemSpacingY, aspectRatio = 9/16, randomizationEnabled = true): Float`
  (`scrollbarWidth` and `randomizationEnabled` are accepted but unused today; keep accepting them.)

### Behavior
The mixer pane stacks, top to bottom: a master preview (full width), a master-controls bar, and two rows of deck tiles
(each row two tiles side by side). Heights are driven by the aspect ratio (height/width, clamped to 0.2..5).

- Content width = window width minus both horizontal paddings, at least 1.
- Vertical chrome (all non-preview height): master-controls bar height + separators/spacings below the master image +
  separators/spacings below the controls bar + a gap between the two deck rows + extra per-tile non-image height for
  two rows (currently 0) + a rounding allowance of one item spacing.
  - controls bar height = max(frame-height-with-spacing + 12, 34).
  - below the master image: three item spacings plus 1 px (spacing, separator, spacing).
  - below the controls bar: four item spacings plus 1 px.
  - row gap: one item spacing plus 6.
- The previews stack to 2.0 times aspect times width: master (1.0) plus two deck rows (0.5 each), minus 8 px per unit of
  aspect for the gutter between tiles. Hence the widest content the available height can hold is
  `(availableHeight - chrome + 8*aspect) / (2*aspect)`; if no height is available (<= 0 after chrome), treat as
  unbounded (`Float.MAX_VALUE`) for the max-width function and as "content width" in `calculate`.
- `calculateMaxAllowedWindowWidth` returns that maximum content width plus both paddings.
- `calculate`: render width = content width capped at the maximum, at least 1. Offset X = half the leftover, at least 0
  (centers the previews when height limits the width).
  - tile width = (render width - 8) / 2 (8 px gap between tiles), at least 1; image width = tile width - 2*inset, at
    least 1.
  - master height = render width * aspect, at least 120.
  - deck tile height (both `deckChildHeight` and `deckPVHeight`) = image width * aspect + per-tile extra (0), at
    least 80.
- `MixerPanel.kt` calls `calculate` with values read from the ImGui style/window (window width, available Y, padding,
  scrollbar size, text line height with spacing, frame height with spacing, item spacing Y, aspect from the master
  output size). The 7 surviving lines there are that call-site argument list; rewrite them in the same shape.

### Test obligations (rewrite `MixerLayoutTest`)
1. Wide, tall pane: render width equals content width (no scrollbar reservation), offset 0.
2. Tight height: render width is smaller than content width, offset > 0, and master + two deck rows + chrome fit in the
   available height within 1 px.
3. Tiny screen: master height >= 120 and deck height >= 80 regardless of inputs.
4. Aspect ratios 16:9, 4:3, 1:1 and 9:16: master height = render width * aspect; aspect outside 0.2..5 is clamped.
5. `calculateMaxAllowedWindowWidth` fed back into `calculate` as the window width yields a layout whose total stacked
   height equals the available height (within 1 px).

---

## 3. PlaylistParser: playlist items and name resolution

**Interface:** `object PlaylistParser` with `parseItems(content: String): List<String>`, `parseFile(file: File): List<String>`,
`resolveItem(name: String, presetRoots: List<File> = default): File?`, `resolveItems(items: List<String>, presetRoots = default): List<File>`.
Default root list: the single directory `library/presets`. Recognised preset extensions: `.lsd` (list, extensible).

- One shared parser for the three playlist kinds (deck, FX, transition): all use a JSON object with an `items` array
  of strings. Do not depend on any of the three DTO classes.
- `parseItems`: if the text, after leading whitespace, starts with `{`, parse as JSON (lenient: unknown keys ignored)
  and return the strings in `items`; a missing `items` key gives an empty list; malformed JSON propagates the exception.
  Otherwise treat as plain text: one entry per line, each trimmed; skip empty lines and lines starting with `#`.
- `parseFile` = `parseItems(file.readText())`.
- `resolveItem(name, roots)`: first match wins, in this order, a match meaning an existing regular file:
  1. `name` as given (relative to cwd, or absolute);
  2. `name` under each root, in list order;
  3. for each recognised extension: the name with the extension appended (not appended again if it already ends with it,
     case-insensitively), as given; then under each root in order.
  Returns `null` if nothing matches.
- `resolveItems` keeps input order and drops unresolved names.

### Test obligations (this behavior is covered in `PlayQueueManagerTest`; the section-2-style tests below replace the
contributor's lines there)
1. JSON text with `items` returns them in order; JSON without `items` returns empty.
2. Text lines: blank lines and `#` comments skipped, whitespace trimmed.
3. A name without extension resolves to `<root>/<name>.lsd`; a name with `.LSD` in any case is not double-suffixed;
   a missing name resolves to null and `resolveItems` drops it.
4. A direct existing path wins over the same name under a root.

---

## 4. JACK start-failure reporting (JackClient + AudioEngine)

**Interface:** `enum class JackStartFailure { NATIVE_LIBRARY_MISSING, CONNECTION_FAILED }`;
`JackClient.lastStartFailure: JackStartFailure?` and `lastStartFailureMessage: String?` (public read, private write,
thread-visible); `AudioEngine.lastJackFailure` and `lastJackFailureMessage` (same shape); `AudioEngine.tryReconnect(force: Boolean = false)`.

- `JackClient.start()` clears both failure fields at the start of an attempt. On failure it sets the kind and the
  exception's message, logs one warning line summarising the cause ("native library not available" vs "could not
  connect to server", plus the exception message, plus "running in silent/fallback mode"), and returns false. It
  returns true on success.
- Classification: a failure is `NATIVE_LIBRARY_MISSING` if the throwable is an `UnsatisfiedLinkError` or its message
  mentions "native library" (case-insensitive); otherwise `CONNECTION_FAILED`.
- `AudioEngine` copies the client's failure kind and message after each start attempt so the UI can display them
  (`AudioEnginePanel` reads these; 1 surviving line there is the read).
- Automatic reconnection: a flag says whether automatic reconnect is allowed. It is turned **off** after a start attempt
  fails because the native library is missing (retrying cannot succeed). `tryReconnect(force = false)` does nothing when
  the flag is off; `tryReconnect(force = true)` turns the flag back on and starts the client. A deliberate start from
  the UI (the 324-326 area: public start that enables reconnect then calls the private start) also turns it on.

### Test obligations
1. A client that fails with an `UnsatisfiedLinkError` reports `NATIVE_LIBRARY_MISSING`; one failing with another
   exception reports `CONNECTION_FAILED` and the message.
2. A fresh successful start clears the previous failure.
3. After a native-library failure automatic reconnect is off; forced reconnect re-enables it. (Needs the existing
   injection seam or a small test hook: add the smallest one that fits the surrounding code.)

---

## 5. GLDebug: OpenGL debug-message logging

**Interface:** `object GLDebug` with an install function (existing name) and `disposeDebugCallback()`.
Callers: `Main.kt` calls install after context creation and `disposeDebugCallback()` during shutdown (the 4 surviving
lines in `Main.kt` are the window-size int arrays and the shutdown call).

- Install: if neither the `GL_KHR_debug` extension nor GL 4.3 is available, log one info line "debug callback not
  supported" and return without error.
- Otherwise create a debug-message callback, hold a reference in a field so it can be freed, and register it. The
  callback ignores `GL_DEBUG_SEVERITY_NOTIFICATION` messages; for every other severity it logs one **warning** with id
  (hex), source, type, severity and the message text (decoded from the native pointer with the supplied length).
- `disposeDebugCallback()` frees the callback if one exists and clears the field; safe to call twice and safe if never
  installed.
- `Main.kt` window size: read the GLFW window size into two one-element int arrays when needed (existing use at ~446-458).

### Test obligations
No unit test (needs a GL context). Manual check: run the app, confirm no startup spam from notification-level messages
and that disposing at exit does not crash.

---

## 6. MIDI profile filenames (MidiMappingManager)

**Interface:** `internal fun sanitiseProfileName(name: String): String` and `internal fun midiProfileFile(midiDir: File, profileName: String): File`.

- `sanitiseProfileName`: trim; throw `IllegalArgumentException` (message includes the offending name) if the trimmed
  name contains `/`, `\`, `..`, or NUL; also throw if it is empty or contains anything outside letters, digits,
  underscore and hyphen. Otherwise return the trimmed name.
- `midiProfileFile(midiDir, profileName)`: sanitises, then returns `File(midiDir, "<name>.json")`; before returning,
  verify the canonical path of that file is inside the canonical `midiDir` (path-component comparison) and throw
  `IllegalArgumentException` mentioning `library/midi` if not.
- Callers (load, save, delete of a profile; the lines near 152-185) must obtain every profile path through
  `midiProfileFile`, and use the *sanitised* name for the active-profile bookkeeping and log lines, so an unsafe name
  can never be stored as the active name. A missing profile file on load creates an empty profile object under that
  sanitised name and sets it active.
- The two top-of-file regexes (`safeProfileCharacter`, `repeatedUnderscores`) are unused helpers in this file today;
  delete them unless something else in the file uses them after your rewrite.

### Test obligations
1. Names with `../x`, `a/b`, `a\b`, a NUL, `..`, empty, spaces, or dots are rejected; `my-profile_2` accepted and trimmed.
2. `midiProfileFile(dir, "ok")` ends in `ok.json` and is inside `dir`.
3. Loading a profile name with a path in it throws and leaves the active profile unchanged.

---

## 7. SystemAudioVolume: bounded external processes

- The helper runs external commands to read and set the system volume. Each spawned process is waited on with a hard
  time limit (**free**, 1 to 5 s). If the limit passes: ask the process to end; if it still lives after a short grace
  (**free**, about 250 ms), kill it forcibly. The helper reports "timed out" as a false return.
- Set-volume: on timeout, log a warning "timed out setting system volume" and carry on (no exception).
- Query-volume (runs on the helper's worker): on timeout, log a warning "timed out querying system volume" and return
  without publishing a value (the cached volume keeps its last value).
- The surviving 21 lines are the constant, the import, and the wait-helper plus the two call sites; rewrite those only.

### Test obligations
Manual (or with a fake process wrapper if one already exists): a command that sleeps longer than the limit is terminated
and the caller returns within about limit + grace.

---

## 8. Residue (one-to-seven-line hunks)

- `PresetManager.kt`: `presetIoExecutor` is a **bounded** single-thread executor (daemon thread named
  `PresetManager-IO`). Bounded means: a fixed small queue, with a rejection policy that runs the task on the caller or
  drops it with a log line (**free**). Also the nested `RestoredQueueState(files, activeIndex, unresolvedPaths = empty)`
  data class (**interface**).
- `PresetRepository.kt`: the four async calls (`CompletableFuture.runAsync(..., executor)`) must pass
  `PresetManager.presetIoExecutor` as the second argument. If your executor choice in `PresetManager` is bounded, ensure
  none of those callers blocks on its own results from inside the executor (deadlock check).
- `SessionSerializer.resolveRestoredQueue(paths, activeIndex)`: already not in the contributor's lines, but its call
  site (203-207) reads the files and index from the returned state. Behavior to preserve (tested by
  `SessionStateTest`): drop missing files, keep their path strings in `unresolvedPaths`, and rebase the active index
  to the surviving item at the same position or the next surviving one, else the first survivor, else -1.
- `SavePresetModal.kt`: the window is centred on the display using the ImGui IO display size (the 4 lines are the
  reads of the width/height and the centre calls).
- `AudioEnginePanel.kt`, `QueueEngine.kt`, `PlayQueueManager.kt`, `MidiEngineQueueTest.kt`, `BgQueueManagerTest.kt`:
  one or three lines each; open them once, rewrite the attributed lines by renaming or rephrasing as appropriate.

### Test obligations (tests the contributor wrote in `SessionStateTest` / `PlayQueueManagerTest` / `MidiEngineQueueTest`)
1. Restored queue where item 1 of 3 is missing and active was item 2 (index 1): result keeps two files, active points
   to the former item 3.
2. Restored queue where the active file is missing and later ones survive: next surviving item becomes active; where
   nothing later survives: the first survivor (never past the end).
3. All missing: empty files, active -1, all paths listed as unresolved.
4. MIDI queue bounded: fill it past capacity, newest dropped (or as the existing queue contract says), no exception.

---

## 9. Docs to delete or regenerate (no spec needed)

- Delete `.planning/codebase/{TESTING,INTEGRATIONS,STACK,CONVENTIONS,CONCERNS,STRUCTURE,ARCHITECTURE}.md` (known stale;
  regenerate from code later if wanted). Delete `.planning/debug/window-startup-layout-jitter.md` and
  `.planning/quick/260707-readme-improvements/`. Remove the contributor's lines in `.planning/STATE.md`.
- README: rewrite the 38 attributed lines in your own words from the app itself.
- After deleting, re-run the blame sweep to confirm the count is zero.

## 10. Finish line

1. `./gradlew test` green.
2. Blame sweep: `git blame -w -M -C --line-porcelain` over `git ls-files` shows no `author Lawrence` line.
   Note: a rename-only or formatting change may still blame to the old author via `-M -C`; if so, re-write the line.
3. Add a ledger row to `docs/developer/clean_room_provenance.md`: spec author, implementer, date, method.
4. Do not squash or rewrite history. The old commits remain in history; this is about the working tree.

---

## 11. Masked-copy procedure (second pass, 2026-10-13)

The first pass could not replace hunks inside large files without seeing them. Procedure instead:

- **Do not open the real files listed in `/tmp/lnmask/manifest.json`.** Read `/tmp/lnmask/masked/<same path>`. Every
  hunk still attributed to the contributor is replaced there by a marker `// [[REWRITE n: replace k line(s) per spec]]`.
  Everything else is the owner's code and may be read freely.
- A marker may be a fragment (a function header, one line inside a function, or the `}` closing one). Infer what goes
  there from the surrounding owner code, the spec section for that file, and the callers; compile errors are your check.
- Write each replacement as `/tmp/lnmask/repl/<repo path>/<n>.txt` (n = marker number). An empty file deletes the hunk.
  A marker you do not write is left as it is, so a missing one stays attributed; the blame sweep catches it.
- Run `python3 -I /tmp/lntool/splice.py <repo> /tmp/lnmask`. It splices bottom-up into the real files (line counts may
  change). Run it **once**; it reads line numbers from HEAD's blame, so re-masking after other edits to those files is
  needed if you change them first. Then `./gradlew test`.
- Already rewritten in pass 1 and excluded from masking: `PlaylistParser.kt`, `GLDebug.kt`, `MixerLayout.kt`.
- Exposure to record in the ledger: pass 1 saw the first 60 lines and the function index of `FileSystemManager.kt`,
  which included a few attributed lines (imports, the scan-cache fields and TTL constant, the cache-clear function).
  Those are trivial and are masked/replaced in this pass anyway.
