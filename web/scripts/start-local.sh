#!/usr/bin/env bash
# Starts Docker (if needed) and local Supabase, then the web app against it.
set -euo pipefail
cd "$(dirname "$0")/../.."

if ! docker info >/dev/null 2>&1; then
  echo "Starting Docker…"
  open -a Docker
  for _ in $(seq 1 60); do
    docker info >/dev/null 2>&1 && break
    sleep 2
  done
  docker info >/dev/null 2>&1 || { echo "Docker didn't start. Open Docker Desktop and try again."; exit 1; }
fi

supabase start
cd web
exec npx vite --mode docker
