// ui.js
// The TV's controls: power, volume, the viewer controls (channel rocker, FX, HOLD, TIME, REACT), the
// on-screen display, keyboard and touch. Viewer controls are locked while the desktop broadcasts live.
import { startAudio, stopAudio, setVolume, setMic, isMicOn, micAvailable, cvState } from './dsp.js';
import { embed } from './embedmode.js';
import { autopilotState, advanceForeground, previousForeground, randomForeground, nextFxChain, setHold, setHoldBars, nowShowing, setHeavyAllowed, setReducedMotion,
  rollScene, setAutoRollBars, setEffects, setFollowMusic, setBackgroundMode, stepBackground,
  setSkipped, skippedChannels, prunedSkips, toggleSkip, restoreChannel, restoreAllChannels, tuneTo, SKIP_FLOOR } from './autopilot.js';
import { createMenu } from './menu.js';
import { heardBeats, tapOffset } from './sync.js';
import { createChat } from './chat.js';
import { createChatboard } from './chatboard.js';
import { scheduleState, refreshSchedule, setShows, groupByDay, zoneLabel, nextLine, SCHEDULE_REFRESH_MS, SCHEDULE_STALE_MS } from './schedule.js';

// Power state — imported by renderer.js each frame
// Shown in the MENU title so a viewer can tell whether the browser has the latest code. Bump it with
// every publish that changes code (scripts/publish_web_tv.sh warns when spaz.org already has this one).
export const TV_VERSION = 'v16';

export const powerState = {
  on: false,              // is the TV powered on?
  warmupProgress: 0,      // 0..1, advanced by renderer.js when turning on
  shutdownProgress: 1.0,  // 0..1, advanced by renderer.js when turning off (starts at 1.0 = off)
};

// Viewer settings — renderer.js scales every audio signal by `react`
export const viewerState = {
  react: 1.0,
  announce: false,     // show the scene and its FX on every change (off: only when asked, or after a control)
  info: false,         // INFO: the info lines stay on screen
  awake: false,        // a screen wake lock is held
  syncMs: 0,           // SYNC: how late the sound reaches the viewer (speakers, Bluetooth), in ms
  reducedMode: 'auto', // Reduced motion: 'auto' (follows the system setting), 'on', 'off'
  heavyMode: 'auto',   // Heavy scenes: 'auto' (the GPU probe decides), 'on', 'off'
  autoRoll: 0,         // AUTO ROLL: bars between rolls of the scene on air (0 = off)
  bgMode: 'hold',      // BG CHANGES: 'scenes' (every 2x TIME), 'hold' (the default: the photo gallery stays), 'off'
  effects: true,       // EFFECTS: AUTO (true) or OFF
  follow: true,        // FOLLOW MUSIC: breakdowns and drops pick the scene
  reducedNow: false,   // reduced motion in effect (updated by applyReduced; the renderer reads it)
  /** Reduced motion as the setting and the system ask for it now. */
  get reduced() {
    if (this.reducedMode !== 'auto') return this.reducedMode === 'on';
    return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
  },
};
const SYNC_MIN = -100, SYNC_MAX = 400;
const MODES = ['auto', 'on', 'off'];

const TIME_STEPS = [8, 16, 32, 64];   // bars per scene on the TIME dial
const AUTO_ROLL_STEPS = [0, 4, 8, 16]; // AUTO ROLL: bars between rolls (0 = off)
const BG_MODES = ['scenes', 'hold', 'off'];
const BG_MODE_LABELS = { scenes: 'WITH SCENES', hold: 'HOLD', off: 'OFF' };
// 100% = the desktop's Audio Response at 100%. Auto gain levels the stream, so no boost is needed by default.
const REACT_MIN = 0.25, REACT_MAX = 2.0, REACT_DEFAULT = 1.0;
// Saved under a new key: values saved before auto gain (200% was the default) would run twice as hot.
const REACT_KEY = 'lsd.react.agc';
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
  if (viewerState.info && INFO_IDS.includes(id)) return; // INFO on: the info lines stay up
  osdTimers[id] = setTimeout(() => el.classList.remove('show'), ms);
}
/** Takes an OSD line down now. */
function unflash(id) {
  clearTimeout(osdTimers[id]);
  document.getElementById(id)?.classList.remove('show');
}
/** The info lines the INFO button keeps on screen (everything on the OSD but the control messages). */
const INFO_IDS = ['osd-ch', 'osd-name', 'osd-bg', 'osd-chain', 'osd-track', 'osd-next', 'osd-brand'];

let nowPlaying = '';
function showLook(info = nowShowing(), ms = 4000, brand = false) {
  if (!powerState.on) return;
  if (autopilotState.isLiveBroadcast) {
    flash('osd-ch', 'LIVE', ms);
    if (autopilotState.broadcaster) flash('osd-name', autopilotState.broadcaster, ms);
    unflash('osd-bg'); unflash('osd-chain'); // the desktop's scene is not ours to name
  } else {
    // Bottom left, one size: the scene, the background behind it, the scene's FX chain.
    if (info.channel > 0) flash('osd-ch', `CH ${String(info.channel).padStart(3, '0')}${info.skipped ? ' \u00B7 SKIPPED' : ''}`, ms);
    flash('osd-name', `SCENE: ${prettyName(info.name)}`, ms);
    if (info.background) flash('osd-bg', `BACKGROUND: ${prettyName(info.background)}`, ms);
    else unflash('osd-bg');
    flash('osd-chain', `FX: ${!info.effects ? 'OFF' : info.chain ? prettyName(info.chain) : 'NONE'}`, ms);
  }
  if (nowPlaying) flash('osd-track', `♪ ${nowPlaying}`, ms);
  flashNext(ms);
  if (brand) flash('osd-brand', undefined, ms); // asked for (I, a click on the picture): who made this
}

const message = (text, ms = 1500) => flash('osd-msg', text, ms);

