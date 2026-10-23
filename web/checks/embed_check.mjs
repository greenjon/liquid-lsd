// Headless embed-mode check: node web/checks/embed_check.mjs
// Serves web/, drives checks/embed_check.html (the real page, ?embed, in an iframe) in headless Firefox and fails on
// any embed behaviour that does not work (feed, options, pause). Needs `firefox` on PATH.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.css': 'text/css' };
let resolveReport;
const reported = new Promise((r) => { resolveReport = r; });
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname === '/report') {
    let b = ''; req.on('data', (c) => (b += c)); req.on('end', () => { res.end('ok'); resolveReport(JSON.parse(b)); });
    return;
  }
  const p = path.normalize(path.join(web, decodeURIComponent(url.pathname)));
  if (!p.startsWith(web + path.sep) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', mime[path.extname(p)] || 'text/plain');
  res.end(fs.readFileSync(p));
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'lsd-ff-'));
fs.writeFileSync(path.join(profile, 'user.js'), 'user_pref("media.autoplay.default", 0);\nuser_pref("media.autoplay.blocking_policy", 0);\n');
const ff = spawn('firefox', ['--headless', '--no-remote', '--profile', profile,
  `http://127.0.0.1:${server.address().port}/checks/embed_check.html`], { stdio: 'ignore' });
const rep = await Promise.race([reported, new Promise((r) => setTimeout(() => r({ fatal: 'timeout', results: [] }), 90000))]);
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
