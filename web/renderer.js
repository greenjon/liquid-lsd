import { cvState, tick } from './dsp.js';
import { powerState } from './ui.js';
import { autopilotState, tickAutopilot, startAutopilot } from './autopilot.js';
import { evaluateParameter } from './evaluator.js';
import { buildFragmentShader, parseHeader, applyUniforms } from './isf.js';

// ISF sources rendered generically from their headers. Mandala is the one special case:
// it is a vertex-displaced ribbon driven by a recipe table, not an ISF shader.
const ISF_SOURCES = [
  'dynamic_spiral', 'icosa_h3', 'hyper_slice', 'gyroid_hyperspace',
  'chladni_cymatics', 'celestial_engine', 'domain_warp_fluid'
];

// Stand-in for the desktop's default transition (linear_crossfade, perceptual cosine curve).
const CROSSFADE_FRAG = `#version 300 es
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uTexA;
uniform sampler2D uTexB;
uniform float uProgress;
void main() {
  float t = 0.5 - 0.5 * cos(clamp(uProgress, 0.0, 1.0) * 3.14159265359);
  fragColor = mix(texture(uTexA, vTexCoord), texture(uTexB, vTexCoord), t);
}`;

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

function createTexture(gl, width, height, internalFormat, format, type) {
  const tex = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, tex);
  gl.texImage2D(gl.TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.bindTexture(gl.TEXTURE_2D, null);
  return tex;
}

function createFramebuffer(gl, texture) {
  const fbo = gl.createFramebuffer();
  gl.bindFramebuffer(gl.FRAMEBUFFER, fbo);
  gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, texture, 0);
  const status = gl.checkFramebufferStatus(gl.FRAMEBUFFER);
  if (status !== gl.FRAMEBUFFER_COMPLETE) {
    console.error(`Framebuffer incomplete status: 0x${status.toString(16)}`);
  }
  gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  return fbo;
}

function createDeck(gl, width, height, internalFormat, format, type) {
  const texA = createTexture(gl, width, height, internalFormat, format, type);
  const fboA = createFramebuffer(gl, texA);
  const texB = createTexture(gl, width, height, internalFormat, format, type);
  const fboB = createFramebuffer(gl, texB);
  const cleanTex = createTexture(gl, width, height, internalFormat, format, type);
  const cleanFBO = createFramebuffer(gl, cleanTex);

  return {
    width,
    height,
    texA, fboA, texB, fboB, cleanTex, cleanFBO,
    readTex: texA, readFBO: fboA,
    writeTex: texB, writeFBO: fboB,
    swap() {
      const tTex = this.readTex; const tFbo = this.readFBO;
      this.readTex = this.writeTex; this.readFBO = this.writeFBO;
      this.writeTex = tTex; this.writeFBO = tFbo;
    },
    resize(newW, newH) {
      this.width = newW; this.height = newH;
      for (const tex of [this.texA, this.texB, this.cleanTex]) {
        gl.bindTexture(gl.TEXTURE_2D, tex);
        gl.texImage2D(gl.TEXTURE_2D, 0, internalFormat, newW, newH, 0, format, type, null);
      }
      gl.bindTexture(gl.TEXTURE_2D, null);
    },
    clear(r = 0, g = 0, b = 0, a = 0) {
      for (const fbo of [this.fboA, this.fboB, this.cleanFBO]) {
        gl.bindFramebuffer(gl.FRAMEBUFFER, fbo);
        gl.clearColor(r, g, b, a);
        gl.clear(gl.COLOR_BUFFER_BIT);
      }
      gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    }
  };
}

