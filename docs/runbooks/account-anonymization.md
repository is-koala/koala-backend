# Account anonymization data map

The scheduled lifecycle is `REQUESTED` → waiting period → `ANONYMIZED`. A request remains `REQUESTED` with a user-visible blocker while there is a pending payment, escrow balance, active assignment or active dispute. A blocked request receives a 24-hour `next_attempt_at`; never-attempted requests are selected before retries so an old blocker cannot monopolize the batch. An administrator may move it to `REJECTED` with a required reason. Reprocessing a terminal request is a no-op.

| Data/table | Action | Retention reason |
| --- | --- | --- |
| `users`, `addresses`, individual jobs | Replace name/email/username, clear phone, birth, identity, profile, CV and provider identifiers; scrub exact fields and coordinates from profile and retained individual-job addresses; force `address_public=false`. | Anonymous subject shell and city/district context preserve referential and transactional integrity without a reusable exact address. |
| `media`, profile/CV/verification documents | After the database commit, delete only personal storage objects idempotently; clear filename/type/URL and soft-delete metadata. Media merely uploaded by the user for a retained company or job record is preserved. | No personal file retained and shared business records remain intact. |
| `messages`, `conversations`, `reviews`, `user_blocks`, `cancellations`, `reports`, `disputes` | Redact authored free text and cached last-message text; remove review, block, cancellation, report and dispute notes when the anonymized user authored, received or participated in the related record while retaining state. | Marketplace transaction and moderation state remains auditable without readable personal narrative. |
| `experiences`, `educations`, availability, alerts, templates, applications | Remove free text/location and soft-delete user-owned profile/work-search data. | No retention requirement. |
| `work_sessions` | Clear check-in/out coordinates. | Work duration/state may remain for settlement evidence. |
| `sessions`, devices, password-reset tokens, login attempts, identity challenges, verifications, authorities | Revoke/delete credentials and clear identifiers/tokens. | Security access ends immediately. |
| communication/notification preferences, saved jobs, talent-pool entries, bank accounts | Disable and soft-delete preferences, remove talent notes, and redact IBAN/account-holder fields. | No reusable financial credential or user-linked free text remains. |
| `payments`, assignments, invoices | Retain transactional state linked only through the anonymized subject shell. | Financial/legal retention and reconciliation. |
| `consent_records` | Retain agreement/version/decision/time; clear IP and user agent. | Legal consent evidence without readable network/device PII. |
| `commercial_message_consents` | Retain category/channel/decision/time/source; clear IP and user agent. | Commercial-consent evidence remains without readable network/device PII. |
| `unsubscribe_tokens` | Expire immediately and soft-delete. | Previously issued unsubscribe credentials cannot be reused after account deletion. |
| `account_deletion_requests` | Clear user relation and free-text reason; retain opaque `subject_id`, status and timestamps. During the rolling-deploy expand phase `subject_id` stays nullable and a compatibility trigger fills it for old binaries; a later contract migration may enforce `NOT NULL` after old binaries are retired. | Lifecycle/audit evidence without breaking mixed-version writes. |
| `account_anonymization_audit` | Append one idempotent row per completed step. | Operational proof without copied personal data. |
| `account_media_deletion_outbox` | Persist the minimum storage key in the same transaction, try after commit, retry hourly, and delete the outbox row after storage confirms deletion. | A storage outage cannot turn an `ANONYMIZED` request into a forgotten personal object. |

The implementation executes under a database transaction and stores no personal values in audit rows. Storage deletion starts only after that transaction commits and is itself idempotent. Failed object deletions remain durable in `account_media_deletion_outbox`; the retry worker runs every 15 minutes and makes due attempts hourly until deletion succeeds. Provider logs, replicas, analytics exports and independent backups require their own retention/erasure controls outside this repository.

Staff access is fail-closed: listing deletion requests requires `user.read_pii`, while rejection requires the dedicated mutating `user.deletion.manage` permission seeded to `TRUST_LEAD` (and inherited by `OWNER`). The rejection action is named `user.deletion.reject` for audit capture.
