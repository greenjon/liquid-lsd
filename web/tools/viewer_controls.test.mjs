// node --test web/tools/viewer_controls.test.mjs : ROLL, AUTO ROLL, EFFECTS, BG CHANGES and channel skip,
// on the real autopilot over the shipped playlists, presets and chains (fetch reads web/ from disk).
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
globalThis.location = { search: '?nolive' };   // no relay
globalThis.fetch = async (p) => {
  const file = path.join(web, String(p).replace(/^\//, ''));
  if (!fs.existsSync(file)) return { ok: false, status: 404 };
  const text = fs.readFileSync(file, 'utf8');
  return { ok: true, status: 200, text: async () => text, json: async () => JSON.parse(text) };
};

const ap = await import('../autopilot.js');
const { autopilotState } = ap;
await ap.startAutopilot();
ap.setHeavyAllowed(true);

let beats = 0;
const settle = () => new Promise((r) => setImmediate(r));
/** Ticks the autopilot to beat `to` in quarter beats, letting scene builds finish between ticks. */
async function runTo(to) {
  while (beats < to) {
    beats = Math.min(to, beats + 0.25);
    ap.tickAutopilot(0.1, beats, {});
    await settle();
  }
}
const onAir = () => (ap.nowShowing().channel > 0 && autopilotState.mixer.balance >= 0.5 ? autopilotState.deckB : autopilotState.deckA);
const rolled = (deck) => Object.entries(deck.raw.parameters).filter(([, p]) => p.randomizeBase).map(([k]) => k);

test('every curated scene and chain has something to roll', () => {
  for (const dir of ['presets', 'fxchains']) {
    for (const f of fs.readdirSync(path.join(web, dir)).filter((n) => n.startsWith('web-'))) {
      const dto = JSON.parse(fs.readFileSync(path.join(web, dir, f), 'utf8'));
      const bags = [dto.parameters, ...(dto.slots ?? []).map((s) => s?.parameters)].filter(Boolean);
      const n = bags.flatMap((b) => Object.values(b)).filter((p) => p.randomizeBase).length;
      assert.ok(n > 0, `${f}: no parameter rolls`);
      for (const b of bags) for (const [k, p] of Object.entries(b)) {
        if (p.randomizeBase) assert.ok(p.baseMin <= p.baseValue && p.baseValue <= p.baseMax, `${f} ${k}: tuned value outside its roll span`);
      }
    }
  }
});

test('ROLL glides continuous parameters to new values and lands on the next bar line; stepped ones snap there', async () => {
  ap.setHold(true);                 // keep this scene for the rest of the tests
  await runTo(6);
  const deck = onAir();
  const names = rolled(deck);
  assert.ok(names.length, 'the scene on air has rolling parameters');
  const stepName = names[0];
  ap.setSteppedLookup((kind, id, name) => kind === 'sources' && name === stepName);
  const before = Object.fromEntries(names.map((n) => [n, deck.params[n].baseValue]));
  const glider = names[1];
  try {
    assert.equal(ap.rollScene(), true);  // from beat 6: lands on the bar line at 8 (at least 2 beats away)
    await runTo(7);
    assert.equal(deck.params[stepName].baseValue, before[stepName], 'a stepped parameter holds until the bar line');
    const mid = deck.params[glider].baseValue;
    await runTo(8);
    const to = deck.params[glider].baseValue;
    if (to !== before[glider]) assert.ok(Math.abs(mid - before[glider]) < Math.abs(to - before[glider]) + 1e-12 && mid !== to, 'halfway, a continuous one is on its way');
  } finally { ap.setSteppedLookup(() => false); }
  await runTo(9);
  const changed = names.filter((n) => deck.params[n].baseValue !== before[n]);
  assert.ok(changed.length >= names.length / 2, `most rolled parameters moved (${changed.length}/${names.length})`);
  for (const n of names) {
    const p = deck.raw.parameters[n];
    assert.ok(deck.params[n].baseValue >= p.baseMin - 1e-9 && deck.params[n].baseValue <= p.baseMax + 1e-9, `${n} within its span`);
  }
});

test('AUTO ROLL rolls the held scene every N bars', async () => {
  const deck = onAir();
  const n = rolled(deck)[0];
  ap.setAutoRollBars(4);
  const start = beats;
  let changes = 0, last = deck.params[n].baseValue;
  for (let b = start; b < start + 40; b += 0.25) {
    await runTo(b);
    if (deck.params[n].baseValue !== last) { last = deck.params[n].baseValue; changes++; }
  }
  ap.setAutoRollBars(0);
  assert.ok(changes >= 2, `rolled at least twice over 10 bars (saw ${changes} moves)`);
});

test('EFFECTS OFF strips the chains on air and F reports it; AUTO brings chains back', async () => {
  await ap.setEffects(false);
  assert.deepEqual(onAir().fx, []);
  assert.equal(ap.nowShowing().effects, false);
  assert.equal(await ap.nextFxChain(), 'off');
  await ap.setEffects(true);
  assert.ok(onAir().fx.some(Boolean) || onAir().pinned === 'none', 'a chain is back on the scene');
});

test('BG CHANGES OFF fades the background out and stops rendering it; back on, one fades in', async () => {
  assert.ok(autopilotState.deckBG, 'a background is on');
  ap.setBackgroundMode('off');
  await runTo(beats + 6);
  assert.equal(autopilotState.deckBG, null);
  assert.equal(await ap.stepBackground(1), 'off');
  ap.setBackgroundMode('hold');
  await runTo(beats + 6);
  assert.ok(autopilotState.deckBG);
  assert.equal(autopilotState.bgAlpha, 1);
  const was = ap.nowShowing().background;
  const line = await ap.stepBackground(1);
  await runTo(beats + 3);
  assert.notEqual(ap.nowShowing().background, was);
  assert.equal(ap.nowShowing().background, line.split('|')[0].trim());
  ap.setBackgroundMode('scenes');
});

test('X skips the channel on air (it changes on), keeps it out of the rotation, and typing its number reaches it', async () => {
  ap.setHold(false);
  const ch = ap.nowShowing().channel;
  const r = ap.toggleSkip();
  assert.equal(r.action, 'skipped');
  await runTo(beats + 6);
  assert.notEqual(ap.nowShowing().channel, ch, 'tuned away');
  assert.deepEqual(ap.skippedChannels().map((c) => c.channel), [ch]);
  // CH up/down and R walk past it
  for (let i = 0; i < 20; i++) { await ap.randomForeground(); await runTo(beats + 6); assert.notEqual(ap.nowShowing().channel, ch); }
  assert.equal(ap.tuneTo(ch), 'ok');
  await runTo(beats + 8);
  assert.equal(ap.nowShowing().channel, ch);
  assert.equal(ap.nowShowing().skipped, true);
  assert.equal(ap.toggleSkip().action, 'restored');
  assert.deepEqual(ap.skippedChannels(), []);
  assert.equal(ap.tuneTo(9999), 'range');
});

test('skipping stops with SKIP_FLOOR channels left; RESTORE ALL brings them back', () => {
  const names = ap.prunedSkips();
  assert.deepEqual(names, []);
  const total = ap.nowShowing().channels;
  ap.setSkipped((fs.readFileSync(path.join(web, 'playlists', 'default.lsdplay'), 'utf8').split('\n')
    .map((l) => l.trim()).filter((l) => l && !l.startsWith('#')).map((l) => l.split('|')[0].trim())
    .filter((n) => n !== ap.nowShowing().name).slice(0, total - ap.SKIP_FLOOR)));
  assert.equal(ap.toggleSkip().action, 'floor');
  ap.restoreAllChannels();
  assert.deepEqual(ap.skippedChannels(), []);
  ap.setSkipped(['not-a-preset']);
  assert.deepEqual(ap.prunedSkips(), [], 'names the playlist no longer has are dropped');
});
