// Measures the standalone beat tracker on real audio: the real onset worklet, BeatTracker and BeatClock run
// offline at the cadence dsp.js uses (analyse every 250 ms). A measuring tool, not a pass/fail check; compare two
// versions on one list. Needs ffmpeg. Lists hold people's own music, so they stay out of the repo.
//   node web/checks/tempo_report.mjs tags  LIST [--web DIR]   LIST: group<TAB>bpm<TAB>path, one minute of each file,
//                                                             scored as AudioTempoReportTest scores the desktop
//   node web/checks/tempo_report.mjs truth FILE [--web DIR]   FILE: {"file": path, "sections": [["12:00","14:40",88], ...]}
//   node web/checks/tempo_report.mjs mixes LIST [--web DIR]   LIST: one path per line; lock, on-beat share and jumps
// --web points at another checkout's web/ folder, so a baseline can come from a git worktree.
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const SELF = fileURLToPath(import.meta.url);
const QUANTUM = 128;
const PARALLEL = 12;

/** One file through the real code. Returns tempo votes, lock time, on-beat share, jumps and a 10 s timeline. */
async function measure(file, { web, start = 0, seconds = 0, rate = 44100 }) {
  let Processor;
  globalThis.sampleRate = rate;
  globalThis.currentTime = 0;
  globalThis.AudioWorkletProcessor = class { constructor() { this.port = { postMessage: (m) => onBatch(m) }; } };
  globalThis.registerProcessor = (_, c) => { Processor = c; };
  const url = (name) => pathToFileURL(path.join(web, name)).href;
  await import(url('onset-worklet.js'));
  const { BeatTracker, BeatClock } = await import(url('beattrack.js'));
  const tracker = new BeatTracker(), clock = new BeatClock();

  // On-beat share: how much of the phase envelope lands within an eighth of a beat of the clock's beats (chance
  // 25%). Beats come from read() at 60 frames a second, as the renderer gets them, not from the clock's estimate.
  const byPhase = new Float64Array(20);
  let waiting = [];
  function onBatch(d) {
    for (let i = 0; i < d.values.length; i++) {
      const t = d.t0 + i * d.hop;
      tracker.push(d.values[i], t, d.bands?.[i]);
      waiting.push([t, d.values[i]]);
    }
  }
  function onFrame(t) {
    const b = clock.read(t * 1000);
    const later = [];
    for (const w of waiting) {
      if (w[0] > t) { later.push(w); continue; }
      if (w[0] > 15) {
        const at = b + (w[0] - t) * (clock.bpm / 60);
        byPhase[Math.floor((at - Math.floor(at)) * 20) % 20] += w[1];
      }
    }
    waiting = later;
  }
  const proc = new Processor();
  const args = ['-v', 'error'];
  if (start > 0) args.push('-ss', String(start));
  if (seconds > 0) args.push('-t', String(seconds));
  args.push('-i', file, '-ac', '1', '-ar', String(rate), '-f', 'f32le', '-');
  const ff = spawn('ffmpeg', args, { stdio: ['ignore', 'pipe', 'ignore'] });

  const input = new Float32Array(QUANTUM), out = [[new Float32Array(QUANTUM)]];
  const votes = {}, timeline = [];
  let rest = Buffer.alloc(0), frames = 0, next = 0.25, nextFrame = 1 / 60, total = 0, locked = 0, jumps = 0, tempoMoves = 0, lastBpm = 0, cost = 0, costN = 0;
  for await (const chunk of ff.stdout) {
    const b = rest.length ? Buffer.concat([rest, chunk]) : chunk;
    let o = 0;
    while (b.length - o >= QUANTUM * 4) {
      for (let i = 0; i < QUANTUM; i++) input[i] = b.readFloatLE(o + i * 4);
      o += QUANTUM * 4;
      proc.process([[input]], out);
      frames += QUANTUM;
      globalThis.currentTime = frames / rate;
      const t = frames / rate;
      if (t >= nextFrame) { nextFrame += 1 / 60; onFrame(t); }
      if (t < next) continue;
      next += 0.25;
      const now = t * 1000;
      const s0 = performance.now();
      const r = tracker.analyse();
      cost += performance.now() - s0; costN++;
      const before = clock.fly.valid ? clock.fly.predict(now) : null;
      clock.update(r, now);
      if (Math.floor(t * 4) % 40 === 0) timeline.push([Math.round(t), +clock.bpm.toFixed(1), clock.locked ? 1 : 0]);
      if (t <= 15) continue;
      if (before !== null && Math.abs(clock.fly.predict(now) - before) > 0.1) jumps++;
      if (lastBpm && Math.abs(clock.bpm / lastBpm - 1) > 0.03) tempoMoves++;
      lastBpm = clock.bpm;
      total++;
      if (clock.locked) { locked++; const k = Math.round(clock.bpm); votes[k] = (votes[k] || 0) + 1; }
    }
    rest = b.subarray(o);
  }
  const phaseSum = byPhase.reduce((a, b) => a + b, 0) || 1;
  const onBeat = (byPhase[0] + byPhase[1] + byPhase[2] + byPhase[18] + byPhase[19]) / phaseSum;
  let found = 0, most = 0;
  for (const k in votes) if (votes[k] > most) { most = votes[k]; found = +k; }
  return { file, secs: frames / rate, found, locked: locked / Math.max(1, total), onBeat, jumps, tempoMoves, cost: cost / Math.max(1, costN), timeline };
}

