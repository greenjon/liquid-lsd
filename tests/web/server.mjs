// Test server for the web TV suite. :8765 serves web/ with the radio's address swapped for :8766, a fake Icecast that
// streams tests/web/.cache/<track>.ogg in real time and can stall or drop. A session per test: /<session>/<track>.
// POST :8766/__ctl/<session>/<mode> with mode live | stall | close. POST :8765/__version/<v> serves ui.js as that version.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url));
const web = path.resolve(here, '../../web'), cache = path.join(here, '.cache');
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.woff2': 'font/woff2', '.json': 'application/json', '.css': 'text/css', '.png': 'image/png', '.jpg': 'image/jpeg', '.webmanifest': 'application/manifest+json', '.ico': 'image/x-icon', '.ogg': 'audio/ogg' };
let versionOverride = null;
http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  const v = url.pathname.match(/^\/__version\/(.*)$/);
  if (v) { versionOverride = v[1] || null; return res.end('ok'); }
  let rel = decodeURIComponent(url.pathname); if (rel.endsWith('/')) rel += 'index.html';
  const p = path.normalize(path.join(web, rel));
  if (!p.startsWith(web + path.sep) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', mime[path.extname(p)] || 'text/plain');
  res.setHeader('Cache-Control', 'no-cache');
  const base = path.basename(p);
  if (base === 'dsp.js' || base === 'ui.js') {
    let s = fs.readFileSync(p, 'utf8');
    // Same text on both: the session and track come from the page's own address (?st=session&track=name).
    s = s.replace("const STREAM_URL = 'https://radio.spaz.org:8060/radio.ogg';",
      "const STREAM_URL = (() => { const q = new URLSearchParams(location.search); return `http://127.0.0.1:8766/${q.get('st') || 'x'}/${q.get('track') || 'dance128'}`; })();");
    if (versionOverride && base === 'ui.js') s = s.replace(/TV_VERSION = '[^']*'/, `TV_VERSION = '${versionOverride}'`);
    return res.end(s);
  }
  res.end(fs.readFileSync(p));
}).listen(8765, '127.0.0.1');

const sessions = new Map(); // session -> { mode, conns:Set }
const sess = (id) => { if (!sessions.has(id)) sessions.set(id, { mode: 'live', conns: new Set(), requests: 0 }); return sessions.get(id); };
http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  const parts = new URL(req.url, 'http://x').pathname.split('/').filter(Boolean);
  if (parts[0] === '__ctl') {
    const s = sess(parts[1]); s.mode = parts[2];
    if (s.mode === 'close') for (const c of s.conns) c.destroy();
    return res.end(JSON.stringify({ requests: s.requests }));
  }
  const [id, track] = parts; const s = sess(id); s.requests++;
  const file = path.join(cache, `${track}.ogg`);
  if (s.mode === 'close' || !fs.existsSync(file)) { res.statusCode = 503; return res.end(); }
  const truth = JSON.parse(fs.readFileSync(path.join(cache, `${track}.json`)));
  const buf = fs.readFileSync(file), rate = buf.length / truth.duration;
  res.setHeader('Content-Type', 'audio/ogg'); res.setHeader('Cache-Control', 'no-store'); res.setHeader('X-Started-At', Date.now());
  s.conns.add(res.socket); res.on('close', () => { s.conns.delete(res.socket); clearInterval(tick); });
  let pos = 0, sent = 0; const start = Date.now();
  const burst = Math.floor(rate * 1.5);
  res.write(buf.subarray(0, burst)); pos = burst; sent = burst;
  const tick = setInterval(() => {
    if (s.mode !== 'live') return; // stall: the connection stays open and goes quiet, as a dead network does
    const want = burst + rate * ((Date.now() - start) / 1000) - (s.pausedFor || 0) * rate;
    while (sent < want) { const n = Math.min(4096, buf.length - pos); if (n <= 0) { pos = 0; continue; } res.write(buf.subarray(pos, pos + n)); pos += n; sent += n; }
  }, 100);
}).listen(8766, '127.0.0.1');
console.log('test server on 8765 / 8766');
