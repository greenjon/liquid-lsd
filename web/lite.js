// lite.js — the TV's lite picture: a Spaz Radio collage without WebGL, for devices that cannot run
// the visuals (no WebGL 2, or the renderer failed to start) and for viewers who pick it (MENU › TV › VISUALS,
// or ?lite=1 / ?lite=0 in the address, which wins over the saved choice; ?lite=0 also keeps the visuals on a
// device renderer.js finds too slow, which otherwise switches to lite by itself and remembers it).
//
// The look is the old spaz.org/radio page's three layers, smoothed: skies at the back (slow, screen-filling,
// long crossfades), listener photos over them (smaller than the screen, so the sky shows around them; each
// turns and grows around a point off its middle, as gallery.js's photos do) and Spaz logos in front (turning
// around their middles). Every picture is an <img> moved only by the Web Animations API on `transform` and
// `opacity`, so the browser's compositor animates it and the script wakes only to start the next one. Each
// <img> is laid out at the picture's own size and scaled by the transform, so the GPU holds a small bitmap.
// Skies are zoomed in far enough that no edge shows however far they turn; photos fade out towards their edges;
// logos come at two to four times their old size (the one opaque logo excepted), so they fill more of the screen.
//
// The sound moves it too, still without a frame loop: a new picture waits for the beat (photos) or the bar (skies
// and logos) while the beat is locked; each layer gets a short scale pulse on every beat, stronger on the bar,
// as the music gets louder and for a few bars after a drop (which also brings in a logo); and the skies brighten
// and dim with the music's energy. Each pulse is a Web Animation scheduled ahead (its `delay` lands it on the
// beat), so the compositor plays it and the 20 Hz tick only has to book the next one.
//
// Bandwidth: everything comes from gallery.json (scripts/gallery_curate.py): photos from its `lite` folder
// (512 px JPEGs, about 35 KB each), skies from sky.base (up to 1024 px, about 100 KB: they are zoomed in a long
// way, and a new one comes only about once a minute), logos from logo.base (512 px PNGs), falling back to the
// other folders. A layer fetches one picture only when its next one is due, and none while the TV is
// off or the tab is hidden. A gallery.json without skies or logos just leaves that layer empty.
//
// No renderer runs in lite mode, so this also ticks the audio analysis (dsp.js `tick`: stream watchdog, beat
// tracking for the beat LED, tempo and the pulses; not the shaders' spectrum) and stands in for the renderer's
// power-on/off bookkeeping.

import { tick, cvState } from './dsp.js';
import { heardBeats } from './sync.js';
import { autopilotState } from './autopilot.js';

const KEY = 'lsd.visuals';     // 'full' | 'lite' (picked) | 'lite-auto' (too slow on an earlier visit); JSON
const TICK_MS = 50;            // audio analysis rate without a render loop
const FAIL_WAIT_MS = 20000;    // after a run of failed photos (offline), wait this long
const BAR = 4;                 // beats to a bar (counted from the beat clock: the web TV finds no downbeat)
const DROP_BEATS = 16;         // stronger pulses for this long after a drop
// Scale pulse per layer at full loudness (1 + this on the beat, back to 1 within it); the bar's is 1.6 times.
const PULSE = { sky: 0.006, photo: 0.025, logo: 0.04 };

const rand = (a, b) => a + Math.random() * (b - a);

/** Lite mode: `on` while the slideshow is the picture; `reason` 'chosen' (the viewer's pick), 'slow' (renderer.js
 *  measured it, now or on an earlier visit), 'no-webgl' or 'error'. */
export const liteState = { on: false, reason: null };

// Set by renderer.js (initLite): ui.js's power and viewer state (ui.js imports this module, so they are passed
// in), and the visuals' pause() / run() (run starts them the first time, resumes them after).
let deps = null;
export function initLite(d) { deps = d; }

/** Switching is possible here: the visuals can run (only the browser's own fallbacks cannot go back). */
export const canSwitch = () => !liteState.on || liteState.reason === 'chosen' || liteState.reason === 'slow';

/** 'full', 'lite' or 'lite-auto': the address (?lite=1 / ?lite=0) first, then the saved choice; 'full' by default.
 *  index.html's startup preloads read the same (inline, before the modules). */
