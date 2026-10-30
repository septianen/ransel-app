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
| `SEED_USER_EMAIL`, `SEED_USER_PASSWORD`, `SEED_USER_NAME` | an account created at startup (already verified) |
| `SMTP_USERNAME`, `SMTP_PASSWORD` (secrets) and `SMTP_HOST`, `SMTP_PORT` (default 587), `EMAIL_FROM` (default `RANSEL <no-reply@mietek.tech>`) (variables) | outgoing email over the provider's SMTP relay: the verification link after sign-up and password reset. Without `SMTP_HOST` nothing is sent, so new accounts cannot verify and never reach Explore. See "Email" below |
| `REGISTRATION_ENABLED` (variable, optional) | `false` closes sign-up again (only seeded accounts); unset = open |
| `LLM_API_KEY`, `PLACES_API_KEY` (optional until Explore goes live) | Gemini API key (Google AI Studio) and Google Places (New) key. Set the repository *variable* `LLM_MODEL` to a model id your key can call (`curl -H "x-goog-api-key: $KEY" https://generativelanguage.googleapis.com/v1beta/models`). Without them Explore answers 503 `AI_UNAVAILABLE`. Restrict the Places key to the Places API and set a **budget alert** in Google Cloud |
| `SEED_USERS` (optional) | more accounts: `email\|password\|name;email\|password\|name`. Use letters and digits in passwords (no `\|`, `;` or `$`) |

| `FCM_SERVICE_ACCOUNT_JSON` (secret), `FCM_PROJECT_ID` (variable), optional until push goes live | Firebase service-account key and project id for push notifications. Store the key as **base64 on one line** (`base64 -i key.json \| tr -d '\n'`) so `.env` and the secret survive the quotes in JSON. Without them pushes are only logged; the in-app inbox works either way. See "Push notifications" below |

**Changing a secret or variable does not change the running server.** The values are written to the VPS by the deploy job, so after editing one (a new `LLM_API_KEY`, say) run the *Deploy server* workflow again: Actions -> Deploy server -> Run workflow on `main` (it has `workflow_dispatch`), or push any change under `server/`, `shared/` or `deploy/`.

### Email

Sign-up is open and every new account gets an email with a verification link (`https://ransel.mietek.tech/verify-email?token=...`, 24 hours). Until the link is opened the account works, except Explore and vouchers, and the 30 signup credits wait. The server sends mail over SMTP, so any provider with an SMTP relay works (Brevo, Resend, Amazon SES, a mailbox host); `no-reply@mietek.tech` does not need a real mailbox, only a verified sending domain:

1. Create the account at the provider and add `mietek.tech` as a sending domain.
2. Add the DNS records it asks for at the domain's DNS host: SPF (TXT on `mietek.tech`, merge with an existing `v=spf1` record instead of adding a second one), DKIM (usually one or more CNAME/TXT records), and a DMARC record (`_dmarc.mietek.tech TXT "v=DMARC1; p=none"` is a safe start). Wait until the provider shows the domain as verified.
3. Create SMTP credentials at the provider and set them in GitHub: secrets `SMTP_USERNAME`, `SMTP_PASSWORD`; variables `SMTP_HOST`, `SMTP_PORT` (587 for STARTTLS, 465 for TLS), optionally `EMAIL_FROM`.
4. Run *Deploy server*. Check: sign up with your own address, the email arrives (look in spam the first time), the link opens "Email terverifikasi".

With SMTP set, Karung's "send password reset" turns on by itself (`ADMIN_PASSWORD_RESET_ENABLED` overrides).

### Place photo quota (do this before enabling photos)

Cards show Google place photos through the signed proxy `GET /api/v1/places/photo/{placeId}`. Each uncached photo is a billed Place Photo request (1,000 per month are free). In Google Cloud Console → APIs & Services → Places API (New) → Quotas, cap *Place Photo requests per day* at about 30 (1,000 / 30 days ≈ 33), and keep the budget alert from above. When the cap is hit the proxy answers 404 and the app falls back to the category colour; the counters `placesPhoto` and `placesPhotoQuotaHits` appear in the hourly `ai.metrics` log line.

