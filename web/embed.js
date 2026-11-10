// embed.js: the window.LSD API a host app drives the picture with (Spaz Radio's WebView, ?embed). Installed
// on every page, but only embed mode has anything to feed. The host owns the radio: it pushes decoded
// audio in with feed(), pauses the picture when it is hidden, and changes options from its own settings.
import { viewerState } from './ui.js';
import { feedPCM, feedReady } from './dsp.js';
import { advanceForeground, previousForeground, randomForeground, setHold, setHeavyAllowed, setReducedMotion, setRelayEnabled,
  tuneTo, rollScene, setHoldBars, setAutoRollBars, setEffects, nextFxChain, setChain, lockChain, lockTransition, setFollowMusic,
  setBackgroundMode, stepBackground, tuneBackground, toggleSkip, setSkipped, restoreChannel, nowShowing, controlState,
  contentCatalog, autopilotState } from './autopilot.js';
import { cvState } from './dsp.js';

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const MODES = ['auto', 'on', 'off'];

/**
 * `hooks` come from the renderer: pause(), resume(), setQuality(floorIndex), frames(), isPaused().
 * Options (setOption(name, value)):
 *   quality        'auto' | 'low' | 'high'   low: no heavy scenes, coarser picture; high: heavy scenes always on
 *   reactivity     0.25 .. 2                 the REACT knob (1 = default; audio is auto-gained, so 1 suits any feed level)
 *   reducedMotion  'auto' | 'on' | 'off'
 *   live           boolean                   follow a desktop live broadcast (needs the network)
 *   sync           -100 .. 400 (ms)          delay the picture to match the speaker
 */
