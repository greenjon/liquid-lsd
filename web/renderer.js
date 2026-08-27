import { cvState, tick } from './dsp.js';
import { powerState } from './ui.js';
import { autopilotState, tickAutopilot, startAutopilot } from './autopilot.js';

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

  // Load shaders (preset.json removed — autopilot.js loads it)
  const [
    blitVertSrc,
    blitFragSrc,
    mandalaVertSrc,
    mandalaFragSrc,
    spiralFragSrc,
    feedbackFragSrc,
    mixerFragSrc,
    crtFragSrc
  ] = await Promise.all([
    loadText('shaders/blit.vert'),
    loadText('shaders/blit.frag'),
    loadText('shaders/mandala.vert'),
    loadText('shaders/mandala.frag'),
    loadText('shaders/dynamic_spiral.frag'),
    loadText('shaders/feedback.frag'),
    loadText('shaders/mixer.frag'),
    loadText('shaders/crt_post.frag')
  ]);

  // Compile programs
  const mandalaProgram  = createProgram(gl, mandalaVertSrc, mandalaFragSrc);
  const spiralProgram   = createProgram(gl, blitVertSrc, spiralFragSrc);
  const feedbackProgram = createProgram(gl, blitVertSrc, feedbackFragSrc);
  const mixerProgram    = createProgram(gl, blitVertSrc, mixerFragSrc);
  const blitProgram     = createProgram(gl, blitVertSrc, blitFragSrc);
  const crtProgram      = createProgram(gl, blitVertSrc, crtFragSrc);

  // Uniform locations
  const mandalaUniforms = getUniformLocations(gl, mandalaProgram, [
    'uL1', 'uL2', 'uL3', 'uL4',
    'uA', 'uB', 'uC', 'uD',
    'u3DMode', 'uSphereWrapX', 'uSphereWrapY', 'uMirrorGroup',
    'uPermuteXY', 'uPermuteYZ', 'uPermuteZX', 'uMaxR',
    'uYaw', 'uPitch', 'uPersp',
    'uThickness', 'uGlobalScale', 'uGlobalRotation', 'uAspectRatio',
    'uHueOffset', 'uHueSweep', 'uAlpha', 'uDepth'
  ]);

  const spiralUniforms = getUniformLocations(gl, spiralProgram, [
    'uResolution', 'uTime', 'uAlpha', 'src',
    'uMaxPoints', 'uScale', 'uDamping', 'uWaveFreq', 'uWaveAmp',
    'uShear', 'uSpeed', 'uDotSize', 'uGlow',
    'uHueOffset', 'uHueSweep', 'uTrailDecay',
    'uIntegratedTime', 'uIntegratedShear'
  ]);

  const feedbackUniforms = getUniformLocations(gl, feedbackProgram, [
    'uTextureLive', 'uTextureHistory',
    'uDecay', 'uGain', 'uZoom', 'uRotate',
    'uHueShift', 'uBlur', 'uChroma',
    'uFeedbackMode', 'uKaleido'
  ]);

  const mixerUniforms = getUniformLocations(gl, mixerProgram, [
    'uTex1', 'uTex2', 'uTexBG',
    'uMode', 'uBalance', 'uAlpha', 'uBloom'
  ]);

  const blitUniforms = getUniformLocations(gl, blitProgram, ['uTexture']);

  const crtUniforms = getUniformLocations(gl, crtProgram, [
    'uTexture', 'uResolution', 'uTime',
    'uPowerOn', 'uWarmupProgress',
    'uBarrelStrength', 'uScanlineStrength', 'uShadowMaskStrength',
    'uVignetteStrength', 'uChromaticAberration'
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

  const deckA    = createDeck(gl, curWidth, curHeight, internalFormat, format, type);
  const deckB    = createDeck(gl, curWidth, curHeight, internalFormat, format, type);
  const deckBG   = createDeck(gl, curWidth, curHeight, internalFormat, format, type);
  const masterFBO = createSingleFBO(gl, curWidth, curHeight, internalFormat, format, type);

  deckA.clear(0, 0, 0, 0);
  deckB.clear(0, 0, 0, 0);
  deckBG.clear(0, 0, 0, 1);
  masterFBO.clear(0, 0, 0, 1);

  // Start autopilot — loads autopilot.json and connects to relay
  await startAutopilot();

  // Expose debug handle
  window.LSD = { cvState, autopilotState, powerState };

  // Timing state
  let lastTime       = performance.now();
  let elapsedTime    = 0;
  let integratedTime = 0;
  let integratedShear = 0;

  function render(now) {
    const dt = Math.min((now - lastTime) / 1000, 0.1);
    lastTime = now;
    elapsedTime += dt;

    // Tick DSP analysis and autopilot scheduler
    tick(dt);
    tickAutopilot(dt);

    // Advance CRT warmup animation (1.5 second duration)
    if (powerState.on && powerState.warmupProgress < 1.0) {
      powerState.warmupProgress = Math.min(1.0, powerState.warmupProgress + dt / 1.5);
    }

    // Resize if canvas dimensions changed
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
      masterFBO.resize(curWidth, curHeight);
    }

    const aspectRatio = curWidth / curHeight;

    // Active preset — null-safe; autopilot may not have loaded yet
    const p = autopilotState.activePreset;

    // Update dynamic spiral integrated timing
    const speed = p ? (p.deckB?.speed ?? 0.5) : 0;
    const shear = p ? (p.deckB?.shear ?? 0.1) : 0;
    integratedTime  += dt * speed;
    integratedShear += dt * speed * shear;

    gl.disable(gl.BLEND);
    gl.disable(gl.DEPTH_TEST);

    // Skip render passes until TV is on and preset is ready
    if (p && (powerState.on || powerState.warmupProgress > 0)) {

      // ==========================================
      // Pass 1A: Render Mandala Source -> deckA.cleanFBO
      // ==========================================
      gl.bindFramebuffer(gl.FRAMEBUFFER, deckA.cleanFBO);
      gl.viewport(0, 0, curWidth, curHeight);
      gl.clearColor(0.0, 0.0, 0.0, 0.0);
      gl.clear(gl.COLOR_BUFFER_BIT);

      gl.useProgram(mandalaProgram);
      gl.bindVertexArray(mandalaVAO);

      const dA = p.deckA || {};
      gl.uniform1f(mandalaUniforms.uL1, dA.L1 ?? 0.4);
      gl.uniform1f(mandalaUniforms.uL2, dA.L2 ?? 0.3);
      gl.uniform1f(mandalaUniforms.uL3, dA.L3 ?? 0.2);
      gl.uniform1f(mandalaUniforms.uL4, dA.L4 ?? 0.1);
      gl.uniform1f(mandalaUniforms.uA,  dA.A  ?? 3.0);
      gl.uniform1f(mandalaUniforms.uB,  dA.B  ?? 4.0);
      gl.uniform1f(mandalaUniforms.uC,  dA.C  ?? 5.0);
      gl.uniform1f(mandalaUniforms.uD,  dA.D  ?? 7.0);

      gl.uniform1f(mandalaUniforms.u3DMode,      0.0);
      gl.uniform1f(mandalaUniforms.uSphereWrapX, 1.0);
      gl.uniform1f(mandalaUniforms.uSphereWrapY, 1.0);
      gl.uniform1f(mandalaUniforms.uMirrorGroup, 0.0);
      gl.uniform1f(mandalaUniforms.uPermuteXY,   1.0);
      gl.uniform1f(mandalaUniforms.uPermuteYZ,   1.0);
      gl.uniform1f(mandalaUniforms.uPermuteZX,   1.0);
      gl.uniform1f(mandalaUniforms.uMaxR,         dA.maxR ?? 0.85);
      gl.uniform1f(mandalaUniforms.uYaw,          0.0);
      gl.uniform1f(mandalaUniforms.uPitch,        0.0);
      gl.uniform1f(mandalaUniforms.uPersp,        0.5);

      gl.uniform1f(mandalaUniforms.uThickness,      dA.thickness ?? 0.012);
      gl.uniform1f(mandalaUniforms.uGlobalScale,    dA.zoom      ?? 0.8);
      gl.uniform1f(mandalaUniforms.uGlobalRotation, (dA.rotateZ ?? 0.0) + cvState.trigger_onset * 0.05);
      gl.uniform1f(mandalaUniforms.uAspectRatio,    aspectRatio);
      gl.uniform1f(mandalaUniforms.uHueOffset,      (dA.hueOffset ?? 0.0) + cvState.beatPhase * 0.1);
      gl.uniform1f(mandalaUniforms.uHueSweep,       dA.hueSweep ?? 0.3);
      gl.uniform1f(mandalaUniforms.uAlpha,          1.0);
      gl.uniform1f(mandalaUniforms.uDepth,          dA.depth ?? 0.35);

      gl.drawArrays(gl.TRIANGLE_STRIP, 0, MANDALA_POINTS * 2);

      // ==========================================
      // Pass 1B: Mandala Feedback -> deckA.writeFBO
      // ==========================================
      gl.bindFramebuffer(gl.FRAMEBUFFER, deckA.writeFBO);
      gl.viewport(0, 0, curWidth, curHeight);
      gl.useProgram(feedbackProgram);
      gl.bindVertexArray(quadVAO);

      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, deckA.cleanTex);
      gl.uniform1i(feedbackUniforms.uTextureLive, 0);

      gl.activeTexture(gl.TEXTURE1);
      gl.bindTexture(gl.TEXTURE_2D, deckA.readTex);
      gl.uniform1i(feedbackUniforms.uTextureHistory, 1);

      const fbA = dA.feedback || {};
      gl.uniform1f(feedbackUniforms.uDecay,        fbA.decay    ?? 0.04);
      gl.uniform1f(feedbackUniforms.uGain,         (fbA.gain    ?? 0.96) + cvState.audio_amp  * 0.03);
      gl.uniform1f(feedbackUniforms.uZoom,         (fbA.zoom    ?? 0.005) + cvState.audio_bass * 0.015);
      gl.uniform1f(feedbackUniforms.uRotate,       (fbA.rotate  ?? 0.008) + cvState.beatSine  * 0.003);
      gl.uniform1f(feedbackUniforms.uHueShift,     (fbA.hueShift ?? 0.001) + cvState.audio_high * 0.004);
      gl.uniform1f(feedbackUniforms.uBlur,         fbA.blur     ?? 0.0);
      gl.uniform1f(feedbackUniforms.uChroma,       fbA.chroma   ?? 0.0);
      gl.uniform1f(feedbackUniforms.uFeedbackMode, fbA.mode     ?? 0.0);
      gl.uniform1f(feedbackUniforms.uKaleido,      fbA.kaleido  ?? 1.0);

      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      deckA.swap();

      // ==========================================
      // Pass 2A: Dynamic Spiral Source -> deckB.cleanFBO
      // ==========================================
      gl.bindFramebuffer(gl.FRAMEBUFFER, deckB.cleanFBO);
      gl.viewport(0, 0, curWidth, curHeight);
      gl.clearColor(0.0, 0.0, 0.0, 0.0);
      gl.clear(gl.COLOR_BUFFER_BIT);

      gl.useProgram(spiralProgram);
      gl.bindVertexArray(quadVAO);

      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, deckB.readTex);
      gl.uniform1i(spiralUniforms.src, 0);

      const dB = p.deckB || {};
      gl.uniform2f(spiralUniforms.uResolution, curWidth, curHeight);
      gl.uniform1f(spiralUniforms.uTime,        now * 0.001);
      gl.uniform1f(spiralUniforms.uAlpha,       1.0);
      gl.uniform1f(spiralUniforms.uMaxPoints,   dB.maxPoints  ?? 500.0);
      gl.uniform1f(spiralUniforms.uScale,       dB.scale      ?? 0.5);
      gl.uniform1f(spiralUniforms.uDamping,     dB.damping    ?? 100.0);
      gl.uniform1f(spiralUniforms.uWaveFreq,    dB.waveFreq   ?? 0.2);
      gl.uniform1f(spiralUniforms.uWaveAmp,     dB.waveAmp    ?? 0.0);
      gl.uniform1f(spiralUniforms.uShear,       dB.shear      ?? 0.1);
      gl.uniform1f(spiralUniforms.uSpeed,       dB.speed      ?? 0.5);
      gl.uniform1f(spiralUniforms.uDotSize,     dB.dotSize    ?? 0.01);
      gl.uniform1f(spiralUniforms.uGlow,        (dB.glow      ?? 1.5)  + cvState.audio_amp  * 2.0);
      gl.uniform1f(spiralUniforms.uHueOffset,   (dB.hueOffset ?? 0.33) + cvState.beatPhase  * 0.15);
      gl.uniform1f(spiralUniforms.uHueSweep,    dB.hueSweep   ?? 0.01);
      gl.uniform1f(spiralUniforms.uTrailDecay,  dB.trailDecay ?? 0.85);
      gl.uniform1f(spiralUniforms.uIntegratedTime,  integratedTime);
      gl.uniform1f(spiralUniforms.uIntegratedShear, integratedShear);

      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);

      // ==========================================
      // Pass 2B: Spiral Feedback -> deckB.writeFBO
      // ==========================================
      gl.bindFramebuffer(gl.FRAMEBUFFER, deckB.writeFBO);
      gl.viewport(0, 0, curWidth, curHeight);
      gl.useProgram(feedbackProgram);
      gl.bindVertexArray(quadVAO);

      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, deckB.cleanTex);
      gl.uniform1i(feedbackUniforms.uTextureLive, 0);

      gl.activeTexture(gl.TEXTURE1);
      gl.bindTexture(gl.TEXTURE_2D, deckB.readTex);
      gl.uniform1i(feedbackUniforms.uTextureHistory, 1);

      const fbB = dB.feedback || {};
      gl.uniform1f(feedbackUniforms.uDecay,        fbB.decay    ?? 0.03);
      gl.uniform1f(feedbackUniforms.uGain,         fbB.gain     ?? 0.97);
      gl.uniform1f(feedbackUniforms.uZoom,         (fbB.zoom    ?? 0.003) + cvState.audio_mid  * 0.01);
      gl.uniform1f(feedbackUniforms.uRotate,       (fbB.rotate  ?? -0.005) - cvState.audio_bass * 0.008);
      gl.uniform1f(feedbackUniforms.uHueShift,     (fbB.hueShift ?? 0.0) + cvState.audio_amp  * 0.003);
      gl.uniform1f(feedbackUniforms.uBlur,         fbB.blur     ?? 0.0);
      gl.uniform1f(feedbackUniforms.uChroma,       fbB.chroma   ?? 0.0);
      gl.uniform1f(feedbackUniforms.uFeedbackMode, fbB.mode     ?? 0.0);
      gl.uniform1f(feedbackUniforms.uKaleido,      fbB.kaleido  ?? 1.0);

      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      deckB.swap();

      // ==========================================
      // Pass 3: Deck BG (clear to black)
      // ==========================================
      gl.bindFramebuffer(gl.FRAMEBUFFER, deckBG.writeFBO);
      gl.viewport(0, 0, curWidth, curHeight);
      gl.clearColor(0.0, 0.0, 0.0, 1.0);
      gl.clear(gl.COLOR_BUFFER_BIT);
      deckBG.swap();

      // ==========================================
      // Pass 4: Mixer -> masterFBO
      // ==========================================
      gl.bindFramebuffer(gl.FRAMEBUFFER, masterFBO.fbo);
      gl.viewport(0, 0, curWidth, curHeight);
      gl.useProgram(mixerProgram);
      gl.bindVertexArray(quadVAO);

      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, deckA.readTex);
      gl.uniform1i(mixerUniforms.uTex1, 0);

      gl.activeTexture(gl.TEXTURE1);
      gl.bindTexture(gl.TEXTURE_2D, deckB.readTex);
      gl.uniform1i(mixerUniforms.uTex2, 1);

      gl.activeTexture(gl.TEXTURE2);
      gl.bindTexture(gl.TEXTURE_2D, deckBG.readTex);
      gl.uniform1i(mixerUniforms.uTexBG, 2);

      const mix = p.mixer || {};
      gl.uniform1i(mixerUniforms.uMode,    mix.mode    ?? 0);
      gl.uniform1f(mixerUniforms.uBalance, mix.balance ?? 0.5);
      // masterAlpha: autopilot fade-through-black × preset alpha
      gl.uniform1f(mixerUniforms.uAlpha,   (mix.alpha  ?? 1.0) * autopilotState.masterAlpha);
      gl.uniform1f(mixerUniforms.uBloom,   mix.bloom   ?? 0.0);

      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }

    // ==========================================
    // Pass 5: CRT Post-Processing -> Screen
    // ==========================================
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
    gl.uniform1f(crtUniforms.uBarrelStrength,      0.12);
    gl.uniform1f(crtUniforms.uScanlineStrength,    0.25);
    gl.uniform1f(crtUniforms.uShadowMaskStrength,  0.25);
    gl.uniform1f(crtUniforms.uVignetteStrength,    0.7);
    gl.uniform1f(crtUniforms.uChromaticAberration, 0.003);

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
