// Lite mode check: node web/checks/lite_check.mjs [--shots DIR]
// Serves web/ and drives the real page with Playwright (repo node_modules): ?lite=1 in Chromium and Firefox, a
// browser without WebGL 2 (getContext('webgl2') -> null), and the full picture (which must not go lite).
// Lite must start the photo slideshow, fetch no shaders or scenes, show LITE on the OSD, and step photos on CH.
// Needs the local gallery build (web/gallery/lite/: scripts/gallery_curate.py lite).
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

async function run(engine, label, query, { noWebgl = false } = {}) {
  const browser = await engine.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  const heavy = [];
  const errors = [];
  let dialogs = 0;
  // The scenes, their FX and the scene shaders (index.html's <link rel=preload> of the six core shaders and the
  // startup JSON, ~64 KB, still loads in lite mode).
  page.on('request', (r) => { if (/\/(presets|content|fxchains)\/|\/shaders\/(?!(blit|mandala|mixer|crt_post)\.)/.test(r.url())) heavy.push(r.url()); });
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('dialog', (d) => { dialogs++; d.dismiss(); });
  if (noWebgl) {
    await page.addInitScript(() => {
      const get = HTMLCanvasElement.prototype.getContext;
      HTMLCanvasElement.prototype.getContext = function (type, ...rest) { return type === 'webgl2' ? null : get.call(this, type, ...rest); };
    });
  }
  await page.goto(base + query);
  await page.waitForTimeout(1500);
  await page.click('#powerSwitch');
  await page.waitForTimeout(7000);   // past the ident (4.6 s): the OSD says what is on
  await page.click('#glCanvas');     // a click on the picture brings up what is on
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
  const before = r.state.imgs;
  const srcs = await r.page.evaluate(() => [...document.querySelectorAll('#lite-layer img')].map((i) => i.src));
  ok(srcs.every((s) => s.includes('/gallery/lite/')), `${name} ?lite=1: small photos`, srcs[0]);
  await r.page.click('#chUp');
  await r.page.waitForTimeout(400);
  const after = await r.page.evaluate(() => [...document.querySelectorAll('#lite-layer img')].map((i) => i.src));
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

server.close();
process.exit(bad ? 1 : 0);
