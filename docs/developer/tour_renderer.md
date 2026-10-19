# Tour renderer (`--tour`)

A deterministic, scripted render of the real UI for the walkthrough video, chapter clips and the interactive tour page. The app runs on a fixed 60 fps simulated clock at 1920x1080, a synthetic cursor drives the UI, and every frame of FBO 0 is read back and piped to ffmpeg (libx264). Same script + same library + same machine/GPU = bit-identical frames.

```
./gradlew run --args="--tour=scripts/tour_phase3_test.json --tour-out=/tmp/t/raw.mp4 --no-audio [--tour-hidden] [--tour-dump=<dir>:70,81]"
scripts/render_tour.sh --draft tours/overview.json tours/draft/overview --tour-hidden   # while writing: a few minutes for the 5 min overview
scripts/render_tour.sh [--webm] tours/overview.json tours/out/overview --tour-hidden     # final: hero.mp4 (+ .webm), poster, .vtt, captions.json, chapters.json, clips/
scripts/render_tour.sh [--draft] --all tours/out --tour-hidden                           # every tours/*.json into tours/out/<name>/
scripts/tour_determinism_check.sh <script.json> --tour-hidden [--tour-draft]   # renders twice, compares per-frame SHA-256
```

Render settings come from the command line, not the script: `--tour-fps=<n>` (default 60), `--tour-scale=<f>` (the UI is always laid out at 1920x1080; the frame is shrunk on the GPU before readback, so targets never move), `--tour-encoder=x264|nvenc` (default x264), `--tour-hashes` (per-frame SHA-256, about 5 ms a frame at 1080p). `--tour-draft` = 30 fps, scale 0.5, NVENC, no hashes. On the dev laptop (RTX A2000) a final frame costs about 20 ms (app and UI about 6, hash 5, x264 4, readback 3) and a draft frame about 11 ms, at half the frame count. Drafts are deterministic too (raw and NVENC-encoded frames, checked on this machine). `TOUR_GL` names the GPU the app rendered on and `TOUR_TIMING` gives the per-frame breakdown. VP9 has no hardware encoder here, so `--webm` is opt-in and slow.

Tour mode isolates itself: preferences are not read or written, MIDI/OSC/Link/watchers/live audio are off, `imgui.ini` is not used, real GLFW input is ignored, randomness comes from `DeterministicRandom(seed)`. Library contents are part of the input: pin a small curated library for the final render.

