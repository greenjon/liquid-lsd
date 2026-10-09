// Minimal ISF loader for WebGL2. Port of the desktop ISFParser.buildGLSLFragmentShader
// (src/main/kotlin/.../rendering/isf/ISFParser.kt). Keep the two in step.
//
// Scope: float/long/bool/event/color/point2D/image inputs, multi-pass shaders with PASSES,
// TARGET, PERSISTENT, FLOAT and $WIDTH/$HEIGHT dimension expressions. IMPORTED assets and
// audio/audioFFT inputs are not supported yet (nothing bundled uses them).
// See .planning/web-renderer-parity-plan.md, phase 1.

const HEADER_RE = /\/\*\s*(\{[\s\S]*?\})\s*\*\//;

export function parseHeader(source) {
  const m = HEADER_RE.exec(source);
  if (!m) return { INPUTS: [], PASSES: [] };
  const h = JSON.parse(m[1]);
  h.INPUTS = h.INPUTS || [];
  h.PASSES = h.PASSES || [];
  return h;
}

export function stripHeader(source) {
  return source.replace(HEADER_RE, '');
}

// Uniforms every wrapped shader may reference. Kept in the same order as the desktop list.
const STANDARD_UNIFORMS = [
  ['vec2', 'RENDERSIZE'], ['vec3', 'iResolution'], ['vec2', 'u_resolution'], ['vec2', 'resolution'],
  ['float', 'TIME'], ['float', 'iTime'], ['float', 'u_time'], ['float', 'time'],
  ['float', 'TIMEDELTA'], ['float', 'iTimeDelta'], ['float', 'u_delta'],
  ['int', 'FRAMEINDEX'], ['int', 'iFrame'], ['int', 'u_frame'], ['float', 'iFrameRate'],
  ['vec4', 'DATE'], ['vec4', 'iDate'], ['int', 'PASSINDEX'], ['float', 'uAlpha'],
  ['vec4', 'iMouse'], ['vec2', 'u_mouse'], ['vec2', 'mouse'],
  ['float', 'audioVolume'], ['float', 'audioBass'], ['float', 'audioMid'], ['float', 'audioTreble'],
  ['sampler2D', 'audioFFT'],
  ['sampler2D', 'iChannel0'], ['sampler2D', 'iChannel1'], ['sampler2D', 'iChannel2'], ['sampler2D', 'iChannel3'],
];

const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const declRe = (name) => new RegExp(`\\buniform\\s+(?:[A-Za-z0-9_]+\\s+)*${escapeRe(name)}\\s*;`);

