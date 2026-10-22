// Service worker for the installable web app (PWA). It caches the app shell only: the web app stays online-only,
// and API calls go to another host (ranselapi.mietek.tech), which this worker never touches.
// __BUILD_ID__ is replaced at build time (composeApp/build.gradle.kts), so every deploy gets a fresh cache and the
// changed bytes make the browser install the new worker.
const CACHE = 'ransel-shell-__BUILD_ID__';

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (event) => {
    event.waitUntil((async () => {
        const keys = await caches.keys();
        await Promise.all(keys.filter((k) => k.startsWith('ransel-shell-') && k !== CACHE).map((k) => caches.delete(k)));
        await self.clients.claim();
    })());
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    if (request.method !== 'GET') return;
    const url = new URL(request.url);
    if (url.origin !== self.location.origin || url.pathname.startsWith('/.well-known/')) return;

    if (request.mode === 'navigate') {
        // Every page, including invite links /j/<code>, is the single-page app.
        event.respondWith(networkFirst(request, '/index.html'));
    } else if (/\.(wasm|ttf|otf|woff2?)$/.test(url.pathname)) {
        // .wasm names are content hashes and fonts do not change: never refetch.
        event.respondWith(cacheFirst(request));
    } else {
        // composeApp.js, the manifest and Compose resources keep their names across builds.
        event.respondWith(networkFirst(request, url.pathname));
    }
});

async function cacheFirst(request) {
    const cache = await caches.open(CACHE);
    const cached = await cache.match(request);
    if (cached) return cached;
    const response = await fetch(request);
    if (response.ok) await cache.put(request, response.clone());
    return response;
}

async function networkFirst(request, cacheKey) {
    const cache = await caches.open(CACHE);
    try {
        const response = await fetch(request);
        if (response.ok) await cache.put(cacheKey, response.clone());
        return response;
    } catch (error) {
        const cached = await cache.match(cacheKey);
        if (cached) return cached;
        throw error;
    }
}
