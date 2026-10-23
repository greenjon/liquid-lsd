import { cvState, tick, suspendAudio, resumeAudio } from './dsp.js';
import { powerState, viewerState } from './ui.js';
import { autopilotState, autopilotSettings, tickAutopilot, startAutopilot, handleRelayMessage, advanceForeground, setPrewarm, setHeavyAllowed } from './autopilot.js';
import { evaluateParameter, makeEvalContext, paramSpec } from './evaluator.js';
import { lookupParam } from './isf.js';
import { Library, DeckPipeline, MixerPipeline } from './graph.js';
import { MandalaRecipes } from './mandala.js';
import { heardBeats, DelayLine } from './sync.js';
import { installEmbedApi } from './embed.js';

async function loadText(url) {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Failed to load ${url}: status ${res.status}`);
  }
  return res.text();
}

function createShader(gl, type, source) {
  const shader = gl.createShader(type);
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const info = gl.getShaderInfoLog(shader);
    gl.deleteShader(shader);
    throw new Error(`Shader compilation failed:\n${info}\nSource:\n${source}`);
  }
  return shader;
}

function createProgram(gl, vertSource, fragSource) {
  const vs = createShader(gl, gl.VERTEX_SHADER, vertSource);
  const fs = createShader(gl, gl.FRAGMENT_SHADER, fragSource);
  const prog = gl.createProgram();
  gl.attachShader(prog, vs);
  gl.attachShader(prog, fs);
  gl.linkProgram(prog);
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
    const info = gl.getProgramInfoLog(prog);
    gl.deleteProgram(prog);
    throw new Error(`Program link failed: ${info}`);
  }
  return prog;
}

function getUniformLocations(gl, program, names) {
  const locs = {};
  for (const name of names) {
    locs[name] = gl.getUniformLocation(program, name);
  }
  return locs;
}

async function init() {
  const canvas = document.getElementById('glCanvas');
  const gl = canvas.getContext('webgl2', {
    alpha: false,
    depth: false,
    antialias: false,
    preserveDrawingBuffer: false,
    powerPreference: 'high-performance'
  });

  if (!gl) {
    alert('WebGL 2 is not supported in this browser.');
    throw new Error('WebGL 2 not supported');
  }

  const extFloat = gl.getExtension('EXT_color_buffer_float');
  const fmt = { internalFormat: gl.RGBA16F, format: gl.RGBA, type: gl.HALF_FLOAT };

  if (extFloat) {
    console.log('EXT_color_buffer_float supported: using RGBA16F render targets.');
  } else {
    console.warn('EXT_color_buffer_float not supported: falling back to RGBA8.');
    fmt.internalFormat = gl.RGBA8;
    fmt.type = gl.UNSIGNED_BYTE;
  }

  const [
    blitVertSrc,
    blitFragSrc,
    mandalaVertSrc,
    mandalaFragSrc,
    mixerFragSrc,
    crtFragSrc,
    catalog,
    mandalaTable
  ] = await Promise.all([
    loadText('shaders/blit.vert'),
    loadText('shaders/blit.frag'),
    loadText('shaders/mandala.vert'),
    loadText('shaders/mandala.frag'),
    loadText('shaders/mixer.frag'),
    loadText('shaders/crt_post.frag'),
    loadText('catalog.json').then(JSON.parse),
    loadText('mandala_recipes.json').then(JSON.parse)
  ]);

  const mandalaProgram = createProgram(gl, mandalaVertSrc, mandalaFragSrc);
  const mixerProgram   = createProgram(gl, blitVertSrc, mixerFragSrc);
  const blitProgram    = createProgram(gl, blitVertSrc, blitFragSrc);
  const crtProgram     = createProgram(gl, blitVertSrc, crtFragSrc);

  const mandalaLocs = getUniformLocations(gl, mandalaProgram, [
    'uL1', 'uL2', 'uL3', 'uL4', 'uA', 'uB', 'uC', 'uD',
    'uMaxR',
    'uYaw', 'uPitch', 'uPersp',
    'uThickness', 'uGlobalScale', 'uGlobalRotation', 'uAspectRatio',
    'uZoom', 'uRotateZ',
    'uHueOffset', 'uHueSweep', 'uAlpha', 'uDepth'
  ]);

  const crtUniforms = getUniformLocations(gl, crtProgram, [
    'uTexture', 'uResolution', 'uTime',
    'uPowerOn', 'uWarmupProgress', 'uShutdownProgress', 'uNoSignal'
  ]);

  // Fullscreen quad VAO
  const quadVAO = gl.createVertexArray();
  gl.bindVertexArray(quadVAO);
  const quadVBO = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, quadVBO);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([
    -1.0, -1.0,  0.0, 0.0,
     1.0, -1.0,  1.0, 0.0,
    -1.0,  1.0,  0.0, 1.0,
     1.0,  1.0,  1.0, 1.0
  ]), gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0);
  gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 16, 0);
  gl.enableVertexAttribArray(1);
  gl.vertexAttribPointer(1, 2, gl.FLOAT, false, 16, 8);
  gl.bindVertexArray(null);

  // Mandala ribbon VAO
  const mandalaVAO = gl.createVertexArray();
  gl.bindVertexArray(mandalaVAO);
  const mandalaVBO = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, mandalaVBO);
  const MANDALA_POINTS = 2048;
  const mandalaVertices = new Float32Array(MANDALA_POINTS * 2 * 2);
  for (let i = 0; i < MANDALA_POINTS; i++) {
    const phase = i / (MANDALA_POINTS - 1);
    mandalaVertices[(i * 2 + 0) * 2 + 0] = phase;
    mandalaVertices[(i * 2 + 0) * 2 + 1] = -1.0;
    mandalaVertices[(i * 2 + 1) * 2 + 0] = phase;
    mandalaVertices[(i * 2 + 1) * 2 + 1] = 1.0;
  }
  gl.bufferData(gl.ARRAY_BUFFER, mandalaVertices, gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0);
  gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 8, 0);
  gl.bindVertexArray(null);

  // Initial sizing
  const dpr = window.devicePixelRatio || 1;
  let curWidth  = Math.max(1, Math.floor(window.innerWidth  * dpr));
  let curHeight = Math.max(1, Math.floor(window.innerHeight * dpr));
  canvas.width  = curWidth;
  canvas.height = curHeight;

  // Start dual-queue autopilot
  await startAutopilot();

  let lastTime        = performance.now();
  let elapsedTime     = 0;
  let frameCount      = 0;
  let frameDt         = 0;

  const evalCtx = makeEvalContext();
  const audioDelay = new DelayLine();
  const AUDIO_KEYS = ['audio_amp', 'audio_bass', 'audio_mid', 'audio_high', 'audio_flux_amp', 'audio_flux_bass', 'audio_flux_mid', 'audio_flux_high'];
  function updateEvalContext() {
    // Live broadcast: the desktop's beat clock (phase-locked flywheel). Standalone: the web's own
    // beat detector (dsp.js) integrates tempo into total beats, so a tempo change never jumps the phase.
    const live = autopilotState.isLiveBroadcast && autopilotState.clock.valid;
    const bpm = live ? autopilotState.clock.bpm : cvState.bpm;
    // SYNC (sync.js): the beat and the audio signals the picture reads are those the viewer hears now.
    const now = performance.now();
    const beats = heardBeats(now, viewerState.syncMs);
    audioDelay.push(now, Object.fromEntries(AUDIO_KEYS.map((key) => [key, cvState[key]])));
    const heard = { ...audioDelay.read(now, viewerState.syncMs) };
    evalCtx.time = elapsedTime;
    evalCtx.beats = beats;
    evalCtx.frame = frameCount;
    evalCtx.dt = frameDt;
    evalCtx.bpm = bpm;
    // REACT dial: scales every audio signal (not the beat), so quiet streams still move and loud ones calm down.
    const k = viewerState.reducedNow ? Math.min(viewerState.react, 1.2) : viewerState.react; // reduced motion caps it
    evalCtx.cv = {
      audio_amp: heard.audio_amp * k, audio_bass: heard.audio_bass * k,
      audio_mid: heard.audio_mid * k, audio_high: heard.audio_high * k,
      audio_flux_amp: heard.audio_flux_amp * k, audio_flux_bass: heard.audio_flux_bass * k,
      audio_flux_mid: heard.audio_flux_mid * k, audio_flux_high: heard.audio_flux_high * k,
      beatSine: Math.sin(beats * 2 * Math.PI), bpm,
    };
  }
  updateEvalContext();

  function evalP(paramObj, fallback = 0.0, spec) {
    return evaluateParameter(paramObj, evalCtx, spec, fallback);
  }

  // ISF input value: looks the NAME up in a parameter bag, then runs the modulation maths
  // with the input's range as the spec.
  function evalInput(bag, input) {
    return evalP(lookupParam(bag, input), input.DEFAULT ?? 0.0, paramSpec(input) ?? undefined);
  }

  // 1x1 transparent texture bound in place of a pass target that is the current render target.
  const dummyTex = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, dummyTex);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, 1, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(4));

  const ctx = {
    gl, quadVAO, dummyTex, fmt,
    hasFloat: !!extFloat,
    library: new Library(gl, blitVertSrc, catalog),
    input: evalInput,
    scalar: (raw, fallback) => evalP(raw, fallback),
    blit: { program: blitProgram, uTexture: gl.getUniformLocation(blitProgram, 'uTexture') },
    mixerProg: {
      program: mixerProgram,
      ...Object.fromEntries(['uTex1', 'uTexBG', 'uProgress', 'uBgAlpha', 'uLevelA', 'uLevelB', 'uLevelBG', 'uMasterLevel']
        .map((n) => [n, gl.getUniformLocation(mixerProgram, n)]))
    }
  };

  // Adaptive render resolution: the decks and the mixer render at a fraction of the canvas and the CRT pass
  // scales the picture up. Internal size is capped at 1080p worth of pixels (a 4K screen gets 1080p scaled
  // up), and steps down while frames take longer than ~22 ms (the GPU cannot keep up), back up when there
  // is headroom. A step that turns out too slow is not tried again, so the size never oscillates.
  const SCALES = [1.0, 0.85, 0.7, 0.55, 0.4];
  const MAX_PIXELS = 1920 * 1080;
  const adapt = { idx: 0, ceiling: 0, ema: 1 / 60, slow: 0, fast: 0, lastChange: 0, wentUp: false };
  function renderSize(w, h) {
    const cap = Math.min(1, Math.sqrt(MAX_PIXELS / (w * h)));
    const k = cap * SCALES[adapt.idx];
    return [Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k))];
  }
  function adaptScale(dt, now) {
    adapt.ema += (dt - adapt.ema) * 0.05;
    if (now - adapt.lastChange < 3000) return false;
    adapt.slow = adapt.ema > 1 / 45 ? adapt.slow + dt : 0;
    adapt.fast = adapt.ema < 1 / 57 ? adapt.fast + dt : 0;
    if (adapt.slow > 2 && adapt.idx < SCALES.length - 1) {
      if (adapt.wentUp) adapt.ceiling = adapt.idx + 1; // the last step up was one too many
      adapt.idx++;
      adapt.wentUp = false;
    } else if (adapt.fast > 15 && adapt.idx > adapt.ceiling) {
      adapt.idx--;
      adapt.wentUp = true;
    } else {
      return false;
    }
    adapt.lastChange = now;
    adapt.slow = adapt.fast = 0;
    return true;
  }
  // Shader prewarm for looks the autopilot has queued (Library.get starts the fetch and compile).
  setPrewarm((deck, transition) => {
    if (deck?.source && deck.source !== 'mandala') ctx.library.get('sources', deck.source.toLowerCase());
    for (const slot of deck?.fx ?? []) if (slot?.id) ctx.library.get('filters', slot.id);
    if (transition?.transition) ctx.library.get('transitions', transition.transition);
  });
  // GPU probe, once, at the first power-on: render the heaviest source a few times with gl.finish() and let
  // the heavy sources (settings.json heavySources) into the rotation only if it is fast enough. Integrated
  // GPUs and phones keep the light looks; discrete GPUs, Apple Silicon and fast integrated chips get all.
  // ?heavy=1 / ?heavy=0 overrides it. The result is window.LSD.gpu.
  const gpu = { state: 'pending', ms: null, heavy: false };
  const heavyParam = new URLSearchParams(location.search).get('heavy');
  if (heavyParam !== null) {
    gpu.state = 'override';
    gpu.heavy = heavyParam === '1';
    setHeavyAllowed(gpu.heavy);
  } else if (autopilotSettings.gpuProbe) {
    ctx.library.get('sources', autopilotSettings.gpuProbe.source); // fetch + compile now, ready by power-on
  }
  let probeWaitedSince = null;
  function probeGpu(now) {
    const probe = autopilotSettings.gpuProbe;
    if (gpu.state !== 'pending' || !probe) return;
    if (!ctx.library.get('sources', probe.source)) {
      // Still compiling; if it never becomes ready (failed), stay with the light looks.
      probeWaitedSince ??= now;
      if (now - probeWaitedSince > 8000 || ctx.library.failed.has(`sources/${probe.source}`)) gpu.state = 'failed';
      return;
    }
    const pipe = new DeckPipeline(ctx, probe.width, probe.height);
    const deck = { source: probe.source, params: {} };
    const frame = (i) => ({ time: 10 + i / 60, dt: 1 / 60, index: i });
    pipe.renderSource(deck, frame(0)); pipe.renderSource(deck, frame(1)); // warm up
    gl.finish();
    // Up to 4 timed frames; a slow GPU is obvious after one, so it stops there (the probe stays ~100 ms).
    let total = 0, n = 0;
    while (n < 4 && !(n > 0 && total / n > 2 * probe.maxMs)) {
      const t0 = performance.now();
      pipe.renderSource(deck, frame(2 + n));
      gl.finish();
      total += performance.now() - t0;
      n++;
    }
    gpu.ms = total / n;
    gpu.heavy = gpu.ms <= probe.maxMs;
    gpu.state = 'done';
    pipe.dispose();
    if (viewerState.heavyMode === 'auto') setHeavyAllowed(gpu.heavy); // the MENU's ON / OFF wins
    console.log(`[lsd] GPU probe: ${probe.source} at ${probe.width}x${probe.height} in ${gpu.ms.toFixed(1)} ms; heavy sources ${gpu.heavy ? 'on' : 'off'}`);
  }
  let [renderW, renderH] = renderSize(curWidth, curHeight);
  const pipeA  = new DeckPipeline(ctx, renderW, renderH);
  const pipeB  = new DeckPipeline(ctx, renderW, renderH);
  const pipeBG = new DeckPipeline(ctx, renderW, renderH);
  const mixerPipe = new MixerPipeline(ctx, renderW, renderH);

  window.LSD = { cvState, autopilotState, powerState, viewerState, library: ctx.library, handleRelayMessage, advanceForeground,
    renderSize: () => [renderW, renderH, SCALES[adapt.idx]], gpu };

  function frameInfo() {
    return { time: elapsedTime, dt: frameDt, index: frameCount };
  }

  // Draws a deck's source (ISF via the graph, or mandala here) then runs its FX chain.
  // Returns the deck's final texture.
  function renderDeck(deckData, pipe) {
    if (!deckData || deckData.empty) {
      if (!pipe.blank) { pipe.clean.clear(); pipe.blank = true; }
      return pipe.clean.tex;
    }
    pipe.blank = false;
    const srcType = (deckData.source || 'mandala').toLowerCase();
    if (srcType === 'mandala') renderMandala(deckData, pipe.clean);
    else if (!ctx.library.has('sources', srcType)) pipe.clean.clear(); // a source the web cannot draw
    else if (!pipe.renderSource(deckData, frameInfo())) return null; // still loading
    return pipe.renderFx(deckData, frameInfo());
  }

  // Clamp ranges of Mandala's parameters (library/sources/mandala/meta.json); ADD modulation
  // scales by the range, so the evaluator needs them.
  const MANDALA_SPECS = {
    Lobes: { min: 3, max: 26, steps: null },
  };
  const UNIT_SPEC = { min: 0, max: 1, steps: null };
  const mandalaRecipes = new MandalaRecipes(mandalaTable);

  function renderMandala(deckData, target) {
    target.bind();
    gl.disable(gl.BLEND);
    gl.clearColor(0.0, 0.0, 0.0, 0.0);
    gl.clear(gl.COLOR_BUFFER_BIT);

    gl.useProgram(mandalaProgram);
    gl.bindVertexArray(mandalaVAO);
    // Live broadcast: the desktop sends the shader-ready uniforms. Autopilot: derive them from
    // the preset's parameters exactly as Mandala.kt does (recipe table, normalised arms).
    const bag = deckData.params ?? {};
    const m = deckData.mandala
      ?? mandalaRecipes.uniforms((name, fallback) => evalP(bag[name], fallback, MANDALA_SPECS[name] ?? UNIT_SPEC));
    const locs = mandalaLocs;
    for (const name of ['uL1', 'uL2', 'uL3', 'uL4', 'uA', 'uB', 'uC', 'uD',
      'uThickness', 'uHueOffset', 'uHueSweep', 'uDepth', 'uMaxR']) {
      gl.uniform1f(locs[name], m[name] ?? 0);
    }
    gl.uniform1f(locs.uAspectRatio, curWidth / curHeight);
    gl.uniform1f(locs.uZoom,        evalP(deckData.viewZoom, 1.0));
    gl.uniform1f(locs.uRotateZ,     evalP(deckData.viewRotateZ, 0.0));
    gl.uniform1f(locs.uAlpha,       evalP(deckData.globalAlpha, 1.0));
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, MANDALA_POINTS * 2);
  }

  let noSignal = 0;
  let paused = false;       // embed mode: the host hid the picture
  let forceResize = false;
  function render(now) {
    if (paused) return;
    const dt = Math.min((now - lastTime) / 1000, 0.1);
    lastTime = now;
    elapsedTime += dt;
    frameDt = dt;
    frameCount++;
    if (frameCount === 3) window.LSDHost?.ready?.();   // embed: the host app's WebView bridge (Spaz Radio)

    // Tick DSP analysis and autopilot scheduler
    tick(dt);
    updateEvalContext();
    tickAutopilot(dt, evalCtx.beats, { section: cvState.section, drops: cvState.drops, bpm: cvState.bpm, locked: cvState.beatLocked });

    if (powerState.on) {
      probeGpu(now);
      if (powerState.warmupProgress < 1.0) {
        powerState.warmupProgress = Math.min(1.0, powerState.warmupProgress + dt / 1.5);
      }
    } else {
      if (powerState.shutdownProgress < 1.0) {
        powerState.shutdownProgress = Math.min(1.0, powerState.shutdownProgress + dt / 0.85);
        if (powerState.shutdownProgress >= 1.0) {
          const tvBody = document.getElementById('tv-body');
          if (tvBody) {
            tvBody.classList.remove('shutting-down');
          }
        }
      }
    }

    const displayW = canvas.clientWidth  || window.innerWidth;
    const displayH = canvas.clientHeight || window.innerHeight;
    const targetW  = Math.max(1, Math.floor(displayW * (window.devicePixelRatio || 1)));
    const targetH  = Math.max(1, Math.floor(displayH * (window.devicePixelRatio || 1)));
    const rescaled = powerState.on && adaptScale(dt, now);
    if (canvas.width !== targetW || canvas.height !== targetH || rescaled || forceResize) {
      forceResize = false;
      canvas.width  = targetW;
      canvas.height = targetH;
      curWidth  = targetW;
      curHeight = targetH;
      [renderW, renderH] = renderSize(curWidth, curHeight);
      pipeA.resize(renderW, renderH);
      pipeB.resize(renderW, renderH);
      pipeBG.resize(renderW, renderH);
      mixerPipe.resize(renderW, renderH);
    }

    gl.disable(gl.BLEND);
    gl.disable(gl.DEPTH_TEST);

    let finalTex = null;
    if (powerState.on || powerState.warmupProgress > 0 || (powerState.shutdownProgress > 0.0 && powerState.shutdownProgress < 0.42)) {
      const texA = renderDeck(autopilotState.deckA, pipeA) ?? pipeA.clean.tex;
      const texB = renderDeck(autopilotState.deckB, pipeB) ?? pipeB.clean.tex;
      const texBG = renderDeck(autopilotState.deckBG, pipeBG);
      const mix = autopilotState.mixer || {};
      finalTex = mixerPipe.render(
        mix, texA, texB, texBG ?? pipeBG.clean.tex,
        (autopilotState.deckBG && !autopilotState.deckBG.empty) ? (autopilotState.bgAlpha ?? 1.0) : 0.0,
        (mix.alpha ?? 1.0) * autopilotState.masterAlpha,
        frameInfo());
    }

    // 5. CRT Post-Processing -> Canvas Screen
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    gl.viewport(0, 0, curWidth, curHeight);
    gl.useProgram(crtProgram);
    gl.bindVertexArray(quadVAO);

    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, finalTex ?? mixerPipe.composite.tex);
    gl.uniform1i(crtUniforms.uTexture, 0);

    gl.uniform2f(crtUniforms.uResolution,          curWidth, curHeight);
    gl.uniform1f(crtUniforms.uTime,                elapsedTime);
    gl.uniform1f(crtUniforms.uPowerOn,             powerState.on ? 1.0 : 0.0);
    gl.uniform1f(crtUniforms.uWarmupProgress,      powerState.warmupProgress);
    gl.uniform1f(crtUniforms.uShutdownProgress,    powerState.shutdownProgress);
    // Snow fades in over ~1 s when the stream drops (not during a live broadcast: the picture comes from the desktop).
    noSignal += ((cvState.noSignal && !autopilotState.isLiveBroadcast ? 1 : 0) - noSignal) * Math.min(1, dt * 2);
    gl.uniform1f(crtUniforms.uNoSignal,            noSignal);

    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);

    requestAnimationFrame(render);
  }

  requestAnimationFrame(render);

  installEmbedApi({
    pause() { if (paused) return; paused = true; suspendAudio(); },
    resume() {
      if (!paused) return;
      paused = false;
      lastTime = performance.now();
      resumeAudio();
      requestAnimationFrame(render);
    },
    // Quality floor: the render scale never goes finer than SCALES[index]'s step (0 = adaptive over all steps).
    setQuality(index) {
      adapt.ceiling = index;
      if (adapt.idx < index) { adapt.idx = index; forceResize = true; }
    },
  });
}

window.addEventListener('DOMContentLoaded', () => {
  init().catch((err) => {
    console.error('Liquid LSD WebGL2 init error:', err);
    window.LSDHost?.failed?.(String(err));   // embed: the host falls back to its own visuals
  });
});
