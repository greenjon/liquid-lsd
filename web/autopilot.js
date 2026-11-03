// autopilot.js
// 24/7 Dual-Queue Autopilot (Foreground A/B Auto-VJ + Background Auto-BG) + Live Relay Client

import { embed } from './embedmode.js';
import { parseDeckPreset, parseFxChain, parseTransition } from './preset.js';
import { BeatFlywheel } from './beatclock.js';
import { chainFits, chainAnswers, dominantBand } from './pairing.js';
import { MandalaRecipes } from './mandala.js';

const RELAY_URL = 'wss://spaz.org/lsd-relay';
let relayEnabled = !embed;   // embed mode starts with it off (the host app turns it on with the `live` option)
let relaySocket = null;

// Wire protocol version this client speaks; see docs/developer/web_subsystem.md.
const PROTOCOL_VERSION = 2;

export const autopilotSettings = {
  fgPlaylist: 'default.lsdplay',
  bgPlaylist: 'default_bg.lsdplay',
  // Holds are counted in bars (4 beats) of the beat clock and every change lands on a bar line: the
  // crossfade starts `FadeBeats` before it and finishes on it. With no clear beat the clock runs at the
  // last tempo (120 BPM until there is one), so 16 bars is about 32 s.
  fgHoldBars: 16,
  fgFadeBeats: 4,
  fgPlaybackOrder: 'sequential',
  // AUTO ROLL (MENU): reroll the scene on air every this many bars of its hold; 0 = off.
  autoRollBars: 0,
  // FX chain / transition preset ids (web/catalog.json) the autopilot rotates through; a
  // playlist line `preset | chain` pins a chain to one preset. Empty = no FX / plain crossfade.
  fxChains: [],
  transitions: [],
  // Transition preset for the cut on a drop (a single flash, kept out of the regular rotation).
  dropTransition: null,
  // Sources too heavy for integrated GPUs and phones: their presets only rotate when the GPU probe
  // (renderer.js, at power-on) renders `gpuProbe.source` at width x height in at most maxMs per frame.
  heavySources: [],
  gpuProbe: null,
  fxPlaybackOrder: 'sequential',
  bgHoldBars: 32,
  bgFadeBeats: 8,
  bgPlaybackOrder: 'sequential',
  audioStreamUrl: 'https://radio.spaz.org:8060/radio.ogg',
  fallbackBpm: 120.0
};

export const autopilotState = {
  // Active Decks
  deckA: null,
  deckB: null,
  deckBG: null,
  mixer: {
    mode: 0,
    balance: 0.0, // 0.0 = Deck A, 1.0 = Deck B
    alpha: 1.0,
    bloom: 0.0
  },
  bgAlpha: 1.0,
  masterAlpha: 1.0,
  isLiveBroadcast: false,
  broadcaster: null,   // the live broadcaster's name, if they set one
  // Viewer HOLD: the foreground scene stays until it is released (the background keeps changing).
  hold: false,
  // Flywheel following the broadcaster's beat clock; `clock.read(performance.now())` is the beat position.
  clock: new BeatFlywheel()
};

// Internal Playlist & State Machines
// Beat positions (the beat clock the modulators read) drive both layers. The next preset is built
// during the hold, so the change starts exactly on its beat instead of after a fetch.
let lastBeats = 0;

let fgPlaylist = [];
let fgPlaylistIdx = 0;
let fgActiveDeck = 'A'; // 'A' or 'B'
let fgTransitionState = 'hold'; // 'hold' | 'crossfading'
let fgSwitchAt = 0;        // beat the next crossfade starts on
let fgFadeStart = 0;
let fgFadeStartBalance = 0.0;
let fgFadeTargetBalance = 0.0;
let fgNext = null;         // {line, deck, transition} built ahead of the switch
let fgPreparing = false;
let fgHoldStart = 0;       // beat the current scene finished fading in
const fgHistory = [];      // scenes that have been on air, newest last (a channel coming back keeps its chain)
const HISTORY_MAX = 50;

let bgPlaylist = [];
let bgPlaylistIdx = 0;
let bgOnAir = '';               // the background playlist line on screen (nowShowing, the OSD)
let bgTransitionState = 'hold'; // 'hold' | 'fade_out' | 'fade_in'
let bgSwitchAt = 0;
let bgFadeStart = 0;
let bgNext = null;
let bgPreparing = false;
let bgPicked = null;            // the line BACKGROUND / B asked for, until it is on air (steps go on from it)
let bgResume = '';              // the line on air when BG CHANGES went OFF: it comes back with the next mode

/** Beat the next change starts on: `fadeBeats` before the first bar line at least `holdBars` from `beats`. */
export function nextSwitchBeat(beats, holdBars, fadeBeats) {
  const barLine = Math.ceil((beats + holdBars * 4) / 4) * 4;
  return Math.max(beats, barLine - fadeBeats);
}

// Preset Caches
const presetCache = new Map();

async function fetchPreset(nameOrPath) {
  let path = nameOrPath.trim();
  if (!path.startsWith('/') && !path.startsWith('http') && !path.startsWith('library/') && !path.startsWith('presets/')) {
    path = `presets/${path}`;
  }
  if (!path.endsWith('.lsd') && !path.endsWith('.json')) {
    path = `${path}.lsd`;
  }

  if (presetCache.has(path)) return presetCache.get(path);

  try {
    const res = await fetch(path);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    presetCache.set(path, data);
    return data;
  } catch (err) {
    console.warn(`[autopilot] Failed to fetch preset '${path}':`, err.message);
    return null;
  }
}

async function loadPlaylistFile(filename) {
  let path = filename.trim();
  if (!path.startsWith('/') && !path.startsWith('http') && !path.startsWith('library/') && !path.startsWith('playlists/')) {
    path = `playlists/${path}`;
  }
  if (!path.endsWith('.lsdplay') && !path.endsWith('.json')) {
    path = `${path}.lsdplay`;
  }

  try {
    const res = await fetch(path);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const text = await res.text();
    const lines = text.split(/\r?\n/)
      .map(l => l.trim())
      .filter(l => l.length > 0 && !l.startsWith('#'));
    return lines;
  } catch (err) {
    console.warn(`[autopilot] Failed to load playlist '${path}':`, err.message);
    return [];
  }
}

