# Deploy (VPS behind the host's nginx)

Public entry: `https://ranselapi.mietek.tech` -> host nginx -> `127.0.0.1:8080` (server container) -> Postgres container (private).

## One-time setup on the VPS (needs sudo)

```
sudo cp deploy/nginx/ranselapi.mietek.tech.conf /etc/nginx/sites-available/ranselapi.mietek.tech
sudo ln -s /etc/nginx/sites-available/ranselapi.mietek.tech /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d ranselapi.mietek.tech
```

The user that deploys (`sen`) must be in the `docker` group and own the deploy folder. The default is `/opt/ransel` (override with the repository variable `DEPLOY_PATH`):

```
sudo mkdir -p /opt/ransel && sudo chown -R sen:sen /opt/ransel
```

## GitHub secrets (Settings -> Secrets and variables -> Actions)

| Secret | Value |
| --- | --- |
| `DEPLOY_SSH_KEY` | private key whose public half is in the VPS `authorized_keys` |
| `DEPLOY_KNOWN_HOSTS` | output of `ssh-keyscan -p <port> -t ed25519 <host>` (verify the fingerprint) |
| `DEPLOY_HOST`, `DEPLOY_PORT`, `DEPLOY_USER` | VPS address, SSH port, SSH user |
| `JWT_SECRET` | 32+ random characters (`openssl rand -hex 32`) |
| `PHOTO_SIGNING_SECRET` | 32+ random characters, different from `JWT_SECRET` (`openssl rand -hex 32`); signs place-photo links |
| `DATABASE_PASSWORD` | random, letters and digits (`openssl rand -hex 24`) |
| `SEED_USER_EMAIL`, `SEED_USER_PASSWORD`, `SEED_USER_NAME` | the closed-beta account |
| `LLM_API_KEY`, `PLACES_API_KEY` (optional until Explore goes live) | Gemini API key (Google AI Studio) and Google Places (New) key. Set the repository *variable* `LLM_MODEL` to a model id your key can call (`curl -H "x-goog-api-key: $KEY" https://generativelanguage.googleapis.com/v1beta/models`). Without them Explore answers 503 `AI_UNAVAILABLE`. Restrict the Places key to the Places API and set a **budget alert** in Google Cloud |
| `SEED_USERS` (optional) | more accounts: `email\|password\|name;email\|password\|name`. Use letters and digits in passwords (no `\|`, `;` or `$`) |

### Place photo quota (do this before enabling photos)

Cards show Google place photos through the signed proxy `GET /api/v1/places/photo/{placeId}`. Each uncached photo is a billed Place Photo request (1,000 per month are free). In Google Cloud Console → APIs & Services → Places API (New) → Quotas, cap *Place Photo requests per day* at about 30 (1,000 / 30 days ≈ 33), and keep the budget alert from above. When the cap is hit the proxy answers 404 and the app falls back to the category colour; the counters `placesPhoto` and `placesPhotoQuotaHits` appear in the hourly `ai.metrics` log line.

## Backups

`deploy/backup-db.sh` dumps the database into `backups/` and keeps 14 days. Add to the deploy user's crontab (`crontab -e`):

```
15 3 * * * cd /opt/ransel && ./deploy/backup-db.sh >> backup.log 2>&1
```

Copying the dumps off the VPS (rsync/rclone to another machine or bucket) is a separate step.

## Rollback

Every deploy tags the image with the commit SHA. On the VPS: edit `SERVER_IMAGE` in `.env` to an older SHA and run
`docker compose -f docker-compose.prod.yml up -d`.

## Web host and invite links (`ransel.mietek.tech`)

One-time, like the API host: copy `deploy/nginx/ransel.mietek.tech.conf` to `sites-available`, enable it, `nginx -t`, reload, then `sudo certbot --nginx -d ransel.mietek.tech`. It serves the web app from `/opt/ransel/web` (single-page fallback, so `/j/<code>` opens the app) and forwards `/.well-known/` to the API.

Gotchas from the first setup:

- **Symlink into `sites-enabled` before running certbot** (`sudo ln -s /etc/nginx/sites-available/ransel.mietek.tech.conf /etc/nginx/sites-enabled/ransel.mietek.tech`). Otherwise certbot attaches the certificate to the `default` site with `return 404` and every path answers 404.
- After certbot, the 443 block may contain `if ($host = ransel.mietek.tech) { return 301 https://$host$request_uri; }`. Delete it from the 443 block (keep it in the port 80 block); left there it is a redirect loop.
- SSH to the VPS listens on port 2212, not 22.
- `index.html` has `<base href="/">`, so the script, `.wasm` files and resources also load on deep links like `/j/<code>`.

### Publishing the web build

No workflow deploys the web app yet. By hand, from the repo root:

```
./gradlew :composeApp:wasmJsBrowserDistribution
rsync -a --delete composeApp/build/dist/wasmJs/productionExecutable/ <vps>:/opt/ransel/web/
```

`index.html` points the app at `https://ranselapi.mietek.tech` when it is served from `ransel.mietek.tech` (the API's
`CORS_ORIGINS` already allows that origin). About 21 MB, mostly two `.wasm` files; nginx should serve them gzip-compressed.

### App Links / Universal Links

The API serves `/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association` (values from the
repository variables *or secrets* `ANDROID_CERT_SHA256` and `IOS_APP_IDS`; both answer 404 until set). Phones fetch them from
the **host of the invite link**, so that host's nginx must forward them to the API:

```
location /.well-known/ { proxy_pass http://127.0.0.1:8080; }
```

`ANDROID_CERT_SHA256` is a comma-separated list: put the release keystore fingerprint and, for builds installed from Android
Studio, the debug keystore fingerprint (`keytool -list -v -keystore ~/.android/debug.keystore -storepass android`). Remove the
debug one before a public release. Changing a repository variable does not start a deploy: run *Deploy server* by hand
(`gh workflow run deploy-server.yml --ref main`) or push a change under `server/`, `shared/` or `deploy/`. Check with
`curl https://ransel.mietek.tech/.well-known/assetlinks.json`, and on the phone
`adb shell pm verify-app-links --re-verify app.ransel`, then `adb shell pm get-app-links app.ransel`.

Invite URLs returned by the API are `APP_BASE_URL/j/<code>`.
