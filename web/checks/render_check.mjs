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

// Every shipped autopilot preset, loaded the way autopilot.js loads it.
for (const f of fs.readdirSync(path.join(web, 'presets')).sort()) {
  if (f.endsWith('.lsd')) sources.push({ name: 'preset_' + f.replace('.lsd', ''), preset: 'presets/' + f, settleMs: 2500 });
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
if (rep.library) { console.log('library loaded:', rep.library.loaded.join(', ')); if (rep.library.failed.length) { console.log('library FAILED:', rep.library.failed.join(', ')); bad++; } }
console.log(`PNGs in ${outDir}`);
done(bad ? 1 : 0);
