/* MRBD Playground — Service Worker: app-shell precache, cache-first for shell,
   network-first for everything else. */
var CACHE = 'mrbd-playground-v1';
var SHELL = ['./', './index.html', './styles.css', './app.js', './favicon.png', './manifest.webmanifest'];

self.addEventListener('install', function (e) {
  e.waitUntil(
    caches.open(CACHE).then(function (c) { return c.addAll(SHELL); }).then(function () {
      return self.skipWaiting();
    })
  );
});

self.addEventListener('activate', function (e) {
  e.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.filter(function (k) { return k !== CACHE; })
        .map(function (k) { return caches.delete(k); }));
    }).then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (e) {
  if (e.request.method !== 'GET') return;
  // Only handle same-origin shell assets; let API calls hit the network directly
  if (new URL(e.request.url).origin !== self.location.origin) return;
  e.respondWith(
    caches.match(e.request).then(function (cached) {
      if (cached) return cached;
      return fetch(e.request).catch(function () {
        return new Response('', { status: 503, statusText: 'offline' });
      });
    })
  );
});
