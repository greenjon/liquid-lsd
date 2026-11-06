# Localization plan — Spanish, German, French

Status: PLANNED 2026-10-29, decisions settled (below). Target: **v1.1** (ROADMAP Milestone 7). Nothing starts before v1.0 ships.

## Decisions (2026-10-29)

1. **Scope of the first release:** app UI plus source/FX/transition parameter labels and descriptions. The user guide, tour captions, web TV and website are later follow-ups (phase 6).
2. **Content names stay English** (`Kaleidoscope`, `Polar Tunnel`…). Parameter labels and descriptions are translated.
3. **Abbreviations stay English** in every language: SRC, FX, MST, GLB, BGPV, deck letters A/B/C/D.
4. **The decimal separator is `.` everywhere.** The existing `Locale.ROOT`/`Locale.US` formatting is untouched.
5. **Reviewers exist but aren't reliable.** Claude drafts all three languages, and they ship marked "beta" in the language picker until a native speaker signs off. Review must cost a reviewer almost nothing (phase 5). An unreviewed language never blocks a release.
6. **Timing:** v1.1.

## What exists today

- **No i18n infrastructure.** No string tables, no `ResourceBundle`, no locale setting. Every UI string is a literal in Kotlin.
- **Size:** 577 Kotlin files / 110k lines. About 1,400 unique capitalized string literals in `src/main/kotlin` (a lower bound). Realistic estimate: **1,800–2,500 strings**. 272 `ImGui.menuItem`, ~250 `text*`, 42 `combo`, 21 modals, plus tooltips via `TooltipHelper`.
- **ImGui IDs are tied to labels.** 507 literals use `label##id`; only 10 use `###`. With `##`, the ID hashes the visible text too, so a translated label would break popups, open/closed state and `imgui.ini` window positions. Every translated label moves to `label###stableId`.
- **Fonts are fine.** `UITheme.MAIN_RANGES` already loads Latin-1, Latin Extended-A and General Punctuation: ñ ¿ ¡ ß ä ö ü é ç œ « » and U+202F are all covered.
- **Content metadata:** ~467 `LABEL`/`DESCRIPTION` entries across 34 sources, 27 filters and 8 transitions. Editing shipped shaders changes `shipped_source_hashes.txt`, so translations go in side tables, never in the shader files.

## Always English, whatever the language setting

OSC addresses, MIDI profile ids, preset/library/perform-page file keys, CLI flags, log output, crash reports, developer docs, `DECISIONS.md`, release notes (for now), user-named things (presets, chains, playlists), and every content name plus abbreviation covered by decisions 2–3.

## Architecture

**One JSON table per language plus a generated Kotlin accessor.**

```
src/main/resources/i18n/
  en.json      source of truth, every key has a _comment
  es.json  de.json  fr.json
  xx.json      pseudo-locale, generated, not committed
```

`en.json` shape (flat dotted keys, grouped by area):

```json
{
  "menu.file.save": "Save",
  "menu.file.save._comment": "File menu. Saves the current set.",
  "deck.die.tip": "Roll a random {kind} for deck {deck}",
  "deck.die.tip._comment": "{kind} = source / FX chain (already translated), {deck} = A-D",
  "library.items": { "one": "{count} item", "other": "{count} items" }
}
```

