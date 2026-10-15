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
| `DATABASE_PASSWORD` | random, letters and digits (`openssl rand -hex 24`) |
| `SEED_USER_EMAIL`, `SEED_USER_PASSWORD`, `SEED_USER_NAME` | the closed-beta account |

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

### App Links / Universal Links

The API serves `/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association` (values from the
repository *variables* `ANDROID_CERT_SHA256` and `IOS_APP_IDS`; both answer 404 until set). Phones fetch them from
the **host of the invite link**, so that host's nginx must forward them to the API:

```
location /.well-known/ { proxy_pass http://127.0.0.1:8080; }
```

Invite URLs returned by the API are `APP_BASE_URL/j/<code>`.