/** Under the track line: the next show on Spaz Radio, when the schedule knows one. */
function flashNext(ms) {
  const line = nextLine(scheduleState.shows, Date.now());
  if (line) flash('osd-next', line, ms);
}

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
  window.addEventListener('mouseup', () => {
    // A drag that ends over the TV would also click it (and power it off): swallow that one click.
    if (dragging && dragged) {
      const swallow = (e) => { e.stopPropagation(); e.preventDefault(); };
      window.addEventListener('click', swallow, { capture: true, once: true });
      setTimeout(() => window.removeEventListener('click', swallow, { capture: true }), 0);
    }
    dragging = false;
  });
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
let audioBlocked = false;  // the browser refused to start audio without a click
let lastUserChange = -Infinity; // a viewer's CH / FX press: its result is announced even with ANNOUNCE off
/** A new scene is on: show it if INFO or ANNOUNCE SCENES is on, or if the viewer just asked for the change. */
function announceLook(info) {
  if (viewerState.info || viewerState.announce || performance.now() - lastUserChange < 4000) showLook(info);
}
let identShown = false;    // the Liquid LSD ident plays at the first power-on of a visit
let lastBumper = 0;        // performance.now() of the last bumper (or the power-on)
const BUMPER_EVERY_MS = 30 * 60 * 1000;

/** A title card (ident, LIVE) is up for `ms`: the OSD steps aside so the two don't overlap. */
let cardTimer = null;
function cardUp(ms) {
  const screen = document.getElementById('tv-screen-area');
  screen.classList.add('card-up');
  clearTimeout(cardTimer);
  cardTimer = setTimeout(() => screen.classList.remove('card-up'), ms);
}

