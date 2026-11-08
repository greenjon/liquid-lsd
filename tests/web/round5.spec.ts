// Round 5 checklist items: every key does its job, panels work with the TV off, branding, chat layout.
import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import { sealOff, openTV, until } from './tv';

const OUT = '.planning/auto-verify-2026-11-06/round5/web';
const save = (name: string, data: unknown) => {
  fs.mkdirSync(OUT, { recursive: true });
  fs.writeFileSync(`${OUT}/${name}.json`, JSON.stringify(data, null, 1));
};

/** What a key can change, read without touching the page. */
const snap = (page: Page) => page.evaluate(() => {
  const L = (window as any).LSD, a = L.autopilotState;
  const vis = (id: string) => { const e = document.getElementById(id); return !!e && !e.hidden && getComputedStyle(e).display !== 'none'; };
  const media = [...document.querySelectorAll('audio,video')].map((m: any) => `${m.muted ? 'M' : ''}${m.volume.toFixed(2)}`).join(',');
  return {
    ch: document.getElementById('osd-ch')?.textContent || '',
    msg: document.getElementById('osd-msg')?.textContent || '',
    scene: a.deckA?.name || a.deckA?.id || '',
    chain: `${a.deckA?.chainName}|${a.deckB?.chainName}`,
    hold: !!a.hold, info: !!L.viewerState.info, react: L.viewerState.react, media,
    fullscreen: !!document.fullscreenElement,
    menu: vis('tv-menu'), schedule: vis('schedule'), tapes: vis('tapes'), keys: vis('keys'), chat: vis('chalkboard'), about: vis('about'),
    vars: JSON.stringify(a.deckA?.params ?? a.deckA?.values ?? null).slice(0, 400),
  };
});

test('checklist: keys R J E U arrows H I = - N P M S T ? F C each do their job (C last: an open chat takes the typing)', async ({ page, context }) => {
  await sealOff(context);
  const tv = await openTV(page);
  await tv.on();
  await page.waitForTimeout(3000);
  const want: [string, string[]][] = [
    ['r', ['vars', 'msg']], ['j', ['ch', 'scene']], ['e', ['chain', 'msg']], ['u', ['media', 'msg']], ['u', ['media', 'msg']],
    ['ArrowUp', ['media', 'msg']], ['ArrowDown', ['media', 'msg']], ['h', ['hold']], ['h', ['hold']], ['i', ['info']], ['i', ['info']],
    ['=', ['react', 'msg']], ['-', ['react', 'msg']], ['n', ['ch', 'scene']], ['p', ['ch', 'scene']],
    ['m', ['menu']], ['Escape', ['menu']], ['s', ['schedule']], ['Escape', ['schedule']], ['t', ['tapes']], ['Escape', ['tapes']],
    ['?', ['keys']], ['Escape', ['keys']], ['f', ['fullscreen']], ['f', ['fullscreen']], ['c', ['chat']], ['c', ['chat']],
  ];
  const rows: any[] = [];
  for (const [k, fields] of want) {
    const before = await snap(page);
    await page.keyboard.press(k);
    await page.waitForTimeout(k === 'r' ? 4000 : 1200);
    const after = await snap(page);
    const changed = Object.keys(after).filter((f) => (after as any)[f] !== (before as any)[f]);
    rows.push({ key: k, want: fields, changed, ok: fields.some((f) => changed.includes(f)) });
  }
  save('keys', rows);
  expect(tv.errors).toEqual([]);
  const bad = rows.filter((r) => !r.ok).map((r) => `${r.key}: wanted ${r.want} changed ${r.changed}`);
  expect(bad, 'keys that changed nothing they should').toEqual([]);
});

test('checklist: with the TV off, MENU, SCHEDULE, TAPES and ? open and close', async ({ page, context }) => {
  await sealOff(context);
  const tv = await openTV(page);
  await page.waitForTimeout(1500);
  const rows: any[] = [];
  for (const [k, panel] of [['m', 'menu'], ['s', 'schedule'], ['t', 'tapes'], ['?', 'keys']] as [string, string][]) {
    await page.keyboard.press(k); await page.waitForTimeout(800);
    const open = (await snap(page) as any)[panel];
    await page.screenshot({ path: `${OUT}/off_${panel}.png` });
    await page.keyboard.press('Escape'); await page.waitForTimeout(600);
    const closed = !(await snap(page) as any)[panel];
    rows.push({ panel, open, closed, stillOff: !(await tv.lsd<boolean>('LSD.powerState.on')) });
  }
  save('power_off_pages', rows);
  expect(rows.filter((r) => !(r.open && r.closed && r.stillOff))).toEqual([]);
  expect(tv.errors).toEqual([]);
});

test('checklist: the nameplate opens the site in a new tab and leaves the TV on', async ({ page, context }) => {
  await sealOff(context);
  const tv = await openTV(page);
  await tv.on();
  const a = await page.evaluate(() => { const e = document.getElementById('nameplate') as HTMLAnchorElement; return { href: e.href, target: e.target, rel: e.rel }; });
  const popup = context.waitForEvent('page', { timeout: 5000 }).catch(() => null);
  await page.click('#nameplate');
  const p = await popup;
  const stillOn = await tv.lsd<boolean>('LSD.powerState.on');
  // The about panel's QR: present, square, and the link beside it names the site.
  await page.keyboard.press('m'); await page.waitForTimeout(500);
  const qr = await page.evaluate(() => { const s = document.querySelector('#about .ab-qr') as SVGElement; return s ? { label: s.getAttribute('aria-label'), box: s.getAttribute('viewBox') } : null; });
  save('branding', { nameplate: a, newTab: p?.url() ?? null, stillOn, qr });
  expect(a.target).toBe('_blank');
  expect(p, 'a new tab opened').not.toBeNull();
  expect(stillOn).toBe(true);
  expect(qr?.label).toContain('liquid-lsd.greenjon.com');
});

for (const [name, w, h] of [['1280x720', 1280, 720], ['phone-portrait', 412, 915]] as [string, number, number][]) {
  test(`checklist: chat blackboard layout ${name} (socket blocked)`, async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: w, height: h }, hasTouch: w < 600, isMobile: w < 600 });
    await sealOff(ctx);
    const page = await ctx.newPage();
    const tv = await openTV(page);
    await tv.on();
    await page.waitForTimeout(1500);
    const s0 = await snap(page);
    if (!s0.chat) { await page.keyboard.press('c'); await page.waitForTimeout(800); }
    await page.screenshot({ path: `${OUT}/chat_${name}.png` });
    const geo = await page.evaluate(() => {
      const r = (id: string) => { const e = document.getElementById(id); if (!e || e.hidden) return null; const b = e.getBoundingClientRect(); return [Math.round(b.x), Math.round(b.y), Math.round(b.width), Math.round(b.height)]; };
      return { chalk: r('chalkboard'), canvas: r('glCanvas'), vw: innerWidth, vh: innerHeight, scrollW: document.documentElement.scrollWidth };
    });
    save(`chat_${name}`, { openAtStart: s0.chat, geo });
    expect(geo.chalk, 'blackboard shown').not.toBeNull();
    expect(geo.scrollW, 'no sideways scroll').toBeLessThanOrEqual(geo.vw + 1);
    expect(tv.errors).toEqual([]);
    await ctx.close();
  });
}
