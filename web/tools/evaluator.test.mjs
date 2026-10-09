// Replays the desktop golden vectors against web/evaluator.js: node --test web/tools/evaluator.test.mjs
// Vectors come from WebEvaluatorVectorsTest (Kotlin); regenerate them there, never by hand.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { evaluateParameter, makeEvalContext, resolveBase, validateModulator, paramSpec } from '../evaluator.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const vectors = JSON.parse(fs.readFileSync(path.join(here, 'evaluator_vectors.json'), 'utf8'));

const TOL = 2e-4; // desktop computes in Float32

function ctxFrom(c, followers) {
  return { ...makeEvalContext(), ...c, followers };
}

for (const c of vectors.cases) {
  test(c.name, () => {
    c.points.forEach((pt, i) => {
      const got = evaluateParameter(c.param, ctxFrom(pt.ctx, new WeakMap()), c.spec);
      assert.ok(Math.abs(got - pt.expected) <= TOL, `point ${i}: got ${got}, desktop ${pt.expected} (ctx ${JSON.stringify(pt.ctx)})`);
    });
  });
}

for (const s of vectors.sequences) {
  test(`sequence: ${s.name}`, () => {
    const followers = new WeakMap();
    s.frames.forEach((f, i) => {
      const got = evaluateParameter(s.param, ctxFrom(f.ctx, followers), s.spec);
      assert.ok(Math.abs(got - f.expected) <= TOL, `frame ${i}: got ${got}, desktop ${f.expected}`);
    });
  });
}

test('rejects what the desktop rejects', () => {
  assert.ok(validateModulator({ sourceId: 'lfo', operator: 'SUB' }).length);
  assert.ok(validateModulator({ sourceId: 'lfo', waveform: 'SAW' }).length);
  assert.ok(validateModulator({ sourceId: 'lfo', operator: 'ADD', waveform: 'SINE', genUnit: 'BEAT' }).length === 0);
  // an invalid modulator is skipped, not thrown on
  const v = evaluateParameter({ baseValue: 0.5, modulators: [{ sourceId: 'lfo', operator: 'SUB', depth: 1 }] }, makeEvalContext(), { min: 0, max: 1 });
  assert.equal(v, 0.5);
});

test('an explicit zero base survives', () => {
  assert.equal(evaluateParameter({ baseValue: 0, modulators: [] }, makeEvalContext(), { min: 0, max: 1 }, 0.7), 0);
  assert.equal(evaluateParameter(0, makeEvalContext(), { min: -1, max: 1 }, 0.7), 0);
});

test('resolveBase honours randomizeBase', () => {
  assert.equal(resolveBase({ baseValue: 0.3, baseMin: 0, baseMax: 1, randomizeBase: false }), 0.3);
  assert.equal(resolveBase({ baseValue: 0.3, baseMin: 0.2, baseMax: 0.2, randomizeBase: true }), 0.3);
  assert.equal(resolveBase({ baseValue: 0.3, baseMin: 2, baseMax: 4, randomizeBase: true }, () => 0.5), 3);
});

test('paramSpec mirrors ISFInput.discreteSteps', () => {
  assert.deepEqual(paramSpec({ NAME: 'a', TYPE: 'float' }), { min: 0, max: 1, steps: null });
  assert.deepEqual(paramSpec({ NAME: 'a', TYPE: 'float', MIN: 0, MAX: 1, STEP: 0.25 }), { min: 0, max: 1, steps: 5 });
  assert.equal(paramSpec({ NAME: 'a', TYPE: 'float', MIN: 0, MAX: 1, STEP: 0.3 }).steps, null);
  assert.equal(paramSpec({ NAME: 'a', TYPE: 'long', MIN: 0, MAX: 3 }).steps, 4);
  assert.equal(paramSpec({ NAME: 'a', TYPE: 'long', VALUES: [0, 1, 2], LABELS: ['x', 'y', 'z'] }).steps, 3);
  assert.equal(paramSpec({ NAME: 'a', TYPE: 'image' }), null);
});