function createSingleFBO(gl, width, height, internalFormat, format, type) {
  const tex = createTexture(gl, width, height, internalFormat, format, type);
  const fbo = createFramebuffer(gl, tex);
  return {
    width, height, tex, fbo,
    resize(newW, newH) {
      this.width = newW; this.height = newH;
      gl.bindTexture(gl.TEXTURE_2D, this.tex);
      gl.texImage2D(gl.TEXTURE_2D, 0, internalFormat, newW, newH, 0, format, type, null);
      gl.bindTexture(gl.TEXTURE_2D, null);
    },
    clear(r = 0, g = 0, b = 0, a = 0) {
      gl.bindFramebuffer(gl.FRAMEBUFFER, this.fbo);
      gl.clearColor(r, g, b, a);
      gl.clear(gl.COLOR_BUFFER_BIT);
      gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    }
  };
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
  let internalFormat = gl.RGBA16F;
  let format = gl.RGBA;
  let type = gl.HALF_FLOAT;

  if (extFloat) {
    console.log('EXT_color_buffer_float supported: using RGBA16F render targets.');
  } else {
    console.warn('EXT_color_buffer_float not supported: falling back to RGBA8.');
    internalFormat = gl.RGBA8;
    type = gl.UNSIGNED_BYTE;
  }

  // Load all available shaders
  const [
    blitVertSrc,
    blitFragSrc,
    mandalaVertSrc,
    mandalaFragSrc,
    feedbackFragSrc,
    mixerFragSrc,
    crtFragSrc,
    ...isfSrcs
  ] = await Promise.all([
    loadText('shaders/blit.vert'),
    loadText('shaders/blit.frag'),
    loadText('shaders/mandala.vert'),
    loadText('shaders/mandala.frag'),
    loadText('shaders/feedback.frag'),
    loadText('shaders/mixer.frag'),
    loadText('shaders/crt_post.frag'),
    ...ISF_SOURCES.map((id) => loadText(`shaders/${id}.frag`))
  ]);

  // Compile programs
  const mandalaProgram     = createProgram(gl, mandalaVertSrc, mandalaFragSrc);
  const feedbackProgram    = createProgram(gl, blitVertSrc, feedbackFragSrc);
  const mixerProgram       = createProgram(gl, blitVertSrc, mixerFragSrc);
  const blitProgram        = createProgram(gl, blitVertSrc, blitFragSrc);
  const crtProgram         = createProgram(gl, blitVertSrc, crtFragSrc);
  const crossfadeProgram   = createProgram(gl, blitVertSrc, CROSSFADE_FRAG);

  // One program per ISF source. A source that fails to compile is skipped, not fatal.
  const isfPrograms = {};
  ISF_SOURCES.forEach((id, i) => {
    try {
      const header = parseHeader(isfSrcs[i]);
      const prog = createProgram(gl, blitVertSrc, buildFragmentShader(isfSrcs[i], header));
      isfPrograms[id] = { header, prog };
    } catch (err) {
      console.error(`ISF source ${id} unavailable:`, err);
    }
  });

  const mandalaLocs = getUniformLocations(gl, mandalaProgram, [
    'uL1', 'uL2', 'uL3', 'uL4', 'uA', 'uB', 'uC', 'uD',
    'uMaxR',
    'uYaw', 'uPitch', 'uPersp',
    'uThickness', 'uGlobalScale', 'uGlobalRotation', 'uAspectRatio',
    'uHueOffset', 'uHueSweep', 'uAlpha', 'uDepth'
  ]);

  const feedbackUniforms = getUniformLocations(gl, feedbackProgram, [
    'uTextureLive', 'uTextureHistory',
    'uDecay', 'uGain', 'uFbZoom', 'uRotate',
    'uHueShift', 'uBlur', 'uChroma',
    'uFeedbackMode', 'uKaleido'
  ]);

  const mixerUniforms = getUniformLocations(gl, mixerProgram, [
    'uTex1', 'uTexBG', 'uProgress', 'uBgAlpha',
    'uLevelA', 'uLevelB', 'uLevelBG', 'uMasterLevel'
  ]);

  const crossfadeUniforms = getUniformLocations(gl, crossfadeProgram, ['uTexA', 'uTexB', 'uProgress']);

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

  // Initial sizing & FBO creation
  const dpr = window.devicePixelRatio || 1;
  let curWidth  = Math.max(1, Math.floor(window.innerWidth  * dpr));
  let curHeight = Math.max(1, Math.floor(window.innerHeight * dpr));
  canvas.width  = curWidth;
  canvas.height = curHeight;

  const deckA     = createDeck(gl, curWidth, curHeight, internalFormat, format, type);
  const deckB     = createDeck(gl, curWidth, curHeight, internalFormat, format, type);
  const deckBG    = createDeck(gl, curWidth, curHeight, internalFormat, format, type);
  const blendFBO  = createSingleFBO(gl, curWidth, curHeight, internalFormat, format, type);
  const masterFBO = createSingleFBO(gl, curWidth, curHeight, internalFormat, format, type);

  deckA.clear(0, 0, 0, 0);
  deckB.clear(0, 0, 0, 0);
  deckBG.clear(0, 0, 0, 1);
  blendFBO.clear(0, 0, 0, 0);
  masterFBO.clear(0, 0, 0, 1);

  // Start dual-queue autopilot
  await startAutopilot();

  window.LSD = { cvState, autopilotState, powerState };

  let lastTime        = performance.now();
  let elapsedTime     = 0;
  let frameCount      = 0;
  let frameDt         = 0;

  function evalP(paramObj, fallback = 0.0) {
    return evaluateParameter(paramObj, elapsedTime, cvState.bpm * (elapsedTime / 60.0), frameCount, fallback);
  }

  // Looks an ISF input up in a deck's parameter bag. Presets key by NAME, LABEL, or camelCase
  // of either; `!== undefined` keeps an explicit 0 from falling through to the default.
  const camel = (k) => k.replace(/[\s-_]+([a-zA-Z0-9])/g, (_, c) => c.toUpperCase()).replace(/^[A-Z]/, (c) => c.toLowerCase());
  function lookupParam(deckData, input) {
    for (const k of [input.NAME, input.LABEL, camel(input.NAME), input.LABEL && camel(input.LABEL)]) {
      if (k && deckData[k] !== undefined) return deckData[k];
    }
    return undefined;
  }

  function renderVisualSource(deckData, targetFBO) {
    if (!deckData) return;
    const srcType = (deckData.source || 'mandala').toLowerCase();
    const isf = isfPrograms[srcType];

    gl.bindFramebuffer(gl.FRAMEBUFFER, targetFBO);
    gl.viewport(0, 0, curWidth, curHeight);
    gl.clearColor(0.0, 0.0, 0.0, 0.0);
    gl.clear(gl.COLOR_BUFFER_BIT);

    if (isf) {
      gl.useProgram(isf.prog);
      gl.bindVertexArray(quadVAO);
      const params = {};
      for (const input of isf.header.INPUTS) {
        const raw = lookupParam(deckData, input);
        let v = evalP(raw, input.DEFAULT ?? 0.0);
        if (typeof v === 'number') {
          if (input.MIN !== undefined) v = Math.max(input.MIN, v);
          if (input.MAX !== undefined) v = Math.min(input.MAX, v);
        }
        params[input.NAME] = v;
      }
      applyUniforms(gl, isf.prog, isf.header, params, {
        width: curWidth, height: curHeight, time: elapsedTime, dt: frameDt, index: frameCount, alpha: 1.0
      });
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      return;
    }

    // Mandala (also the fallback for unknown source ids)
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
    gl.uniform1f(locs.uHueOffset,      evalP(deckData['Hue Offset'] ?? deckData.hueOffset, 0.0));
    gl.uniform1f(locs.uHueSweep,       evalP(deckData['Hue Sweep'] ?? deckData.hueSweep, 0.3));
    gl.uniform1f(locs.uAlpha,          1.0);
    gl.uniform1f(locs.uDepth,          evalP(deckData.Depth ?? deckData.depth, 0.35));

    gl.drawArrays(gl.TRIANGLE_STRIP, 0, MANDALA_POINTS * 2);
  }

  function renderFeedbackPass(deck, deckData) {
    gl.bindFramebuffer(gl.FRAMEBUFFER, deck.writeFBO);
    gl.viewport(0, 0, curWidth, curHeight);
    gl.useProgram(feedbackProgram);
    gl.bindVertexArray(quadVAO);

    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, deck.cleanTex);
    gl.uniform1i(feedbackUniforms.uTextureLive, 0);

    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_2D, deck.readTex);
    gl.uniform1i(feedbackUniforms.uTextureHistory, 1);

    const fb = deckData?.feedback || {};
    gl.uniform1f(feedbackUniforms.uDecay,        evalP(fb.decay ?? fb.fbDecay, 0.04));
    gl.uniform1f(feedbackUniforms.uGain,         evalP(fb.gain ?? fb.fbGain, 0.96));
    gl.uniform1f(feedbackUniforms.uFbZoom,       evalP(fb.zoom ?? fb.fbZoom, 0.005));
    gl.uniform1f(feedbackUniforms.uRotate,       evalP(fb.rotate ?? fb.fbRotate, 0.008));
    gl.uniform1f(feedbackUniforms.uHueShift,     evalP(fb.hueShift ?? fb.fbHueShift, 0.001));
    gl.uniform1f(feedbackUniforms.uBlur,         evalP(fb.blur ?? fb.fbBlur, 0.0));
    gl.uniform1f(feedbackUniforms.uChroma,       evalP(fb.chroma ?? fb.fbChroma, 0.0));
    gl.uniform1f(feedbackUniforms.uFeedbackMode, evalP(fb.mode ?? fb.fbMode, 0.0));
    gl.uniform1f(feedbackUniforms.uKaleido,      evalP(fb.kaleido ?? fb.fbKaleido, 1.0));

    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    deck.swap();
  }

  function render(now) {
    const dt = Math.min((now - lastTime) / 1000, 0.1);
    lastTime = now;
    elapsedTime += dt;
    frameDt = dt;
    frameCount++;

    // Tick DSP analysis and autopilot scheduler
    tick(dt);
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
      deckA.resize(curWidth, curHeight);
      deckB.resize(curWidth, curHeight);
      deckBG.resize(curWidth, curHeight);
      blendFBO.resize(curWidth, curHeight);
      masterFBO.resize(curWidth, curHeight);
    }

    gl.disable(gl.BLEND);
    gl.disable(gl.DEPTH_TEST);

    if (powerState.on || powerState.warmupProgress > 0 || (powerState.shutdownProgress > 0.0 && powerState.shutdownProgress < 0.42)) {
      // 1. Render Deck A
      if (autopilotState.deckA) {
        renderVisualSource(autopilotState.deckA, deckA.cleanFBO);
        renderFeedbackPass(deckA, autopilotState.deckA);
      }

      // 2. Render Deck B
      if (autopilotState.deckB) {
        renderVisualSource(autopilotState.deckB, deckB.cleanFBO);
        renderFeedbackPass(deckB, autopilotState.deckB);
      }

      // 3. Render Deck BG
      if (autopilotState.deckBG) {
        renderVisualSource(autopilotState.deckBG, deckBG.cleanFBO);
        renderFeedbackPass(deckBG, autopilotState.deckBG);
      } else {
        gl.bindFramebuffer(gl.FRAMEBUFFER, deckBG.writeFBO);
        gl.viewport(0, 0, curWidth, curHeight);
        gl.clearColor(0.0, 0.0, 0.0, 1.0);
        gl.clear(gl.COLOR_BUFFER_BIT);
        deckBG.swap();
      }

      const mix = autopilotState.mixer || {};
      const progress = mix.balance ?? 0.0;

      // 4a. Transition A -> B into blendFBO
      gl.bindFramebuffer(gl.FRAMEBUFFER, blendFBO.fbo);
      gl.viewport(0, 0, curWidth, curHeight);
      gl.useProgram(crossfadeProgram);
      gl.bindVertexArray(quadVAO);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, deckA.readTex);
      gl.uniform1i(crossfadeUniforms.uTexA, 0);
      gl.activeTexture(gl.TEXTURE1);
      gl.bindTexture(gl.TEXTURE_2D, deckB.readTex);
      gl.uniform1i(crossfadeUniforms.uTexB, 1);
      gl.uniform1f(crossfadeUniforms.uProgress, progress);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);

      // 4b. Composite over BG -> masterFBO
      gl.bindFramebuffer(gl.FRAMEBUFFER, masterFBO.fbo);
      gl.viewport(0, 0, curWidth, curHeight);
      gl.useProgram(mixerProgram);
      gl.bindVertexArray(quadVAO);

      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, blendFBO.tex);
      gl.uniform1i(mixerUniforms.uTex1, 0);

      gl.activeTexture(gl.TEXTURE1);
      gl.bindTexture(gl.TEXTURE_2D, deckBG.readTex);
      gl.uniform1i(mixerUniforms.uTexBG, 1);

      gl.uniform1f(mixerUniforms.uProgress,    progress);
      gl.uniform1f(mixerUniforms.uBgAlpha,     autopilotState.bgAlpha ?? 1.0);
      gl.uniform1f(mixerUniforms.uLevelA,      1.0);
      gl.uniform1f(mixerUniforms.uLevelB,      1.0);
      gl.uniform1f(mixerUniforms.uLevelBG,     1.0);
      gl.uniform1f(mixerUniforms.uMasterLevel, (mix.alpha ?? 1.0) * autopilotState.masterAlpha);

      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }

    // 5. CRT Post-Processing -> Canvas Screen
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    gl.viewport(0, 0, curWidth, curHeight);
    gl.useProgram(crtProgram);
    gl.bindVertexArray(quadVAO);

    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, masterFBO.tex);
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