/** Runs `jobs` (option objects for `one`) in child processes, a few at a time. */
function runAll(jobs, web) {
  const results = [];
  let at = 0;
  const worker = async () => {
    while (at < jobs.length) {
      const job = jobs[at++];
      const r = await new Promise((resolve) => {
        const c = spawn(process.execPath, [SELF, 'one', JSON.stringify({ ...job, web })], { stdio: ['ignore', 'pipe', 'ignore'] });
        let text = '';
        c.stdout.on('data', (d) => { text += d; });
        c.on('close', () => { try { resolve(JSON.parse(text)); } catch { resolve(null); } });
      });
      if (r) results.push({ ...r, ...job.meta });
    }
  };
  return Promise.all(Array.from({ length: PARALLEL }, worker)).then(() => results);
}

const near = (found, want, ratio) => Math.abs(found - want * ratio) <= want * ratio * 0.04;
const pct = (x) => `${Math.round(x * 100)}%`;
const mean = (rs, f) => rs.reduce((a, r) => a + f(r), 0) / Math.max(1, rs.length);
const perMin = (rs, key) => (rs.reduce((a, r) => a + r[key], 0) / Math.max(1, rs.reduce((a, r) => a + Math.max(0, r.secs - 15), 0))) * 60;

function kindOf(found, want) {
  const r = found / want;
  const is = (list) => list.some((q) => Math.abs(r / q - 1) < 0.03);
  if (is([1])) return 'right';
  if (is([2, 0.5, 4, 0.25])) return 'octave';
  if (is([1.5, 2 / 3, 3, 1 / 3])) return '3:2';
  if (is([4 / 3, 0.75, 8 / 3, 3 / 8])) return '4:3';
  return 'other';
}

async function tags(list, web) {
  const jobs = fs.readFileSync(list, 'utf8').split('\n').filter((l) => l.trim()).map((l) => {
    const [group, bpm, file] = l.split('\t');
    return { file, start: 30, seconds: 60, meta: { group, tag: +bpm } };
  });
  const results = (await runAll(jobs, web)).filter((r) => r.secs >= 20);
  const line = (name, rs) => {
    const octave = (r) => near(r.found, r.tag, 1) || near(r.found, r.tag, 2) || near(r.found, r.tag, 0.5);
    const family = (r) => !near(r.found, r.tag, 1) && [1.5, 2 / 3, 4 / 3, 0.75, 3, 1 / 3].some((q) => near(r.found, r.tag, q));
    console.log(`${name.padEnd(10)} n ${String(rs.length).padStart(3)}  exact ${pct(mean(rs, (r) => +near(r.found, r.tag, 1)))}  with octaves ${pct(mean(rs, (r) => +octave(r)))}  3:2 family ${pct(mean(rs, (r) => +family(r)))}  locked ${pct(mean(rs, (r) => r.locked))}`);
  };
  const groups = [...new Set(results.map((r) => r.group))].sort();
  for (const g of groups) line(g, results.filter((r) => r.group === g));
  line('ALL', results);
  console.log(`on-beat ${(mean(results, (r) => r.onBeat) * 100).toFixed(1)}%  jumps/min ${perMin(results, 'jumps').toFixed(2)}  tempo moves/min ${perMin(results, 'tempoMoves').toFixed(2)}`);
}

