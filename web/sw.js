// sw.js — lets the web TV install as an app and keep going without a network.
// Network first for everything on this site (so updated presets and code always land when online), with
// the last copy as the fallback; the app shell is cached at install, and all the content (every scene,
// shader, FX chain and transition the autopilot can pick) once the page asks (cacheContent). Gallery photos
// are kept as they are shown, the newest PHOTO_MAX of them, so the background still has photos offline. The radio stream, its status, the
// relay and the chat (broker and log) are other origins and are never touched. The archive shares this
// origin, so it is left out by path below.
const CACHE = 'lsd-tv-v2';
const PHOTOS = 'lsd-tv-photos';
const PHOTO_MAX = 60;           // about 8 MB of photos
const REVALIDATE_MS = 6 * 3600e3; // how often cached content is checked against the server
const SHELL = [
  './', 'index.html', 'tv.css', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png', 'favicon.png',
  'ui.js', 'renderer.js', 'autopilot.js', 'dsp.js', 'beatclock.js', 'beattrack.js', 'energy.js', 'onset-worklet.js',
  'evaluator.js', 'graph.js', 'isf.js', 'mandala.js', 'gallery.js', 'gallery.json', 'pairing.js', 'preset.js',
  'settings.json', 'catalog.json', 'mandala_recipes.json', 'playlists/default.lsdplay', 'playlists/default_bg.lsdplay',
  'shaders/blit.vert', 'shaders/blit.frag', 'shaders/mandala.vert', 'shaders/mandala.frag', 'shaders/mixer.frag',
  'shaders/crt_post.frag',
  'audiotex.js', 'sync.js', 'embed.js', 'embedmode.js', 'menu.js', 'schedule.js',
  'tapes.js', 'tapedeck.js', 'mqtt.js', 'chat.js', 'chatboard.js', 'fonts/caveat-latin.woff2', 'fonts/caveat-latin-ext.woff2',
];

self.addEventListener('install', (e) => {
  // One by one, so a single missing file (or a server that does not map ./ to index.html) does not stop the install.
  e.waitUntil(caches.open(CACHE)
    .then((c) => Promise.allSettled(SHELL.map((u) => c.add(u))))
    .then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== CACHE && k !== PHOTOS).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== self.location.origin) return;
  // The curating preview and the archive (tapes.js) are left to the browser: shows run to hundreds of MB, and
  // a seek's partial response can't be cached at all.
  if (/\/(curate|archives)\//.test(url.pathname)) return;
  if (/\/gallery\//.test(url.pathname)) { e.respondWith(photo(e.request)); return; }
  // spaz.org sends no Cache-Control (and ignores .htaccess), so browsers guess a lifetime from Last-Modified and
  // a reload after a publish could pair the new index.html with an old ui.js. Every file is checked with the
  // server instead (a cheap 304 when unchanged). A navigation can't take a cache mode; reloads revalidate it anyway.
  const req = e.request.mode === 'navigate' ? fetch(e.request) : fetch(e.request, { cache: 'no-cache' });
  e.respondWith(req.then((res) => {
    if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
    return res;
    // An address with options (?kiosk) is still the same page offline.
  }).catch(() => caches.match(e.request, { ignoreSearch: e.request.mode === 'navigate' }).then((hit) => hit || Response.error())));
});

// A gallery photo: the network, kept in PHOTOS (the newest PHOTO_MAX), or the kept copy when offline.
// Photos never change under the same name, so the browser's own cache is fine here.
async function photo(req) {
  try {
    const res = await fetch(req);
    if (res.ok) {
      const copy = res.clone();
      caches.open(PHOTOS).then(async (c) => {
        await c.delete(req);           // to the end of the list: keys() keeps insertion order
        await c.put(req, copy);
        const keys = await c.keys();
        for (const k of keys.slice(0, Math.max(0, keys.length - PHOTO_MAX))) await c.delete(k);
      }).catch(() => {});
    }
    return res;
  } catch (err) {
    const hit = await caches.match(req, { cacheName: PHOTOS });
    if (hit) return hit;
    throw err;
  }
}

// --- Content for offline use ---
// Everything the autopilot can load: every file in catalog.json (sources, effects, transitions, FX chains,
// transition presets) plus the presets named in the playlists settings.json points at.
const presetPath = (name) => {
  let p = name.trim();
  if (!/^(\/|http|library\/|presets\/)/.test(p)) p = `presets/${p}`;
  return /\.(lsd|json)$/.test(p) ? p : `${p}.lsd`;
};
const playlistPath = (name) => {
  let p = name.trim();
  if (!/^(\/|http|library\/|playlists\/)/.test(p)) p = `playlists/${p}`;
  return /\.(lsdplay|json)$/.test(p) ? p : `${p}.lsdplay`;
};
const fresh = (u) => fetch(u, { cache: 'no-cache' }).then((r) => { if (!r.ok) throw new Error(`${u}: ${r.status}`); return r; });

async function contentFiles() {
  const [catalog, settings] = await Promise.all([fresh('catalog.json').then((r) => r.json()), fresh('settings.json').then((r) => r.json())]);
  const files = new Set(['catalog.json', 'settings.json']);
  for (const list of Object.values(catalog)) for (const e of list ?? []) if (e?.file) files.add(e.file);
  for (const name of [settings.fgPlaylist, settings.bgPlaylist].filter(Boolean)) {
    const pl = playlistPath(name);
    files.add(pl);
    const text = await fresh(pl).then((r) => r.text()).catch(() => '');
    for (const line of text.split(/\r?\n/)) {
      const l = line.trim();
      if (l && !l.startsWith('#')) files.add(presetPath(l.split('|')[0]));
    }
  }
  return [...files];
}

// Fetches what is missing; every REVALIDATE_MS, checks the rest with the server too (a 304 when unchanged).
// One run at a time; the page asks at every start (ui.js).
let contentRun = null;
function cacheContent() {
  contentRun ??= (async () => {
    const cache = await caches.open(CACHE);
    const stampReq = new Request('__content-checked');
    const stamp = await cache.match(stampReq).then((r) => (r ? r.text() : '0')).then(Number);
    const revalidate = Date.now() - stamp > REVALIDATE_MS;
    const files = await contentFiles();
    const todo = [];
    for (const f of files) if (revalidate || !(await cache.match(f))) todo.push(f);
    let ok = 0;
    const worker = async () => {
      for (let f = todo.shift(); f !== undefined; f = todo.shift()) {
        try { await cache.put(f, await fresh(f)); ok++; } catch (err) { console.warn('[sw] content:', err.message); }
      }
    };
    const total = todo.length;
    await Promise.all(Array.from({ length: 4 }, worker));
    if (ok === total) await cache.put(stampReq, new Response(String(revalidate ? Date.now() : stamp || Date.now())));
    return { files: files.length, fetched: ok, wanted: total };
  })().finally(() => { contentRun = null; });
  return contentRun;
}

self.addEventListener('message', (e) => {
  if (e.data !== 'cache-content') return;
  const run = cacheContent().then((r) => { e.source?.postMessage({ type: 'content-cached', ...r }); })
    .catch((err) => console.warn('[sw] content:', err.message));
  e.waitUntil(run);
});
