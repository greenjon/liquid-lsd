// Headless shader compile check: node web/checks/shader_check.mjs [--keep]
// Serves the repo, opens checks/shader_check.html in headless Firefox, prints compile logs,
// exits non-zero if any shipped shader fails. Needs `firefox` on PATH.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
// Everything in the web catalog: sources, FX filters, transitions.
const catalog = JSON.parse(fs.readFileSync(path.join(root, 'web', 'catalog.json'), 'utf8'));
const list = [catalog.sources, catalog.filters, catalog.transitions].flat().map((e) => `web/${e.file}`);

let resolveReport;
const reported = new Promise((r) => { resolveReport = r; });
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript' };
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname === '/report') {
    let b = ''; req.on('data', (c) => (b += c)); req.on('end', () => { res.end('ok'); resolveReport(JSON.parse(b)); });
  } else if (url.pathname === '/list') {
    res.end(JSON.stringify(list));
  } else if (url.pathname === '/file') {
    const p = path.normalize(url.searchParams.get('p'));
    if (p.startsWith('..') || !list.includes(p.replaceAll('\\', '/'))) { res.statusCode = 403; return res.end(); }
    res.end(fs.readFileSync(path.join(root, p)));
  } else {
    const p = path.normalize(path.join(root, url.pathname));
    if (!p.startsWith(root + path.sep) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.statusCode = 404; return res.end(); }
    res.setHeader('Content-Type', mime[path.extname(p)] || 'text/plain');
    res.end(fs.readFileSync(p));
  }
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const port = server.address().port;
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'lsd-ff-'));
const ff = spawn('firefox', ['--headless', '--no-remote', '--profile', profile,
  `http://127.0.0.1:${port}/web/checks/shader_check.html`], { stdio: 'ignore' });
const timer = setTimeout(() => { console.error('timeout waiting for Firefox'); cleanup(1); }, 120000);
function cleanup(code) {
  clearTimeout(timer); ff.kill('SIGKILL'); server.close();
  fs.rmSync(profile, { recursive: true, force: true }); process.exit(code);
}
const rep = await reported;
if (rep.fatal) { console.error(rep.fatal); cleanup(2); }
console.log('GL renderer:', rep.renderer);
if (!rep.vertex.ok) console.log('VERTEX FAIL blit.vert:\n' + rep.vertex.log);
let bad = rep.vertex.ok ? 0 : 1;
for (const r of rep.results) {
  console.log(`${r.ok ? 'ok  ' : 'FAIL'} ${r.file}`);
  if (!r.ok) { bad++; console.log(r.log.split('\n').slice(0, 6).map((l) => '     ' + l).join('\n')); }
}
console.log(`${rep.results.length - (bad - (rep.vertex.ok ? 0 : 1))}/${rep.results.length} compiled`);
cleanup(bad ? 1 : 0);