## Script format (`tour/TourScript.kt`)
Top level: `durationSec` (optional: defaults to the last step's end plus `tailSec`), `seed`, `audio` (file fed to the analyzer by frame clock and muxed), `bpm` (manual beat clock), `burnCaptions` (draw captions into the video; the `.vtt` is always written), `captionStyle` (`cursor`, the default, or `bottom`), `tooltips` (ImGui hover tooltips, off by default so they never collide with the caption box), `chapters` (`{t, id, title, endSec?, clipSec?}`, or use `chapter` steps), `steps`.

Timing: a step starts at absolute `t` seconds, or `dt` seconds after the previous step (`dt` alone is the easiest to edit: inserting a step shifts everything after it).
Captions: `caption` on any step becomes a cue lasting until the next captioned step (`""` clears). `{ch:<id>}` in a caption becomes "Ch. N at m:ss". With `captionStyle: cursor` the caption is an opaque box beside the last `move` target, below it by default; `captionPos` (`above`, `left`, `right`) on the captioned step keeps it clear of what the step shows. The box stays put during drags and clicks.

Checks printed as `TOUR_WARN`: a caption shown for less than its reading time (14 characters per second, at least 2.5 s), a target that was not drawn in the previous frame (wrong page or view, or a move on the same frame as a page switch: wait about 0.4 s), unknown ids.

| Action | Fields |
|---|---|
| `move` | `target` (widget key) or `x,y`; `durationSec`. Also draws a highlight ring on the target. |
| `click` / `drag` | click at the cursor; drag presses, travels `dx,dy` over `durationSec`, releases (aim with a `move` first) |
| `hold` | caption only |
| `chapter` | `target` = id, `title`, `clipSec` |
| `tooltips` | `value` 0 or 1 |
| `set_page` | `target` = `ab`, `bgpv`, `mixer` |
| `library_mode`, `theme`, `preferences`, `preferences_close` | `FULL`/`HALF`; `light`/`dark`; category name |
| `open_params`, `open_params_pair`, `focus_pair`, `close_focus` | module id / pair tag |
| `load_source`, `load_preset` | `deck` A/B/BG/PV, `target` = source id / `.lsd` path |
| `set_param`, `bind_modulator` | `deck`, `param`, `value`; modulator `target` = CV source id, `unit` BEAT/TIME |
| `fx_filter`, `fx_enable`, `fx_link`, `fx_param`, `meta_knob` | `deck` (A/B/BG/PV/MASTER), `slot` 0-2, `target`/`value`/`param` |
| `fx_chain_enable` | `deck`, `value` 0 or 1: the chain's BYPASS switch (0 = bypassed) |
| `fx_swap` | `deck`, `slot`, `slot2` (a real drag of one slot cell onto another does the same through the UI) |
| `fx_chain`, `fx_clear`, `fx_dry_wet`, `super_knob` | `deck`, `.lsdfxchain` path / `value`. `super_knob` and `meta_knob` set the FX row's macro knob, as a hand would; soft takeover still applies |
| `transition`, `transition_preset`, `crossfade` | stock id, `.lsdtrans` path, `value` -1..1 |

Unknown targets print `TOUR_WARN` and are skipped; treat any warning as a script bug.

## Widget keys (`tour/WidgetRegistry.kt`)
Call sites do `WidgetRegistry.note { key }` right after the ImGui item (free outside tour mode). Keys: `perf_tab_<page>`, `perf_<page>_r<row>_c<col>` (knobs), `perf_gen_badge_<tag>`, `perf_src_more_/save_/eject_/rand_<tag>`, `rack_chevron_<id>`, `crossfader_track`, `fxcell_<bank>_<slot>`, `fxbypass_`/`fxlink_<bank>_<slot>`, `fxchain_name_<bank>`, `fxpill_<bank>_<slot>`, `fxchainlink_<bank>`, `fxchain_bypass_<A|B|BG|PV|MST>` (chain BYPASS), `browser_tab_<mode>`, `browser_search`, `browser_row_<src|fx|trans>_<stock id or file name without extension>` (list rows; only rows scrolled into view are clickable), `deck_fader_<A|B|BG|PV>` (preview faders), `prefs_cat_<category>`, `ui_theme`, `edit_side_<tab>`, `macro_name_<bank>_<label>`. `<out>.keys` lists every key seen during a render, handy when authoring. Custom-drawn items without an ImGui item (deck title badges, knob strip labels) need `x,y` or a new explicit rect. Do not key on `##title_badge_*_x_y` (coordinates in the id).

## Outputs next to `--tour-out`
`.hashes` (per frame), `.vtt`, `.captions.json` (index/start/end/chapter/text for the web page and transcript), `.chapters.json`, `.ffmeta` (ffmpeg chapters). `render_tour.sh` derives the rest.

Known leftovers: wall-clock use in `FileSystemManager`, DeckOps autosave names, `PlaylistManager`, `PerformanceStats`, `TouchConsoleController` (no pixel effect so far); other GPUs/compositors unverified.

## The walkthrough videos
One script per video in `tours/`: `overview.json` (six chapters, about 5 minutes) now, deep dives (Modulation, FX chains, Mixing and transitions, Library and presets, Hardware, Output) to follow, each embedded on its user-guide page. A UI change re-renders only the videos it touches. In a deck's bound Browse (click the source name) a single click on a row loads it, so scripts show loads with `move` + `click` on `browser_row_*` keys instead of `load_source`. Their soundtrack `tours/beat.wav` is a generated placeholder (`scripts/make_tour_beat.sh`, not committed) until the licensed track arrives; the script only names the file, so swapping it changes nothing else.
