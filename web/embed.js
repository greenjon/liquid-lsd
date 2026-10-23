// embed.js: the window.LSD API a host app drives the picture with (Spaz Radio's WebView, ?embed). Installed
// on every page, but only embed mode has anything to feed. The host owns the radio: it pushes decoded
// audio in with feed(), pauses the picture when it is hidden, and changes options from its own settings.
import { viewerState } from './ui.js';
import { feedPCM, feedReady } from './dsp.js';
import { advanceForeground, previousForeground, setHold, setHeavyAllowed, setReducedMotion, setRelayEnabled } from './autopilot.js';

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const MODES = ['auto', 'on', 'off'];

/**
 * `hooks` come from the renderer: pause(), resume(), setQuality(floorIndex).
 * Options (setOption(name, value)):
 *   quality        'auto' | 'low' | 'high'   low: no heavy looks, coarser picture; high: heavy looks always on
 *   reactivity     0.25 .. 4                 the REACT knob (2 = default)
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
    reactivity(v) { viewerState.react = clamp(Number(v) || 2, 0.25, 4); },
    reducedMotion(v) {
      viewerState.reducedMode = MODES.includes(v) ? v : 'auto';
      viewerState.reducedNow = viewerState.reduced;
      setReducedMotion(viewerState.reducedNow);
    },
    live(v) { setRelayEnabled(!!v); },
    sync(v) { viewerState.syncMs = clamp(Math.round(Number(v) || 0), -100, 400); },
  };

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