## Firebase (push notifications, Crashlytics, Analytics)

The server and the apps are ready and stay quiet until these steps are done.

1. https://console.firebase.google.com -> Add project ("ransel"). Turn **Google Analytics on** (Project settings -> Integrations -> Google Analytics if the project already exists); the apps send screen views and the PRD-metric events (`docs/TRD.md` "Testing, deploy, and observability").
2. Project settings -> Your apps -> Add Android app, package `app.ransel` (release build) with the SHA-1/SHA-256 of the release / Play App Signing key, and a second Android app, package `app.ransel.debug` (debug build, talks to staging) with the SHA-1/SHA-256 of the debug key (`./gradlew :composeApp:signingReport`).
3. Download `google-services.json` into `composeApp/` (it is git-ignored; it must list both packages, otherwise the debug build fails in `processDebugGoogleServices`). Build again: `./gradlew :composeApp:assembleDebug`. Without the file the app builds but `PushRegistrar.available` is false.
4. Project settings -> Service accounts -> Generate new private key. Put `base64 -i key.json | tr -d '\n'` in the GitHub secret `FCM_SERVICE_ACCOUNT_JSON` and the project id in the repository variable `FCM_PROJECT_ID`, then run the *Deploy server* workflow again. The server log then says `push.sent` instead of `push.logged`.
5. Android 13+ asks for the notification permission once, after the first sign-in. If it is refused the inbox still works.
6. Crashlytics: open Crashlytics in the console once (it waits for the first crash). Debug and release builds both report (debug as the app `app.ransel.debug`), and the Gradle plugin uploads the R8 mapping file with each release build, so `assembleRelease` needs network access to Firebase. Try it: install a debug build, `adb shell am crash app.ransel.debug`, open the app again, and the crash shows up within a few minutes.
7. Analytics on Android: debug and release builds both send; debug shows up as the app `app.ransel.debug`, so pick the app (stream) in the console to keep test data apart. To watch events live in DebugView: `adb shell setprop debug.firebase.analytics.app app.ransel.debug` (or `app.ransel` for a release build) (back to normal: `.none.`).
8. Analytics on the web: Project settings -> Your apps -> Add app -> Web ("RANSEL web"), with Analytics on. Copy `apiKey`, `appId` and `measurementId` into `firebaseConfig` in `composeApp/src/wasmJsMain/resources/index.html` (public client config, not a secret) and deploy the web app. It only sends from `ransel.mietek.tech`; staging and localhost stay silent.
9. iOS (later, on a Mac): add the iOS app (bundle id `app.ransel`) in Firebase, upload an APNs auth key (Apple Developer -> Keys) under Cloud Messaging, add the Firebase iOS SDK (Messaging, Analytics, Crashlytics) and the Push Notifications capability to the Xcode project, and replace `NoPushRegistrar`, `NoAnalytics` and `NoCrashReporter` in `Platform.ios.kt`.

Reminders run on the server every minute and use WIB for every trip: the packing reminder at 18:00 the day before departure (`NOTIFY_PACKING_HOUR`), the activity reminder 30 minutes before it starts (`NOTIFY_ACTIVITY_LEAD_MINUTES`). A trip in WITA/WIT gets them 1-2 hours off until trips have a timezone.

## Android builds (Firebase App Distribution)

The *Distribute Android* workflow (`.github/workflows/distribute-android.yml`) builds an APK and sends it to testers through Firebase App Distribution (they install it from the email link or the *App Tester* app):

| Trigger | Build | App | API |
| --- | --- | --- | --- |
| push to `develop` (app, `iris`, `shared` or Gradle changes), or *Run workflow* with target `staging` | debug | "RANSEL Dev" `app.ransel.debug` | `ranselapi-dev.mietek.tech` |
| push to `main`, or *Run workflow* on `main` with target `production` | release (R8, signed) | "RANSEL" `app.ransel` | `ranselapi.mietek.tech` |

`versionCode` is the workflow's run number, so every build installs over the previous one; the release notes are the commit message (or the text given to *Run workflow*). Each run also keeps the APK as a workflow artifact for 14 days.

