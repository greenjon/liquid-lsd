// gallery.js — the "gallery" source: the Spaz Radio listener photos (spaz.org/radio "Add Pic"), drawn the way
// the old radio page drew them behind the player, smoothed out. Trial (branch trial/spaz-gallery-bg).
//
// The old page (groundCycler in spaz.org/radio): every 0.5-7.5 s a random photo faded in over 0-10 s while
// it grew from 3000 to 8000 px and turned 200 degrees, with the turn's centre off the middle of the screen,
// so each picture swung across the screen; the old one faded out over the same time, so two or three photos
// were on screen at once. Here: a new photo every 2-8 beats, on the beat; fades of 6-16 beats; a slow
// steady zoom and turn around a point off the photo's middle; the newest photo on top; the photo keeps its
// shape. Only a few photos are held at a time (the old page kept all of them, 500 MB and more).
//
// Images come from gallery.json, built by scripts/gallery_curate.py (not in git; publish_web_tv.sh uploads it).
//
// Curating (?gallery=curate, served by `scripts/gallery_curate.py preview`): the photos left in gallery-curate/,
// one at a time in name order with the name on screen, the list re-read every 10 s (so deleting in a file manager
// works too). Delete / Backspace rejects the photo on top, U puts the last reject back, . brings the next one in.

const VERT = `#version 300 es
precision highp float;
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
uniform vec2 uHalf;      // the photo's half size, in screen-height units (screen is 2 high)
uniform vec2 uPivotUV;   // the point of the photo it turns around (0..1)
uniform vec2 uPivotPos;  // where that point sits on the screen (x -aspect..aspect, y -1..1)
uniform float uAngle;
uniform float uAspect;
out vec2 vUV;
void main() {
  vec2 local = (aTexCoord - uPivotUV) * uHalf * 2.0;
  float c = cos(uAngle), s = sin(uAngle);
  vec2 p = uPivotPos + vec2(c * local.x - s * local.y, s * local.x + c * local.y);
  vUV = aTexCoord;
  gl_Position = vec4(p.x / uAspect, p.y, 0.0, 1.0);
}`;

const FRAG = `#version 300 es
precision highp float;
in vec2 vUV;
uniform sampler2D uTex;
uniform float uAlpha;
out vec4 fragColor;
void main() {
  vec2 e = min(vUV, 1.0 - vUV);
  float edge = smoothstep(0.0, 0.04, min(e.x, e.y));   // soft edges, so a turning photo has no hard rim
  float a = uAlpha * edge;
  fragColor = vec4(texture(uTex, vUV).rgb * a, a);     // premultiplied
}`;

const MAX_SIDE = 1024;       // photos are scaled down to this before they go to the GPU
const POOL = 8;              // photos held at once
const MAX_LAYERS = 4;        // photos on screen at once
const FETCH_EVERY_S = 20;    // a new photo in the pool this often, once the pool is full

const rand = (a, b) => a + Math.random() * (b - a);
const smooth = (x) => { const t = Math.min(1, Math.max(0, x)); return t * t * (3 - 2 * t); };

export class Gallery {
  constructor(gl, quadVAO, manifestUrl = 'gallery.json') {
    this.gl = gl;
    this.quadVAO = quadVAO;
    this.manifestUrl = manifestUrl;
    this.started = false;
    this.urls = [];          // shuffled
    this.next = 0;           // next url to fetch
    this.pool = [];          // { tex, aspect, shown } on the GPU
    this.decoded = [];       // canvases waiting to go to the GPU (one per frame)
    this.inflight = 0;       // photos being fetched
    this.failures = 0;       // in a row; offline: stop filling, the 20 s fetch tries again
    this.lastFetch = 0;
    this.layers = [];
    this.clock = 0;          // smooth beat clock (dt * bpm / 60): drives motion, never jumps
    this.nextSpawn = 0;      // clock value from which the next photo may come in
    this.lastBeats = null;
    this.curate = typeof location !== 'undefined' && new URLSearchParams(location.search).get('gallery') === 'curate';
    if (this.curate) {
      this.manifestUrl = 'curate/list.json';
      this.names = [];         // what is left in gallery-curate/, sorted
      this.seen = new Set();   // names fetched this pass
      this.fetching = new Set();
    }
  }

