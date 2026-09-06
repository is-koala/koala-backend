#!/usr/bin/env bash
set -euo pipefail

dry_run=false
tier="daily"
target_time=""
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
    --target-time)
      target_time="${2:-}"
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
if [[ -z "$target_time" || ! "$target_time" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]+)?Z$ ]]; then
  printf 'target time must be a UTC ISO-8601 timestamp ending in Z\n' >&2
  exit 2
fi

required=(BACKUP_S3_URI BACKUP_ENVIRONMENT RESTORE_DATA_DIR RESTORE_WAL_SCRIPT)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'missing required variable: %s\n' "$name" >&2
    exit 2
  fi
done
if [[ ! "$RESTORE_WAL_SCRIPT" =~ ^[/A-Za-z0-9._-]+$ ]]; then
  printf 'RESTORE_WAL_SCRIPT contains unsupported characters\n' >&2
  exit 2
fi
if [[ "${RESTORE_WAL_SCRIPT##*/}" != "restore-wal.sh" ]]; then
  printf 'RESTORE_WAL_SCRIPT must point to restore-wal.sh\n' >&2
  exit 2
fi

printf 'phase=select-physical-base environment=%s retention=%s\n' "$BACKUP_ENVIRONMENT" "$tier"
printf 'phase=verify-checksum\n'
printf 'phase=configure-pitr target=%s\n' "$target_time"

if [[ "$dry_run" == true ]]; then
  exit 0
fi
if [[ "${RESTORE_CONFIRM_EMPTY_DATA_DIR:-}" != "I_HAVE_VERIFIED_THE_DATA_DIRECTORY_IS_EMPTY" ]]; then
  printf 'RESTORE_CONFIRM_EMPTY_DATA_DIR confirmation is required\n' >&2
  exit 3
fi

for command_name in aws jq sha256sum tar; do
  command -v "$command_name" >/dev/null
done

mkdir -p "$RESTORE_DATA_DIR"
if [[ -n "$(find "$RESTORE_DATA_DIR" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  printf 'restore data directory is not empty\n' >&2
  exit 3
fi

restore_tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "$restore_tmp_dir"' EXIT
bucket_and_prefix="${BACKUP_S3_URI#s3://}"
bucket="${bucket_and_prefix%%/*}"
base_prefix="${bucket_and_prefix#*/}"
if [[ "$base_prefix" == "$bucket_and_prefix" ]]; then
  base_prefix=""
fi
retention_prefix="${base_prefix:+$base_prefix/}pitr/$tier/$BACKUP_ENVIRONMENT/"
latest_key="$(aws s3api list-objects-v2 --bucket "$bucket" --prefix "$retention_prefix" --output json \
  | jq -r --arg target "$target_time" \
    '[.Contents[]? | select(.Key | endswith(".base.tar")) | select(.LastModified <= $target)] | sort_by(.LastModified) | last | .Key // empty')"
if [[ -z "$latest_key" || "$latest_key" == "None" ]]; then
  printf 'no physical base backup was found\n' >&2
  exit 4
fi

archive="$restore_tmp_dir/${latest_key##*/}"
checksum="$archive.sha256"
aws s3 cp "s3://$bucket/$latest_key" "$archive" --only-show-errors
aws s3 cp "s3://$bucket/$latest_key.sha256" "$checksum" --only-show-errors
(cd "$restore_tmp_dir" && sha256sum --check "$(basename "$checksum")")
bundle_directory="$restore_tmp_dir/bundle"
mkdir -p "$bundle_directory"
tar -xf "$archive" -C "$bundle_directory"
tar -xf "$bundle_directory/base.tar" -C "$RESTORE_DATA_DIR"
mkdir -p "$RESTORE_DATA_DIR/pg_wal"
tar -xf "$bundle_directory/pg_wal.tar" -C "$RESTORE_DATA_DIR/pg_wal"
chmod 0700 "$RESTORE_DATA_DIR"
touch "$RESTORE_DATA_DIR/recovery.signal"
printf "restore_command = '%s %%p %%f'\n" "$RESTORE_WAL_SCRIPT" >> "$RESTORE_DATA_DIR/postgresql.auto.conf"
printf "recovery_target_time = '%s'\n" "$target_time" >> "$RESTORE_DATA_DIR/postgresql.auto.conf"
printf "recovery_target_action = 'pause'\n" >> "$RESTORE_DATA_DIR/postgresql.auto.conf"
printf 'pitr_ready data_directory=%s\n' "$RESTORE_DATA_DIR"
