# Alert response runbook

All production rules route to the single backend on-call channel configured outside this repository. The `owner` label names the accountable team; the on-call platform owner maintains routing and escalation. Notification delivery is a deployment acceptance gate.

| Alert | First response | Escalate when |
| --- | --- | --- |
| `KoalaHighHttp5xxRate` | Compare release/environment and safe error-code rates; check health and the latest deployment. | Rate remains above 5% for ten minutes or checkout/authentication is affected. |
| `KoalaPaymentIntegrationFailure` | Pause risky payment rollout, inspect provider health and the bounded technical outcome; never replay blindly. A synthetic callback drill must begin only after Prometheus has scraped the pre-registered zero-valued series. | A callback cannot be reconciled within five minutes or money state is ambiguous. |
| `KoalaPayoutSettlementOverdue` | Run the reconciliation queue in `payments.md` §4 step 7; match each row against the provider settlement report using `provider_reference` only. Never mark a payout completed without a statement line. | No statement line explains the row, or the provider confirms the instruction was accepted but no transfer left. |
| `KoalaPayoutSettlementEvidenceMismatch` | Read the rejected field name from the log line; confirm the statement line belongs to that payout. The payout stayed in `PROCESSING` and no money state changed. | The statement line and the payout snapshot genuinely disagree on amount or currency. |
| `KoalaPaymentFailureSpike` | Compare provider decline and fraud-rejection rates with the normal baseline; do not treat one customer decline as an integration outage. | The rate remains elevated for five minutes or multiple payment methods are affected. |
| `KoalaRabbitConsumerLag` | Check consumer replica health, connection/channel counts and oldest queued message age. | Queue continues growing after consumer recovery or processing violates its SLA. |
| `KoalaRabbitDeadLetterQueue` | Quarantine and classify one message using identifiers only; correct the consumer before replay. | Any payment/notification message is involved or the DLQ continues growing. |
| `KoalaScheduledJobFailure` | Identify the failed scheduled task from bounded metric labels and inspect the latest safe log correlation. | Retry is unsafe, repeated, or affects deletion/settlement lifecycle state. |
| `KoalaBackupStale` | Stop nonessential data changes, verify runner health, destination availability and last checksum. | No valid encrypted recovery point exists inside the RPO target. Follow `restore.md`. |
| `KoalaCertificateExpiring` | Confirm the certificate resource alias and renewal controller state; trigger a staging renewal check. | Less than seven days remain or automated renewal fails. |

For every incident, record timeline, affected environment, safe metric labels, decision and resolution. Never copy request bodies, personal data, tokens, payment references, exception messages or secret values into the incident channel.