function getNextIndex(currentIdx, length, order) {
  if (length <= 1) return 0;
  if (order === 'shuffle' || order === 'random') {
    let next;
    do {
      next = Math.floor(Math.random() * length);
    } while (next === currentIdx && length > 1);
    return next;
  }
  return (currentIdx + 1) % length;
}

// Content catalog (web/catalog.json): FX chain and transition presets by id.
let catalog = { fxChains: [], transitionPresets: [] };
let mandalaRecipes = null; // MandalaRecipes, loaded at startup for the recipe roll
const contentCache = new Map();
let fxIdx = -1;
let transitionIdx = -1;

async function fetchContent(list, id) {
  const entry = list.find((e) => e.id === id);
  if (!entry) { console.warn(`[autopilot] Unknown content id '${id}'`); return null; }
  if (contentCache.has(entry.file)) return contentCache.get(entry.file);
  try {
    const res = await fetch(entry.file);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    contentCache.set(entry.file, data);
    return data;
  } catch (err) {
    console.warn(`[autopilot] Failed to fetch '${entry.file}':`, err.message);
    return null;
  }
}

/**
 * A playlist line `preset` or `preset | fxchain` -> deck state: the preset's source and
 * parameters plus an FX chain (the pinned one, else the next in the settings rotation).
 */
async function buildDeck(line) {
  const [presetName, pinned] = line.split('|').map((p) => p.trim());
  const raw = await fetchPreset(presetName);
  const deck = parseDeckPreset(raw, Math.random, { mandalaRecipes });
  deck.raw = raw;    // the preset as stored, for ROLL
  deck.tags = raw?.tags ?? [];
  deck.dominant = dominantBand(raw); // the band the scene mostly follows (pairing.js)
  deck.pinned = pinned ?? null;
  let dto = null;
  if (!effectsOn) dto = null;
  else if (pinned && pinned !== 'none') dto = await fetchContent(catalog.fxChains, pinned);
  else if (!pinned) dto = await pickChain(deck.tags, deck.dominant);
  applyChain(deck, dto);
  deck.fxStripped = !effectsOn;   // EFFECTS OFF: gets its chain when they come back on
  return deck;
}

/** Next chain in the settings rotation that suits a preset with these tags (pairing.js); null if none does. */
async function pickChain(tags, dominant = null) {
  const n = autopilotSettings.fxChains.length;
  const calmOk = (c) => !(reducedMotion && (c.tags ?? []).some((t) => CALM_SKIP_CHAIN_TAGS.includes(t)));
  let fallback = null;
  for (let tries = 0; tries < n; tries++) {
    fxIdx = getNextIndex(fxIdx, n, autopilotSettings.fxPlaybackOrder);
    const candidate = await fetchContent(catalog.fxChains, autopilotSettings.fxChains[fxIdx]);
    if (!candidate || !calmOk(candidate) || !chainFits(tags, candidate)) continue;
    if (chainAnswers(tags, dominant, candidate)) return candidate;  // follows another part of the music
    fallback ??= candidate;
  }
  return fallback;
}

// -------------------------------------------------------
// Reduced motion (ui.js: the MENU setting, or the system's prefers-reduced-motion): no hard scenes, no
// flashing chains or transitions, no cut on the drop, crossfades twice as long. ui.js also caps REACT.
// -------------------------------------------------------
let reducedMotion = false;
export function setReducedMotion(on) {
  if (!!on === reducedMotion) return;
  reducedMotion = !!on;
  if (reducedMotion) { fgPrepareToken++; fgPreparing = false; fgNext = null; dropLook = null; } // rebuild the queued scene calmly
}
const CALM_SKIP_LOOK_TAGS = ['hard'];
const CALM_SKIP_CHAIN_TAGS = ['strobe', 'glitch'];
const FLASH_TRANSITIONS = ['luminous_flash', 'cyber_datamosh', 'film_burn'];

function applyChain(deck, dto) {
  Object.assign(deck, dto ? parseFxChain(dto) : { fx: [], fxDryWet: undefined });
  deck.chainName = dto?.name ?? null;
  deck.chainDto = dto ?? null;   // the chain as stored, for ROLL
}

/** Picks the transition for the next foreground crossfade from the settings rotation. */
async function pickTransition() {
  const n = autopilotSettings.transitions.length;
  if (n === 0) return { transition: 'linear_crossfade', transitionParams: {} };
  for (let tries = 0; tries < n; tries++) {
    transitionIdx = getNextIndex(transitionIdx, n, autopilotSettings.fxPlaybackOrder);
    const dto = await fetchContent(catalog.transitionPresets, autopilotSettings.transitions[transitionIdx]);
    const t = dto ? parseTransition(dto) : null;
    if (t && !(reducedMotion && FLASH_TRANSITIONS.includes(t.transition))) return t;
  }
  return { transition: 'linear_crossfade', transitionParams: {} };
}

