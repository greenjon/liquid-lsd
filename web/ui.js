// ui.js
// The TV's controls: power, volume, the viewer controls (channel rocker, FX, HOLD, TIME, REACT), the
// on-screen display, keyboard and touch. Viewer controls are locked while the desktop broadcasts live.
import { startAudio, stopAudio, setVolume, setMic, isMicOn, micAvailable, cvState, tapeStatus, setEq, eqGains, EQ_MIN_DB, EQ_MAX_DB, radioBypassesGraph, elementVolumeFixed } from './dsp.js';
import { embed } from './embedmode.js';
import { liteState, liteNext, setVisuals, canSwitch } from './lite.js';
import { autopilotState, advanceForeground, previousForeground, randomForeground, nextFxChain, setHold, setHoldBars, nowShowing, setHeavyAllowed, setReducedMotion,
  rollScene, setAutoRollBars, setEffects, setFollowMusic, setBackgroundMode, stepBackground,
  tuneTo } from './autopilot.js';
import { createMenu } from './menu.js';
import { heardBeats, tapOffset } from './sync.js';
import { createChat } from './chat.js';
import { createChatboard } from './chatboard.js';
import { createTapeDeck } from './tapedeck.js';
import { appUrl, parseTapeLink, archiveFileFor, tapesState, restoreTapes, refreshTapes, tapesStale } from './tapes.js';
import { timeoutSignal, scheduleState, refreshSchedule, setShows, groupByDay, upcoming, parsePlaying, loadPlayingJsonp, zoneLabel, nextLine, SCHEDULE_REFRESH_MS, SCHEDULE_STALE_MS } from './schedule.js';

// Power state — imported by renderer.js each frame
// Shown in the MENU title so a viewer can tell whether the browser has the latest code. Bump it with
// every publish that changes code (scripts/publish_web_tv.sh warns when spaz.org already has this one).
export const TV_VERSION = 'v71';
// Channel numbers (badge, OSD, INFO, tuning by digits) are hidden: a number names only the scene, not the
// background and FX on with it, so it can't bring a look back. Kept behind this switch in case they return.
const SHOW_CHANNELS = false;
// The scene, background and FX lines (INFO, a click on the picture, CH / FX / B presses) are hidden too: INFO is
// about the radio now. Kept behind this switch in case they return.
const SHOW_SCENE_INFO = false;

export const powerState = {
  on: false,              // is the TV powered on?
  warmupProgress: 0,      // 0..1, advanced by renderer.js when turning on
  shutdownProgress: 1.0,  // 0..1, advanced by renderer.js when turning off (starts at 1.0 = off)
};

