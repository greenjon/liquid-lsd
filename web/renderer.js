import { cvState, tick } from './dsp.js';
import { powerState } from './ui.js';
import { autopilotState, tickAutopilot, startAutopilot } from './autopilot.js';
import { evaluateParameter, makeEvalContext, paramSpec } from './evaluator.js';
import { lookupParam } from './isf.js';
import { Library, DeckPipeline, MixerPipeline } from './graph.js';

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
    catalog
  ] = await Promise.all([
    loadText('shaders/blit.vert'),
    loadText('shaders/blit.frag'),
    loadText('shaders/mandala.vert'),
    loadText('shaders/mandala.frag'),
    loadText('shaders/mixer.frag'),
    loadText('shaders/crt_post.frag'),
    loadText('catalog.json').then(JSON.parse)
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
    'uPowerOn', 'uWarmupProgress', 'uShutdownProgress'
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
  function updateEvalContext() {
    const beats = cvState.bpm * (elapsedTime / 60.0);
    evalCtx.time = elapsedTime;
    evalCtx.beats = beats;
    evalCtx.frame = frameCount;
    evalCtx.dt = frameDt;
    evalCtx.bpm = cvState.bpm;
    // audio_flux_* is the bass-only onset trigger until phase 4 adds per-band flux
    const onset = cvState.trigger_onset;
    evalCtx.cv = {
      audio_amp: cvState.audio_amp, audio_bass: cvState.audio_bass,
      audio_mid: cvState.audio_mid, audio_high: cvState.audio_high,
      audio_flux_amp: cvState.audio_flux_amp ?? onset, audio_flux_bass: cvState.audio_flux_bass ?? onset,
      audio_flux_mid: cvState.audio_flux_mid ?? onset, audio_flux_high: cvState.audio_flux_high ?? onset,
      beatSine: Math.sin(beats * 2 * Math.PI), bpm: cvState.bpm,
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

  const pipeA  = new DeckPipeline(ctx, curWidth, curHeight);
  const pipeB  = new DeckPipeline(ctx, curWidth, curHeight);
  const pipeBG = new DeckPipeline(ctx, curWidth, curHeight);
  const mixerPipe = new MixerPipeline(ctx, curWidth, curHeight);

  window.LSD = { cvState, autopilotState, powerState, library: ctx.library };

  function frameInfo() {
    return { time: elapsedTime, dt: frameDt, index: frameCount };
  }

  // Draws a deck's source (ISF via the graph, or mandala here) then runs its FX chain.
  // Returns the deck's final texture.
  function renderDeck(deckData, pipe) {
    if (!deckData) return null;
    const srcType = (deckData.source || 'mandala').toLowerCase();
    if (!ctx.library.has('sources', srcType)) renderMandala(deckData, pipe.clean);
    else if (!pipe.renderSource(deckData, frameInfo())) return null; // still loading
    return pipe.renderFx(deckData, frameInfo());
  }

  function renderMandala(deckData, target) {
    target.bind();
    gl.disable(gl.BLEND);
    gl.clearColor(0.0, 0.0, 0.0, 0.0);
    gl.clear(gl.COLOR_BUFFER_BIT);

    const locs = mandalaLocs;
    gl.useProgram(mandalaProgram);
    gl.bindVertexArray(mandalaVAO);
    const rawL1 = evalP(deckData.L1 ?? deckData.l1, 0.4);
    const rawL2 = evalP(deckData.L2 ?? deckData.l2, 0.3);
    const rawL3 = evalP(deckData.L3 ?? deckData.l3, 0.2);
    const rawL4 = evalP(deckData.L4 ?? deckData.l4, 0.1);
    const sumL = Math.abs(rawL1) + Math.abs(rawL2) + Math.abs(rawL3) + Math.abs(rawL4);
    const targetRadius = 2.0;
    const normScale = sumL > 1e-5 ? (targetRadius / sumL) : 0.0;

    gl.uniform1f(locs.uL1, rawL1 * normScale);
    gl.uniform1f(locs.uL2, rawL2 * normScale);
    gl.uniform1f(locs.uL3, rawL3 * normScale);
    gl.uniform1f(locs.uL4, rawL4 * normScale);
    gl.uniform1f(locs.uA,  evalP(deckData.A ?? deckData.a ?? deckData.recipe?.a, 3.0));
    gl.uniform1f(locs.uB,  evalP(deckData.B ?? deckData.b ?? deckData.recipe?.b, 4.0));
    gl.uniform1f(locs.uC,  evalP(deckData.C ?? deckData.c ?? deckData.recipe?.c, 5.0));
    gl.uniform1f(locs.uD,  evalP(deckData.D ?? deckData.d ?? deckData.recipe?.d, 7.0));

    gl.uniform1f(locs.uMaxR, sumL > 1e-5 ? targetRadius : 0.001);
    gl.uniform1f(locs.uThickness,      evalP(deckData.Thickness ?? deckData.thickness, 0.012));
    gl.uniform1f(locs.uAspectRatio,    curWidth / curHeight);
    gl.uniform1f(locs.uZoom,           evalP(deckData.viewZoom, 1.0));
    gl.uniform1f(locs.uRotateZ,        evalP(deckData.viewRotateZ, 0.0));
    gl.uniform1f(locs.uHueOffset,      evalP(deckData['Hue Offset'] ?? deckData.hueOffset, 0.0));
    gl.uniform1f(locs.uHueSweep,       evalP(deckData['Hue Sweep'] ?? deckData.hueSweep, 0.3));
    gl.uniform1f(locs.uAlpha,          1.0);
    gl.uniform1f(locs.uDepth,          evalP(deckData.Depth ?? deckData.depth, 0.35));

    gl.drawArrays(gl.TRIANGLE_STRIP, 0, MANDALA_POINTS * 2);
  }

  function render(now) {
    const dt = Math.min((now - lastTime) / 1000, 0.1);
    lastTime = now;
    elapsedTime += dt;
    frameDt = dt;
    frameCount++;

    // Tick DSP analysis and autopilot scheduler
    tick(dt);
    updateEvalContext();
    tickAutopilot(dt);

    if (powerState.on) {
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
    if (canvas.width !== targetW || canvas.height !== targetH) {
      canvas.width  = targetW;
      canvas.height = targetH;
      curWidth  = targetW;
      curHeight = targetH;
      pipeA.resize(curWidth, curHeight);
      pipeB.resize(curWidth, curHeight);
      pipeBG.resize(curWidth, curHeight);
      mixerPipe.resize(curWidth, curHeight);
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
        autopilotState.deckBG ? (autopilotState.bgAlpha ?? 1.0) : 0.0,
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

    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);

    requestAnimationFrame(render);
  }

  requestAnimationFrame(render);
}

window.addEventListener('DOMContentLoaded', () => {
  init().catch((err) => {
    console.error('Liquid LSD WebGL2 init error:', err);
  });
});