// -------------------------------------------------------
// State machine updates
// -------------------------------------------------------
export function tickAutopilot(dt, beats, music = {}) {
  if (autopilotState.isLiveBroadcast) return;
  lastBeats = beats;
  lastMusic = music;
  if (followMusic) followSections(beats, music);
  tickRoll(beats);
  tickAutoRoll(beats);
  if (fgSwitchAt === null) fgSwitchAt = nextSwitchBeat(beats, 0, 0);
  if (bgSwitchAt === null) bgSwitchAt = nextSwitchBeat(beats, 0, 0);

  // 1. Foreground A/B crossfade, on the bar line
  if (fgPlaylist.length > 0) {
    if (fgTransitionState === 'hold') {
      prepareForeground();
      if (!autopilotState.hold && beats >= fgSwitchAt && fgNext) startForegroundCrossfade(beats);
    } else {
      const fade = Math.max(0.25, fgFadeLen);
      const progress = Math.min(1.0, Math.max(0, (beats - fgFadeStart) / fade));
      const t = progress * progress * (3.0 - 2.0 * progress); // smooth Hermite blend
      autopilotState.mixer.balance = fgFadeStartBalance + (fgFadeTargetBalance - fgFadeStartBalance) * t;
      if (progress >= 1.0) {
        finishForegroundCrossfade(beats);
      }
    }
  }

  // 2. Background dip to black: out over the first half of the fade, the swap, in over the second
  if (bgPlaylist.length > 0) {
    const half = Math.max(0.25, bgFadeLen * 0.5);
    switch (bgTransitionState) {
      case 'hold':
        prepareBackground();
        if (bgMode === 'scenes' && beats >= bgSwitchAt && bgNext) startBackgroundChange(beats, autopilotSettings.bgFadeBeats);
        break;
      case 'off':
        // BG CHANGES back on: the next background fades in from black.
        if (bgMode === 'off') break;
        if (!bgNext && !bgPreparing) {
          const back = bgPlaylist.indexOf(bgResume);
          prepareBackground(back >= 0 ? back : null);
        }
        if (bgNext) {
          autopilotState.deckBG = bgNext.deck;
          bgOnAir = bgNext.line;
          if (bgPicked === bgOnAir) bgPicked = null;
          bgNext = null;
          bgFadeLen = 4;
          bgTransitionState = 'fade_in';
          bgFadeStart = beats;
          announceBackground();
        }
        break;
      case 'fade_out':
        autopilotState.bgAlpha = Math.max(0.0, 1.0 - (beats - bgFadeStart) / half);
        if (autopilotState.bgAlpha <= 0.0) {
          if (bgMode === 'off' || !bgNext) {   // BG CHANGES OFF: stays black, and is not rendered
            autopilotState.deckBG = null;
            if (bgOnAir) bgResume = bgOnAir;
            bgOnAir = '';
            bgNext = null;
            bgTransitionState = 'off';
            break;
          }
          autopilotState.deckBG = bgNext.deck;
          bgOnAir = bgNext.line;
          if (bgPicked === bgOnAir) bgPicked = null;
          console.log(`[autopilot] Background: ${bgNext.line}`);
          announceBackground();
          bgNext = null;
          bgTransitionState = 'fade_in';
          bgFadeStart = beats;
        }
        break;
      case 'fade_in':
        autopilotState.bgAlpha = Math.min(1.0, (beats - bgFadeStart) / half);
        if (autopilotState.bgAlpha >= 1.0) {
          bgTransitionState = 'hold';
          bgSwitchAt = nextSwitchBeat(beats, autopilotSettings.bgHoldBars, autopilotSettings.bgFadeBeats);
        }
        break;
    }
  }
}

// renderer.js registers a function that loads and compiles a deck's shaders; prepared scenes are warmed
// with it during the hold, so the new scene's source and effects are ready the frame its crossfade starts.
let prewarm = () => {};
export function setPrewarm(fn) { prewarm = fn; }

/**
 * Builds the next foreground scene (and its FX chain and transition) ahead of the switch: the next
 * playlist line, or `line` when the music asks for a particular kind of scene. A newer request
 * supersedes one still being built.
 */
let fgPrepareToken = 0;
function prepareForeground(line = null) {
  if (!line && (fgNext || fgPreparing || fgPlaylist.length === 0)) return null;
  const token = ++fgPrepareToken;
  fgPreparing = true;
  if (!line) {
    const order = autopilotSettings.fgPlaybackOrder;
    const tempo = followMusic && lastMusic.locked && lastMusic.bpm && (order === 'random' || order === 'shuffle');
    const weights = tempo ? fgPlaylist.map((l) => (playable(l) ? tempoWeight(presetTags.get(l) ?? [], lastMusic.bpm) : 0)) : null;
    const pick = weights ? weightedPick(fgPlaylist, weights, fgPlaylistIdx) : -1;
    fgPlaylistIdx = pick >= 0 ? pick : nextPlayable(fgPlaylistIdx, fgPlaylist, order);
    line = fgPlaylist[fgPlaylistIdx];
  }
  return buildLook(line).then((look) => {
    if (token !== fgPrepareToken) return;
    fgNext = look;
    prewarm(look.deck, look.transition);
  }).finally(() => { if (token === fgPrepareToken) fgPreparing = false; });
}

async function buildLook(line) {
  const [deck, transition] = await Promise.all([buildDeck(line), pickTransition()]);
  return { line, deck, transition };
}

// -------------------------------------------------------
// Following the music's sections (energy.js via dsp.js): in a breakdown the next bar line brings a calm
// or building scene; when the drop lands, a hard scene cuts in on it.
// -------------------------------------------------------
const CALM_TAGS = ['ambient', 'build'];
const DROP_TAGS = ['hard', 'bass-gated'];
const presetTags = new Map(); // playlist line -> preset tags
const presetSource = new Map(); // playlist line -> source id

// Heavy sources stay out until the GPU probe says this machine can take them (setHeavyAllowed).
let heavyAllowed = false;
export function setHeavyAllowed(on) { heavyAllowed = !!on; }
const runnable = (line) => (heavyAllowed || !autopilotSettings.heavySources.includes(presetSource.get(line)))
  && !(reducedMotion && (presetTags.get(line) ?? []).some((t) => CALM_SKIP_LOOK_TAGS.includes(t)));
const playable = (line) => runnable(line) && !skipped.has(presetName(line));
const presetName = (line) => (line ?? '').split('|')[0].trim();

// -------------------------------------------------------
// Tempo-aware picks: with a locked beat, a random pick leans towards scenes that suit the tempo (slow:
// ambient, groove, mid; fast: hard, kick, hat). A weighting, not a filter, so every scene still comes up.
// -------------------------------------------------------
let lastMusic = {};
export function tempoWeight(tags, bpm) {
  const has = (...t) => t.some((x) => tags.includes(x));
  if (bpm < 95) return has('ambient', 'groove', 'mid') ? 3 : has('hard') ? 0.3 : 1;
  if (bpm <= 125) return has('groove') ? 2 : 1;
  return has('hard', 'kick', 'hat') ? 2.5 : has('ambient') ? 0.5 : 1;
}

