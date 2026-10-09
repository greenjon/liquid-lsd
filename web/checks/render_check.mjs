// Headless render check: node web/checks/render_check.mjs [outDir]
// Serves web/, drives the app in headless Firefox through every ISF source plus mandala,
// writes PNGs to outDir (default /tmp/lsd-render-check) and fails on console errors or
// blank frames.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outDir = process.argv[2] || '/tmp/lsd-render-check';
fs.mkdirSync(outDir, { recursive: true });
const ids = ['dynamic_spiral', 'icosa_h3', 'hyper_slice', 'gyroid_hyperspace', 'chladni_cymatics',
  'celestial_engine', 'domain_warp_fluid'];
const sources = [{ name: 'mandala', deck: { source: 'mandala' } },
  ...ids.map((id) => ({ name: id, deck: { source: id } }))];

// Every catalog filter over a bright source, and every transition at the midpoint between two.
const catalog = JSON.parse(fs.readFileSync(path.join(web, 'catalog.json'), 'utf8'));
for (const f of catalog.filters) {
  sources.push({ name: 'fx_' + f.id, deck: { source: 'domain_warp_fluid', fx: [{ id: f.id, enabled: true, dryWet: 1, params: {} }] }, settleMs: 900 });
}
for (const t of catalog.transitions) {
  sources.push({ name: 'trans_' + t.id, deck: { source: 'domain_warp_fluid' }, deckB: { source: 'chladni_cymatics' },
    mixer: { balance: 0.5, transition: t.id }, settleMs: 900 });
}
sources.push({ name: 'master_fx', deck: { source: 'domain_warp_fluid' },
  mixer: { balance: 0, fx: [{ id: 'invert', enabled: true, dryWet: 1, params: {} }, null, null] }, settleMs: 900 });

// Live-broadcast replays: wire protocol v2 messages shaped like WebPresetSerializer output.
const slot = (id, params, dryWet = 1) => ({ id, dryWet, params });
const spiral = { source: 'dynamic_spiral', params: { Scale: 0.5, Speed: 0.5, Glow: 0.4, HueOffset: 0.2 },
  viewZoom: 1, viewRotateZ: 0, globalAlpha: 1, fxDryWet: 1,
  fx: { 0: slot('feedback', { fbDecay: 0.65, fbGain: 0.48, fbKaleido: 1 }), 1: null, 2: null } };
const mandala = { source: 'mandala', params: {}, viewZoom: 1, viewRotateZ: 0.3, globalAlpha: 1, fxDryWet: 1,
  fx: { 0: null, 1: null, 2: null },
  mandala: { uL1: 0.8, uL2: 0.6, uL3: 0.4, uL4: 0.2, uA: 3, uB: 4, uC: 5, uD: 7, uThickness: 0.02,
    uHueOffset: 0.1, uHueSweep: 3, uDepth: 0.35, uMaxR: 2 } };
const clock = { beats: 8, bpm: 126 };
const full = (deckA, deckB, mixer) => ({ type: 'state_full', v: 2, clock,
  preset: { deckA, deckB, deckBG: { empty: true }, mixer: { balance: 0, alpha: 1, levelA: 1, levelB: 1, levelBG: 1,
    transition: 'linear_crossfade', transitionParams: {}, fxDryWet: 1, fx: { 0: null, 1: null, 2: null }, ...mixer } } });
sources.push({ name: 'live_spiral_fx', wire: [full(spiral, { empty: true })], settleMs: 1500 });
sources.push({ name: 'live_mandala', wire: [full(mandala, { empty: true })], settleMs: 1200 });
sources.push({ name: 'live_delta_fx_and_crossfade', settleMs: 1500, wire: [
  full(spiral, mandala),
  { type: 'state_delta', clock, patch: { mixer: { balance: 0.5, fx: { 0: slot('invert', {}) } },
    deckA: { fx: { 0: { params: { fbDecay: 0.9 } } } } } },
] });
sources.push({ name: 'live_wrong_version_ignored', settleMs: 600, wire: [
  { type: 'state_full', v: 1, preset: { deckA: { source: 'mandala' } } }] });

