// ui.js
// The TV's controls: power, volume, the viewer controls (channel rocker, FX, HOLD, TIME, REACT), the
// on-screen display, keyboard and touch. Viewer controls are locked while the desktop broadcasts live.
import { startAudio, stopAudio, setVolume, cvState } from './dsp.js';
import { autopilotState, advanceForeground, previousForeground, nextFxChain, setHold, setHoldBars, nowShowing } from './autopilot.js';

// Power state — imported by renderer.js each frame
export const powerState = {
  on: false,              // is the TV powered on?
  warmupProgress: 0,      // 0..1, advanced by renderer.js when turning on
  shutdownProgress: 1.0,  // 0..1, advanced by renderer.js when turning off (starts at 1.0 = off)
};

// Viewer settings — renderer.js scales every audio signal by `react`
export const viewerState = {
  react: 1.0,
  awake: false,   // a screen wake lock is held
};

const TIME_STEPS = [8, 16, 32, 64];   // bars per look on the TIME dial
const REACT_MIN = 0.25, REACT_MAX = 2.0;
const STREAM_STATUS_URL = 'https://radio.spaz.org:8060/status-json.xsl';
const STREAM_MOUNT = '/radio.ogg';

// Per-viewer conveniences; the TV works the same without storage.
function load(key, fallback) {
  try { const v = localStorage.getItem(key); return v === null ? fallback : JSON.parse(v); } catch { return fallback; }
}
function save(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* private window: not remembered */ }
}

/** `web-kick-icosa` -> `KICK ICOSA` (the OSD upper-cases). */
const prettyName = (id) => (id || '').replace(/^web-/, '').replace(/[-_]+/g, ' ').trim();

// -------------------------------------------------------
// On-screen display
// -------------------------------------------------------
const osdTimers = {};
function flash(id, text, ms = 3000) {
  const el = document.getElementById(id);
  if (!el) return;
  if (text !== undefined) el.textContent = text;
  el.classList.add('show');
  clearTimeout(osdTimers[id]);
  osdTimers[id] = setTimeout(() => el.classList.remove('show'), ms);
}

let nowPlaying = '';
function showLook(info = nowShowing(), ms = 4000) {
  if (!powerState.on) return;
  if (autopilotState.isLiveBroadcast) {
    flash('osd-ch', 'LIVE', ms);
  } else {
    if (info.channel > 0) flash('osd-ch', `CH ${String(info.channel).padStart(2, '0')}`, ms);
    flash('osd-name', prettyName(info.name), ms);
    flash('osd-chain', info.chain ? `FX ${prettyName(info.chain)}` : 'NO FX', ms);
  }
  if (nowPlaying) flash('osd-track', `♪ ${nowPlaying}`, ms);
}

const message = (text, ms = 1500) => flash('osd-msg', text, ms);

// -------------------------------------------------------
// Dials: drag up/down, mouse wheel, arrow keys when focused
// -------------------------------------------------------
const MIN_ANGLE = -150, MAX_ANGLE = 150;

