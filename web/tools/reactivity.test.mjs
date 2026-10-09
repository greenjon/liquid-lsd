// Do the web-* presets and FX chains react the way their tags promise?
// node --test web/tools/reactivity.test.mjs
//
// Evaluates every parameter through the real evaluator while sweeping one synthetic signal at a
// time (beat position, bass level, kick flux, ...) and checks that at least one parameter moves.
// `bass-gated` additionally requires a parameter that is flat below its threshold (the signal at
// 0.2 gives the same value as silence) yet moves when the signal is full. Tags are set in
// scripts/gen_web_presets.py. This tests the modulation wiring, not how it looks: look at the
// frames from `node web/checks/render_check.mjs` for that.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { evaluateParameter, makeEvalContext, paramSpec } from '../evaluator.js';
import { parseHeader } from '../isf.js';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const readJson = (p) => JSON.parse(fs.readFileSync(path.join(web, p), 'utf8'));
const catalog = readJson('catalog.json');
const header = (entry) => parseHeader(fs.readFileSync(path.join(web, entry.file), 'utf8'));
const specOf = (entry, name) => {
  const input = header(entry).INPUTS.find((i) => i.NAME === name);
  return input ? paramSpec(input) : undefined;
};

/** [{label, dto, spec}] for every parameter of a preset or chain. */
function targets(file, dto) {
  const out = [];
  if (dto.visualSourceType) {
    const entry = catalog.sources.find((s) => s.id === dto.visualSourceType);
    for (const [name, p] of Object.entries(dto.parameters)) {
      const spec = entry ? specOf(entry, name) : (name === 'Lobes' ? { min: 3, max: 26, steps: null } : { min: 0, max: 1, steps: null });
      out.push({ label: `${dto.visualSourceType}.${name}`, dto: p, spec });
    }
  } else {
    for (const s of dto.slots) {
      if (!s) continue;
      const entry = catalog.filters.find((f) => f.id === s.filterId);
      for (const [name, p] of Object.entries(s.parameters)) out.push({ label: `${s.filterId}.${name}`, dto: p, spec: specOf(entry, name) });
    }
  }
  return out;
}

const SILENT = { audio_amp: 0, audio_bass: 0, audio_mid: 0, audio_high: 0, audio_flux_amp: 0, audio_flux_bass: 0, audio_flux_mid: 0, audio_flux_high: 0 };

/** Parameter values with the given signals held for 90 frames (so envelope followers settle). */
function values(list, { beats = 0, cv = {} } = {}) {
  const ctx = { ...makeEvalContext(), followers: new WeakMap(), bpm: 120, dt: 1 / 60 };
  ctx.cv = { ...SILENT, ...cv, beatSine: Math.sin(beats * 2 * Math.PI), bpm: 120 };
  ctx.beats = beats;
  let out;
  for (let f = 0; f < 90; f++) {
    ctx.frame = f; ctx.time = f / 60;
    out = list.map((t) => evaluateParameter(t.dto, ctx, t.spec));
  }
  return out;
}

const spread = (series) => series[0].map((_, i) => {
  const col = series.map((s) => s[i]);
  return Math.max(...col) - Math.min(...col);
});
const moved = (list, series, eps = 1e-3) => spread(series).map((d, i) => (d > eps ? list[i].label : null)).filter(Boolean);

function sweepBeats(list, from, to, step) {
  const series = [];
  for (let b = from; b <= to; b += step) series.push(values(list, { beats: b }));
  return series;
}

const SIGNALS = {
  bass: 'audio_bass', kick: 'audio_flux_bass', mid: 'audio_mid', amp: 'audio_amp',
};

function check(name, list, tags) {
  const finite = values(list);
  assert.ok(finite.every(Number.isFinite), `${name}: non-finite value at silence`);

  if (tags.includes('beat')) {
    const m = moved(list, sweepBeats(list, 0, 1, 1 / 16));
    assert.ok(m.length, `${name}: tagged beat but nothing moves within one beat`);
  }
  if (tags.includes('bar')) {
    const m = moved(list, sweepBeats(list, 0, 64, 0.5));
    assert.ok(m.length, `${name}: tagged bar but nothing moves over 64 beats`);
  }
  for (const [tag, source] of Object.entries(SIGNALS)) {
    if (!tags.includes(tag)) continue;
    const m = moved(list, [values(list), values(list, { cv: { [source]: 1 } })]);
    assert.ok(m.length, `${name}: tagged ${tag} but nothing moves with ${source}`);
  }
  if (tags.includes('high')) {
    const m = moved(list, [values(list), values(list, { cv: { audio_high: 1 } }), values(list, { cv: { audio_flux_high: 1 } })]);
    assert.ok(m.length, `${name}: tagged high but nothing moves with audio_high or audio_flux_high`);
  }
  if (tags.includes('bass-gated')) {
    const silent = values(list), low = values(list, { cv: { audio_bass: 0.2 } }), full = values(list, { cv: { audio_bass: 1 } });
    // Flat below the threshold, moving above it, and silent at its own base (a gate on a non-minimum
    // base dips below the base at silence; see the note in scripts/gen_web_presets.py).
    const base = (t) => (typeof t.dto === 'number' ? t.dto : t.dto.baseValue);
    const gated = list.filter((t, i) => Math.abs(low[i] - silent[i]) < 1e-6 && Math.abs(full[i] - silent[i]) > 1e-3
      && Math.abs(silent[i] - base(t)) < 1e-6).map((t) => t.label);
    assert.ok(gated.length, `${name}: tagged bass-gated but no parameter that sits at its base until bass passes a threshold`);
  }
}

const presetsDir = path.join(web, 'presets');
for (const f of fs.readdirSync(presetsDir).filter((n) => n.startsWith('web-') && n.endsWith('.lsd')).sort()) {
  const dto = readJson(`presets/${f}`);
  const tags = (dto.tags ?? []).filter((t) => t !== 'web');
  if (tags.length) test(`preset ${dto.name} reacts as tagged (${tags.join(', ')})`, () => check(dto.name, targets(f, dto), tags));
}
for (const c of catalog.fxChains.filter((e) => e.file.startsWith('fxchains/'))) {
  const dto = readJson(c.file);
  const tags = (dto.tags ?? []).filter((t) => t !== 'web');
  const reactive = tags.filter((t) => ['beat', 'bar', 'bass', 'kick', 'mid', 'high', 'amp', 'bass-gated'].includes(t));
  if (reactive.length) test(`chain ${dto.name} reacts as tagged (${reactive.join(', ')})`, () => check(dto.name, targets(c.file, dto), reactive));
}