export function installEmbedApi(hooks) {
  const applyHeavy = () => {
    if (viewerState.heavyMode === 'auto') setHeavyAllowed(!!window.LSD?.gpu?.heavy);
    else setHeavyAllowed(viewerState.heavyMode === 'on');
  };
  const options = {
    quality(v) {
      const q = ['auto', 'low', 'high'].includes(v) ? v : 'auto';
      viewerState.heavyMode = { auto: 'auto', low: 'off', high: 'on' }[q];
      applyHeavy();
      hooks.setQuality(q === 'low' ? 2 : 0);
    },
    reactivity(v) { viewerState.react = clamp(Number(v) || 1, 0.25, 2); },
    reducedMotion(v) {
      viewerState.reducedMode = MODES.includes(v) ? v : 'auto';
      viewerState.reducedNow = viewerState.reduced;
      setReducedMotion(viewerState.reducedNow);
    },
    live(v) { setRelayEnabled(!!v); },
    sync(v) { viewerState.syncMs = clamp(Math.round(Number(v) || 0), -100, 400); },
  };

  // ?debug: a status line every 2 s (the host app logs the console), for working out why a picture is black.
  if (new URLSearchParams(location.search).has('debug')) {
    // An on-screen readout: frames per second, the render resolution the adaptive scaler settled on, heavy scenes.
    const box = document.createElement('div');
    box.style.cssText = 'position:fixed;left:6px;top:calc(6px + env(safe-area-inset-top,0px));z-index:1000;padding:3px 6px;' +
      'font:12px/1.3 monospace;color:#7CFC00;background:rgba(0,0,0,.55);pointer-events:none;white-space:pre';
    document.body.appendChild(box);
    let lastFrames = hooks.frames(), lastT = performance.now();
    setInterval(() => {
      const f = hooks.frames(), t = performance.now(), L = window.LSD;
      const fps = ((f - lastFrames) * 1000) / (t - lastT);
      lastFrames = f; lastT = t;
      const [w, h, k] = L.renderSize();
      box.textContent = `${fps.toFixed(0)} fps${hooks.isPaused() ? ' (paused)' : ''}\n${w}x${h} (${Math.round(k * 100)}%)\n` +
        `heavy ${viewerState.heavyMode}/${L.gpu?.heavy ? 'on' : 'off'}  ${L.autopilotState.deckA?.source ?? ''}`;
    }, 500);
    setInterval(() => {
      const L = window.LSD, c = document.getElementById('glCanvas'), a = L.autopilotState;
      console.log('[lsd-debug] ' + JSON.stringify({
        frames: hooks.frames(), paused: hooks.isPaused(), on: L.powerState.on, warm: L.powerState.warmupProgress,
        canvas: [c.width, c.height, c.clientWidth, c.clientHeight], render: L.renderSize(),
        deckA: a.deckA?.source ?? null, deckB: a.deckB?.source ?? null, bg: a.deckBG?.source ?? null,
        balance: a.mixer?.balance, alpha: a.mixer?.alpha, master: a.masterAlpha, noSignal: L.cvState.noSignal,
        amp: +L.cvState.audio_amp.toFixed(3), feedReady: feedReady(), vis: document.visibilityState,
      }));
    }, 2000);
  }

  Object.assign(window.LSD, {
    /** Mono PCM from the host: Int16Array / Float32Array / ArrayBuffer (LE int16) / base64 string, plus its sample rate. */
    feed: feedPCM,
    feedReady,
    setOption(name, value) {
      if (!options[name]) { console.warn('[lsd] unknown option', name); return false; }
      options[name](value);
      return true;
    },
    pause: hooks.pause,
    resume: hooks.resume,

    // --- Controls (the VJ app). Spaz Radio uses none of these. Each returns what it did; every one does
    // nothing during a live broadcast (state().live), as on the TV. The state is pushed after each. ---
    /** What can be chosen: {scenes: [{channel, name, tags, source, pinnedChain, skipped, runnable}], backgrounds:
     *  [{index, name, source, tags}], fxChains: [{id, name, inRotation}], transitions: [{id, name, inRotation}]}. */
    catalog: () => contentCatalog(),
    /** What is on air and how the controls are set (the object LSDHost.state receives). */
    state,
    next: () => after(advanceForeground()),
    prev: () => after(previousForeground()),
    random: () => after(randomForeground()),
    /** Scene by channel number (catalog().scenes[i].channel): 'ok', 'live', 'range' or 'heavy'. */
    channel: (n) => after(tuneTo(Math.round(Number(n)))),
    /** Rerolls the scene on air's random parameters; false if it has none. */
    roll: () => after(rollScene()),
    /** HOLD: the scene stays until released (automatic scene changes off). */
    hold: (on) => after(setHold(!!on)),
    /** How long a scene holds, in bars (the background twice that). */
    time: (bars) => after(setHoldBars(clamp(Math.round(Number(bars) || 16), 1, 256))),
    /** Reroll the scene every this many bars; 0 = off. */
    autoRoll: (bars) => after(setAutoRollBars(clamp(Math.round(Number(bars) || 0), 0, 256))),
    effects: (on) => after(setEffects(!!on)),
    nextChain: () => after(nextFxChain()),
    /** FX chain by id on the scene on air (null = none): 'ok', 'off', 'live' or 'unknown'. */
    chain: (id) => after(setChain(id ?? null)),
    /** Every scene takes this chain until unlocked (null). */
    lockChain: (id) => after(lockChain(id ?? null)),
    /** Every scene change uses this transition until unlocked (null). */
    lockTransition: (id) => after(lockTransition(id ?? null)),
    /** Calm scenes in breakdowns, a cut on the drop, tempo-weighted picks. */
    follow: (on) => after(setFollowMusic(!!on)),
    /** 'scenes' (changes every 2x TIME), 'hold' (stays) or 'off'. */
    backgroundMode: (mode) => after(setBackgroundMode(mode)),
    nextBackground: () => after(stepBackground(1)),
    prevBackground: () => after(stepBackground(-1)),
    /** Background by index (catalog().backgrounds[i].index). */
    background: (i) => after(tuneBackground(Math.round(Number(i)))),
    /** Skips the scene on air (it no longer comes up by itself), or restores it if skipped. */
    skip: () => after(toggleSkip()),
    restore: (name) => after(restoreChannel(String(name))),
    setSkipped: (names) => after(setSkipped(Array.isArray(names) ? names.map(String) : [])),
  });

  // State to the host: on every change of what is on air or of a control, and once a second if the tempo,
  // beat lock or live broadcast changed. LSDHost.state(json) is optional (Spaz Radio has none).
  let lastPushed = '';
  function state() {
    const live = autopilotState.isLiveBroadcast;
    const bpm = live ? autopilotState.clock.bpm : cvState.bpm;
    return { ...nowShowing(), ...controlState(), bpm: Math.round((bpm || 0) * 10) / 10,
      beatLocked: live ? !!autopilotState.clock.valid : !!cvState.beatLocked, paused: hooks.isPaused() };
  }
  function push() {
    const host = window.LSDHost;
    if (!host?.state) return;
    const json = JSON.stringify(state());
    if (json === lastPushed) return;
    lastPushed = json;
    try { host.state(json); } catch (err) { console.warn('[lsd] LSDHost.state:', err); }
  }
  /** Pushes the state once a command has acted (some are async), and hands back its result. */
  function after(result) {
    Promise.resolve(result).finally(() => setTimeout(push, 0));
    return result;
  }
  window.addEventListener('lsd-now-showing', () => setTimeout(push, 0));
  setInterval(push, 1000);
}
