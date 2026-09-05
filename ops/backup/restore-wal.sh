#!/usr/bin/env bash
set -euo pipefail

destination="${1:-}"
wal_name="${2:-}"
if [[ -z "$destination" || -z "$wal_name" || -z "${BACKUP_S3_URI:-}" || -z "${BACKUP_ENVIRONMENT:-}" ]]; then
  printf 'usage: restore-wal.sh DESTINATION WAL_NAME with backup environment variables\n' >&2
  exit 2
fi
if [[ ! "$wal_name" =~ ^[A-F0-9]{8,24}(\.(history|partial))?$ ]]; then
  printf 'invalid WAL file name\n' >&2
  exit 2
fi

aws s3 cp "$BACKUP_S3_URI/wal/$BACKUP_ENVIRONMENT/$wal_name" "$destination" --only-show-errors
