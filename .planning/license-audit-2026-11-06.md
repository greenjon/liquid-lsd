# License audit, 2026-11-06

What the desktop app ships, and what would block option B: moving future versions from GPL-3.0 to a source-available license with paid commercial tiers. Not legal advice; have a lawyer review it before relicensing.

**How each license was found:** Maven POMs for libraries, the font files' own metadata for fonts, and the repo's ledgers for our own code. Rows marked *(from memory)* have not been checked against the upstream project. No code scanner was run: "our code" relies on `docs/developer/clean_room_provenance.md` and `docs/licenses/SHADERS_LICENSE.md`.

**Out of scope:** the web TV and its gallery photos, the Spaz Android app, and the relay server. None of them is in the desktop app's zips.

## Blocks B

| Item | License or issue | Way out |
|---|---|---|
| **Ableton Link** (`link_jni`, branch `link-follow-lead`, not yet on `main`) | GPLv2+ or Ableton's proprietary license | Ableton license; otherwise remove it, or ask a lawyer about a separate program such as Carabiner |
| **Flame variations 49-97** (46 built, in the Fractal Flame source) | The ledger says one AI read "the original program's code" and wrote the description a second AI implemented: "the spec side is not clean". Which program is unrecorded, and the original description is missing | Re-derive them with the black-box kit in `tools/blackbox/`, as already done for 3 variations; or remove them; or a lawyer accepts the two-AI process |
| **Flame variations 46-48** (`secant2`, `twintrian`, `cross`) | Commit `a7cba2b` changed them to "follow flam3"; the basis is unknown, and the ledger says to treat them as unclean | Go back to the paper as written, then re-derive by black box |

Under GPL (option A), none of these is a problem.

## Needs work for B, not a blocker

| Item | Issue | Way out |
|---|---|---|
| **JNAJack** (LGPL-2.1-or-later, per its source headers) | LGPL allows use in non-GPL apps, if users can replace the library and the license terms allow that, including reverse engineering for debugging. It sits inside a fat jar | Write the allowance into the new license and let a lawyer check it, or ship the jar separately |
| **Logback** (EPL-2.0 or LGPL-2.1), **JNA** (LGPL-2.1+ or Apache-2.0) | Dual licensed | Choose EPL-2.0 and Apache-2.0 in the notices |
| **Norton's contributions** | Rewritten from a spec (`.planning/lawrence-rewrite-spec.md`, ledger section "Replaced third-party contribution"). `git blame -w -M -C` still gives 265 lines on `main`, which matches the ledger's "about 280". A sample was imports, signatures, field declarations and short calls, plus a few real lines (`ScanCacheEntry`, one `sortedWith`). No written grant on file; his commits stay in public history | Low risk. Ask him for a one-line written grant, and rename the ledger's two listed candidates |
| **Beat tracker** | The ledger says built from papers, on the owner's account, marked to confirm. BTrack itself is GPL *(from memory)* | Owner's written statement; cite the papers |
| **KIFS Storm** and Inigo Quilez helpers | Marked **To confirm**. `SHADERS_LICENSE.md` gives Quilez's cosine palette as "MIT/Public Domain" with no source. Check the terms of his articles | Confirm the terms, or rewrite the helpers from the mathematics |
| **MIT labels on shaders** | Some headers say MIT and the audit table says MIT, while the repo's `LICENSE` says GPL-3.0. Anything released as MIT stays reusable by anyone, including commercially | Decide whether shaders stay MIT or fall under the new license; make headers, table and license agree |
| **Released versions** | Stay GPL-3.0 permanently | Accepted; B applies to new versions only |

## Needed under either option

- **Done on branch `license-notices`:** a generated `THIRD_PARTY_NOTICES.txt` and `LICENSE` in the jar and zips, and the font licenses beside the fonts. The Inter and JetBrains Mono licenses declare no Reserved Font Name, so our modified Inter may keep its name. flexmark and SnakeYAML are now build-only, which removes openhtmltopdf (LGPL), PDFBox, ICU and jsoup from the app.
- **Open ledger items:** the trademark search (Fractal Flame, Garden, Breed), and the checks in "Credits and notices".

## Full list

### Libraries in the jar

| License | Libraries |
|---|---|
| **GPLv2+ or proprietary** | Ableton Link (branch only) |
| **LGPL-2.1+** | JNAJack 1.4.0 |
| **EPL-2.0 or LGPL-2.1** | logback-classic, logback-core 1.5.38 |
| **Apache-2.0 or LGPL-2.1+** | JNA 5.19.1 |
| **Apache-2.0** | Kotlin stdlib, kotlinx-coroutines, kotlinx-serialization, kotlin-logging, JetBrains annotations |
| **BSD-3-Clause** | LWJGL 3.4.1 (core, GLFW, OpenGL, SDL, stb) |
| **MIT** | imgui-java 1.92.7.1 and natives, SLF4J |

The native files inside those libraries carry their upstream licenses, checked against each project: Dear ImGui and the 8 extensions compiled with it (MIT), FreeType (FTL), GLFW (zlib), stb (MIT or public domain), libffi (MIT).

### Other bundled files

| Item | License |
|---|---|
| Inter (3 weights), JetBrains Mono | SIL OFL 1.1 (font metadata) |
| Lucide icon font | ISC, with parts from Feather under MIT (upstream `LICENSE`; the font file has no metadata) |
| Temurin JRE 17, in the release zips | GPLv2 with Classpath Exception *(from memory)*. A separate program, so it does not affect the app's license |
| Syphon.framework (macOS, from our `syphon-natives` release) | Syphon's own BSD-style license (upstream `License.txt`) |
| Spout (Windows) | Not shipped: `library/natives/SpoutLibrary.dll` is gitignored and the workflows never fetch it. See below |

### Our own files

| Item | State |
|---|---|
| App code | GPL-3.0 (`LICENSE`), apart from the items above |
| Bundled shaders: 27 filters, 8 transitions, sources | Our own per `SHADERS_LICENSE.md` and the ledger; headers vary between MIT and no license |
| Stock presets, FX chains, curated flames | Our own. The ledger says flames come from our own search, with only statistics taken from public collections |

## Side finding, not licensing

Release builds appear to have no Spout on Windows: `TextureReceiver` and `ExternalVideoDiscovery` load `SpoutLibrary`, but CI never provides it. If so, the README's Spout2 claim doesn't hold for downloaded builds. Check this on Windows.
