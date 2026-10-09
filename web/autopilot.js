// autopilot.js
// 24/7 Dual-Queue Autopilot (Foreground A/B Auto-VJ + Background Auto-BG) + Live Relay Client

import { parseDeckPreset, parseFxChain, parseTransition } from './preset.js';
import { BeatFlywheel } from './beatclock.js';

const RELAY_URL = 'wss://spaz.org/lsd-relay';

// Wire protocol version this client speaks; see docs/developer/web_subsystem.md.
const PROTOCOL_VERSION = 2;

export const autopilotSettings = {
  fgPlaylist: 'default.lsdplay',
  bgPlaylist: 'default_bg.lsdplay',
  fgHoldDuration: 45.0,
  fgFadeDuration: 2.5,
  fgPlaybackOrder: 'sequential',
  // FX chain / transition preset ids (web/catalog.json) the autopilot rotates through; a
  // playlist line `preset | chain` pins a chain to one preset. Empty = no FX / plain crossfade.
  fxChains: [],
  transitions: [],
  fxPlaybackOrder: 'sequential',
  bgHoldDuration: 90.0,
  bgFadeDuration: 4.0,
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
  // Flywheel following the broadcaster's beat clock; `clock.read(performance.now())` is the beat position.
  clock: new BeatFlywheel()
};

// Internal Playlist & State Machines
let fgPlaylist = [];
let fgPlaylistIdx = 0;
let fgActiveDeck = 'A'; // 'A' or 'B'
let fgTransitionState = 'hold'; // 'hold' | 'crossfading'
let fgHoldTimer = 45.0;
let fgFadeTimer = 0.0;
let fgFadeStartBalance = 0.0;
let fgFadeTargetBalance = 0.0;

let bgPlaylist = [];
let bgPlaylistIdx = 0;
let bgTransitionState = 'hold'; // 'hold' | 'fade_out' | 'fade_in'
let bgHoldTimer = 90.0;
let bgFadeTimer = 0.0;
let pendingBgPreset = null;

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
  const deck = parseDeckPreset(await fetchPreset(presetName));
  let chainId = pinned;
  if (!chainId && autopilotSettings.fxChains.length > 0) {
    const n = autopilotSettings.fxChains.length;
    fxIdx = getNextIndex(fxIdx, n, autopilotSettings.fxPlaybackOrder);
    chainId = autopilotSettings.fxChains[fxIdx];
  }
  if (chainId && chainId !== 'none') {
    const dto = await fetchContent(catalog.fxChains, chainId);
    if (dto) Object.assign(deck, parseFxChain(dto));
  }
  return deck;
}

/** Picks the transition for the next foreground crossfade from the settings rotation. */
async function pickTransition() {
  const n = autopilotSettings.transitions.length;
  if (n === 0) return { transition: 'linear_crossfade', transitionParams: {} };
  transitionIdx = getNextIndex(transitionIdx, n, autopilotSettings.fxPlaybackOrder);
  const dto = await fetchContent(catalog.transitionPresets, autopilotSettings.transitions[transitionIdx]);
  return dto ? parseTransition(dto) : { transition: 'linear_crossfade', transitionParams: {} };
}

// -------------------------------------------------------
// State machine updates
// -------------------------------------------------------
export function tickAutopilot(dt) {
  if (autopilotState.isLiveBroadcast) return;

  // 1. Foreground A/B Crossfade State Machine
  if (fgPlaylist.length > 0) {
    switch (fgTransitionState) {
      case 'hold':
        fgHoldTimer -= dt;
        if (fgHoldTimer <= 0) {
          advanceForeground();
        }
        break;

      case 'crossfading':
        fgFadeTimer += dt;
        const progress = Math.min(1.0, fgFadeTimer / Math.max(0.1, autopilotSettings.fgFadeDuration));
        // Smooth Hermite blend
        const t = progress * progress * (3.0 - 2.0 * progress);
        autopilotState.mixer.balance = fgFadeStartBalance + (fgFadeTargetBalance - fgFadeStartBalance) * t;

        if (progress >= 1.0) {
          autopilotState.mixer.balance = fgFadeTargetBalance;
          fgTransitionState = 'hold';
          fgHoldTimer = autopilotSettings.fgHoldDuration;
        }
        break;
    }
  }

  // 2. Background Dip-to-Black State Machine
  if (bgPlaylist.length > 0) {
    const fadeDur = Math.max(0.1, autopilotSettings.bgFadeDuration * 0.5);
    switch (bgTransitionState) {
      case 'hold':
        bgHoldTimer -= dt;
        if (bgHoldTimer <= 0) {
          startBgTransition();
        }
        break;

      case 'fade_out':
        bgFadeTimer += dt;
        autopilotState.bgAlpha = Math.max(0.0, 1.0 - (bgFadeTimer / fadeDur));
        if (autopilotState.bgAlpha <= 0.0) {
          if (pendingBgPreset) {
            autopilotState.deckBG = pendingBgPreset;
            pendingBgPreset = null;
          }
          bgTransitionState = 'fade_in';
          bgFadeTimer = 0.0;
        }
        break;

      case 'fade_in':
        bgFadeTimer += dt;
        autopilotState.bgAlpha = Math.min(1.0, bgFadeTimer / fadeDur);
        if (autopilotState.bgAlpha >= 1.0) {
          autopilotState.bgAlpha = 1.0;
          bgTransitionState = 'hold';
          bgHoldTimer = autopilotSettings.bgHoldDuration;
        }
        break;
    }
  }
}