  /** Starts fetching the manifest and photos; called the first time a gallery deck is drawn. */
  start() {
    if (this.started) return;
    this.started = true;
    const gl = this.gl;
    const sh = (type, src) => {
      const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(`gallery shader: ${gl.getShaderInfoLog(s)}`);
      return s;
    };
    const prog = gl.createProgram();
    gl.attachShader(prog, sh(gl.VERTEX_SHADER, VERT));
    gl.attachShader(prog, sh(gl.FRAGMENT_SHADER, FRAG));
    gl.linkProgram(prog);
    this.prog = prog;
    this.loc = Object.fromEntries(['uHalf', 'uPivotUV', 'uPivotPos', 'uAngle', 'uAspect', 'uTex', 'uAlpha']
      .map((n) => [n, gl.getUniformLocation(prog, n)]));
    if (this.curate) { this.startCurate(); return; }
    this.loadManifest();
  }

  /** The photo list (gallery.json), shuffled. A failure is tried again with the 20 s fetch (render). */
  loadManifest() {
    this.inflight++;
    this.lastFetch = performance.now();
    fetch(this.manifestUrl).then((r) => r.json()).then((m) => {
      this.urls = m.images.map((n) => m.base + encodeURIComponent(n));
      for (let i = this.urls.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [this.urls[i], this.urls[j]] = [this.urls[j], this.urls[i]];
      }
    }).catch((e) => console.warn('[gallery] no manifest:', e.message))
      .finally(() => { this.inflight--; this.fill(); });
  }

  /** At the start: three photos at a time until the first few are in, so the picture starts quickly. */
  fill() {
    while (this.failures < 6 && this.inflight < 3 && this.pool.length + this.decoded.length + this.inflight < Math.min(5, this.urls.length)) this.fetchNext();
  }

  /** Fetches and decodes the next photo, scaled down, onto a canvas for upload. */
  fetchNext() {
    let url, name = null;
    if (this.curate) {
      name = this.names.find((n) => !this.seen.has(n) && !this.fetching.has(n));
      if (name === undefined) {                     // a full pass: start over
        if (this.names.length && this.names.every((n) => this.seen.has(n))) this.seen.clear();
        return;
      }
      this.seen.add(name);
      this.fetching.add(name);
      url = 'curate/' + encodeURIComponent(name);
    } else {
      if (this.urls.length === 0) return;
      url = this.urls[this.next];
      this.next = (this.next + 1) % this.urls.length;
    }
    this.inflight++;
    this.lastFetch = performance.now();
    const done = () => { this.inflight--; if (name !== null) this.fetching.delete(name); };
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.decoding = 'async';
    img.onload = () => {
      const k = Math.min(1, MAX_SIDE / Math.max(img.naturalWidth, img.naturalHeight));
      const c = document.createElement('canvas');
      c.width = Math.max(1, Math.round(img.naturalWidth * k));
      c.height = Math.max(1, Math.round(img.naturalHeight * k));
      c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
      c.photoName = name;
      this.decoded.push(c);
      done();
      this.failures = 0;
      this.fill();
    };
    img.onerror = () => { done(); this.failures++; console.warn('[gallery] could not load', url); this.fill(); };
    img.src = url;
  }

