# Spaz gallery background

Trialled on branch `trial/spaz-gallery-bg`, then (2026-11-03) made the default background, merged to main, TV v14:
first line of `default_bg.lsdplay`, BG CHANGES defaults to HOLD; the `?bg=gallery` flag is gone.

## The legacy page (www.spaz.org/radio)

- The server puts 748 listener uploads ("Add Pic", `gallery/uploader.php`) from `/images/ground/` into the page as
  `imageURLs.ground` (alphabetical). The folder cannot be listed (403). Images send `Access-Control-Allow-Origin: *`.
- Loads them one after another (each `onload` starts the next) and keeps every one: 500 MB+, off on mobile.
- `groundCycler`: every 0.5-7.5 s a random photo fades in over 0-10 s while jQuery grows it 3000 -> 8000 px square;
  CSS `lazy` turns it 0 -> 200 deg every 10 s, then snaps back; the turn's centre starts near the screen centre
  and runs off down-right as it grows, so the photo swings across. The previous photo fades out over the same time.
- `#bg`: a tumblr GIF at 300%, turning backwards once per 50 s (probably a dead link).

## The trial

- `scripts/gen_gallery_manifest.py` -> `web/gallery.json`: the 200 oldest uploads by Last-Modified (2013-05 .. 2017-03),
  none over 1.5 MB (162 of the 748 are bigger originals). User call: early uploads were more curated.
- `web/gallery.js`: source type `gallery`, drawn like `mandala` (outside the ISF graph) then the deck's FX chain.
  Smooth version: a new photo every 2-8 beats on the beat, fades 6-16 beats, slow zoom (log growth) and turn around
  an off-centre pivot that drifts, newest on top, aspect kept, soft edges, up to 4 on screen. 8 photos on the GPU,
  scaled to 1024 px; 3 at a time at start, then one new every 20 s.
- `presets/spaz-gallery.lsd`, first line of `playlists/default_bg.lsdplay`. Background FX chains are picked as usual
  (EFFECTS OFF in the menu shows it clean).
- Not `web-` prefixed, so the roll/reactivity sweeps skip it; validate_content knows `gallery` (no parameters); the
  rotation reactivity gate skips source `gallery`; render_check settles it 7 s.
- Curating: the user's picks go in `web/gallery.json` `images` (file names under `base`); order does not matter
  (gallery.js shuffles).

## Open

- "Jerkier" (old snap-back turn, square stretch, faster swaps) only if asked.
- User will hand-curate the 700+ uploads to 100-300 favourites: then gallery.json becomes that list (hand-edited
  or a curated-list input to gen_gallery_manifest.py), not the oldest 200.
- Android data use (~1 photo per 20 s, median 130 kB); a live endpoint on spaz.org for new uploads.
- Music reaction (beat zoom bump, energy -> turn speed, drop -> cut to a new photo) is a possible step 2.