// Every shipped autopilot preset, loaded the way autopilot.js loads it.
for (const f of fs.readdirSync(path.join(web, 'presets')).sort()) {
  if (f.endsWith('.lsd')) sources.push({ name: 'preset_' + f.replace('.lsd', ''), preset: '/presets/' + f, chain: 'web-trails', settleMs: 2500 });
}
// Desktop-shipped Mandala presets, unmodified (Lobes / Recipe Select / the recipe table)
for (const f of ['mandala-7', 'mandala-10']) {
  sources.push({ name: 'desktop_' + f, preset: `/repo/defaults/presets/${f}.lsd`, settleMs: 1500 });
}
// Every FX chain and transition preset the catalog lists
for (const c of catalog.fxChains) {
  sources.push({ name: 'chain_' + c.id, deck: { source: 'domain_warp_fluid' }, chain: c.id, settleMs: 1100 });
}
for (const t of catalog.transitionPresets) {
  sources.push({ name: 'transpreset_' + t.id, deck: { source: 'domain_warp_fluid' }, deckB: { source: 'chladni_cymatics' },
    mixer: { balance: 0.5 }, transitionPreset: t.id, settleMs: 1100 });
}

let resolveReport;
const reported = new Promise((r) => { resolveReport = r; });
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.css': 'text/css' };
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname === '/report') {
    let b = ''; req.on('data', (c) => (b += c)); req.on('end', () => { res.end('ok'); resolveReport(JSON.parse(b)); });
  } else if (url.pathname === '/sources') {
    res.end(JSON.stringify(sources));
  } else if (url.pathname.startsWith('/repo/')) {
    // Desktop defaults (defaults/presets, ...) so desktop-format files can be rendered as-is
    const p = path.normalize(path.join(path.resolve(web, '..'), decodeURIComponent(url.pathname.slice(6))));
    if (!p.startsWith(path.resolve(web, '..') + path.sep) || !fs.existsSync(p)) { res.statusCode = 404; return res.end(); }
    res.end(fs.readFileSync(p));
  } else {
    const p = path.normalize(path.join(web, url.pathname));
    if (!p.startsWith(web + path.sep) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.statusCode = 404; return res.end(); }
    res.setHeader('Content-Type', mime[path.extname(p)] || 'text/plain');
    res.end(fs.readFileSync(p));
  }
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'lsd-ff-'));
const ff = spawn('firefox', ['--headless', '--no-remote', '--profile', profile,
  `http://127.0.0.1:${server.address().port}/checks/render_check.html`], { stdio: 'ignore' });
const timer = setTimeout(() => { console.error('timeout waiting for Firefox'); done(1); }, 240000);
function done(code) {
  clearTimeout(timer); ff.kill('SIGKILL'); server.close();
  fs.rmSync(profile, { recursive: true, force: true }); process.exit(code);
}
const rep = await reported;
if (rep.fatal) console.error('FATAL:', rep.fatal);
let bad = rep.fatal ? 1 : 0;
for (const f of rep.frames || []) {
  fs.writeFileSync(path.join(outDir, f.name + '.png'), Buffer.from(f.png.split(',')[1], 'base64'));
  const blank = f.meanLuma < 0.5 && f.litFraction < 0.002;
  if (blank) bad++;
  if (f.glError) { bad++; console.log(`GL ERROR 0x${f.glError.toString(16)} after ${f.name}`); }
  console.log(`${blank ? 'BLANK' : 'ok   '} ${f.name.padEnd(20)} luma=${f.meanLuma.toFixed(1)} lit=${(f.litFraction * 100).toFixed(1)}%`);
}
const errs = [...new Set(rep.errors || [])].filter((e) => !/audio|stream|AudioContext|NotSupported|WebSocket/i.test(e));
for (const e of errs) console.log('console error:', e.split('\n').slice(0, 6).join(' / '));
if (errs.length) bad++;
if (rep.autopilot) {
  const a = rep.autopilot;
  console.log('autopilot start   :', JSON.stringify(a.start));
  console.log('autopilot advance :', JSON.stringify(a.advanced));
  if (!a.start.deckA.source || a.start.deckA.fx0 !== 'feedback' || !a.advanced.deckB.source || !a.advanced.transition) {
    console.log('AUTOPILOT did not load a preset with its pinned FX chain and a transition'); bad++;
  }
}
if (rep.library) { console.log('library loaded:', rep.library.loaded.join(', ')); if (rep.library.failed.length) { console.log('library FAILED:', rep.library.failed.join(', ')); bad++; } }
console.log(`PNGs in ${outDir}`);
done(bad ? 1 : 0);
