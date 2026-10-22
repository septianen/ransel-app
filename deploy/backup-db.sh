#!/usr/bin/env bash
# Daily pg_dump of the database in this folder (production /opt/ransel, staging /opt/ransel-dev). Cron on the VPS, e.g.:
#   15 3 * * * cd /opt/ransel && ./deploy/backup-db.sh >> backup.log 2>&1
# Copying the dumps off the VPS (rclone/rsync to another machine or bucket) is a separate step.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p backups
file="backups/ransel-$(date +%Y%m%d-%H%M%S).sql.gz"
docker compose -f docker-compose.prod.yml exec -T db pg_dump -U ransel ransel | gzip > "$file"
# keep 14 days locally
find backups -name 'ransel-*.sql.gz' -mtime +14 -delete
echo "wrote $file"
