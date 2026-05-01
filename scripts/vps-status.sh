#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

docker compose ps
curl -fsS http://localhost/health
echo
curl -fsS http://localhost/api/health
echo
curl -fsS http://localhost/api/logs/health
echo
docker compose logs --tail=80 server