/** Weighted random index into `list` among playable lines other than `current` (`rand` for tests). */
export function weightedPick(list, weights, current, rand = Math.random) {
  let total = 0;
  for (let i = 0; i < list.length; i++) if (i !== current) total += weights[i];
  if (total <= 0) return -1;
  let r = rand() * total;
  for (let i = 0; i < list.length; i++) {
    if (i === current) continue;
    r -= weights[i];
    if (r <= 0) return i;
  }
  return list.length - 1 === current ? list.length - 2 : list.length - 1;
}

/** Next index in `list` (as getNextIndex) whose scene this machine can play; the plain next one if none can. */
function nextPlayable(idx, list, order) {
  let next = idx;
  for (let tries = 0; tries < list.length; tries++) {
    next = getNextIndex(next, list.length, order);
    if (playable(list[next])) return next;
  }
  return getNextIndex(idx, list.length, order);
}
let lastSection = 'normal';
let lastDrops = 0;
let dropLook = null;          // hard scene built when the breakdown starts, ready for the drop

/** A random line from both playlists whose preset has one of `tags`, not the scene on air. */
function pickLine(tags) {
  const current = fgHistory.at(-1)?.line;
  const pool = [...new Set([...fgPlaylist, ...bgPlaylist])]
    .filter((l) => l !== current && playable(l) && (presetTags.get(l) ?? []).some((t) => tags.includes(t)));
  return pool.length ? pool[Math.floor(Math.random() * pool.length)] : null;
}

let calmForBreakdown = false;  // the scene on air (or queued) was brought in by a breakdown
function followSections(beats, { section = 'normal', drops = 0 }) {
  if (section !== lastSection) {
    lastSection = section;
    if (section === 'breakdown' && !autopilotState.hold && fgPlaylist.length) {
      const calm = pickLine(CALM_TAGS);
      if (calm) {
        fgNext = null;
        prepareForeground(calm);
        fgSwitchAt = nextSwitchBeat(beats, 0, autopilotSettings.fgFadeBeats);
        calmForBreakdown = true;
      }
      const hard = pickLine(DROP_TAGS);
      dropLook = null;
      if (hard) {
        Promise.all([buildLook(hard), dropTransition()]).then(([look, flash]) => {
          if (flash) look.transition = flash;
          dropLook = look;
          prewarm(look.deck, look.transition);
        });
      }
      console.log('[autopilot] Breakdown');
    }
  }
  if (drops !== lastDrops) {
    lastDrops = drops;
    if (dropLook && !autopilotState.hold && !reducedMotion) {
      console.log('[autopilot] Drop');
      settleCrossfade();
      fgPrepareToken++;          // drop anything still being prepared
      fgPreparing = false;
      fgNext = dropLook;
      dropLook = null;
      startForegroundCrossfade(beats, 0.5); // a cut on the drop, not a fade
      calmForBreakdown = false;
    }
  }
  // The energy came back without a drop (a short dip): the calm scene goes again at the next bar line.
  if (section === 'normal' && calmForBreakdown && drops === lastDrops) {
    calmForBreakdown = false;
    if (!autopilotState.hold) {
      fgPrepareToken++;
      fgPreparing = false;
      fgNext = null;
      fgSwitchAt = nextSwitchBeat(beats, 0, autopilotSettings.fgFadeBeats);
    }
  }
}

async function dropTransition() {
  if (!autopilotSettings.dropTransition) return null;
  const dto = await fetchContent(catalog.transitionPresets, autopilotSettings.dropTransition);
  return dto ? parseTransition(dto) : null;
}

/** Loads every playlist preset's tags (cached), so section changes can pick a fitting scene. */
async function loadPresetTags() {
  await Promise.all([...new Set([...fgPlaylist, ...bgPlaylist])].map(async (line) => {
    const raw = await fetchPreset(line.split('|')[0].trim());
    presetTags.set(line, raw?.tags ?? []);
    presetSource.set(line, raw?.visualSourceType ?? null);
  }));
}

let bgPrepareToken = 0;
function prepareBackground(idx = null) {
  if (idx === null && (bgNext || bgPreparing || bgPlaylist.length === 0)) return null;
  const token = ++bgPrepareToken;
  bgPreparing = true;
  bgPlaylistIdx = idx ?? nextPlayable(bgPlaylistIdx, bgPlaylist, autopilotSettings.bgPlaybackOrder);
  const line = bgPlaylist[bgPlaylistIdx];
  return buildDeck(line).then((deck) => {
    if (token !== bgPrepareToken) return false;
    bgNext = { line, deck };
    prewarm(deck, null);
    return true;
  }).finally(() => { if (token === bgPrepareToken) bgPreparing = false; });
}

let bgFadeLen = 8;          // beats this background change takes (a dip to black and back)
function startBackgroundChange(beats, fadeBeats) {
  bgFadeLen = fadeBeats;
  bgTransitionState = 'fade_out';
  bgFadeStart = beats;
}

function announceBackground() {
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('lsd-background', { detail: nowShowing() }));
}

let fgFadeLen = 4;          // beats this crossfade takes
function startForegroundCrossfade(beats, fadeBeats = autopilotSettings.fgFadeBeats) {
  finishRoll();
  fgFadeLen = reducedMotion ? fadeBeats * 2 : fadeBeats;
  const { line, deck, transition } = fgNext;
  fgNext = null;
  Object.assign(autopilotState.mixer, transition);
  // Load the next preset onto the off-air deck and fade the crossfader over to it.
  fgFadeStartBalance = autopilotState.mixer.balance;
  if (fgActiveDeck === 'A') {
    autopilotState.deckB = deck;
    fgFadeTargetBalance = 1.0;
    fgActiveDeck = 'B';
  } else {
    autopilotState.deckA = deck;
    fgFadeTargetBalance = 0.0;
    fgActiveDeck = 'A';
  }
  fgFadeStart = beats;
  fgTransitionState = 'crossfading';
  fgHistory.push({ line, deck, transition });
  if (fgHistory.length > HISTORY_MAX) fgHistory.shift();
  console.log(`[autopilot] Crossfading to FG: ${line} (Deck ${fgActiveDeck})`);
  announce();
}

