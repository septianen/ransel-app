# Deploy (VPS behind the host's nginx)

Public entry: `https://api.ransel.app` -> host nginx -> `127.0.0.1:8080` (server container) -> Postgres container (private).

Production (`api.ransel.app`, `ransel.app`, the Play AAB, Karung) ships only from the **Release** workflow. A push deploys staging and does not change the app version. See [Releases](#releases).

## One-time setup on the VPS (needs sudo)

```
sudo cp deploy/nginx/api.ransel.app.conf /etc/nginx/sites-available/api.ransel.app
sudo ln -s /etc/nginx/sites-available/api.ransel.app /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d api.ransel.app
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
| `SMTP_USERNAME`, `SMTP_PASSWORD` (secrets) and `SMTP_HOST`, `SMTP_PORT` (default 587), `EMAIL_FROM` (default `RANSEL <no-reply@ransel.app>`) (variables) | outgoing email over the provider's SMTP relay: the verification link after sign-up and password reset. Without `SMTP_HOST` nothing is sent, so new accounts cannot verify and never reach Explore. See "Email" below |
| `REGISTRATION_ENABLED` (variable, optional) | `false` closes sign-up again (only seeded accounts); unset = open |
| `LLM_API_KEY`, `PLACES_API_KEY` (optional until Explore goes live) | Gemini API key (Google AI Studio) and Google Places (New) key. Set the repository *variable* `LLM_MODEL` to a model id your key can call (`curl -H "x-goog-api-key: $KEY" https://generativelanguage.googleapis.com/v1beta/models`). Without them Explore answers 503 `AI_UNAVAILABLE`. Restrict the Places key to the Places API and set a **budget alert** in Google Cloud |
| `GOOGLE_DRIVE_API_KEY` (optional) | Trip albums: a Google Cloud API key with the **Google Drive API** enabled, restricted to that API. It only lists folders shared as "Anyone with the link" (no OAuth, no user data); the photos load from Google, not through the server. Without it albums still save but the gallery answers 503 `ALBUM_UNAVAILABLE` |
| `GOOGLE_WEB_CLIENT_ID`, `GOOGLE_IOS_CLIENT_ID`, `GOOGLE_WEB_CLIENT_SECRET` (optional) | "Lanjutkan dengan Google": OAuth client ids from Google Cloud (Web application client = audience of Android and web tokens and the id the web button starts with; iOS client; the Web client secret exchanges the web auth code). Authorised JavaScript origins of the Web client: `https://ransel.app`, `https://staging.ransel.app`, `http://localhost:8081`. Android also needs an Android OAuth client (package `app.ransel`, SHA-1 of the signing key and the debug key) in the same project. Blank web id = Google sign-in off: `GET /auth/google/config` has no ids and the apps hide the button. Set the three as GitHub secrets: `deploy-server.yml` passes them to `docker-compose.prod.yml` |
| `SEED_USERS` (optional) | more accounts: `email\|password\|name;email\|password\|name`. Use letters and digits in passwords (no `\|`, `;` or `$`) |

| `FCM_SERVICE_ACCOUNT_JSON` (secret), `FCM_PROJECT_ID` (variable), optional until push goes live | Firebase service-account key and project id for push notifications. Store the key as **base64 on one line** (`base64 -i key.json \| tr -d '\n'`) so `.env` and the secret survive the quotes in JSON. Without them pushes are only logged; the in-app inbox works either way. See "Push notifications" below |

**Changing a secret or variable does not change the running server.** The values are written to the VPS by the deploy job. A push under `server/`, `shared/` or `deploy/`, or Actions -> Deploy server -> Run workflow, updates **staging**. Production picks them up on the next [Release](#releases) (that also bumps the app version).

### Email

Sign-up is open and every new account gets an email with a verification link (`https://ransel.app/verify-email?token=...`, 24 hours). Until the link is opened the account works, except Explore and vouchers, and the 30 signup credits wait. The server sends mail over SMTP, so any provider with an SMTP relay works (Brevo, Resend, Amazon SES, a mailbox host); `no-reply@ransel.app` does not need a real mailbox, only a verified sending domain:

1. Create the account at the provider and add `ransel.app` as a sending domain (Brevo; done 3 Nov 2026).
2. Add the DNS records it asks for at the domain's DNS host: SPF (TXT on `ransel.app`, merge with an existing `v=spf1` record instead of adding a second one), DKIM (usually one or more CNAME/TXT records), and a DMARC record (`_dmarc.ransel.app TXT "v=DMARC1; p=none"` is a safe start). Wait until the provider shows the domain as verified.
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
8. Analytics on the web: Project settings -> Your apps -> Add app -> Web ("RANSEL web"), with Analytics on. Copy `apiKey`, `appId` and `measurementId` into `firebaseConfig` in `composeApp/src/wasmJsMain/resources/index.html` (public client config, not a secret) and deploy the web app. It only sends from `ransel.app`; staging and localhost stay silent.
9. iOS (later, on a Mac): add the iOS app (bundle id `app.ransel`) in Firebase, upload an APNs auth key (Apple Developer -> Keys) under Cloud Messaging, add the Firebase iOS SDK (Messaging, Analytics, Crashlytics) and the Push Notifications capability to the Xcode project, and replace `NoPushRegistrar`, `NoAnalytics` and `NoCrashReporter` in `Platform.ios.kt`.

Reminders run on the server every minute and use WIB for every trip: the packing reminder at 18:00 the day before departure (`NOTIFY_PACKING_HOUR`), the activity reminder 30 minutes before it starts (`NOTIFY_ACTIVITY_LEAD_MINUTES`). A trip in WITA/WIT gets them 1-2 hours off until trips have a timezone.

## Releases

`version.properties` in the repo root is the only `versionName` and `versionCode`. The Android build reads it. Nothing else writes it.

Actions -> **Release** -> Run workflow:

| `RELEASE_TYPE` | What runs |
| --- | --- |
| `none` (default) | Staging only: Deploy server, Deploy web, and a debug APK (*Distribute Android*). No version bump, no commit, no tag, no release AAB, no Karung deploy. Any branch. |
| `MAJOR` | From `main` only. `versionName` becomes X+1.0.0 (1.4.2 -> 2.0.0) and `versionCode` increases by 1. The commit is pushed to `main` and tagged `vX.Y.Z`. Then production: server, web, release APK + AAB, Karung. |
| `MINOR` | Same, with `versionName` X.Y+1.0 (1.4.2 -> 1.5.0). |
| `PATCH` | Same, with `versionName` X.Y.Z+1 (1.0.1 -> 1.0.2). |

A push (to `develop` or `main`) behaves like `none` for the workflow it starts: staging, current version, no release AAB. Push to `main` does **not** update `api.ransel.app` or `ransel.app`. Karung is not on the push path; it ships with a release.

The menu value is `none` rather than a blank line because a GitHub choice input cannot be empty. The workflow treats `none` as no release. Release passes `production: true` to the deploy workflows only for `MAJOR`, `MINOR` or `PATCH` run from `main`. Those workflows gate every production step on that flag. `github.event_name` inside a called workflow is the caller's event, so it is not used as the gate.

A release commits the bump on the runner, then runs `:shared:jvmTest`, `:server:test`, `:composeApp:testDebugUnitTest` and `:adminApp:wasmJsBrowserTest` (Karung) on that commit. The commit and the `vX.Y.Z` tag are pushed only after those tests pass. Server, web, the Android release build and Karung then check out that SHA.

Two releases use the concurrency group `ransel-release` and wait in line, so they cannot both add 1 to the same `versionCode`. The release Android build uses the group `distribute-android-release` and is not cancelled by a staging build. Re-running the workflow reuses the bump when this run's commit is already on `main`, or when the `vX.Y.Z` tag points at that commit, so a second attempt does not bump again.

The bump is committed as `github-actions[bot]` with the workflow's `contents: write` permission, then tagged `vX.Y.Z`. That push uses `GITHUB_TOKEN`, which does not start other workflows. The same run deploys production from the bumped SHA.

If branch protection rejects the Actions bot, set the secret `RELEASE_PUSH_TOKEN` to a fine-grained token or a classic PAT with contents write on this repo. The Release workflow uses it for the checkout and the push when it is set. Do not put the token on the command line.

```
gh workflow run release.yml --ref main -f RELEASE_TYPE=PATCH
```

`scripts/bump_version.py` is the bump. `python3 -m unittest discover -s scripts -p 'test_bump_version.py' -v` runs its tests; the CI `test` job runs the same command.

The current file is `1.0.1` / `67`: `1.0.1` is the version name already on `main`, and `67` is the latest Distribute Android run number already shipped (the counter debug and release used to share). The next release is `68` or higher, which is above the last production build (`1.0.1` run 62) and not a downgrade from the latest debug build. If more Distribute Android runs land before this is on `main`, set `versionCode` to that run number (do not go backwards) and leave `versionName` at `1.0.1`.

## Android builds (Firebase App Distribution)

The *Distribute Android* workflow (`.github/workflows/distribute-android.yml`) builds an APK and sends it to testers through Firebase App Distribution (they install it from the email link or the *App Tester* app):

| Trigger | Build | App | API |
| --- | --- | --- | --- |
| push (app, `iris`, `shared` or Gradle changes) to `develop` or `main`, or *Run workflow* | debug | "RANSEL Dev" `app.ransel.debug` | `api-staging.ransel.app` |
| **Release** with `MAJOR`, `MINOR` or `PATCH` on `main` | release (R8, signed) plus the AAB | "RANSEL" `app.ransel` | `api.ransel.app` |

`versionName` and `versionCode` are the values in `version.properties` for every build. A staging build does not increment them, so a tester who already has that `versionCode` may need to reinstall if the device refuses the same code. The release notes are the commit message (or the text given to *Run workflow* / Release). Each run also keeps the APK as a workflow artifact for 14 days.

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
| `GOOGLE_SERVICES_JSON` | secret | `composeApp/google-services.json` (both packages), pasted as is or as base64; without it the APK has no push, Analytics or Crashlytics. The run log lists the packages it found |
| `DEBUG_KEYSTORE_BASE64` | secret | Your `~/.android/debug.keystore`, base64, so every staging build has the same signature (testers can update, and it matches your local debug builds). Without it each build must be uninstalled first |
| `RANSEL_KEYSTORE_BASE64` | secret | The release keystore (`keystore.properties.example`), base64; required for production |
| `RANSEL_KEYSTORE_PASSWORD`, `RANSEL_KEY_ALIAS`, `RANSEL_KEY_PASSWORD` | secrets | Its passwords and alias |

Keep the release keystore and its passwords safe outside git: the Play Store later needs the same key (or Play App Signing's upload key), and an app signed with another key cannot update the installed one. For push in the debug app, add its SHA fingerprints in Firebase only if a feature needs them (FCM does not).

## Google Play (closed testing)

A **Release** (`MAJOR`, `MINOR` or `PATCH` on `main`) also builds `composeApp-release.aab` and keeps it with `mapping.txt` as the workflow artifact `ransel-play-<versionName>-<versionCode>` for 90 days. Upload the AAB in Play Console -> Test and release -> Closed testing (the first upload is always by hand). The bundle is signed with the release keystore, which becomes the **upload key** once Play App Signing is on. A push does not build the AAB.

- **Version:** `versionName` and `versionCode` are in `version.properties`. A release adds 1 to `versionCode` and moves `versionName` as in [Releases](#releases). Play rejects an upload whose `versionCode` is not higher than the last one, so do not set the file backwards.
- **Checks in every run:** the permission list of the APK is printed; the run fails on `AD_ID` / AdServices permissions (the app has no ads, the manifest removes what Firebase Analytics adds) and on native libraries that are not 16 KB aligned.
- **App Links with Play App Signing:** Play re-signs the app with its own key, so `assetlinks.json` must list both fingerprints. Set the repository variable `ANDROID_CERT_SHA256` to both, comma separated: the *App signing key certificate* SHA-256 and the *Upload key certificate* SHA-256 (Play Console -> Test and release -> App integrity -> App signing). Run *Deploy server*, then check `https://ransel.app/.well-known/assetlinks.json`. Add the Play signing SHA-1/SHA-256 to the `app.ransel` app in Firebase too.
- **Privacy policy and account deletion URLs** (Play Console -> App content): `https://ransel.app/privacy.html` and `https://ransel.app/delete-account.html` (live once the delete-account branch is deployed by *Deploy web*).

### Store review account

Reviewers need a verified account with a full trip. Accounts in `SEED_USERS` (repository **secret**, `email|password|name;...`) are created verified on every server start and never get a password reset, so:

1. Add `reviewer@<your domain>|<password>|Reviewer RANSEL` to the `SEED_USERS` secret (keep the entries already there) and run *Deploy server*. The password must pass the sign-up rule and must not contain `|` or `;`.
2. Make the sample trip from any machine (the password is asked for, never typed on the command line):
   ```
   python3 deploy/seed-demo-trip.py --api https://api.ransel.app --email reviewer@<your domain>
   ```
   It makes "Liburan Jogja" three weeks from today: three days of itinerary, two members without an account, packing items with who brings what, four shared expenses and an open poll with the reviewer's vote. Running it again does nothing; `--replace` makes it again with fresh dates (do that before each review if the dates have passed).
3. Give the email and password in Play Console -> App content -> App access.

## Explore credits and vouchers

Every user has a wallet (30 credits on first use) plus 3 free credits a day (resets 00:00 WIB). A new search costs 3, the next batch 1. All numbers and the pack list can be changed in the VPS `.env` (`CREDIT_SIGNUP_GRANT`, `CREDIT_DAILY_FREE`, `CREDIT_COST_SEARCH`, `CREDIT_COST_NEXT_BATCH`, `CREDIT_COST_PLACE_DETAIL`, `CREDIT_COST_RECEIPT` (one receipt photo, default 1), `CREDIT_PACKAGES=id:credits:priceMinor;...`); restart the server container after changing them. These env values are the defaults: an admin can override each credit number (receipt photo included) in Karung, Sistem > Pengaturan, without a restart; "Kembalikan bawaan" goes back to the env value. There is no payment provider. With the repository variable `CREDIT_PURCHASE_ENABLED=true` (takes effect with the next *Deploy server* run), the web app lets users order a pack: it opens an email draft to the owner, who answers with payment instructions and adds the credits in Karung once the money arrives. Android and iOS keep showing "coming soon". Promo codes are made in Karung (Voucher page) once that branch is merged; on the VPS the script still works:

```
./deploy/create-voucher.sh 15            # 15 credits, one use, valid 365 days
./deploy/create-voucher.sh 40 5 60       # 40 credits, five users, valid 60 days
```

Promo vouchers are easier in Karung (Voucher page: make, copy, revoke). The script prints the code (`ABCD-EFGH-JKLM`); the user enters it under Account > Credits. One user can redeem a code once. To revoke: `UPDATE credit_vouchers SET revoked_at = now() WHERE code = '...'`. Watch the real cost per search in the hourly `ai.metrics` log line before changing the pack prices.

## Admin app (Karung)

Admins are the accounts in `ADMIN_EMAILS` (comma separated, case-insensitive). The deploy workflow rewrites the VPS `.env` on every deploy, so set it as the GitHub **repository variable** `ADMIN_EMAILS` (Settings > Secrets and variables > Actions > Variables), not by editing `.env`; it takes effect with the next *Deploy server* run. The server checks the list on every `/admin` request, so removing an email locks that person out at once. Set `ADMIN_APP_ORIGIN=https://karung.ransel.app` so the browser may call the API. `APP_ENV` (`production` / `staging`) is shown as a badge in Karung. Karung manages both servers: pick Production or Staging on its sign-in screen (switching signs out). To use Staging, your account must exist there (e.g. via the seed variables) and be in staging's `ADMIN_EMAILS` (the staging Environment, else the repository value); staging's `ADMIN_APP_ORIGIN` is `https://karung.ransel.app` as well, so the browser may call it. Leave `ADMIN_PASSWORD_RESET_ENABLED` unset until an email provider is connected. Every admin action is in the `admin_audit_log` table. Karung's cost card estimates provider cost from list prices per call (`COST_*_MICRO_USD`, defaults in `AppConfig.ProviderPrices`); set the VPS price per month as the repository variable `SERVER_MONTHLY_COST_USD` (e.g. `24.60`) for the "Server dan database" line. `AI_MONTHLY_BUDGET_USD` (repository variable, blank = no warning) is the LLM + Places budget Karung warns about from `AI_BUDGET_ALERT_PERCENT` (80/90/100); with `AI_BUDGET_MODE=CACHE_ONLY` new LLM calls stop once it is spent (searches from the cache still work, new ones and receipt photos answer 503 `AI_PAUSED` and charge nothing). `LLM_MODELS` lists the other models Karung may switch to. All of these can be overridden in Karung (stage C, table `app_settings`). "Backup terakhir" on the system page is the time `deploy/backup-db.sh` writes to `/data/documents/.last-backup` after a good backup (copy the new script to the VPS: the deploy does that). Karung itself: `deploy/nginx/karung.ransel.app.conf` (copy, enable, `certbot --nginx -d karung.ransel.app`, DNS A record for `karung`), served from `/opt/ransel/karung`. A production **Release** builds `:adminApp:wasmJsBrowserDistribution` and swaps it in the same way as *Deploy web*, with the same secrets (`.github/workflows/deploy-admin.yml`). A push does not publish Karung.

## Backups

`deploy/backup-db.sh` dumps the database into `backups/` and keeps 14 days. Add to the deploy user's crontab (`crontab -e`):

```
15 3 * * * cd /opt/ransel && ./deploy/backup-db.sh >> backup.log 2>&1
```

Copying the dumps off the VPS (rsync/rclone to another machine or bucket) is a separate step.

## Rollback

Every deploy tags the image with the commit SHA. On the VPS (in `/opt/ransel`, or `/opt/ransel-dev` for staging): edit `SERVER_IMAGE` in `.env` to an older SHA and run
`docker compose -f docker-compose.prod.yml up -d`.

## Staging (`api-staging.ransel.app`, `staging.ransel.app`)

A second, separate server and web app for the Android **debug** build (`app.ransel.debug`, see `composeApp/CLAUDE.md` /
the build types in `composeApp/build.gradle.kts`) and for trying a branch before `main`. Same VPS, same image and web
build, its own Postgres and data (Karung has no staging):

| | production | staging |
| --- | --- | --- |
| Deployed by | **Release** on `main` with `MAJOR`, `MINOR` or `PATCH` | push to `develop` or `main`, or Actions -> Deploy server / Deploy web -> Run workflow (any branch), or **Release** with `none` |
| API host | `api.ransel.app` | `api-staging.ransel.app` |
| Web host | `ransel.app` | `staging.ransel.app` (`index.html` picks the API from the hostname) |
| Folder on the VPS | `/opt/ransel` (`DEPLOY_PATH`), web in `web/` | `/opt/ransel-dev` (`STAGING_DEPLOY_PATH`), web in `web/` |
| Loopback port | 8080 | 8090 |
| `APP_ENV` | `production` | `staging` (same required-secret checks) |
| GitHub Environment | `production` (uses the repository secrets) | `staging` (its own secrets override the repository ones) |

One-time setup:

1. DNS: A records `ranselapi-dev` and `ransel-dev` -> the VPS address.
2. VPS: `sudo mkdir -p /opt/ransel-dev && sudo chown -R sen:sen /opt/ransel-dev`, then nginx like the production hosts,
   with `deploy/nginx/api-staging.ransel.app.conf` and `deploy/nginx/staging.ransel.app.conf` (copy, symlink
   into `sites-enabled`, `sudo nginx -t && sudo systemctl reload nginx`, `sudo certbot --nginx -d <host>`; the gotchas
   under "Web host" below apply to `ransel-dev` too).
3. GitHub -> Settings -> Environments -> New environment `staging`. Add its **own** secrets `JWT_SECRET`,
   `PHOTO_SIGNING_SECRET` and `DATABASE_PASSWORD` (new random values, never the production ones), then the
   environment **variable** `STAGING_SECRETS_SET=true`. The deploy refuses to run without that variable, because a
   secret missing in the Environment silently falls back to the production repository secret.
4. Optional in the same Environment: `LLM_API_KEY`, `PLACES_API_KEY`, `GOOGLE_DRIVE_API_KEY`, `SMTP_*`, `SEED_USER_*`, `ADMIN_EMAILS`, FCM.
   Anything not set there is taken from the repository, i.e. staging then shares the production key and its quota and
   bill (Gemini, Places) or sends real email through the same SMTP account. Without SMTP, staging sign-ups cannot
   verify their email: use `SEED_USER_*` / `SEED_USERS` accounts (already verified) instead. `APP_BASE_URL` and
   `CORS_ORIGINS` (variables) default to `https://staging.ransel.app`, so staging's invite and verification links
   open the staging web app, or the debug app. For the debug app to open them directly (no chooser), set the
   Environment variable `ANDROID_CERT_SHA256` to the debug key's SHA-256 (`./gradlew :composeApp:signingReport`);
   staging already answers `assetlinks.json` for `app.ransel.debug`.
5. Create the branch `develop` (`git push origin main:develop`) or run Deploy server and Deploy web by hand (they deploy staging).
6. Check: `curl https://api-staging.ransel.app/api/v1/health` and open `https://staging.ransel.app`.

Push for the debug app: register `app.ransel.debug` as a second Android app in the same Firebase project (see "Push
notifications"); staging then uses the same `FCM_*` values from the repository. Backups: add a second cron line with
`cd /opt/ransel-dev`. Rollback works the same way inside `/opt/ransel-dev`. Staging pushes the image tag `:staging`;
`:latest` stays production.

## Web host and invite links (`ransel.app`)

**Trip covers and profile photos (B19.1).** JPEG files at most 1 MB per upload, stored in `IMAGES_DIR` (default `DOCUMENTS_DIR/images`, so the `ransel-documents` volume and its backup hold them too). Files that no live trip or account uses are removed hourly. They fit under nginx's default `client_max_body_size`.

**Trip documents (stage 9).** Files live on the `ransel-documents` Docker volume (`DOCUMENTS_DIR=/data/documents`, owner decision 26 Oct 2026: VPS disk, not object storage). Limits are 20 MB per file and 200 MB per trip; files of deleted documents and trips are removed hourly. `deploy/backup-db.sh` also writes a `ransel-documents-*.tar.gz` of the volume next to the dump. nginx: uploads go through the API host, so `client_max_body_size 25m;` must be raised from 5m to 25m in the live `ranselapi` (and `ranselapi-dev`) server block, or nginx answers 413 to documents above 5 MB.

**Link previews and the guest page (stage 8).** `/p/<code>` (poll links) opens the web app like `/j/<code>`. WhatsApp and other crawlers must get Open Graph tags from the API instead: copy `deploy/nginx/ransel-preview-bots.conf` to `/etc/nginx/conf.d/` once, then add the `location ~ ^/(p|j)/...` and `location /og/` blocks from `deploy/nginx/ransel.app.conf` (and the `-dev` file, port 8090) to the **live** site files: certbot rewrote those, so copying the repo files over them would drop the 443 block. `sudo nginx -t && sudo systemctl reload nginx`. Check with `curl -A WhatsApp https://ransel.app/p/<code>` (HTML with `og:title`) and a browser (the app). The preview images `og/poll.png` and `og/invite.png` ship with the web build.

One-time, like the API host: copy `deploy/nginx/ransel.app.conf` to `sites-available`, enable it, `nginx -t`, reload, then `sudo certbot --nginx -d ransel.app`. It serves the web app from `/opt/ransel/web` (single-page fallback, so `/j/<code>` opens the app) and forwards `/.well-known/` to the API.

Gotchas from the first setup:

- **Symlink into `sites-enabled` before running certbot** (`sudo ln -s /etc/nginx/sites-available/ransel.app.conf /etc/nginx/sites-enabled/ransel.app`). Otherwise certbot attaches the certificate to the `default` site with `return 404` and every path answers 404.
- After certbot, the 443 block may contain `if ($host = ransel.app) { return 301 https://$host$request_uri; }`. Delete it from the 443 block (keep it in the port 80 block); left there it is a redirect loop.
- SSH to the VPS listens on port 2212, not 22.
- `index.html` has `<base href="/">`, so the script, `.wasm` files and resources also load on deep links like `/j/<code>`.
- The web app is an installable PWA (`manifest.webmanifest`, `sw.js`). The conf keeps `index.html`, `sw.js` and the
  manifest uncached and serves the manifest as `application/manifest+json`.
- **Never copy the repo conf over the live file once certbot has run.** The repo file is only the port 80 block;
  certbot turned the live one into a 443 block plus a port 80 redirect block. Overwriting it drops both: HTTPS falls
  back to another site's certificate and `http://` stops redirecting. Instead, paste only the changed `location`
  blocks into the 443 block (for the PWA: `= /index.html`, `= /sw.js`, `= /manifest.webmanifest`). If it happens
  anyway, `sudo certbot --nginx -d ransel.app` (reinstall) restores the 443 block; the port 80 block must end
  up as just `return 301 https://$host$request_uri;`.
- `nginx -t` needs `sudo`: without it nginx cannot read the Let's Encrypt keys and reports `Permission denied`.

### Publishing the web build

The *Deploy web* workflow (`.github/workflows/deploy-web.yml`) publishes **staging** on a push to `develop` or `main` that touches
`composeApp/`, `shared/`, `gradle/`, a `*.gradle.kts` file, `gradle.properties` or the workflow itself, and when you run it by hand
(Actions -> Deploy web -> Run workflow). Production (`ransel.app`) is a **Release** (`MAJOR` / `MINOR` / `PATCH` on `main`). It builds
`:composeApp:wasmJsBrowserDistribution`, rsyncs the result into `/opt/ransel/web.new` (seeded with a copy of the live
folder, so only changed files travel) and then swaps it in with two `mv`s, so visitors never get a half-uploaded app. It
uses the same SSH secrets as *Deploy server* (`DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`, `DEPLOY_HOST`, `DEPLOY_PORT`,
`DEPLOY_USER`) and the `DEPLOY_PATH` variable; no new secrets are needed.

Permissions: the deploy user must be able to create, rename and delete folders **in `/opt/ransel` itself** (not only
inside `web/`), because the swap renames `web`. The `chown -R sen:sen /opt/ransel` from the top of this file covers that;
if `/opt/ransel/web` was created by root, run `sudo chown -R sen:sen /opt/ransel/web` once. Files are uploaded as
755/644 so nginx (`www-data`) can read them. If you set `DEPLOY_PATH` to something else, the `root` in
`deploy/nginx/ransel.app.conf` must point at `$DEPLOY_PATH/web` too.

Fallback by hand, from the repo root (not atomic: fine when nobody is using the site):

```
./gradlew :composeApp:wasmJsBrowserDistribution
rsync -a --delete -e "ssh -p 2212" composeApp/build/dist/wasmJs/productionExecutable/ <user>@<vps>:/opt/ransel/web/
```

`index.html` points the app at `https://api.ransel.app` when it is served from `ransel.app` (the API's
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
instead of directly; pick "RANSEL Dev". Changing a repository variable does not start a deploy: push a change under `server/`, `shared/` or `deploy/` (staging) or run a **Release** (production). Check with
`curl https://ransel.app/.well-known/assetlinks.json`, and on the phone
`adb shell pm verify-app-links --re-verify app.ransel`, then `adb shell pm get-app-links app.ransel`.

Invite URLs returned by the API are `APP_BASE_URL/j/<code>`.
