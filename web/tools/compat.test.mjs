// The web TV's oldest browser is Safari 15.0 (iOS 15: iPhone 6s/7, iPad Air 2, iPad mini 4 stop there, and
// Safari 15 is the first with WebGL 2). Chrome and Firefox of the same age (2021) are fine. A script that calls
// something newer fails on those devices, so this test refuses the newer calls and syntax that are easy to
// reach for. Lite mode (lite.js) needs the same floor: every module loads with the page.
// node --test web/tools/compat.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

// [pattern, what it is, the Safari version that has it]
const BANNED = [
  [/\.at\(/, 'Array/String .at()', '15.4'],
  [/\bfindLast(Index)?\(/, 'findLast / findLastIndex', '15.4'],
  [/\bstructuredClone\(/, 'structuredClone', '15.4'],
  [/\bObject\.hasOwn\(/, 'Object.hasOwn', '15.4'],
  [/\brandomUUID\(/, 'crypto.randomUUID', '15.4'],
  [/\.(toSorted|toReversed|toSpliced)\(/, 'change-array-by-copy', '16'],
  [/\bstatic\s*\{/, 'class static block', '16.4'],
  [/\(\?<[=!]/, 'regex lookbehind', '16.4'],
  [/\bAbortSignal\.any\(/, 'AbortSignal.any', '17.4'],
  [/\b(Object|Map)\.groupBy\(/, 'Object/Map.groupBy', '17.4'],
  [/\bPromise\.withResolvers\(/, 'Promise.withResolvers', '17.4'],
  [/\bArray\.fromAsync\(/, 'Array.fromAsync', '16.4'],
  [/\brequestIdleCallback\(/, 'requestIdleCallback', 'none'],
];
// Newer calls that are fine behind a feature test in the same file.
const GUARDED = [
  [/\bAbortSignal\.timeout\(/, /typeof AbortSignal !== 'undefined' && AbortSignal\.timeout/, 'AbortSignal.timeout', '16'],
];

/** The code without comments (string contents stay; URLs keep their // because no space comes before it). */
const code = (src) => src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|\s)\/\/.*$/gm, '$1');

test('the page uses nothing newer than Safari 15.0', () => {
  const problems = [];
  for (const file of fs.readdirSync(web).filter((f) => f.endsWith('.js')).sort()) {
    const src = code(fs.readFileSync(path.join(web, file), 'utf8'));
    src.split('\n').forEach((line, i) => {
      for (const [re, what, safari] of BANNED) if (re.test(line)) problems.push(`${file}:${i + 1}: ${what} (Safari ${safari}): ${line.trim()}`);
    });
    for (const [re, guard, what, safari] of GUARDED) {
      if (re.test(src) && !guard.test(src)) problems.push(`${file}: ${what} (Safari ${safari}) without a feature test`);
    }
  }
  assert.deepEqual(problems, [], `\n${problems.join('\n')}\n`);
});

// Lite mode is for old and slow devices, often on slow or metered connections: its photos stay small.
// (web/gallery/ is not in git; this checks the local build that publish_web_tv.sh uploads.)
test('the lite photos stay small', { skip: !fs.existsSync(path.join(web, 'gallery', 'lite')) && 'no web/gallery/lite (gallery_curate.py lite)' }, () => {
  const dir = path.join(web, 'gallery', 'lite');
  const sizes = fs.readdirSync(dir).map((f) => [f, fs.statSync(path.join(dir, f)).size]);
  const big = sizes.filter(([, n]) => n > 120 * 1024).map(([f, n]) => `${f} ${Math.round(n / 1024)} KB`);
  assert.deepEqual(big, [], 'lite photos over 120 KB');
  const total = sizes.reduce((a, [, n]) => a + n, 0);
  assert.ok(total < 6 * 2 ** 20, `lite photos total ${(total / 2 ** 20).toFixed(1)} MB (cap 6 MB)`);
  const manifest = JSON.parse(fs.readFileSync(path.join(web, 'gallery.json'), 'utf8'));
  assert.equal(manifest.lite, 'gallery/lite/', 'gallery.json names the lite folder');
});
