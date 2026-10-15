# Deploy (VPS behind the host's nginx)

Public entry: `https://ranselapi.mietek.tech` -> host nginx -> `127.0.0.1:8080` (server container) -> Postgres container (private).

## One-time setup on the VPS (needs sudo)

```
sudo cp deploy/nginx/ranselapi.mietek.tech.conf /etc/nginx/sites-available/ranselapi.mietek.tech
sudo ln -s /etc/nginx/sites-available/ranselapi.mietek.tech /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d ranselapi.mietek.tech
```

The user that deploys (`sen`) must be in the `docker` group and own the deploy folder. The default is `/app/opt/ransel` (override with the repository variable `DEPLOY_PATH`):

```
sudo mkdir -p /app/opt/ransel && sudo chown -R sen:sen /app
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
15 3 * * * cd /app/opt/ransel && ./deploy/backup-db.sh >> backup.log 2>&1
```

Copying the dumps off the VPS (rsync/rclone to another machine or bucket) is a separate step.

## Rollback

Every deploy tags the image with the commit SHA. On the VPS: edit `SERVER_IMAGE` in `.env` to an older SHA and run
`docker compose -f docker-compose.prod.yml up -d`.
