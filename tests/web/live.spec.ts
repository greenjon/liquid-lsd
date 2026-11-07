import { test, expect, chromium } from '@playwright/test';
import { sealOff, openTV, until, note, picture } from './tv';
const only = () => test.skip(test.info().project.name !== 'chromium');
const full = (broadcaster?: string) => JSON.stringify({ type: 'state_full', v: 2, clock: { bpm: 128, beats: 16, t: Date.now() }, preset: { deckA: { source: 'metaballs', params: {} }, deckB: null, deckBG: null, mixer: { balance: 0, alpha: 1 }, ...(broadcaster ? { broadcaster } : {}) } });
test('checklist: a real relay socket: LIVE title card with the name, controls locked, name cleared, back to autopilot; socket loss recovers', async ({ page, context }) => {
  only(); test.setTimeout(100_000);
  let sock: any = null; let conns = 0;
  await sealOff(context, { relay: (ws) => { sock = ws; conns++; } });
  const tv = await openTV(page);
  await tv.on(); await page.waitForTimeout(4000);
  expect(await until(page, 'true', 1)).not.toBeNull();
  const t0 = Date.now(); while (!sock && Date.now() - t0 < 15000) await page.waitForTimeout(200);
  expect(sock, 'the TV connects to the relay').not.toBeNull();
  sock.send(full('DJ Test'));
  const card = await until(page, "document.body.innerText.includes('DJ Test')", 6);
  const badge = await page.locator('#source-badge').innerText();
  const live0 = await tv.lsd('LSD.autopilotState.isLiveBroadcast');
  const name0 = await tv.lsd<string>('LSD.autopilotState.deckA?.source');
  const disabled = await page.locator('#chUp').isDisabled(); await page.keyboard.press('n'); await page.keyboard.press('h'); await page.waitForTimeout(800);
  const locked = { source: await tv.lsd('LSD.autopilotState.deckA?.source'), hold: await tv.lsd('LSD.autopilotState.hold') };
  sock.send(JSON.stringify({ type: 'state_delta', clock: { bpm: 128, beats: 32, t: Date.now() }, patch: { broadcaster: '' } }));
  await page.waitForTimeout(500);
  const cleared = await tv.lsd('LSD.autopilotState.broadcaster');
  sock.send(JSON.stringify({ type: 'broadcaster_offline' }));
  const off = await until(page, '!LSD.autopilotState.isLiveBroadcast', 5);
  await page.waitForTimeout(6000);
  const after = await page.evaluate(() => { const A = (window as any).LSD.autopilotState; return (A.mixer.balance > .5 ? A.deckB : A.deckA)?.name; });
  const c0 = conns; await sock.close(); const t1 = Date.now(); while (conns === c0 && Date.now() - t1 < 40000) await page.waitForTimeout(300);
  note('live', { disabled, card, badge, live0, name0, locked, cleared, off, after, reconnectS: (Date.now() - t1) / 1000, conns, errors: tv.errors });
  expect(card, 'title card names the broadcaster').not.toBeNull(); expect(badge).toBe('LIVE SHOW'); expect(locked.source).toBe('metaballs'); expect(locked.hold).toBe(false);
  expect(off).not.toBeNull(); expect(after).toBeTruthy(); expect(conns, 'a dropped relay socket is reconnected').toBeGreaterThan(c0);
  expect(tv.errors).toEqual([]);
});
test('checklist: GPU tier: the NVIDIA says heavy on, the Intel GPU says heavy off', async () => {
  only(); test.setTimeout(80_000);
  const r: any = {};
  for (const [n, env] of [['nvidia', { __NV_PRIME_RENDER_OFFLOAD: '1', __GLX_VENDOR_LIBRARY_NAME: 'nvidia' }], ['intel', {}]] as [string, any][]) {
    const clean = { ...process.env }; delete clean.__NV_PRIME_RENDER_OFFLOAD; delete clean.__GLX_VENDOR_LIBRARY_NAME;
    const b = await chromium.launch({ args: ['--use-angle=gl', '--ignore-gpu-blocklist', '--enable-gpu', '--autoplay-policy=no-user-gesture-required'], env: { ...clean, ...env } as any });
    const ctx = await b.newContext({ viewport: { width: 1280, height: 720 } }); const page = await ctx.newPage(); await sealOff(ctx);
    const tv = await openTV(page); await tv.on();
    await until(page, "LSD.gpu.state !== 'pending'", 15);
    r[n] = await page.evaluate(() => { const g = (document.getElementById('glCanvas') as HTMLCanvasElement).getContext('webgl2')!; const e = g.getExtension('WEBGL_debug_renderer_info'); return { gpu: { ...(window as any).LSD.gpu }, renderer: e && g.getParameter(e.UNMASKED_RENDERER_WEBGL) }; });
    await b.close();
  }
  note('gpu_tier', r);
  expect(r.nvidia.gpu.heavy).toBe(true); expect(r.intel.gpu.heavy).toBe(false);
});
const lum = (c: number[]) => { const f = (v: number) => { v /= 255; return v <= .03928 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4; }; return .2126 * f(c[0]) + .7152 * f(c[1]) + .0722 * f(c[2]); };
for (const theme of ['light', 'dark']) test(`checklist: ${theme} theme: text contrast on the cabinet`, async ({ page, context }) => {
  only();
  await sealOff(context);
  await page.addInitScript((t) => localStorage.setItem('lsd.theme', JSON.stringify(t)), theme);
  const tv = await openTV(page, 'nolive'); await tv.on(); await page.waitForTimeout(5000);
  const items = await page.evaluate(() => {
    const out: any[] = [];
    for (const e of document.querySelectorAll('body *')) {
      if (e.closest('#tv-screen-area')) continue;
      if (![...e.childNodes].some((n) => n.nodeType === 3 && n.textContent!.trim())) continue;
      const r = e.getBoundingClientRect(), s = getComputedStyle(e); if (!r.width || !r.height || s.visibility === 'hidden' || s.display === 'none' || r.bottom > innerHeight) continue;
      out.push({ t: e.textContent!.trim().slice(0, 24), id: e.id || String(e.className).slice(0, 20), fg: s.color, px: parseFloat(s.fontSize), r: [r.x, r.y, r.width, r.height] });
    }
    return out;
  });
  const shot = (await page.screenshot()).toString('base64');
  const low = await page.evaluate(async ({ items, shot }) => {
    const bm = await createImageBitmap(await (await fetch('data:image/png;base64,' + shot)).blob()); const c = document.createElement('canvas'); c.width = bm.width; c.height = bm.height; const x = c.getContext('2d')!; x.drawImage(bm, 0, 0);
    const lum = (c: number[]) => { const f = (v: number) => { v /= 255; return v <= .03928 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4; }; return .2126 * f(c[0]) + .7152 * f(c[1]) + .0722 * f(c[2]); };
    return items.map((i: any) => {
      const [X, Y, W, H] = i.r.map(Math.round); const d = x.getImageData(Math.max(0, X), Math.max(0, Y), Math.max(1, W), Math.max(1, H)).data; const ls: number[] = [];
      for (let k = 0; k < d.length; k += 4) ls.push(lum([d[k], d[k + 1], d[k + 2]])); ls.sort((a, b) => a - b); const bg = ls[ls.length >> 1];
      const fg = lum(i.fg.match(/[\d.]+/g).slice(0, 3).map(Number)); return { t: i.t, id: i.id, px: i.px, ratio: +((Math.max(fg, bg) + .05) / (Math.min(fg, bg) + .05)).toFixed(2) };
    });
  }, { items, shot });
  note(`contrast_${theme}`, { low: low.filter((x: any) => x.ratio < 4.5), checked: low.length });
});
