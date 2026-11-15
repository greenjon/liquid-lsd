// index.html's <link rel="modulepreload"> list must be every module the page imports, so the browser asks for
// them all at once instead of finding them one import level (one round trip to spaz.org) at a time.
// node --test web/tools/preload.test.mjs   (print the list: node web/tools/preload.test.mjs --list)
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const html = fs.readFileSync(path.join(web, 'index.html'), 'utf8');

/** Every module reachable by static import from the page's module scripts, with its import depth. */
export function moduleGraph() {
  const entries = [...html.matchAll(/<script type="module" src="([^"]+)"/g)].map((m) => m[1]);
  const imports = /^\s*(?:import|export)\s[^;]*?from\s*['"](\.\/[^'"]+)['"]|^\s*import\s*['"](\.\/[^'"]+)['"]/gms;
  const depth = new Map();
  const queue = entries.map((f) => [f, 1]);
  while (queue.length) {
    const [file, d] = queue.shift();
    if (depth.has(file) && depth.get(file) <= d) continue;
    depth.set(file, d);
    const src = fs.readFileSync(path.join(web, file), 'utf8');
    for (const m of src.matchAll(imports)) queue.push([(m[1] || m[2]).slice(2), d + 1]);
  }
  return { entries, depth };
}

const preloaded = () => [...html.matchAll(/<link rel="modulepreload" href="([^"]+)">/g)].map((m) => m[1]);

if (process.argv.includes('--list')) {
  const { entries, depth } = moduleGraph();
  for (const [f, d] of [...depth].sort((a, b) => a[1] - b[1] || a[0].localeCompare(b[0]))) {
    console.log(`  <link rel="modulepreload" href="${f}">  <!-- depth ${d} -->`);
  }
}

test('index.html preloads every imported module, and nothing else', () => {
  const { entries, depth } = moduleGraph();
  const want = [...depth.keys()].sort();   // the entry scripts too: they sit at the end of <body>
  assert.ok(want.length > 10, 'module graph looks wrong');
  assert.deepEqual([...preloaded()].sort(), want,
    'update the modulepreload list in index.html (node web/tools/preload.test.mjs --list prints it)');
});

test('the startup files index.html preloads are the ones startup fetches', () => {
  const settings = JSON.parse(fs.readFileSync(path.join(web, 'settings.json'), 'utf8'));
  const fetched = [...html.matchAll(/<link rel="preload" href="([^"]+)" as="fetch" crossorigin>/g)].map((m) => m[1]);
  const renderer = fs.readFileSync(path.join(web, 'renderer.js'), 'utf8');
  const shaders = [...renderer.matchAll(/loadText\('([^']+)'\)/g)].map((m) => m[1]);
  assert.ok(shaders.length >= 6, 'renderer.js core shaders not found');
  assert.deepEqual(fetched.sort(), ['catalog.json', 'mandala_recipes.json', `playlists/${settings.fgPlaylist}`, 'settings.json', ...shaders].sort());
  for (const f of fetched) assert.ok(fs.existsSync(path.join(web, f)), `${f} is missing`);
});

test('nothing but tags in <head> (stray text there ends the head early and pushes the links into <body>)', () => {
  const head = html.slice(html.indexOf('<head>') + 6, html.indexOf('</head>')).replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<(script|style|title)\b[\s\S]*?<\/\1>/g, '');
  const stray = head.split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('<'));
  assert.deepEqual(stray, []);
});
