// GPU cost per source on this machine: node web/checks/gpu_bench.mjs [WxH ...]
// Renders each source (its first web-* preset) at the given sizes in headless Firefox with gl.finish()
// after every frame and prints ms per frame, so it measures the GPU, not the (throttled) frame timer.
// Sources over 33 ms (under 30 fps) at the internal render size are too heavy for this machine.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sizes = process.argv.slice(2).length ? process.argv.slice(2) : ['1280x720', '768x432'];
const catalog = JSON.parse(fs.readFileSync(path.join(web, 'catalog.json'), 'utf8'));
const presetFiles = fs.readdirSync(path.join(web, 'presets')).filter((f) => f.startsWith('web-')).sort();
const bySource = new Map();
for (const f of presetFiles) {
  const src = JSON.parse(fs.readFileSync(path.join(web, 'presets', f), 'utf8')).visualSourceType;
  if (!bySource.has(src) && catalog.sources.some((s) => s.id === src)) bySource.set(src, f.replace('.lsd', ''));
}
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json' };
let resolveReport;
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname === '/report') { let b = ''; req.on('data', (c) => (b += c)); req.on('end', () => { res.end('ok'); resolveReport(JSON.parse(b)); }); return; }
  const p = path.normalize(path.join(web, decodeURIComponent(url.pathname)));
  if (!p.startsWith(web + path.sep) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', mime[path.extname(p)] || 'text/plain');
  res.end(fs.readFileSync(p));
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const table = new Map();
let renderer = '';
for (const size of sizes) {
  const [w, h] = size.split('x').map(Number);
  const reported = new Promise((r) => { resolveReport = r; });
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'lsd-ff-'));
  fs.writeFileSync(path.join(profile, 'user.js'), 'user_pref("webgl.sanitize-unmasked-renderer", false);\n');
  const ff = spawn('firefox', ['--headless', '--no-remote', '--profile', profile,
    `http://127.0.0.1:${server.address().port}/checks/gpu_bench.html?w=${w}&h=${h}&presets=${[...bySource.values()].join(',')}`], { stdio: 'ignore' });
  const rep = await Promise.race([reported, new Promise((r) => setTimeout(() => r({ fatal: 'timeout' }), 300000))]);
  ff.kill('SIGKILL');
  fs.rmSync(profile, { recursive: true, force: true });
  if (rep.fatal) { console.error(size, rep.fatal); process.exit(1); }
  renderer = rep.renderer;
  for (const r of rep.results) table.set(r.source, { ...(table.get(r.source) ?? {}), [size]: r.ms });
}
server.close();
console.log(`GPU: ${renderer}   (ms per frame, source only; 33 ms = 30 fps)`);
console.log('source'.padEnd(22) + sizes.map((s) => s.padStart(11)).join(''));
for (const [src, row] of [...table].sort((a, b) => b[1][sizes[0]] - a[1][sizes[0]])) {
  console.log(src.padEnd(22) + sizes.map((s) => row[s].toFixed(1).padStart(11)).join(''));
}
