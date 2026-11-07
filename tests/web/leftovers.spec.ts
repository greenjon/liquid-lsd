import { test, expect, firefox, chromium } from '@playwright/test';
import { sealOff, openTV, until, note } from './tv';
const only = () => test.skip(test.info().project.name !== 'chromium');
test('checklist: the schedule scrolls under a finger without closing', async ({ browser }) => {
  only(); test.setTimeout(60_000);
  const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, hasTouch: true, isMobile: true });
  const page = await ctx.newPage(); await sealOff(ctx);
  const tv = await openTV(page); await tv.on(); await page.waitForTimeout(3000);
  await page.evaluate(() => { const n = Date.now(), H = 3600e3; (window as any).LSD.viewerState.schedule.setShows(Array.from({ length: 60 }, (_, i) => ({ start: n + i * H, end: n + (i + 1) * H, name: 'Show ' + i }))); });
  await page.locator('#scheduleBtn').tap(); await page.waitForTimeout(500);
  const box = (await page.locator('#schedule .sc-list').boundingBox())!;
  const scroller = () => page.evaluate(() => { let e: HTMLElement | null = document.querySelector('#schedule .sc-list'); const out: number[] = []; for (; e; e = e.parentElement) if (e.scrollHeight > e.clientHeight + 1) out.push(e.scrollTop); return { tops: out, hidden: (document.getElementById('schedule') as HTMLElement).hidden }; });
  const s0 = await scroller();
  const cdp = await ctx.newCDPSession(page);
  const x = box.x + box.width / 2, y0 = box.y + box.height * .8, y1 = box.y + box.height * .2;
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y: y0 }] });
  for (let i = 1; i <= 10; i++) { await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x, y: y0 + (y1 - y0) * i / 10 }] }); await page.waitForTimeout(16); }
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  await page.waitForTimeout(600);
  const s1 = await scroller();
  note('schedule_scroll', { s0, s1, errors: tv.errors });
  expect(s1.hidden, 'still open').toBe(false);
  expect(Math.max(...s1.tops), 'moved').toBeGreaterThan(Math.max(0, ...s0.tops));
  await ctx.close();
});
test('@long checklist: BG CHANGES WITH SCENES: a new background about every 32 bars', async ({ page, context }) => {
  only(); test.setTimeout(330_000);
  await sealOff(context);
  await page.addInitScript(() => localStorage.setItem('lsd.bgMode', '"scenes"'));
  const tv = await openTV(page, 'heavy=1', { track: 'steady128' }); await tv.on();
  await page.evaluate(() => { const w = window as any, L = w.LSD; w.__bg = []; let last: any = null; setInterval(() => { const n = L.autopilotState.deckBG?.name ?? L.autopilotState.deckBG?.source; if (n !== last) { w.__bg.push({ t: performance.now() / 1000, beats: L.cvState.totalBeats, n }); last = n; } }, 100); });
  await page.waitForTimeout(300_000);
  const ev = await page.evaluate(() => (window as any).__bg);
  const bars = ev.slice(2).map((e: any, i: number) => +((e.beats - ev[i + 1].beats) / 4).toFixed(1));
  note('bg_changes', { n: ev.length, bars, names: ev.map((e: any) => e.n).slice(0, 8), errors: tv.errors });
  expect(ev.length).toBeGreaterThan(2);
});
test('checklist: GPU test result in Firefox', async () => {
  only(); test.setTimeout(60_000);
  const b = await firefox.launch({ firefoxUserPrefs: { 'media.autoplay.default': 0, 'media.autoplay.blocking_policy': 0 } });
  const ctx = await b.newContext({ viewport: { width: 1280, height: 720 } }); const page = await ctx.newPage(); await sealOff(ctx);
  const tv = await openTV(page); await tv.on();
  await until(page, "LSD.gpu.state !== 'pending'", 15);
  const r = await page.evaluate(() => { const g = (document.getElementById('glCanvas') as HTMLCanvasElement).getContext('webgl2')!; const e = g.getExtension('WEBGL_debug_renderer_info'); return { gpu: { ...(window as any).LSD.gpu }, renderer: e ? g.getParameter(e.UNMASKED_RENDERER_WEBGL) : g.getParameter(g.RENDERER) }; });
  note('gpu_firefox', r); await b.close();
  expect(r.gpu.state).toBe('done');
});
test('@radio the real TV on the real radio: read only, chat socket blocked', async ({ page, context }) => {
  only(); test.setTimeout(13 * 60_000);
  await context.routeWebSocket(/radio\.spaz\.org:1885/, () => {});
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message)); page.on('console', (m) => { if (m.type() === 'error' && !/CORS|access control|Failed to load|net::/i.test(m.text())) errors.push(m.text()); });
  await page.goto('https://spaz.org/tv/'); await page.waitForFunction(() => (window as any).LSD, null, { timeout: 30000 });
  const version = await page.evaluate(() => { document.dispatchEvent(new Event('x')); return (window as any).LSD.gpu; });
  await page.click('#tv-screen-area', { position: { x: 50, y: 50 } });
  const rows: any[] = []; const ev: any[] = []; let last = '';
  for (let i = 0; i < 300; i++) {
    await page.waitForTimeout(2000);
    const r = await page.evaluate(() => { const L = (window as any).LSD, c = L.cvState, A = L.autopilotState, d = A.mixer.balance > .5 ? A.deckB : A.deckA; return { bpm: +c.bpm.toFixed(1), locked: c.beatLocked, noSignal: c.noSignal, amp: +c.raw.audio_amp.toFixed(3), section: c.section, drops: c.drops, live: A.isLiveBroadcast, name: d?.name, tags: d?.tags, src: document.getElementById('source-badge')!.textContent, t: (document.getElementById('lsdAudio') as HTMLAudioElement).currentTime }; });
    rows.push({ bpm: r.bpm, locked: r.locked, ns: r.noSignal, amp: r.amp, s: r.section, d: r.drops, live: r.live, src: r.src });
    if (r.name !== last) { ev.push({ at: i * 2, name: r.name, tags: r.tags, bpm: r.bpm }); last = r.name; }
  }
  const n = rows.length, on = rows.filter((x) => x.amp > .004);
  const hist: Record<string, number> = {}; for (const x of on) { const k = String(Math.round(x.bpm / 5) * 5); hist[k] = (hist[k] ?? 0) + 1; }
  note('radio', { samples: n, withSound: on.length, lockedShare: +(on.filter((x) => x.locked).length / Math.max(1, on.length)).toFixed(2), noSignal: rows.filter((x) => x.ns).length, bpmHist: hist, breakdownSamples: rows.filter((x) => x.s === 'breakdown').length, drops: rows.at(-1).d, live: rows.some((x) => x.live), sources: [...new Set(rows.map((x) => x.src))], looks: ev.map((e) => [e.at, e.name, e.bpm]), schedule: await page.evaluate(() => document.querySelectorAll('#schedule .sc-list *').length), listeners: await page.evaluate(() => document.body.innerText.match(/\d+ mutants? watching/)?.[0] ?? null), gpu: await page.evaluate(() => (window as any).LSD.gpu), errors: errors.slice(0, 8) });
  expect(errors).toEqual([]);
});