- **Codegen:** a Gradle task (`generateStrings`, wired before `compileKotlin`) reads `en.json` and writes `build/generated/.../Str.kt`. Keys become properties (`Str.menu.file.save`) and placeholders become typed functions (`Str.deck.die.tip(kind, deck)`), so a typo or a missing argument fails at compile time.
- **Runtime `I18n` object:** holds the active table plus the English table. A lookup tries the active language, then English, then returns the key itself (loud, so it's easy to see in testing). No allocation per frame: each formatted string is cached until the language or its arguments change. Hot paths (perform matrix, knobs) use plain lookups without arguments.
- **Formatter:** `{name}` placeholders plus `one`/`other` plurals. The French rule treats 0 as `one`; ES and DE use the English rule. No ICU4J.
- **Labels:** a helper `L(key, id)` returns `"<text>###<id>"`. The id stays the old English literal, so existing `imgui.ini` positions and saved UI state survive the migration.
- **Live switching:** set `I18n.active`; the next frame redraws in the new language. Widths cached by `PerfRowGeometry`/`KnobSpec` must be invalidated when the language changes.
- **Selecting the language:** first run follows the OS locale (`es`/`de`/`fr` if it matches, else `en`); Preferences → Language picker showing each language's name in that language ("Deutsch (beta)"); `--lang=xx` overrides for screenshots and testing. Saved in `lsd-preferences.properties`.
- **Tests:**
  - every key in a translation exists in `en.json`, and its placeholders match exactly;
  - plural keys have both `one` and `other`;
  - untranslated keys are reported per language, without failing;
  - a source-scan test flags string literals passed to ImGui label/text calls in `ui/`, with an allowlist for symbols, units and the abbreviations from decision 3.

## Phases

### 0. Glossary (before any translation)
Draft below. Decide one term per concept and per language, then never vary it. The glossary lives at `src/main/resources/i18n/GLOSSARY.md`, and each translation pass is prompted with it.

### 1. Infrastructure (~2–3 sessions)
Loader, codegen, formatter, plurals, fallback, `L()` helper, Preferences picker, `--lang`, tests, the pseudo-locale generator. The pseudo-locale (`xx`): accented and stretched 40%, e.g. `[Šàvé ~~~~]`; a string still in plain English is one that wasn't extracted.
Migrate one small panel end to end (Preferences) as the reference pattern.

### 2. Extraction (~10–15 sessions, the bulk)
English only, no visible change. One panel per commit, each checked in the app under `--lang=xx`. Order:
1. menus and Preferences
2. perform matrix: row titles, badges, pills, Clock/Master rows
3. Browse/Edit bay, BrowserDock, the Library and queues
4. modulation: Seq/LFO/Audio tabs, macro editor
5. modals, confirmations, errors and toasts
6. tooltips (largest by word count; keep the text, add `_comment`)
7. MIDI/OSC/controller UI text (labels only, never addresses)

Each commit converts `##` to `###` for the labels it touches. The source-scan test starts with an allowlist of files not yet migrated, which shrinks with each commit.

### 3. Layout robustness (~2–3 sessions)
Run the whole UI under `xx` at 1280×720 and in German. Fix pattern, in order of preference:
1. let the widget grow when the geometry allows it (layout must not change with mode — see perf-row-layout-stability);
2. measure the text, ellipsize it, and show the full text in a tooltip (one shared helper);
3. a shorter translation, recorded in `_comment` as a width limit ("max ~8 chars").

Known risks: row-title badges, header pills, slot cells, `KnobSpec` caption widths, combo widths, the 1280×720 minimum.

### 4. Content metadata (~1–2 sessions)
Side tables `i18n/content/<lang>.json` keyed `source.<id>.<param>.label` / `.description`, filled by a script that dumps the ISF headers. The Properties panel and knob captions look up the table and fall back to the ISF text. User-imported ISF files keep their own text. Names stay English (decision 2).

### 5. Translation and low-friction review
- Claude drafts `es`/`de`/`fr` from the glossary and `_comment`s; ship them as beta.
- **The review kit for unreliable reviewers**, generated per language by a script:
  - a single HTML page per language: key area, English, translation, comment, and a screenshot of the panel where the string appears (from the existing screenshot automation with `--lang=<lang>`), split into short chunks ("Menus — 5 min");
  - the reviewer marks lines wrong, or writes a fix, in any form: email, photo, a marked-up PDF. Claude merges the fixes;
  - a partial review is still worth having; progress is tracked per area in `GLOSSARY.md`.
- "beta" comes off a language once its menus, perform matrix and Preferences are reviewed; tooltips can lag.

### 6. Later surfaces (separate follow-up releases)
User guide via `mkdocs-static-i18n`; tour captions as per-language VTT (the scripts in `tours/` already separate captions from actions); web TV strings using the same JSON format; website templates.

### 7. Ongoing workflow
The repo's done rule becomes: code + both release-notes files + docs + tooltips + **an en.json key with a `_comment`**. New keys land in English only; a translation batch before each release fills es/de/fr, and the untranslated-keys report says what's left.

## Glossary draft

Content names and the abbreviations from decision 3 aren't in this table: they stay English.

| English | Español | Deutsch | Français | Note |
|---|---|---|---|---|
| Deck | Deck | Deck | Deck | DJ term, same in all three |
| Source | Fuente | Quelle | Source | |
| Effect / FX | Efecto | Effekt | Effet | "FX" abbreviation stays |
| FX chain | Cadena de efectos | Effektkette | Chaîne d'effets | |
| Transition | Transición | Übergang | Transition | |
| Crossfader | Crossfader | Crossfader | Crossfader | DJ term |
| Macro | Macro | Makro | Macro | |
| Super Knob | Super Knob | Super Knob | Super Knob | product term, keep |
| Metaknob | Metaknob | Metaknob | Metaknob | product term, keep |
| Knob | Perilla | Regler | Potard / Bouton | FR: confirm with reviewer |
| Library | Biblioteca | Bibliothek | Bibliothèque | |
| Queue | Cola | Warteschlange | File d'attente | DE is long: watch width |
| Preset | Preset | Preset | Préréglage | |
| Perform / Edit view | Actuar / Editar | Performance / Bearbeiten | Jouer / Éditer | view names, confirm |
| Browse | Explorar | Durchsuchen | Parcourir | |
| Bypass | Bypass | Bypass | Bypass | audio term |
| Dry/Wet | Seco/Húmedo | Dry/Wet | Sec/Mouillé | DE keeps English by convention |
| Tap tempo | Tap tempo | Tap-Tempo | Tap tempo | |
| Breakdown / Drop | Breakdown / Drop | Breakdown / Drop | Breakdown / Drop | dance-music terms |
| Modulator | Modulador | Modulator | Modulateur | |
| Step sequencer | Secuenciador por pasos | Step-Sequencer | Séquenceur pas à pas | |
| Learn (MIDI/OSC) | Aprender | Lernen | Apprendre | |
| Output | Salida | Ausgang | Sortie | |
| Record | Grabar | Aufnehmen | Enregistrer | |

Register: informal `tú` (es), `du` (de), and `vous` (fr). Most creative tools use these; confirm with reviewers.
