#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

git pull
docker compose up -d --build db server nginx
docker compose ps
curl -fsS http://localhost/health
echo
curl -fsS http://localhost/api/health
echo
