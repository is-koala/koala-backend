# Account anonymization data map

The scheduled lifecycle is `REQUESTED` → waiting period → `ANONYMIZED`. A request remains `REQUESTED` with a user-visible blocker while there is a pending payment, escrow balance, active assignment or active dispute. An administrator may move it to `REJECTED` with a required reason. Reprocessing a terminal request is a no-op.

| Data/table | Action | Retention reason |
| --- | --- | --- |
| `users`, `addresses` | Replace name/email/username, clear phone, birth, identity, profile, CV, exact address and provider identifiers; soft-delete account. | Anonymous subject shell preserves referential integrity. |
| `media`, profile/CV/verification documents | Delete storage object idempotently; clear filename/type/URL and soft-delete metadata. | No personal file retained. |
| `messages`, `reviews`, `reports`, `disputes` | Redact authored free text; remove review/report/dispute notes while retaining state. | Marketplace dispute and moderation state remains auditable. |
| `experiences`, `educations`, availability, alerts, templates, applications | Remove free text/location and soft-delete user-owned profile/work-search data. | No retention requirement. |
| `work_sessions` | Clear check-in/out coordinates. | Work duration/state may remain for settlement evidence. |
| `sessions`, devices, login attempts, identity challenges, verifications, authorities | Revoke/delete credentials and clear identifiers/tokens. | Security access ends immediately. |
| preferences, saved jobs, bank accounts | Disable/delete preferences; redact IBAN/account-holder fields. | No reusable financial credential remains. |
| `payments`, assignments, invoices | Retain transactional state linked only through the anonymized subject shell. | Financial/legal retention and reconciliation. |
| `consent_records` | Retain agreement/version/decision/time; clear IP and user agent. | Legal consent evidence without readable network/device PII. |
| `account_deletion_requests` | Clear user relation and free-text reason; retain opaque `subject_id`, status and timestamps. | Lifecycle/audit evidence. |
| `account_anonymization_audit` | Append one idempotent row per completed step. | Operational proof without copied personal data. |

The implementation executes under a database transaction and stores no personal values in audit rows. Storage deletion is itself idempotent. Provider logs, replicas, analytics exports and independent backups require their own retention/erasure controls outside this repository.
