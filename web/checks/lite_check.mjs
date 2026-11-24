// Lite mode check: node web/checks/lite_check.mjs [--shots DIR]
// Serves web/ and drives the real page with Playwright (repo node_modules): ?lite=1 in Chromium and Firefox, a
// browser without WebGL 2 (getContext('webgl2') -> null), the full picture (which must not go lite), and a
// device too slow for it (requestAnimationFrame slowed to ~3 fps: lite takes over and the next visit starts
// lite; embedded, the host hears `failed`), and switching in place both ways (MENU › TV › VISUALS).
// Lite must start the collage (sky, photos, logos), fetch no shaders or scenes, show LITE on the OSD, and step photos on CH.
// Needs the local gallery build (web/gallery/lite/, sky/, logo/: scripts/gallery_curate.py lite, --set sky|logo build).
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium, firefox } from 'playwright';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const shots = process.argv.includes('--shots') ? process.argv[process.argv.indexOf('--shots') + 1] : null;
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.css': 'text/css', '.jpg': 'image/jpeg', '.woff2': 'font/woff2', '.png': 'image/png' };
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  const p = path.normalize(path.join(web, decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname)));
  if (!p.startsWith(web + path.sep) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', mime[path.extname(p)] || 'text/plain');
  res.end(fs.readFileSync(p));
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const base = `http://127.0.0.1:${server.address().port}/`;

let bad = 0;
const ok = (cond, name, detail = '') => { if (!cond) bad++; console.log(`${cond ? 'ok  ' : 'FAIL'} ${name}${!cond && detail ? ` (${detail})` : ''}`); };

async function run(engine, label, query, { noWebgl = false, slow = false, host = false } = {}) {
  const browser = await engine.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  const heavy = [];
  const errors = [];
  let dialogs = 0;
  // The scenes, their FX, the shaders and the startup files (none of them in lite mode).
  page.on('request', (r) => { if (/\/(presets|content|fxchains|shaders|playlists)\/|catalog\.json|settings\.json|mandala_recipes\.json/.test(r.url())) heavy.push(r.url()); });
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('dialog', (d) => { dialogs++; d.dismiss(); });
  if (noWebgl) {
    await page.addInitScript(() => {
      const get = HTMLCanvasElement.prototype.getContext;
      HTMLCanvasElement.prototype.getContext = function (type, ...rest) { return type === 'webgl2' ? null : get.call(this, type, ...rest); };
    });
  }
  if (slow) {
    await page.addInitScript(() => {
      window.requestAnimationFrame = (cb) => setTimeout(() => cb(performance.now()), 300);
    });
  }
  if (host) {
    await page.addInitScript(() => {
      window.hostFailed = [];
      window.LSDHost = { ready() {}, failed(why) { window.hostFailed.push(why); } };
    });
  }
  await page.goto(base + query);
  await page.waitForTimeout(1500);
  if (!host) await page.click('#powerSwitch');   // embedded, the TV comes on by itself
  await page.waitForTimeout(7000);   // past the ident (4.6 s): the OSD says what is on
  if (!host) await page.click('#glCanvas');      // a click on the picture brings up what is on
  await page.waitForTimeout(300);
  const state = await page.evaluate(() => ({
    layer: !!document.getElementById('lite-layer'),
    imgs: document.querySelectorAll('#lite-layer img').length,
    ch: document.getElementById('osd-ch')?.textContent,
    lite: document.body.classList.contains('lite'),
  }));
  return { browser, page, heavy, errors, dialogs, state };
}

// 1. Lite picked in the address.
for (const [engine, name] of [[chromium, 'chromium'], [firefox, 'firefox']]) {
  const r = await run(engine, name, '?lite=1&nolive');
  ok(r.state.layer && r.state.lite, `${name} ?lite=1: lite layer`);
  ok(r.state.imgs >= 1, `${name} ?lite=1: a photo on screen`, `${r.state.imgs}`);
  ok(r.state.ch === 'LITE', `${name} ?lite=1: OSD says LITE`, r.state.ch);
  ok(r.heavy.length === 0, `${name} ?lite=1: no shaders or scenes fetched`, r.heavy.slice(0, 3).join(' '));
  ok(r.errors.length === 0, `${name} ?lite=1: no page errors`, r.errors.join(' | '));
  const srcs = await r.page.evaluate(() => [...document.querySelectorAll('#lite-layer .lite-photo img')].map((i) => i.src));
  ok(srcs.length >= 1 && srcs.every((s) => s.includes('/gallery/lite/')), `${name} ?lite=1: small photos`, srcs[0]);
  const lanes = await r.page.evaluate(() => ['sky', 'photo', 'logo'].map((n) => [...document.querySelectorAll(`#lite-layer .lite-${n} img`)].map((i) => i.src)));
  ok(lanes[0].length >= 1 && lanes[0].every((s) => /\/gallery\/sky\/[^/]+$/.test(s)), `${name} ?lite=1: a full-size sky behind`, lanes[0][0]);
  ok(lanes[2].length >= 1 && lanes[2].every((s) => s.includes('/gallery/logo/')), `${name} ?lite=1: a logo in front`, lanes[2][0]);
  await r.page.click('#chUp');
  await r.page.waitForTimeout(400);
  const before = srcs.length;
  const after = await r.page.evaluate(() => [...document.querySelectorAll('#lite-layer .lite-photo img')].map((i) => i.src));
  ok(after.length > before || after.at(-1) !== srcs.at(-1), `${name} ?lite=1: CH ▲ brings the next photo`, `${before} -> ${after.length}`);
  if (shots) {
    fs.mkdirSync(shots, { recursive: true });
    await r.page.waitForTimeout(6000);
    await r.page.screenshot({ path: path.join(shots, `lite-${name}.png`) });
  }
  await r.browser.close();
}

// 2. No WebGL 2: lite on its own, no alert.
{
  const r = await run(chromium, 'chromium', '?nolive', { noWebgl: true });
  ok(r.state.layer && r.state.imgs >= 1, 'no WebGL 2: lite slideshow');
  ok(r.dialogs === 0, 'no WebGL 2: no alert');
  const reason = await r.page.evaluate(async () => (await import('./lite.js')).liteState.reason);
  ok(reason === 'no-webgl', 'no WebGL 2: reason no-webgl', reason);
  await r.browser.close();
}

// 3. Full picture: no lite layer.
{
  const r = await run(chromium, 'chromium', '?nolive&lite=0');
  ok(!r.state.layer, 'full: no lite layer');
  ok(r.heavy.length > 0, 'full: scenes load');
  await r.browser.close();
}

// 4. Too slow: lite takes over, says so, and the next visit starts lite without the visuals' files.
{
  const r = await run(chromium, 'chromium', '?nolive', { slow: true });
  await r.page.waitForTimeout(45000);   // the scaler steps down (~12 s), then two 8 s windows (a stall restarts them)
  const s = await r.page.evaluate(async () => ({
    imgs: document.querySelectorAll('#lite-layer img').length,
    reason: (await import('./lite.js')).liteState.reason,
    saved: localStorage.getItem('lsd.visuals'),
  }));
  ok(s.reason === 'slow' && s.imgs >= 1, 'too slow: the lite slideshow takes over', JSON.stringify(s));
  ok(s.saved === '"lite-auto"', 'too slow: remembered on this device', s.saved);
  ok(r.errors.length === 0, 'too slow: no page errors', r.errors.join(' | '));
  r.heavy.length = 0;
  await r.page.reload();
  await r.page.waitForTimeout(1500);
  await r.page.click('#powerSwitch');
  await r.page.waitForTimeout(3000);
  const again = await r.page.evaluate(async () => (await import('./lite.js')).liteState.reason);
  ok(again === 'slow', 'too slow: the next visit starts lite', again);
  ok(r.heavy.length === 0, 'too slow: the next visit fetches none of the visuals\' files', r.heavy.slice(0, 3).join(' '));
  await r.browser.close();
}

// 5. Too slow, embedded: the host app hears `failed` (it shows its own visuals); no lite slideshow.
{
  const r = await run(chromium, 'chromium', '?embed&nolive', { slow: true, host: true });
  await r.page.waitForTimeout(45000);   // a software-rendered shader compile over 3 s restarts the count
  const s = await r.page.evaluate(() => ({ failed: window.hostFailed, layer: !!document.getElementById('lite-layer') }));
  ok(s.failed.includes('too slow') && !s.layer, 'too slow, embedded: the host hears failed', JSON.stringify(s));
  await r.browser.close();
}

// 6. Switching in place (MENU › TV › VISUALS): no reload, the TV stays on, the visuals pause and carry on.
{
  const r = await run(chromium, 'chromium', '?nolive&lite=0');
  const p = r.page;
  const frames = () => p.evaluate(() => window.LSD.frames());
  await p.evaluate(() => { window.sameVisit = true; });
  await p.evaluate(async () => (await import('./lite.js')).setVisuals('lite'));
  await p.waitForTimeout(2000);
  const f1 = await frames();
  await p.waitForTimeout(1000);
  const s = await p.evaluate(() => ({ same: window.sameVisit, on: window.LSD.powerState.on, imgs: document.querySelectorAll('#lite-layer img').length,
    saved: localStorage.getItem('lsd.visuals'), msg: document.getElementById('osd-msg')?.textContent }));
  ok(s.same && s.on && s.imgs >= 1, 'switch to lite: in place, TV still on, photos up', JSON.stringify(s));
  ok((await frames()) === f1, 'switch to lite: the visuals rest');
  ok(s.saved === '"lite"', 'switch to lite: remembered', s.saved);
  await p.evaluate(async () => (await import('./lite.js')).setVisuals('full'));
  await p.waitForTimeout(1500);
  const f2 = await frames();
  await p.waitForTimeout(1000);
  const t = await p.evaluate(() => ({ same: window.sameVisit, on: window.LSD.powerState.on, layer: !!document.getElementById('lite-layer') }));
  ok(t.same && t.on && !t.layer, 'switch to full: in place, TV still on, photos gone', JSON.stringify(t));
  ok((await frames()) > f2, 'switch to full: the visuals carry on');
  ok(r.errors.length === 0, 'switching: no page errors', r.errors.join(' | '));
  await r.browser.close();
}

// 7. Started in lite, switched to full: the visuals start from scratch, in place.
{
  const r = await run(chromium, 'chromium', '?lite=1&nolive');
  const p = r.page;
  await p.evaluate(() => { window.sameVisit = true; });
  await p.evaluate(async () => (await import('./lite.js')).setVisuals('full'));
  await p.waitForTimeout(8000);
  const s = await p.evaluate(() => ({ same: window.sameVisit, on: window.LSD?.powerState.on, frames: window.LSD?.frames(),
    layer: !!document.getElementById('lite-layer'), url: location.search }));
  await p.waitForTimeout(1000);
  const later = await p.evaluate(() => window.LSD?.frames());
  ok(s.same && s.on && later > s.frames && !s.layer, 'lite -> full: the visuals start in place', JSON.stringify({ ...s, later }));
  ok(!s.url.includes('lite='), 'lite -> full: ?lite=1 leaves the address', s.url);
  ok(r.errors.length === 0, 'lite -> full: no page errors', r.errors.join(' | '));
  await r.browser.close();
}

// 8. The sound: with the beat held locked (the analysis can't overwrite it), each layer pulses on the beats;
// skies cover the screen at every moment (no edge in any corner); logos come larger than the screen's third.
{
  const r = await run(chromium, 'chromium', '?lite=1&nolive');
  const p = r.page;
  await p.evaluate(async () => {
    const { cvState } = await import('./dsp.js');
    const pin = (k, v) => Object.defineProperty(cvState, k, { get: () => v, set() {}, configurable: true });
    pin('beatLocked', true); pin('noSignal', false); pin('bpm', 120); pin('audio_energy', 0.4);
    window.pulses = { sky: 0, photo: 0, logo: 0 };
    for (const n of Object.keys(window.pulses)) {
      const el = document.querySelector(`#lite-layer .lite-${n}`);
      const animate = el.animate.bind(el);
      el.animate = (...a) => { window.pulses[n]++; return animate(...a); };
    }
  });
  const covered = () => p.evaluate(() => {
    const lane = document.querySelector('#lite-layer .lite-sky');
    const W = lane.clientWidth, H = lane.clientHeight;
    const out = [];
    for (const img of lane.querySelectorAll('img')) {
      const cs = getComputedStyle(img);
      const inv = new DOMMatrix(cs.transform).inverse();
      const [ox, oy] = cs.transformOrigin.split(' ').map(parseFloat);
      for (const [X, Y] of [[0, 0], [W, 0], [0, H], [W, H]]) {
        const q = inv.transformPoint({ x: X - img.offsetLeft - ox, y: Y - img.offsetTop - oy });
        const x = q.x + ox, y = q.y + oy;
        if (x < 0 || y < 0 || x > img.naturalWidth || y > img.naturalHeight) out.push(`${img.src.split('/').pop()} corner ${X},${Y} -> ${x | 0},${y | 0}`);
      }
    }
    return { n: lane.querySelectorAll('img').length, out };
  });
  const c1 = await covered();
  await p.waitForTimeout(5000);
  const c2 = await covered();
  const seen = await p.evaluate(() => ({ ...window.pulses }));
  ok(c1.n >= 1 && c1.out.length === 0 && c2.out.length === 0, 'sound: skies cover every corner', JSON.stringify([c1, c2]));
  ok(seen.photo >= 8 && seen.photo <= 12 && seen.sky === seen.photo && seen.logo === seen.photo, 'sound: a pulse per beat on each layer (120 BPM, 5 s)', JSON.stringify(seen));
  const logo = await p.evaluate(() => {
    const img = document.querySelector('#lite-layer .lite-logo img');
    if (!img) return null;
    const m = new DOMMatrix(getComputedStyle(img).transform);
    return { h: Math.hypot(m.b, m.d) * img.naturalHeight / img.parentElement.clientHeight, opaque: img.dataset.opaque === '1' };
  });
  ok(logo && (logo.opaque || logo.h >= 0.6), 'sound: the logo is two to four times its old size', JSON.stringify(logo));
  ok(r.errors.length === 0, 'sound: no page errors', r.errors.join(' | '));
  if (shots) await p.screenshot({ path: path.join(shots, 'lite-sound.png') });
  await r.browser.close();
}

server.close();
process.exit(bad ? 1 : 0);