/** The power-on ident: the Liquid LSD logo and name over the picture for a few seconds. */
function showIdent() {
  if (embed) return;
  cardUp(3600);
  const el = document.getElementById('ident');
  el.classList.remove('show');
  void el.offsetWidth;
  el.classList.add('show');
}
const kiosk = embed || new URLSearchParams(location.search).has('kiosk'); // embed is a kiosk without the extras
let chatboard = null;      // the chat's blackboard (chatboard.js); none under kiosk / embed

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
  // Hover help follows the power state: off, the whole set is the power target; on, only PWR is.
  // (The menu, ABOUT and SCHEDULE overlays carry an empty title so they don't inherit either.)
  const updatePowerTitles = () => {
    tvBody.title = powerState.on
      ? 'Click the power button (PWR) to turn the TV off\nPress ? for keyboard shortcuts'
      : 'Click the TV to turn it on';
    canvas.title = powerState.on
      ? 'Click for info (press I or INFO to keep it on screen)\nDouble-click for fullscreen (or press S)\nPress ? for keyboard shortcuts'
      : 'Click the TV to turn it on';
  };
  updatePowerTitles();
  powerSwitch.addEventListener('click', async () => {
    if (powerState.on) {
      powerState.on = false;
      updatePowerTitles();
      viewerState.schedule?.hide();
      document.getElementById('keys').hidden = true;
      document.getElementById('skipped').hidden = true;
      letScreenSleep();
      updateMediaSession();
      powerState.warmupProgress = 0;
      powerState.shutdownProgress = 0;
      tvBody.classList.remove('powered-on');
      tvBody.classList.add('shutting-down');
      powerSwitch.classList.remove('active');
      try { await stopAudio(); } catch (err) { console.error('Audio stop failed:', err); }
    } else {
      powerState.on = true;
      updatePowerTitles();
      powerState.warmupProgress = embed ? 1 : 0;  // renderer will advance this (embed: no CRT warm-up)
      powerState.shutdownProgress = 0;
      tvBody.classList.remove('shutting-down');
      tvBody.classList.add('powered-on');
      powerSwitch.classList.add('active');
      if (!identShown) {
        // First power-on of the visit: the Liquid LSD ident over the end of the warm-up, then the scene.
        identShown = true;
        setTimeout(showIdent, 900);
        setTimeout(() => announceLook(), 4600);
      } else {
        setTimeout(() => announceLook(), 1600); // after the warm-up
      }
      lastBumper = performance.now();
      pollNowPlaying();
      pollSchedule();
      holdScreenAwake();
      updateMediaSession();
      try {
        await startAudio();
        audioBlocked = false;
      } catch (err) {
        // Autoplay blocked (kiosk power-on without a click) or the stream failed: the next click or key
        // anywhere starts the sound; meanwhile dsp.js shows no signal and keeps retrying the stream.
        console.warn('Audio start failed:', err);
        audioBlocked = true;
      }
    }
  });

  // When the TV is off, a click anywhere on it turns it on. When it is on, the cabinet itself does nothing:
  // only the power switch turns it off. A click that just misses a control (on its label, or in the gap
  // beside it) presses the nearest one, within a square of NEAR_MISS px around it; a near-miss on a dial
  // does nothing (dials are dragged). With the menu open, a stray click only closes the menu.
  const NEAR_MISS = 22;
  const nearTargets = [...document.querySelectorAll('#tv-controls button, #tv-controls .rotary-dial')];
  const nearestControl = (x, y) => {
    let best = null, bestD = NEAR_MISS;
    for (const el of nearTargets) {
      const r = el.getBoundingClientRect();
      if (!r.width || !r.height) continue; // hidden (e.g. CHAT before the board loads)
      const d = Math.max(r.left - x, x - r.right, r.top - y, y - r.bottom, 0); // square (Chebyshev) distance
      if (d <= bestD) { best = el; bestD = d; }
    }
    return best;
  };
  tvBody.addEventListener('click', (e) => {
    if (e.target.closest('button, .rotary-dial, .rocker, #tv-menu, #about, #schedule, #skipped, a')) return; // they handle their own clicks
    if (!powerState.on) { powerSwitch.click(); return; }
    if (viewerState.menu?.open) { viewerState.menu.hide(); return; } // a near-miss on the menu closes it, not the TV
    if (!e.target.closest('#tv-controls')) return; // the picture shows the info; the cabinet is inert
    const near = nearestControl(e.clientX, e.clientY);
    if (near?.tagName === 'BUTTON') near.click();
  });

  // --- Volume ---
  const volumeDial = makeDial(document.getElementById('volumeDial'), {
    initial: 0.8,
    onChange: (v) => { setVolume(v); if (powerState.on) message(`VOL ${Math.round(v * 100)}%`); },
  });
  setVolume(0.8);
  const stepVolume = (dir) => volumeDial.set(volumeDial.get() + dir * 0.05);

  // --- Channel rocker, FX, HOLD ---
  const next = control(() => { lastUserChange = performance.now(); advanceForeground(); });
  const prev = control(() => { lastUserChange = performance.now(); previousForeground(); });
  const random = control(() => { lastUserChange = performance.now(); randomForeground(); });
  const fx = control(async () => {
    lastUserChange = performance.now();
    if (await nextFxChain() === 'off') message('EFFECTS OFF \u00B7 MENU', 2000);
  });
  // ROLL: a new variation of the scene on air (autopilot.rollScene glides it in on the next bar).
  const roll = control(() => message(rollScene() ? 'ROLL' : 'NOTHING TO ROLL HERE'));
  // B: the next background now.
  const nextBackground = control(async () => {
    const r = await stepBackground(1);
    if (r === 'off') message('BACKGROUND OFF \u00B7 MENU', 2000);
    else if (r && !['live', 'none', 'superseded'].includes(r)) { message(`BACKGROUND: ${prettyName(r.split('|')[0])}`, 2500); menu.refresh(); }
  });
  // X: skip the channel on air (it changes on at once), or restore a skipped one; X again within 5 s undoes a skip.
  let lastSkip = null;
  const saveSkips = () => save('lsd.skipped', prunedSkips());
  const skip = control(() => {
    if (lastSkip && performance.now() - lastSkip.at < 5000) {
      const { name, channel } = lastSkip;
      lastSkip = null;
      lastUserChange = performance.now();
      restoreChannel(name, true);
      saveSkips();
      message(`CH ${String(channel).padStart(3, '0')} RESTORED`, 2500);
      return;
    }
    const r = toggleSkip();
    if (r.action === 'none') return;
    const ch = `CH ${String(r.channel).padStart(3, '0')}`;
    if (r.action === 'floor') { message(`${SKIP_FLOOR} CHANNELS MUST STAY`, 2500); return; }
    lastUserChange = performance.now();
    if (r.action === 'skipped') { lastSkip = { ...r, at: performance.now() }; message(`${ch} SKIPPED \u00B7 X TO UNDO`, 5000); }
    else message(`${ch} RESTORED`, 2500);
    saveSkips();
    menu.refresh();
  });
  // 0-9: tune by number, like a remote: three digits tune at once, fewer after a pause.
  let digits = '', digitTimer = null;
  const tuneDigits = () => {
    clearTimeout(digitTimer);
    const n = Number(digits);
    digits = '';
    if (!n) { message('CH ---', 1000); return; }
    lastUserChange = performance.now();
    const r = tuneTo(n);
    if (r === 'range') message(`NO CH ${String(n).padStart(3, '0')}`, 2000);
    else if (r === 'heavy') message(`CH ${String(n).padStart(3, '0')} \u00B7 NEEDS BIG GPU SCENES (MENU)`, 3000);
  };
  const digit = control((d) => {
    digits += d;
    message(`CH ${digits.padEnd(3, '-')}`, 1600);
    clearTimeout(digitTimer);
    if (digits.length >= 3) tuneDigits();
    else digitTimer = setTimeout(tuneDigits, 1500);
  });
  const hold = control(() => {
    setHold(!autopilotState.hold);
    holdBtn.setAttribute('aria-pressed', String(autopilotState.hold));
    message(autopilotState.hold ? 'HOLD' : 'HOLD OFF');
  });
  document.getElementById('chUp').addEventListener('click', next);
  document.getElementById('chDown').addEventListener('click', prev);
  document.getElementById('fxBtn').addEventListener('click', fx);
  document.getElementById('rollBtn').addEventListener('click', roll);
  holdBtn.addEventListener('click', hold);

  // --- TIME: bars per scene ---
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
      if (powerState.on) message(`${bars} BARS PER SCENE`);
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
  viewerState.react = Math.max(REACT_MIN, Math.min(REACT_MAX, Number(load(REACT_KEY, REACT_DEFAULT)) || REACT_DEFAULT));
  const reactDial = makeDial(document.getElementById('reactDial'), {
    initial: toDial(viewerState.react),
    locked: () => live(),
    onChange: (v) => {
      viewerState.react = REACT_MIN + v * (REACT_MAX - REACT_MIN);
      save(REACT_KEY, viewerState.react);
      if (powerState.on) message(`REACT ${Math.round(viewerState.react * 100)}%`);
    },
  });
  document.getElementById('reactDial').addEventListener('dblclick', control(() => reactDial.set(toDial(REACT_DEFAULT))));
  const stepReact = control((dir) => reactDial.set(reactDial.get() + dir * 0.05));

  const about = document.getElementById('about');
  const schedulePage = document.getElementById('schedule');
  const keysPage = document.getElementById('keys');
  const menuBtn = document.getElementById('menuBtn');
  const scheduleBtn = document.getElementById('scheduleBtn');

  // --- Service MENU (menu.js): SYNC, REACT, TIME, Reduced motion, Heavy scenes, Schedule, About ---
  viewerState.syncMs = Math.max(SYNC_MIN, Math.min(SYNC_MAX, Number(load('lsd.sync', 0)) || 0));
  viewerState.announce = load('lsd.announce', false) === true;
  viewerState.reducedMode = MODES.includes(load('lsd.reduced', 'auto')) ? load('lsd.reduced', 'auto') : 'auto';
  viewerState.heavyMode = MODES.includes(load('lsd.heavy', 'auto')) ? load('lsd.heavy', 'auto') : 'auto';
  viewerState.autoRoll = AUTO_ROLL_STEPS.includes(load('lsd.autoRoll', 0)) ? load('lsd.autoRoll', 0) : 0;
  viewerState.bgMode = BG_MODES.includes(load('lsd.bgMode', 'hold')) ? load('lsd.bgMode', 'hold') : 'hold';
  viewerState.effects = load('lsd.effects', true) !== false;
  viewerState.follow = load('lsd.follow', true) !== false;
  setAutoRollBars(viewerState.autoRoll);
  setBackgroundMode(viewerState.bgMode);
  setEffects(viewerState.effects);
  setFollowMusic(viewerState.follow);
  setSkipped(Array.isArray(load('lsd.skipped', [])) ? load('lsd.skipped', []) : []);
  const stepIn = (list, v, dir) => list[Math.max(0, Math.min(list.length - 1, list.indexOf(v) + dir))];
  const applyAutoRoll = (bars) => { viewerState.autoRoll = bars; save('lsd.autoRoll', bars); setAutoRollBars(bars); };
  const applyBgMode = (mode) => { viewerState.bgMode = mode; save('lsd.bgMode', mode); setBackgroundMode(mode); };
  const applyEffects = (on) => { viewerState.effects = on; save('lsd.effects', on); setEffects(on); };
  const applyFollow = (on) => { viewerState.follow = on; save('lsd.follow', on); setFollowMusic(on); };
  const applyHeavy = () => {
    if (viewerState.heavyMode === 'auto') setHeavyAllowed(!!window.LSD?.gpu?.heavy);
    else setHeavyAllowed(viewerState.heavyMode === 'on');
  };
  if (viewerState.heavyMode !== 'auto') applyHeavy();
  // Reduced motion: REACT capped (renderer.js), calmer rotation (autopilot.setReducedMotion).
  const applyReduced = () => {
    viewerState.reducedNow = viewerState.reduced;
    setReducedMotion(viewerState.reducedNow);
    document.body.classList.toggle('reduced-motion', viewerState.reducedNow); // the page's own small motions (tv.css)
  };
  applyReduced();
  if (typeof matchMedia === 'function') matchMedia('(prefers-reduced-motion: reduce)').addEventListener?.('change', applyReduced);
  const cycle = (mode, dir) => MODES[(MODES.indexOf(mode) + dir + MODES.length) % MODES.length];
  const onOff = (b) => (b ? 'ON' : 'OFF');
  const rows = [
    { label: 'SYNC', help: 'Delay the picture so its beats line up with what you hear (Bluetooth speakers are often 150-250 ms late).', value: () => `${viewerState.syncMs > 0 ? '+' : ''}${viewerState.syncMs} MS`,
      step: (dir) => {
        viewerState.syncMs = Math.max(SYNC_MIN, Math.min(SYNC_MAX, viewerState.syncMs + dir * 10));
        save('lsd.sync', viewerState.syncMs);
      } },
    { label: 'TAP SYNC', value: () => 'TAP 8 BEATS', run: () => startTapSync(),
      help: 'Press Enter, then tap Space or the picture on 8 beats you hear: sets SYNC for you.' },
    { label: 'ANNOUNCE SCENES', value: () => onOff(viewerState.announce),
      step: () => { viewerState.announce = !viewerState.announce; save('lsd.announce', viewerState.announce); },
      help: 'Show the name of each new scene, its background and its effects when it comes on, and the background when it changes. Off: only when you click the picture (or turn INFO on).' },
    { label: 'REACT', locked: live, help: 'How strongly the picture moves with the music. Raise it for quiet shows, lower it if it is too busy.', value: () => `${Math.round(viewerState.react * 100)}%`,
      step: (dir) => reactDial.set(toDial(Math.max(REACT_MIN, Math.min(REACT_MAX, viewerState.react + dir * 0.05)))) },
    { label: 'TIME', locked: live, help: 'How long each scene stays before the next one, in bars of the music.', value: () => timeLabel.textContent,
      step: (dir) => {
        const i = Math.round(timeDial.get() * (TIME_STEPS.length - 1));
        timeDial.set(Math.max(0, Math.min(TIME_STEPS.length - 1, i + dir)) / (TIME_STEPS.length - 1));
      } },
    { label: 'AUTO ROLL', locked: live, value: () => (viewerState.autoRoll ? `EVERY ${viewerState.autoRoll} BARS` : 'OFF'),
      step: (dir) => applyAutoRoll(stepIn(AUTO_ROLL_STEPS, viewerState.autoRoll, dir)),
      help: 'Rolls a new variation of the scene on air every few bars, like pressing ROLL (D). Best with HOLD on: the scene stays and keeps changing.' },
    { label: 'BACKGROUND', locked: () => live() || viewerState.bgMode === 'off',
      value: () => (viewerState.bgMode === 'off' ? 'OFF' : prettyName(nowShowing().backgroundPicked ?? '') || '---'),
      step: (dir) => stepBackground(dir).then(() => menu.refresh()),
      help: 'The picture behind the scene, which shows through wherever the scene is dark or see-through: the Spaz Radio photo gallery to start with. \u25C0 \u25B6 or B pick another one now.' },
    { label: 'BG CHANGES', locked: live, value: () => BG_MODE_LABELS[viewerState.bgMode],
      step: (dir) => applyBgMode(BG_MODES[(BG_MODES.indexOf(viewerState.bgMode) + dir + BG_MODES.length) % BG_MODES.length]),
      help: 'Hold (the default): the background stays until you change it. With scenes: a new one every two scenes (TIME). Off: no background, just the scene.' },
    { label: 'EFFECTS', locked: live, value: () => (viewerState.effects ? 'AUTO' : 'OFF'),
      step: () => applyEffects(!viewerState.effects),
      help: 'Auto: every scene and background gets an FX chain that suits it (FX or F picks another). Off: the scenes play clean.' },
    { label: 'FOLLOW MUSIC', locked: live, value: () => onOff(viewerState.follow),
      step: () => applyFollow(!viewerState.follow),
      help: 'On: a calm scene comes in for a breakdown, a hard one cuts in on the drop, and the tempo leans the picks. Off: the scenes just take turns.' },
    { label: 'SKIP THIS CHANNEL', locked: live, value: () => (nowShowing().skipped ? 'SKIPPED' : `CH ${String(nowShowing().channel).padStart(3, '0')}`),
      run: () => skip(),
      help: 'Skipped channels never come up by themselves (X does this too). Type a channel number to watch one again; X there restores it.' },
    { label: 'SKIPPED', value: () => { const n = skippedChannels().length; return n ? `${n} CHANNEL${n === 1 ? '' : 'S'}` : 'NONE'; },
      run: () => { menu.hide(); skippedPage.show(); },
      help: 'The channels you skipped: pick one to watch it, then X restores it.' },
    { label: 'REDUCED MOTION', help: 'Gentler: no flashes, no hard scenes, slower changes. Auto follows your system setting.',
      value: () => (viewerState.reducedMode === 'auto' ? `AUTO (${onOff(viewerState.reduced)})` : viewerState.reducedMode.toUpperCase()),
      step: (dir) => { viewerState.reducedMode = cycle(viewerState.reducedMode, dir); save('lsd.reduced', viewerState.reducedMode); applyReduced(); } },
    { label: 'BIG GPU SCENES', locked: live,
      help: 'The most demanding 3D fractals and simulations. Auto: on if your graphics card passed a quick test at power-on. On: always (may stutter on laptops and phones). Off: never, for a smoother picture.',
      value: () => {
        if (viewerState.heavyMode !== 'auto') return viewerState.heavyMode.toUpperCase();
        const gpu = window.LSD?.gpu;
        return gpu?.state === 'done' ? `AUTO (${onOff(gpu.heavy)}, ${gpu.ms.toFixed(0)} MS)` : `AUTO (${onOff(gpu?.heavy)})`;
      },
      step: (dir) => { viewerState.heavyMode = cycle(viewerState.heavyMode, dir); save('lsd.heavy', viewerState.heavyMode); applyHeavy(); } },
    { label: 'INPUT', locked: live, value: () => (isMicOn() ? 'MICROPHONE' : 'RADIO'),
      help: 'What the picture listens to. Radio: the Spaz Radio stream. Microphone: the sound in the room (the browser asks first; it is analysed only, never played back).',
      step: () => toggleMic() },
    { label: 'FULLSCREEN', value: () => (document.fullscreenElement ? 'ON' : 'OFF'), step: () => toggleFullscreen(), run: () => toggleFullscreen(),
      help: 'The picture and its on-screen display fill the screen. Double-click the picture or press S to toggle; Esc leaves.' },
    { label: 'RESET', help: 'Put every setting back to its default. Skipped channels stay skipped (MENU \u203A SKIPPED).', run: () => {
      viewerState.syncMs = 0; save('lsd.sync', 0);
      viewerState.reducedMode = 'auto'; save('lsd.reduced', 'auto'); applyReduced();
      viewerState.heavyMode = 'auto'; save('lsd.heavy', 'auto'); applyHeavy();
      viewerState.announce = false; save('lsd.announce', false);
      if (!live()) {
        reactDial.set(toDial(REACT_DEFAULT)); timeDial.set(TIME_STEPS.indexOf(16) / (TIME_STEPS.length - 1));
        applyAutoRoll(0); applyBgMode('hold'); applyEffects(true); applyFollow(true);
      }
    } },
    ...(kiosk ? [] : [{ label: 'CHAT', help: 'The Spaz Radio chat, on a blackboard beside the TV: the same room as the Spaz Radio app. Press C or the CHAT button any time.',
      value: () => (chatboard?.open ? 'OPEN' : 'CLOSED'), step: () => chatboard?.toggle(), run: () => chatboard?.toggle() }]),
    { label: 'SCHEDULE', help: 'Spaz Radio\u2019s upcoming shows, in your local time. Press G or the SCHEDULE button to open it any time.', value: () => 'SPAZ RADIO', run: () => { menu.hide(); schedule.show(); } },
    { label: 'ABOUT', help: 'About Liquid LSD, the software that makes these visuals.', value: () => 'LIQUID LSD', run: () => { menu.hide(); about.hidden = false; } },
    { label: 'EXIT', run: () => menu.hide() },
  ];
  /** Listens to the microphone instead of the radio, or back. */
  const toggleMic = control(async () => {
    if (audioBlocked) { message('CLICK OR PRESS A KEY FOR SOUND', 2000); return; }
    if (!micAvailable()) { message('NO MICROPHONE ACCESS HERE', 3000); return; }
    try {
      await setMic(!isMicOn());
      message(isMicOn() ? 'INPUT \u00B7 MICROPHONE' : 'INPUT \u00B7 RADIO', 2500);
    } catch (err) {
      console.warn('Microphone unavailable:', err);
      message('MICROPHONE BLOCKED', 3000);
    }
    menu.refresh();
  });
  function toggleFullscreen() {
    if (!screen.requestFullscreen) { message('FULLSCREEN NOT AVAILABLE HERE', 3000); return; }
    if (!document.fullscreenElement) {
      // Going full screen from the menu closes it, so the Esc that would close it can't also leave full screen.
      menu.hide();
      // Where the browser allows it (Chromium), keep Esc for the page: a tap closes the menu or leaves full
      // screen through the keyboard handler below, and holding it still leaves full screen.
      screen.requestFullscreen().then(() => navigator.keyboard?.lock?.(['Escape'])).catch(console.error);
    } else document.exitFullscreen().catch(console.error);
  }
  document.addEventListener('fullscreenchange', () => {
    if (!document.fullscreenElement) navigator.keyboard?.unlock?.();
    menu.refresh();
  });
  window.addEventListener('lsd-mic', () => menu.refresh());

  // TAP SYNC: tap Space (or the picture) on 8 beats you hear; the median lateness against the locked beat
  // clock becomes SYNC. Needs a locked beat (or a live broadcast), and taps that agree within ~150 ms.
  const TAPS = 8;
  let tapping = null;
  function startTapSync() {
    if (!(cvState.beatLocked || (live() && autopilotState.clock.valid))) { menu.hide(); message('NO BEAT LOCK YET \u00B7 TRY AGAIN SOON', 3000); return; }
    menu.hide();
    tapping = { times: [], timer: null };
    message(`TAP SPACE OR THE PICTURE ON THE BEAT \u00B7 ${TAPS}`, 6000);
    armTapTimeout();
  }
  function armTapTimeout() {
    clearTimeout(tapping.timer);
    tapping.timer = setTimeout(() => { tapping = null; message('TAP SYNC CANCELLED', 2000); }, 6000);
  }
  function tapSync() {
    tapping.times.push(performance.now());
    const left = TAPS - tapping.times.length;
    if (left > 0) { message(`TAP \u00B7 ${left}`, 6000); armTapTimeout(); return; }
    clearTimeout(tapping.timer);
    const bpm = live() ? autopilotState.clock.bpm : cvState.bpm;
    const { ms: late, spread } = tapOffset(tapping.times, (t) => heardBeats(t, 0), bpm);
    tapping = null;
    if (spread > 150) { message('TAPS TOO UNEVEN \u00B7 TRY AGAIN', 3000); return; }
    const ms = Math.max(SYNC_MIN, Math.min(SYNC_MAX, Math.round(late / 10) * 10));
    viewerState.syncMs = ms;
    save('lsd.sync', ms);
    message(`SYNC ${ms > 0 ? '+' : ''}${ms} MS`, 3000);
  }
  viewerState.tapSync = () => tapping && tapSync(); // for the headless check

  const menu = createMenu(screen, rows, {
    version: TV_VERSION,
    onOpen: () => { screen.classList.add('menu-open'); menuBtn.setAttribute('aria-pressed', 'true'); }, // the OSD steps aside
    onClose: () => { screen.classList.remove('menu-open'); menuBtn.setAttribute('aria-pressed', 'false'); },
  });
  const toggleMenu = () => { if (powerState.on) { schedule.hide(); menu.toggle(); } };
  menuBtn.addEventListener('click', toggleMenu);
  viewerState.menu = menu;

  // --- SCHEDULE (schedule.js): Spaz Radio's upcoming shows, like ABOUT; any key or click closes it ---
  const schedule = {
    get open() { return !schedulePage.hidden; },
    show() {
      if (!powerState.on) return;
      menu.hide();
      about.hidden = true;
      keysPage.hidden = true;
      schedulePage.hidden = false;
      scheduleBtn.setAttribute('aria-pressed', 'true');
      // A list older than 10 minutes (or none yet) is fetched again; the page fills in when it lands.
      if (Date.now() - scheduleState.fetchedAt > SCHEDULE_STALE_MS) {
        refreshSchedule().then(() => { if (!schedulePage.hidden) renderSchedule(); });
      }
      renderSchedule();
    },
    hide() { schedulePage.hidden = true; scheduleBtn.setAttribute('aria-pressed', 'false'); },
    toggle() { schedulePage.hidden ? schedule.show() : schedule.hide(); },
    /** The headless check's fixture: parsed shows ({start, end, name}) instead of the network. */
    setShows,
  };
  function renderSchedule() {
    const now = Date.now();
    const days = groupByDay(scheduleState.shows, now);
    const zone = zoneLabel(now);
    schedulePage.querySelector('.sc-zone').textContent = zone ? `TIMES IN ${zone}` : '';
    const list = schedulePage.querySelector('.sc-list');
    list.replaceChildren();
    if (!days.length) {
      const empty = document.createElement('div');
      empty.className = 'sc-empty';
      empty.textContent = scheduleState.loading && !scheduleState.fetchedAt ? 'LOADING\u2026'
        : scheduleState.failed ? 'SCHEDULE UNAVAILABLE \u00B7 TRY AGAIN LATER' : 'NO SHOWS SCHEDULED';
      list.appendChild(empty);
      return;
    }
    for (const { day, shows } of days) {
      const head = document.createElement('div');
      head.className = 'sc-day';
      head.textContent = day;
      list.appendChild(head);
      for (const s of shows) {
        const row = document.createElement('div');
        row.className = 'sc-show' + (s.onAir ? ' on-air' : '');
        row.setAttribute('role', 'listitem');
        const time = document.createElement('span');
        time.className = 'sc-time';
        time.textContent = s.time;
        const name = document.createElement('span');
        name.className = 'sc-show-name';
        if (s.onAir) {
          const tag = document.createElement('span');
          tag.className = 'sc-on-air';
          tag.textContent = 'ON AIR \u00B7';
          name.appendChild(tag);
        }
        name.append(s.name); // text, never markup: names come from the station's feed
        row.append(time, name);
        list.appendChild(row);
      }
    }
  }
  scheduleBtn.addEventListener('click', () => schedule.toggle());
  schedulePage.addEventListener('click', () => schedule.hide());

  // --- ? : every keyboard shortcut, like ABOUT; any key or click closes it ---
  const showKeys = () => {
    if (!powerState.on) return;
    menu.hide();
    schedule.hide();
    about.hidden = true;
    keysPage.hidden = false;
  };
  keysPage.addEventListener('click', () => { keysPage.hidden = true; });

  // --- MENU › SKIPPED: the skipped channels; picking one tunes to it (X there restores it). ↑↓ Enter, Esc ---
  const skippedEl = document.getElementById('skipped');
  const skippedList = skippedEl.querySelector('.sk-list');
  let skipSel = 0;
  const skippedPage = {
    get open() { return !skippedEl.hidden; },
    show() {
      if (!powerState.on) return;
      menu.hide(); schedule.hide(); about.hidden = true; keysPage.hidden = true;
      skipSel = 0;
      skippedEl.hidden = false;
      this.render();
    },
    hide() { skippedEl.hidden = true; },
    render() {
      const list = skippedChannels();
      skippedList.replaceChildren();
      if (!list.length) {
        const empty = document.createElement('div');
        empty.className = 'sk-empty';
        empty.textContent = 'NO SKIPPED CHANNELS. PRESS X ON A CHANNEL TO SKIP IT.';
        skippedList.appendChild(empty);
        return;
      }
      skipSel = Math.min(skipSel, list.length);
      list.forEach((c, i) => {
        const row = document.createElement('button');
        row.className = 'sk-row' + (i === skipSel ? ' sel' : '');
        row.setAttribute('role', 'listitem');
        const ch = document.createElement('span');
        ch.className = 'sk-ch';
        ch.textContent = `CH ${String(c.channel).padStart(3, '0')}`;
        const name = document.createElement('span');
        name.textContent = prettyName(c.name).toUpperCase();
        row.append(ch, name);
        row.addEventListener('click', (e) => { e.stopPropagation(); this.pick(i); });
        skippedList.appendChild(row);
      });
      // Last row: RESTORE ALL
      const all = document.createElement('button');
      all.className = 'sk-row sk-all' + (skipSel === list.length ? ' sel' : '');
      all.textContent = 'RESTORE ALL';
      all.addEventListener('click', (e) => { e.stopPropagation(); this.pick(list.length); });
      skippedList.appendChild(all);
      skippedList.children[skipSel]?.scrollIntoView?.({ block: 'nearest' });
    },
    pick(i) {
      const list = skippedChannels();
      if (i === list.length && list.length) {   // RESTORE ALL
        restoreAllChannels();
        saveSkips();
        this.hide();
        message(`${list.length} CHANNEL${list.length === 1 ? '' : 'S'} RESTORED`, 2500);
        return;
      }
      const c = list[i];
      this.hide();
      if (!c) return;
      if (live()) { message('LIVE \u00B7 CONTROLS LOCKED', 2000); return; }
      lastUserChange = performance.now();
      const r = tuneTo(c.channel);
      if (r === 'heavy') message(`CH ${String(c.channel).padStart(3, '0')} \u00B7 NEEDS BIG GPU SCENES (MENU)`, 3000);
      else message(`CH ${String(c.channel).padStart(3, '0')} \u00B7 SKIPPED \u00B7 X RESTORES IT`, 4000);
    },
    key(e) {
      const n = skippedChannels().length && skippedChannels().length + 1; // the channels and RESTORE ALL
      if (e.key === 'ArrowDown' && n) { skipSel = (skipSel + 1) % n; this.render(); }
      else if (e.key === 'ArrowUp' && n) { skipSel = (skipSel + n - 1) % n; this.render(); }
      else if ((e.key === 'Enter' || e.key === ' ') && n) this.pick(skipSel);
      else this.hide();
    },
  };
  skippedEl.addEventListener('click', () => skippedPage.hide());
  viewerState.skippedPage = skippedPage;

  // --- INFO: keeps the info lines (channel, scene, background, FX, track, next show) on screen ---
  const infoBtn = document.getElementById('infoBtn');
  function setInfo(on) {
    viewerState.info = on;
    save('lsd.info', on);
    infoBtn.setAttribute('aria-pressed', String(on));
    if (on) showLook(undefined, 4000, true);
    else for (const id of INFO_IDS) unflash(id);
  }
  const toggleInfo = () => { if (powerState.on) setInfo(!viewerState.info); };
  viewerState.info = load('lsd.info', false) === true;
  infoBtn.setAttribute('aria-pressed', String(viewerState.info));
  infoBtn.addEventListener('click', toggleInfo);
  viewerState.schedule = schedule;

  // --- CHAT (chat.js, chatboard.js): the Spaz Radio chat on a blackboard beside the TV. It connects at page
  // load whether the TV is on or off, and not at all under ?kiosk / ?embed (the Spaz Radio app has its own
  // chat). The headless checks (?nolive) never reach the real room: they start it on a fake broker. ---
  if (!kiosk) {
    let storage = null;
    try { storage = localStorage; } catch { /* private window: nothing remembered */ }
    const chat = createChat({ storage, onChange: (what) => chatboard?.onChange(what), onLive: (msg) => chatboard?.onLive(msg) });
    chatboard = createChatboard({ chat, board: document.getElementById('chalkboard'), button: document.getElementById('chatBtn'),
      reduced: () => viewerState.reducedNow, storage });
    const noLive = new URLSearchParams(location.search).has('nolive');
    if (!noLive) chat.start();
    document.getElementById('chatBtn').addEventListener('click', () => { chatboard.toggle(); menu.refresh(); });
    window.addEventListener('online', () => chat.reconnectNow());
    window.addEventListener('pagehide', () => chat.leave()); // clears our name from the room at once
    window.addEventListener('pageshow', (e) => { if (e.persisted && !noLive) chat.start(); }); // back from the cache
    viewerState.chat = { board: chatboard, chat };
  }

  // --- Keyboard ---
  window.addEventListener('keydown', (e) => {
    if (e.ctrlKey || e.metaKey || e.altKey || e.target.closest?.('input, textarea')) return;
    if (!about.hidden) { e.preventDefault(); about.hidden = true; return; }
    if (schedule.open) { e.preventDefault(); schedule.hide(); return; }
    if (!keysPage.hidden) { e.preventDefault(); keysPage.hidden = true; return; }
    if (skippedPage.open) { e.preventDefault(); skippedPage.key(e); return; }
    if (tapping && (e.key === ' ' || e.key === 'Enter')) { e.preventDefault(); tapSync(); return; }
    // With the menu open these still work: ? and G and I close it and do their thing, C toggles the chat.
    if (menu.open && ['?', 'i', 'I', 'g', 'G'].includes(e.key)) menu.hide();
    if (!(menu.open && (e.key === 'c' || e.key === 'C')) && menu.key(e)) { e.preventDefault(); return; }
    if ((e.key === 'm' || e.key === 'M') && powerState.on) { e.preventDefault(); menu.show(); return; }
    if ((e.key === 'g' || e.key === 'G') && powerState.on) { e.preventDefault(); schedule.show(); return; }
    if ((e.key === 'c' || e.key === 'C') && chatboard) { e.preventDefault(); chatboard.toggle(); menu.refresh(); return; }
    if (e.key === '?') { e.preventDefault(); showKeys(); return; }
    const keys = {
      ArrowRight: next, n: next, N: next,
      ArrowLeft: prev, p: prev, P: prev,
      r: random, R: random,
      ArrowUp: () => stepVolume(1), ArrowDown: () => stepVolume(-1),
      f: fx, F: fx, h: hold, H: hold, ' ': hold,
      d: roll, D: roll, b: nextBackground, B: nextBackground, x: skip, X: skip,
      ...Object.fromEntries([...'0123456789'].map((d) => [d, () => digit(d)])),
      '[': () => stepTime(-1), ']': () => stepTime(1),
      '-': () => stepReact(-1), '=': () => stepReact(1), '+': () => stepReact(1),
      Escape: () => document.fullscreenElement && document.exitFullscreen().catch(console.error),
      s: toggleFullscreen, S: toggleFullscreen, a: toggleMic, A: toggleMic,
      i: toggleInfo, I: toggleInfo,
    };
    const fn = keys[e.key];
    if (!fn) return;
    if (e.target.closest?.('button') && (e.key === ' ' || e.key === 'Enter')) return; // let buttons click
    e.preventDefault();
    fn();
  });

  // --- Touch on the picture: swipe left = next scene, right = previous, tap = info, long press = hold ---
  let touch = null;
  canvas.addEventListener('pointerdown', () => { if (tapping) tapSync(); });
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
  canvas.addEventListener('click', () => showLook(undefined, 4000, true));

  // --- Fullscreen: double-click the picture, S, or MENU (the OSD comes along) ---
  canvas.addEventListener('dblclick', toggleFullscreen);

  // --- Phone lock screen / media keys: play-pause is the power switch, next/previous change the scene ---
  if ('mediaSession' in navigator) {
    const ms = navigator.mediaSession;
    const handle = (action, fn) => { try { ms.setActionHandler(action, fn); } catch { /* action not supported */ } };
    handle('play', () => { if (!powerState.on) powerSwitch.click(); });
    handle('pause', () => { if (powerState.on) powerSwitch.click(); });
    handle('stop', () => { if (powerState.on) powerSwitch.click(); });
    handle('nexttrack', next);
    handle('previoustrack', prev);
  }

  // --- Sound after a blocked start: the first click or key anywhere starts it ---
  const unblock = async () => {
    if (!audioBlocked || !powerState.on) return;
    try { await startAudio(); audioBlocked = false; showLook(); } catch { /* still blocked or no stream */ }
  };
  window.addEventListener('pointerdown', unblock);
  window.addEventListener('keydown', unblock);

  // --- Kiosk (?kiosk): no TV body; turns itself on; the cursor hides when idle ---
  if (kiosk) {
    document.body.classList.add('kiosk');
    if (embed) document.body.classList.add('embed');
    let idle = null;
    const wake = () => { document.body.classList.remove('idle'); clearTimeout(idle); idle = setTimeout(() => document.body.classList.add('idle'), 3000); };
    window.addEventListener('mousemove', wake);
    wake();
    setTimeout(() => { if (!powerState.on) powerSwitch.click(); }, 300);
  }

  // --- Installable app: the service worker (sw.js) caches the app shell; not in the headless checks ---
  if (!embed && 'serviceWorker' in navigator && !new URLSearchParams(location.search).has('nolive')) {
    navigator.serviceWorker.register('sw.js').catch((err) => console.warn('[lsd] service worker:', err.message));
  }

  // --- Autopilot and broadcast events ---
  // The station badge: the channel on air, three digits so the badge keeps its width (CH.001 .. CH.100).
  const stationText = document.querySelector('#station-badge .station-text');
  const updateBadge = (info = nowShowing()) => {
    const ch = info.channel > 0 ? String(info.channel).padStart(3, '0') : '---';
    stationText.textContent = live() ? 'SPAZ RADIO \u2022 LIVE' : `SPAZ RADIO \u2022 CH.${ch}`;
  };
  // The first scene on air means the playlist has loaded: drop stored skips for channels it no longer has.
  let skipsPruned = false;
  window.addEventListener('lsd-now-showing', (e) => {
    announceLook(e.detail); updateMediaSession(); updateBadge(e.detail);
    if (!skipsPruned) { skipsPruned = true; save('lsd.skipped', prunedSkips()); }
    menu.refresh();
  });
  // The background changes on its own clock: with ANNOUNCE SCENES on, only its line comes up.
  window.addEventListener('lsd-background', (e) => {
    if ((viewerState.announce || viewerState.info) && powerState.on && !autopilotState.isLiveBroadcast && e.detail.background) {
      flash('osd-bg', `BACKGROUND: ${prettyName(e.detail.background)}`, 4000);
    }
    menu.refresh();   // MENU › BACKGROUND names the new one
  });
  window.addEventListener('lsd-broadcast-status', (e) => {
    tvBody.classList.toggle('live', e.detail.live);
    updateBadge();
    stationText.title = e.detail.live && autopilotState.broadcaster ? `Live: ${autopilotState.broadcaster}` : '';
    for (const id of ['chUp', 'chDown', 'fxBtn', 'rollBtn', 'holdBtn']) document.getElementById(id).setAttribute('aria-disabled', String(e.detail.live));
    if (e.detail.live) {
      setHold(false);
      holdBtn.setAttribute('aria-pressed', 'false');
      menu.hide();
      // The station ident: LIVE and the broadcaster's name roll in over the picture (if the TV is on).
      const card = document.getElementById('title-card');
      card.querySelector('.tc-name').textContent = autopilotState.broadcaster ?? '';
      cardUp(4500);
      card.classList.remove('show');
      void card.offsetWidth; // restart the animation
      card.classList.add('show');
    }
    showLook();
    updateMediaSession();
  });

  // --- ABOUT (MENU › ABOUT): any key or a click outside the link closes it ---
  about.addEventListener('click', (e) => { if (!e.target.closest('a')) about.hidden = true; });

  // --- Bumper: about every 30 minutes on air, a station ident line for 8 seconds (not while live or
  // while the menu or ABOUT is open; the timer restarts at power-on) ---
  const bumper = document.getElementById('bumper');
  setInterval(() => {
    if (embed || !powerState.on || live() || menu.open || !about.hidden || schedule.open) return;
    if (performance.now() - lastBumper < BUMPER_EVERY_MS) return;
    lastBumper = performance.now();
    showBumper();
  }, 15000);
  function showBumper() {
    bumper.innerHTML = 'YOU\u2019RE WATCHING LIQUID LSD ON SPAZ RADIO \u00B7 MAKE YOUR OWN: <span class="bm-site">LIQUID-LSD.GREENJON.COM</span>';
    bumper.classList.add('show');
    setTimeout(() => bumper.classList.remove('show'), 8000);
  }
  viewerState.showBumper = showBumper; // for the headless check

  // --- Beat LED, and NO SIGNAL while the stream is down ---
  const beatLed = document.getElementById('beatLed');
  let noSignalShown = false;
  (function blink() {
    // Standing notices only fill the message line when nothing else (HOLD, REACT...) is showing there.
    const msgFree = !document.getElementById('osd-msg').classList.contains('show');
    if (audioBlocked && powerState.on) { if (msgFree) message('CLICK OR PRESS A KEY FOR SOUND', 1500); noSignalShown = true; }
    else if (cvState.noSignal && powerState.on && !live()) { if (msgFree) message('NO SIGNAL', 1500); noSignalShown = true; }
    else if (noSignalShown) { noSignalShown = false; announceLook(); }
    const isLive = live() && autopilotState.clock.valid;
    const beats = heardBeats(performance.now(), viewerState.syncMs);
    beatLed.classList.toggle('on', powerState.on && ((beats % 1) + 1) % 1 < 0.15);
    beatLed.classList.toggle('locked', isLive || cvState.beatLocked);
    requestAnimationFrame(blink);
  })();
}