  /** One decoded photo to the GPU; when the pool is full, it replaces the most-shown photo not on screen. */
  upload() {
    const c = this.decoded.shift();
    if (!c) return;
    const gl = this.gl;
    let slot;
    if (this.pool.length < POOL) {
      slot = { tex: gl.createTexture(), aspect: 1, shown: 0 };
      this.pool.push(slot);
    } else {
      const onScreen = new Set(this.layers.map((l) => l.photo));
      slot = this.pool.filter((p) => !onScreen.has(p)).sort((a, b) => b.shown - a.shown)[0];
      if (!slot) return;
    }
    gl.bindTexture(gl.TEXTURE_2D, slot.tex);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, c);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
    gl.generateMipmap(gl.TEXTURE_2D);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.bindTexture(gl.TEXTURE_2D, null);
    slot.aspect = c.width / c.height;
    slot.shown = 0;
    slot.fresh = true;
    slot.name = c.photoName;
  }

  /** A photo for a new layer: a fresh one first, else the least shown that is not already on screen. */
  pickPhoto() {
    const onScreen = new Set(this.layers.map((l) => l.photo));
    const free = this.pool.filter((p) => !onScreen.has(p));
    if (free.length === 0) return null;
    const fresh = free.find((p) => p.fresh);
    if (fresh || this.curate) return fresh ?? null;   // curating: each photo once, in order
    const least = Math.min(...free.map((p) => p.shown));
    const pick = free.filter((p) => p.shown === least);
    return pick[Math.floor(Math.random() * pick.length)];
  }

  spawn(aspect, calm) {
    const photo = this.pickPhoto();
    if (!photo) return;
    photo.fresh = false;
    photo.shown++;
    if (this.layers.length >= MAX_LAYERS) {        // the oldest photo on screen starts to leave
      const old = this.layers.find((l) => l.outAt > this.clock);
      if (old) old.outAt = this.clock;
    }
    // Cover the screen at the start, then grow: the turn and the off-centre pivot sweep the photo across.
    const cover = Math.max(1, aspect / photo.aspect);
    const fadeIn = this.curate ? 2 : rand(6, 16);
    const life = this.curate ? 6 : rand(24, 48);
    const turn = (calm ? 0.4 : 1) * rand(1.5, 4) * (Math.random() < 0.5 ? -1 : 1) * Math.PI / 180; // radians per beat
    this.layers.push({
      photo,
      born: this.clock,
      fadeIn,
      outAt: this.clock + fadeIn + life,
      fadeOut: this.curate ? 3 : rand(6, 16),
      scale0: cover * rand(1.15, 1.5),
      grow: rand(0.006, 0.014) * (calm ? 0.5 : 1),          // log-scale growth per beat
      angle0: rand(-Math.PI, Math.PI),
      turn,
      pivotUV: [rand(0.25, 0.75), rand(0.25, 0.75)],
      pivotPos: [rand(-0.35, 0.35) * aspect, rand(-0.35, 0.35)],
      drift: [rand(-0.004, 0.004) * aspect, rand(-0.004, 0.004)], // the pivot wanders, screen units per beat
    });
  }

  /** Draws the gallery into `target` (a graph.js Target). `beats`: the heard beat count. */
  render(target, { dt, bpm, beats, calm = false, alpha = 1 }) {
    this.start();
    const gl = this.gl;
    this.clock += dt * Math.min(180, Math.max(60, bpm || 120)) / 60;
    if (this.decoded.length) this.upload();
    // Every 20 s, one more photo; after a bad start (no list, or every photo failing: offline), try again.
    if (this.inflight === 0 && performance.now() - this.lastFetch > FETCH_EVERY_S * 1000) {
      if (!this.curate && this.urls.length === 0) this.loadManifest();
      else if (this.pool.length > 0) this.fetchNext();
      else { this.failures = 0; this.fill(); }
    }

    const aspect = target.w / target.h;
    // A new photo every 2-8 beats, on a beat (or a beat late, when the beat stands still: no audio yet);
    // straight away when nothing is showing.
    const onBeat = this.lastBeats !== null && Math.floor(beats) !== Math.floor(this.lastBeats);
    this.lastBeats = beats;
    const visible = this.layers.filter((l) => l.outAt > this.clock).length;
    const due = this.clock >= this.nextSpawn && (onBeat || this.clock >= this.nextSpawn + 1);
    if (this.pool.length > 0 && (visible === 0 || due)) {
      this.spawn(aspect, calm);
      this.nextSpawn = this.clock + (this.curate ? 4 : rand(2, 8) * (calm ? 2 : 1)) - 0.5;
    }
    this.layers = this.layers.filter((l) => this.clock < l.outAt + l.fadeOut);
    if (this.curate) this.tickCurate();

    target.bind();
    gl.clearColor(0, 0, 0, 0);
    gl.clear(gl.COLOR_BUFFER_BIT);
    if (!this.prog || this.layers.length === 0) return;
    gl.useProgram(this.prog);
    gl.bindVertexArray(this.quadVAO);
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
    gl.activeTexture(gl.TEXTURE0);
    gl.uniform1i(this.loc.uTex, 0);
    gl.uniform1f(this.loc.uAspect, aspect);
    for (const l of this.layers) {                    // oldest first, so the newest photo is on top
      const age = this.clock - l.born;
      const a = smooth(age / l.fadeIn) * (1 - smooth((this.clock - l.outAt) / l.fadeOut)) * alpha;
      if (a <= 0.001) continue;
      const s = l.scale0 * Math.exp(l.grow * age);
      gl.bindTexture(gl.TEXTURE_2D, l.photo.tex);
      gl.uniform2f(this.loc.uHalf, s * l.photo.aspect, s);
      gl.uniform2f(this.loc.uPivotUV, l.pivotUV[0], l.pivotUV[1]);
      gl.uniform2f(this.loc.uPivotPos, l.pivotPos[0] + l.drift[0] * age, l.pivotPos[1] + l.drift[1] * age);
      gl.uniform1f(this.loc.uAngle, l.angle0 + l.turn * age);
      gl.uniform1f(this.loc.uAlpha, a);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }
    gl.disable(gl.BLEND);
    gl.bindTexture(gl.TEXTURE_2D, null);
  }

  // ---------------------------------------------------------------- curating (?gallery=curate)
  startCurate() {
    const refresh = () => fetch('curate/list.json', { cache: 'no-store' }).then((r) => r.json()).then((m) => {
      this.names = m.images;
      const left = new Set(this.names);
      for (const n of [...this.seen]) if (!left.has(n)) this.seen.delete(n);
      this.fill();
    }).catch((e) => console.warn('[gallery] curate list:', e.message));
    refresh();
    setInterval(refresh, 10000);
    const el = document.createElement('div');
    el.style.cssText = 'position:fixed;left:50%;top:max(8px, env(safe-area-inset-top));transform:translateX(-50%);z-index:9999;'
      + 'background:rgba(0,0,0,.75);color:#ffe066;font:14px/1.4 monospace;padding:6px 12px;border-radius:6px;'
      + 'max-width:calc(100vw - 32px);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;pointer-events:none';
    document.body.appendChild(el);
    this.overlay = el;
    this.flashText = null;
    this.flashUntil = 0;
    window.addEventListener('keydown', (e) => {
      if (e.target.closest?.('input, textarea')) return;
      if (e.key === 'Delete' || e.key === 'Backspace') { e.preventDefault(); this.reject(); }
      else if (e.key === 'u' || e.key === 'U') { e.preventDefault(); this.undo(); }
      else if (e.key === '.') { e.preventDefault(); this.nextSpawn = this.clock - 1; }
    }, true);
  }

  /** The photo on top: the newest layer still on screen. */
  current() {
    for (let i = this.layers.length - 1; i >= 0; i--) if (this.layers[i].outAt > this.clock) return this.layers[i];
    return null;
  }

  flash(text) { this.flashText = text; this.flashUntil = performance.now() + 2500; }

  reject() {
    const l = this.current();
    const name = l?.photo.name;
    if (!name) return;
    l.outAt = this.clock;
    l.fadeOut = 0.5;
    this.nextSpawn = this.clock;
    fetch('curate/reject', { method: 'POST', body: JSON.stringify({ name }) }).then((r) => r.json()).then((r) => {
      if (!r.ok) return this.flash(`could not reject ${name}`);
      this.names = this.names.filter((n) => n !== name);
      l.photo.name = null;
      this.flash(`REJECTED ${name}  \u00B7  U puts it back`);
    }).catch(() => this.flash('reject failed: is gallery_curate.py preview running?'));
  }

  undo() {
    fetch('curate/undo', { method: 'POST' }).then((r) => r.json()).then((r) => {
      if (!r.ok) return this.flash('nothing to put back');
      if (!this.names.includes(r.name)) this.names = [...this.names, r.name].sort();
      this.seen.delete(r.name);
      this.flash(`PUT BACK ${r.name}: it comes up next`);
      this.fill();
    }).catch(() => this.flash('undo failed'));
  }

  tickCurate() {
    // Keep a few unseen photos ready, so the next one is always there on time.
    const ready = this.pool.filter((p) => p.fresh).length + this.decoded.length + this.inflight;
    if (ready < 3 && this.inflight < 2) this.fetchNext();
    if (!this.overlay) return;
    const done = this.names.filter((n) => this.seen.has(n)).length;
    const cur = this.current()?.photo.name;
    const text = performance.now() < this.flashUntil ? this.flashText
      : `CURATE ${done}/${this.names.length}  \u00B7  ${cur ?? '...'}  \u00B7  DEL reject  U undo  . next`;
    if (this.overlay.textContent !== text) this.overlay.textContent = text;
  }
}
