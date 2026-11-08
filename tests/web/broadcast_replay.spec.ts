// Replays a broadcast recorded from the real desktop app (round5/relay.py) into the web TV through a routed relay socket.
// Skipped unless BCAST_RECORDING names the .jsonl file.
import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import { sealOff, openTV, until, picture } from './tv';

const FILE = process.env.BCAST_RECORDING;
test('desktop broadcast replayed into the web TV: LIVE, the broadcaster name, the desktop decks and mixer', async ({ page, context }) => {
  test.skip(!FILE || test.info().project.name !== 'chromium');
  test.setTimeout(120_000);
  const msgs = fs.readFileSync(FILE!, 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l)).filter((x) => x.msg);
  let sock: any = null;
  await sealOff(context, { relay: (ws) => { sock = ws; } });
  const tv = await openTV(page);
  await tv.on();
  const t0 = Date.now(); while (!sock && Date.now() - t0 < 20000) await page.waitForTimeout(200);
  expect(sock, 'the TV opens the relay socket').not.toBeNull();
  // Real timing, capped so the test stays short: the first 30 s of the recording.
  const start = msgs[0].t;
  const replay0 = Date.now();
  for (const m of msgs) {
    if (m.t - start > 30) break;
    const wait = (m.t - start) * 1000 - (Date.now() - replay0);
    if (wait > 5) await page.waitForTimeout(wait);
    sock.send(m.msg);
  }
  const card = await until(page, "document.body.innerText.includes('Overnight Test')", 5);
  const st = await page.evaluate(() => { const A = (window as any).LSD.autopilotState; return { live: A.isLiveBroadcast, deckA: A.deckA?.source ?? (A.deckA?.empty ? 'empty' : null), deckB: A.deckB?.source, balance: A.mixer?.balance, fx: Object.values(A.mixer?.fx ?? {}).map((f: any) => f?.id), broadcaster: A.broadcaster }; });
  await page.waitForTimeout(1500);
  const pic = await picture(page);
  await page.screenshot({ path: '.planning/auto-verify-2026-11-06/round5/web/broadcast_replay.png' });
  fs.writeFileSync('.planning/auto-verify-2026-11-06/round5/web/broadcast_replay.json', JSON.stringify({ sent: msgs.length, st, pic, card: card !== null, errors: tv.errors }, null, 1));
  expect(st.live).toBe(true);
  expect(st.broadcaster).toBe('Overnight Test');
  expect(st.deckB).toBe('aurora_ribbons');
  expect(pic.mean, 'the picture isn\'t black').toBeGreaterThan(3);
  expect(tv.errors).toEqual([]);
});
