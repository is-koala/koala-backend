#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

IMAGE="${KOALA_GHCR_IMAGE:-ghcr.io/is-koala/koala}"
VERSION="$(sed -n '/<artifactId>koala<\/artifactId>/,/<version>/s/.*<version>\([^<]*\)<\/version>.*/\1/p' pom.xml | head -1)"
[ -n "$VERSION" ] || { echo "pom.xml version not found" >&2; exit 1; }

LATEST=0
for arg in "$@"; do
  case "$arg" in
    --latest) LATEST=1 ;;
    *)
      echo "usage: $0 [--latest]" >&2
      exit 1
      ;;
  esac
done

TAGS=(-t "$IMAGE:$VERSION")
if [ "$LATEST" = 1 ]; then
  TAGS+=(-t "$IMAGE:latest")
fi

docker buildx build \
  --platform linux/amd64 \
  --provenance=false \
  --sbom=false \
  --push \
  "${TAGS[@]}" \
  .