One-time setup:

1. Firebase console -> the RANSEL project -> **App Distribution** -> Get started. Register both Android apps if they are not there yet (`app.ransel`, `app.ransel.debug`; see "Firebase" above). Under *Testers & Groups* create the group `testers` (or another alias, see below) and add the testers' emails.
2. Google Cloud console (same project) -> IAM -> Service accounts -> create `app-distribution` with the role **Firebase App Distribution Admin**, add a JSON key, and paste the whole file as the secret `FIREBASE_DISTRIBUTION_SERVICE_ACCOUNT` (base64 works too).
3. GitHub (Settings -> Secrets and variables -> Actions):

| Name | Kind | What |
| --- | --- | --- |
| `FIREBASE_APP_ID_DEBUG` | variable | App ID of `app.ransel.debug` (Firebase -> Project settings -> Your apps, `1:…:android:…`) |
| `FIREBASE_APP_ID_RELEASE` | variable | App ID of `app.ransel` |
| `FIREBASE_TESTER_GROUPS` | variable, optional | Group aliases, comma separated (default `testers`) |
| `FIREBASE_DISTRIBUTION_SERVICE_ACCOUNT` | secret | The service-account key file from step 2, pasted as is (or base64) |
| `GOOGLE_SERVICES_JSON` | secret | `composeApp/google-services.json` (both packages), base64; without it the APK has no push, Analytics or Crashlytics |
| `DEBUG_KEYSTORE_BASE64` | secret | Your `~/.android/debug.keystore`, base64, so every staging build has the same signature (testers can update, and it matches your local debug builds). Without it each build must be uninstalled first |
| `RANSEL_KEYSTORE_BASE64` | secret | The release keystore (`keystore.properties.example`), base64; required for production |
| `RANSEL_KEYSTORE_PASSWORD`, `RANSEL_KEY_ALIAS`, `RANSEL_KEY_PASSWORD` | secrets | Its passwords and alias |