/** Wraps ISF source into a WebGL2 (GLSL ES 3.00) fragment shader. */
export function buildFragmentShader(rawSource, header = parseHeader(rawSource)) {
  const stripped = stripHeader(rawSource);
  let out = '#version 300 es\nprecision highp float;\nprecision highp int;\n\n';
  out += 'in vec2 vTexCoord;\nout vec4 isf_FragColor;\n\n';
  out += '#define texture2D texture\n#define textureCube texture\n#define gl_FragColor isf_FragColor\n\n';
  out += '#define isf_FragNormCoord vTexCoord\n#define vv_FragNormCoord vTexCoord\n#define vv_FragCoord gl_FragCoord\n';
  out += '#define IMG_NORM_PIXEL(sampler, coord) texture(sampler, (coord))\n';
  out += '#define IMG_PIXEL(sampler, coord) texture(sampler, (coord) / RENDERSIZE)\n';
  out += '#define IMG_THIS_PIXEL(sampler) texture(sampler, gl_FragCoord.xy / RENDERSIZE)\n';
  out += '#define IMG_THIS_NORM_PIXEL(sampler) texture(sampler, vTexCoord)\n';
  out += '#define IMG_SIZE(sampler) vec2(textureSize(sampler, 0))\n\n';

  const passTargets = new Set(header.PASSES.map((p) => p.TARGET).filter(Boolean));
  const samplerInputs = new Set(
    header.INPUTS.filter((i) => ['image', 'audio', 'audiofft'].includes(i.TYPE.toLowerCase())).map((i) => i.NAME));
  const samplerTargets = new Set([...passTargets, ...samplerInputs]);

  for (const [type, name] of STANDARD_UNIFORMS) {
    if (samplerTargets.has(name)) continue;
    out += `uniform ${type} ${name};\n`;
  }
  out += 'uniform vec3 iChannelResolution[4];\nuniform float iChannelTime[4];\n\n';

  const standardNames = new Set(STANDARD_UNIFORMS.map(([, n]) => n));
  for (const input of header.INPUTS) {
    if (standardNames.has(input.NAME) && !samplerTargets.has(input.NAME)) continue;
    let decl;
    switch (input.TYPE.toLowerCase()) {
      case 'float': decl = `uniform float ${input.NAME};`; break;
      case 'bool': case 'event': decl = `uniform bool ${input.NAME};`; break;
      case 'long': case 'int': decl = `uniform int ${input.NAME};`; break;
      case 'color': decl = `uniform vec4 ${input.NAME};`; break;
      case 'point2d': decl = `uniform vec2 ${input.NAME};`; break;
      case 'image': case 'audio': case 'audiofft':
        decl = `uniform sampler2D ${input.NAME};\nuniform vec4 _${input.NAME}_imgRect;\n` +
               `uniform vec2 _${input.NAME}_imgSize;\nuniform bool _${input.NAME}_flip;`;
        break;
      default: decl = `uniform float ${input.NAME};`;
    }
    if (!declRe(input.NAME).test(stripped)) out += decl + '\n';
  }
  out += '\n';

  for (const target of passTargets) {
    if (!declRe(target).test(stripped)) out += `uniform sampler2D ${target};\n`;
  }
  out += '\n';

  let body = stripped;
  body = body.replace(/^\s*#version\s+.*$/gm, '');
  body = body.replace(/^\s*#extension\s+.*$/gm, '');
  body = body.replace(/^\s*precision\s+(highp|mediump|lowp)\s+(float|int)\s*;/gm, '');
  body = body.replace(/^\s*in\s+vec2\s+vTexCoord\s*;/gm, '');
  for (const [, name] of STANDARD_UNIFORMS) {
    body = body.replace(
      new RegExp(`\\buniform\\s+(?:[A-Za-z0-9_]+\\s+)*${escapeRe(name)}\\s*(?:\\[\\s*\\d*\\s*\\]\\s*)?;`, 'g'), '');
  }
  const outFrag = /\bout\s+vec4\s+([A-Za-z0-9_]+)\s*;/.exec(body);
  if (outFrag) body = body.replace(outFrag[0], `#define ${outFrag[1]} isf_FragColor`);
  return out + body;
}

/**
 * Sets the standard and per-input uniforms for one frame. `params` is keyed by input NAME;
 * `frame` is {width, height, time, dt, index, alpha, view?:{zoom, rotateZ}}.
 * Scalar params fill color as (v,v,v,1) and point2D as (v,0), as the desktop does.
 */
export function applyUniforms(gl, prog, params, frame) {
  const { header } = prog;
  const set1f = (n, v) => { const l = prog.loc(n); if (l) gl.uniform1f(l, v); };
  const set1i = (n, v) => { const l = prog.loc(n); if (l) gl.uniform1i(l, v); };

  for (const n of ['uResolution', 'u_resolution', 'resolution']) {
    const l = prog.loc(n); if (l) gl.uniform2f(l, frame.width, frame.height);
  }
  const res3 = prog.loc('iResolution'); if (res3) gl.uniform3f(res3, frame.width, frame.height, 1);
  for (const n of ['TIME', 'iTime', 'u_time', 'time', 'uTime']) set1f(n, frame.time);
  for (const n of ['TIMEDELTA', 'iTimeDelta', 'u_delta']) set1f(n, frame.dt);
  for (const n of ['FRAMEINDEX', 'iFrame', 'u_frame']) set1i(n, frame.index);
  set1f('iFrameRate', frame.dt > 0 ? 1 / frame.dt : 60);
  set1f('uAlpha', frame.alpha ?? 1.0);
  const d = new Date();
  const date = [d.getFullYear(), d.getMonth() + 1, d.getDate(),
    d.getHours() * 3600 + d.getMinutes() * 60 + d.getSeconds() + d.getMilliseconds() / 1000];
  for (const n of ['DATE', 'iDate']) { const l = prog.loc(n); if (l) gl.uniform4fv(l, date); }

  // blit.vert applies the 2D view (zoom / roll) to vTexCoord for every shader that uses it.
  const view = frame.view || {};
  set1f('uZoom', view.zoom ?? 1.0);
  set1f('uRotateZ', view.rotateZ ?? 0.0);
  set1f('uAspectRatio', frame.width / Math.max(1, frame.height));

  for (const input of header.INPUTS) {
    const type = input.TYPE.toLowerCase();
    if (type === 'image' || type === 'audio' || type === 'audiofft') continue;
    const l = prog.loc(input.NAME);
    if (!l) continue;
    const v = params[input.NAME] ?? input.DEFAULT ?? 0;
    switch (type) {
      case 'bool': case 'event': gl.uniform1i(l, Number(v) > 0.5 ? 1 : 0); break;
      case 'long': case 'int': gl.uniform1i(l, Math.round(Number(v))); break;
      case 'color': gl.uniform4fv(l, Array.isArray(v) ? v : [v, v, v, 1]); break;
      case 'point2d': gl.uniform2fv(l, Array.isArray(v) ? v : [v, 0]); break;
      default: gl.uniform1f(l, Number(v));
    }
  }
}

/** Default parameter values for a header, keyed by input NAME. */
export function defaultParams(header) {
  const p = {};
  for (const i of header.INPUTS) if (i.DEFAULT !== undefined) p[i.NAME] = i.DEFAULT;
  return p;
}

const camel = (k) => k.replace(/[\s-_]+([a-zA-Z0-9])/g, (_, c) => c.toUpperCase()).replace(/^[A-Z]/, (c) => c.toLowerCase());

/**
 * Finds an input's value in a parameter bag. Presets key by NAME, LABEL, or camelCase of
 * either; `!== undefined` keeps an explicit 0 from falling through to the default.
 */
export function lookupParam(bag, input) {
  if (!bag) return undefined;
  for (const k of [input.NAME, input.LABEL, camel(input.NAME), input.LABEL && camel(input.LABEL)]) {
    if (k && bag[k] !== undefined) return bag[k];
  }
  return undefined;
}

// ---------------------------------------------------------------------------------------
// Runtime: compiled programs, per-instance pass targets, multi-pass rendering.
// Mirrors ISFFilter.kt (passes, persistent ping-pong, dimension expressions).
// ---------------------------------------------------------------------------------------

function compile(gl, type, src, label) {
  const s = gl.createShader(type);
  gl.shaderSource(s, src);
  gl.compileShader(s);
  if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
    const log = gl.getShaderInfoLog(s);
    gl.deleteShader(s);
    throw new Error(`${label}: shader compile failed: ${log}`);
  }
  return s;
}

export function linkProgram(gl, vertSrc, fragSrc, label = 'program') {
  const vs = compile(gl, gl.VERTEX_SHADER, vertSrc, label + ' (vertex)');
  const fs = compile(gl, gl.FRAGMENT_SHADER, fragSrc, label);
  const p = gl.createProgram();
  gl.attachShader(p, vs);
  gl.attachShader(p, fs);
  gl.linkProgram(p);
  if (!gl.getProgramParameter(p, gl.LINK_STATUS)) {
    const log = gl.getProgramInfoLog(p);
    gl.deleteProgram(p);
    throw new Error(`${label}: link failed: ${log}`);
  }
  return p;
}

// "$WIDTH/2.0", "$HEIGHT", "512", "$WIDTH*0.5" -> (w, h) => int, or null for "use the target size".
function parseDim(expr) {
  if (expr == null) return null;
  const operand = (t) => {
    t = t.trim();
    if (t === '$WIDTH') return (w) => w;
    if (t === '$HEIGHT') return (w, h) => h;
    const n = parseFloat(t);
    return Number.isFinite(n) ? () => n : null;
  };
  const m = /^(.+?)([\/*])(.+)$/.exec(String(expr));
  if (m) {
    const l = operand(m[1]), r = operand(m[3]);
    if (!l || !r) return null;
    return m[2] === '/' ? (w, h) => Math.floor(l(w, h) / r(w, h)) : (w, h) => Math.floor(l(w, h) * r(w, h));
  }
  const o = operand(String(expr));
  return o ? (w, h) => Math.floor(o(w, h)) : null;
}

/** A compiled ISF shader, shared by every instance of it. */
export class ISFProgram {
  constructor(gl, vertSrc, rawFrag, label = 'isf') {
    this.gl = gl;
    this.header = parseHeader(rawFrag);
    this.program = linkProgram(gl, vertSrc, buildFragmentShader(rawFrag, this.header), label);
    this.locs = new Map();
    const images = this.header.INPUTS.filter((i) => i.TYPE.toLowerCase() === 'image').map((i) => i.NAME);
    this.inputName = images[0] ?? null;
    this.startName = images.find((n) => /^(startImage|inputImage|uTex1)$/i.test(n)) ?? images[0] ?? 'startImage';
    this.endName = images.find((n) => /^(endImage|toImage|uTex2)$/i.test(n)) ?? images[1] ?? 'endImage';
    this.passes = this.header.PASSES.map((p) => ({
      target: p.TARGET || null, persistent: !!p.PERSISTENT, float: !!p.FLOAT,
      width: parseDim(p.WIDTH), height: parseDim(p.HEIGHT),
    }));
    this.is3D = !!this.header.is3D ||
      (this.header.INPUTS.some((i) => i.NAME === 'Rotate X') && this.header.INPUTS.some((i) => i.NAME === 'Rotate Y'));
  }

  loc(name) {
    let l = this.locs.get(name);
    if (l === undefined) { l = this.gl.getUniformLocation(this.program, name); this.locs.set(name, l); }
    return l;
  }
}

function makeTarget(gl, w, h, floatFmt) {
  const tex = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, tex);
  if (floatFmt) gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA16F, w, h, 0, gl.RGBA, gl.HALF_FLOAT, null);
  else gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  const fbo = gl.createFramebuffer();
  gl.bindFramebuffer(gl.FRAMEBUFFER, fbo);
  gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0);
  gl.clearColor(0, 0, 0, 0);
  gl.clear(gl.COLOR_BUFFER_BIT);
  return { tex, fbo, w, h };
}

/** Per-instance pass targets (one per FX slot, transition, or deck source). */
export class ISFState {
  constructor() { this.w = 0; this.h = 0; this.regular = new Map(); this.persistent = new Map(); this.frame = 0; }

  ensure(gl, prog, w, h, hasFloat) {
    if (this.w === w && this.h === h && this.prog === prog) return;
    this.dispose(gl);
    this.w = w; this.h = h; this.prog = prog;
    for (const p of prog.passes) {
      if (!p.target) continue;
      const pw = Math.max(1, p.width ? p.width(w, h) : w);
      const ph = Math.max(1, p.height ? p.height(w, h) : h);
      const fl = p.float && hasFloat;
      if (p.persistent) {
        if (!this.persistent.has(p.target)) this.persistent.set(p.target, { front: makeTarget(gl, pw, ph, fl), back: makeTarget(gl, pw, ph, fl) });
      } else if (!this.regular.has(p.target)) {
        this.regular.set(p.target, makeTarget(gl, pw, ph, fl));
      }
    }
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  }

  dispose(gl) {
    const kill = (t) => { gl.deleteTexture(t.tex); gl.deleteFramebuffer(t.fbo); };
    for (const t of this.regular.values()) kill(t);
    for (const s of this.persistent.values()) { kill(s.front); kill(s.back); }
    this.regular.clear(); this.persistent.clear();
    this.w = this.h = 0;
  }
}

/**
 * Renders one ISF shader into `targetFBO` (null = canvas). `images` maps image-input NAME to a
 * texture. Non-final passes go to their TARGETs; the last pass always writes `targetFBO`.
 * ctx: {gl, quadVAO, dummyTex, hasFloat}; frame: see applyUniforms.
 */
export function renderISF(ctx, prog, state, { targetFBO, width, height, params, frame, images = {} }) {
  const { gl } = ctx;
  state.ensure(gl, prog, width, height, ctx.hasFloat);
  gl.disable(gl.BLEND);
  gl.useProgram(prog.program);
  gl.bindVertexArray(ctx.quadVAO);
  applyUniforms(gl, prog, params, { ...frame, width, height, index: state.frame });
  state.frame++;

  const passes = prog.passes;
  const count = Math.max(1, passes.length);
  for (let i = 0; i < count; i++) {
    const pass = passes[i];
    const last = i === count - 1;
    let writeFbo = targetFBO, pw = width, ph = height, selfTex = null;
    if (pass && !last && pass.target) {
      const t = pass.persistent ? state.persistent.get(pass.target).front : state.regular.get(pass.target);
      writeFbo = t.fbo; pw = t.w; ph = t.h; selfTex = t.tex;
    } else if (pass && pass.width) {
      pw = pass.width(width, height) || width; ph = pass.height ? pass.height(width, height) || height : height;
    }
    gl.bindFramebuffer(gl.FRAMEBUFFER, writeFbo);
    gl.viewport(0, 0, pw, ph);
    const rs = prog.loc('RENDERSIZE'); if (rs) gl.uniform2f(rs, pw, ph);
    const pi = prog.loc('PASSINDEX'); if (pi) gl.uniform1i(pi, i);

    let unit = 0;
    const bind = (name, tex) => {
      const l = prog.loc(name);
      if (!l) return;
      gl.activeTexture(gl.TEXTURE0 + unit);
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.uniform1i(l, unit++);
    };
    for (const name of Object.keys(images)) bind(name, images[name]);
    for (const [name, t] of state.regular) bind(name, t.tex === selfTex ? ctx.dummyTex : t.tex);
    for (const [name, s] of state.persistent) bind(name, s.back.tex);

    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    if (pass && pass.persistent && pass.target && !last) {
      const s = state.persistent.get(pass.target);
      const tmp = s.front; s.front = s.back; s.back = tmp;
    }
  }
  gl.activeTexture(gl.TEXTURE0);
}