async function truth(file, web) {
  const { file: audio, sections } = JSON.parse(fs.readFileSync(file, 'utf8'));
  const [r] = await runAll([{ file: audio }], web);
  const toSec = (s) => s.split(':').reduce((a, x) => a * 60 + +x, 0);
  const totals = {};
  let n = 0, lockedN = 0;
  for (const [from, to, bpm] of sections) {
    const row = r.timeline.filter(([t]) => t >= toSec(from) && t < toSec(to));
    const counts = {};
    for (const [, b, l] of row) { const k = kindOf(b, bpm); counts[k] = (counts[k] || 0) + 1; totals[k] = (totals[k] || 0) + 1; n++; lockedN += l; }
    const good = (counts.right || 0) + (counts.octave || 0);
    const bad = Object.entries(counts).filter(([k]) => k !== 'right' && k !== 'octave').map(([k, v]) => `${k} ${v}`).join(' ');
    console.log(`${from.padStart(6)} ${String(bpm).padStart(7)}  good ${good}/${row.length}  ${bad}`);
  }
  console.log(`right or an octave off ${pct(((totals.right || 0) + (totals.octave || 0)) / n)} (exact ${pct((totals.right || 0) / n)}, octave ${pct((totals.octave || 0) / n)}, 3:2 ${pct((totals['3:2'] || 0) / n)}, 4:3 ${pct((totals['4:3'] || 0) / n)}, other ${pct((totals.other || 0) / n)})  locked ${pct(lockedN / n)}`);
}

async function mixes(list, web) {
  const files = fs.readFileSync(list, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean);
  const results = await runAll(files.map((file) => ({ file })), web);
  const weigh = (f) => results.reduce((a, r) => a + f(r) * r.secs, 0) / Math.max(1, results.reduce((a, r) => a + r.secs, 0));
  for (const r of results.sort((a, b) => a.locked - b.locked)) {
    console.log(`${path.basename(r.file).slice(0, 44).padEnd(44)} ${String(Math.round(r.secs / 60)).padStart(4)} min  locked ${pct(r.locked).padStart(4)}  on-beat ${(r.onBeat * 100).toFixed(1)}%  jumps/min ${(r.jumps / Math.max(1, (r.secs - 15) / 60)).toFixed(2)}  most ${r.found}`);
  }
  console.log(`ALL ${results.length} files, ${(results.reduce((a, r) => a + r.secs, 0) / 3600).toFixed(1)} h: locked ${pct(weigh((r) => r.locked))}  on-beat ${(weigh((r) => r.onBeat) * 100).toFixed(1)}%  jumps/min ${perMin(results, 'jumps').toFixed(2)}  tempo moves/min ${perMin(results, 'tempoMoves').toFixed(2)}  analyse ${mean(results, (r) => r.cost).toFixed(1)} ms`);
}

const [mode, arg] = process.argv.slice(2);
const webAt = process.argv.indexOf('--web');
const web = path.resolve(webAt > 0 ? process.argv[webAt + 1] : path.join(path.dirname(SELF), '..'));
if (mode === 'one') {
  const o = JSON.parse(arg);
  console.log(JSON.stringify(await measure(o.file, o)));
  process.exit(0);
}
if (!arg || !['tags', 'truth', 'mixes'].includes(mode)) {
  console.error('usage: tempo_report.mjs tags|truth|mixes FILE [--web DIR]');
  process.exit(2);
}
if (spawnSync('ffmpeg', ['-version']).status !== 0) { console.error('needs ffmpeg'); process.exit(2); }
await { tags, truth, mixes }[mode](arg, web);