function finishForegroundCrossfade(beats) {
  autopilotState.mixer.balance = fgFadeTargetBalance;
  fgTransitionState = 'hold';
  fgHoldStart = beats;
  fgSwitchAt = nextSwitchBeat(beats, autopilotSettings.fgHoldBars, autopilotSettings.fgFadeBeats);
  planAutoRoll(beats);
}

/** The next change after the current hold, or the next bar line if that has already passed. */
function replanForeground() {
  const { fgHoldBars, fgFadeBeats } = autopilotSettings;
  fgSwitchAt = Math.max(nextSwitchBeat(fgHoldStart, fgHoldBars, fgFadeBeats), nextSwitchBeat(lastBeats, 0, fgFadeBeats));
}

/** What is on air, for the on-screen display: {name, chain, channel, channels}. Fires `lsd-now-showing`. */
export function nowShowing() {
  const deck = fgActiveDeck === 'A' ? autopilotState.deckA : autopilotState.deckB;
  const line = fgHistory.at(-1)?.line ?? fgPlaylist[fgPlaylistIdx] ?? '';
  const name = line.split('|')[0].trim();
  const background = bgOnAir.split('|')[0].trim() || null;
  const backgroundPicked = (bgPicked ?? bgOnAir).split('|')[0].trim() || null;   // BACKGROUND's row: where a step is going
  return { name, chain: deck?.chainName ?? null, background, backgroundPicked, channel: fgPlaylist.indexOf(line) + 1, channels: fgPlaylist.length,
    skipped: skipped.has(name), effects: effectsOn, bgMode };
}

function announce() {
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('lsd-now-showing', { detail: nowShowing() }));
}

// -------------------------------------------------------
// Viewer controls (ui.js). All of them do nothing during a live broadcast.
// -------------------------------------------------------

/** Ends a crossfade in progress at once, so a control acts on a settled pair of decks. */
function settleCrossfade() {
  if (fgTransitionState === 'crossfading') finishForegroundCrossfade(lastBeats);
}

/**
 * CH up / down: the playable scene `dir` (+1 / -1) channels from the one on air (or the one being tuned
 * to), in playlist order, now. A scene that was on air before comes back with the chain it had.
 */
let fgTuning = null;       // the line a channel change is building, so quick presses keep counting
function changeChannel(dir) {
  const n = fgPlaylist.length;
  if (autopilotState.isLiveBroadcast || n === 0) return;
  const from = fgPlaylist.indexOf(fgTuning ?? fgHistory.at(-1)?.line);
  let idx = from < 0 ? fgPlaylistIdx : from;
  for (let tries = 0; tries < n; tries++) {
    idx = (idx + dir + n) % n;
    if (playable(fgPlaylist[idx])) break;
  }
  return tuneChannel(idx);
}

/** R: a playable channel picked at random, never the one on air (or being tuned to). */
function randomChannel(rand = Math.random) {
  const n = fgPlaylist.length;
  if (autopilotState.isLiveBroadcast || n === 0) return;
  const current = fgTuning ?? fgHistory.at(-1)?.line;
  const choices = [];
  for (let i = 0; i < n; i++) if (fgPlaylist[i] !== current && playable(fgPlaylist[i])) choices.push(i);
  if (!choices.length) return;
  return tuneChannel(choices[Math.floor(rand() * choices.length)]);
}

/** Puts playlist line `idx` on air now (shared by CH up / down and R). */
async function tuneChannel(idx) {
  const line = fgPlaylist[idx];
  fgPlaylistIdx = idx;
  fgNext = null;
  if (fgTuning) {
    fgPrepareToken++;        // drop the change still being built
    fgPreparing = false;
    fgTuning = null;
  }
  if (line === fgHistory.at(-1)?.line) return; // back to the one on air (or the only channel)
  const seen = fgHistory.findLast((h) => h.line === line);
  if (seen) {
    // Back with the chain it had, but a fresh transition (reduced motion may have come on since).
    fgNext = { ...seen, transition: await pickTransition() };
    if (effectsOn && seen.deck.fxStripped) await rechain(seen.deck);
    if (fgTuning) return; // a newer press is building another channel
  } else {
    fgTuning = line;
    await prepareForeground(line);
    if (fgTuning !== line) return; // a later press took over
    fgTuning = null;
  }
  settleCrossfade();
  if (fgNext?.line === line && !autopilotState.isLiveBroadcast) startForegroundCrossfade(lastBeats);
}

export const advanceForeground = () => changeChannel(1);
export const previousForeground = () => changeChannel(-1);
export const randomForeground = () => randomChannel();

/** Puts the next suitable FX chain on the scene on air, now. */
export async function nextFxChain() {
  if (autopilotState.isLiveBroadcast) return;
  if (!effectsOn) return 'off';
  const deck = fgActiveDeck === 'A' ? autopilotState.deckA : autopilotState.deckB;
  if (!deck) return;
  finishRoll();
  applyChain(deck, await pickChain(deck.tags ?? [], deck.dominant));
  prewarm(deck, null);
  announce();
}

/** HOLD: keep the current scene until released; on release the scene changes at the next bar line due. */
export function setHold(on) {
  autopilotState.hold = !!on;
  if (!on) replanForeground();
}

/** How long a scene holds, in bars; the background holds twice as long. */
let viewerHoldBars = null; // the TIME dial's setting; outlives settings.json loading after it
export function setHoldBars(bars) {
  viewerHoldBars = bars;
  autopilotSettings.fgHoldBars = bars;
  autopilotSettings.bgHoldBars = bars * 2;
  replanForeground();
}

