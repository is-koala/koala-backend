#!/usr/bin/env bash
set -euo pipefail

dry_run=false
if [[ "${1:-}" == "--dry-run" ]]; then
  dry_run=true
fi

required=(RESTORE_DATABASE_URL BACKUP_S3_URI BACKUP_ENVIRONMENT)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'missing required variable: %s\n' "$name" >&2
    exit 2
  fi
done

printf 'phase=select-latest-backup environment=%s\n' "$BACKUP_ENVIRONMENT"
printf 'phase=verify-checksum\n'
printf 'phase=restore-and-validate\n'

if [[ "$dry_run" == true ]]; then
  exit 0
fi
if [[ "${RESTORE_CONFIRM_EMPTY_TARGET:-}" != "I_HAVE_VERIFIED_THE_TARGET_IS_EMPTY" ]]; then
  printf 'RESTORE_CONFIRM_EMPTY_TARGET confirmation is required\n' >&2
  exit 3
fi

for command_name in aws jq pg_restore sha256sum; do
  command -v "$command_name" >/dev/null
done

restore_tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "$restore_tmp_dir"' EXIT
bucket_and_prefix="${BACKUP_S3_URI#s3://}"
bucket="${bucket_and_prefix%%/*}"
base_prefix="${bucket_and_prefix#*/}"
if [[ "$base_prefix" == "$bucket_and_prefix" ]]; then
  base_prefix=""
fi
daily_prefix="${base_prefix:+$base_prefix/}daily/$BACKUP_ENVIRONMENT/"
latest_key="$(aws s3api list-objects-v2 --bucket "$bucket" --prefix "$daily_prefix" --query 'reverse(sort_by(Contents[?ends_with(Key, `.dump`)], &LastModified))[0].Key' --output text)"
if [[ -z "$latest_key" || "$latest_key" == "None" ]]; then
  printf 'no backup was found\n' >&2
  exit 4
fi

archive="$restore_tmp_dir/${latest_key##*/}"
checksum="$archive.sha256"
aws s3 cp "s3://$bucket/$latest_key" "$archive" --only-show-errors
aws s3 cp "s3://$bucket/$latest_key.sha256" "$checksum" --only-show-errors
(cd "$restore_tmp_dir" && sha256sum --check "$(basename "$checksum")")
started_at="$(date -u +%s)"
pg_restore --exit-on-error --no-owner --no-acl --dbname="$RESTORE_DATABASE_URL" "$archive"
finished_at="$(date -u +%s)"
printf 'restore_duration_seconds=%s\n' "$((finished_at - started_at))"