export function visualsChoice() {
  const q = new URLSearchParams(globalThis.location?.search ?? '').get('lite');
  if (q === '1') return 'lite';
  if (q === '0') return 'full';
  try {
    const v = JSON.parse(localStorage.getItem(KEY));
    return v === 'lite' || v === 'lite-auto' ? v : 'full';
  } catch { return 'full'; }
}

/** This device was too slow for the visuals: start the next visit in lite mode (until VISUALS › FULL). */
export function rememberSlow() {
  try { localStorage.setItem(KEY, JSON.stringify('lite-auto')); } catch { /* not remembered */ }
}

/** The viewer's pick (MENU › TV › VISUALS), saved and switched to at once: the sound plays on, the TV stays on. */
export function setVisuals(choice) {
  try { localStorage.setItem(KEY, JSON.stringify(choice)); } catch { /* private window: this visit only */ }
  const parts = location.search.slice(1).split('&').filter(Boolean);
  const kept = parts.filter((p) => !/^lite(=|$)/.test(p));
  if (kept.length !== parts.length) {     // the address would override the pick on a reload
    history.replaceState(history.state, '', `${location.pathname}${kept.length ? `?${kept.join('&')}` : ''}${location.hash}`);
  }
  if (choice === 'lite') startLite('chosen');
  else stopLite();
}

let show = null;

/** Starts the slideshow and pauses the visuals (if they ever ran). */
export function startLite(reason) {
  if (liteState.on) return;
  liteState.on = true;
  liteState.reason = reason;
  deps.pause?.();
  document.body.classList.add('lite');
  const screen = document.getElementById('tv-screen-area');
  const layer = document.createElement('div');
  layer.id = 'lite-layer';
  layer.setAttribute('aria-hidden', 'true');
  screen.querySelector('#glCanvas').after(layer);
  show = new Slideshow(layer, deps.powerState, deps.viewerState);
  show.start();
  window.dispatchEvent(new CustomEvent('lsd-lite', { detail: { on: true, reason } }));
}

/** Back to the visuals: the slideshow goes, and the visuals start (the first time) or carry on. */
export function stopLite() {
  if (!liteState.on || !canSwitch()) return;
  liteState.on = false;
  liteState.reason = null;
  show.stop();
  show = null;
  document.body.classList.remove('lite');
  window.dispatchEvent(new CustomEvent('lsd-lite', { detail: { on: false } }));
  deps.run();
}

/** CH ▲▼ in lite mode: the next photo now. */
export function liteNext() { show?.spawnNow(); }

/**
 * How each layer moves, in beats. `look(W, H, nw, nh, calm)` places one picture: its height on screen at the
 * start (screen heights), growth, turn and the point it turns around.
 */
const LANES = {
  sky: {
    max: 2,
    on: 'bar',
    cover: true,                                            // zoomed until no edge shows (Lane.spawn)
    gap: () => rand(24, 48),
    times: () => [rand(12, 24), rand(48, 96), rand(12, 24)],
    look: (W, H, pa, calm) => ({
      s0: 0,                                                 // set by the cover rule
      grow: rand(0.001, 0.003) * (calm ? 0.5 : 1),
      turn: (calm ? 0.4 : 1) * rand(0.3, 0.8) * (Math.random() < 0.5 ? -1 : 1),
      u: rand(0.45, 0.55), v: rand(0.45, 0.55),              // near its middle: the least zoom for a full turn
      px: rand(-0.05, 0.05), py: rand(-0.05, 0.05), drift: 0.0005,
    }),
  },
  photo: {
    max: 2,
    on: 'beat',
    gap: () => rand(4, 10),
    times: () => [rand(6, 16), rand(24, 48), rand(6, 16)],
    look: (W, H, pa, calm) => ({
      s0: Math.min(rand(0.55, 0.85), 0.9 * (W / H) / pa),   // smaller than the screen: the sky shows round it
      grow: rand(0.004, 0.01) * (calm ? 0.5 : 1),
      turn: (calm ? 0.4 : 1) * rand(1.5, 4) * (Math.random() < 0.5 ? -1 : 1),
      u: rand(0.3, 0.7), v: rand(0.3, 0.7),
      px: rand(-0.5, 0.5), py: rand(-0.4, 0.4), drift: 0.004,
    }),
  },
  logo: {
    max: 1,
    on: 'bar',
    gap: () => rand(12, 24),
    times: () => [rand(4, 8), rand(16, 32), rand(4, 8)],
    look: (W, H, pa, calm, opaque) => ({
      // Two to four times the old page's size, past the screen's edges at the top end; the opaque one (a
      // picture on its own square) stays small enough to fit, so it never blots out everything behind it.
      s0: opaque ? Math.min(rand(0.3, 0.55), 0.8 * (W / H) / pa) : rand(0.3, 0.55) * rand(2, 4),
      grow: rand(0.002, 0.006) * (calm ? 0.5 : 1),
      turn: (calm ? 0.4 : 1) * rand(3, 8) * (Math.random() < 0.5 ? -1 : 1),
      u: 0.5, v: 0.5,                                        // around its middle, like the old page's logos
      px: rand(-0.6, 0.6), py: rand(-0.55, 0.55), drift: 0.003,
    }),
  },
};

