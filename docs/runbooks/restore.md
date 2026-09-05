# PostgreSQL backup and restore runbook

## Objectives and retention

- RPO target: at most 15 minutes when WAL archiving is healthy; otherwise the latest daily backup.
- RTO target: four hours from declared incident to validated service restoration.
- Retention: **7 daily / 4 weekly / 6 monthly** logical backups, plus seven days of WAL.
- Media: the media bucket must use versioning and cross-region replication independently of database backups.

These are deployment targets, not evidence that the provider settings exist. The seven-day history, a measured production-sized restore, object lock, replication, alarms and RPO/RTO proof remain external acceptance gates.

## Preconditions

Use an isolated recovery account and empty target database. Confirm the separate backup identity has read-only recovery access. Never put credentials in commands, tickets, logs or evidence documents. Record only resource aliases, timestamps, row counts and checksums.

Run the non-mutating contract first:

```bash
BACKUP_DATABASE_URL=postgresql://placeholder \
BACKUP_S3_URI=s3://placeholder \
BACKUP_KMS_KEY_ID=placeholder \
BACKUP_ENVIRONMENT=staging \
ops/backup/postgres-backup.sh --dry-run
```

## Daily backup verification

1. Confirm the scheduler completed and `koala_backup_last_success_timestamp_seconds` is less than 26 hours old.
2. Confirm dump and `.sha256` objects exist in the daily prefix and use the expected KMS key alias.
3. Confirm object versioning, Object Lock, lifecycle and cross-region replication status in the provider console.
4. Inspect seven distinct daily recovery points and the expected weekly/monthly prefixes.

## Restore drill

1. Create a disposable empty PostgreSQL target in the recovery environment.
2. Export `RESTORE_DATABASE_URL`, `BACKUP_S3_URI`, `BACKUP_ENVIRONMENT` through the approved secret store.
3. Run `ops/backup/restore-latest.sh --dry-run`, then set `RESTORE_CONFIRM_EMPTY_TARGET=I_HAVE_VERIFIED_THE_TARGET_IS_EMPTY` and run without the flag.
4. Validate Liquibase state, critical table counts, foreign-key constraints and a read-only application smoke test.
5. For point-in-time recovery, restore the base backup into an isolated PostgreSQL data directory, configure `restore_command` to fetch `wal/<environment>/%f`, set `recovery_target_time`, and start PostgreSQL. Stop before promotion until the target timestamp is confirmed.
6. Record start/end times, selected recovery point, anonymous aggregate row counts, checksum result and smoke-test status. Do not record rows or secret values.
7. Destroy the disposable recovery environment through the platform's approved workflow.

Escalate immediately when the checksum fails, a required WAL segment is absent, RPO exceeds 15 minutes, RTO exceeds four hours, or the restored schema/data checks fail.
