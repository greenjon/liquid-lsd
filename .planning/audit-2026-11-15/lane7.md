# Lane 7: Licensing and provenance (audit 2026-11-15)

Baseline `audit-2026-10-29` (fa4335e) to HEAD aeb7937 (154 commits). Read-only review; nothing but this file was written.
Not legal advice: items the owner must settle are marked **decision**.

## Checklist coverage

| Item | Result |
|---|---|
| Run `scripts/shipped_source_hashes.py` | **No check mode**: it always rewrites `src/main/resources/shipped_source_hashes.txt`, so it was not run. Checked by hand instead: all 80 files in `library/sources` (37 sources) and `src/main/resources/default_sources` hash to entries in the 224-line list. **Clean.** Usage: run after changing a bundled source; the guard is `VisualSourceManifestTest.everyBundledSourceFileIsInTheShippedHashList`, not the `ShippedSourceHashesTest` the docstring names (L7-15). The two source trees differ for 2 files (L7-16); the jar takes `library/sources` (checked in `build/resources/main/default_sources`). |
| Shaders added since baseline | `kifs_storm`, `scope`, `spectrogram` (desktop + `web/shaders/*.frag`). All three have `SHADERS_LICENSE.md` rows and ledger rows; CREDIT "Liquid LSD", no LICENSE field. `kifs_storm` stays **To confirm** (L7-3). `scope`/`spectrogram` rows record that Vidvox shaders were read (L7-10). Changed since baseline: `chladni_cymatics` rewrite (no row anywhere, L7-8/L7-9), `kifs` icosahedral type (ledger row updated: clean). |
| ISF headers CREDIT/LICENSE | All 35 `.fs` sources, 27 filters, 8 transitions have a CREDIT; **no file has a LICENSE key**. 22 filter headers say MIT in CREDIT and in a `// License: MIT License` comment; sources and transitions say only "Liquid LSD" (L7-1). Quilez credited only in comments (kifs_storm, dynamic_spiral, gradient_map, neon_edge) (L7-3). Web copies (`web/shaders/fx`, `transitions`) keep CREDIT; differ from desktop only by the GLSL ES preamble. Web-only `blit`, `crt_post`, `mixer`, `mandala` frags: own, no third-party markers. |
| Fonts added since baseline | `web/fonts/caveat-*.woff2` with `web/fonts/OFL.txt` (Caveat Project Authors, OFL 1.1, no Reserved Font Name): **clean**. Desktop font licences (`src/main/resources/fonts/LICENSE-{Inter,JetBrainsMono,Lucide}.txt`) added since baseline and fed into the notices: **clean**. |
| Libraries | `build.gradle.kts`: no runtime dependency added since baseline; flexmark and SnakeYAML removed. `src/main/notices/libraries.txt` covers every runtime group, the build fails on a missing one; generated `THIRD_PARTY_NOTICES.txt` (build/generated, 2026-11-12) lists all libraries, the bundled natives, Syphon, Spout, Temurin, JNAJack and the three fonts. **Clean.** SpoutLibrary.dll now ships (3b8e9c4) with a BSD-2 notice (`51-spout.txt`): clean. Web: no vendored third-party JS (no copyright/licence banners, no CDN loads). Node: `package.json` is dev tooling only (Playwright), nothing ships, but its metadata is wrong (L7-12). `android/lsd-visuals` (new): Apache-2.0 AndroidX/OkHttp/Gson, shipped by the apps that copy it (L7-13). |
| Images/media added since baseline | `stickers/spaz-go-qr.svg` (generated QR, own): clean. `web/checks/tape_fixture.ogg`: 12 s mono, spectrum shows synthesized low pulses, excluded from publishing (`--exclude=/checks/`): clean, origin unrecorded (not worth a finding). Untracked but published/bundled: the gallery photos (L7-14). |
| New clean-room work recorded | Recorded: `kifs` icosahedral, `kifs_storm`, `spectrogram`, `scope`, `web/mqtt.js`. Not recorded: `chladni_cymatics` rewrite + Pitch Follow, BeatPhaseLock, TransientDetector/HitScale, web tempo port, interop work from third-party headers/DLL (L7-9, L7-11, L7-4). Black-box evidence lives only in gitignored build dirs (L7-7). |
| `To confirm` list | Grep across docs, `.planning`, shaders, Kotlin, JS. Shaders: **`kifs_storm` only** (`SHADERS_LICENSE.md:58`, ledger line 33). Other ledger To-confirm items: beat tracker, flame variations 46-48 ("Unknown") and 49-97 (missing original description), Norton grant, Quilez terms. Five older sources have no provenance entry at all, so they are not even "To confirm" (L7-8). |
| `.planning/license-audit-2026-11-06.md` | Status table below; the file itself is stale (L7-17). |

