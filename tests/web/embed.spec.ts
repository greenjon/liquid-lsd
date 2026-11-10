import { test, expect, type Page } from '@playwright/test';
import { sealOff, until, note, ORIGIN } from './tv';

// The host-app control API (web/embed.js) that the VJ app drives: catalog, scenes, chains, transitions,
// backgrounds, hold/time, effects, skip, and the state pushed to LSDHost.state.
const only = (n: string) => test.skip(test.info().project.name !== n);

async function openEmbed(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.addInitScript(() => {
    const w = window as any;
    w.__states = [];
    w.LSDHost = { ready() { w.__ready = true; }, failed(r: string) { w.__failed = r; }, state(j: string) { w.__states.push(JSON.parse(j)); } };
  });
  await page.goto(`${ORIGIN}/index.html?embed`);
  await page.waitForFunction(() => (window as any).__ready || (window as any).__failed, null, { timeout: 30000 });
  await page.waitForFunction(() => (window as any).LSD.catalog().scenes.length > 0 && (window as any).LSD.state().name, null, { timeout: 30000 });
  return errors;
}
const L = <T>(page: Page, f: string) => page.evaluate(`(async () => (${f}))()`) as Promise<T>;
const lastPushed = (page: Page) => page.evaluate(() => (window as any).__states.at(-1));

test('embed control API: catalog, scenes, hold, chains, transitions, backgrounds, effects, skip, state', async ({ page, context }) => {
  only('chromium'); test.setTimeout(150_000);
  await sealOff(context);
  const errors = await openEmbed(page);
  expect(await page.evaluate(() => (window as any).__failed ?? null)).toBeNull();
  const cat = await L<any>(page, 'LSD.catalog()');
  const r: Record<string, unknown> = { scenes: cat.scenes.length, backgrounds: cat.backgrounds.length, fxChains: cat.fxChains.length, transitions: cat.transitions.length };
  expect(cat.scenes.length).toBeGreaterThan(10);
  expect(cat.backgrounds.length).toBeGreaterThan(1);
  expect(cat.fxChains.length).toBeGreaterThan(5);
  expect(cat.transitions.length).toBeGreaterThan(2);
  await L(page, 'LSD.time(1)'); // 1-bar holds: changes every couple of seconds at the fallback 120 BPM

  // A scene by channel.
  const now = await L<any>(page, 'LSD.state()');
  const target = cat.scenes.find((s: any) => s.runnable && !s.skipped && s.name !== now.name);
  expect(await L(page, `LSD.channel(${target.channel})`)).toBe('ok');
  r.channelIn = await until(page, `LSD.state().name === ${JSON.stringify(target.name)}`, 10);
  expect(r.channelIn, 'tuned to the channel').not.toBeNull();

  // HOLD keeps it; release lets the rotation move on.
  await L(page, 'LSD.hold(true)');
  await page.waitForTimeout(6000);
  r.heldName = (await L<any>(page, 'LSD.state()')).name;
  expect(r.heldName).toBe(target.name);
  expect((await L<any>(page, 'LSD.state()')).hold).toBe(true);
  await L(page, 'LSD.hold(false)');
  r.releasedIn = await until(page, `LSD.state().name !== ${JSON.stringify(target.name)}`, 15);
  expect(r.releasedIn, 'a new scene after HOLD is released').not.toBeNull();
  await L(page, 'LSD.hold(true)');

  // A chain by id on the scene on air; unknown ids are refused.
  const chain = cat.fxChains.find((c: any) => c.inRotation) ?? cat.fxChains[0];
  expect(await L(page, `LSD.chain(${JSON.stringify(chain.id)})`)).toBe('ok');
  expect((await L<any>(page, 'LSD.state()')).chain).toBe(chain.name);
  expect(await L(page, `LSD.chain('no-such-chain')`)).toBe('unknown');

  // A locked chain follows every scene change.
  const other = cat.fxChains.find((c: any) => c.id !== chain.id);
  expect(await L(page, `LSD.lockChain(${JSON.stringify(other.id)})`)).toBe('ok');
  await L(page, 'LSD.next()');
  await page.waitForTimeout(5000);
  const locked = await L<any>(page, 'LSD.state()');
  r.lockedChain = { lockedChain: locked.lockedChain, chain: locked.chain };
  expect(locked.lockedChain).toBe(other.id);
  expect(locked.chain).toBe(other.name);
  await L(page, 'LSD.lockChain(null)');

  // Transitions lock (refuses unknown ids).
  expect(await L(page, `LSD.lockTransition(${JSON.stringify(cat.transitions[0].id)})`)).toBe('ok');
  expect((await L<any>(page, 'LSD.state()')).lockedTransition).toBe(cat.transitions[0].id);
  expect(await L(page, `LSD.lockTransition('nope')`)).toBe('unknown');
  await L(page, 'LSD.lockTransition(null)');

  // Effects off strips the chain; on puts one back.
  await L(page, 'LSD.effects(false)');
  const off = await L<any>(page, 'LSD.state()');
  expect(off.effects).toBe(false);
  expect(off.chain).toBeNull();
  expect(await L(page, `LSD.chain(${JSON.stringify(chain.id)})`)).toBe('off');
  await L(page, 'LSD.effects(true)');
  expect((await L<any>(page, 'LSD.state()')).effects).toBe(true);

  // Backgrounds: by index, refused when off, mode in state.
  const bg = cat.backgrounds[1];
  await L(page, `LSD.background(${bg.index})`);
  r.backgroundIn = await until(page, `LSD.state().background === ${JSON.stringify(bg.name)}`, 10);
  expect(r.backgroundIn, 'background changed').not.toBeNull();
  await L(page, `LSD.backgroundMode('off')`);
  expect((await L<any>(page, 'LSD.state()')).bgMode).toBe('off');
  expect(await L(page, 'LSD.background(0)')).toBe('off');
  await L(page, `LSD.backgroundMode('hold')`);

  // Skip the scene on air, then restore it.
  const before = (await L<any>(page, 'LSD.state()')).name;
  const sk = await L<any>(page, 'LSD.skip()');
  expect(sk.action).toBe('skipped');
  expect((await L<any>(page, 'LSD.catalog()')).scenes.find((s: any) => s.name === before).skipped).toBe(true);
  await L(page, `LSD.restore(${JSON.stringify(before)})`);
  expect((await L<any>(page, 'LSD.catalog()')).scenes.find((s: any) => s.name === before).skipped).toBe(false);

  // Time, auto roll, follow show in the state; the state was pushed to the host and matches.
  await L(page, 'LSD.time(8)'); await L(page, 'LSD.autoRoll(4)'); await L(page, 'LSD.follow(false)');
  await page.waitForTimeout(300);
  const st = await L<any>(page, 'LSD.state()');
  expect([st.holdBars, st.autoRollBars, st.follow]).toEqual([8, 4, false]);
  const pushed = await lastPushed(page);
  r.pushes = await page.evaluate(() => (window as any).__states.length);
  r.lastPushed = pushed;
  expect(pushed.holdBars).toBe(8);
  expect(pushed.follow).toBe(false);
  expect(typeof pushed.bpm).toBe('number');
  note('embed_controls', { ...r, errors });
  expect(errors).toEqual([]);
});