class Slideshow {
  constructor(layer, powerState, viewerState) {
    this.layer = layer;
    this.power = powerState;
    this.viewer = viewerState;
    this.lanes = ['sky', 'photo', 'logo'].map((name) => {   // back to front
      const el = document.createElement('div');
      el.className = `lite-lane lite-${name}`;
      layer.appendChild(el);
      return new Lane(name, el, LANES[name], viewerState);
    });
    this.photos = this.lanes[1];
    this.listed = false;
    this.retryAt = 0;
    this.wasOn = false;
    this.offAt = 0;
    this.lastBeat = null;   // the heard beat (whole beats) at the last tick
    this.booked = -1;       // the latest beat a pulse is scheduled for
    this.drops = cvState.drops;
    this.dropUntil = -Infinity;   // beat until which the pulses are stronger
    this.glow = 1;          // the skies' brightness, eased towards the music's energy
  }

  stop() {
    clearInterval(this.timer);
    this.layer.remove();   // its pictures and their animations go with it
  }

  async start() {
    let last = performance.now();
    this.timer = setInterval(() => {
      const now = performance.now();
      tick(Math.min((now - last) / 1000, 0.25), false);
      last = now;
      this.update(now);
    }, TICK_MS);
    await this.loadManifest();
  }

  async loadManifest() {
    try {
      const r = await fetch('gallery.json', { cache: 'no-cache', signal: typeof AbortSignal !== 'undefined' && AbortSignal.timeout ? AbortSignal.timeout(10000) : undefined });
      const m = await r.json();
      const list = (part, base) => (part?.images ?? []).map((n) => base + encodeURIComponent(n));
      this.lanes[1].setUrls(list(m, m.lite || m.base));
      this.lanes[0].setUrls(list(m.sky, m.sky?.base || m.sky?.lite));
      this.lanes[2].setUrls(list(m.logo, m.logo?.base), new Set(list({ images: m.logo?.opaque }, m.logo?.base)));
      this.listed = true;
    } catch (err) {
      console.warn('[lite] no gallery list; trying again in 20 s', err);
      this.retryAt = performance.now() + FAIL_WAIT_MS;
    }
  }

  update(now) {
    const on = this.power.on;
    // The renderer's power bookkeeping: warmed up at once; the cabinet's shutting-down look ends after 0.85 s.
    if (on) this.power.warmupProgress = 1;
    if (on !== this.wasOn) { this.wasOn = on; if (!on) this.offAt = now; }
    if (!on && this.offAt && now - this.offAt > 850) {
      this.offAt = 0;
      this.power.shutdownProgress = 1;
      document.getElementById('tv-body')?.classList.remove('shutting-down');
    }
    if (!on || document.hidden) return;
    if (!this.listed) {
      if (now >= this.retryAt && this.retryAt) { this.retryAt = 0; this.loadManifest(); }
      return;
    }
    const beat = this.followBeat(now);
    // MENU › SPAZ LOGOS off: the logos lane fades out and brings no new logos (nor fetches any).
    const logos = this.viewer.logos !== false;
    const logoEl = this.lanes[2].el;
    if (logoEl.classList.contains('lite-off') === logos) logoEl.classList.toggle('lite-off', !logos);
    for (const lane of this.lanes) if (logos || lane !== this.lanes[2]) lane.update(now, beat);
  }