### Status of the 2026-11-06 licence audit items

| Item | Status 2026-11-15 |
|---|---|
| Ableton Link (`link_jni`) | Still only on `link-follow-lead` (not an ancestor of `main`). Unchanged; blocks option B (L7-6). |
| Flame variations 49-97 | Unchanged (no flame code commits since baseline apart from file-save plumbing). Original description file still missing (L7-2). |
| Flame variations 46-48 | Unchanged, basis still "Unknown" (L7-2). |
| JNAJack (LGPL) | Partly done: `70-jnajack-source.txt` explains replacing it. Relicensing allowance still open; only matters for option B. |
| Logback / JNA dual licences | **Done**: `libraries.txt` names EPL-2.0 and Apache-2.0. |
| Norton contributions | Open. `git blame -w -M -C` still gives **265 lines in 24 files** (re-run today, same as 11-06); `ScanCacheEntry` not renamed; no written grant (L7-5). |
| Beat tracker | Open; ledger now names the papers. Web port added since (L7-4). |
| KIFS Storm and Quilez helpers | Open (L7-3). |
| MIT labels on shaders | Open (L7-1). |
| Released versions stay GPL | Accepted; nothing to do. |
| Notices/LICENSE in jar and zips (branch `license-notices`) | **Done**: merged as 0aaa3ab; verified in `build.gradle.kts:415-475`, `:536`, zips include `LICENSE`. |
| Trademark search (Fractal Flame, Garden, Breed) | Open; nothing in the repo shows it was done (L7-6). |
| Credits checks (paper authors/revision) | Open; `SHADERS_LICENSE.md` still says "verify ... before citing". |
| Side finding: Spout not shipped | **Fixed** by 3b8e9c4 (CI downloads the hash-pinned 2.007.017 SDK and ships the MD `SpoutLibrary.dll`). Still unchecked on Windows hardware (not a licensing item). |

## Findings

### L7-1 [decision] Shader licence labels contradict the repo licence and the shader policy
- Where: 22 of 27 `src/main/resources/default_filters/*.fs` (CREDIT "Liquid LSD (MIT License)" / "(Cleanroom MIT)" plus `// License: MIT License`, e.g. `radial_blur.fs:3,65`); `docs/licenses/SHADERS_LICENSE.md:9-12` (policy: "Strictly Prohibited: No shaders under copyleft (GPL/LGPL/AGPL)") and every table row's "MIT"; `LICENSE` (GPL-3.0); sources and transitions carry no licence at all.
- Evidence: the app is GPL-3.0, its shader policy forbids GPL shaders, and the audit table calls every bundled shader MIT while most headers say nothing. Carried over from the 11-06 licence audit; nothing changed since. Linked to the undecided licensing intent (free non-commercial vs GPL).
- Impact: anyone can lift the MIT-labelled filters into a commercial product; a relicence (option B) cannot cover files already published as MIT. Readers of the docs get three different answers.
- Fix: owner decides the shader licence (MIT, GPL, or the future licence); then make headers (add an ISF `LICENSE` key or drop the comments), the table and the policy paragraph agree.
- Verified:

### L7-2 [decision] Fractal Flame variations 46-48 and 49-97 still have an unclean specification side
- Where: `docs/developer/clean_room_provenance.md:26-27`; `.planning/flame-variations-spec.md`; commits `a7cba2b`, `18ad6d4`, `7c7582a`, `de8e555`.
- Evidence: unchanged since the 11-06 audit. 46-48 were changed to "follow flam3" on an unknown basis; 49-97 come from a spec whose first author read "the original program's code"; the original description file is still not in the repo and which program was read is unrecorded. The black-box kit exists (`tools/blackbox/`) and has re-derived only three variations.
- Impact: blocks option B (selling); fine under GPL.
- Fix: owner chooses: re-derive by black box, remove them, or accept with a lawyer. Either way record in the ledger that the original description is gone.
- Verified:

### L7-3 [decision] KIFS Storm provenance and the Quilez helpers are still To confirm, and Quilez attribution is uneven
- Where: `library/sources/kifs_storm/kifs_storm.fs:66,186`; `library/sources/dynamic_spiral/dynamic_spiral.fs:37,120` ("IQ cosine palette"); `src/main/resources/default_filters/gradient_map.fs:67`, `neon_edge.fs:69` (and web copies); `docs/licenses/SHADERS_LICENSE.md:24,58`; ledger line 33.
- Evidence: kifs_storm (added since baseline, ca67009) is the only shader marked To confirm (how the 2026-06 original was written, whether Quilez's helpers need a credit or licence note). `SHADERS_LICENSE.md` still gives Quilez's palette as "MIT/Public Domain" with no source. `dynamic_spiral` and `neon_edge` credit IQ in comments but have no mention in the table or ledger; no shader puts the credit in the ISF `CREDIT` field, which the policy (`SHADERS_LICENSE.md:12`) says is where attribution goes.
- Impact: provenance record incomplete for a commercial release; attribution not where the policy says.
- Fix: owner confirms how the original KIFS was written; check the terms of Quilez's articles (or rewrite the helpers from the mathematics); then either add "palette after Inigo Quilez" to the four CREDIT fields and the table, or drop the claim.
- Verified:

### L7-4 [decision] Beat tracker still To confirm, now also ported to the web without its attribution line
- Where: `docs/developer/clean_room_provenance.md:24`; `src/main/kotlin/llm/slop/liquidlsd/audio/BeatTrackerEngine.kt:8` ("Inspired by ... Adam Stark and ... Dan Ellis"); `web/beattrack.js` (2298c14, port of the desktop tracker, no such line); `audio/BeatPhaseLock.kt` (ef9c2ac, "same method as web/beattrack.js").
- Evidence: the owner's written statement is still missing. Since baseline the tracker was rebuilt (f38bb1e) and ported to the web TV (2298c14); the ledger row's evidence column lists neither `web/beattrack.js` nor the rebuild.
- Impact: the web TV ships the same To-confirm algorithm; the ledger understates its reach.
- Fix: owner's statement; add `web/beattrack.js` and `BeatPhaseLock` to the row's evidence; copy the "inspired by / papers" line into `web/beattrack.js`.
- Verified:

### L7-5 [decision] Norton contribution: no grant, residue unchanged
- Where: `docs/developer/clean_room_provenance.md:109-131`; `ui/FileSystemManager.kt:41` (`ScanCacheEntry`); top blame files: `FileSystemManager.kt` 37, `FileSystemManagerTest.kt` 25, `MixerLayout.kt` 25, `README.md` 20, `PlaylistParser.kt` 17, `GLDebug.kt` 16.
- Evidence: `git blame -w -M -C` over `src/main/kotlin`, `src/test/kotlin`, `README.md`, `docs/developer` gives 265 lines in 24 files (re-run 2026-11-15), the same as on 11-06. The two rename candidates are untouched; no grant on file. Ledger line 127 still says "about 280".
- Impact: low risk per the earlier audit; open item before selling.
- Fix: ask for the one-line grant; optionally rename the two candidates; update "about 280" to 265.
- Verified:

### L7-6 [decision] Open blockers with no movement: Ableton Link branch, trademark search
- Where: branch `link-follow-lead` (not merged; `main` only has the loader `link/NativeJniLinkBackend.kt` and Carabiner backend, no bundled `link_jni`); `docs/developer/clean_room_provenance.md:137` (trademark search).
- Evidence: both unchanged since 11-06. Merging `link-follow-lead` would bundle GPLv2+-or-proprietary Link, which is fine under GPL-3.0 but blocks option B.
- Impact: merging the branch is a licensing decision, not only a release one.
- Fix: owner decides before merging (Ableton licence, Carabiner only, or accept GPL); schedule the trademark search.
- Verified:

### L7-7 [wrong] Black-box clean-room evidence is cited as "in the repo" but lives in gitignored build folders
- Where: `docs/developer/clean_room_provenance.md:68-73` (evidence column: `build/blackbox/jwildfire/hemisphere/`, `tools/blackbox/build/blackbox/jwildfire/plusrecip/`, `build/blackbox/jw_pre/`, `build/blackbox/cloud_w*`, `build/blackbox/flam3/`, `build/blackbox/flam3t/fan/`); `.gitignore:8` (`build/`).
- Evidence: the ledger header is "Evidence in the repo", but `git check-ignore` matches both `build/blackbox` and `tools/blackbox/build`. The tables exist on this machine only; `./gradlew clean` deletes `build/blackbox`. Only the derived test pairs (`FlameVariationsTest` etc.) are in git.
- Impact: the observation tables that make the black-box variations (hemisphere, plusrecip, pre_blur, inversion, fan/blob/ngon/perspective/fan2 corrections) defensible can be lost by a routine clean or a new clone.
- Fix: copy the `table.csv` files (small text) into a tracked folder, e.g. `tools/blackbox/evidence/<program>/<variation>/`, and point the ledger at it.
- Verified:

### L7-8 [stale] Licence table misses six stock sources and all eight transitions; one path is wrong
- Where: `docs/licenses/SHADERS_LICENSE.md` (sources table lines 51-83, no transitions section); stock list in `DECISIONS.md:63`.
- Evidence: no rows for `celestial_engine`, `chladni_cymatics`, `domain_warp_fluid`, `gyroid_hyperspace` (added c7ed16e, 2026-09-20), `hyper_slice` (159db62, 2026-08-25), `fractal_flame` (prose paragraph only), nor for the 8 stock transitions (`linear_crossfade` ... `vortex_swirl`), although the 11-06 audit counts "27 filters, 8 transitions, sources: our own per SHADERS_LICENSE.md". The Mandala row points at `library/sources/mandala/mandala.fs`, which does not exist (`shader.frag`, `shader.vert`, `meta.json`). `chladni_cymatics` was fully rewritten since baseline (e9cbbee, 7516ac8, 5d628ff) and still has no row. Pre-baseline gaps except the cymatics rewrite, but never caught.
- Impact: the "what we ship and why we may" record is incomplete for 14 shipped shaders.
- Fix: add the six source rows and a transitions table (origin, licence per L7-1); fix the Mandala path. Add a test or `scripts/audit` check that every `DEFAULT_SOURCE_IDS` entry and every stock filter/transition has a row (see F below).
- Verified:

### L7-9 [stale] New algorithm work since baseline is not in the ledger
- Where: `library/sources/chladni_cymatics/chladni_cymatics.fs` (plate modes, `besselJ` WKB approximation line 139, Pitch Follow pitch picker lines 55-96); `audio/BeatPhaseLock.kt` (comb over an onset envelope); `audio/TransientDetector.kt` (TransientGate, HitScale) and `web/dsp.js` ports; `audio/BeatHopper.kt`.
- Evidence: the ledger rule (line 3-4) is "add a row before a new algorithm ships". The cymatics rebuild rests on Chladni plate physics and Bessel functions; BeatPhaseLock on comb-filter phase estimation; none has a row stating source and method (even "own design from textbook physics/DSP"). `.planning/cymatics-rewrite-plan.md` has no provenance note.
- Impact: gaps in the clean-room record; each is likely textbook or own design, so cheap to record now and costly to reconstruct later.
- Fix: add one ledger row each (source of the maths, method, evidence), marking own designs as such.
- Verified:

### L7-10 [decision] Spectrogram and Scope rows record that the Vidvox shaders were read, which the ledger's own rules forbid
- Where: `docs/developer/clean_room_provenance.md:57-58` (added since baseline with 5e8a861); rule at line 12-14 ("Shader ... files that an app ships as plain text count as source"); `scripts/verify/round6/vidvox.py`, `ISFAudioCompatTest`.
- Evidence: "The Vidvox ISF-Files 'FFT Spectrogram' and 'Radial Spectrogram' were run and read as host compatibility tests ... shares no code with them"; "Audio Waveform Shape was run and read". Running them is fine under the rules; reading them is not, and the rows don't say whether reading happened before or after our shaders were written, or who did each.
- Impact: two shipped sources whose clean-room status the ledger itself contradicts. (If the Vidvox files are permissively licensed this may not matter; their licence is not recorded.)
- Fix: owner decides: record the Vidvox files' licence and order of events and accept, or re-derive the two shaders by someone who has not read them.
- Verified:

### L7-11 [decision] Interop code written from third-party headers and a DLL's code is not covered by the ledger rules
- Where: `rendering/SpoutApi.kt` (vtable slots "from `SPOUTLIBRARY` in SpoutLibrary.h 2.007.017 ... Checked against the DLL itself: slots 8 and 24 ... jump to the same function"); `rendering/pipewire/SpaPod.kt` ("Constants are from PipeWire 1.6's headers"); `rendering/SyphonBridge.kt` (Syphon.framework headers). All three added since baseline.
- Evidence: the ledger forbids "decompiling, disassembling ... another program's source" and says reverse-engineering tools are not part of the protocol. Inspecting the DLL's vtable is disassembly in the ordinary sense, though done for interoperability with a BSD-2 library we ship, not to take an algorithm. PipeWire headers are MIT, Syphon BSD.
- Impact: a reviewer reading the ledger literally sees a rule broken; probably a scope gap, not a problem.
- Fix: owner decides; then add a short "Interoperability" section: reading a permissively licensed library's public headers and checking a shipped binary's exported table to call it is allowed, with these three entries listed.
- Verified:

### L7-12 [wrong] `package.json` declares the repo ISC-licensed
- Where: `package.json` (added since baseline): `"license": "ISC"`, `"main": "index.js"` (no such file), `"author": ""`, Playwright under `dependencies`.
- Evidence: npm init defaults left in place; the repo is GPL-3.0 (`LICENSE`, `README.md:224-226`). Nothing ships from it (web/ has no node deps; publish copies web/ only).
- Impact: licence scanners and GitHub's dependency graph read the wrong licence for the repo.
- Fix: `"license": "GPL-3.0-only"` (or `-or-later`, matching LICENSE), `"private": true`, drop `main`, move Playwright to `devDependencies`.
- Verified:

### L7-13 [decision] The MIT-licensed Spaz Radio app bundles the GPL-3.0 web TV engine
- Where: `android/lsd-visuals/` (new, no licence file or headers), `scripts/sync_lsd_visuals.sh` (copies the module plus `web/` as assets into an app); `~/AndroidStudioProjects/spazradio/LICENSE` (MIT); `~/AndroidStudioProjects/liquid-lsd-android/LICENSE` (GPL-3.0).
- Evidence: the Spaz Radio APK (v2.0.x on GitHub and Play) contains liquid-lsd's GPL-3.0 web engine and the `lsd-visuals` Kotlin module, while its repo says MIT and its README does not name the GPL part. The owner holds the copyright in both (Norton's code is desktop-only), so this is a labelling choice, but recipients of the APK get no GPL notice or source pointer for that part. The lsd-visuals module's own Apache-2.0 dependencies are noticed (or not) by the host app, outside this repo.
- Impact: the published app's licence statement is incomplete; if the owner later moves liquid-lsd to a non-GPL licence, the same question repeats.
- Fix: owner decides the licence of `android/lsd-visuals` and the embedded web engine (dual licence it for the app, or add a GPL notice + source link in the Spaz app); add a LICENSE/notice line to `android/lsd-visuals/README.md` either way.
- Verified:

### L7-14 [decision] Gallery photos are listener uploads; rights to republish them are unrecorded
- Where: `scripts/gallery_curate.py:4-5` ("The photos are the listener uploads from the old radio page (spaz.org/radio, 'Add Pic')"); `web/gallery/` (untracked, 129 photos, published to spaz.org/tv by `publish_web_tv.sh`); bundled into the Spaz Radio APK by `sync_lsd_visuals.sh` (about 9 MB).
- Evidence: added since baseline (37a9098, e6a109b). No record of the upload terms on the old page, or of any permission to redistribute them in an app. Outside the desktop zips (so outside the 11-06 audit), but they are distributed.
- Impact: third-party photos (possibly of people) shipped in a released APK and on the web TV.
- Fix: owner records the basis (upload terms, station policy) in the licence docs, or limits the gallery to photos the station owns.
- Verified:

### L7-15 [wrong] `shipped_source_hashes.py` names a test that does not exist and has no check mode
- Where: `scripts/shipped_source_hashes.py:12` ("ShippedSourceHashesTest fails ..."); the real guard is `src/test/kotlin/llm/slop/liquidlsd/rendering/VisualSourceManifestTest.kt:128` (`everyBundledSourceFileIsInTheShippedHashList`); `docs/developer/audit.md:109` ("Run `scripts/shipped_source_hashes.py` and check ...").
- Evidence: the script always rewrites `shipped_source_hashes.txt`; the blueprint's step tells a read-only reviewer to run a writer. The test only walks `library/sources`, not the tracked `default_sources` copy.
- Impact: a reviewer either skips the step or dirties the tree; the docstring sends readers to a missing test.
- Fix: fix the docstring; add `--check` (compute, diff against the file, exit 1 on a missing hash, write nothing) and point audit.md lane 7 at it.
- Verified:

### L7-16 [stale] The tracked `default_sources` copy has drifted from `library/sources`
- Where: `src/main/resources/default_sources/gyroid_hyperspace/gyroid_hyperspace.fs` (last 0c637a3, 2026-10-26) and `.../mandelbox/mandelbox.fs` (050e60f, 2026-10-26) vs `library/sources/...` (c30acc1, 2026-11-06); `docs/developer/rendering.md:169` ("identical copy"), `:212` ("Both `.fs` trees must be identical").
- Evidence: `diff -rq` reports exactly these two files. `processResources` copies `library/sources` over `default_sources` (`build.gradle.kts:483-486`), so the jar gets the new versions (confirmed: `build/resources/main/default_sources/*` equals `library/sources`). Both trees' versions are in the hash list. Lane 3 overlap.
- Impact: none at runtime; the "identical" rule is false, and anyone reading or licence-checking the tracked copy sees old shader text.
- Fix: copy the two files across (or delete the tracked tree, since the build regenerates it, and update rendering.md); add the `diff -rq` to the hash test.
- Verified:

### L7-17 [stale] The 2026-11-06 licence audit no longer matches the tree
- Where: `.planning/license-audit-2026-11-06.md:31` ("Done on branch `license-notices`"), `:57` ("Spout (Windows): Not shipped"), `:70-72` (side finding).
- Evidence: `license-notices` merged (0aaa3ab); Spout ships since 3b8e9c4 with `51-spout.txt`. The file does not list the items added since (kifs_storm, scope, spectrogram, Caveat, gallery, lsd-visuals).
- Impact: the next reader of the licence audit sees resolved items as open and misses new ones.
- Fix: update the file in place from the status table above, or fold its open rows into `.planning/open-items.md` and delete it.
- Verified:

### L7-18 [polish] Ledger rows for the light sources have one cell too few
- Where: `docs/developer/clean_room_provenance.md:42-58` (quasicrystal ... scope).
- Evidence: 4 cells under a 5-column header; the "To confirm" column is empty by omission rather than "n/a" as in the rows above.
- Impact: cosmetic; renders with a missing cell.
- Fix: append `| n/a |` to each.
- Verified:

## F. Proposed automatic checks

- `scripts/shipped_source_hashes.py --check` (L7-15), plus a `diff -rq library/sources src/main/resources/default_sources` assertion in `VisualSourceManifestTest` (L7-16).
- `scripts/audit/licence_rows.py`: every `VisualSourceRegistry.DEFAULT_SOURCE_IDS` id, stock filter and stock transition has a row in `SHADERS_LICENSE.md`, every row's path exists, and every source id appears in the ledger or is marked own work (L7-8). Second audit with this kind of gap would make it mandatory.
- Grep check that every `.fs` has the same licence statement in CREDIT/LICENSE once L7-1 is decided.

## Severity counts

bug 0, wrong 3 (L7-7, L7-12, L7-15), stale 4 (L7-8, L7-9, L7-16, L7-17), polish 1 (L7-18), decision 10 (L7-1 to L7-6, L7-10, L7-11, L7-13, L7-14).
