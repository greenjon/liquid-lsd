// Headless side feed check: node web/checks/sidefeed_check.mjs
// Serves web/ and a fake Icecast mount (/stream: checks/stream_fixture.ogg, its header pages at once and the
// rest at about real time), and drives checks/sidefeed_check.html in headless Firefox. Needs `firefox` on PATH.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.ogg': 'audio/ogg' };
const fixture = fs.readFileSync(path.join(web, 'checks', 'stream_fixture.ogg'));
let resolveReport;
const reported = new Promise((r) => { resolveReport = r; });
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname === '/report') {
    let b = ''; req.on('data', (c) => (b += c)); req.on('end', () => { res.end('ok'); resolveReport(JSON.parse(b)); });
    return;
  }
  if (url.pathname === '/stream') {
    res.setHeader('Content-Type', 'application/ogg');
    const head = 58 + 3868;                      // the fixture's two header pages
    const rate = (fixture.length - head) / 6;    // bytes per second of audio
    res.write(fixture.subarray(0, head));
    let o = head;
    const timer = setInterval(() => {
      const n = Math.ceil(rate / 10);
      res.write(fixture.subarray(o, o + n));
      o += n;
      if (o >= fixture.length) { clearInterval(timer); res.end(); }
    }, 100);
    req.on('close', () => clearInterval(timer));
    return;
  }
  const p = path.normalize(path.join(web, decodeURIComponent(url.pathname)));
  if (!p.startsWith(web + path.sep) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.statusCode = 404; res.end(); return; }
  res.setHeader('Content-Type', mime[path.extname(p)] || 'text/plain');
  res.end(fs.readFileSync(p));
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'lsd-ff-'));
fs.writeFileSync(path.join(profile, 'user.js'), [
  'user_pref("media.autoplay.default", 0);',
  'user_pref("media.autoplay.blocking_policy", 0);',
].join('\n'));
const ff = spawn('firefox', ['--headless', '--no-remote', '--profile', profile,
  `http://127.0.0.1:${server.address().port}/checks/sidefeed_check.html`], { stdio: 'ignore' });
const rep = await Promise.race([reported, new Promise((r) => setTimeout(() => r({ fatal: 'timeout', results: [] }), 60000))]);
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
