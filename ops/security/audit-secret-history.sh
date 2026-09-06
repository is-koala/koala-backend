#!/usr/bin/env bash
set -euo pipefail

git rev-parse --is-inside-work-tree >/dev/null
findings=0

while IFS='|' read -r secret_name pattern; do
  while IFS= read -r commit_hash; do
    [[ -z "$commit_hash" ]] && continue
    printf '%s secret-name=%s\n' "$commit_hash" "$secret_name"
    findings=$((findings + 1))
  done < <(git log --all --format='%H' -G"$pattern" -- . ':!*.md' ':!*.example' ':!ops/security/audit-secret-history.sh' | sort -u)
done <<'PATTERNS'
AWS_ACCESS_KEY_ID|(AWS_ACCESS_KEY_ID|STORAGE_ACCESS_KEY|BACKUP_ACCESS_KEY)[[:space:]]*[=:][[:space:]]*[^$[:space:]]
AWS_SECRET_ACCESS_KEY|(AWS_SECRET_ACCESS_KEY|STORAGE_SECRET_KEY|BACKUP_SECRET_KEY)[[:space:]]*[=:][[:space:]]*[^$[:space:]]
JWT_SECRET|JWT_SECRET[[:space:]]*[=:][[:space:]]*[^$[:space:]]
GOOGLE_API_KEY|(GOOGLE_PLACES_API_KEY|GOOGLE_MAPS_API_KEY)[[:space:]]*[=:][[:space:]]*[^$[:space:]]
PAYMENT_SECRET|(IYZICO_SECRET_KEY|PAYMENT_SECRET_KEY)[[:space:]]*[=:][[:space:]]*[^$[:space:]]
PRIVATE_KEY|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY
PATTERNS

while IFS= read -r commit_hash; do
  [[ -z "$commit_hash" ]] && continue
  printf '%s secret-name=SENSITIVE_FILE\n' "$commit_hash"
  findings=$((findings + 1))
done < <(git log --all --format='%H' -- '.env' '*.pem' '*.p12' '*.pfx' '*.jks' | sort -u)

printf 'audit-complete findings=%s output=names-only\n' "$findings"
