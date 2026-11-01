# Web TV info panel (OSD) re-layout — plan

Status: DONE 2026-11-01 (TV v10). Background line flashes on its own swap only with ANNOUNCE SCENES on.

## Target layout

```
♪ SHOW / TRACK NAME                      CH 07
NEXT: Show · Sat 9pm

              HOLD            <- control messages, centred

SCENE: Beat Spiral
BACKGROUND: Ambient Nebula
FX: Kaleido Bloom
LIQUID LSD · liquid-lsd.greenjon.com   (only on I / picture click, as now)
```

## Decisions

- Top left: the ♪ now-playing line (Spaz Radio show/track, `#osd-track`), with `#osd-next` under it.
- Top right: `CH nn` stays.
- Control messages (`#osd-msg`: HOLD, VOL, BARS, SYNC, NO SIGNAL…) move to the centre of the picture.
- Bottom left, all three lines the same size: `SCENE:` (the preset name, as `#osd-name` shows today,
  not the generator), `BACKGROUND:` (the background playlist's preset name), `FX:` (the foreground
  deck's chain; `FX: NONE` when there is none).
- The background's own FX chain is NOT shown.
- Live broadcast: unchanged in spirit: `LIVE` top right, broadcaster in the SCENE slot (unlabelled or
  `LIVE:`), no BACKGROUND / FX lines (we don't know the desktop's parts).

## Steps

1. **autopilot.js**: expose the background's name. `prepareBackground` / the startup load keep the
   playlist line; add `background` (preset name from `bgPlaylist[bgPlaylistIdx]`, taken at the swap,
   not at prepare time) to `nowShowing()`. Optionally dispatch `lsd-background-changed` at the swap.
2. **index.html**: split the OSD into a top-left block (`#osd-track`, `#osd-next`) and the bottom
   block (`#osd-name`, new `#osd-bg`, `#osd-chain`, `#osd-brand`). Keep the existing ids so
   `web/checks/ui_check.html` and the `flash()` calls keep working.
3. **tv.css**: top-left block at `top: 5%; left: 5%`, right edge clear of `#osd-ch` (ellipsis on long
   track names); `#osd-msg` centred (`inset: 0; display: grid; place-items: center` or
   `top: 45%; text-align: center`); bottom lines one size (drop `#osd-name`'s 1.5em). Check the
   narrow-screen and fullscreen font clamps, and that `.card-up` / menu / ABOUT / SCHEDULE still hide it.
4. **ui.js `showLook()`**: write `SCENE: …`, `BACKGROUND: …`, `FX: …`; live branch as above.
   Open question for build time: flash only the BACKGROUND line when the background swaps on its own
   timer (suggested yes, only if ANNOUNCE SCENES is on, same as scene changes).
5. **Checks**: update `web/checks/ui_check.html` text matches (`I names the scene` etc.), add
   "I shows the background". Run ui_check + embed_check.
6. **Docs**: both release-notes files, `docs/user_guide/web_broadcast.md` (OSD description),
   `docs/developer/web_subsystem.md`, regenerate HTML docs (`./gradlew generateDocs`), MENU help for
   ANNOUNCE SCENES if its wording names the lines. Bump `TV_VERSION`.

## Notes

- Background: separate playlist (`default_bg.lsdplay`), own clock at 2× the TIME dial
  (`bgHoldBars`), dip-to-black swaps, not frozen by HOLD, gets its own FX chain from `pickChain`.
- `body.embed` hides the OSD entirely, so the Spaz Radio app is unaffected.