// -------------------------------------------------------
// ROLL: the scene on air rerolls its random parameters (the preset's and its FX chain's). Continuous
// values glide to the new ones and land on the next bar line at least 2 beats away; stepped ones (a
// colour mode, a petal count, Mandala's recipe) snap there. Gliding keeps feedback simulations (Lenia,
// Reaction Diffusion) from breaking on a jump. The deck is changed in place, so its history entry (CH
// back to it) keeps the rolled version.
// -------------------------------------------------------
// renderer.js registers how to tell a stepped parameter: (source or filter id, kind, name) -> bool.
let isStepped = () => false;
export function setSteppedLookup(fn) { isStepped = fn; }
const MANDALA_STEPPED = new Set(['Lobes', 'Recipe Select']);

let roll = null;   // {deck, items: [{param, from, to, step}], start, end}

/** The glide for one parameter bag: every parameter whose settled base differs in `fresh`. */
function rollItems(bag, fresh, stepped) {
  const items = [];
  for (const [name, param] of Object.entries(bag ?? {})) {
    const next = fresh?.[name];
    if (!param || typeof param !== 'object' || !next || typeof next !== 'object') continue;
    const from = param.baseValue ?? 0, to = next.baseValue ?? 0;
    if (from === to) continue;
    // Mandala's recipe also clears modulators on Lobes / Recipe Select; take the fresh dto whole at the snap.
    items.push({ param, from, to, step: stepped(name), next });
  }
  return items;
}

/** Rerolls the scene on air (ROLL). Returns false when it has nothing that rolls (or during a broadcast). */
export function rollScene() {
  if (autopilotState.isLiveBroadcast) return false;
  settleCrossfade();
  finishRoll();
  const deck = fgActiveDeck === 'A' ? autopilotState.deckA : autopilotState.deckB;
  if (!deck?.raw) return false;
  const fresh = parseDeckPreset(deck.raw, Math.random, { mandalaRecipes });
  const source = deck.source;
  const items = rollItems(deck.params, fresh.params,
    (n) => (source === 'mandala' ? MANDALA_STEPPED.has(n) : isStepped('sources', source, n)));
  if (deck.chainDto) {
    const chain = parseFxChain(deck.chainDto);
    deck.fx?.forEach((slot, i) => {
      const next = chain.fx[i];
      if (slot && next && slot.id === next.id) items.push(...rollItems(slot.params, next.params, (n) => isStepped('filters', slot.id, n)));
    });
  }
  if (!items.length) return false;
  const start = lastBeats;
  roll = { deck, items, start, end: Math.ceil((start + 2) / 4) * 4 };
  planAutoRoll(roll.end);
  return true;
}

function tickRoll(beats) {
  if (!roll) return;
  const p = Math.min(1, Math.max(0, (beats - roll.start) / Math.max(0.25, roll.end - roll.start)));
  if (p >= 1) { finishRoll(); return; }
  const t = p * p * (3 - 2 * p);
  for (const it of roll.items) if (!it.step) it.param.baseValue = it.from + (it.to - it.from) * t;
}

/** Lands a roll in progress at once (a crossfade or another control is about to act on the deck). */
function finishRoll() {
  if (!roll) return;
  for (const it of roll.items) {
    if (it.step && it.next.modulators) it.param.modulators = it.next.modulators;
    it.param.baseValue = it.to;
  }
  roll = null;
}

// AUTO ROLL: every `autoRollBars` bars of a hold, on the bar line, never while a change is due within a bar.
let autoRollAt = null;
function planAutoRoll(from) {
  const bars = autopilotSettings.autoRollBars;
  autoRollAt = bars > 0 ? Math.ceil(from / 4) * 4 + bars * 4 : null;
}
function tickAutoRoll(beats) {
  if (autoRollAt === null || beats < autoRollAt || fgTransitionState !== 'hold') return;
  const changeSoon = !autopilotState.hold && fgSwitchAt - beats < 4;
  if (changeSoon || !rollScene()) planAutoRoll(beats);
}
export function setAutoRollBars(bars) {
  autopilotSettings.autoRollBars = bars;
  planAutoRoll(lastBeats);
}

// -------------------------------------------------------
// EFFECTS (MENU): AUTO puts FX chains on the scenes and backgrounds, OFF plays the sources clean.
// -------------------------------------------------------
let effectsOn = true;
function stripChain(deck) {
  if (!deck || deck.empty) return;
  if (roll?.deck === deck) finishRoll();
  applyChain(deck, null);
  deck.fxStripped = true;
}
async function rechain(deck) {
  if (!deck || deck.empty) return;
  const dto = deck.pinned && deck.pinned !== 'none' ? await fetchContent(catalog.fxChains, deck.pinned)
    : deck.pinned === 'none' ? null : await pickChain(deck.tags ?? [], deck.dominant);
  applyChain(deck, dto);
  deck.fxStripped = false;
  prewarm(deck, null);
}
export async function setEffects(on) {
  if (!!on === effectsOn) return;
  effectsOn = !!on;
  const onAir = [fgActiveDeck === 'A' ? autopilotState.deckA : autopilotState.deckB, autopilotState.deckBG];
  const queued = [fgNext?.deck, dropLook?.deck, bgNext?.deck];
  if (!effectsOn) {
    for (const d of [autopilotState.deckA, autopilotState.deckB, ...onAir, ...queued]) stripChain(d);
  } else if (!autopilotState.isLiveBroadcast) {
    await Promise.all([...new Set([...onAir, ...queued])].map((d) => rechain(d)));
  }
  announce();
}

// FOLLOW MUSIC (MENU): off = no calm scene in a breakdown, no cut on the drop, no tempo-weighted picks.
let followMusic = true;
export function setFollowMusic(on) {
  followMusic = !!on;
  if (!followMusic) { dropLook = null; calmForBreakdown = false; lastSection = 'normal'; }
}

