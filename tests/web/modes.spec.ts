import { test, expect, chromium } from '@playwright/test';
import { sealOff, openTV, until, picture, note, ORIGIN, session } from './tv';
const only = (n: string) => test.skip(test.info().project.name !== n);
test('checklist: OS reduced motion: MENU shows AUTO (ON), REACT capped, no hard looks in 30 changes', async ({ page, context }) => {
  only('chromium'); test.setTimeout(200_000);
  await sealOff(context);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const tv = await openTV(page);
  await tv.on();
  await page.keyboard.press('m');
  for (let i = 0; i < 3; i++) { if (await page.locator('#tv-menu .menu-label', { hasText: 'REDUCED MOTION' }).count()) break; await page.keyboard.press('Tab'); }
  const row = await page.locator('#tv-menu .menu-row', { hasText: 'REDUCED MOTION' }).innerText();
  await page.keyboard.press('Escape');
  const looks: any[] = [];
  for (let i = 0; i < 30; i++) {
    await page.keyboard.press('j'); await page.waitForTimeout(1800);
    looks.push(await page.evaluate(() => { const a = (window as any).LSD.autopilotState; const d = a.mixer.balance > .5 ? a.deckB : a.deckA; return { tags: d?.tags, chain: d?.chainName }; }));
  }
  note('reduced', { row, reducedNow: await tv.lsd('LSD.viewerState.reducedNow'), hard: looks.filter((l) => l.tags?.includes('hard')).length, n: looks.length, chains: [...new Set(looks.map((l) => l.chain))] });
  expect(row).toMatch(/AUTO.*ON/s);
  expect(await tv.lsd('LSD.viewerState.reducedNow')).toBe(true);
  expect(tv.errors).toEqual([]);
});
test('checklist: ?kiosk: picture only, turns itself on, pointer hides', async ({ page, context }) => {
  only('chromium');
  await sealOff(context);
  const tv = await openTV(page, 'kiosk');
  expect(await until(page, 'LSD.powerState.on', 5), 'turns on by itself').not.toBeNull();
  const body = await page.evaluate(() => ({ kiosk: document.body.classList.contains('kiosk'), cabinet: getComputedStyle(document.getElementById('powerSwitch')!).display + '/' + (document.getElementById('powerSwitch')!.getBoundingClientRect().width) }));
  await page.mouse.move(200, 200); await page.waitForTimeout(3600);
  const idle = await page.evaluate(() => ({ idle: document.body.classList.contains('idle'), cursor: getComputedStyle(document.querySelector('canvas')!).cursor }));
  await page.waitForTimeout(3000);
  const pic = await picture(page);
  note('kiosk', { body, idle, pic, errors: tv.errors });
  expect(body.kiosk).toBe(true); expect(idle.idle).toBe(true); expect(idle.cursor).toBe('none');
  expect(pic.mean).toBeGreaterThan(2);
  expect(tv.errors).toEqual([]);
});
test('checklist: ?kiosk with autoplay blocked: asks for a click, sound starts on it', async () => {
  only('chromium');
  const b = await chromium.launch({ args: ['--use-angle=gl', '--ignore-gpu-blocklist', '--autoplay-policy=document-user-activation-required'] });
  const ctx = await b.newContext({ viewport: { width: 1280, height: 720 } });
  const page = await ctx.newPage();
  await sealOff(ctx);
  await openTV(page, 'kiosk');
  expect(await until(page, 'LSD.powerState.on', 6)).not.toBeNull();
  await page.waitForTimeout(3500);
  const asked = await page.evaluate(() => document.body.innerText);
  const before = await page.evaluate(() => (window as any).LSD.cvState.raw.audio_amp);
  await page.mouse.click(300, 300);
  const heard = await until(page, 'LSD.cvState.raw.audio_amp > 0.01', 10);
  note('kiosk_blocked', { text: asked.slice(0, 200), before, heardAfterS: heard });
  expect(heard, 'sound after the click').not.toBeNull();
  await b.close();
});
test('checklist: media session: play/pause powers, next/previous change the look; wake lock held while on, released off', async ({ page, context }) => {
  only('chromium');
  await sealOff(context);
  await page.addInitScript(() => {
    const w = window as any; w.__h = {}; const o = navigator.mediaSession.setActionHandler.bind(navigator.mediaSession);
    navigator.mediaSession.setActionHandler = (a: any, f: any) => { w.__h[a] = f; try { o(a, f); } catch {} };
    w.__wl = { req: 0, rel: 0 };
    Object.defineProperty(navigator, 'wakeLock', { value: { request: async () => { w.__wl.req++; const l: any = new EventTarget(); l.release = async () => { w.__wl.rel++; l.dispatchEvent(new Event('release')); }; return l; } } });
  });
  const tv = await openTV(page);
  const name = () => page.evaluate(() => { const a = (window as any).LSD.autopilotState; return (a.mixer.balance > .5 ? a.deckB : a.deckA)?.name ?? (a.mixer.balance > .5 ? a.deckB : a.deckA)?.source; });
  await page.evaluate(() => (window as any).__h.play());
  expect(await until(page, 'LSD.powerState.on && LSD.powerState.warmupProgress >= 1', 20), 'play powers on').not.toBeNull();
  await page.waitForTimeout(5000);
  const awakeOn = await page.evaluate(() => ({ ...(window as any).__wl, awake: (window as any).LSD.viewerState.awake }));
  const n0 = await name();
  await page.evaluate(() => (window as any).__h.nexttrack()); await page.waitForTimeout(5000);
  const n1 = await name();
  await page.evaluate(() => (window as any).__h.previoustrack()); await page.waitForTimeout(5000);
  const n2 = await name();
  await page.evaluate(() => (window as any).__h.pause());
  expect(await until(page, '!LSD.powerState.on', 5), 'pause powers off').not.toBeNull();
  const awakeOff = await page.evaluate(() => ({ ...(window as any).__wl, awake: (window as any).LSD.viewerState.awake }));
  note('media_wake', { n0, n1, n2, awakeOn, awakeOff, meta: await page.evaluate(() => { const m = navigator.mediaSession.metadata; return m && { t: m.title, a: m.artist, al: m.album }; }) });
  expect(n1).not.toBe(n0); expect(n2).toBe(n0);
  expect(awakeOn.awake).toBe(true); expect(awakeOff.rel).toBeGreaterThan(0); expect(awakeOff.awake).toBe(false);
  expect(tv.errors).toEqual([]);
});
test('checklist: microphone: AUDIO INPUT switches, badge says MIC, signals move, back to the radio', async () => {
  only('chromium');
  const b = await chromium.launch({ args: ['--use-angle=gl', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required', '--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] });
  const ctx = await b.newContext({ viewport: { width: 1280, height: 720 }, permissions: ['microphone'] });
  const page = await ctx.newPage();
  await sealOff(ctx);
  const tv = await openTV(page);
  await tv.on(); await page.waitForTimeout(3000);
  await page.keyboard.press('a'); await page.waitForTimeout(3000);
  let peak = 0; for (let i = 0; i < 30; i++) { peak = Math.max(peak, await tv.lsd<number>('LSD.cvState.raw.audio_amp')); await page.waitForTimeout(100); }
  const mic = { peak, badge: await page.locator('#source-badge').innerText(), amp: await tv.lsd<number>('LSD.cvState.raw.audio_amp'), noSignal: await tv.lsd('LSD.cvState.noSignal') };
  await page.keyboard.press('a'); await page.waitForTimeout(3000);
  const radio = { badge: await page.locator('#source-badge').innerText(), amp: await tv.lsd<number>('LSD.cvState.raw.audio_amp') };
  note('mic', { mic, radio, errors: tv.errors });
  expect(mic.badge).toBe('MIC'); expect(mic.peak, 'the microphone reaches the analysers').toBeGreaterThan(0.005); expect(radio.badge).not.toBe('MIC'); expect(tv.errors).toEqual([]);
  await b.close();
});
test('checklist: light theme reload: no dark flash, theme set before first paint', async ({ page, context }) => {
  only('chromium');
  await sealOff(context);
  await page.addInitScript(() => { localStorage.setItem('lsd.theme', '"light"'); const w = window as any; w.__themeAtPaint = null; new PerformanceObserver((l) => { for (const e of l.getEntries()) if (w.__themeAtPaint === null) w.__themeAtPaint = document.documentElement.getAttribute('data-theme'); }).observe({ type: 'paint', buffered: true }); });
  await openTV(page);
  await page.waitForTimeout(1000);
  const r = await page.evaluate(() => ({ atPaint: (window as any).__themeAtPaint, now: document.documentElement.getAttribute('data-theme'), bar: document.querySelector('meta[name="theme-color"]')!.getAttribute('content') }));
  note('theme_flash', r);
  expect(r.atPaint).toBe('light'); expect(r.now).toBe('light');
});
