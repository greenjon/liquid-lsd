# Web TV overnight batch (2026-10-23)

Unattended batch. Rules for the run:
- One commit per item on `main`, explicit paths only (other agents share the tree). Never push.
- Every item: unit tests + `ui_check.mjs` + a render-check subset pass before committing; release notes
  (both files), web docs (developer + user guide) and the in-app checklist updated in the same commit.
- Stop an item (leave it uncommitted and note why in "Morning report") if it needs a design call not
  covered here. Do not touch files another agent has modified.
- Everything stays audio responsive; nothing flashes faster than a beat.

Decisions (2026-10-23): broadcaster name = desktop preference + optional wire field; new settings live
in a TV service MENU. Not in this batch: track-change looks (shows run 2-7 h), downbeat detection
(desktop first), channels (needs a design call), viewer count (low priority).

## Order

1. **Wake lock** (#6). `navigator.wakeLock.request('screen')` while powered on; re-acquire on
   `visibilitychange`; release on power off. No-op where unsupported.
2. **Media Session** (#7). Title = show title from Icecast, artist "Spaz Radio", artwork = icon-512;
   handlers: play/pause = power, nexttrack = CH up, previoustrack = CH down (locked while live).
3. **PWA + kiosk** (#8). `manifest.webmanifest` (fullscreen display, icons), a small service worker
   that caches the app shell only (never the stream, presets fetched network-first so updates land);
   `?kiosk`: no TV body, picture fills the window, auto power-on attempt (click-to-start fallback),
   controls via keyboard / swipe, OSD kept.
4. **TV service MENU** (#2 + #10 home). MENU button + `M` key: CRT-green on-screen menu over the
   picture, arrows / clicks / wheel: SYNC (-100..+400 ms), REACT, TIME, Reduced motion
   (auto / on / off), Heavy looks (auto / on / off), Reset. Stored per viewer (localStorage).
   Locked items greyed while live.
5. **SYNC offset** (#2). Shifts the beat clock and the audio signals the picture reads by the offset
   (delay line on cvState band values; beat clock read at now - offset). Optional "tap to the beat"
   calibration in the menu: 8 taps -> median offset against the locked clock.
6. **Reduced motion** (#10). Auto from `prefers-reduced-motion`. When on: REACT capped at 60%, no
   `hard` looks, no drop cut (calm change instead), fades doubled, no flash transitions.
7. **Tempo-aware picks** (#4). Tracked BPM (when locked) biases the next pick: < 95 BPM favours
   ambient / groove / mid, 95-125 groove, > 125 hard / kick / hat. A bias, not a filter (weighted
   random), so variety stays. Unit-tested weights.
8. **No doubled reactions** (#5). Chain choice avoids chains whose signal tags (kick / hat / bass /
   mid) duplicate the preset's dominant one; prefers a chain that answers a different band.
   Extends `pairing.js`; content test asserts every rotation preset still has a fitting chain.
9. **LIVE title card** (#12). Desktop: `BroadcastPreferences.broadcasterName` (Web Broadcast
   prefs tab, tooltip), sent as optional `state_full.broadcaster` (protocol stays v2; relay passes
   it through and keeps it in merged state). Web: on going live, a 4 s CRT title card
   "LIVE · <name>" (or "LIVE"), and the OSD shows the name. Kotlin unit test for the serializer,
   server test for the relay, ui_check for the card. Kept to the broadcast files only.

## Morning report
(filled in during the run: what landed, commit ids, anything skipped and why, what to check by hand)
