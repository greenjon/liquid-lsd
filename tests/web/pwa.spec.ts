import { test, expect } from '@playwright/test';
import { sealOff, openTV, until, note, ORIGIN } from './tv';
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
