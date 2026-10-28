// embed.js: the window.LSD API a host app drives the picture with (Spaz Radio's WebView, ?embed). Installed
// on every page, but only embed mode has anything to feed. The host owns the radio: it pushes decoded
// audio in with feed(), pauses the picture when it is hidden, and changes options from its own settings.
import { viewerState } from './ui.js';
import { feedPCM, feedReady } from './dsp.js';
import { advanceForeground, previousForeground, setHold, setHeavyAllowed, setReducedMotion, setRelayEnabled } from './autopilot.js';

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const MODES = ['auto', 'on', 'off'];

/**
 * `hooks` come from the renderer: pause(), resume(), setQuality(floorIndex), frames(), isPaused().
 * Options (setOption(name, value)):
 *   quality        'auto' | 'low' | 'high'   low: no heavy looks, coarser picture; high: heavy looks always on
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
    // An on-screen readout: frames per second, the render resolution the adaptive scaler settled on, heavy looks.
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
    // Not used by Spaz Radio v1 (the picture is a background); here so touch controls can be added later.
    next: () => advanceForeground(),
    prev: () => previousForeground(),
    hold: (on) => setHold(!!on),
  });
}
