// Authored web content must load and render: node --test web/tools/content.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateAll } from './validate_content.mjs';
import { parseDeckPreset, parseFxChain, parseTransition } from '../preset.js';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const readJson = (p) => JSON.parse(fs.readFileSync(path.join(web, p), 'utf8'));

test('every preset, FX chain and transition preset is valid for the web', (t) => {
  const warnings = [];
  assert.deepEqual(validateAll(warnings), []);
  for (const w of warnings) t.diagnostic(`desktop content references a parameter its shader no longer has: ${w}`);
});

test('every playlist line names a preset and (optionally) a known FX chain', () => {
  const cat = readJson('catalog.json');
  for (const file of fs.readdirSync(path.join(web, 'playlists')).filter((n) => n.endsWith('.lsdplay'))) {
    const lines = fs.readFileSync(path.join(web, 'playlists', file), 'utf8').split(/\r?\n/)
      .map((l) => l.trim()).filter((l) => l && !l.startsWith('#'));
    for (const line of lines) {
      const [preset, chain] = line.split('|').map((p) => p.trim());
      assert.ok(fs.existsSync(path.join(web, 'presets', `${preset}.lsd`)), `${file}: no preset ${preset}`);
      if (chain && chain !== 'none') assert.ok(cat.fxChains.some((c) => c.id === chain), `${file}: no FX chain ${chain}`);
    }
  }
});

test('parseDeckPreset keeps ParameterDtos and resolves randomizeBase once', () => {
  const dto = {
    visualSourceType: 'dynamic_spiral',
    parameters: {
      Scale: { baseValue: 0.5, baseMin: 0, baseMax: 1, randomizeBase: false, modulators: [] },
      Glow: { baseValue: 0.1, baseMin: 0.2, baseMax: 0.4, randomizeBase: true, modulators: [] },
    },
    viewParameters: { viewZoom: { baseValue: 2, baseMin: 0.1, baseMax: 5, randomizeBase: false, modulators: [] } },
  };
  const deck = parseDeckPreset(dto, () => 0.5);
  assert.equal(deck.source, 'dynamic_spiral');
  assert.equal(deck.params.Scale.baseValue, 0.5);
  assert.ok(Math.abs(deck.params.Glow.baseValue - 0.3) < 1e-9);
  assert.equal(deck.params.Glow.randomizeBase, false);
  assert.equal(deck.viewZoom.baseValue, 2);
  assert.deepEqual(deck.fx, []);
});

test('parseFxChain keeps slot order and nulls; parseTransition falls back to the crossfade', () => {
  const chain = parseFxChain({ dryWet: { baseValue: 0.8 }, slots: [null, { filterId: 'bloom', dryWet: { baseValue: 1 }, parameters: { threshold: { baseValue: 0.3 } } }] });
  assert.equal(chain.fx[0], null);
  assert.equal(chain.fx[1].id, 'bloom');
  assert.equal(chain.fx[1].params.threshold.baseValue, 0.3);
  assert.equal(chain.fx[2], null);
  assert.equal(chain.fxDryWet.baseValue, 0.8);
  assert.deepEqual(parseTransition({}), { transition: 'linear_crossfade', transitionParams: {} });
  assert.equal(parseTransition({ slot: { filterId: 'vortex_swirl', parameters: {} } }).transition, 'vortex_swirl');
});