// -------------------------------------------------------
// Background: B or MENU › BACKGROUND changes it now; BG CHANGES picks 'scenes' (every 2x TIME), 'hold'
// (stays until changed by hand) or 'off' (faded out and not rendered).
// -------------------------------------------------------
let bgMode = 'hold';      // the default: the gallery (first background line) stays
export function setBackgroundMode(mode) {
  if (!['scenes', 'hold', 'off'].includes(mode) || mode === bgMode) return;
  bgMode = mode;
  if (mode === 'off' && bgTransitionState !== 'off' && autopilotState.deckBG) {
    bgPrepareToken++; bgPreparing = false; bgNext = null; bgPicked = null;
    startBackgroundChange(lastBeats, 4);
    if (autopilotState.bgAlpha < 1) bgFadeStart = lastBeats - (1 - autopilotState.bgAlpha) * 2; // carry on from a fade in progress
  }
  if (mode === 'scenes') bgSwitchAt = nextSwitchBeat(lastBeats, autopilotSettings.bgHoldBars, autopilotSettings.bgFadeBeats);
}

/** The background `dir` (+1 / -1) along the background playlist, now: a 2-beat dip. Returns its line, or a reason. */
export async function stepBackground(dir = 1) {
  if (autopilotState.isLiveBroadcast) return 'live';
  if (bgMode === 'off') return 'off';
  const n = bgPlaylist.length;
  if (n === 0) return 'none';
  // From the one on screen (or the one a step is already fading to), not the one the rotation has queued.
  let idx = bgPlaylist.indexOf(bgPicked ?? bgOnAir);
  if (idx < 0) idx = bgPlaylistIdx;
  for (let tries = 0; tries < n; tries++) {
    idx = (idx + dir + n) % n;
    if (playable(bgPlaylist[idx])) break;
  }
  const line = bgPicked = bgPlaylist[idx];
  const ok = await Promise.resolve(prepareBackground(idx)).catch(() => false);
  if (!ok && bgPicked === line) bgPicked = null;
  if (!ok || bgMode === 'off' || autopilotState.isLiveBroadcast) return 'superseded';
  if (bgTransitionState === 'hold' || bgTransitionState === 'fade_in') startBackgroundChange(lastBeats, 2);
  return bgNext?.line ?? bgOnAir;
}

// -------------------------------------------------------
// Channel skip, like an old TV's: a skipped channel keeps its number but never comes up by itself (the
// rotation, R, CH up / down, the breakdown and drop picks). Typing its number tunes to it. Kept by preset
// name, so a playlist change can't move a skip; ui.js stores the list. At least SKIP_FLOOR channels stay.
// -------------------------------------------------------
const skipped = new Set();
export const SKIP_FLOOR = 8;
export function setSkipped(names) { skipped.clear(); for (const n of names ?? []) skipped.add(n); }
/** The skipped channels in this playlist, in channel order: [{channel, name}]. */
export function skippedChannels() {
  return fgPlaylist.map((l, i) => ({ channel: i + 1, name: presetName(l) })).filter((c) => skipped.has(c.name));
}
/** Names stored as skipped that this playlist still has (the rest are dropped). */
export function prunedSkips() { const live = new Set(fgPlaylist.map(presetName)); return [...skipped].filter((n) => live.has(n)); }

/**
 * X: skips the channel on air and tunes to the next one, or restores it if it is skipped.
 * Returns {action: 'skipped' | 'restored' | 'floor' | 'none', channel, name}.
 */
export function toggleSkip() {
  if (autopilotState.isLiveBroadcast) return { action: 'none' };
  const line = fgTuning ?? fgHistory.at(-1)?.line;
  if (!line) return { action: 'none' };
  const name = presetName(line), channel = fgPlaylist.indexOf(line) + 1;
  if (skipped.has(name)) { skipped.delete(name); announce(); return { action: 'restored', channel, name }; }
  const left = fgPlaylist.filter((l) => playable(l) && presetName(l) !== name).length;
  if (left < SKIP_FLOOR) return { action: 'floor', channel, name };
  skipped.add(name);
  changeChannel(1);
  return { action: 'skipped', channel, name };
}
/** Takes `name` off the skip list (the SKIPPED page, undo); with `tune`, also tunes to it. */
export function restoreChannel(name, tune = false) {
  skipped.delete(name);
  if (tune) { const idx = fgPlaylist.findIndex((l) => presetName(l) === name); if (idx >= 0) return tuneTo(idx + 1); }
  announce();
}
export function restoreAllChannels() { skipped.clear(); announce(); }

/** Direct tuning (digits, the SKIPPED page): channel `n`, skipped or not. Returns 'ok', 'live', 'range' or 'heavy'. */
export function tuneTo(n) {
  if (autopilotState.isLiveBroadcast) return 'live';
  const idx = n - 1;
  if (!(idx >= 0 && idx < fgPlaylist.length)) return 'range';
  if (!runnable(fgPlaylist[idx])) return 'heavy';
  tuneChannel(idx);
  return 'ok';
}

/** Applies one relay message (state_full / state_delta / broadcaster_offline) to the live state. */
export function handleRelayMessage(msg) {
  switch (msg.type) {
    case 'state_full':
      if (msg.v !== PROTOCOL_VERSION) {
        console.warn(`[autopilot] Ignoring state_full with protocol v${msg.v}; this client speaks v${PROTOCOL_VERSION}`);
        break;
      }
      const wasLive = autopilotState.isLiveBroadcast;
      autopilotState.isLiveBroadcast = true;
      noteClock(msg.clock);
      if (msg.preset) {
        autopilotState.deckA = msg.preset.deckA || null;
        autopilotState.deckB = msg.preset.deckB || null;
        autopilotState.deckBG = msg.preset.deckBG || null;
        // Replace, don't merge: keys absent from a full snapshot (e.g. a cleared FX slot) must go.
        autopilotState.mixer = Object.assign({ balance: 0.0, alpha: 1.0 }, msg.preset.mixer);
        autopilotState.bgAlpha = 1.0;
        // Optional (desktop Preferences > Web Broadcast > Broadcaster name); deltas update it like any key.
        autopilotState.broadcaster = typeof msg.preset.broadcaster === 'string' ? msg.preset.broadcaster : null;
      }
      if (!wasLive) broadcastStatus(true);
      break;

    case 'state_delta':
      if (!autopilotState.isLiveBroadcast) break; // wait for a full snapshot of a known version
      noteClock(msg.clock);
      if (msg.patch) {
        applyPatch(autopilotState, msg.patch);
      }
      break;

    case 'broadcaster_offline':
      if (autopilotState.isLiveBroadcast) {
        autopilotState.isLiveBroadcast = false;
        autopilotState.broadcaster = null;
        autopilotState.clock.reset();
        broadcastStatus(false);
        console.log('[autopilot] Broadcaster offline — returning to autopilot');
        // Pick the autopilot up again at the next bar line (planned on the next tick, from the current beat).
        fgSwitchAt = null;
        bgSwitchAt = null;
      }
      break;
  }
}

