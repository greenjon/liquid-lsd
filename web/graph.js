// Render graph: library of ISF assets, deck source + FX chain, transition, master FX chain.
// Mirrors Renderer.kt (renderDeck / renderMixer / renderFxChainPass). Mandala is the one
// source drawn outside this graph (renderer.js), but its output is just a texture here.
//
// State shapes (see .planning/web-renderer-parity-plan.md; phase 3 will put these on the wire):
//   deck:  { source, <ISF input NAME>: number|ParameterDto, viewZoom, viewRotateZ, globalAlpha,
//            fx: [slot|null x3], fxDryWet, fxEnabled }
//   slot:  { id, enabled, dryWet, params: { <ISF input NAME>: number|ParameterDto } }
//   mixer: { balance, transition, transitionParams, levelA, levelB, levelBG, master,
//            fx: [slot|null x3], fxDryWet, fxEnabled }

import { ISFProgram, ISFState, renderISF, lookupParam } from './isf.js';

export const FX_SLOTS = 3;

/** An offscreen colour target. */
export class Target {
  constructor(gl, w, h, fmt) {
    this.gl = gl; this.fmt = fmt;
    this.tex = gl.createTexture();
    this.fbo = gl.createFramebuffer();
    this.attached = false;
    this.resize(w, h);
    gl.bindFramebuffer(gl.FRAMEBUFFER, this.fbo);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, this.tex, 0);
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    this.attached = true;
    this.clear();
  }

  resize(w, h) {
    const { gl, fmt } = this;
    this.w = w; this.h = h;
    gl.bindTexture(gl.TEXTURE_2D, this.tex);
    gl.texImage2D(gl.TEXTURE_2D, 0, fmt.internalFormat, w, h, 0, fmt.format, fmt.type, null);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.bindTexture(gl.TEXTURE_2D, null);
    if (this.attached) this.clear();
  }

  bind() {
    this.gl.bindFramebuffer(this.gl.FRAMEBUFFER, this.fbo);
    this.gl.viewport(0, 0, this.w, this.h);
  }

  clear(r = 0, g = 0, b = 0, a = 0) {
    const { gl } = this;
    gl.bindFramebuffer(gl.FRAMEBUFFER, this.fbo);
    gl.clearColor(r, g, b, a);
    gl.clear(gl.COLOR_BUFFER_BIT);
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  }
}

/**
 * Lazily fetches and compiles ISF assets listed in catalog.json. `get` never blocks: it
 * returns null until the asset has loaded (callers skip the effect for those frames) and
 * null forever if it failed (logged once).
 */
export class Library {
  constructor(gl, vertSrc, catalog, baseUrl = '') {
    this.gl = gl; this.vertSrc = vertSrc; this.baseUrl = baseUrl;
    this.entries = {};
    for (const kind of ['sources', 'filters', 'transitions']) {
      this.entries[kind] = new Map((catalog[kind] || []).map((e) => [e.id, e]));
    }
    this.programs = new Map();
    this.pending = new Set();
    this.failed = new Set();
  }

  has(kind, id) { return this.entries[kind]?.has(id) ?? false; }

  get(kind, id) {
    const key = `${kind}/${id}`;
    const p = this.programs.get(key);
    if (p) return p;
    if (!this.pending.has(key) && !this.failed.has(key)) this.load(kind, id, key);
    return null;
  }

  async load(kind, id, key) {
    const entry = this.entries[kind]?.get(id);
    if (!entry) { this.failed.add(key); console.warn(`[lsd] unknown ${kind} id "${id}"`); return; }
    this.pending.add(key);
    try {
      const res = await fetch(this.baseUrl + entry.file);
      if (!res.ok) throw new Error(`status ${res.status}`);
      this.programs.set(key, new ISFProgram(this.gl, this.vertSrc, await res.text(), key));
    } catch (err) {
      this.failed.add(key);
      console.error(`[lsd] ${key} unavailable:`, err);
    } finally {
      this.pending.delete(key);
    }
  }

  /** Resolves once every listed asset has loaded or failed. */
  async preload(kind, ids) {
    for (const id of ids) this.get(kind, id);
    while ([...this.pending].length) await new Promise((r) => setTimeout(r, 20));
  }
}