  /**
   * The sound's part, once a tick: books the next beat's pulse, eases the skies' brightness, starts a logo on a
   * drop. Returns what the lanes wait for: { locked, beat, bar } (a beat or a bar began since the last tick).
   */
  followBeat(now) {
    const live = autopilotState.isLiveBroadcast && autopilotState.clock.valid;
    const locked = !cvState.noSignal && (live || cvState.beatLocked);
    const bpm = Math.min(180, Math.max(60, (live ? autopilotState.clock.bpm : cvState.bpm) || 120));
    const beats = heardBeats(now, this.viewer.syncMs ?? 0);
    const whole = Math.floor(beats);
    const prev = this.lastBeat;
    this.lastBeat = whole;
    const crossed = prev !== null && whole !== prev;
    const edge = { locked, beat: crossed, bar: crossed && Math.floor(whole / BAR) !== Math.floor(prev / BAR) };

    // A drop: stronger pulses for a few bars, and a logo now. (A restarted watch counts from 0 again.)
    if (cvState.drops !== this.drops) {
      if (cvState.drops > this.drops && locked) {
        this.dropUntil = beats + DROP_BEATS;
        if (this.viewer.logos !== false) this.lanes[2].spawnNow();
      }
      this.drops = cvState.drops;
    }

    // The skies follow the music's energy (-1..1, 0 when unknown): dimmer when quiet, full when loud.
    const target = Math.min(1, Math.max(0.55, 0.85 + 0.3 * (cvState.audio_energy || 0)));
    this.glow += (target - this.glow) * Math.min(1, TICK_MS / 1500);
    const sky = this.lanes[0].el;
    const op = this.glow.toFixed(2);   // a new style only when it shows
    if (sky.style.opacity !== op) sky.style.opacity = op;

    // Book the next beat's pulse once it is under two ticks away, so the compositor plays it on time.
    if (this.booked > whole + 2) this.booked = whole;   // the clock jumped back
    const next = whole + 1;
    const delay = (next - beats) * 60000 / bpm;
    if (locked && !this.viewer.reducedNow && next > this.booked && delay <= 2 * TICK_MS + 10) {
      this.booked = next;
      const level = Math.min(1, Math.max(0.25, 0.5 + 0.6 * (cvState.audio_energy || 0)));
      const accent = (next % BAR === 0 ? 1.6 : 1) * (next < this.dropUntil ? 2 : 1);
      const dur = Math.min(0.85 * 60000 / bpm, 420);
      for (const lane of this.lanes) lane.pulse(PULSE[lane.name] * level * accent, delay, dur);
    }
    return edge;
  }

  spawnNow() { this.photos.spawnNow(); }
}

/** One layer of the collage: its own picture list, one picture fetched ahead, its pictures on screen. */
class Lane {
  constructor(name, el, profile, viewer) {
    this.name = name;
    this.el = el;
    this.profile = profile;
    this.viewer = viewer;
    this.urls = [];         // shuffled
    this.opaque = new Set(); // urls of pictures on a solid square (logos)
    this.next = 0;
    this.ready = null;      // a decoded <img> waiting to go on screen
    this.loading = false;
    this.failures = 0;
    this.retryAt = 0;
    this.layers = [];       // { el, fade, leaving } on screen, oldest first
    this.spawnAt = 0;       // performance.now() from which the next picture comes in
  }

  setUrls(urls, opaque = new Set()) {
    this.opaque = opaque;
    for (let i = urls.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [urls[i], urls[j]] = [urls[j], urls[i]];
    }
    this.urls = urls;
  }

  /** `beat`: Slideshow.followBeat's edges. A due picture waits for its beat or bar while the beat is locked. */
  update(now, beat) {
    if (this.urls.length === 0) return;
    if (!this.ready && !this.loading && now >= this.retryAt) this.fetchNext();
    const showing = this.layers.some((l) => !l.leaving);
    const onTime = !beat?.locked || beat[this.profile.on];
    if (this.ready && (!showing || (now >= this.spawnAt && onTime))) this.spawn(now);
  }

  /** A scale pulse on the whole layer: 1 + `amount` in `delayMs` (on the beat), back to 1 over `durMs`. */
  pulse(amount, delayMs, durMs) {
    if (!(amount > 0) || !this.el.animate) return;
    this.el.animate([{ transform: `scale(${1 + amount})` }, { transform: 'scale(1)' }],
      { duration: durMs, delay: delayMs, easing: 'cubic-bezier(0.2, 0.7, 0.3, 1)' });
  }

