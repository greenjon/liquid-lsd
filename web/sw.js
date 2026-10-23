// sw.js — lets the web TV install as an app and start without a network.
// Network first for everything on this site (so updated presets and code always land when online), with
// the last copy as the fallback; the app shell is cached at install. The radio stream, its status and
// the relay are other origins and are never touched.
const CACHE = 'lsd-tv-v1';
const SHELL = [
  './', 'index.html', 'tv.css', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png', 'favicon.png',
  'ui.js', 'renderer.js', 'autopilot.js', 'dsp.js', 'beatclock.js', 'beattrack.js', 'energy.js', 'onset-worklet.js',
  'evaluator.js', 'graph.js', 'isf.js', 'mandala.js', 'pairing.js', 'preset.js',
  'settings.json', 'catalog.json', 'mandala_recipes.json', 'playlists/default.lsdplay', 'playlists/default_bg.lsdplay',
  'shaders/blit.vert', 'shaders/blit.frag', 'shaders/mandala.vert', 'shaders/mandala.frag', 'shaders/mixer.frag',
  'shaders/crt_post.frag',
];

self.addEventListener('install', (e) => {
  // One by one, so a single missing file (or a server that does not map ./ to index.html) does not stop the install.
  e.waitUntil(caches.open(CACHE)
    .then((c) => Promise.allSettled(SHELL.map((u) => c.add(u))))
    .then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== self.location.origin) return;
  e.respondWith(fetch(e.request).then((res) => {
    if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
    return res;
  }).catch(() => caches.match(e.request).then((hit) => hit || Response.error())));
});
