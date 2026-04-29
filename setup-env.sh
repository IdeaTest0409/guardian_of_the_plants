#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE=".env"
ENV_EXAMPLE=".env.example"

if [ ! -f "$ENV_EXAMPLE" ]; then
    echo "Error: $ENV_EXAMPLE not found"
    exit 1
fi

if [ -f "$ENV_FILE" ]; then
    echo "Found existing .env file."
    read -rp "Overwrite existing .env? (y/N): " confirm
    if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi
fi

echo "Creating .env from $ENV_EXAMPLE ..."
cp "$ENV_EXAMPLE" "$ENV_FILE"

read -rsp "Enter AI_API_KEY: " api_key
echo

if [ -n "$api_key" ]; then
    sed -i "s|^AI_API_KEY=.*|AI_API_KEY=$api_key|" "$ENV_FILE"
    echo "AI_API_KEY set."
else
    echo "AI_API_KEY left empty (server will return an error on chat requests)."
fi

echo ""
echo "Done. To verify: grep AI_API_KEY $ENV_FILE"
