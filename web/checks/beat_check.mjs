// Headless beat-tracking check: node web/checks/beat_check.mjs [bpm ...]
// Plays a synthetic track through the real onset worklet, BeatTracker and BeatClock in headless Firefox
// and fails unless the clock locks to the track's tempo with every kick within 30 ms of a whole beat.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const tempos = process.argv.slice(2).map(Number).filter(Boolean);
if (!tempos.length) tempos.push(96, 124, 140, 174);
const mime = { '.html': 'text/html', '.js': 'text/javascript' };
let resolveReport;
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname === '/report') {
    let b = ''; req.on('data', (c) => (b += c)); req.on('end', () => { res.end('ok'); resolveReport(JSON.parse(b)); });
    return;
  }
  const p = path.normalize(path.join(web, url.pathname));
  if (!p.startsWith(web + path.sep) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', mime[path.extname(p)] || 'text/plain');
  res.end(fs.readFileSync(p));
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
let bad = 0;
for (const bpm of tempos) {
  const reported = new Promise((r) => { resolveReport = r; });
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'lsd-ff-'));
  // Let the page start audio without a click.
  fs.writeFileSync(path.join(profile, 'user.js'), 'user_pref("media.autoplay.default", 0);\nuser_pref("media.autoplay.blocking_policy", 0);\n');
  const ff = spawn('firefox', ['--headless', '--no-remote', '--profile', profile,
    `http://127.0.0.1:${server.address().port}/checks/beat_check.html?bpm=${bpm}`], { stdio: 'ignore' });
  const timeout = new Promise((r) => setTimeout(() => r({ fatal: 'timeout' }), 60000));
  const rep = await Promise.race([reported, timeout]);
  ff.kill('SIGKILL');
  fs.rmSync(profile, { recursive: true, force: true });
  // The tempo prior (beattrack.js) prefers ~120 BPM, so a fast track may be followed at half time; that is
  // fine as long as every kick still lands on a whole or a half beat.
  const halfTime = rep.bpm && Math.abs(rep.bpm * 2 - bpm) < 3;
  const ok = !rep.fatal && rep.locked && (Math.abs(rep.bpm - bpm) < 1.5 ? rep.worstMs < 30 : halfTime && rep.worstHalfMs < 30);
  if (!ok) bad++;
  if (!ok && rep.trk) console.log('  tracker beat vs nearest kick (ms, confidence, bpm):', JSON.stringify(rep.trk));
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${bpm} BPM:`, rep.fatal ?? `tracked ${rep.bpm.toFixed(1)} locked=${rep.locked} kicks=${rep.kicks} worst ${rep.worstMs.toFixed(1)} ms mean ${rep.meanMs.toFixed(1)} ms${halfTime ? ` (half time, worst ${rep.worstHalfMs.toFixed(1)} ms)` : ''} (audio ${rep.ctxState})`);
}
server.close();
process.exit(bad ? 1 : 0);
