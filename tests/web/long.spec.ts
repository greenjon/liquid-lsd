// Long measurements in real time. LONG=1 npx playwright test --project=chromium long.spec
import { test, expect } from '@playwright/test';
import { sealOff, openTV, until, picture, note, truth } from './tv';
import fs from 'node:fs';
const only = () => test.skip(test.info().project.name !== 'chromium');
const dir = '.planning/auto-verify-2026-11-06/round3/web/measure'; fs.mkdirSync(dir, { recursive: true });
const SAMPLER = () => {
  const w = window as any; w.__s = []; w.__ev = [];
  const L = w.LSD, a = document.getElementById('lsdAudio') as HTMLAudioElement; let last: any = null;
  const onair = () => { const A = L.autopilotState; return A.mixer.balance > .5 ? A.deckB : A.deckA; };
  const off = () => { const A = L.autopilotState; return A.mixer.balance > .5 ? A.deckA : A.deckB; };
  const tick = () => {
    const c = L.cvState, A = L.autopilotState, d = onair();
    w.__s.push([+a.currentTime.toFixed(3), +c.totalBeats.toFixed(3), +c.bpm.toFixed(2), c.beatLocked ? 1 : 0, c.section === 'breakdown' ? 1 : 0, c.drops, +A.mixer.balance.toFixed(3), +(c.audio_energy ?? 0).toFixed(2), +(c.audio_bass ?? 0).toFixed(3), +c.raw.audio_bass.toFixed(3), +(c.audio_drop ?? 0).toFixed(3)]);
    const key = d?.name + '|' + A.deckBG?.source + '|' + (A.deckBG?.name ?? '');
    if (key !== last) { w.__ev.push({ t: a.currentTime, beats: c.totalBeats, name: d?.name, tags: d?.tags, chain: d?.chainName, transition: d?.transition, bg: A.deckBG?.name ?? A.deckBG?.source, bpm: c.bpm }); last = key; }
  };
  setInterval(tick, 50);
};
for (const [track, secs] of [['dance128', 245], ['dnb174', 180], ['hiphop85', 200]] as [string, number][]) test(`@long checklist: ${track}: tempo, beat phase, look changes, sections`, async ({ page, context }) => {
  only(); test.setTimeout((secs + 60) * 1000);
  await sealOff(context);
  await page.addInitScript(() => { localStorage.setItem('lsd.time', '16'); });
  const tv = await openTV(page, 'heavy=1', { track });
  await tv.on();
  await page.evaluate(() => { (window as any).LSD.viewerState.bgMode = 'scenes'; });
  await page.evaluate(SAMPLER);
  await page.waitForTimeout(secs * 1000);
  const data = await page.evaluate(() => ({ s: (window as any).__s, ev: (window as any).__ev }));
  fs.writeFileSync(`${dir}/run_${track}.json`, JSON.stringify({ ...data, errors: tv.errors, truth: truth(track) }));
  expect(tv.errors).toEqual([]);
});
test('@long checklist: every channel renders, nothing blank, no errors; full-frame looks avoid trail and bloom chains', async ({ page, context }) => {
  only(); test.setTimeout(30 * 60_000);
  await sealOff(context);
  const tv = await openTV(page, 'heavy=1', { track: 'steady128' });
  await tv.on(); await page.waitForTimeout(6000);
  const out: any[] = []; const count = await page.evaluate(() => fetch('playlists/default.lsdplay').then((r) => r.text()).then((t) => t.split('\n').filter((l) => l.trim() && !l.startsWith('#')).length));
  const already = new Set<string>();
  for (let ch = 1; ch <= count; ch++) {
    const errs0 = tv.errors.length;
    for (const k of String(ch).padStart(3, '0')) await page.keyboard.press(k);
    await page.waitForTimeout(3500);
    const f0 = await page.evaluate(() => new Promise<number>((res) => { let n = 0; const t0 = performance.now(); const f = () => { if (++n === 40) res((performance.now() - t0) / 40); else requestAnimationFrame(f); }; requestAnimationFrame(f); }));
    const p1 = await picture(page); await page.waitForTimeout(700); const p2 = await picture(page);
    const info = await page.evaluate(() => { const A = (window as any).LSD.autopilotState, d = A.mixer.balance > .5 ? A.deckB : A.deckA; return { name: d?.name, src: d?.source, tags: d?.tags, chain: d?.chainName, chainTags: d?.chain?.tags }; });
    out.push({ ch, ...info, ms: +f0.toFixed(1), mean: +p1.mean.toFixed(1), sd: +p1.sd.toFixed(1), moved: +Math.abs(p1.mean - p2.mean).toFixed(2), errs: tv.errors.slice(errs0) });
    fs.writeFileSync(`${dir}/channels.json`, JSON.stringify(out));
  }
  expect(tv.errors).toEqual([]);
});
test('@long checklist: 25 minutes on: no growth in memory, GL objects or listeners; the frame clock stays smooth', async ({ page, context }) => {
  only(); test.setTimeout(28 * 60_000);
  await sealOff(context);
  await page.addInitScript(() => {
    const w = window as any; w.__gl = {}; const P = WebGL2RenderingContext.prototype as any;
    for (const k of ['Texture', 'Framebuffer', 'Buffer', 'Program', 'Shader', 'Renderbuffer', 'VertexArray']) {
      const c = P['create' + k], d = P['delete' + k]; w.__gl[k] = 0;
      P['create' + k] = function (...a: any[]) { w.__gl[k]++; return c.apply(this, a); };
      P['delete' + k] = function (o: any) { if (o) w.__gl[k]--; return d.call(this, o); };
    }
    w.__ft = []; let l = performance.now(); const f = (t: number) => { w.__ft.push(t - l); l = t; requestAnimationFrame(f); }; requestAnimationFrame(f);
  });
  const tv = await openTV(page, 'heavy=1', { track: 'steady128' });
  await tv.on();
  const cdp = await context.newCDPSession(page); await cdp.send('Performance.enable');
  const rows: any[] = [];
  for (let m = 0; m < 50; m++) {
    await page.waitForTimeout(30_000);
    await cdp.send('HeapProfiler.collectGarbage').catch(() => {});
    const met = Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map((x: any) => [x.name, x.value]));
    const r = await page.evaluate(() => { const w = window as any, ft = w.__ft.splice(0).sort((a: number, b: number) => a - b), c = w.LSD.cvState; return { gl: w.__gl, frames: ft.length, p50: +ft[ft.length >> 1]?.toFixed(1), p99: +ft[Math.floor(ft.length * .99)]?.toFixed(1), max: +ft.at(-1)?.toFixed(1), res: w.LSD.renderSize()[2], bpm: c.bpm, locked: c.beatLocked }; });
    rows.push({ min: (m + 1) / 2, heapMB: +(met.JSHeapUsedSize / 1e6).toFixed(1), nodes: met.Nodes, listeners: met.JSEventListeners, docs: met.Documents, ...r, errs: tv.errors.length });
    fs.writeFileSync(`${dir}/soak.json`, JSON.stringify(rows));
  }
  expect(tv.errors).toEqual([]);
});