test('embed: a build with no photos never shows the gallery background; maxPixels raises the render budget', async ({ page, context }) => {
  only('chromium'); test.setTimeout(90_000);
  await sealOff(context);
  await page.route('**/gallery.json', (r) => r.fulfill({ json: { base: 'gallery/', images: [] } }));
  await page.setViewportSize({ width: 1920, height: 1080 });
  const errors = await openEmbed(page);
  const st = await L<any>(page, 'LSD.state()');
  const cat = await L<any>(page, 'LSD.catalog()');
  const gallery = cat.backgrounds.filter((b: any) => b.source === 'gallery');
  expect(gallery.length).toBeGreaterThan(0);
  expect(gallery.every((b: any) => b.runnable === false)).toBe(true);
  expect(cat.backgrounds.find((b: any) => b.name === st.background)?.source).not.toBe('gallery');
  await L(page, `LSD.backgroundMode('scenes')`);
  const before = await L<number[]>(page, 'LSD.renderSize()');
  await L(page, `LSD.setOption('maxPixels', 1920 * 1080)`);
  await page.waitForTimeout(1500);
  const after = await L<number[]>(page, 'LSD.renderSize()');
  note('embed_nophotos', { background: st.background, before, after, errors });
  expect(after[0] * after[1]).toBeGreaterThan(before[0] * before[1]);
  expect(errors).toEqual([]);
});