/** Wires a rotary dial to a 0..1 value. `onChange(v)` runs on every move; returns setter(v). */
function makeDial(el, { initial, onChange, steps = 0, locked = () => false }) {
  let value = initial;
  const quantise = (v) => (steps > 1 ? Math.round(v * (steps - 1)) / (steps - 1) : v);
  function set(v, notify = true) {
    value = Math.max(0, Math.min(1, v));
    const shown = quantise(value);
    el.style.transform = `rotate(${MIN_ANGLE + shown * (MAX_ANGLE - MIN_ANGLE)}deg)`;
    el.setAttribute('aria-valuenow', String(Math.round(shown * 100)));
    if (notify) onChange(shown);
  }
  let dragging = false, lastY = 0, dragged = false;
  const start = (y) => { if (locked()) return false; dragging = true; dragged = false; lastY = y; return true; };
  const move = (y) => {
    if (!dragging || y === lastY) return;
    dragged = true;
    set(value + (lastY - y) / 200);
    lastY = y;
  };
  el.addEventListener('mousedown', (e) => { if (start(e.clientY)) e.preventDefault(); });
  window.addEventListener('mousemove', (e) => move(e.clientY));
  window.addEventListener('mouseup', () => { dragging = false; });
  el.addEventListener('touchstart', (e) => { if (start(e.touches[0].clientY)) e.preventDefault(); }, { passive: false });
  window.addEventListener('touchmove', (e) => { if (dragging) move(e.touches[0].clientY); });
  window.addEventListener('touchend', () => { dragging = false; });
  el.addEventListener('wheel', (e) => {
    if (locked()) return;
    e.preventDefault();
    const step = steps > 1 ? 1 / (steps - 1) : 0.05;
    set(quantise(value) + (e.deltaY < 0 ? step : -step));
  }, { passive: false });
  el.addEventListener('keydown', (e) => {
    if (locked()) return;
    const step = steps > 1 ? 1 / (steps - 1) : 0.05;
    if (e.key === 'ArrowUp' || e.key === 'ArrowRight') { set(quantise(value) + step); e.preventDefault(); e.stopPropagation(); }
    if (e.key === 'ArrowDown' || e.key === 'ArrowLeft') { set(quantise(value) - step); e.preventDefault(); e.stopPropagation(); }
  });
  set(initial, false);
  // `dragged()`: the last press turned the dial, so its click is not a separate tap.
  return { set, get: () => quantise(value), dragged: () => dragged };
}