Keep the release keystore and its passwords safe outside git: the Play Store later needs the same key (or Play App Signing's upload key), and an app signed with another key cannot update the installed one. For push in the debug app, add its SHA fingerprints in Firebase only if a feature needs them (FCM does not).

## Explore credits and vouchers

Every user has a wallet (30 credits on first use) plus 3 free credits a day (resets 00:00 WIB). A new search costs 3, the next batch 1. All numbers and the pack list can be changed in the VPS `.env` (`CREDIT_SIGNUP_GRANT`, `CREDIT_DAILY_FREE`, `CREDIT_COST_SEARCH`, `CREDIT_COST_NEXT_BATCH`, `CREDIT_COST_PLACE_DETAIL`, `CREDIT_COST_RECEIPT` (one receipt photo, default 1), `CREDIT_PACKAGES=id:credits:priceMinor;...`); restart the server container after changing them. These env values are the defaults: an admin can override each credit number (receipt photo included) in Karung, Sistem > Pengaturan, without a restart; "Kembalikan bawaan" goes back to the env value. Nothing can be bought yet: until a payment provider exists, top up users with a voucher. On the VPS:

```
./deploy/create-voucher.sh 15            # 15 credits, one use, valid 30 days
./deploy/create-voucher.sh 40 5 60       # 40 credits, five users, valid 60 days
```

It prints the code (`ABCD-EFGH-JKLM`); the user enters it under Account > Credits. One user can redeem a code once. To revoke: `UPDATE credit_vouchers SET revoked_at = now() WHERE code = '...'`. Watch the real cost per search in the hourly `ai.metrics` log line before changing the pack prices.

## Admin app (Karung)

Admins are the accounts in `ADMIN_EMAILS` (comma separated, case-insensitive). The deploy workflow rewrites the VPS `.env` on every deploy, so set it as the GitHub **repository variable** `ADMIN_EMAILS` (Settings > Secrets and variables > Actions > Variables), not by editing `.env`; it takes effect with the next *Deploy server* run. The server checks the list on every `/admin` request, so removing an email locks that person out at once. Set `ADMIN_APP_ORIGIN=https://karung.mietek.tech` so the browser may call the API. `APP_ENV` (`production` / `staging`) is shown as a badge in Karung. Karung manages both servers: pick Production or Staging on its sign-in screen (switching signs out). To use Staging, your account must exist there (e.g. via the seed variables) and be in staging's `ADMIN_EMAILS` (the staging Environment, else the repository value); staging's `ADMIN_APP_ORIGIN` is `https://karung.mietek.tech` as well, so the browser may call it. Leave `ADMIN_PASSWORD_RESET_ENABLED` unset until an email provider is connected. Every admin action is in the `admin_audit_log` table. Karung's cost card estimates provider cost from list prices per call (`COST_*_MICRO_USD`, defaults in `AppConfig.ProviderPrices`); set the VPS price per month as the repository variable `SERVER_MONTHLY_COST_USD` (e.g. `24.60`) for the "Server dan database" line. `AI_MONTHLY_BUDGET_USD` (repository variable, blank = no warning) is the LLM + Places budget Karung warns about from `AI_BUDGET_ALERT_PERCENT` (80/90/100); with `AI_BUDGET_MODE=CACHE_ONLY` new LLM calls stop once it is spent (searches from the cache still work, new ones and receipt photos answer 503 `AI_PAUSED` and charge nothing). `LLM_MODELS` lists the other models Karung may switch to. All of these can be overridden in Karung (stage C, table `app_settings`). "Backup terakhir" on the system page is the time `deploy/backup-db.sh` writes to `/data/documents/.last-backup` after a good backup (copy the new script to the VPS: the deploy does that). Karung itself: `deploy/nginx/karung.mietek.tech.conf` (copy, enable, `certbot --nginx -d karung.mietek.tech`, DNS A record for `karung`), served from `/opt/ransel/karung`; the *Deploy admin* workflow (`.github/workflows/deploy-admin.yml`) builds `:adminApp:wasmJsBrowserDistribution` and swaps it in the same way as *Deploy web*, with the same secrets.

## Backups

`deploy/backup-db.sh` dumps the database into `backups/` and keeps 14 days. Add to the deploy user's crontab (`crontab -e`):

```
15 3 * * * cd /opt/ransel && ./deploy/backup-db.sh >> backup.log 2>&1
```

Copying the dumps off the VPS (rsync/rclone to another machine or bucket) is a separate step.

## Rollback

Every deploy tags the image with the commit SHA. On the VPS (in `/opt/ransel`, or `/opt/ransel-dev` for staging): edit `SERVER_IMAGE` in `.env` to an older SHA and run
`docker compose -f docker-compose.prod.yml up -d`.

## Staging (`ranselapi-dev.mietek.tech`, `ransel-dev.mietek.tech`)

A second, separate server and web app for the Android **debug** build (`app.ransel.debug`, see `composeApp/CLAUDE.md` /
the build types in `composeApp/build.gradle.kts`) and for trying a branch before `main`. Same VPS, same image and web
build, its own Postgres and data (Karung has no staging):

| | production | staging |
| --- | --- | --- |
| Deployed by | push to `main` | push to `develop`, or Actions -> Deploy server / Deploy web -> Run workflow with target `staging` (any branch) |
| API host | `ranselapi.mietek.tech` | `ranselapi-dev.mietek.tech` |
| Web host | `ransel.mietek.tech` | `ransel-dev.mietek.tech` (`index.html` picks the API from the hostname) |
| Folder on the VPS | `/opt/ransel` (`DEPLOY_PATH`), web in `web/` | `/opt/ransel-dev` (`STAGING_DEPLOY_PATH`), web in `web/` |
| Loopback port | 8080 | 8090 |
| `APP_ENV` | `production` | `staging` (same required-secret checks) |
| GitHub Environment | `production` (uses the repository secrets) | `staging` (its own secrets override the repository ones) |

One-time setup:

1. DNS: A records `ranselapi-dev` and `ransel-dev` -> the VPS address.
2. VPS: `sudo mkdir -p /opt/ransel-dev && sudo chown -R sen:sen /opt/ransel-dev`, then nginx like the production hosts,
   with `deploy/nginx/ranselapi-dev.mietek.tech.conf` and `deploy/nginx/ransel-dev.mietek.tech.conf` (copy, symlink
   into `sites-enabled`, `sudo nginx -t && sudo systemctl reload nginx`, `sudo certbot --nginx -d <host>`; the gotchas
   under "Web host" below apply to `ransel-dev` too).
3. GitHub -> Settings -> Environments -> New environment `staging`. Add its **own** secrets `JWT_SECRET`,
   `PHOTO_SIGNING_SECRET` and `DATABASE_PASSWORD` (new random values, never the production ones), then the
   environment **variable** `STAGING_SECRETS_SET=true`. The deploy refuses to run without that variable, because a
   secret missing in the Environment silently falls back to the production repository secret.
4. Optional in the same Environment: `LLM_API_KEY`, `PLACES_API_KEY`, `SMTP_*`, `SEED_USER_*`, `ADMIN_EMAILS`, FCM.
   Anything not set there is taken from the repository, i.e. staging then shares the production key and its quota and
   bill (Gemini, Places) or sends real email through the same SMTP account. Without SMTP, staging sign-ups cannot
   verify their email: use `SEED_USER_*` / `SEED_USERS` accounts (already verified) instead. `APP_BASE_URL` and
   `CORS_ORIGINS` (variables) default to `https://ransel-dev.mietek.tech`, so staging's invite and verification links
   open the staging web app, or the debug app. For the debug app to open them directly (no chooser), set the
   Environment variable `ANDROID_CERT_SHA256` to the debug key's SHA-256 (`./gradlew :composeApp:signingReport`);
   staging already answers `assetlinks.json` for `app.ransel.debug`.
5. Create the branch `develop` (`git push origin main:develop`) or run both workflows by hand with target `staging`.
6. Check: `curl https://ranselapi-dev.mietek.tech/api/v1/health` and open `https://ransel-dev.mietek.tech`.

Push for the debug app: register `app.ransel.debug` as a second Android app in the same Firebase project (see "Push
notifications"); staging then uses the same `FCM_*` values from the repository. Backups: add a second cron line with
`cd /opt/ransel-dev`. Rollback works the same way inside `/opt/ransel-dev`. Staging pushes the image tag `:staging`;
`:latest` stays production.

## Web host and invite links (`ransel.mietek.tech`)

**Trip documents (stage 9).** Files live on the `ransel-documents` Docker volume (`DOCUMENTS_DIR=/data/documents`, owner decision 26 Oct 2026: VPS disk, not object storage). Limits are 20 MB per file and 200 MB per trip; files of deleted documents and trips are removed hourly. `deploy/backup-db.sh` also writes a `ransel-documents-*.tar.gz` of the volume next to the dump. nginx: uploads go through the API host, so `client_max_body_size 25m;` must be raised from 5m to 25m in the live `ranselapi` (and `ranselapi-dev`) server block, or nginx answers 413 to documents above 5 MB.

**Link previews and the guest page (stage 8).** `/p/<code>` (poll links) opens the web app like `/j/<code>`. WhatsApp and other crawlers must get Open Graph tags from the API instead: copy `deploy/nginx/ransel-preview-bots.conf` to `/etc/nginx/conf.d/` once, then add the `location ~ ^/(p|j)/...` and `location /og/` blocks from `deploy/nginx/ransel.mietek.tech.conf` (and the `-dev` file, port 8090) to the **live** site files: certbot rewrote those, so copying the repo files over them would drop the 443 block. `sudo nginx -t && sudo systemctl reload nginx`. Check with `curl -A WhatsApp https://ransel.mietek.tech/p/<code>` (HTML with `og:title`) and a browser (the app). The preview images `og/poll.png` and `og/invite.png` ship with the web build.

One-time, like the API host: copy `deploy/nginx/ransel.mietek.tech.conf` to `sites-available`, enable it, `nginx -t`, reload, then `sudo certbot --nginx -d ransel.mietek.tech`. It serves the web app from `/opt/ransel/web` (single-page fallback, so `/j/<code>` opens the app) and forwards `/.well-known/` to the API.

Gotchas from the first setup:

- **Symlink into `sites-enabled` before running certbot** (`sudo ln -s /etc/nginx/sites-available/ransel.mietek.tech.conf /etc/nginx/sites-enabled/ransel.mietek.tech`). Otherwise certbot attaches the certificate to the `default` site with `return 404` and every path answers 404.
- After certbot, the 443 block may contain `if ($host = ransel.mietek.tech) { return 301 https://$host$request_uri; }`. Delete it from the 443 block (keep it in the port 80 block); left there it is a redirect loop.
- SSH to the VPS listens on port 2212, not 22.
- `index.html` has `<base href="/">`, so the script, `.wasm` files and resources also load on deep links like `/j/<code>`.
- The web app is an installable PWA (`manifest.webmanifest`, `sw.js`). The conf keeps `index.html`, `sw.js` and the
  manifest uncached and serves the manifest as `application/manifest+json`.
- **Never copy the repo conf over the live file once certbot has run.** The repo file is only the port 80 block;
  certbot turned the live one into a 443 block plus a port 80 redirect block. Overwriting it drops both: HTTPS falls
  back to another site's certificate and `http://` stops redirecting. Instead, paste only the changed `location`
  blocks into the 443 block (for the PWA: `= /index.html`, `= /sw.js`, `= /manifest.webmanifest`). If it happens
  anyway, `sudo certbot --nginx -d ransel.mietek.tech` (reinstall) restores the 443 block; the port 80 block must end
  up as just `return 301 https://$host$request_uri;`.
- `nginx -t` needs `sudo`: without it nginx cannot read the Let's Encrypt keys and reports `Permission denied`.

### Publishing the web build

The *Deploy web* workflow (`.github/workflows/deploy-web.yml`) publishes it on every push to `main` that touches
`composeApp/`, `shared/`, `gradle/`, a `*.gradle.kts` file, `gradle.properties` or the workflow itself; run it by hand with
Actions -> Deploy web -> Run workflow (or `gh workflow run deploy-web.yml --ref main`). It builds
`:composeApp:wasmJsBrowserDistribution`, rsyncs the result into `/opt/ransel/web.new` (seeded with a copy of the live
folder, so only changed files travel) and then swaps it in with two `mv`s, so visitors never get a half-uploaded app. It
uses the same SSH secrets as *Deploy server* (`DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`, `DEPLOY_HOST`, `DEPLOY_PORT`,
`DEPLOY_USER`) and the `DEPLOY_PATH` variable; no new secrets are needed.

Permissions: the deploy user must be able to create, rename and delete folders **in `/opt/ransel` itself** (not only
inside `web/`), because the swap renames `web`. The `chown -R sen:sen /opt/ransel` from the top of this file covers that;
if `/opt/ransel/web` was created by root, run `sudo chown -R sen:sen /opt/ransel/web` once. Files are uploaded as
755/644 so nginx (`www-data`) can read them. If you set `DEPLOY_PATH` to something else, the `root` in
`deploy/nginx/ransel.mietek.tech.conf` must point at `$DEPLOY_PATH/web` too.

Fallback by hand, from the repo root (not atomic: fine when nobody is using the site):

```
./gradlew :composeApp:wasmJsBrowserDistribution
rsync -a --delete -e "ssh -p 2212" composeApp/build/dist/wasmJs/productionExecutable/ <user>@<vps>:/opt/ransel/web/
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

`ANDROID_CERT_SHA256` is a comma-separated list of release / Play App Signing fingerprints for `app.ransel`. The debug build
is `app.ransel.debug`, which these files do not name, so on a debug build invite links open through the "Open with" chooser
instead of directly; pick "RANSEL Dev". Changing a repository variable does not start a deploy: run *Deploy server* by hand
(`gh workflow run deploy-server.yml --ref main`) or push a change under `server/`, `shared/` or `deploy/`. Check with
`curl https://ransel.mietek.tech/.well-known/assetlinks.json`, and on the phone
`adb shell pm verify-app-links --re-verify app.ransel`, then `adb shell pm get-app-links app.ransel`.

Invite URLs returned by the API are `APP_BASE_URL/j/<code>`.
