// autopilot.js
// 24/7 autopilot playlist scheduler + live relay WebSocket client
//
// Crossfade strategy: fade-through-black at the master output level.
// No parameter interpolation — each source runs cleanly with its own params.
// masterAlpha: 1.0 (full output) → 0.0 (black) → switch preset → 1.0

const RELAY_URL    = 'wss://spaz.org/lsd-relay';  // WebSocket proxy — see Apache /lsd-relay config
const FADE_OUT_DUR = 1.0;  // seconds to fade to black
const FADE_IN_DUR  = 1.0;  // seconds to fade back from black
const MIN_HOLD     = 30;   // minimum seconds per preset entry

// -------------------------------------------------------
// autopilotState — imported and read by renderer.js every frame
// -------------------------------------------------------
export const autopilotState = {
  activePreset: null,  // current preset object; renderer reads .deckA/.deckB/.mixer
  masterAlpha:  1.0,   // 0.0 = black, 1.0 = full; applied to mixer uAlpha in renderer
};

// -------------------------------------------------------
// Internal state
// -------------------------------------------------------
let playlist        = [];
let playlistIdx     = 0;
let holdTimer       = 0;
let transitionState = 'hold';  // 'hold' | 'fade_out' | 'fade_in'
let pendingPreset   = null;    // preset to install when we reach black

let isLiveBroadcast = false;

// -------------------------------------------------------
// Per-frame tick — called from renderer.js rAF loop
// dt: delta time in seconds
// -------------------------------------------------------
export function tickAutopilot(dt) {
  if (!autopilotState.activePreset) return;

  switch (transitionState) {
    case 'hold':
      if (!isLiveBroadcast) {
        holdTimer -= dt;
        if (holdTimer <= 0) startTransitionTo(nextAutopilotPreset());
      }
      break;

    case 'fade_out':
      autopilotState.masterAlpha = Math.max(0, autopilotState.masterAlpha - dt / FADE_OUT_DUR);
      if (autopilotState.masterAlpha <= 0) {
        // Install the new preset at black — completely invisible to viewer
        if (pendingPreset !== null) {
          autopilotState.activePreset = pendingPreset;
          pendingPreset = null;
        }
        transitionState = 'fade_in';
      }
      break;

    case 'fade_in':
      autopilotState.masterAlpha = Math.min(1, autopilotState.masterAlpha + dt / FADE_IN_DUR);
      if (autopilotState.masterAlpha >= 1) {
        autopilotState.masterAlpha = 1;
        transitionState = 'hold';
      }
      break;
  }
}

// -------------------------------------------------------
// Advance autopilot playlist and return the next preset
// -------------------------------------------------------
function nextAutopilotPreset() {
  playlistIdx = (playlistIdx + 1) % playlist.length;
  const entry = playlist[playlistIdx];
  holdTimer = Math.max(MIN_HOLD, entry.duration ?? MIN_HOLD);
  console.log(`[autopilot] Advancing to: ${entry.id} (hold ${holdTimer}s)`);
  return entry.preset;
}

// -------------------------------------------------------
// Begin a fade-through-black transition to a new preset
// -------------------------------------------------------
function startTransitionTo(preset) {
  pendingPreset   = preset;
  transitionState = 'fade_out';
}

// -------------------------------------------------------
// WebSocket relay connection with auto-reconnect
// -------------------------------------------------------
function connectRelay() {
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

    switch (msg.type) {

      case 'state_full':
        // Broadcaster sent a full session state — crossfade into it
        if (!isLiveBroadcast) {
          isLiveBroadcast = true;
          notifyBroadcastStatus(true);
          console.log('[autopilot] Broadcaster online — fading to live preset');
        }
        // Always accept full state updates (e.g. preset changes mid-broadcast)
        startTransitionTo(msg.preset);
        break;

      case 'state_delta':
        // Partial parameter update from broadcaster — apply immediately, no fade.
        // Live knob tweaks need to be snappy, not smeared over 2 seconds.
        if (msg.patch && autopilotState.activePreset) {
          applyPatch(autopilotState.activePreset, msg.patch);
        }
        break;

      case 'broadcaster_online':
        // Relay-generated notification; state_full will follow shortly
        console.log('[autopilot] Broadcaster connecting...');
        break;

      case 'broadcaster_offline':
        if (isLiveBroadcast) {
          isLiveBroadcast = false;
          notifyBroadcastStatus(false);
          console.log('[autopilot] Broadcaster offline — returning to autopilot');
          // Restore the current autopilot entry and reset its hold timer
          const entry = playlist[playlistIdx];
          holdTimer = Math.max(MIN_HOLD, entry.duration ?? MIN_HOLD);
          startTransitionTo(entry.preset);
        }
        break;
    }
  });

  ws.addEventListener('close', () => {
    console.log('[autopilot] Relay disconnected — retrying in 5s');
    setTimeout(connectRelay, 5000);
  });

  ws.addEventListener('error', () => {
    // 'close' always follows 'error'; reconnect handled there
  });
}

// -------------------------------------------------------
// Shallow recursive patch — only touches keys present in patch
// -------------------------------------------------------
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
// Notify ui.js of broadcaster status changes via DOM event
// -------------------------------------------------------
function notifyBroadcastStatus(isLive) {
  window.dispatchEvent(new CustomEvent('lsd-broadcast-status', {
    detail: { live: isLive }
  }));
}

// -------------------------------------------------------
// Public init — awaited by renderer.js before first rAF
// -------------------------------------------------------
export async function startAutopilot() {
  const res = await fetch('autopilot.json');
  if (!res.ok) throw new Error(`Failed to load autopilot.json: ${res.status}`);
  playlist = await res.json();
  if (playlist.length === 0) throw new Error('autopilot.json is empty');

  const first = playlist[0];
  // Deep clone so mutations don't corrupt the playlist source
  autopilotState.activePreset = structuredClone(first.preset);
  holdTimer       = Math.max(MIN_HOLD, first.duration ?? MIN_HOLD);
  playlistIdx     = 0;
  transitionState = 'hold';

  console.log(`[autopilot] Loaded ${playlist.length} entries. Starting: ${first.id}`);

  // Connect to relay — non-blocking. Autopilot runs fine if relay is unreachable.
  connectRelay();
}
