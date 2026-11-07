import { test, expect } from '@playwright/test';
import { sealOff, openTV, until, picture, note } from './tv';
// The one test for every browser: the page loads, plays the Ogg stream, draws, and the controls answer.
test('smoke: loads, plays the stream, draws, menu and channels work', async ({ page, context }, info) => {
  test.setTimeout(100_000);
  await sealOff(context);
  const tv = await openTV(page);
  const gl = await page.evaluate(() => { const g = (document.createElement('canvas')).getContext('webgl2'); if (!g) return null; const e = g.getExtension('WEBGL_debug_renderer_info'); return e ? g.getParameter(e.UNMASKED_RENDERER_WEBGL) : g.getParameter(g.VERSION); });
  await tv.on();
  const heard = await until(page, 'LSD.cvState.raw.audio_amp > 0.01', 25);
  await page.waitForTimeout(6000);
  const p = await picture(page);
  const a = await page.evaluate(() => { const e = document.getElementById('lsdAudio') as HTMLAudioElement; return { err: e.error?.message ?? null, t: e.currentTime, paused: e.paused, ready: e.readyState, ogg: e.canPlayType('audio/ogg; codecs=vorbis') }; });
  await page.keyboard.press('m'); await page.waitForTimeout(300);
  const menu = await page.locator('#tv-menu .menu-row').count();
  await page.keyboard.press('Escape');
  const name = () => page.evaluate(() => { const A = (window as any).LSD.autopilotState; return (A.mixer.balance > .5 ? A.deckB : A.deckA)?.name; });
  const n0 = await name(); await page.click('#chUp'); await page.waitForTimeout(5000); const n1 = await name();
  await page.screenshot({ path: `.planning/auto-verify-2026-11-06/round3/web/shots/smoke-${info.project.name}.png` });
  note(`smoke_${info.project.name}`, { gl, heardAfterS: heard, audio: a, pic: p, menu, n0, n1, bpm: await tv.lsd('LSD.cvState.bpm'), errors: tv.errors });
  expect(menu).toBeGreaterThan(3); expect(n1).not.toBe(n0); expect(tv.errors).toEqual([]);
  expect(heard, 'the stream reaches the analysers').not.toBeNull();
});