// Viewer settings — renderer.js scales every audio signal by `react`
export const viewerState = {
  // Reads radio.spaz.org/playing ([LIVE!]). Null under ?nolive, where the headless check puts in a stub.
  readPlaying: new URLSearchParams(globalThis.location?.search ?? '').has('nolive') ? null : readPlayingFeed,
  react: 1.0,
  info: false,         // INFO: the info lines stay on screen
  awake: false,        // a screen wake lock is held
  syncMs: 0,           // SYNC: how late the sound reaches the viewer (speakers, Bluetooth), in ms
  reducedMode: 'auto', // Reduced motion: 'auto' (follows the system setting), 'on', 'off'
  heavyMode: 'auto',   // Heavy scenes: 'auto' (the GPU probe decides), 'on', 'off'
  autoRoll: 0,         // AUTO ROLL: bars between rolls of the scene on air (0 = off)
  bgMode: 'scenes',    // BG CHANGES: 'scenes' (the default: every 2x TIME), 'hold' (stays, new FX every 2x TIME), 'off'
  logos: true,         // SPAZ LOGOS: the logos layer between the background and the scene (renderer.js, lite.js)
  effects: 'random',   // EFFECTS: RANDOM ('random', the default), AUTO (true) or OFF (false)
  follow: true,        // FOLLOW MUSIC: breakdowns and drops pick the scene
  theme: 'light',      // THEME: the cabinet and the room, 'dark' or 'light' (tv.css)
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
const EFFECTS_MODES = ['random', true, false];   // EFFECTS: RANDOM, AUTO, OFF
const EFFECTS_LABELS = new Map([[true, 'AUTO'], ['random', 'RANDOM'], [false, 'OFF']]);
const BG_MODE_LABELS = { scenes: 'WITH SCENES', hold: 'HOLD', off: 'OFF' };
// 100% = the desktop's Audio Response at 100%. Auto gain levels the stream, so no boost is needed by default.
const REACT_MIN = 0.25, REACT_MAX = 2.0, REACT_DEFAULT = 1.0;
// Saved under a new key: values saved before auto gain (200% was the default) would run twice as hot.
const REACT_KEY = 'lsd.react.agc';
const STREAM_STATUS_URL = 'https://radio.spaz.org:8060/status-json.xsl';
const STREAM_MOUNT = '/radio.ogg';
// What the Spaz Radio app shows: the title with [LIVE!] for a show streamed in real time. /playing sends no CORS
// header, so the TV reads its JSONP copy (as spaz.org/radio does) and tries fetch() only if that fails.
const PLAYING_URL = 'https://radio.spaz.org/playing';
const PLAYING_RETRY_MS = 10 * 60 * 1000; // a failed read waits this long, or for the next power-on
// THEME: the browser's own bar matches the cabinet (index.html sets the theme before the first paint).
const THEME_BAR = { dark: '#2a2520', light: '#d3d1cb' };

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
  if (id === 'osd-track' && saveHover) return;           // the pointer is on SAVE: the line waits for it to leave
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
let nowFile = '';        // the jukebox show's file as radio.spaz.org/playing names it (`download`), '' when unknown or live
let streamLive = false;  // the server says the show on the stream is live, in real time
let streamKnown = false; // the last read of that flag worked (else the schedule stands in for it)
let playingRetryAt = 0;
function showLook(info = nowShowing(), ms = 4000, brand = false) {
  if (!powerState.on) return;
  if (liteState.on) {
    // Lite mode (lite.js): the Spaz collage, no scenes.
    flash('osd-ch', 'LITE', ms);
    if (SHOW_SCENE_INFO) flash('osd-name', 'SCENE: SPAZ RADIO COLLAGE', ms); else unflash('osd-name');
    unflash('osd-bg'); unflash('osd-chain');
  } else if (autopilotState.isLiveBroadcast) {
    flash('osd-ch', 'LIVE', ms);
    if (autopilotState.broadcaster) flash('osd-name', autopilotState.broadcaster, ms);
    unflash('osd-bg'); unflash('osd-chain'); // the desktop's scene is not ours to name
  } else if (info.tuning) {
    // Still loading the first scene: say so, not an empty SCENE line.
    flash('osd-ch', 'TUNING\u2026', Math.max(ms, TUNING_MS));
    unflash('osd-name'); unflash('osd-bg'); unflash('osd-chain');
  } else {
    // Bottom left, one size: the scene, the background behind it, the scene's FX chain.
    if (SHOW_CHANNELS && info.channel > 0) flash('osd-ch', `CH ${String(info.channel).padStart(3, '0')}`, ms);
    else unflash('osd-ch');
    if (SHOW_SCENE_INFO) {
      flash('osd-name', `SCENE: ${prettyName(info.name)}`, ms);
      if (info.background) flash('osd-bg', `BACKGROUND: ${prettyName(info.background)}`, ms);
      else unflash('osd-bg');
      flash('osd-chain', `FX: ${!info.effects ? 'OFF' : info.chain ? prettyName(info.chain) : 'NONE'}`, ms);
    } else { unflash('osd-name'); unflash('osd-bg'); unflash('osd-chain'); }
  }
  if (deck?.current) flashTrack(tapeLine(), ms);
  else if (nowPlaying) flashTrack(`♪ ${nowPlaying}`, ms);
  flashNext(ms);
  deck?.showStrip(ms);
  if (brand) flash('osd-brand', undefined, ms); // asked for (I, a click on the picture): who made this
}

const message = (text, ms = 1500) => flash('osd-msg', text, ms);
const TUNING_MS = 120000;  // TUNING… stays up until the first scene is on air (lsd-now-showing takes it down)
/** The TV is on and the first scene is still loading: TUNING… on screen. */
function showTuning() {
  // Lite mode the browser chose (no WebGL 2, or the visuals failed): say so at power-on.
  if (liteState.on) { if (liteState.reason !== 'chosen') showLook(); return; }
  if (powerState.on && !autopilotState.isLiveBroadcast && nowShowing().tuning) flash('osd-ch', 'TUNING\u2026', TUNING_MS);
}

/** Under the badge: everyone on the stream. Nothing when the count is unknown or zero, as in the Spaz Radio app. */
function showListeners(n, fromStatus = false) {
  // The Safari side feed (sidefeed.js) is this viewer's second connection to the same mount: don't count it.
  // (Reads cvState.sideFeed, dsp.js's status string: '' when no side feed is in use.) Never below 1 for ourselves.
  if (fromStatus && n > 0 && cvState.sideFeed === 'playing') n = Math.max(1, n - 1);
  const el = document.getElementById('listeners');
  if (el) el.textContent = n > 0 ? `${n} ${n === 1 ? 'mutant' : 'mutants'} watching TV.` : '';
}
const tapeLine = () => `\u25B6 ${deck.current.title}${deck.current.date ? ` \u00B7 ${deck.current.date}` : ''}`;

// -------------------------------------------------------
// SAVE: the show's file, at the end of the track line (and D). The tape in, else the jukebox's show; nothing
// while the radio is live, which has no file yet. The TV and the archive are both on spaz.org, so the link's
// `download` saves the file; from anywhere else the browser opens it in a new tab instead.
// -------------------------------------------------------
let saveHover = false;     // the pointer is on SAVE (its line doesn't fade under it)
const fileSizes = new Map(); // url -> bytes (0: asked, unknown)
/** A show on the schedule's clock, when the station's live flag can't be read (the badge's fallback). */
const scheduledOnAir = () => (streamKnown ? null : upcoming(scheduleState.shows, Date.now()).find((s) => s.onAir)?.name ?? null);
/** The radio is a live show: a broadcast, the station's flag, or the schedule standing in for it. */
const radioLive = () => autopilotState.isLiveBroadcast || streamLive || !!scheduledOnAir();
/** The file SAVE saves, or null. The jukebox's file as the server names it when it is there (often it is not:
 * `archives/olde/` holds only some of what the radio plays), else the archive's copy (see archiveFileFor), else
 * none: SAVE hides rather than offer a 404. */
function saveFile() {
  if (deck?.current) return deck.current.url || null;
  if (radioLive() || !nowFile) return null;
  return fileThere.get(nowFile) === true ? nowFile : archiveFileFor(nowFile);
}
const fileThere = new Map(); // jukebox file -> true (served) / false (404 or unreachable); absent: not asked yet
/** One HEAD request per jukebox file: is it served where the server says? (Same origin on spaz.org; elsewhere a
 * 404 carries no CORS header, so a failed request counts as not there.) */
async function checkFileThere(url) {
  if (fileThere.has(url)) return fileThere.get(url);
  fileThere.set(url, false);
  try {
    const res = await fetch(url, { method: 'HEAD' });
    fileThere.set(url, res.ok);
    const n = res.ok ? Number(res.headers.get('content-length')) : 0;
    if (n > 0) fileSizes.set(url, n);
  } catch { /* not there */ }
  return fileThere.get(url);
}
let lookedUp = '';         // the jukebox file the archive list was last fetched for
let lookingUp = false;     // the archive list is coming in for the jukebox's file
/** Finds where the jukebox's file can be saved from: where the server says (one HEAD request), else the archive
 * list: the kept copy first, then the feed (316 KB gzipped) if there is none, or if the kept one is stale and lacks
 * the show. Once per file, so a show that is nowhere does not fetch the feed on every poll. */
async function lookUpNowFile() {
  const file = nowFile;
  if (!file || file.startsWith('https://spaz.org/archives/imports/') || fileThere.has(file)) return;
  lookingUp = true;
  try {
    if (await checkFileThere(file)) return;
    await restoreTapes();
    if (file === nowFile && !archiveFileFor(file) && lookedUp !== file && (!tapesState.shows.length || tapesStale())) {
      lookedUp = file;
      await refreshTapes();
    }
  } finally { lookingUp = false; updateSave(); }
}
function saveTitle(url) {
  const ext = (url.match(/\.([a-z0-9]{2,4})(?:[?#]|$)/i)?.[1] ?? '').toUpperCase();
  const bytes = fileSizes.get(url);
  const size = !bytes ? '' : bytes >= 1e9 ? `${(bytes / 1e9).toFixed(1)} GB` : `${Math.max(1, Math.round(bytes / 1e6))} MB`;
  return ['Save this show', ext, size].filter(Boolean).join(' \u00B7 ') + ' (D)';
}
/** Puts SAVE in step with what is playing: shown with the show's file, hidden with none. */
function updateSave() {
  const a = document.getElementById('osd-save');
  if (!a) return;
  const url = saveFile();
  a.hidden = !url;
  if (!url) { a.removeAttribute('href'); return; }
  if (a.getAttribute('href') !== url) a.setAttribute('href', url);
  a.title = saveTitle(url);
}
/** The size for the tooltip: one HEAD request per file (the archive sends Content-Length and a CORS header). */
async function askSize(url) {
  if (fileSizes.has(url)) return;
  fileSizes.set(url, 0);
  try {
    const res = await fetch(url, { method: 'HEAD' });
    const n = res.ok ? Number(res.headers.get('content-length')) : 0;
    if (n > 0) { fileSizes.set(url, n); updateSave(); }
  } catch { /* no size: the tooltip goes without */ }
}
/** The track line (the jukebox's \u266A or the tape's \u25B6), with SAVE after it. */
function flashTrack(text, ms) {
  const el = document.getElementById('osd-track-text');
  if (el) el.textContent = text;
  updateSave();
  flash('osd-track', undefined, ms);
}

/** Under the track line: the next show on Spaz Radio, when the schedule knows one. */
function flashNext(ms) {
  if (deck?.current && deck.airName) { flash('osd-next', `LIVE NOW: ${deck.airName}`, ms); return; }
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
let lastUserChange = -Infinity; // a viewer's CH / FX press: with SHOW_SCENE_INFO its result is named on screen
/** A new scene is on: refresh the INFO lines if INFO is on; with SHOW_SCENE_INFO, also name a change the viewer asked for. */
function announceLook(info) {
  if (viewerState.info || (SHOW_SCENE_INFO && performance.now() - lastUserChange < 4000)) showLook(info);
}
let identShown = false;    // the Liquid LSD ident plays at the first power-on of a visit
let lastBumper = 0;        // performance.now() of the last bumper (or the power-on)
const BUMPER_EVERY_MS = 30 * 60 * 1000;
const AUTOPLAY_MODES = ['ask', 'on', 'off'];
const AUTOPLAY_ASK_AFTER_MS = 12000;  // the question waits for the ident and the power-on lines to go
const AUTOPLAY_ASK_FOR_MS = 20000;    // unanswered, it goes and comes back next visit
const AUTOPLAY_RETRY_MS = 5000;       // a page is over the picture: try again this much later
const AUTOPLAY_MAX_UNANSWERED = 3;    // visits without an answer before it stops asking (and stays off)

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
let deck = null;           // the VCR (tapedeck.js); none under kiosk / embed
let syncKeys = () => {};   // puts the key bank's keys in step with the pages (set up with the controls)

export function initUI() {
  const powerSwitch = document.getElementById('powerSwitch');
  const tvBody = document.getElementById('tv-body');
  const canvas = document.getElementById('glCanvas');
  const screen = document.getElementById('tv-screen-area');
  const holdBtn = document.getElementById('holdBtn');

  const live = () => autopilotState.isLiveBroadcast;
  /** The scene controls do nothing: the desktop is live, or lite mode shows the collage, not scenes. */
  const noScenes = () => live() || liteState.on;
  /** Runs a viewer control unless the desktop is live (then says why nothing happened). In lite mode it runs
   *  `liteFn` instead, or says the control needs the full visuals. */
  const control = (fn, liteFn) => (...args) => {
    if (!powerState.on) return;
    if (live()) { message('LIVE · CONTROLS LOCKED', 2000); return; }
    if (liteState.on) {
      if (liteFn) liteFn(...args);
      else message('LITE PICTURE · MENU › TV › VISUALS', 2500);
      return;
    }
    fn(...args);
  };

  // --- Power ---
  // Hover help follows the power state: off, the whole set is the power target; on, only PWR is.
  // (The menu, ABOUT and SCHEDULE overlays carry an empty title so they don't inherit either.)
  const updatePowerTitles = () => {
    tvBody.title = powerState.on
      ? 'Click the power button (PWR) to turn the TV off\nPress ? for about and keyboard shortcuts'
      : 'Click the TV to turn it on\nPress ? for about and keyboard shortcuts';
    canvas.title = powerState.on
      ? 'Click for info (press I or INFO to keep it on screen)\nDouble-click for fullscreen (or press F)\nPress ? for about and keyboard shortcuts'
      : 'Click the TV to turn it on\nPress ? for about and keyboard shortcuts';
  };
  updatePowerTitles();
  // The pages over the picture also open with the TV off; pressing power either way clears them.
  const closePages = () => {
    menu.hide();
    schedule.hide();
    deck?.close();
    keysPage.hidden = true;
  };
  powerSwitch.addEventListener('click', async () => {
    closePages();
    if (powerState.on) {
      powerState.on = false;
      updatePowerTitles();
      deck?.save();
      deck?.refreshLights();
      letScreenSleep();
      updateMediaSession();
      powerState.warmupProgress = 0;
      powerState.shutdownProgress = 0;
      tvBody.classList.remove('powered-on');
      tvBody.classList.add('shutting-down');
      powerSwitch.classList.remove('active');
      hideAutoplayAsk();
      try { await stopAudio(); } catch (err) { console.error('Audio stop failed:', err); }
    } else {
      powerState.on = true;
      playingRetryAt = 0;
      updatePowerTitles();
      deck?.refreshLights();
      powerState.warmupProgress = embed ? 1 : 0;  // renderer will advance this (embed: no CRT warm-up)
      powerState.shutdownProgress = 0;
      tvBody.classList.remove('shutting-down');
      tvBody.classList.add('powered-on');
      powerSwitch.classList.add('active');
      if (!identShown) {
        // First power-on of the visit: the Liquid LSD ident over the end of the warm-up, then the scene.
        identShown = true;
        setTimeout(showIdent, 900);
        setTimeout(() => { announceLook(); showTuning(); }, 4600);
      } else {
        setTimeout(() => { announceLook(); showTuning(); }, 1600); // after the warm-up
      }
      lastBumper = performance.now();
      pollNowPlaying();
      pollSchedule();
      holdScreenAwake();
      updateMediaSession();
      scheduleAutoplayAsk();
      try {
        await startAudio();
        audioBlocked = false;
      } catch (err) {
        // Autoplay blocked (kiosk power-on without a click): the next click or key anywhere starts the sound.
        // Any other failure is the stream (offline, radio down): the audio is set up, so the microphone works,
        // and dsp.js shows no signal and keeps retrying the stream.
        console.warn('Audio start failed:', err);
        audioBlocked = err?.name === 'NotAllowedError';
      }
    }
  });

  // When the TV is off, a click anywhere on it turns it on. When it is on, the cabinet itself does nothing:
  // only the power switch turns it off. A click that just misses a control (on its label, or in the gap
  // beside it) presses the nearest one, within a square of NEAR_MISS px around it; a near-miss on a dial
  // does nothing (dials are dragged). Off, that holds only for the buttons that work with the TV off.
  // With the menu open, a stray click only closes the menu.
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
    if (e.target.closest('button, .rotary-dial, .rocker, #tv-menu, #schedule, #keys, a')) return; // they handle their own clicks
    if (viewerState.menu?.open) { viewerState.menu.hide(); return; } // a near-miss on the menu closes it, not the TV
    const near = e.target.closest('#tv-controls') ? nearestControl(e.clientX, e.clientY) : null;
    if (!powerState.on && !near?.matches('#key-bank button, #chatBtn')) { powerSwitch.click(); return; }
    if (near?.tagName === 'BUTTON') near.click(); // no control near: the picture shows the info; the cabinet is inert
  });

  // --- Volume ---
  // Safari on an iPhone or iPad plays the live radio past the page's volume, and the page cannot set the
  // element's own (dsp.radioBypassesGraph): only the device's buttons can. The dial is greyed out then.
  const volumeFixed = () => radioBypassesGraph() && elementVolumeFixed();
  const volumeLocked = () => {
    if (!volumeFixed()) return false;
    if (powerState.on) message('VOLUME: USE THE DEVICE BUTTONS', 2500);
    return true;
  };
  const volumeDial = makeDial(document.getElementById('volumeDial'), {
    initial: 0.8,
    locked: volumeLocked,
    onChange: (v) => { setVolume(v); if (powerState.on) message(`VOL ${Math.round(v * 100)}%`); },
  });
  setVolume(0.8);
  const soundPathChanged = () => {
    document.getElementById('volumeDial').classList.toggle('fixed', volumeFixed());
    menu.refresh(); // the EQ rows
  };
  window.addEventListener('lsd-sound-path', soundPathChanged);
  window.addEventListener('lsd-mic', soundPathChanged);
  const stepVolume = (dir) => { if (!volumeLocked()) volumeDial.set(volumeDial.get() + dir * 0.05); };
  // U: the dial goes to zero and back to where it was (if it is still at zero).
  let unmuted = 0.8;
  const toggleMute = () => {
    if (volumeLocked()) return;
    if (volumeDial.get() > 0) {
      unmuted = volumeDial.get();
      volumeDial.set(0);
      if (powerState.on) message('MUTE');
    } else volumeDial.set(unmuted);
  };

  // --- Channel rocker, FX, HOLD ---
  const next = control(() => { lastUserChange = performance.now(); showTuning(); advanceForeground(); }, liteNext);
  const prev = control(() => { lastUserChange = performance.now(); showTuning(); previousForeground(); }, liteNext);
  const random = control(() => { lastUserChange = performance.now(); showTuning(); randomForeground(); }, liteNext);
  const fx = control(async () => {
    lastUserChange = performance.now();
    if (await nextFxChain() === 'off') message('EFFECTS OFF \u00B7 MENU', 2000);
  });
  // ROLL: a new variation of the scene on air (autopilot.rollScene glides it in on the next bar).
  const roll = control(() => message(rollScene() ? 'ROLL' : 'NOTHING TO ROLL HERE'));
  // 0-9 (only with SHOW_CHANNELS): tune by number, like a remote: three digits tune at once, fewer after a pause.
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
    locked: noScenes,
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
    if (noScenes()) return control(() => {})();
    const i = Math.round(timeDial.get() * (TIME_STEPS.length - 1));
    timeDial.set(((i + 1) % TIME_STEPS.length) / (TIME_STEPS.length - 1));
  });

  // --- REACT: audio sensitivity ---
  const toDial = (k) => (k - REACT_MIN) / (REACT_MAX - REACT_MIN);
  viewerState.react = Math.max(REACT_MIN, Math.min(REACT_MAX, Number(load(REACT_KEY, REACT_DEFAULT)) || REACT_DEFAULT));
  const reactDial = makeDial(document.getElementById('reactDial'), {
    initial: toDial(viewerState.react),
    locked: noScenes,
    onChange: (v) => {
      viewerState.react = REACT_MIN + v * (REACT_MAX - REACT_MIN);
      save(REACT_KEY, viewerState.react);
      if (powerState.on) message(`REACT ${Math.round(viewerState.react * 100)}%`);
    },
  });
  document.getElementById('reactDial').addEventListener('dblclick', control(() => reactDial.set(toDial(REACT_DEFAULT))));
  const stepReact = control((dir) => reactDial.set(reactDial.get() + dir * 0.05));

  const schedulePage = document.getElementById('schedule');
  const keysPage = document.getElementById('keys');
  const menuBtn = document.getElementById('menuBtn');
  const scheduleBtn = document.getElementById('scheduleBtn');

  // --- Service MENU (menu.js): four pages, SCENES, SOUND, EQ and TV (no EQ in embed: the host app plays the sound) ---
  viewerState.syncMs = Math.max(SYNC_MIN, Math.min(SYNC_MAX, Number(load('lsd.sync', 0)) || 0));
  viewerState.reducedMode = MODES.includes(load('lsd.reduced', 'auto')) ? load('lsd.reduced', 'auto') : 'auto';
  viewerState.heavyMode = MODES.includes(load('lsd.heavy', 'auto')) ? load('lsd.heavy', 'auto') : 'auto';
  viewerState.autoRoll = AUTO_ROLL_STEPS.includes(load('lsd.autoRoll', 0)) ? load('lsd.autoRoll', 0) : 0;
  viewerState.bgMode = BG_MODES.includes(load('lsd.bgMode', 'scenes')) ? load('lsd.bgMode', 'scenes') : 'scenes';
  viewerState.logos = load('lsd.logos', true) !== false;
  viewerState.effects = EFFECTS_MODES.includes(load('lsd.effects', 'random')) ? load('lsd.effects', 'random') : 'random';
  viewerState.follow = load('lsd.follow', true) !== false;
  viewerState.theme = load('lsd.theme', 'light') === 'dark' ? 'dark' : 'light';
  const paintTheme = () => {
    document.documentElement.setAttribute('data-theme', viewerState.theme);
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_BAR[viewerState.theme]);
  };
  const applyTheme = (theme) => { viewerState.theme = theme; save('lsd.theme', theme); paintTheme(); };
  if (!kiosk) paintTheme(); // kiosk and embed have no cabinet to paint
  viewerState.showListeners = showListeners; // for the headless check
  setAutoRollBars(viewerState.autoRoll);
  setBackgroundMode(viewerState.bgMode);
  setEffects(viewerState.effects);
  setFollowMusic(viewerState.follow);
  // Channel skip (X, MENU › SKIPPED) is gone: forget any skips a viewer stored, so every scene comes up again.
  // ANNOUNCE SCENES went with the scene lines: drop its stored setting too.
  try { localStorage.removeItem('lsd.skipped'); localStorage.removeItem('lsd.announce'); } catch { /* no storage */ }
  const stepIn = (list, v, dir) => list[Math.max(0, Math.min(list.length - 1, list.indexOf(v) + dir))];
  const applyAutoRoll = (bars) => { viewerState.autoRoll = bars; save('lsd.autoRoll', bars); setAutoRollBars(bars); };
  const applyBgMode = (mode) => { viewerState.bgMode = mode; save('lsd.bgMode', mode); setBackgroundMode(mode); };
  const applyLogos = (on) => { viewerState.logos = on; save('lsd.logos', on); };
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
  const EQ_FLAT = { on: false, bass: 0, mid: 0, treble: 0 };
  const savedEq = load('lsd.eq', EQ_FLAT);
  const eqClamp = (v) => Math.max(EQ_MIN_DB, Math.min(EQ_MAX_DB, Math.round(Number(v)) || 0));
  const applyEq = (changes, store = true) => {
    const e = { ...viewerState.eq, ...changes };
    viewerState.eq = { on: e.on === true, bass: eqClamp(e.bass), mid: eqClamp(e.mid), treble: eqClamp(e.treble) };
    setEq(viewerState.eq);
    if (store) save('lsd.eq', viewerState.eq);
  };
  viewerState.eq = EQ_FLAT;
  applyEq(savedEq && typeof savedEq === 'object' ? savedEq : {}, false);
  viewerState.eqGains = eqGains; // for the headless check
  const dbLabel = (db) => `${db > 0 ? '+' : ''}${db} DB`;
  // Safari plays the live radio past the EQ (dsp.radioBypassesGraph): its rows are locked then; a tape has it.
  const EQ_SAFARI_HELP = 'Safari plays the live radio straight to the speakers, so the EQ can’t reach it. It works on tapes.';
  const eqLocked = () => !viewerState.eq.on || radioBypassesGraph();
  const eqBand = (label, key, help) => ({ label, help: () => (radioBypassesGraph() ? EQ_SAFARI_HELP : help), locked: eqLocked, value: () => dbLabel(viewerState.eq[key]),
    step: (dir) => applyEq({ [key]: viewerState.eq[key] + dir }) });
  const cycle = (mode, dir) => MODES[(MODES.indexOf(mode) + dir + MODES.length) % MODES.length];
  const onOff = (b) => (b ? 'ON' : 'OFF');
  const pages = [
    { name: 'SCENES', rows: [
      { label: 'TIME', locked: noScenes, help: 'How long each scene stays before the next one, in bars of the music.', value: () => timeLabel.textContent,
        step: (dir) => {
          const i = Math.round(timeDial.get() * (TIME_STEPS.length - 1));
          timeDial.set(Math.max(0, Math.min(TIME_STEPS.length - 1, i + dir)) / (TIME_STEPS.length - 1));
        } },
      { label: 'AUTO ROLL', locked: noScenes, value: () => (viewerState.autoRoll ? `EVERY ${viewerState.autoRoll} BARS` : 'OFF'),
        step: (dir) => applyAutoRoll(stepIn(AUTO_ROLL_STEPS, viewerState.autoRoll, dir)),
        help: 'Rolls a new variation of the scene on air every few bars, like pressing ROLL (R). Best with HOLD on: the scene stays and keeps changing.' },
      { label: 'FOLLOW MUSIC', locked: noScenes, value: () => onOff(viewerState.follow),
        step: () => applyFollow(!viewerState.follow),
        help: 'On: a calm scene comes in for a breakdown, a hard one cuts in on the drop, and the tempo leans the picks. Off: the scenes just take turns.' },
      { label: 'EFFECTS', locked: noScenes, value: () => EFFECTS_LABELS.get(viewerState.effects),
        step: (dir) => applyEffects(EFFECTS_MODES[(EFFECTS_MODES.indexOf(viewerState.effects) + dir + EFFECTS_MODES.length) % EFFECTS_MODES.length]),
        help: 'Random (the default): any FX chain on any scene or background, for more variety (FX or E picks another). Auto: only chains that suit the scene. Off: the scenes play clean.' },
      { label: 'BACKGROUND', needsPower: true, locked: () => noScenes() || viewerState.bgMode === 'off',
        value: () => (viewerState.bgMode === 'off' ? 'OFF' : prettyName(nowShowing().backgroundPicked ?? '') || '---'),
        step: (dir) => stepBackground(dir).then(() => menu.refresh()),
        help: 'The picture behind the scene, which shows through wherever the scene is dark or see-through: the listener photos to start with, then slow ambient scenes. ◀ ▶ pick another one now.' },
      { label: 'BG CHANGES', locked: noScenes, value: () => BG_MODE_LABELS[viewerState.bgMode],
        step: (dir) => applyBgMode(BG_MODES[(BG_MODES.indexOf(viewerState.bgMode) + dir + BG_MODES.length) % BG_MODES.length]),
        help: 'With scenes (the default): a new background every two scenes (TIME). Hold: the background stays until you change it, with new effects every two scenes. Off: no background, just the scene (and the logos).' },
      { label: 'SPAZ LOGOS', locked: () => live() && !liteState.on, value: () => onOff(viewerState.logos),
        step: () => applyLogos(!viewerState.logos), run: () => applyLogos(!viewerState.logos),
        help: 'The Spaz Radio logos, turning slowly between the background and the scene, so they show wherever the scene is dark or see-through. Not over a live show.' },
    ] },
    { name: 'SOUND', rows: [
      { label: 'AUDIO INPUT', needsPower: true, locked: live, value: () => (isMicOn() ? 'MICROPHONE' : deck?.current ? 'TAPE' : 'RADIO'),
        help: 'What the picture listens to. Radio: the Spaz Radio stream, or the tape if one is in. Microphone: the sound in the room, also offline (the browser asks first; it is analysed only, never played back).',
        step: () => toggleMic() },
      { label: 'REACT', locked: noScenes, help: 'How strongly the picture moves with the music. Raise it for quiet shows, lower it if it is too busy.', value: () => `${Math.round(viewerState.react * 100)}%`,
        step: (dir) => reactDial.set(toDial(Math.max(REACT_MIN, Math.min(REACT_MAX, viewerState.react + dir * 0.05)))) },
      { label: 'SYNC', help: 'Delay the picture so its beats line up with what you hear (Bluetooth speakers are often 150-250 ms late).', value: () => `${viewerState.syncMs > 0 ? '+' : ''}${viewerState.syncMs} MS`,
        step: (dir) => {
          viewerState.syncMs = Math.max(SYNC_MIN, Math.min(SYNC_MAX, viewerState.syncMs + dir * 10));
          save('lsd.sync', viewerState.syncMs);
        } },
      { label: 'TAP SYNC', needsPower: true, value: () => 'TAP 8 BEATS', run: () => startTapSync(),
        help: 'Press Enter, then tap Space or the picture on 8 beats you hear: sets SYNC for you.' },
    ] },
    ...(embed ? [] : [{ name: 'EQ', rows: [
      { label: 'EQUALIZER', locked: radioBypassesGraph, value: () => (radioBypassesGraph() ? 'NOT ON SAFARI LIVE' : onOff(viewerState.eq.on)),
        step: () => applyEq({ on: !viewerState.eq.on }), run: () => applyEq({ on: !viewerState.eq.on }),
        help: () => (radioBypassesGraph() ? EQ_SAFARI_HELP : 'Shapes the sound you hear from the radio or a tape. The picture still reacts to the music as broadcast.') },
      eqBand('BASS', 'bass', 'Low end: kick and bass (120 Hz and below), up or down 12 dB.'),
      eqBand('MID', 'mid', 'The middle: voices and most instruments (around 1 kHz).'),
      eqBand('TREBLE', 'treble', 'Top end: hi-hats and air (6 kHz and above).'),
      { label: 'FLAT', locked: eqLocked, value: () => ((viewerState.eq.bass || viewerState.eq.mid || viewerState.eq.treble) ? 'RESET' : 'FLAT'),
        run: () => applyEq({ bass: 0, mid: 0, treble: 0 }),
        help: 'Put all three bands back to 0. Boosting a band turns the overall volume down a little so loud songs don’t distort.' },
    ] }]),
    { name: 'TV', rows: [
      { label: 'FULLSCREEN', value: () => ((document.fullscreenElement ?? document.webkitFullscreenElement) ? 'ON' : 'OFF'), step: () => toggleFullscreen(), run: () => toggleFullscreen(),
        help: 'The picture and its on-screen display fill the screen. Double-click the picture or press F to toggle; Esc leaves.' },
      ...(kiosk ? [] : [{ label: 'THEME', value: () => viewerState.theme.toUpperCase(),
        step: () => applyTheme(viewerState.theme === 'dark' ? 'light' : 'dark'),
        help: 'The TV’s cabinet and the room around it: light, a 1970s hi-fi in brushed aluminium and walnut (the default), or dark plastic. The picture is the same in both.' }]),
      ...(kiosk ? [] : [{ label: 'AUTOPLAY', value: () => autoplay.toUpperCase(),
        step: (dir) => setAutoplay(AUTOPLAY_MODES[(AUTOPLAY_MODES.indexOf(autoplay) + dir + AUTOPLAY_MODES.length) % AUTOPLAY_MODES.length]),
        help: 'On: the TV turns itself on when you open this page (if the browser won’t start the sound by itself, the screen asks for a click). Off: it waits for your click. Ask: it asks you, a little while after you turn it on. PWR turns it off either way.' }]),
      { label: 'VISUALS',
        value: () => (!liteState.on ? 'FULL' : { chosen: 'LITE', slow: 'LITE (TOO SLOW HERE)' }[liteState.reason] ?? 'LITE (NO FULL HERE)'),
        step: () => {
          if (!canSwitch()) { message('THIS BROWSER CAN\u2019T DRAW THE FULL VISUALS', 3000); return; }
          setVisuals(liteState.on ? 'full' : 'lite');
        },
        help: 'Full: the Liquid LSD visuals. Lite: a Spaz Radio collage of skies, listener photos and logos, for older or slower phones and computers (less battery and data). The TV picks Lite by itself where the visuals run too slowly, and remembers it on this device.' },
      { label: 'REDUCED MOTION', help: 'Gentler: no flashes, no hard scenes, slower changes. Auto follows your system setting.',
        value: () => (viewerState.reducedMode === 'auto' ? `AUTO (${onOff(viewerState.reduced)})` : viewerState.reducedMode.toUpperCase()),
        step: (dir) => { viewerState.reducedMode = cycle(viewerState.reducedMode, dir); save('lsd.reduced', viewerState.reducedMode); applyReduced(); } },
      { label: 'BIG GPU SCENES', locked: noScenes,
        help: 'The most demanding 3D fractals and simulations. Auto: on if your graphics card passed a quick test at power-on. On: always (may stutter on laptops and phones). Off: never, for a smoother picture.',
        value: () => {
          if (viewerState.heavyMode !== 'auto') return viewerState.heavyMode.toUpperCase();
          const gpu = window.LSD?.gpu;
          return gpu?.state === 'done' ? `AUTO (${onOff(gpu.heavy)}, ${gpu.ms.toFixed(0)} MS)` : `AUTO (${onOff(gpu?.heavy)})`;
        },
        step: (dir) => { viewerState.heavyMode = cycle(viewerState.heavyMode, dir); save('lsd.heavy', viewerState.heavyMode); applyHeavy(); } },
      { label: 'RESET', help: 'Puts the menu settings back to their defaults: sync, equalizer, theme, autoplay, SPAZ LOGOS, reduced motion, big GPU scenes and, when the TV picks the scenes, react, time, auto roll, follow music, effects and BG changes. It leaves the VISUALS choice (full or lite), the INFO display, the chat and any tape alone.', run: () => {
        viewerState.syncMs = 0; save('lsd.sync', 0);
        viewerState.reducedMode = 'auto'; save('lsd.reduced', 'auto'); applyReduced();
        viewerState.heavyMode = 'auto'; save('lsd.heavy', 'auto'); applyHeavy();
        applyLogos(true);
        applyEq(EQ_FLAT);
        if (!kiosk) { applyTheme('light'); setAutoplay('ask'); }
        if (!live()) {
          reactDial.set(toDial(REACT_DEFAULT)); timeDial.set(TIME_STEPS.indexOf(16) / (TIME_STEPS.length - 1));
          applyAutoRoll(0); applyBgMode('scenes'); applyEffects('random'); applyFollow(true);
        }
      } },
      { label: 'ABOUT', help: 'About Liquid LSD, the software that makes these visuals, the Spaz Radio app, and every keyboard shortcut (also ?).', value: () => 'LIQUID LSD', run: () => showKeys() },
    ] },
  ];
  /** Listens to the microphone instead of the radio, or back. */
  const toggleMic = control(async () => {
    if (audioBlocked) { message('CLICK OR PRESS A KEY FOR SOUND', 2000); return; }
    if (!micAvailable()) { message('NO MICROPHONE ACCESS HERE', 3000); return; }
    try {
      await setMic(!isMicOn());
      message(isMicOn() ? 'AUDIO INPUT \u00B7 MICROPHONE' : deck?.current ? 'AUDIO INPUT \u00B7 TAPE' : 'AUDIO INPUT \u00B7 RADIO', 2500);
    } catch (err) {
      console.warn('Microphone unavailable:', err);
      message('MICROPHONE BLOCKED', 3000);
    }
    deck?.refreshLights();
    menu.refresh();
  });
  function toggleFullscreen() {
    const request = screen.requestFullscreen ?? screen.webkitRequestFullscreen;
    if (!request) { message('FULLSCREEN NOT AVAILABLE HERE', 3000); return; }
    if (!fullscreenNow()) {
      // Going full screen from the menu closes it, so the Esc that would close it can't also leave full screen.
      menu.hide();
      // Where the browser allows it (Chromium), keep Esc for the page: a tap closes the menu or leaves full
      // screen through the keyboard handler below, and holding it still leaves full screen.
      Promise.resolve(request.call(screen)).then(() => navigator.keyboard?.lock?.(['Escape'])).catch(console.error);
    } else exitFullscreen();
  }
  // Safari before 16.4 (and iPad) only has the webkit-prefixed fullscreen API.
  function fullscreenNow() { return document.fullscreenElement ?? document.webkitFullscreenElement ?? null; }
  function exitFullscreen() { return Promise.resolve((document.exitFullscreen ?? document.webkitExitFullscreen)?.call(document)).catch(console.error); }
  const onFullscreenChange = () => {
    if (!fullscreenNow()) navigator.keyboard?.unlock?.();
    menu.refresh();
  };
  document.addEventListener('fullscreenchange', onFullscreenChange);
  document.addEventListener('webkitfullscreenchange', onFullscreenChange);
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

  const menu = createMenu(screen, pages, {
    version: TV_VERSION,
    isOn: () => powerState.on,
    onOpen: () => { screen.classList.add('menu-open'); syncKeys(); }, // the OSD steps aside
    onClose: () => { screen.classList.remove('menu-open'); syncKeys(); },
  });
  const toggleMenu = () => {
    const wasOpen = menu.open;
    closePages();
    if (!wasOpen) menu.show();
  };
  menuBtn.addEventListener('click', toggleMenu);
  viewerState.menu = menu;

  // --- SCHEDULE (schedule.js): Spaz Radio's upcoming shows, like ABOUT; any key or click closes it ---
  const schedule = {
    get open() { return !schedulePage.hidden; },
    show() {
      menu.hide();
      deck?.close();
      keysPage.hidden = true;
      schedulePage.hidden = false;
      syncKeys();
      // A list older than 10 minutes (or none yet) is fetched again; the page fills in when it lands.
      // Not under ?nolive: the headless checks give the page a fixture, and a real show on air would turn the source badge.
      if (Date.now() - scheduleState.fetchedAt > SCHEDULE_STALE_MS && !new URLSearchParams(location.search).has('nolive')) {
        refreshSchedule().then(() => { if (!schedulePage.hidden) renderSchedule(); });
      }
      renderSchedule();
    },
    hide() { schedulePage.hidden = true; syncKeys(); },
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

  // --- ? and MENU › ABOUT: Liquid LSD, the Spaz Radio app and every keyboard shortcut; any key or a click
  // outside the links closes it ---
  const showKeys = () => {
    menu.hide();
    schedule.hide();
    deck?.close();
    keysPage.hidden = false;
  };
  keysPage.querySelector('.ab-app').href = appUrl();
  keysPage.addEventListener('click', (e) => { if (!e.target.closest('a')) keysPage.hidden = true; });

  // --- INFO: keeps the info lines (channel, scene, background, FX, track, next show) on screen ---
  // It is the key bank's resting key: a page opened over it pops it up, and closing that page lets it back down.
  const infoBtn = document.getElementById('infoBtn');
  function setInfo(on) {
    viewerState.info = on;
    save('lsd.info', on);
    syncKeys();
    if (!powerState.on) return; // a setting with the TV off: the lines come up when it comes on
    if (on) showLook(undefined, 4000, true);
    else { for (const id of INFO_IDS) unflash(id); deck?.hideStrip(); }
  }
  viewerState.info = load('lsd.info', false) === true;
  viewerState.schedule = schedule;

  // --- CHAT (chat.js, chatboard.js): the Spaz Radio chat on a blackboard beside the TV. It connects when the board is first opened (open at load on a first visit),
  // whether the TV is on or off, and not at all under ?kiosk / ?embed (the Spaz Radio app has its own
  // chat). The headless checks (?nolive) never reach the real room: they start it on a fake broker. ---
  if (!kiosk) {
    let storage = null;
    try { storage = localStorage; } catch { /* private window: nothing remembered */ }
    const chat = createChat({ storage, onChange: (what) => chatboard?.onChange(what), onLive: (msg) => chatboard?.onLive(msg) });
    chatboard = createChatboard({ chat, board: document.getElementById('chalkboard'), button: document.getElementById('chatBtn'),
      reduced: () => viewerState.reducedNow, storage });
    const noLive = new URLSearchParams(location.search).has('nolive');
    if (!noLive) chatboard.startOnOpen(() => chat.start()); // connects when the board is first opened
    document.getElementById('chatBtn').addEventListener('click', () => chatboard.toggle());
    window.addEventListener('online', () => chat.reconnectNow());
    window.addEventListener('pagehide', () => chat.leave()); // clears our name from the room at once
    window.addEventListener('pageshow', (e) => { if (e.persisted && !noLive && chatboard.everOpened) chat.start(); }); // back from the cache
    viewerState.chat = { board: chatboard, chat };
  }

  // --- TAPES (tapedeck.js): the archive, played as an audio input. Not under ?kiosk / ?embed. ---
  if (!kiosk) {
    let storage = null;
    try { storage = localStorage; } catch { /* private window: nothing remembered */ }
    deck = createTapeDeck({
      page: document.getElementById('tapes'), strip: document.getElementById('tape-strip'),
      button: document.getElementById('tapesBtn'), storage,
      isOn: () => powerState.on, powerOn: () => { if (!powerState.on) powerSwitch.click(); }, reduced: () => viewerState.reducedNow, stickyStrip: () => viewerState.info,
      message,
      onOpen: () => { menu.hide(); schedule.hide(); keysPage.hidden = true; },
      onChange: (show) => {
        updateBadge();
        updateMediaSession();
        chatboard?.setTape(!!show);
        menu.refresh();
        if (show) showLook();
        else { unflash('osd-track'); unflash('osd-next'); announceLook(); }
      },
    });
    viewerState.tapes = deck;
    // A share link (?tape=<id>&t=1h12m): that show, cued at that moment; power-on (the click a browser needs for sound) plays it.
    deck.cueLink(parseTapeLink(location.search)).catch((err) => console.warn('Tape link failed:', err));
  }

  // --- The key bank: OFF · INFO · MENU · SCHED · TAPES · ?, one key down at a time, like a car radio's.
  // The pages decide which key is down (a MutationObserver watches them), so every way in or out (keys, clicks,
  // Esc, the power switch) keeps the bank in step. No page up: INFO or OFF is down, whichever is resting. ---
  const pageKey = () => (menu.open ? 'menu'
    : schedule.open ? 'sched' : deck?.pageOpen ? 'tapes' : !keysPage.hidden ? 'keys' : null);
  const bankKeys = { off: 'offBtn', info: 'infoBtn', menu: 'menuBtn', sched: 'scheduleBtn', tapes: 'tapesBtn', keys: 'keysBtn' };
  syncKeys = () => {
    const down = pageKey() ?? (viewerState.info ? 'info' : 'off');
    for (const [k, id] of Object.entries(bankKeys)) {
      const b = document.getElementById(id);
      b.classList.toggle('down', k === down);
      if (k !== 'tapes') b.setAttribute('aria-pressed', String(k === down)); // TAPES's own light is the tape deck's
    }
  };
  /** INFO: from a page, closes it and rests on INFO; with no page up, toggles it. */
  const pressInfo = () => {
    if (pageKey()) { closePages(); if (!viewerState.info) setInfo(true); else syncKeys(); }
    else setInfo(!viewerState.info);
  };
  document.getElementById('offBtn').addEventListener('click', () => { closePages(); if (viewerState.info) setInfo(false); });
  infoBtn.addEventListener('click', () => pressInfo());
  document.getElementById('keysBtn').addEventListener('click', () => {
    if (keysPage.hidden) { closePages(); showKeys(); } else keysPage.hidden = true;
  });
  new MutationObserver(syncKeys).observe(screen, { subtree: true, attributes: true, attributeFilter: ['hidden'] });
  syncKeys();

  // --- Keyboard ---
  window.addEventListener('keydown', (e) => {
    if (e.ctrlKey || e.metaKey || e.altKey || e.target.closest?.('input, textarea')) return;
    if (schedule.open) { e.preventDefault(); schedule.hide(); return; }
    if (!keysPage.hidden) { e.preventDefault(); keysPage.hidden = true; return; }
    if (deck?.pageOpen) { deck.key(e); return; }
    if (tapping && (e.key === ' ' || e.key === 'Enter')) { e.preventDefault(); tapSync(); return; }
    // With the menu open these still work: ?, S, I and T close it and do their thing, C toggles the chat.
    if (menu.open && ['?', 's', 'S', 't', 'T'].includes(e.key)) menu.hide();
    if (menu.open && (e.key === 'i' || e.key === 'I')) { e.preventDefault(); pressInfo(); return; }
    if (!(menu.open && (e.key === 'c' || e.key === 'C')) && menu.key(e)) { e.preventDefault(); return; }
    if (e.key === 'm' || e.key === 'M') { e.preventDefault(); menu.show(); return; }
    if (e.key === 's' || e.key === 'S') { e.preventDefault(); schedule.show(); return; }
    if ((e.key === 't' || e.key === 'T') && deck) { e.preventDefault(); deck.open(); return; }
    if ((e.key === 'c' || e.key === 'C') && chatboard) { e.preventDefault(); chatboard.toggle(); return; }
    if (e.key === '?') { e.preventDefault(); showKeys(); return; }
    const keys = {
      ArrowRight: next, n: next, N: next,
      ArrowLeft: prev, p: prev, P: prev,
      j: random, J: random,
      ArrowUp: () => stepVolume(1), ArrowDown: () => stepVolume(-1), u: toggleMute, U: toggleMute,
      e: fx, E: fx, h: hold, H: hold,
      ' ': deck?.current && powerState.on ? deck.togglePause : hold, // a tape in: Space pauses it, as on any player; H still holds
      r: roll, R: roll,
      ...(SHOW_CHANNELS ? Object.fromEntries([...'0123456789'].map((d) => [d, () => digit(d)])) : {}),
      '[': () => stepTime(-1), ']': () => stepTime(1),
      '-': () => stepReact(-1), '=': () => stepReact(1), '+': () => stepReact(1),
      Escape: () => fullscreenNow() && exitFullscreen(),
      f: toggleFullscreen, F: toggleFullscreen, a: toggleMic, A: toggleMic,
      i: pressInfo, I: pressInfo, d: saveShow, D: saveShow,
      ...(deck && powerState.on ? {
        k: deck.togglePause, K: deck.togglePause, l: deck.backToRadio, L: deck.backToRadio,
        ',': () => deck.seekBy(-30), '.': () => deck.seekBy(30), '<': () => deck.seekBy(-300), '>': () => deck.seekBy(300),
      } : {}),
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
  // SAVE on the track line: the line stays up while the pointer is on it; the size comes in on the way.
  const saveLink = document.getElementById('osd-save');
  saveLink.addEventListener('pointerenter', () => {
    saveHover = true;
    clearTimeout(osdTimers['osd-track']);
    const url = saveFile();
    if (url) askSize(url);
  });
  saveLink.addEventListener('pointerleave', () => {
    saveHover = false;
    if (document.getElementById('osd-track').classList.contains('show')) flash('osd-track', undefined, 2000);
  });
  saveLink.addEventListener('click', () => message('SAVING THE SHOW'));
  /** D: SAVE from the keyboard, with the track line up to say which show. */
  const saveShow = () => {
    if (!powerState.on) return;
    if (!saveFile()) {
      const live = !deck?.current && radioLive();
      message(live ? 'LIVE: NOTHING TO SAVE YET' : !deck?.current && nowFile ? (lookingUp ? 'FINDING THE SHOW IN THE ARCHIVE…' : 'THIS SHOW IS NOT IN THE ARCHIVE') : 'NOTHING TO SAVE');
      return;
    }
    showLook();
    updateSave();
    saveLink.click();
  };
  // Pointing at the picture shows the same (and a tape's strip, with EJECT), as long as the mouse moves.
  let hoverAt = 0;
  canvas.addEventListener('pointermove', (e) => {
    if (e.pointerType !== 'mouse' || performance.now() - hoverAt < 500) return;
    hoverAt = performance.now();
    showLook(undefined, 3000, true);
  });

  // --- Fullscreen: double-click the picture, F, or MENU (the OSD comes along) ---
  canvas.addEventListener('dblclick', toggleFullscreen);

  // --- Phone lock screen / media keys: play-pause is the power switch, next/previous change the scene ---
  if ('mediaSession' in navigator) {
    const ms = navigator.mediaSession;
    const handle = (action, fn) => { try { ms.setActionHandler(action, fn); } catch { /* action not supported */ } };
    handle('play', () => { if (!powerState.on) powerSwitch.click(); else if (deck?.current && tapeStatus()?.paused) deck.togglePause(); });
    handle('pause', () => { if (deck?.current && powerState.on) { if (!tapeStatus()?.paused) deck.togglePause(); } else if (powerState.on) powerSwitch.click(); });
    handle('stop', () => { if (powerState.on) powerSwitch.click(); });
    handle('nexttrack', next);
    handle('previoustrack', prev);
    if (deck) {
      handle('seekbackward', (d) => deck.seekBy(-(d.seekOffset || 30)));
      handle('seekforward', (d) => deck.seekBy(d.seekOffset || 30));
      handle('seekto', (d) => { if (Number.isFinite(d.seekTime)) deck.seekTo(d.seekTime); });
    }
  }

  // --- Sound after a blocked start: the first click or key anywhere starts it ---
  const unblock = async () => {
    if (!audioBlocked || !powerState.on) return;
    try { await startAudio(); audioBlocked = false; showLook(); } catch { /* still blocked or no stream */ }
  };
  // Sound is sound, however it came: a stream that came back, or a tape put in while the radio was down.
  document.getElementById('lsdAudio')?.addEventListener('playing', () => { audioBlocked = false; });
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

  // --- AUTOPLAY (MENU › TV): the TV turns itself on when the page opens, as under ?kiosk; where the browser
  // wants a click before sound, the picture plays and asks for one (CLICK OR PRESS A KEY FOR SOUND). Unset
  // ('ask'), the TV asks once a visit, a little after you turn it on; three visits unanswered count as no. ---
  let autoplay = load('lsd.autoplay', 'ask');
  if (!AUTOPLAY_MODES.includes(autoplay)) autoplay = 'ask';
  viewerState.autoplay = autoplay;
  let autoplayAsked = false; // asked this visit
  let askTimer = null;
  const askEl = document.getElementById('autoplay-ask');
  askEl.addEventListener('click', (e) => {
    e.stopPropagation(); // not the cabinet's click
    const v = e.target.closest('button')?.dataset.autoplay;
    if (!v) return;
    setAutoplay(v);
    message(v === 'on' ? 'AUTOPLAY ON · MENU › TV' : 'AUTOPLAY OFF · MENU › TV', 2500);
  });
  function setAutoplay(v) {
    autoplay = viewerState.autoplay = v;
    save('lsd.autoplay', v);
    save('lsd.autoplay.unanswered', 0);
    hideAutoplayAsk();
    menu.refresh();
  }
  function hideAutoplayAsk() {
    clearTimeout(askTimer);
    askEl.hidden = true;
  }
  /** At a power-on with AUTOPLAY on ASK: the question comes up once the ident and the first lines are gone. */
  function scheduleAutoplayAsk() {
    if (kiosk || autoplay !== 'ask' || autoplayAsked) return;
    clearTimeout(askTimer);
    const tryAsk = () => {
      if (!powerState.on || autoplay !== 'ask') return;
      // A page over the picture: wait until it is closed.
      if (pageKey()) {
        askTimer = setTimeout(tryAsk, AUTOPLAY_RETRY_MS);
        return;
      }
      autoplayAsked = true;
      askEl.hidden = false;
      askTimer = setTimeout(() => {
        askEl.hidden = true;
        const n = load('lsd.autoplay.unanswered', 0) + 1;
        if (n >= AUTOPLAY_MAX_UNANSWERED) setAutoplay('off');
        else save('lsd.autoplay.unanswered', n);
      }, AUTOPLAY_ASK_FOR_MS);
    };
    askTimer = setTimeout(tryAsk, AUTOPLAY_ASK_AFTER_MS);
  }
  if (!kiosk && autoplay === 'on') setTimeout(() => { if (!powerState.on) powerSwitch.click(); }, 300);

  // --- Installable app: the service worker (sw.js) caches the app shell; not in the headless checks ---
  if (!embed && 'serviceWorker' in navigator && !new URLSearchParams(location.search).has('nolive')) {
    navigator.serviceWorker.register('sw.js').catch((err) => console.warn('[lsd] service worker:', err.message));
    // Then every scene for offline use (sw.js cacheContent), once the picture has started; only what is missing,
    // plus a check of the rest every few hours.
    navigator.serviceWorker.ready.then((reg) => setTimeout(() => reg.active?.postMessage('cache-content'), 5000))
      .catch(() => {});
    navigator.serviceWorker.addEventListener('message', (e) => {
      if (e.data?.type === 'content-cached') viewerState.offlineContent = e.data;   // for the headless check
    });
  }

  // --- Autopilot and broadcast events ---
  // The source badge: what the TV is listening to. The microphone takes over from a tape, and a tape from the radio.
  // The radio is a LIVE SHOW only when one is really on (a broadcast, or the station's live flag); otherwise it is the
  // JUKEBOX, the station playing archive shows at random. The schedule stands in only when the flag can't be read.
  const sourceEl = document.getElementById('source-badge');
  const updateSource = () => {
    updateSave(); // a show that goes live takes SAVE away
    const scheduled = scheduledOnAir();
    const [text, title] = isMicOn() ? ['MIC', 'Listening to your microphone']
      : deck?.current ? ['TAPE', deck.airName ? `Playing a tape. Live now: ${deck.airName} (eject or L to listen)` : `Playing a tape: ${deck.current.title}`]
      : live() || streamLive || scheduled ? ['LIVE SHOW', `Live on Spaz Radio: ${autopilotState.broadcaster || scheduled || nowPlaying.replace('[LIVE!] ', '') || 'a live show'}`]
      : ['JUKEBOX', 'Spaz Radio is playing shows from the archive'];
    if (sourceEl.textContent !== text) sourceEl.textContent = text;
    sourceEl.classList.toggle('on-air', text === 'TAPE' && !!deck?.airName);
    sourceEl.title = title;
  };
  updateSource();
  setInterval(updateSource, 1000); // a scheduled show starts by the clock, with no event to say so
  window.addEventListener('lsd-mic', updateSource);
  // The station badge: the station, and with SHOW_CHANNELS the channel in three digits (CH.001 .. CH.100).
  const stationText = document.querySelector('#station-badge .station-text');
  const updateBadge = (info = nowShowing()) => {
    const ch = info.channel > 0 ? String(info.channel).padStart(3, '0') : '---';
    stationText.textContent = SHOW_CHANNELS ? `SPAZ RADIO \u2022 CH.${ch}` : 'SPAZ RADIO';
    updateSource();
  };
  window.addEventListener('lsd-now-showing', (e) => {
    if (document.getElementById('osd-ch')?.textContent.startsWith('TUNING')) unflash('osd-ch');
    announceLook(e.detail); updateMediaSession(); updateBadge(e.detail);
    menu.refresh();
  });
  // The background changes on its own clock: with INFO on (and SHOW_SCENE_INFO), only its line comes up.
  window.addEventListener('lsd-background', (e) => {
    if (SHOW_SCENE_INFO && viewerState.info && powerState.on && !autopilotState.isLiveBroadcast && e.detail.background) {
      flash('osd-bg', `BACKGROUND: ${prettyName(e.detail.background)}`, 4000);
    }
    menu.refresh();   // MENU › BACKGROUND names the new one
  });
  // Lite mode on or off (lite.js), switched in place: in lite the scene buttons do nothing; the OSD says what is on.
  const liteButtons = () => {
    const off = liteState.on || live();
    for (const id of ['fxBtn', 'rollBtn', 'holdBtn']) document.getElementById(id).setAttribute('aria-disabled', String(off));
  };
  if (liteState.on) liteButtons();
  window.addEventListener('lsd-lite', (e) => {
    liteButtons(); menu.refresh();
    if (!powerState.on) return;
    if (e.detail.on) {
      showLook();
      message(e.detail.reason === 'slow' ? 'TOO SLOW HERE FOR THE VISUALS \u00B7 COLLAGE INSTEAD' : 'VISUALS: LITE', e.detail.reason === 'slow' ? 5000 : 2000);
    } else {
      message('VISUALS: FULL', 2000);
      if (nowShowing().tuning) showTuning(); else showLook();
    }
  });
  window.addEventListener('lsd-broadcast-status', (e) => {
    tvBody.classList.toggle('live', e.detail.live);
    updateBadge();
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

  // --- Bumper: about every 30 minutes on air, a station ident line for 8 seconds (not while live or
  // while the menu, SCHEDULE, TAPES or the ? page is open; the timer restarts at power-on) ---
  const bumper = document.getElementById('bumper');
  setInterval(() => {
    if (embed || !powerState.on || live() || menu.open || !keysPage.hidden || schedule.open || deck?.pageOpen) return;
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
  // Offline the radio can't come back by itself: point at the microphone, which works without a network.
  const coarse = matchMedia('(pointer: coarse)').matches;
  const noSignalText = () => (navigator.onLine || isMicOn() || !micAvailable() ? 'NO SIGNAL'
    : coarse ? 'OFFLINE \u00B7 MIC: MENU \u203A SOUND' : 'OFFLINE \u00B7 A FOR MICROPHONE');
  (function blink() {
    // TV off: the LED is dark and nothing here changes fast, so wake four times a second instead of every frame.
    const idle = !powerState.on && !audioBlocked;
    // Standing notices only fill the message line when nothing else (HOLD, REACT...) is showing there.
    const msgFree = !document.getElementById('osd-msg').classList.contains('show');
    if (audioBlocked && powerState.on) { if (msgFree) message('CLICK OR PRESS A KEY FOR SOUND', 1500); noSignalShown = true; }
    else if (cvState.noSignal && powerState.on && !live()) { if (msgFree) message(deck?.current && !isMicOn() ? 'LOADING TAPE\u2026' : noSignalText(), 1500); noSignalShown = true; }
    else if (noSignalShown) { noSignalShown = false; announceLook(); }
    const isLive = live() && autopilotState.clock.valid;
    const beats = heardBeats(performance.now(), viewerState.syncMs);
    beatLed.classList.toggle('on', powerState.on && ((beats % 1) + 1) % 1 < 0.15);
    beatLed.classList.toggle('locked', isLive || cvState.beatLocked);
    if (idle) setTimeout(blink, 250); else requestAnimationFrame(blink);
  })();
}

// -------------------------------------------------------
// Media Session: what the phone's lock screen and the OS media controls show (the show playing on the
// radio, and the scene on air).
// -------------------------------------------------------
function updateMediaSession() {
  if (embed) return;
  if (!('mediaSession' in navigator) || typeof MediaMetadata === 'undefined') return;
  // The album line names Liquid LSD (and LIVE during a desktop broadcast), not the scene (SHOW_SCENE_INFO).
  const look = autopilotState.isLiveBroadcast ? 'LIVE' : SHOW_SCENE_INFO ? prettyName(nowShowing().name).toUpperCase() : '';
  navigator.mediaSession.metadata = new MediaMetadata({
    title: deck?.current?.title || nowPlaying || 'Spaz Radio',
    artist: deck?.current ? `Spaz Radio archive${deck.current.date ? ` \u00B7 ${deck.current.date}` : ''}` : 'Spaz Radio',
    album: look ? `Liquid LSD \u00B7 ${look}` : 'Liquid LSD',
    artwork: [
      { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
      { src: 'icon-512.png', sizes: '512x512', type: 'image/png' },
    ],
  });
  navigator.mediaSession.playbackState = powerState.on && !tapeStatus()?.paused ? 'playing' : 'paused';
  try {
    const status = tapeStatus();
    if (status && deck?.current?.seconds) {
      navigator.mediaSession.setPositionState({ duration: deck.current.seconds, position: Math.min(status.pos, deck.current.seconds), playbackRate: 1 });
    } else navigator.mediaSession.setPositionState();
  } catch { /* not supported here */ }
}

// -------------------------------------------------------
// Wake lock: a laptop or phone used as a TV must not dim and lock while the TV is on. The browser drops
// the lock when the page is hidden, so it is taken again when the page comes back.
// -------------------------------------------------------
let wakeLock = null, wakeLockPending = false;
async function holdScreenAwake() {
  if (embed) return;
  if (!powerState.on || wakeLock || wakeLockPending || !navigator.wakeLock || document.visibilityState !== 'visible') return;
  wakeLockPending = true;
  try {
    const lock = await navigator.wakeLock.request('screen');
    // Powered off (or hidden) while the request was pending: give the lock straight back
    if (!powerState.on || document.visibilityState !== 'visible') { lock.release().catch(() => {}); return; }
    wakeLock = lock;
    viewerState.awake = true;
    lock.addEventListener('release', () => { if (wakeLock === lock) wakeLock = null; viewerState.awake = false; });
  } catch { /* denied (battery saver, unsupported): the screen may sleep */ }
  finally { wakeLockPending = false; }
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
let pollGen = 0; // one poll chain at a time: a chain that was overtaken (TV off and on during its requests) ends
/** radio.spaz.org/playing's raw object: the JSONP copy, else fetch() (if the server ever sends a CORS header). */
async function readPlayingFeed() {
  try { return await loadPlayingJsonp(); } catch { /* fall through */ }
  const res = await fetch(PLAYING_URL, { cache: 'no-store', signal: timeoutSignal(10000) });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
/** The server's title, with [LIVE!] when it is live, or '' when it can't be read. */
async function readPlaying() {
  if (!viewerState.readPlaying) { streamLive = streamKnown = false; nowFile = ''; return ''; }
  if (performance.now() < playingRetryAt) return '';
  try {
    const { title, live, download } = parsePlaying(await viewerState.readPlaying());
    streamLive = live; streamKnown = true; nowFile = download;
    lookUpNowFile();
    return title;
  } catch {
    streamLive = streamKnown = false; nowFile = '';
    playingRetryAt = performance.now() + PLAYING_RETRY_MS;
    return '';
  }
}
async function pollNowPlaying() {
  if (embed) return;
  clearTimeout(pollTimer);
  const gen = ++pollGen;
  if (!powerState.on) return;
  try {
    const res = await fetch(STREAM_STATUS_URL, { cache: 'no-store', signal: timeoutSignal(10000) });
    const sources = [].concat((await res.json())?.icestats?.source ?? []);
    // Every mount (the app's low-bandwidth stream too) makes the number the app shows from radio.spaz.org/playing.
    // The TV can't read that one itself: it sends no CORS header, and this status does.
    showListeners(sources.reduce((n, s) => n + (Number(s.listeners) || 0), 0), true);
    const src = sources.find((s) => String(s.listenurl || '').endsWith(STREAM_MOUNT)) ?? sources[0];
    // The Ogg mount sends artist and title separately; the title alone may be just a date.
    const artist = String(src?.artist ?? ''), name = String(src?.title ?? '');
    const title = await readPlaying() || (artist && !name.includes(artist) ? `${artist} - ${name}` : name).replace(/_/g, ' ').trim();
    if (title && title !== nowPlaying) {
      nowPlaying = title;
      updateMediaSession();
      if (!deck?.current) { // on a tape, the track line is the tape's
        flashTrack(`♪ ${nowPlaying}`, 6000);
        flashNext(6000);
      }
    }
  } catch { if (gen === pollGen) showListeners(0); /* status unavailable (or timed out): no track line, no count; the next poll still comes */ }
  if (gen !== pollGen) return;
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
