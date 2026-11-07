// Helpers for the web TV suite: a page on the local server, no real network, errors collected, probes.
import { type Page, type BrowserContext, type Browser, test as base, expect } from '@playwright/test';
import fs from 'node:fs';
export const ORIGIN = 'http://127.0.0.1:8765', STREAM = 'http://127.0.0.1:8766';
let n = 0;
export const session = () => `s${process.pid}_${Date.now()}_${n++}`;
export const ctl = (s: string, mode: string) => fetch(`${STREAM}/__ctl/${s}/${mode}`, { method: 'POST' }).then((r) => r.json());
export const truth = (t: string) => JSON.parse(fs.readFileSync(`tests/web/.cache/${t}.json`, 'utf8'));
/** No request leaves the machine: the radio's status and schedule are fixtures, the relay and chat sockets stay silent or use `relay`. */
export async function sealOff(ctx: BrowserContext, opts: { relay?: (ws: any) => void; chat?: (ws: any) => void } = {}) {
  await ctx.route((u) => !['127.0.0.1'].includes(u.hostname), (route) => {
    const u = new URL(route.request().url());
    if (u.pathname.endsWith('status-json.xsl')) return route.fulfill({ json: { icestats: { source: [{ listeners: 3, listenurl: 'x/radio.ogg' }] } }, headers: { 'Access-Control-Allow-Origin': '*' } });
    return route.abort();
  });
  await ctx.routeWebSocket(/spaz\.org/, (ws) => {
    if (/lsd-relay/.test(ws.url())) opts.relay?.(ws); else opts.chat?.(ws);
  });
}
export type TV = { page: Page; session: string; errors: string[]; on: () => Promise<void>; lsd: <T>(f: string) => Promise<T> };
const NOISE = /Failed to load resource|ERR_FAILED|net::|WebSocket|access control|Access-Control|CORS/i;
export async function openTV(page: Page, q = '', { track = 'dance128', sess = session() } = {}): Promise<TV> {
  const errors: string[] = [];
  page.on("pageerror", (e) => { if (!NOISE.test(e.message)) errors.push("pageerror: " + e.message); });
  page.on('console', (m) => { if (m.type() === 'error' && !NOISE.test(m.text())) errors.push('console: ' + m.text()); });
  await page.goto(`${ORIGIN}/index.html?st=${sess}&track=${track}${q ? '&' + q : ''}`);
  await page.waitForFunction(() => (window as any).LSD, null, { timeout: 30000 });
  const lsd = <T>(f: string) => page.evaluate(f) as Promise<T>;
  return { page, session: sess, errors, lsd, on: async () => {
    await page.click('#tv-screen-area', { position: { x: 50, y: 50 } });
    await page.waitForFunction(() => (window as any).LSD.powerState.on && (window as any).LSD.powerState.warmupProgress >= 1, null, { timeout: 30000 });
  } };
}
/** Polls until f is true; returns the seconds it took, or null at the limit. */
export async function until(page: Page, f: string, limitS: number): Promise<number | null> {
  const t0 = Date.now();
  while ((Date.now() - t0) / 1000 < limitS) { if (await page.evaluate(f)) return (Date.now() - t0) / 1000; await page.waitForTimeout(100); }
  return null;
}
/** Mean, spread and lit share of the WebGL canvas from a screenshot-free read of the on-screen picture. */
export const picture = (page: Page) => page.evaluate(() => new Promise<any>((res) => requestAnimationFrame(() => {
  const c = document.getElementById('glCanvas') as HTMLCanvasElement; const o = document.createElement('canvas'); o.width = 64; o.height = 36;
  const x = o.getContext('2d')!; x.drawImage(c, 0, 0, 64, 36); const d = x.getImageData(0, 0, 64, 36).data; let s = 0, s2 = 0;
  const N = d.length / 4; for (let i = 0; i < d.length; i += 4) { const v = (d[i] + d[i + 1] + d[i + 2]) / 3; s += v; s2 += v * v; }
  res({ mean: s / N, sd: Math.sqrt(Math.max(0, s2 / N - (s / N) ** 2)) });
})));
export function note(name: string, data: unknown) {
  fs.mkdirSync('.planning/auto-verify-2026-11-06/round3/web/measure', { recursive: true });
  fs.writeFileSync(`.planning/auto-verify-2026-11-06/round3/web/measure/${name}.json`, JSON.stringify(data, null, 1));
  console.log(`[measure] ${name}: ${JSON.stringify(data).slice(0, 900)}`);
}
