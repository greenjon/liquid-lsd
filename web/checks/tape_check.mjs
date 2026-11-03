// Headless TAPES check: node web/checks/tape_check.mjs
// Serves web/ (with Range requests, as Apache does), drives checks/tape_check.html (the real page in an iframe)
// in headless Firefox, with a short Ogg file standing in for an archive show. Autoplay is allowed so sound really
// flows through the analysis graph, and radio.spaz.org resolves to nowhere so the check never reaches the station.
// Needs `firefox` on PATH.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.woff2': 'font/woff2', '.json': 'application/json', '.css': 'text/css', '.ogg': 'audio/ogg' };
let resolveReport;
const reported = new Promise((r) => { resolveReport = r; });
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname === '/report') {
    let b = ''; req.on('data', (c) => (b += c)); req.on('end', () => { res.end('ok'); resolveReport(JSON.parse(b)); });
    return;
  }
  const p = path.normalize(path.join(web, decodeURIComponent(url.pathname)));
  if (!p.startsWith(web + path.sep) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.statusCode = 404; res.end(); return; }
  res.setHeader('Content-Type', mime[path.extname(p)] || 'text/plain');
  res.setHeader('Accept-Ranges', 'bytes');
  const file = fs.readFileSync(p);
  const m = (req.headers.range || '').match(/^bytes=(\d+)-(\d*)$/);
  if (m) {
    const start = Number(m[1]), end = m[2] ? Math.min(Number(m[2]), file.length - 1) : file.length - 1;
    res.statusCode = 206;
    res.setHeader('Content-Range', `bytes ${start}-${end}/${file.length}`);
    res.end(file.subarray(start, end + 1));
    return;
  }
  res.end(file);
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'lsd-ff-'));
fs.writeFileSync(path.join(profile, 'user.js'), [
  'user_pref("media.autoplay.default", 0);',
  'user_pref("media.autoplay.blocking_policy", 0);',
  'user_pref("network.dns.localDomains", "radio.spaz.org");',
].join('\n'));
const ff = spawn('firefox', ['--headless', '--no-remote', '--profile', profile,
  `http://127.0.0.1:${server.address().port}/checks/tape_check.html`], { stdio: 'ignore' });
const rep = await Promise.race([reported, new Promise((r) => setTimeout(() => r({ fatal: 'timeout', results: [] }), 120000))]);
ff.kill('SIGKILL');
server.close();
fs.rmSync(profile, { recursive: true, force: true });
let bad = rep.fatal ? 1 : 0;
if (rep.fatal) console.log('FATAL:', rep.fatal);
for (const r of rep.results) {
  if (!r.ok) bad++;
  console.log(`${r.ok ? 'ok  ' : 'FAIL'} ${r.name}${r.detail && !r.ok ? ` (${r.detail})` : ''}`);
}
process.exit(bad ? 1 : 0);
