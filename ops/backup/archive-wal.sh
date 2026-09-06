#!/usr/bin/env bash
set -euo pipefail

wal_path="${1:-}"
wal_name="${2:-}"
if [[ -z "$wal_path" || -z "$wal_name" || -z "${BACKUP_S3_URI:-}" || -z "${BACKUP_KMS_KEY_ID:-}" || -z "${BACKUP_ENVIRONMENT:-}" ]]; then
  printf 'usage: archive-wal.sh WAL_PATH WAL_NAME with backup environment variables\n' >&2
  exit 2
fi

bucket_and_prefix="${BACKUP_S3_URI#s3://}"
bucket="${bucket_and_prefix%%/*}"
base_prefix="${bucket_and_prefix#*/}"
if [[ "$base_prefix" == "$bucket_and_prefix" ]]; then
  base_prefix=""
fi
object_key="${base_prefix:+$base_prefix/}wal/$BACKUP_ENVIRONMENT/$wal_name"
if aws s3api head-object --bucket "$bucket" --key "$object_key" >/dev/null 2>&1; then
  exit 0
fi

aws s3 cp "$wal_path" "s3://$bucket/$object_key" --sse aws:kms --sse-kms-key-id "$BACKUP_KMS_KEY_ID" --only-show-errors