/**
 * Evaluates an ISF input bag into {NAME: value}. `ctx.input(bag, input)` does the
 * modulation maths (renderer.js owns the evaluator context).
 */
function evalParams(ctx, prog, bag) {
  const params = {};
  for (const input of prog.header.INPUTS) {
    if (input.TYPE.toLowerCase() === 'image') continue;
    params[input.NAME] = ctx.input(bag, input);
  }
  return params;
}

/** A deck's source renderer, FX chain and scratch targets. */
export class DeckPipeline {
  constructor(ctx, w, h) {
    this.ctx = ctx;
    const { gl, fmt } = ctx;
    this.clean = new Target(gl, w, h, fmt);
    this.fxState = new FxChainState(ctx, w, h);
    this.srcState = new ISFState();
    this.srcId = null;
    this.output = this.clean.tex;
  }

  resize(w, h) {
    this.clean.resize(w, h);
    this.fxState.resize(w, h);
  }

  /**
   * Draws the deck's ISF source into the clean target. Returns false if the source is not an
   * ISF source or has not loaded yet (the caller handles mandala itself).
   */
  renderSource(deck, frame) {
    const { ctx } = this;
    const prog = ctx.library.get('sources', (deck.source || '').toLowerCase());
    if (!prog) return false;
    const { gl } = ctx;
    this.clean.bind();
    gl.clearColor(0, 0, 0, 0);
    gl.clear(gl.COLOR_BUFFER_BIT);
    const view = prog.is3D ? undefined
      : { zoom: ctx.scalar(deck.viewZoom, 1.0), rotateZ: ctx.scalar(deck.viewRotateZ, 0.0) };
    renderISF(ctx, prog, this.srcState, {
      targetFBO: this.clean.fbo, width: this.clean.w, height: this.clean.h,
      params: evalParams(ctx, prog, deck),
      frame: { ...frame, alpha: ctx.scalar(deck.globalAlpha, 1.0), view },
    });
    return true;
  }

  /** Runs the FX chain over the clean target and records the deck's final texture. */
  renderFx(deck, frame) {
    this.output = this.fxState.run(deck.fx, deck.fxDryWet, deck.fxEnabled, this.clean.tex, frame);
    return this.output;
  }
}

/** Per-chain scratch targets and slot instances. Used for deck chains and the master chain. */
export class FxChainState {
  constructor(ctx, w, h) {
    this.ctx = ctx;
    const { gl, fmt } = ctx;
    this.ping = new Target(gl, w, h, fmt);
    this.pong = new Target(gl, w, h, fmt);
    this.out = new Target(gl, w, h, fmt);
    this.slotIds = new Array(FX_SLOTS).fill(null);
    this.slotStates = new Array(FX_SLOTS).fill(null);
  }

  resize(w, h) { this.ping.resize(w, h); this.pong.resize(w, h); this.out.resize(w, h); }

  drawTexture(tex) {
    const ctx = this.ctx;
    const { gl } = ctx;
    gl.useProgram(ctx.blit.program);
    gl.bindVertexArray(ctx.quadVAO);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, tex);
    gl.uniform1i(ctx.blit.uTexture, 0);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  }

  /** Dry/wet blend: draws `tex` over the bound target weighted by (1 - wet). */
  drawDry(tex, wet) {
    const { gl } = this.ctx;
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.CONSTANT_ALPHA, gl.ONE_MINUS_CONSTANT_ALPHA);
    gl.blendColor(0, 0, 0, 1 - wet);
    this.drawTexture(tex);
    gl.disable(gl.BLEND);
  }

  /**
   * Port of Renderer.renderFxChainPass. Returns the texture holding the chain output, which is
   * `cleanTex` itself when the chain is off, all slots are dry, or nothing has loaded.
   */
  run(slots, chainDryWet, chainEnabled, cleanTex, frame) {
    const { ctx } = this;
    const { gl } = ctx;
    const chainWet = chainEnabled === false ? 0 : ctx.scalar(chainDryWet, 1.0);
    if (chainWet <= 0 || !Array.isArray(slots)) return cleanTex;

    let input = cleanTex;
    let write = this.ping, read = this.pong;
    let rendered = false;

    for (let i = 0; i < FX_SLOTS; i++) {
      const slot = slots[i];
      if (!slot || !slot.id || slot.enabled === false) continue;
      const wet = ctx.scalar(slot.dryWet, 1.0);
      if (wet <= 0) continue;
      const prog = ctx.library.get('filters', slot.id);
      if (!prog) continue;
      if (this.slotIds[i] !== slot.id) {
        this.slotStates[i]?.dispose(gl);
        this.slotStates[i] = new ISFState();
        this.slotIds[i] = slot.id;
      }

      write.bind();
      gl.disable(gl.BLEND);
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT);
      renderISF(ctx, prog, this.slotStates[i], {
        targetFBO: write.fbo, width: write.w, height: write.h,
        params: evalParams(ctx, prog, slot.params),
        frame,
        images: prog.inputName ? { [prog.inputName]: input } : {},
      });
      write.bind();
      if (wet < 1) this.drawDry(input, wet);

      input = write.tex;
      [write, read] = [read, write];
      rendered = true;
    }
    if (!rendered) return cleanTex;

    this.out.bind();
    gl.disable(gl.BLEND);
    gl.clearColor(0, 0, 0, 0);
    gl.clear(gl.COLOR_BUFFER_BIT);
    this.drawTexture(input);
    if (chainWet < 1) this.drawDry(cleanTex, chainWet);
    return this.out.tex;
  }
}