/** Tells the UI the desktop went on or off air (`lsd-broadcast-status`, detail {live}). */
function broadcastStatus(live) {
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('lsd-broadcast-status', { detail: { live } }));
}

// -------------------------------------------------------
// Relay Client for Live Takeover
// -------------------------------------------------------
function connectRelay() {
  // ?nolive keeps test runs (tools/render_check.mjs) from picking up a real broadcast.
  if (new URLSearchParams(location.search).has('nolive')) return;
  if (!relayEnabled || relaySocket) return;
  let ws;
  try {
    ws = new WebSocket(RELAY_URL);
  } catch (err) {
    console.warn('[autopilot] WebSocket unavailable:', err.message);
    return;
  }
  relaySocket = ws;

  ws.addEventListener('open', () => {
    console.log('[autopilot] Connected to relay server');
  });

  ws.addEventListener('message', (event) => {
    let msg;
    try { msg = JSON.parse(event.data); } catch { return; }
    handleRelayMessage(msg);
  });

  ws.addEventListener('close', () => {
    if (relaySocket === ws) relaySocket = null;
    if (relayEnabled) setTimeout(connectRelay, 5000);
  });
}

/** Turns the live-broadcast connection on or off (embed mode); off also returns to the autopilot. */
export function setRelayEnabled(on) {
  relayEnabled = !!on;
  if (relayEnabled) connectRelay();
  else {
    relaySocket?.close();
    relaySocket = null;
    handleRelayMessage({ type: 'broadcaster_offline' });
  }
}

function noteClock(clock) {
  if (clock && Number.isFinite(clock.beats) && Number.isFinite(clock.bpm)) {
    autopilotState.clock.anchor(clock.beats, clock.bpm, performance.now());
  }
}

function applyPatch(target, patch) {
  for (const [key, val] of Object.entries(patch)) {
    if (
      typeof val === 'object' && val !== null && !Array.isArray(val) &&
      typeof target[key] === 'object' && target[key] !== null
    ) {
      applyPatch(target[key], val);
    } else {
      target[key] = val;
    }
  }
}

// -------------------------------------------------------
// Startup
// -------------------------------------------------------
export async function startAutopilot() {
  // 1. Load settings.json
  try {
    const res = await fetch('settings.json');
    if (res.ok) {
      const cfg = await res.json();
      Object.assign(autopilotSettings, cfg);
      if (viewerHoldBars) { autopilotSettings.fgHoldBars = viewerHoldBars; autopilotSettings.bgHoldBars = viewerHoldBars * 2; }
      console.log('[autopilot] Loaded settings.json:', autopilotSettings);
    }
  } catch (e) {
    console.warn('[autopilot] Could not load settings.json, using defaults.');
  }

  // 1a. Mandala's recipe table, so each mandala scene rolls one recipe as it loads (preset.js)
  try {
    const res = await fetch('mandala_recipes.json');
    if (res.ok) mandalaRecipes = new MandalaRecipes(await res.json());
  } catch (e) {
    console.warn('[autopilot] Could not load mandala_recipes.json; mandala scenes keep their stored recipe.');
  }

  // 1b. Load the content catalog (FX chain and transition presets)
  try {
    const res = await fetch('catalog.json');
    if (res.ok) catalog = await res.json();
  } catch (e) {
    console.warn('[autopilot] Could not load catalog.json; no FX chains or transitions.');
  }

  // 2. Load Foreground playlist
  fgPlaylist = await loadPlaylistFile(autopilotSettings.fgPlaylist);
  if (fgPlaylist.length === 0) {
    // Fallback: check autopilot.json
    try {
      const res = await fetch('autopilot.json');
      if (res.ok) {
        const list = await res.json();
        if (list.length > 0) {
          const first = list[0].preset;
          autopilotState.deckA = first.deckA || first;
          autopilotState.deckB = first.deckB || first;
          autopilotState.mixer = Object.assign(autopilotState.mixer, first.mixer || {});
        }
      }
    } catch (e) {}
  } else {
    bgPlaylist = await loadPlaylistFile(autopilotSettings.bgPlaylist);
    await loadPresetTags();
    // A random playable channel to start on, so every visit doesn't open on the same scene.
    const starts = fgPlaylist.flatMap((line, i) => (playable(line) ? [i] : []));
    fgPlaylistIdx = starts.length ? starts[Math.floor(Math.random() * starts.length)] : 0;
    const first = fgPlaylist[fgPlaylistIdx];
    autopilotState.deckA = await buildDeck(first);
    autopilotState.deckB = await buildDeck(first);
    autopilotState.mixer.balance = 0.0;
    fgActiveDeck = 'A';
    fgHistory.push({ line: first, deck: autopilotState.deckA, transition: {} });
    fgSwitchAt = nextSwitchBeat(lastBeats, autopilotSettings.fgHoldBars, autopilotSettings.fgFadeBeats);
    announce();
  }

  // 3. Background playlist
  if (bgPlaylist.length === 0) {
    bgPlaylist = await loadPlaylistFile(autopilotSettings.bgPlaylist);
    await loadPresetTags();
  }
  if (bgPlaylist.length > 0) {
    bgPlaylistIdx = 0;
    autopilotState.deckBG = await buildDeck(bgPlaylist[0]);
    bgOnAir = bgPlaylist[0];
    autopilotState.bgAlpha = 1.0;
    bgSwitchAt = nextSwitchBeat(lastBeats, autopilotSettings.bgHoldBars, autopilotSettings.bgFadeBeats);
  }

  // 4. Connect to live relay
  connectRelay();
}
