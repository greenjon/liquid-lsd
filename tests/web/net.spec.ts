import { test, expect } from '@playwright/test';
import { sealOff, openTV, ctl, until, picture, note } from './tv';
// Checklist 16: "Pull the network while the TV plays: snow and NO SIGNAL within a few seconds; plug it back: radio and picture back within ~10 s."
for (const mode of ['stall', 'close']) {
  test(`stream ${mode}: NO SIGNAL, then recovery`, async ({ page, context }) => {
    test.setTimeout(150_000);
    await sealOff(context);
    const tv = await openTV(page);
    await tv.on();
    expect(await until(page, 'LSD.cvState.raw.audio_amp > 0.01', 20), 'music reaches the analysers').not.toBeNull();
    await page.waitForTimeout(8000);
    const before = await picture(page);
    await ctl(tv.session, mode);
    const lost = await until(page, 'LSD.cvState.noSignal', 40);
    const osd = await until(page, "document.body.innerText.includes('NO SIGNAL')", 3) !== null;
    const snow = await picture(page);
    await page.waitForTimeout(12000);
    const still = await tv.lsd<boolean>('LSD.cvState.noSignal');
    await ctl(tv.session, 'live');
    const back = await until(page, '!LSD.cvState.noSignal && LSD.cvState.raw.audio_amp > 0.005', 40);
    await page.waitForTimeout(1500);
    const after = await picture(page);
    note(`net_${mode}_${test.info().project.name}`, { lostAfterS: lost, osd, still, backAfterS: back, before, snow, after, errors: tv.errors });
    expect(lost, 'NO SIGNAL comes up').not.toBeNull();
    expect(lost!).toBeLessThan(10);
    expect(osd, 'NO SIGNAL text on screen').toBe(true);
    expect(still, 'stays down while the stream is down').toBe(true);
    expect(back, 'comes back').not.toBeNull();
    expect(back!).toBeLessThan(13);
    expect(tv.errors).toEqual([]);
  });
}
