#!/usr/bin/env bash
set -euo pipefail

dry_run=false
tier="daily"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      dry_run=true
      shift
      ;;
    --tier)
      tier="${2:-}"
      shift 2
      ;;
    *)
      printf 'unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done
if [[ "$tier" != "daily" && "$tier" != "weekly" && "$tier" != "monthly" ]]; then
  printf 'tier must be daily, weekly or monthly\n' >&2
  exit 2
fi

required=(RESTORE_DATABASE_URL BACKUP_S3_URI BACKUP_ENVIRONMENT)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'missing required variable: %s\n' "$name" >&2
    exit 2
  fi
done

printf 'phase=select-latest-backup environment=%s retention=%s\n' "$BACKUP_ENVIRONMENT" "$tier"
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
retention_prefix="${base_prefix:+$base_prefix/}$tier/$BACKUP_ENVIRONMENT/"
latest_key="$(aws s3api list-objects-v2 --bucket "$bucket" --prefix "$retention_prefix" --query 'reverse(sort_by(Contents[?ends_with(Key, `.dump`)], &LastModified))[0].Key' --output text)"
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
