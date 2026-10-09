// Minimal ISF loader for WebGL2. Port of the desktop ISFParser.buildGLSLFragmentShader
// (src/main/kotlin/.../rendering/isf/ISFParser.kt). Keep the two in step.
//
// Scope: single-pass shaders with float/long/bool/event/color/point2D/image inputs.
// PASSES/PERSISTENT/IMPORTED are parsed into the header but not yet rendered (see
// .planning/web-renderer-parity-plan.md, phase 1).

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

/** Uniform values for one frame. Names come from ISF INPUTS, so `params` is keyed by NAME. */
export function applyUniforms(gl, program, header, params, frame) {
  const loc = (n) => gl.getUniformLocation(program, n);
  const set1f = (n, v) => { const l = loc(n); if (l) gl.uniform1f(l, v); };
  const set1i = (n, v) => { const l = loc(n); if (l) gl.uniform1i(l, v); };
  const setSize = (l) => { if (l) gl.uniform2f(l, frame.width, frame.height); };

  setSize(loc('RENDERSIZE'));
  const res3 = loc('iResolution'); if (res3) gl.uniform3f(res3, frame.width, frame.height, 1);
  setSize(loc('u_resolution')); setSize(loc('resolution'));
  for (const n of ['TIME', 'iTime', 'u_time', 'time']) set1f(n, frame.time);
  for (const n of ['TIMEDELTA', 'iTimeDelta', 'u_delta']) set1f(n, frame.dt);
  for (const n of ['FRAMEINDEX', 'iFrame', 'u_frame']) set1i(n, frame.index);
  set1i('PASSINDEX', 0);
  set1f('uAlpha', frame.alpha ?? 1.0);

  for (const input of header.INPUTS) {
    const type = input.TYPE.toLowerCase();
    if (type === 'image' || type === 'audio' || type === 'audiofft') continue;
    const l = loc(input.NAME);
    if (!l) continue;
    const v = params[input.NAME] ?? input.DEFAULT ?? 0;
    switch (type) {
      case 'bool': case 'event': case 'long': case 'int':
        gl.uniform1i(l, Math.round(Number(v)));
        break;
      case 'color': gl.uniform4fv(l, Array.isArray(v) ? v : [v, v, v, 1]); break;
      case 'point2d': gl.uniform2fv(l, Array.isArray(v) ? v : [v, v]); break;
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
