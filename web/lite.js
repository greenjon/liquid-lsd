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
//
// Bandwidth: everything comes from gallery.json (scripts/gallery_curate.py): photos from its `lite` folder
// (512 px JPEGs, about 35 KB each), skies from sky.lite, logos from logo.base (512 px PNGs), falling back to
// the full-size folders. A layer fetches one picture only when its next one is due, and none while the TV is
// off or the tab is hidden. A gallery.json without skies or logos just leaves that layer empty.
//
// No renderer runs in lite mode, so this also ticks the audio analysis (dsp.js `tick`: stream watchdog, beat
// tracking for the beat LED and tempo) and stands in for the renderer's power-on/off bookkeeping.

import { tick, cvState } from './dsp.js';

const KEY = 'lsd.visuals';     // 'full' | 'lite' (picked) | 'lite-auto' (too slow on an earlier visit); JSON
const TICK_MS = 50;            // audio analysis rate without a render loop
const FAIL_WAIT_MS = 20000;    // after a run of failed photos (offline), wait this long

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
    gap: () => rand(24, 48),
    times: () => [rand(12, 24), rand(48, 96), rand(12, 24)],
    look: (W, H, pa, calm) => ({
      s0: Math.max(1, (W / H) / pa) * rand(1.2, 1.4),       // always covers the screen
      grow: rand(0.001, 0.003) * (calm ? 0.5 : 1),
      turn: (calm ? 0.4 : 1) * rand(0.3, 0.8) * (Math.random() < 0.5 ? -1 : 1),
      u: rand(0.4, 0.6), v: rand(0.4, 0.6),
      px: rand(-0.1, 0.1), py: rand(-0.1, 0.1), drift: 0.001,
    }),
  },
  photo: {
    max: 2,
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
    gap: () => rand(12, 24),
    times: () => [rand(4, 8), rand(16, 32), rand(4, 8)],
    look: (W, H, pa, calm) => ({
      s0: Math.min(rand(0.3, 0.55), 0.8 * (W / H) / pa),
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
  }

  stop() {
    clearInterval(this.timer);
    this.layer.remove();   // its pictures and their animations go with it
  }

  async start() {
    let last = performance.now();
    this.timer = setInterval(() => {
      const now = performance.now();
      tick(Math.min((now - last) / 1000, 0.25));
      last = now;
      this.update(now);
    }, TICK_MS);
    await this.loadManifest();
  }

  async loadManifest() {
    try {
      const r = await fetch('gallery.json', { cache: 'no-cache' });
      const m = await r.json();
      const list = (part, base) => (part?.images ?? []).map((n) => base + encodeURIComponent(n));
      this.lanes[0].setUrls(list(m.sky, m.sky?.lite || m.sky?.base));
      this.lanes[1].setUrls(list(m, m.lite || m.base));
      this.lanes[2].setUrls(list(m.logo, m.logo?.base));
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
    for (const lane of this.lanes) lane.update(now);
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
    this.next = 0;
    this.ready = null;      // a decoded <img> waiting to go on screen
    this.loading = false;
    this.failures = 0;
    this.retryAt = 0;
    this.layers = [];       // { el, fade, leaving } on screen, oldest first
    this.spawnAt = 0;       // performance.now() from which the next picture comes in
  }

  setUrls(urls) {
    for (let i = urls.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [urls[i], urls[j]] = [urls[j], urls[i]];
    }
    this.urls = urls;
  }

  update(now) {
    if (this.urls.length === 0) return;
    if (!this.ready && !this.loading && now >= this.retryAt) this.fetchNext();
    const showing = this.layers.some((l) => !l.leaving);
    if (this.ready && (!showing || now >= this.spawnAt)) this.spawn(now);
  }

  fetchNext() {
    this.loading = true;
    const img = new Image();
    img.decoding = 'async';
    img.alt = '';
    img.src = this.urls[this.next];
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
    const k = this.profile.look(W, H, nw / nh, calm);
    const px = W / 2 + k.px * W / 2, py = H / 2 + k.py * H / 2;      // where the turning point sits
    const dx = rand(-k.drift, k.drift) * total * W / 2, dy = rand(-k.drift, k.drift) * total * H / 2;
    const k0 = (k.s0 * H) / nh;
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
