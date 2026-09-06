# Secret rotation runbook

Never paste a secret into a terminal transcript, Git diff, issue, pull request, chat, screenshot or evidence document. Use only the approved secret store/provider console. Evidence records logical name, environment, resource alias, time, operator and result.

## Zero-downtime sequence

1. Create a second credential with the same minimal scope while the old credential remains valid.
2. Store the new version under the existing runtime secret reference and deploy/restart one instance at a time.
3. Verify health, authentication and provider calls from every instance; watch error metrics.
4. Revoke the old credential and verify old-credential attempts fail without exposing the value.
5. Record the new creation/expiry metadata and next rotation date in the private operations system.

This sequence applies to database/Rabbit users, Iyzico, storage, backup, mail, SMS, Sentry auth and Keycloak client secrets when the provider supports overlapping credentials. Google keys require API restriction, application restriction, quota and budget-alert verification before old-key revocation. Mobile/web releases must propagate the replacement key before revocation.

## Type-specific checks

- Database and RabbitMQ: create a new role/user, grant only the existing schema or vhost permissions, roll instances, verify connections, then drop the old principal.
- Iyzico: create the replacement merchant credential, update one environment, run a synthetic authorization/callback/refund reconciliation, then revoke the old credential.
- Media and backup S3: create a new principal key, verify its bucket/prefix and KMS policy with a write-read-delete canary, roll the runner, then disable and delete the old key. Never reuse the media principal for backup.
- Google backend: create a server key, restrict to the exact egress IPs and two approved APIs, deploy and verify Places/Geocoding before deleting the old key.
- Google Android/iOS/web: create the matching package/signing certificate, bundle identifier or HTTPS-referrer restriction, ship through the relevant environment, verify telemetry, then revoke after the old client population is below the approved threshold.
- SMTP and SMS: add a provider credential scoped to the approved sender, send to an allowlisted synthetic recipient, roll instances, then revoke the previous credential.
- Keycloak and Sentry: overlap the client credential/auth token, verify OIDC or release upload, then revoke the old version. DSN rotation also requires updating ingestion rules and a sanitized synthetic event.
- Apple and Play release credentials: create the least-privilege upload identity, update CI, perform a non-production validation/upload check, then revoke the old identity.

## JWT exception

The current single `JWT_SECRET` cannot be rotated transparently. Rotation invalidates active access tokens and can force reauthentication. Schedule a maintenance window or first implement key IDs with overlapping verification keys. Never silently rotate it as a low-risk exercise.

## Validation and rollback

Use synthetic identities/data and provider health endpoints. Roll back the deployment to the old secret reference only while the old credential is still active. A leak response skips the overlap window: revoke immediately, rotate dependent credentials, invalidate sessions where relevant, audit access and notify the security owner.

Repository history can be checked with `ops/security/audit-secret-history.sh`. Its output is deliberately limited to commit hashes and logical secret names; it never prints matched lines or values. A real low-risk credential rotation and provider restriction screenshots remain external acceptance gates.
