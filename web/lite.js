// lite.js — the TV's lite picture: the Spaz Radio photo gallery without WebGL, for devices that cannot run
// the visuals (no WebGL 2, or the renderer failed to start) and for viewers who pick it (MENU › TV › VISUALS,
// or ?lite=1 / ?lite=0 in the address, which wins over the saved choice).
//
// The look is gallery.js's (itself the old spaz.org/radio page's groundCycler, smoothed): photos fade in over
// each other while each one slowly turns and grows around a point off its middle, newest on top. Here every
// photo is an <img> moved only by the Web Animations API on `transform` and `opacity`, so the browser's
// compositor animates it and the script wakes only to start the next photo. Each <img> is laid out at the
// photo's own size and scaled up by the transform, so the GPU holds a small bitmap, not a screen-sized one.
//
// Bandwidth: the photos come from gallery.json's `lite` folder (512 px JPEGs, about 35 KB each, built by
// scripts/gallery_curate.py lite), falling back to the full-size `base`. One photo is fetched only when the
// next one is due, and none while the TV is off or the tab is hidden.
//
// No renderer runs in lite mode, so this also ticks the audio analysis (dsp.js `tick`: stream watchdog, beat
// tracking for the beat LED and tempo) and stands in for the renderer's power-on/off bookkeeping.

import { tick, cvState } from './dsp.js';

const KEY = 'lsd.visuals';     // 'lite' | 'full' (ui.js save(): JSON)
const MAX_LAYERS = 3;          // photos on screen at once
const TICK_MS = 50;            // audio analysis rate without a render loop
const FAIL_WAIT_MS = 20000;    // after a run of failed photos (offline), wait this long

const rand = (a, b) => a + Math.random() * (b - a);

/** Lite mode: `on` once started; `reason` 'chosen' (the viewer's pick), 'no-webgl' or 'error'. */
export const liteState = { on: false, reason: null };

/** 'lite' or 'full': the address (?lite=1 / ?lite=0) first, then the saved choice; 'full' by default. */
export function visualsChoice() {
  const q = new URLSearchParams(globalThis.location?.search ?? '').get('lite');
  if (q === '1') return 'lite';
  if (q === '0') return 'full';
  try { return JSON.parse(localStorage.getItem(KEY)) === 'lite' ? 'lite' : 'full'; } catch { return 'full'; }
}

/** Saves the viewer's pick and reloads the page, which then starts the picture that was picked. */
export function setVisuals(choice) {
  try { localStorage.setItem(KEY, JSON.stringify(choice)); } catch { /* private window: this visit only */ }
  const url = new URL(location.href);
  url.searchParams.delete('lite');   // the address would override the pick
  location.replace(url.href);
}

let show = null;

/** Starts lite mode. `powerState` and `viewerState` come from ui.js (passed in: ui.js imports this module). */
export function startLite(reason, { powerState, viewerState }) {
  if (liteState.on) return;
  liteState.on = true;
  liteState.reason = reason;
  document.body.classList.add('lite');
  const screen = document.getElementById('tv-screen-area');
  const layer = document.createElement('div');
  layer.id = 'lite-layer';
  layer.setAttribute('aria-hidden', 'true');
  screen.querySelector('#glCanvas').after(layer);
  show = new Slideshow(layer, powerState, viewerState);
  show.start();
  window.dispatchEvent(new CustomEvent('lsd-lite', { detail: { reason } }));
}

/** CH ▲▼ in lite mode: the next photo now. */
export function liteNext() { show?.spawnNow(); }

class Slideshow {
  constructor(layer, powerState, viewerState) {
    this.layer = layer;
    this.power = powerState;
    this.viewer = viewerState;
    this.urls = [];         // shuffled
    this.next = 0;
    this.ready = null;      // a decoded <img> waiting to go on screen
    this.loading = false;
    this.failures = 0;
    this.retryAt = 0;
    this.layers = [];       // { el, fade } on screen, oldest first
    this.spawnAt = 0;       // performance.now() from which the next photo comes in
    this.wasOn = false;
    this.offAt = 0;
  }

  async start() {
    let last = performance.now();
    setInterval(() => {
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
      const base = m.lite || m.base;
      const urls = m.images.map((n) => base + encodeURIComponent(n));
      for (let i = urls.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [urls[i], urls[j]] = [urls[j], urls[i]];
      }
      this.urls = urls;
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
    if (this.urls.length === 0) {
      if (now >= this.retryAt && this.retryAt) { this.retryAt = 0; this.loadManifest(); }
      return;
    }
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
    if (live.length >= MAX_LAYERS) this.leave(live[0], rand(4, 8) * beat);

    const W = this.layer.clientWidth || 1, H = this.layer.clientHeight || 1;
    const nw = img.naturalWidth, nh = img.naturalHeight, pa = nw / nh;
    // gallery.js's motion, in beats: cover the screen at the start, then grow and turn around an off-centre point.
    const fadeIn = rand(6, 16), life = rand(24, 48), fadeOut = rand(6, 16);
    const total = fadeIn + life + fadeOut;
    const s0 = Math.max(1, (W / H) / pa) * rand(1.15, 1.5);       // photo height in screen heights
    const grow = rand(0.006, 0.014) * (calm ? 0.5 : 1);           // log-scale growth per beat
    const turn = (calm ? 0.4 : 1) * rand(1.5, 4) * (Math.random() < 0.5 ? -1 : 1);   // degrees per beat
    const a0 = rand(-180, 180);
    const u = rand(0.25, 0.75), v = rand(0.25, 0.75);             // the turning point on the photo
    const px = W / 2 + rand(-0.35, 0.35) * W / 2, py = H / 2 + rand(-0.35, 0.35) * H / 2;   // ...and on screen
    const dx = rand(-0.004, 0.004) * total * W / 2, dy = rand(-0.004, 0.004) * total * H / 2;
    const k0 = (s0 * H) / nh;

    const st = img.style;
    st.width = `${nw}px`;
    st.height = `${nh}px`;
    st.left = `${px - u * nw}px`;
    st.top = `${py - v * nh}px`;
    st.transformOrigin = `${u * 100}% ${v * 100}%`;
    st.opacity = '0';
    this.layer.appendChild(img);   // newest on top

    const ms = total * beat * 1000;
    img.animate([
      { transform: `translate(0px, 0px) rotate(${a0}deg) scale(${k0})` },
      { transform: `translate(${dx}px, ${dy}px) rotate(${a0 + turn * total}deg) scale(${k0 * Math.exp(grow * total)})` },
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
    this.spawnAt = now + rand(3, 9) * (calm ? 2 : 1) * beat * 1000;
  }

  /** Fades a photo out early (too many on screen), from wherever its fade is now. */
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
