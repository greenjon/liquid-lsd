import { test, expect, chromium } from '@playwright/test';
import { sealOff, openTV, until, note, picture, ORIGIN } from './tv';
const version = (page) => page.evaluate(() => { const v = document.querySelector('#tv-menu .menu-version'); return v?.textContent ?? null; });
async function menuVersion(page) { await page.keyboard.press('m'); await page.waitForSelector('#tv-menu .menu-version', { state: 'attached', timeout: 5000 }); const v = await version(page); await page.keyboard.press('Escape'); return v; }
const swReady = (page) => page.evaluate(async () => { const r = await navigator.serviceWorker.ready; await new Promise((res) => { if (navigator.serviceWorker.controller) return res(0); navigator.serviceWorker.addEventListener('controllerchange', () => res(0)); setTimeout(() => res(0), 8000); }); return !!r.active; });
test('checklist: reload after a publish shows the new version within two reloads', async ({ page, context }) => {
  test.skip(test.info().project.name !== 'chromium');
  await sealOff(context);
  await fetch(`${ORIGIN}/__version/`, { method: 'POST' });
  const tv = await openTV(page);
  expect(await swReady(page)).toBe(true);
  const v0 = await menuVersion(page);
  await page.reload(); await page.waitForFunction(() => (window as any).LSD);
  await fetch(`${ORIGIN}/__version/v99`, { method: 'POST' });
  const seen: (string | null)[] = [];
  try {
    for (let i = 0; i < 3; i++) { await page.reload(); await page.waitForFunction(() => (window as any).LSD); seen.push(await menuVersion(page)); }
  } finally { await fetch(`${ORIGIN}/__version/`, { method: 'POST' }); }
  note('sw_publish', { v0, afterPublish: seen });
  expect(seen[0] === 'v99' || seen[1] === 'v99').toBe(true);
  expect(seen[seen.length - 1]).toBe('v99');
});
for (const [visits, addr] of [[1, 'index.html'], [2, 'index.html'], [1, './'], [1, 'index.html?kiosk']] as [number, string][]) test(`checklist: opens with the network off after ${visits} visit(s) of ${addr}`, async ({ page, context }) => {
  test.skip(test.info().project.name !== 'chromium');
  await sealOff(context);
  await page.goto(`${ORIGIN}/${addr}`); await page.waitForFunction(() => (window as any).LSD, null, { timeout: 30000 });
  expect(await swReady(page)).toBe(true);
  if (visits > 1) { await page.reload(); await page.waitForFunction(() => (window as any).LSD); await page.waitForTimeout(1500); }
  else await page.waitForTimeout(2500);
  const cached = await page.evaluate(async () => { const names = await caches.keys(); const c = await caches.open(names[0]); return (await c.keys()).map((r) => new URL(r.url).pathname.slice(1)); });
  await context.setOffline(true);
  const failed: string[] = [];
  page.on('requestfailed', (r) => { if (r.url().startsWith(ORIGIN)) failed.push(new URL(r.url()).pathname); });
  let loaded = true;
  try { await page.reload({ timeout: 20000 }); await page.waitForFunction(() => (window as any).LSD, null, { timeout: 15000 }); } catch { loaded = false; }
  let onOk = false, lost: number | null = null;
  if (loaded) {
    if (!addr.includes('kiosk')) await page.click('#tv-screen-area', { position: { x: 50, y: 50 } });
    onOk = await until(page, 'LSD.powerState.on && LSD.powerState.warmupProgress >= 1', 20) !== null;
    lost = await until(page, 'LSD.cvState.noSignal', 15);
  }
  await page.waitForTimeout(500);
  note(`sw_offline_${visits}_${addr.replace(/\W/g, '_')}`, { loaded, onOk, noSignalAfterS: lost, failed: [...new Set(failed)], cachedFiles: cached.length, missingFromShell: await page.evaluate(() => 0) });
  expect(failed.filter((f) => !/gallery|\.lsd|shaders|fxchains|presets/.test(f)), 'code files missing offline').toEqual([]);
  expect(loaded).toBe(true);
  expect(onOk).toBe(true);
  expect(lost, 'NO SIGNAL offline').not.toBeNull();
});
test('checklist: the TV is installable (manifest, icons, fullscreen display)', async ({ page, context }) => {
  test.skip(test.info().project.name !== 'chromium');
  await sealOff(context);
  await openTV(page);
  await page.evaluate(() => navigator.serviceWorker.ready);
  const cdp = await context.newCDPSession(page);
  const m = await cdp.send('Page.getAppManifest');
  const errs = await cdp.send('Page.getInstallabilityErrors');
  const j = JSON.parse(m.data ?? '{}');
  note('pwa_manifest', { errors: m.errors, installability: errs.installabilityErrors, name: j.name, display: j.display, icons: j.icons?.map((i) => i.sizes), start: j.start_url });
  expect(m.errors).toEqual([]);
  expect(errs.installabilityErrors).toEqual([]);
  expect(['fullscreen', 'standalone']).toContain(j.display);
});
test('checklist: after one visit every scene, effect and photo kept works with the network off', async ({ page, context }) => {
  test.skip(test.info().project.name !== 'chromium');
  test.setTimeout(180_000);
  await sealOff(context);
  const tv = await openTV(page);
  expect(await swReady(page)).toBe(true);
  await tv.on();
  // sw.js caches the content 5 s after start; it reports back to the page.
  const cachedIn = await until(page, 'LSD.viewerState.offlineContent', 90);
  const report = await tv.lsd<any>('LSD.viewerState.offlineContent');
  await page.waitForTimeout(4000); // a gallery photo or two
  await context.setOffline(true);
  const failed: string[] = [];
  page.on('requestfailed', (r) => { if (r.url().startsWith(ORIGIN)) failed.push(new URL(r.url()).pathname); });
  let photosShown = 0;
  page.on('requestfinished', async (r) => { if (/\/gallery\/.+\.jpg/.test(r.url()) && (await r.response())?.ok()) photosShown++; });
  await page.reload(); await page.waitForFunction(() => (window as any).LSD, null, { timeout: 15000 });
  await page.click('#tv-screen-area', { position: { x: 50, y: 50 } });
  await page.waitForFunction(() => (window as any).LSD.powerState.on && (window as any).LSD.powerState.warmupProgress >= 1, null, { timeout: 30000 });
  // Every file the autopilot can ask for, read offline the way it reads them.
  const missing = await page.evaluate(async () => {
    const get = (u: string) => fetch(u).then((r) => (r.ok ? r : Promise.reject(new Error(u))));
    const catalog = await get('catalog.json').then((r) => r.json()), settings = await get('settings.json').then((r) => r.json());
    const files: string[] = Object.values(catalog).flat().map((e: any) => e.file);
    for (const pl of [settings.fgPlaylist, settings.bgPlaylist]) {
      const text = await get(`playlists/${pl}`).then((r) => r.text());
      for (const l of text.split(/\r?\n/).map((x) => x.trim()).filter((x) => x && !x.startsWith('#'))) files.push(`presets/${l.split('|')[0].trim()}.lsd`);
    }
    const bad: string[] = [];
    await Promise.all(files.map((f) => get(f).catch(() => bad.push(f))));
    return { total: files.length, bad };
  });
  // Every source, effect and transition shader compiles from the cache.
  const lib = await page.evaluate(async () => {
    const L = (window as any).LSD.library;
    for (const kind of ['sources', 'filters', 'transitions']) await L.preload(kind, [...L.entries[kind].keys()]);
    return { programs: L.programs.size, failed: [...L.failed] };
  });
  // A few scene changes, offline.
  for (let i = 0; i < 4; i++) { await page.evaluate(() => (window as any).LSD.advanceForeground()); await page.waitForTimeout(2500); }
  const photos = await page.evaluate(async () => (await (await caches.open('lsd-tv-photos')).keys()).length);
  const hint = await page.evaluate(() => document.getElementById('osd-msg')?.textContent ?? '');
  note('sw_offline_content', { cachedInS: cachedIn, report, missing, lib, photos, photosShown, hint, failed: [...new Set(failed)] });
  expect(cachedIn, 'content cached').not.toBeNull();
  expect(report.fetched).toBe(report.wanted);
  expect(missing.bad).toEqual([]);
  expect(lib.failed).toEqual([]);
  expect(photosShown, 'kept gallery photos load offline').toBeGreaterThan(0);
  expect(failed.filter((f) => !/\/gallery\//.test(f)), 'requests failed offline').toEqual([]);
});
test('checklist: offline after one visit: the TV points at the microphone, and runs on it through scene changes', async () => {
  test.skip(test.info().project.name !== 'chromium');
  test.setTimeout(180_000);
  const b = await chromium.launch({ args: ['--use-angle=gl', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required', '--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] });
  const context = await b.newContext({ viewport: { width: 1280, height: 720 }, permissions: ['microphone'] });
  const page = await context.newPage();
  try {
    await sealOff(context);
    const tv = await openTV(page);
    expect(await swReady(page)).toBe(true);
    await tv.on();
    expect(await until(page, 'LSD.viewerState.offlineContent', 90), 'content cached').not.toBeNull();
    await context.setOffline(true);
    await page.reload(); await page.waitForFunction(() => (window as any).LSD, null, { timeout: 15000 });
    await page.click('#tv-screen-area', { position: { x: 50, y: 50 } });
    await page.waitForFunction(() => (window as any).LSD.powerState.on && (window as any).LSD.powerState.warmupProgress >= 1, null, { timeout: 30000 });
    await until(page, 'LSD.cvState.noSignal', 20);
    let hint = '';
    for (let i = 0; i < 30 && !hint.startsWith('OFFLINE'); i++) { hint = await page.evaluate(() => document.getElementById('osd-msg')?.textContent ?? ''); await page.waitForTimeout(100); }
    await page.keyboard.press('a');
    const micIn = await until(page, "document.getElementById('source-badge').textContent.includes('MIC') && !LSD.cvState.noSignal", 15);
    const looks: any[] = [];
    for (let i = 0; i < 5; i++) {
      await page.evaluate(() => (window as any).LSD.advanceForeground());
      let peak = 0;
      for (let k = 0; k < 40; k++) { peak = Math.max(peak, await tv.lsd<number>('LSD.cvState.raw.audio_amp')); await page.waitForTimeout(100); }
      looks.push({ source: await tv.lsd('LSD.autopilotState.deckA?.source'), noSignal: await tv.lsd('LSD.cvState.noSignal'), peak, pic: await picture(page) });
    }
    note('sw_offline_mic', { hint, micInS: micIn, looks, errors: tv.errors });
    expect(hint).toMatch(/^OFFLINE/);
    expect(micIn, 'microphone takes over offline').not.toBeNull();
    expect(looks.every((l) => !l.noSignal)).toBe(true);
    expect(Math.max(...looks.map((l) => l.peak)), 'the microphone reaches the analysers').toBeGreaterThan(0.005);
    expect(looks.filter((l) => l.pic.sd > 2).length, 'a picture on screen').toBeGreaterThanOrEqual(4);
  } finally { await b.close(); }
});
