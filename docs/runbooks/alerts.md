# Alert response runbook

All production rules route to the single backend on-call channel configured outside this repository. The `owner` label names the accountable team; the on-call platform owner maintains routing and escalation. Notification delivery is a deployment acceptance gate.

| Alert | First response | Escalate when |
| --- | --- | --- |
| `KoalaHighHttp5xxRate` | Compare release/environment and safe error-code rates; check health and the latest deployment. | Rate remains above 5% for ten minutes or checkout/authentication is affected. |
| `KoalaPaymentFailureSpike` | Pause risky payment rollout, inspect provider health and count failures by safe code; never replay blindly. | A callback cannot be reconciled within five minutes or money state is ambiguous. |
| `KoalaRabbitConsumerLag` | Check consumer replica health, connection/channel counts and oldest queued message age. | Queue continues growing after consumer recovery or processing violates its SLA. |
| `KoalaRabbitDeadLetterQueue` | Quarantine and classify one message using identifiers only; correct the consumer before replay. | Any payment/notification message is involved or the DLQ continues growing. |
| `KoalaScheduledJobFailure` | Identify the failed scheduled task from bounded metric labels and inspect the latest safe log correlation. | Retry is unsafe, repeated, or affects deletion/settlement lifecycle state. |
| `KoalaBackupStale` | Stop nonessential data changes, verify runner health, destination availability and last checksum. | No valid encrypted recovery point exists inside the RPO target. Follow `restore.md`. |
| `KoalaCertificateExpiring` | Confirm the certificate resource alias and renewal controller state; trigger a staging renewal check. | Less than seven days remain or automated renewal fails. |

For every incident, record timeline, affected environment, safe metric labels, decision and resolution. Never copy request bodies, personal data, tokens, payment references, exception messages or secret values into the incident channel.
