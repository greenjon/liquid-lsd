import { cvState, tick } from './dsp.js';
import { powerState } from './ui.js';
import { autopilotState, tickAutopilot, startAutopilot } from './autopilot.js';
import { evaluateParameter } from './evaluator.js';
import { generateH3Normals } from './icosahedron_math.js';

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
    spiralFragSrc,
    attractorFragSrc,
    chladniFragSrc,
    gyroidFragSrc,
    hyperSliceFragSrc,
    icosahedronFragSrc,
    icosaDodecaFragSrc,
    icosaV3FragSrc,
    feedbackFragSrc,
    mixerFragSrc,
    crtFragSrc
  ] = await Promise.all([
    loadText('shaders/blit.vert'),
    loadText('shaders/blit.frag'),
    loadText('shaders/mandala.vert'),
    loadText('shaders/mandala.frag'),
    loadText('shaders/dynamic_spiral.frag'),
    loadText('shaders/attractor_feedback.frag'),
    loadText('shaders/chladni.frag'),
    loadText('shaders/gyroid.frag'),
    loadText('shaders/hyper_slice.frag'),
    loadText('shaders/icosahedron.frag'),
    loadText('shaders/icosa_dodeca.frag'),
    loadText('shaders/icosa_v3.frag'),
    loadText('shaders/feedback.frag'),
    loadText('shaders/mixer.frag'),
    loadText('shaders/crt_post.frag')
  ]);

  // Compile programs
  const mandalaProgram     = createProgram(gl, mandalaVertSrc, mandalaFragSrc);
  const spiralProgram      = createProgram(gl, blitVertSrc, spiralFragSrc);
  const attractorProgram   = createProgram(gl, blitVertSrc, attractorFragSrc);
  const chladniProgram     = createProgram(gl, blitVertSrc, chladniFragSrc);
  const gyroidProgram      = createProgram(gl, blitVertSrc, gyroidFragSrc);
  const hyperSliceProgram  = createProgram(gl, blitVertSrc, hyperSliceFragSrc);
  const icosahedronProgram = createProgram(gl, blitVertSrc, icosahedronFragSrc);
  const icosaDodecaProgram = createProgram(gl, blitVertSrc, icosaDodecaFragSrc);
  const icosaV3Program     = createProgram(gl, blitVertSrc, icosaV3FragSrc);
  const feedbackProgram    = createProgram(gl, blitVertSrc, feedbackFragSrc);
  const mixerProgram       = createProgram(gl, blitVertSrc, mixerFragSrc);
  const blitProgram        = createProgram(gl, blitVertSrc, blitFragSrc);
  const crtProgram         = createProgram(gl, blitVertSrc, crtFragSrc);

  // Uniform locations lookup tables
  const programs = {
    mandala: {
      prog: mandalaProgram,
      locs: getUniformLocations(gl, mandalaProgram, [
        'uL1', 'uL2', 'uL3', 'uL4', 'uA', 'uB', 'uC', 'uD',
        'uMaxR',
        'uYaw', 'uPitch', 'uPersp',
        'uThickness', 'uGlobalScale', 'uGlobalRotation', 'uAspectRatio',
        'uHueOffset', 'uHueSweep', 'uAlpha', 'uDepth'
      ])
    },
    dynamic_spiral: {
      prog: spiralProgram,
      locs: getUniformLocations(gl, spiralProgram, [
        'uResolution', 'uTime', 'uAlpha', 'src',
        'uMaxPoints', 'uScale', 'uDamping', 'uWaveFreq', 'uWaveAmp',
        'uShear', 'uSpeed', 'uDotSize', 'uGlow',
        'uHueOffset', 'uHueSweep', 'uTrailDecay',
        'uIntegratedTime', 'uIntegratedShear'
      ])
    },
    attractor_feedback: {
      prog: attractorProgram,
      locs: getUniformLocations(gl, attractorProgram, [
        'uPlaneScale', 'uColorShift', 'uPersistence',
        'uScale0', 'uRotate0', 'uOffsetX0', 'uOffsetY0', 'uVarCoef0', 'uJacobian0',
        'uScale1', 'uRotate1', 'uOffsetX1', 'uOffsetY1', 'uVarCoef1', 'uJacobian1',
        'src'
      ])
    },
    chladni: {
      prog: chladniProgram,
      locs: getUniformLocations(gl, chladniProgram, [
        'uMode', 'uFrequencyN', 'uFrequencyM', 'uFrequencyL', 'uThickness', 'uWallWidth',
        'uScale', 'uSpeed', 'uZoom', 'uColorShift', 'uRotateX', 'uRotateY', 'uRotateZ',
        'uAlpha', 'uResolution', 'uGlow', 'uTime'
      ])
    },
    gyroid: {
      prog: gyroidProgram,
      locs: getUniformLocations(gl, gyroidProgram, [
        'uScaleX', 'uScaleY', 'uScaleZ', 'uThickness', 'uWallWidth', 'uSpeed', 'uZoom',
        'uColorShift', 'uRotateX', 'uRotateY', 'uRotateZ', 'uAlpha', 'uResolution', 'uGlow', 'uTime'
      ])
    },
    hyper_slice: {
      prog: hyperSliceProgram,
      locs: getUniformLocations(gl, hyperSliceProgram, [
        'uSliceOffset', 'uRotateXW', 'uRotateYW', 'uRotateZW',
        'uRotateX', 'uRotateY', 'uRotateZ', 'uMorph', 'uSupportH', 'uZoom',
        'uColorMethod', 'uHueOffset', 'uSaturation', 'uBrightness', 'uOpacity',
        'uEdgeThickness', 'uEdgeBrightness', 'uGlow', 'uAlpha', 'uResolution', 'uTime'
      ])
    },
    icosahedron: {
      prog: icosahedronProgram,
      locs: getUniformLocations(gl, icosahedronProgram, [
        'uControlX', 'uControlY', 'uColorMethod', 'uHueOffset', 'uSaturation', 'uBrightness',
        'uOpacity', 'uEdgeThickness', 'uEdgeBrightness', 'uZoom',
        'uRotateX', 'uRotateY', 'uRotateZ', 'uSupportH', 'uAlpha', 'uResolution', 'uTime',
        'uH3Normals', 'uPlaneCount'
      ])
    },
    icosa_dodeca: {
      prog: icosaDodecaProgram,
      locs: getUniformLocations(gl, icosaDodecaProgram, [
        'uMorph', 'uStellation', 'uSupportH', 'uColorMethod', 'uHueOffset', 'uSaturation',
        'uBrightness', 'uOpacity', 'uEdgeThickness', 'uEdgeBrightness', 'uZoom',
        'uRotateX', 'uRotateY', 'uRotateZ', 'uAlpha', 'uResolution', 'uTime'
      ])
    },
    'icosa-v3': {
      prog: icosaV3Program,
      locs: getUniformLocations(gl, icosaV3Program, [
        'uControlX', 'uStellationSpike', 'uBlockerSize', 'uColorMethod', 'uHueOffset',
        'uSaturation', 'uBrightness', 'uOpacity', 'uEdgeThickness', 'uEdgeBrightness',
        'uZoom', 'uRotateX', 'uRotateY', 'uRotateZ', 'uSupportH', 'uAlpha', 'uResolution', 'uTime'
      ])
    },
    icosa_v3: {
      prog: icosaV3Program,
      locs: getUniformLocations(gl, icosaV3Program, [
        'uControlX', 'uStellationSpike', 'uBlockerSize', 'uColorMethod', 'uHueOffset',
        'uSaturation', 'uBrightness', 'uOpacity', 'uEdgeThickness', 'uEdgeBrightness',
        'uZoom', 'uRotateX', 'uRotateY', 'uRotateZ', 'uSupportH', 'uAlpha', 'uResolution', 'uTime'
      ])
    }
  };

  const feedbackUniforms = getUniformLocations(gl, feedbackProgram, [
    'uTextureLive', 'uTextureHistory',
    'uDecay', 'uGain', 'uZoom', 'uRotate',
    'uHueShift', 'uBlur', 'uChroma',
    'uFeedbackMode', 'uKaleido'
  ]);

  const mixerUniforms = getUniformLocations(gl, mixerProgram, [
    'uTex1', 'uTex2', 'uTexBG',
    'uMode', 'uBalance', 'uAlpha', 'uBgAlpha', 'uBloom'
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

  // Initial sizing & FBO creation
  const dpr = window.devicePixelRatio || 1;
  let curWidth  = Math.max(1, Math.floor(window.innerWidth  * dpr));
  let curHeight = Math.max(1, Math.floor(window.innerHeight * dpr));
  canvas.width  = curWidth;
  canvas.height = curHeight;

  const deckA     = createDeck(gl, curWidth, curHeight, internalFormat, format, type);
  const deckB     = createDeck(gl, curWidth, curHeight, internalFormat, format, type);
  const deckBG    = createDeck(gl, curWidth, curHeight, internalFormat, format, type);
  const masterFBO = createSingleFBO(gl, curWidth, curHeight, internalFormat, format, type);

  deckA.clear(0, 0, 0, 0);
  deckB.clear(0, 0, 0, 0);
  deckBG.clear(0, 0, 0, 1);
  masterFBO.clear(0, 0, 0, 1);

  // Start dual-queue autopilot
  await startAutopilot();

  window.LSD = { cvState, autopilotState, powerState };

  let lastTime        = performance.now();
  let elapsedTime     = 0;
  let frameCount      = 0;
  let integratedTime  = 0;
  let integratedShear = 0;

  function evalP(paramObj, fallback = 0.0) {
    return evaluateParameter(paramObj, elapsedTime, cvState.bpm * (elapsedTime / 60.0), frameCount, fallback);
  }

  function renderVisualSource(deckData, targetFBO, historyTex) {
    if (!deckData) return;
    const srcType = (deckData.source || 'mandala').toLowerCase();
    const progInfo = programs[srcType] || programs.mandala;
    const locs = progInfo.locs;

    gl.bindFramebuffer(gl.FRAMEBUFFER, targetFBO);
    gl.viewport(0, 0, curWidth, curHeight);
    gl.clearColor(0.0, 0.0, 0.0, 0.0);
    gl.clear(gl.COLOR_BUFFER_BIT);

    gl.useProgram(progInfo.prog);

    if (srcType === 'mandala') {
      gl.bindVertexArray(mandalaVAO);
      const rawL1 = evalP(deckData.L1 || deckData.l1, 0.4);
      const rawL2 = evalP(deckData.L2 || deckData.l2, 0.3);
      const rawL3 = evalP(deckData.L3 || deckData.l3, 0.2);
      const rawL4 = evalP(deckData.L4 || deckData.l4, 0.1);
      const sumL = Math.abs(rawL1) + Math.abs(rawL2) + Math.abs(rawL3) + Math.abs(rawL4);
      const targetRadius = 2.0;
      const normScale = sumL > 1e-5 ? (targetRadius / sumL) : 0.0;

      gl.uniform1f(locs.uL1, rawL1 * normScale);
      gl.uniform1f(locs.uL2, rawL2 * normScale);
      gl.uniform1f(locs.uL3, rawL3 * normScale);
      gl.uniform1f(locs.uL4, rawL4 * normScale);
      gl.uniform1f(locs.uA,  evalP(deckData.A || deckData.a || deckData.recipe?.a, 3.0));
      gl.uniform1f(locs.uB,  evalP(deckData.B || deckData.b || deckData.recipe?.b, 4.0));
      gl.uniform1f(locs.uC,  evalP(deckData.C || deckData.c || deckData.recipe?.c, 5.0));
      gl.uniform1f(locs.uD,  evalP(deckData.D || deckData.d || deckData.recipe?.d, 7.0));

      gl.uniform1f(locs.uMaxR, sumL > 1e-5 ? targetRadius : 0.001);
      gl.uniform1f(locs.uThickness,      evalP(deckData.Thickness || deckData.thickness, 0.012));
      gl.uniform1f(locs.uAspectRatio,    curWidth / curHeight);
      gl.uniform1f(locs.uHueOffset,      evalP(deckData['Hue Offset'] || deckData.hueOffset, 0.0));
      gl.uniform1f(locs.uHueSweep,       evalP(deckData['Hue Sweep'] || deckData.hueSweep, 0.3));
      gl.uniform1f(locs.uAlpha,          1.0);
      gl.uniform1f(locs.uDepth,          evalP(deckData.Depth || deckData.depth, 0.35));

      gl.drawArrays(gl.TRIANGLE_STRIP, 0, MANDALA_POINTS * 2);
    } else {
      gl.bindVertexArray(quadVAO);

      if (srcType === 'dynamic_spiral') {
        gl.activeTexture(gl.TEXTURE0);
        gl.bindTexture(gl.TEXTURE_2D, historyTex);
        gl.uniform1i(locs.src, 0);

        gl.uniform2f(locs.uResolution, curWidth, curHeight);
        gl.uniform1f(locs.uTime,        elapsedTime);
        gl.uniform1f(locs.uAlpha,       1.0);
        gl.uniform1f(locs.uMaxPoints,   evalP(deckData['Max Points'] || deckData.maxPoints, 500.0));
        gl.uniform1f(locs.uScale,       evalP(deckData.Scale || deckData.scale, 0.5));
        gl.uniform1f(locs.uDamping,     evalP(deckData.Damping || deckData.damping, 100.0));
        gl.uniform1f(locs.uWaveFreq,    evalP(deckData['Wave Freq'] || deckData.waveFreq, 0.2));
        gl.uniform1f(locs.uWaveAmp,     evalP(deckData['Wave Amp'] || deckData.waveAmp, 0.0));
        gl.uniform1f(locs.uShear,       evalP(deckData.Shear || deckData.shear, 0.1));
        gl.uniform1f(locs.uSpeed,       evalP(deckData.Speed || deckData.speed, 0.5));
        gl.uniform1f(locs.uDotSize,     evalP(deckData['Dot Size'] || deckData.dotSize, 0.01));
        gl.uniform1f(locs.uGlow,        evalP(deckData.Glow || deckData.glow, 1.5));
        gl.uniform1f(locs.uHueOffset,   evalP(deckData['Hue Offset'] || deckData.hueOffset, 0.33));
        gl.uniform1f(locs.uHueSweep,    evalP(deckData['Hue Sweep'] || deckData.hueSweep, 0.01));
        gl.uniform1f(locs.uTrailDecay,  evalP(deckData['Trail Decay'] || deckData.trailDecay, 0.85));
        gl.uniform1f(locs.uIntegratedTime,  deckData.integratedTime !== undefined ? deckData.integratedTime : integratedTime);
        gl.uniform1f(locs.uIntegratedShear, deckData.integratedShear !== undefined ? deckData.integratedShear : integratedShear);

      } else if (srcType === 'attractor_feedback') {
        gl.activeTexture(gl.TEXTURE0);
        gl.bindTexture(gl.TEXTURE_2D, historyTex);
        gl.uniform1i(locs.src, 0);

        gl.uniform1f(locs.uPlaneScale,  evalP(deckData['Plane Scale'] || deckData.planeScale, 0.3));
        gl.uniform1f(locs.uColorShift,  evalP(deckData['Color Shift'] || deckData.colorShift, 0.0));
        gl.uniform1f(locs.uPersistence, evalP(deckData.Persistence || deckData.persistence, 0.95));
        gl.uniform1f(locs.uScale0,      evalP(deckData['Scale 0'] || deckData.scale0, 0.9));
        gl.uniform1f(locs.uRotate0,     evalP(deckData['Rotate 0'] || deckData.rotate0, 0.1));
        gl.uniform1f(locs.uOffsetX0,    evalP(deckData['Offset X0'] || deckData.offsetX0, 0.0));
        gl.uniform1f(locs.uOffsetY0,    evalP(deckData['Offset Y0'] || deckData.offsetY0, 0.0));
        gl.uniform1f(locs.uVarCoef0,    evalP(deckData['Var Coef 0'] || deckData.varCoef0, 1.0));
        gl.uniform1f(locs.uJacobian0,   evalP(deckData['Jacobian 0'] || deckData.jacobian0, 1.0));
        gl.uniform1f(locs.uScale1,      evalP(deckData['Scale 1'] || deckData.scale1, 0.9));
        gl.uniform1f(locs.uRotate1,     evalP(deckData['Rotate 1'] || deckData.rotate1, -0.1));
        gl.uniform1f(locs.uOffsetX1,    evalP(deckData['Offset X1'] || deckData.offsetX1, 0.2));
        gl.uniform1f(locs.uOffsetY1,    evalP(deckData['Offset Y1'] || deckData.offsetY1, 0.0));
        gl.uniform1f(locs.uVarCoef1,    evalP(deckData['Var Coef 1'] || deckData.varCoef1, 1.0));
        gl.uniform1f(locs.uJacobian1,   evalP(deckData['Jacobian 1'] || deckData.jacobian1, 1.0));

      } else if (srcType === 'chladni') {
        gl.uniform2f(locs.uResolution, curWidth, curHeight);
        gl.uniform1f(locs.uTime,       elapsedTime);
        gl.uniform1f(locs.uAlpha,      1.0);
        gl.uniform1f(locs.uMode,       evalP(deckData.Mode || deckData.mode, 0.0));
        gl.uniform1f(locs.uFrequencyN, evalP(deckData['Frequency N'] || deckData.frequencyN, 3.0));
        gl.uniform1f(locs.uFrequencyM, evalP(deckData['Frequency M'] || deckData.frequencyM, 5.0));
        gl.uniform1f(locs.uFrequencyL, evalP(deckData['Frequency L'] || deckData.frequencyL, 2.0));
        gl.uniform1f(locs.uThickness,  evalP(deckData.Thickness || deckData.thickness, 0.05));
        gl.uniform1f(locs.uWallWidth,  evalP(deckData['Wall Width'] || deckData.wallWidth, 1.0));
        gl.uniform1f(locs.uScale,      evalP(deckData.Scale || deckData.scale, 2.0));
        gl.uniform1f(locs.uSpeed,      evalP(deckData.Speed || deckData.speed, 0.5));
        gl.uniform1f(locs.uZoom,       evalP(deckData.Zoom || deckData.zoom, 1.0));
        gl.uniform1f(locs.uColorShift, evalP(deckData['Color Shift'] || deckData.colorShift, 0.0));
        gl.uniform1f(locs.uRotateX,    evalP(deckData['Rotate X'] || deckData.rotateX, 0.0));
        gl.uniform1f(locs.uRotateY,    evalP(deckData['Rotate Y'] || deckData.rotateY, 0.0));
        gl.uniform1f(locs.uRotateZ,    evalP(deckData['Rotate Z'] || deckData.rotateZ, 0.0));
        gl.uniform1f(locs.uGlow,       evalP(deckData.Glow || deckData.glow, 1.0));

      } else if (srcType === 'gyroid') {
        gl.uniform2f(locs.uResolution, curWidth, curHeight);
        gl.uniform1f(locs.uTime,       elapsedTime);
        gl.uniform1f(locs.uAlpha,      1.0);
        gl.uniform1f(locs.uScaleX,     evalP(deckData['Scale X'] || deckData.scaleX, 2.0));
        gl.uniform1f(locs.uScaleY,     evalP(deckData['Scale Y'] || deckData.scaleY, 2.0));
        gl.uniform1f(locs.uScaleZ,     evalP(deckData['Scale Z'] || deckData.scaleZ, 2.0));
        gl.uniform1f(locs.uThickness,  evalP(deckData.Thickness || deckData.thickness, 0.1));
        gl.uniform1f(locs.uWallWidth,  evalP(deckData['Wall Width'] || deckData.wallWidth, 1.0));
        gl.uniform1f(locs.uSpeed,      evalP(deckData.Speed || deckData.speed, 0.5));
        gl.uniform1f(locs.uZoom,       evalP(deckData.Zoom || deckData.zoom, 1.0));
        gl.uniform1f(locs.uColorShift, evalP(deckData['Color Shift'] || deckData.colorShift, 0.0));
        gl.uniform1f(locs.uRotateX,    evalP(deckData['Rotate X'] || deckData.rotateX, 0.0));
        gl.uniform1f(locs.uRotateY,    evalP(deckData['Rotate Y'] || deckData.rotateY, 0.0));
        gl.uniform1f(locs.uRotateZ,    evalP(deckData['Rotate Z'] || deckData.rotateZ, 0.0));
        gl.uniform1f(locs.uGlow,       evalP(deckData.Glow || deckData.glow, 1.0));

      } else if (srcType === 'hyper_slice') {
        gl.uniform2f(locs.uResolution,     curWidth, curHeight);
        gl.uniform1f(locs.uTime,           elapsedTime);
        gl.uniform1f(locs.uAlpha,          1.0);
        gl.uniform1f(locs.uSliceOffset,    evalP(deckData['Slice Offset'] || deckData.sliceOffset, 0.0));
        gl.uniform1f(locs.uRotateXW,       evalP(deckData['Rotate XW'] || deckData.rotateXW, 0.0));
        gl.uniform1f(locs.uRotateYW,       evalP(deckData['Rotate YW'] || deckData.rotateYW, 0.0));
        gl.uniform1f(locs.uRotateZW,       evalP(deckData['Rotate ZW'] || deckData.rotateZW, 0.0));
        gl.uniform1f(locs.uRotateX,        evalP(deckData['Rotate X'] || deckData.rotateX, 0.0));
        gl.uniform1f(locs.uRotateY,        evalP(deckData['Rotate Y'] || deckData.rotateY, 0.0));
        gl.uniform1f(locs.uRotateZ,        evalP(deckData['Rotate Z'] || deckData.rotateZ, 0.0));
        gl.uniform1f(locs.uMorph,          evalP(deckData.Morph || deckData.morph, 0.0));
        gl.uniform1f(locs.uSupportH,       evalP(deckData['Support H'] || deckData.supportH, 0.8));
        gl.uniform1f(locs.uZoom,           evalP(deckData.Zoom || deckData.zoom, 1.0));
        gl.uniform1f(locs.uColorMethod,    evalP(deckData['Color Method'] || deckData.colorMethod, 0.0));
        gl.uniform1f(locs.uHueOffset,      evalP(deckData['Hue Offset'] || deckData.hueOffset, 0.0));
        gl.uniform1f(locs.uSaturation,     evalP(deckData.Saturation || deckData.saturation, 1.0));
        gl.uniform1f(locs.uBrightness,     evalP(deckData.Brightness || deckData.brightness, 1.0));
        gl.uniform1f(locs.uOpacity,        evalP(deckData.Opacity || deckData.opacity, 0.8));
        gl.uniform1f(locs.uEdgeThickness,  evalP(deckData['Edge Thickness'] || deckData.edgeThickness, 0.02));
        gl.uniform1f(locs.uEdgeBrightness, evalP(deckData['Edge Brightness'] || deckData.edgeBrightness, 1.0));
        gl.uniform1f(locs.uGlow,           evalP(deckData.Glow || deckData.glow, 1.0));

      } else if (srcType === 'icosahedron') {
        const cY = evalP(deckData['Control Y'] || deckData.controlY, 0.0);
        const { planeCount, normals } = generateH3Normals(cY);

        gl.uniform2f(locs.uResolution,     curWidth, curHeight);
        gl.uniform1f(locs.uTime,           elapsedTime);
        gl.uniform1f(locs.uAlpha,          1.0);
        gl.uniform1f(locs.uControlX,       evalP(deckData['Control X'] || deckData.controlX, 0.0));
        gl.uniform1f(locs.uControlY,       cY);
        gl.uniform1f(locs.uColorMethod,    evalP(deckData['Color Method'] || deckData.colorMethod, 0.0));
        gl.uniform1f(locs.uHueOffset,      evalP(deckData['Hue Offset'] || deckData.hueOffset, 0.0));
        gl.uniform1f(locs.uSaturation,     evalP(deckData.Saturation || deckData.saturation, 1.0));
        gl.uniform1f(locs.uBrightness,     evalP(deckData.Brightness || deckData.brightness, 1.0));
        gl.uniform1f(locs.uOpacity,        evalP(deckData.Opacity || deckData.opacity, 0.8));
        gl.uniform1f(locs.uEdgeThickness,  evalP(deckData['Edge Thickness'] || deckData.edgeThickness, 0.02));
        gl.uniform1f(locs.uEdgeBrightness, evalP(deckData['Edge Brightness'] || deckData.edgeBrightness, 1.0));
        gl.uniform1f(locs.uZoom,           evalP(deckData.Zoom || deckData.zoom, 1.0));
        gl.uniform1f(locs.uRotateX,        evalP(deckData['Rotate X'] || deckData.rotateX, 0.0));
        gl.uniform1f(locs.uRotateY,        evalP(deckData['Rotate Y'] || deckData.rotateY, 0.0));
        gl.uniform1f(locs.uRotateZ,        evalP(deckData['Rotate Z'] || deckData.rotateZ, 0.0));
        gl.uniform1f(locs.uSupportH,       evalP(deckData['Support H'] || deckData.supportH, 0.82));
        gl.uniform1i(locs.uPlaneCount,     planeCount);
        gl.uniform3fv(locs.uH3Normals,     normals);

      } else if (srcType === 'icosa_dodeca') {
        gl.uniform2f(locs.uResolution,     curWidth, curHeight);
        gl.uniform1f(locs.uTime,           elapsedTime);
        gl.uniform1f(locs.uAlpha,          1.0);
        gl.uniform1f(locs.uMorph,          evalP(deckData.Morph || deckData.morph, 0.0));
        gl.uniform1f(locs.uStellation,     evalP(deckData.Stellation || deckData.stellation, 0.0));
        gl.uniform1f(locs.uSupportH,       evalP(deckData['Support H'] || deckData.supportH, 0.0));
        gl.uniform1f(locs.uColorMethod,    evalP(deckData['Color Method'] || deckData.colorMethod, 0.0));
        gl.uniform1f(locs.uHueOffset,      evalP(deckData['Hue Offset'] || deckData.hueOffset, 0.0));
        gl.uniform1f(locs.uSaturation,     evalP(deckData.Saturation || deckData.saturation, 1.0));
        gl.uniform1f(locs.uBrightness,     evalP(deckData.Brightness || deckData.brightness, 1.0));
        gl.uniform1f(locs.uOpacity,        evalP(deckData.Opacity || deckData.opacity, 0.8));
        gl.uniform1f(locs.uEdgeThickness,  evalP(deckData['Edge Thickness'] || deckData.edgeThickness, 0.02));
        gl.uniform1f(locs.uEdgeBrightness, evalP(deckData['Edge Brightness'] || deckData.edgeBrightness, 1.0));
        gl.uniform1f(locs.uZoom,           evalP(deckData.Zoom || deckData.zoom, 1.0));
        gl.uniform1f(locs.uRotateX,        evalP(deckData['Rotate X'] || deckData.rotateX, 0.0));
        gl.uniform1f(locs.uRotateY,        evalP(deckData['Rotate Y'] || deckData.rotateY, 0.0));
        gl.uniform1f(locs.uRotateZ,        evalP(deckData['Rotate Z'] || deckData.rotateZ, 0.0));

      } else if (srcType === 'icosa_v3' || srcType === 'icosa-v3') {
        gl.uniform2f(locs.uResolution,      curWidth, curHeight);
        gl.uniform1f(locs.uTime,            elapsedTime);
        gl.uniform1f(locs.uAlpha,           1.0);
        gl.uniform1f(locs.uControlX,        evalP(deckData['Control X'] || deckData.controlX, 0.0));
        gl.uniform1f(locs.uStellationSpike, evalP(deckData['Stellation Spike'] || deckData.stellationSpike, 0.0));
        gl.uniform1f(locs.uBlockerSize,     evalP(deckData['Blocker Size'] || deckData.blockerSize, 0.0));
        gl.uniform1f(locs.uColorMethod,     evalP(deckData['Color Method'] || deckData.colorMethod, 0.0));
        gl.uniform1f(locs.uHueOffset,       evalP(deckData['Hue Offset'] || deckData.hueOffset, 0.0));
        gl.uniform1f(locs.uSaturation,      evalP(deckData.Saturation || deckData.saturation, 1.0));
        gl.uniform1f(locs.uBrightness,      evalP(deckData.Brightness || deckData.brightness, 1.0));
        gl.uniform1f(locs.uOpacity,         evalP(deckData.Opacity || deckData.opacity, 0.8));
        gl.uniform1f(locs.uEdgeThickness,   evalP(deckData['Edge Thickness'] || deckData.edgeThickness, 0.02));
        gl.uniform1f(locs.uEdgeBrightness,  evalP(deckData['Edge Brightness'] || deckData.edgeBrightness, 1.0));
        gl.uniform1f(locs.uZoom,            evalP(deckData.Zoom || deckData.zoom, 1.0));
        gl.uniform1f(locs.uRotateX,         evalP(deckData['Rotate X'] || deckData.rotateX, 0.0));
        gl.uniform1f(locs.uRotateY,         evalP(deckData['Rotate Y'] || deckData.rotateY, 0.0));
        gl.uniform1f(locs.uRotateZ,         evalP(deckData['Rotate Z'] || deckData.rotateZ, 0.0));
        gl.uniform1f(locs.uSupportH,        evalP(deckData['Support H'] || deckData.supportH, 0.82));
      }

      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }
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
    gl.uniform1f(feedbackUniforms.uDecay,        evalP(fb.decay || fb.fbDecay, 0.04));
    gl.uniform1f(feedbackUniforms.uGain,         evalP(fb.gain || fb.fbGain, 0.96));
    gl.uniform1f(feedbackUniforms.uZoom,         evalP(fb.zoom || fb.fbZoom, 0.005));
    gl.uniform1f(feedbackUniforms.uRotate,       evalP(fb.rotate || fb.fbRotate, 0.008));
    gl.uniform1f(feedbackUniforms.uHueShift,     evalP(fb.hueShift || fb.fbHueShift, 0.001));
    gl.uniform1f(feedbackUniforms.uBlur,         evalP(fb.blur || fb.fbBlur, 0.0));
    gl.uniform1f(feedbackUniforms.uChroma,       evalP(fb.chroma || fb.fbChroma, 0.0));
    gl.uniform1f(feedbackUniforms.uFeedbackMode, evalP(fb.mode || fb.fbMode, 0.0));
    gl.uniform1f(feedbackUniforms.uKaleido,      evalP(fb.kaleido || fb.fbKaleido, 1.0));

    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    deck.swap();
  }

  function render(now) {
    const dt = Math.min((now - lastTime) / 1000, 0.1);
    lastTime = now;
    elapsedTime += dt;
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
      masterFBO.resize(curWidth, curHeight);
    }

    // Dynamic spiral speed integration
    const speedA = evalP(autopilotState.deckA?.speed || autopilotState.deckA?.Speed, 0.5);
    const shearA = evalP(autopilotState.deckA?.shear || autopilotState.deckA?.Shear, 0.1);

    if (autopilotState.deckA?.integratedTime !== undefined) {
      integratedTime = autopilotState.deckA.integratedTime;
      delete autopilotState.deckA.integratedTime;
    } else {
      integratedTime += dt * speedA;
    }

    if (autopilotState.deckA?.integratedShear !== undefined) {
      integratedShear = autopilotState.deckA.integratedShear;
      delete autopilotState.deckA.integratedShear;
    } else {
      integratedShear += dt * speedA * shearA;
    }

    gl.disable(gl.BLEND);
    gl.disable(gl.DEPTH_TEST);

    if (powerState.on || powerState.warmupProgress > 0 || (powerState.shutdownProgress > 0.0 && powerState.shutdownProgress < 0.42)) {
      // 1. Render Deck A
      if (autopilotState.deckA) {
        renderVisualSource(autopilotState.deckA, deckA.cleanFBO, deckA.readTex);
        renderFeedbackPass(deckA, autopilotState.deckA);
      }

      // 2. Render Deck B
      if (autopilotState.deckB) {
        renderVisualSource(autopilotState.deckB, deckB.cleanFBO, deckB.readTex);
        renderFeedbackPass(deckB, autopilotState.deckB);
      }

      // 3. Render Deck BG
      if (autopilotState.deckBG) {
        renderVisualSource(autopilotState.deckBG, deckBG.cleanFBO, deckBG.readTex);
        renderFeedbackPass(deckBG, autopilotState.deckBG);
      } else {
        gl.bindFramebuffer(gl.FRAMEBUFFER, deckBG.writeFBO);
        gl.viewport(0, 0, curWidth, curHeight);
        gl.clearColor(0.0, 0.0, 0.0, 1.0);
        gl.clear(gl.COLOR_BUFFER_BIT);
        deckBG.swap();
      }

      // 4. Mixer Pass -> masterFBO
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

      const mix = autopilotState.mixer || {};
      gl.uniform1i(mixerUniforms.uMode,    mix.mode ?? 0);
      gl.uniform1f(mixerUniforms.uBalance, mix.balance ?? 0.0);
      gl.uniform1f(mixerUniforms.uAlpha,   (mix.alpha ?? 1.0) * autopilotState.masterAlpha);
      gl.uniform1f(mixerUniforms.uBgAlpha, autopilotState.bgAlpha ?? 1.0);
      gl.uniform1f(mixerUniforms.uBloom,   mix.bloom ?? 0.0);

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