// -------------------------------------------------------
// Setup
// -------------------------------------------------------
export function initUI() {
  const powerSwitch = document.getElementById('powerSwitch');
  const tvBody = document.getElementById('tv-body');
  const canvas = document.getElementById('glCanvas');
  const screen = document.getElementById('tv-screen-area');
  const holdBtn = document.getElementById('holdBtn');

  const live = () => autopilotState.isLiveBroadcast;
  /** Runs a viewer control unless the desktop is live (then says why nothing happened). */
  const control = (fn) => (...args) => {
    if (!powerState.on) return;
    if (live()) { message('LIVE · CONTROLS LOCKED', 2000); return; }
    fn(...args);
  };

  // --- Power ---
  powerSwitch.addEventListener('click', async () => {
    if (powerState.on) {
      powerState.on = false;
      letScreenSleep();
      powerState.warmupProgress = 0;
      powerState.shutdownProgress = 0;
      tvBody.classList.remove('powered-on');
      tvBody.classList.add('shutting-down');
      powerSwitch.classList.remove('active');
      try { await stopAudio(); } catch (err) { console.error('Audio stop failed:', err); }
    } else {
      powerState.on = true;
      powerState.warmupProgress = 0;  // renderer will advance this
      powerState.shutdownProgress = 0;
      tvBody.classList.remove('shutting-down');
      tvBody.classList.add('powered-on');
      powerSwitch.classList.add('active');
      setTimeout(() => showLook(), 1600); // after the warm-up
      pollNowPlaying();
      holdScreenAwake();
      try {
        await startAudio();
      } catch (err) {
        console.error('Audio start failed:', err);
        message('NO SIGNAL · PRESS PWR AGAIN', 5000);
      }
    }
  });

  // --- Volume ---
  makeDial(document.getElementById('volumeDial'), { initial: 0.8, onChange: (v) => setVolume(v) });
  setVolume(0.8);

  // --- Channel rocker, FX, HOLD ---
  const next = control(() => { advanceForeground(); });
  const prev = control(() => { previousForeground(); });
  const fx = control(() => { nextFxChain(); });
  const hold = control(() => {
    setHold(!autopilotState.hold);
    holdBtn.setAttribute('aria-pressed', String(autopilotState.hold));
    message(autopilotState.hold ? 'HOLD' : 'HOLD OFF');
  });
  document.getElementById('chUp').addEventListener('click', next);
  document.getElementById('chDown').addEventListener('click', prev);
  document.getElementById('fxBtn').addEventListener('click', fx);
  holdBtn.addEventListener('click', hold);

  // --- TIME: bars per look ---
  const timeLabel = document.getElementById('timeLabel');
  const showTime = (bars) => { timeLabel.textContent = `${bars} BARS`; };
  const savedBars = TIME_STEPS.includes(load('lsd.timeBars', 16)) ? load('lsd.timeBars', 16) : 16;
  const timeDial = makeDial(document.getElementById('timeDial'), {
    initial: TIME_STEPS.indexOf(savedBars) / (TIME_STEPS.length - 1),
    steps: TIME_STEPS.length,
    locked: () => live(),
    onChange: (v) => {
      const bars = TIME_STEPS[Math.round(v * (TIME_STEPS.length - 1))];
      setHoldBars(bars);
      showTime(bars);
      save('lsd.timeBars', bars);
      if (powerState.on) message(`${bars} BARS PER LOOK`);
    },
  });
  setHoldBars(savedBars);
  showTime(savedBars);
  const stepTime = control((dir) => {
    const i = Math.round(timeDial.get() * (TIME_STEPS.length - 1));
    timeDial.set(Math.max(0, Math.min(TIME_STEPS.length - 1, i + dir)) / (TIME_STEPS.length - 1));
  });
  document.getElementById('timeDial').addEventListener('click', () => {
    if (timeDial.dragged()) return;
    if (live()) return control(() => {})();
    const i = Math.round(timeDial.get() * (TIME_STEPS.length - 1));
    timeDial.set(((i + 1) % TIME_STEPS.length) / (TIME_STEPS.length - 1));
  });

  // --- REACT: audio sensitivity ---
  const toDial = (k) => (k - REACT_MIN) / (REACT_MAX - REACT_MIN);
  viewerState.react = Math.max(REACT_MIN, Math.min(REACT_MAX, Number(load('lsd.react', 1.0)) || 1.0));
  const reactDial = makeDial(document.getElementById('reactDial'), {
    initial: toDial(viewerState.react),
    locked: () => live(),
    onChange: (v) => {
      viewerState.react = REACT_MIN + v * (REACT_MAX - REACT_MIN);
      save('lsd.react', viewerState.react);
      if (powerState.on) message(`REACT ${Math.round(viewerState.react * 100)}%`);
    },
  });
  document.getElementById('reactDial').addEventListener('dblclick', control(() => reactDial.set(toDial(1.0))));
  const stepReact = control((dir) => reactDial.set(reactDial.get() + dir * 0.05));

  // --- Keyboard ---
  window.addEventListener('keydown', (e) => {
    if (e.ctrlKey || e.metaKey || e.altKey || e.target.closest?.('input, textarea')) return;
    const keys = {
      ArrowRight: next, ArrowUp: next, n: next, N: next,
      ArrowLeft: prev, ArrowDown: prev, p: prev, P: prev,
      f: fx, F: fx, h: hold, H: hold, ' ': hold,
      '[': () => stepTime(-1), ']': () => stepTime(1),
      '-': () => stepReact(-1), '=': () => stepReact(1), '+': () => stepReact(1),
      i: () => showLook(), I: () => showLook(),
    };
    const fn = keys[e.key];
    if (!fn) return;
    if (e.target.closest?.('button') && (e.key === ' ' || e.key === 'Enter')) return; // let buttons click
    e.preventDefault();
    fn();
  });

  // --- Touch on the picture: swipe left = next look, right = previous, tap = info, long press = hold ---
  let touch = null;
  canvas.addEventListener('touchstart', (e) => {
    const t = e.touches[0];
    touch = { x: t.clientX, y: t.clientY, at: performance.now(), longPress: setTimeout(() => { touch.held = true; hold(); }, 650) };
  }, { passive: true });
  canvas.addEventListener('touchmove', (e) => {
    if (!touch) return;
    const t = e.touches[0];
    if (Math.hypot(t.clientX - touch.x, t.clientY - touch.y) > 15) clearTimeout(touch.longPress);
  }, { passive: true });
  canvas.addEventListener('touchend', (e) => {
    if (!touch) return;
    clearTimeout(touch.longPress);
    const t = e.changedTouches[0];
    const dx = t.clientX - touch.x, dy = t.clientY - touch.y;
    if (!touch.held) {
      if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 1.5) (dx < 0 ? next : prev)();
      else if (Math.hypot(dx, dy) < 15 && performance.now() - touch.at < 300) showLook();
    }
    touch = null;
  });
  canvas.addEventListener('click', () => showLook());

  // --- Fullscreen: double-click the picture (the OSD comes along) ---
  canvas.addEventListener('dblclick', () => {
    if (!document.fullscreenElement) screen.requestFullscreen?.().catch(console.error);
    else document.exitFullscreen().catch(console.error);
  });

  // --- Autopilot and broadcast events ---
  window.addEventListener('lsd-now-showing', (e) => showLook(e.detail));
  window.addEventListener('lsd-broadcast-status', (e) => {
    const text = document.querySelector('#station-badge .station-text');
    tvBody.classList.toggle('live', e.detail.live);
    if (text) text.textContent = e.detail.live ? 'SPAZ RADIO • LIVE' : 'SPAZ RADIO • CH.1';
    for (const id of ['chUp', 'chDown', 'fxBtn', 'holdBtn']) document.getElementById(id).setAttribute('aria-disabled', String(e.detail.live));
    if (e.detail.live) {
      setHold(false);
      holdBtn.setAttribute('aria-pressed', 'false');
    }
    showLook();
  });

  // --- Beat LED, and NO SIGNAL while the stream is down ---
  const beatLed = document.getElementById('beatLed');
  let noSignalShown = false;
  (function blink() {
    if (cvState.noSignal && powerState.on && !live()) { message('NO SIGNAL', 1200); noSignalShown = true; }
    else if (noSignalShown) { noSignalShown = false; showLook(); }
    const isLive = live() && autopilotState.clock.valid;
    const beats = isLive ? autopilotState.clock.read(performance.now()) : cvState.totalBeats;
    beatLed.classList.toggle('on', powerState.on && ((beats % 1) + 1) % 1 < 0.15);
    beatLed.classList.toggle('locked', isLive || cvState.beatLocked);
    requestAnimationFrame(blink);
  })();
}

