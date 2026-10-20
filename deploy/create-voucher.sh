#!/usr/bin/env bash
# Makes an Explore credit voucher on the production database (there is no admin role in the app yet).
#   ./deploy/create-voucher.sh <credits> [max_uses=1] [valid_days=30]
# Prints the code to hand to the user, who enters it under Account > Kredit. Typical amounts match the packs: 15, 40, 100.
set -euo pipefail
cd "$(dirname "$0")/.."
credits="${1:?usage: create-voucher.sh <credits> [max_uses] [valid_days]}"
max_uses="${2:-1}"
days="${3:-30}"
[[ "$credits" =~ ^[0-9]+$ && "$max_uses" =~ ^[0-9]+$ && "$days" =~ ^[0-9]+$ ]] || { echo "numbers only" >&2; exit 1; }
# 12 characters without look-alikes (no 0/O/1/I), grouped 4-4-4
code="$(LC_ALL=C tr -dc 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789' < /dev/urandom | head -c 12 | sed 's/\(....\)\(....\)\(....\)/\1-\2-\3/')"
docker compose -f docker-compose.prod.yml exec -T db psql -U ransel ransel -v ON_ERROR_STOP=1 -q -c \
  "INSERT INTO credit_vouchers (code, credits, max_uses, expires_at) VALUES ('$code', $credits, $max_uses, now() + interval '$days days')"
echo "$code  ($credits credits, $max_uses use(s), valid $days days)"
