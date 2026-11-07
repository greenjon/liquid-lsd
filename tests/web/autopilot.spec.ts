import { test, expect } from '@playwright/test';
import { sealOff, openTV, until } from './tv';
// Found by the dance128 run: a breakdown starting during a scene change lost its calm look for the whole breakdown.
test('a breakdown that starts during a crossfade still brings the calm look at the next bar line', async ({ page, context }) => {
  test.skip(test.info().project.name !== 'chromium'); test.setTimeout(90_000);
  await sealOff(context);
  const tv = await openTV(page, 'nostatic', { track: 'none' });
  await tv.on(); await page.waitForTimeout(6000);
  await page.click('#chUp');
  await page.waitForFunction(() => { const m = (window as any).LSD.autopilotState.mixer.balance; return m > 0.05 && m < 0.95; }, null, { timeout: 15000 });
  await page.evaluate(() => { (window as any).LSD.cvState.section = 'breakdown'; });
  const calm = await until(page, "(() => { const A = LSD.autopilotState, d = A.mixer.balance > .5 ? A.deckB : A.deckA; return (d?.tags ?? []).some((t) => ['ambient', 'build'].includes(t)); })()", 15);
  expect(calm, 'calm look on air').not.toBeNull();
  expect(tv.errors).toEqual([]);
});