// -------------------------------------------------------
// Wake lock: a laptop or phone used as a TV must not dim and lock while the TV is on. The browser drops
// the lock when the page is hidden, so it is taken again when the page comes back.
// -------------------------------------------------------
let wakeLock = null;
async function holdScreenAwake() {
  if (!powerState.on || wakeLock || !navigator.wakeLock || document.visibilityState !== 'visible') return;
  try {
    wakeLock = await navigator.wakeLock.request('screen');
    viewerState.awake = true;
    wakeLock.addEventListener('release', () => { wakeLock = null; viewerState.awake = false; });
  } catch { /* denied (battery saver, unsupported): the screen may sleep */ }
}
function letScreenSleep() {
  wakeLock?.release().catch(() => {});
  wakeLock = null;
}
document.addEventListener('visibilitychange', holdScreenAwake);

// -------------------------------------------------------
// Now playing (Icecast status), refreshed while the TV is on
// -------------------------------------------------------
let pollTimer = null;
async function pollNowPlaying() {
  clearTimeout(pollTimer);
  if (!powerState.on) return;
  try {
    const res = await fetch(STREAM_STATUS_URL, { cache: 'no-store' });
    const sources = [].concat((await res.json())?.icestats?.source ?? []);
    const src = sources.find((s) => String(s.listenurl || '').endsWith(STREAM_MOUNT)) ?? sources[0];
    // The Ogg mount sends artist and title separately; the title alone may be just a date.
    const artist = String(src?.artist ?? ''), name = String(src?.title ?? '');
    const title = (artist && !name.includes(artist) ? `${artist} - ${name}` : name).replace(/_/g, ' ').trim();
    if (title && title !== nowPlaying) {
      nowPlaying = title;
      flash('osd-track', `♪ ${nowPlaying}`, 6000);
    }
  } catch { /* status unavailable: no track line */ }
  pollTimer = setTimeout(pollNowPlaying, 30000);
}

// Auto-init when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initUI);
} else {
  initUI();
}