  fetchNext() {
    this.loading = true;
    const img = new Image();
    img.decoding = 'async';
    img.alt = '';
    img.src = this.urls[this.next];
    if (this.opaque.has(img.src) || this.opaque.has(this.urls[this.next])) img.dataset.opaque = '1';
    this.next = (this.next + 1) % this.urls.length;
    const done = (ok) => {
      this.loading = false;
      if (ok && img.naturalWidth > 0) { this.ready = img; this.failures = 0; return; }
      if (++this.failures >= 4) { this.failures = 0; this.retryAt = performance.now() + FAIL_WAIT_MS; }
    };
    (img.decode ? img.decode() : new Promise((res, rej) => { img.onload = res; img.onerror = rej; }))
      .then(() => done(true), () => done(false));
  }

  spawnNow() {
    if (this.ready) this.spawn(performance.now());
    else this.spawnAt = 0;   // as soon as the next one is in
  }

  spawn(now) {
    const img = this.ready;
    this.ready = null;
    const calm = this.viewer.reducedNow;
    const bpm = Math.min(180, Math.max(60, cvState.bpm || 120));
    const beat = 60 / bpm;                                  // seconds
    const live = this.layers.filter((l) => !l.leaving);
    if (live.length >= this.profile.max) this.leave(live[0], rand(4, 8) * beat);

    const W = this.el.clientWidth || 1, H = this.el.clientHeight || 1;
    const nw = img.naturalWidth, nh = img.naturalHeight;
    const [fadeIn, life, fadeOut] = this.profile.times();
    const total = fadeIn + life + fadeOut;
    const k = this.profile.look(W, H, nw / nh, calm, img.dataset.opaque === '1');
    const px = W / 2 + k.px * W / 2, py = H / 2 + k.py * H / 2;      // where the turning point sits
    const dx = rand(-k.drift, k.drift) * total * W / 2, dy = rand(-k.drift, k.drift) * total * H / 2;
    let k0 = (k.s0 * H) / nh;
    if (this.profile.cover) {
      // No edge may show at any angle: the picture's nearest edge to its turning point must lie beyond the
      // screen's farthest corner from it, wherever the drift takes it (it only grows from here). The beat
      // pulses only ever enlarge it.
      const R = Math.hypot(W / 2 + Math.abs(px - W / 2) + Math.abs(dx), H / 2 + Math.abs(py - H / 2) + Math.abs(dy));
      const near = Math.min(k.u, 1 - k.u) * nw, nearV = Math.min(k.v, 1 - k.v) * nh;
      k0 = Math.max(k0, 1.02 * R / Math.min(near, nearV));
    }
    const a0 = rand(-180, 180);

    const st = img.style;
    st.width = `${nw}px`;
    st.height = `${nh}px`;
    st.left = `${px - k.u * nw}px`;
    st.top = `${py - k.v * nh}px`;
    st.transformOrigin = `${k.u * 100}% ${k.v * 100}%`;
    st.opacity = '0';
    this.el.appendChild(img);   // newest on top

    const ms = total * beat * 1000;
    img.animate([
      { transform: `translate(0px, 0px) rotate(${a0}deg) scale(${k0})` },
      { transform: `translate(${dx}px, ${dy}px) rotate(${a0 + k.turn * total}deg) scale(${k0 * Math.exp(k.grow * total)})` },
    ], { duration: ms, easing: 'linear', fill: 'forwards' });
    const fade = img.animate([
      { opacity: 0 },
      { opacity: 1, offset: fadeIn / total },
      { opacity: 1, offset: (fadeIn + life) / total },
      { opacity: 0 },
    ], { duration: ms, easing: 'ease-in-out', fill: 'forwards' });
    const layer = { el: img, fade, leaving: false };
    fade.onfinish = () => this.remove(layer);
    this.layers.push(layer);
    this.spawnAt = now + this.profile.gap() * (calm ? 2 : 1) * beat * 1000;
  }

  /** Fades a picture out early (too many on screen), from wherever its fade is now. */
  leave(layer, seconds) {
    layer.leaving = true;
    const from = Number(getComputedStyle(layer.el).opacity) || 0;
    layer.fade.onfinish = null;
    layer.fade.cancel();
    layer.fade = layer.el.animate([{ opacity: from }, { opacity: 0 }], { duration: seconds * 1000, easing: 'ease-in', fill: 'forwards' });
    layer.fade.onfinish = () => this.remove(layer);
  }

  remove(layer) {
    layer.el.remove();   // drops its animations and its decoded bitmap
    this.layers = this.layers.filter((l) => l !== layer);
  }
}
