#!/usr/bin/env bash
set -euo pipefail

dry_run=false
if [[ "${1:-}" == "--dry-run" ]]; then
  dry_run=true
fi

required=(BACKUP_DATABASE_URL BACKUP_S3_URI BACKUP_KMS_KEY_ID BACKUP_ENVIRONMENT)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'missing required variable: %s\n' "$name" >&2
    exit 2
  fi
done

printf 'phase=logical-backup environment=%s\n' "$BACKUP_ENVIRONMENT"
printf 'phase=physical-base-backup environment=%s\n' "$BACKUP_ENVIRONMENT"
printf 'phase=retention-copies policy="7 daily / 4 weekly / 6 monthly"\n'
printf 'phase=success-metric\n'

if [[ "$dry_run" == true ]]; then
  exit 0
fi

for command_name in pg_dump pg_basebackup aws sha256sum tar; do
  command -v "$command_name" >/dev/null
done

backup_tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "$backup_tmp_dir"' EXIT
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
archive="$backup_tmp_dir/koala-$timestamp.dump"
checksum="$archive.sha256"
base_directory="$backup_tmp_dir/base"
base_archive="$backup_tmp_dir/koala-$timestamp.base.tar"
base_checksum="$base_archive.sha256"
daily_key="daily/$BACKUP_ENVIRONMENT/koala-$timestamp.dump"
daily_base_key="pitr/daily/$BACKUP_ENVIRONMENT/koala-$timestamp.base.tar"

pg_dump --format=custom --no-owner --no-acl --file="$archive" "$BACKUP_DATABASE_URL"
mkdir -p "$base_directory"
pg_basebackup --dbname="$BACKUP_DATABASE_URL" --pgdata="$base_directory" \
  --format=tar --wal-method=stream --checkpoint=fast
tar -cf "$base_archive" -C "$base_directory" .
(cd "$backup_tmp_dir" && sha256sum "$(basename "$archive")" > "$(basename "$checksum")")
(cd "$backup_tmp_dir" && sha256sum "$(basename "$base_archive")" > "$(basename "$base_checksum")")
aws s3 cp "$archive" "$BACKUP_S3_URI/$daily_key" --sse aws:kms --sse-kms-key-id "$BACKUP_KMS_KEY_ID" --only-show-errors
aws s3 cp "$checksum" "$BACKUP_S3_URI/$daily_key.sha256" --sse aws:kms --sse-kms-key-id "$BACKUP_KMS_KEY_ID" --only-show-errors
aws s3 cp "$base_archive" "$BACKUP_S3_URI/$daily_base_key" --sse aws:kms --sse-kms-key-id "$BACKUP_KMS_KEY_ID" --only-show-errors
aws s3 cp "$base_checksum" "$BACKUP_S3_URI/$daily_base_key.sha256" --sse aws:kms --sse-kms-key-id "$BACKUP_KMS_KEY_ID" --only-show-errors

day_of_week="$(date -u +%u)"
day_of_month="$(date -u +%d)"
if [[ "$day_of_week" == "7" ]]; then
  weekly_key="weekly/$BACKUP_ENVIRONMENT/koala-$timestamp.dump"
  weekly_checksum_key="$weekly_key.sha256"
  weekly_base_key="pitr/weekly/$BACKUP_ENVIRONMENT/koala-$timestamp.base.tar"
  weekly_base_checksum_key="$weekly_base_key.sha256"
  aws s3 cp "$archive" "$BACKUP_S3_URI/$weekly_key" --sse aws:kms --sse-kms-key-id "$BACKUP_KMS_KEY_ID" --only-show-errors
  aws s3 cp "$checksum" "$BACKUP_S3_URI/$weekly_checksum_key" --sse aws:kms --sse-kms-key-id "$BACKUP_KMS_KEY_ID" --only-show-errors
  aws s3 cp "$base_archive" "$BACKUP_S3_URI/$weekly_base_key" --sse aws:kms --sse-kms-key-id "$BACKUP_KMS_KEY_ID" --only-show-errors
  aws s3 cp "$base_checksum" "$BACKUP_S3_URI/$weekly_base_checksum_key" --sse aws:kms --sse-kms-key-id "$BACKUP_KMS_KEY_ID" --only-show-errors
fi
if [[ "$day_of_month" == "01" ]]; then
  monthly_key="monthly/$BACKUP_ENVIRONMENT/koala-$timestamp.dump"
  monthly_checksum_key="$monthly_key.sha256"
  monthly_base_key="pitr/monthly/$BACKUP_ENVIRONMENT/koala-$timestamp.base.tar"
  monthly_base_checksum_key="$monthly_base_key.sha256"
  aws s3 cp "$archive" "$BACKUP_S3_URI/$monthly_key" --sse aws:kms --sse-kms-key-id "$BACKUP_KMS_KEY_ID" --only-show-errors
  aws s3 cp "$checksum" "$BACKUP_S3_URI/$monthly_checksum_key" --sse aws:kms --sse-kms-key-id "$BACKUP_KMS_KEY_ID" --only-show-errors
  aws s3 cp "$base_archive" "$BACKUP_S3_URI/$monthly_base_key" --sse aws:kms --sse-kms-key-id "$BACKUP_KMS_KEY_ID" --only-show-errors
  aws s3 cp "$base_checksum" "$BACKUP_S3_URI/$monthly_base_checksum_key" --sse aws:kms --sse-kms-key-id "$BACKUP_KMS_KEY_ID" --only-show-errors
fi

if [[ -n "${BACKUP_METRICS_FILE:-}" ]]; then
  metrics_tmp="$backup_tmp_dir/backup.prom"
  printf 'koala_backup_last_success_timestamp_seconds %s\n' "$(date -u +%s)" > "$metrics_tmp"
  install -m 0644 "$metrics_tmp" "$BACKUP_METRICS_FILE"
fi
