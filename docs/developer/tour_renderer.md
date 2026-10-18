# Tour renderer (`--tour`)

A deterministic, scripted render of the real UI for the walkthrough video, chapter clips and the interactive tour page. The app runs on a fixed 60 fps simulated clock at 1920x1080, a synthetic cursor drives the UI, and every frame of FBO 0 is read back and piped to ffmpeg (libx264). Same script + same library + same machine/GPU = bit-identical frames.

```
./gradlew run --args="--tour=scripts/tour_phase3_test.json --tour-out=/tmp/t/raw.mp4 --no-audio [--tour-hidden] [--tour-dump=<dir>:70,81]"
scripts/render_tour.sh <script.json> <out-dir> [--tour-hidden]   # hero.mp4/.webm, poster, .vtt, captions.json, chapters.json, clips/
scripts/tour_determinism_check.sh <script.json> --tour-hidden    # renders twice, compares per-frame SHA-256
```

Tour mode isolates itself: preferences are not read or written, MIDI/OSC/Link/watchers/live audio are off, `imgui.ini` is not used, real GLFW input is ignored, randomness comes from `DeterministicRandom(seed)`. Library contents are part of the input: pin a small curated library for the final render.

## Script format (`tour/TourScript.kt`)
Top level: `durationSec`, `seed`, `audio` (file fed to the analyzer by frame clock and muxed), `bpm` (manual beat clock), `burnCaptions` (draw captions into the video; the `.vtt` is always written), `chapters` (`{t, id, title, endSec?, clipSec?}`), `steps`.
A step starts at `t` seconds. `caption` on any step becomes a cue lasting until the next captioned step.

| Action | Fields |
|---|---|
| `move` | `target` (widget key) or `x,y`; `durationSec`. Also draws a highlight ring on the target. |
| `click` / `drag` | click at the cursor; drag presses, travels `dx,dy` over `durationSec`, releases (aim with a `move` first) |
| `hold` | caption only |
| `set_page` | `target` = `ab`, `bgpv`, `mixer` |
| `library_mode`, `theme`, `preferences`, `preferences_close` | `FULL`/`HALF`; `light`/`dark`; category name |
| `open_params`, `open_params_pair`, `focus_pair`, `close_focus` | module id / pair tag |
| `load_source`, `load_preset` | `deck` A/B/BG/PV, `target` = source id / `.lsd` path |
| `set_param`, `bind_modulator` | `deck`, `param`, `value`; modulator `target` = CV source id, `unit` BEAT/TIME |
| `fx_filter`, `fx_enable`, `fx_link`, `fx_param`, `meta_knob` | `deck` (A/B/BG/PV/MASTER), `slot` 0-2, `target`/`value`/`param` |
| `fx_chain`, `fx_clear`, `fx_dry_wet`, `super_knob` | `deck`, `.lsdfxchain` path / `value` |
| `transition`, `transition_preset`, `crossfade` | stock id, `.lsdtrans` path, `value` -1..1 |

Unknown targets print `TOUR_WARN` and are skipped; treat any warning as a script bug.

## Widget keys (`tour/WidgetRegistry.kt`)
Call sites do `WidgetRegistry.note { key }` right after the ImGui item (free outside tour mode). Keys: `perf_tab_<page>`, `perf_<page>_r<row>_c<col>` (knobs), `perf_gen_badge_<tag>`, `perf_src_more_/save_/eject_/rand_<tag>`, `rack_chevron_<id>`, `crossfader_track`, `fxcell_<bank>_<slot>`, `fxbypass_`/`fxlink_<bank>_<slot>`, `fxchain_name_<bank>`, `fxpill_<bank>_<slot>`, `browser_tab_<mode>`, `browser_search`, `prefs_cat_<category>`, `ui_theme`. Custom-drawn items without an ImGui item (deck title badges, knob strip labels) need `x,y` or a new explicit rect. Do not key on `##title_badge_*_x_y` (coordinates in the id).

## Outputs next to `--tour-out`
`.hashes` (per frame), `.vtt`, `.captions.json` (index/start/end/chapter/text for the web page and transcript), `.chapters.json`, `.ffmeta` (ffmpeg chapters). `render_tour.sh` derives the rest.

Known leftovers: wall-clock use in `FileSystemManager`, DeckOps autosave names, `PlaylistManager`, `PerformanceStats`, `TouchConsoleController` (no pixel effect so far); other GPUs/compositors unverified.
