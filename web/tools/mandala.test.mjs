// Replays the desktop's recipe selection against web/mandala.js: node --test web/tools/mandala.test.mjs
// Vectors come from WebMandalaVectorsTest (Kotlin); regenerate them there, never by hand.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { MandalaRecipes } from '../mandala.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const read = (p) => JSON.parse(fs.readFileSync(path.join(here, p), 'utf8'));
const recipes = new MandalaRecipes(read('../mandala_recipes.json'));
const vectors = read('mandala_vectors.json');

test('recipe selection and hue cycles match the desktop for every vector', () => {
  const [iL, iSel, iSw, iA, iB, iC, iD, iP, iH] = vectors.fields.map((f) => vectors.fields.indexOf(f));
  assert.ok(vectors.rows.length > 100);
  for (const row of vectors.rows) {
    const r = recipes.pickRecipe(row[iL], row[iSel]);
    assert.deepEqual([r.a, r.b, r.c, r.d, r.petals], [row[iA], row[iB], row[iC], row[iD], row[iP]], `lobes ${row[iL]} select ${row[iSel]}`);
    assert.equal(recipes.hueSweepCycles(r.petals, row[iSw]), row[iH], `petals ${r.petals} sweep ${row[iSw]}`);
  }
});

test('uniforms normalise arm lengths to the target radius and scale thickness', () => {
  const values = { Lobes: 7, 'Recipe Select': 0, L1: 1, L2: 1, L3: 0, L4: 0, Thickness: 1, 'Hue Offset': 0.25, 'Hue Sweep': 0, Depth: 0.5 };
  const u = recipes.uniforms((name, fb) => values[name] ?? fb);
  assert.equal(u.uL1 + u.uL2 + u.uL3 + u.uL4, 2);
  assert.equal(u.uThickness, 0.035);
  assert.equal(u.uMaxR, 2);
  assert.equal(u.uHueOffset, 0.25);
});

test('all-zero arms draw nothing (maxR collapses, arms stay zero)', () => {
  const u = recipes.uniforms((name, fb) => (/^L\d$/.test(name) ? 0 : fb));
  assert.deepEqual([u.uL1, u.uL2, u.uL3, u.uL4], [0, 0, 0, 0]);
  assert.equal(u.uMaxR, 0.001);
});
