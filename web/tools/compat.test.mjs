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

// CSS newer than Safari 15.0. A browser that does not know a selector or value drops the whole rule, so a use
// is only allowed when the page still works without that rule, and it is named in CSS_ALLOWED with the reason.
// [pattern, id, what it is, the Safari version that has it]
const CSS_BANNED = [
  [/:has\(/, ':has', ':has()', '15.4'],
  [/:focus-visible/, ':focus-visible', ':focus-visible', '15.4'],
  [/overflow-wrap:\s*anywhere/, 'overflow-wrap-anywhere', 'overflow-wrap: anywhere', '15.4'],
  [/\b\d*\.?\d+(dvh|svh|lvh|dvw|svw|lvw)\b/, 'viewport-units', 'dvh/svh/lvh units', '15.4'],
  [/@layer\b/, '@layer', '@layer', '15.4'],
  [/accent-color\s*:/, 'accent-color', 'accent-color', '15.4'],
  [/last baseline/, 'last-baseline', 'last baseline alignment', '16'],
  [/@container\b/, '@container', '@container', '16'],
  [/\bsubgrid\b/, 'subgrid', 'subgrid', '16'],
  [/color-mix\(/, 'color-mix', 'color-mix()', '16.2'],
  [/@property\b/, '@property', '@property', '16.4'],
  [/\blight-dark\(/, 'light-dark', 'light-dark()', '18'],
  [/text-wrap\s*:/, 'text-wrap', 'text-wrap', '17.4'],
];
// pattern id -> why web/tv.css may use it anyway: each is a rule that only polishes, older Safari drops it
// and the page stays usable. An entry tv.css no longer uses fails the test, so the list stays honest.
const CSS_ALLOWED = {
  ':has': 'the OSD radio block shifts left only while a channel badge shows (hidden since TV v68); below 15.4 it keeps its default place',
  ':focus-visible': 'keyboard focus rings; below 15.4 the rule is dropped and the browser default focus ring applies',
  'overflow-wrap-anywhere': 'long track titles may wrap at any character; below 15.4 they wrap at spaces as normal',
  'last-baseline': 'a second declaration after a plain baseline one, so below 16 the first value stands',
};

/** CSS with comments removed. */
const css = (src) => src.replace(/\/\*[\s\S]*?\*\//g, '');

test('the stylesheet uses nothing newer than Safari 15.0 unless allowed with a reason', () => {
  const problems = [], used = new Set();
  const src = css(fs.readFileSync(path.join(web, 'tv.css'), 'utf8'));
  src.split('\n').forEach((line, i) => {
    for (const [re, id, what, safari] of CSS_BANNED) {
      if (!re.test(line)) continue;
      used.add(id);
      if (!CSS_ALLOWED[id]) problems.push(`tv.css:${i + 1}: ${what} (Safari ${safari}) is not in CSS_ALLOWED: ${line.trim()}`);
    }
  });
  for (const id of Object.keys(CSS_ALLOWED)) if (!used.has(id)) problems.push(`CSS_ALLOWED has "${id}" but tv.css no longer uses it: remove it`);
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
