#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

git pull
export APP_VERSION="${APP_VERSION:-0.0.1-SNAPSHOT}"
export GIT_COMMIT="$(git rev-parse --short HEAD)"
export BUILD_TIME="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
export APP_ENV="${APP_ENV:-vps}"
docker compose up -d --build db server nginx
docker compose ps
curl -fsS http://localhost/health
echo
curl -fsS http://localhost/api/health
echo