/** Transition + composite + master FX. Returns the texture to hand to the CRT pass. */
export class MixerPipeline {
  constructor(ctx, w, h) {
    this.ctx = ctx;
    const { gl, fmt } = ctx;
    this.blend = new Target(gl, w, h, fmt);
    this.composite = new Target(gl, w, h, fmt);
    this.fx = new FxChainState(ctx, w, h);
    this.transId = null;
    this.transState = new ISFState();
  }

  resize(w, h) { this.blend.resize(w, h); this.composite.resize(w, h); this.fx.resize(w, h); }

  render(mixer, texA, texB, texBG, bgAlpha, masterLevel, frame) {
    const { ctx } = this;
    const { gl } = ctx;
    const progress = Math.min(1, Math.max(0, ctx.scalar(mixer.balance, 0)));

    // Transition A -> B, falling back to the default crossfade while the chosen one loads.
    let transId = mixer.transition || 'linear_crossfade';
    let prog = ctx.library.get('transitions', transId);
    if (!prog && transId !== 'linear_crossfade') { transId = 'linear_crossfade'; prog = ctx.library.get('transitions', transId); }
    this.blend.bind();
    gl.disable(gl.BLEND);
    gl.clearColor(0, 0, 0, 0);
    gl.clear(gl.COLOR_BUFFER_BIT);
    if (prog) {
      if (this.transId !== transId) {
        this.transState.dispose(gl);
        this.transState = new ISFState();
        this.transId = transId;
      }
      const params = evalParams(ctx, prog, transId === (mixer.transition || 'linear_crossfade') ? mixer.transitionParams : null);
      params.progress = progress;
      renderISF(ctx, prog, this.transState, {
        targetFBO: this.blend.fbo, width: this.blend.w, height: this.blend.h, params, frame,
        images: { [prog.startName]: texA, [prog.endName]: texB },
      });
    }

    // Composite over BG with levels
    this.composite.bind();
    gl.clearColor(0, 0, 0, 1);
    gl.clear(gl.COLOR_BUFFER_BIT);
    const m = ctx.mixerProg;
    gl.useProgram(m.program);
    gl.bindVertexArray(ctx.quadVAO);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, this.blend.tex);
    gl.uniform1i(m.uTex1, 0);
    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_2D, texBG);
    gl.uniform1i(m.uTexBG, 1);
    gl.uniform1f(m.uProgress, progress);
    gl.uniform1f(m.uBgAlpha, bgAlpha);
    gl.uniform1f(m.uLevelA, ctx.scalar(mixer.levelA, 1.0));
    gl.uniform1f(m.uLevelB, ctx.scalar(mixer.levelB, 1.0));
    gl.uniform1f(m.uLevelBG, ctx.scalar(mixer.levelBG, 1.0));
    gl.uniform1f(m.uMasterLevel, masterLevel);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    gl.activeTexture(gl.TEXTURE0);

    return this.fx.run(mixer.fx, mixer.fxDryWet, mixer.fxEnabled, this.composite.tex, frame);
  }
}

export { lookupParam };