export async function advanceForeground() {
  if (fgPlaylist.length === 0) return;
  fgPlaylistIdx = getNextIndex(fgPlaylistIdx, fgPlaylist.length, autopilotSettings.fgPlaybackOrder);
  const presetName = fgPlaylist[fgPlaylistIdx];
  const normalized = await buildDeck(presetName);
  Object.assign(autopilotState.mixer, await pickTransition());

  // Crossfade target
  if (fgActiveDeck === 'A') {
    // Current is Deck A (balance ~0.0) -> load next to Deck B and crossfade to balance 1.0
    autopilotState.deckB = normalized;
    fgFadeStartBalance = autopilotState.mixer.balance;
    fgFadeTargetBalance = 1.0;
    fgActiveDeck = 'B';
  } else {
    // Current is Deck B (balance ~1.0) -> load next to Deck A and crossfade to balance 0.0
    autopilotState.deckA = normalized;
    fgFadeStartBalance = autopilotState.mixer.balance;
    fgFadeTargetBalance = 0.0;
    fgActiveDeck = 'A';
  }

  fgFadeTimer = 0.0;
  fgTransitionState = 'crossfading';
  console.log(`[autopilot] Crossfading to FG: ${presetName} (Deck ${fgActiveDeck})`);
}

async function startBgTransition() {
  if (bgPlaylist.length === 0) return;
  bgPlaylistIdx = getNextIndex(bgPlaylistIdx, bgPlaylist.length, autopilotSettings.bgPlaybackOrder);
  const presetName = bgPlaylist[bgPlaylistIdx];
  pendingBgPreset = await buildDeck(presetName);

  bgFadeTimer = 0.0;
  bgTransitionState = 'fade_out';
  console.log(`[autopilot] Dipping to BG: ${presetName}`);
}

/** Applies one relay message (state_full / state_delta / broadcaster_offline) to the live state. */
export function handleRelayMessage(msg) {
  switch (msg.type) {
    case 'state_full':
      if (msg.v !== PROTOCOL_VERSION) {
        console.warn(`[autopilot] Ignoring state_full with protocol v${msg.v}; this client speaks v${PROTOCOL_VERSION}`);
        break;
      }
      autopilotState.isLiveBroadcast = true;
      noteClock(msg.clock);
      if (msg.preset) {
        autopilotState.deckA = msg.preset.deckA || null;
        autopilotState.deckB = msg.preset.deckB || null;
        autopilotState.deckBG = msg.preset.deckBG || null;
        // Replace, don't merge: keys absent from a full snapshot (e.g. a cleared FX slot) must go.
        autopilotState.mixer = Object.assign({ balance: 0.0, alpha: 1.0 }, msg.preset.mixer);
        autopilotState.bgAlpha = 1.0;
      }
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
        autopilotState.clock.reset();
        console.log('[autopilot] Broadcaster offline — returning to autopilot');
        fgHoldTimer = 5.0;
        bgHoldTimer = 5.0;
      }
      break;
  }
}

// -------------------------------------------------------
// Relay Client for Live Takeover
// -------------------------------------------------------
function connectRelay() {
  // ?nolive keeps test runs (tools/render_check.mjs) from picking up a real broadcast.
  if (new URLSearchParams(location.search).has('nolive')) return;
  let ws;
  try {
    ws = new WebSocket(RELAY_URL);
  } catch (err) {
    console.warn('[autopilot] WebSocket unavailable:', err.message);
    return;
  }

  ws.addEventListener('open', () => {
    console.log('[autopilot] Connected to relay server');
  });

  ws.addEventListener('message', (event) => {
    let msg;
    try { msg = JSON.parse(event.data); } catch { return; }
    handleRelayMessage(msg);
  });

  ws.addEventListener('close', () => {
    setTimeout(connectRelay, 5000);
  });
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
      console.log('[autopilot] Loaded settings.json:', autopilotSettings);
    }
  } catch (e) {
    console.warn('[autopilot] Could not load settings.json, using defaults.');
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
    fgPlaylistIdx = 0;
    autopilotState.deckA = await buildDeck(fgPlaylist[0]);
    autopilotState.deckB = await buildDeck(fgPlaylist[0]);
    autopilotState.mixer.balance = 0.0;
    fgActiveDeck = 'A';
    fgHoldTimer = autopilotSettings.fgHoldDuration;
  }

  // 3. Load Background playlist
  bgPlaylist = await loadPlaylistFile(autopilotSettings.bgPlaylist);
  if (bgPlaylist.length > 0) {
    bgPlaylistIdx = 0;
    autopilotState.deckBG = await buildDeck(bgPlaylist[0]);
    autopilotState.bgAlpha = 1.0;
    bgHoldTimer = autopilotSettings.bgHoldDuration;
  }

  // 4. Connect to live relay
  connectRelay();
}