// -------------------------------------------------------
// Media Session: what the phone's lock screen and the OS media controls show (the show playing on the
// radio, and the scene on air).
// -------------------------------------------------------
function updateMediaSession() {
  if (embed) return;
  if (!('mediaSession' in navigator) || typeof MediaMetadata === 'undefined') return;
  const look = autopilotState.isLiveBroadcast ? 'LIVE' : prettyName(nowShowing().name).toUpperCase();
  navigator.mediaSession.metadata = new MediaMetadata({
    title: nowPlaying || 'Spaz Radio',
    artist: 'Spaz Radio',
    album: look ? `Liquid LSD \u00B7 ${look}` : 'Liquid LSD',
    artwork: [
      { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
      { src: 'icon-512.png', sizes: '512x512', type: 'image/png' },
    ],
  });
  navigator.mediaSession.playbackState = powerState.on ? 'playing' : 'paused';
}

// -------------------------------------------------------
// Wake lock: a laptop or phone used as a TV must not dim and lock while the TV is on. The browser drops
// the lock when the page is hidden, so it is taken again when the page comes back.
// -------------------------------------------------------
let wakeLock = null;
async function holdScreenAwake() {
  if (embed) return;
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
  if (embed) return;
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
      updateMediaSession();
      flash('osd-track', `♪ ${nowPlaying}`, 6000);
      flashNext(6000);
    }
  } catch { /* status unavailable: no track line */ }
  pollTimer = setTimeout(pollNowPlaying, 30000);
}

// -------------------------------------------------------
// Spaz Radio schedule (schedule.js), refreshed at power-on and hourly while the TV is on. Not in embed (the
// host app has its own) or in the headless checks (?nolive: the check gives the page a fixture).
// -------------------------------------------------------
let scheduleTimer = null;
function pollSchedule() {
  clearTimeout(scheduleTimer);
  if (embed || !powerState.on || new URLSearchParams(location.search).has('nolive')) return;
  refreshSchedule();
  scheduleTimer = setTimeout(pollSchedule, SCHEDULE_REFRESH_MS);
}

// Auto-init when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initUI);
} else {
  initUI();
}
