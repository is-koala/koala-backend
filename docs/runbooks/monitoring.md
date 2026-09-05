# Backend monitoring runbook

`/actuator/health/liveness` and `/actuator/health/readiness` are public load-balancer probes with no details. `/actuator/metrics` and `/actuator/prometheus` are exposed by Actuator but remain behind the existing admin authorization rule. Prometheus rules live in `ops/monitoring/prometheus-alerts.yml`.

The application emits `koala.backend.errors` with only bounded `code` and exception `type` tags. Payment initiation and provider callback paths emit `koala.payment.operations` with fixed `operation` and `outcome` values; the finite counter set is registered at startup so a post-scrape synthetic failure is visible to the five-minute `increase` alert. No payment, user, assignment, provider transaction or token identifier is used as a tag. Sentry is disabled by default. When enabled, the backend sends a generic event with the two error allowlist tags; request, user, IP, throwable, breadcrumbs, extras, contexts and raw messages are removed by the strict sanitizer.

`koala-web` owns a separate client Sentry project and sanitizer. `koala-admin` does not yet contain an application runtime; its client integration is therefore an explicit dependency of `koala-admin#2` (Wave 4) and must use a third, separate Sentry project when that scaffold lands. Backend issue #29 must remain open until that runtime integration and a real provider receipt are verified.

Before enabling Sentry externally:

1. Create separate staging and production projects and DSNs in the secret store.
2. Disable default PII and IP-address storage in the provider project.
3. Route critical alerts to the on-call channel and assign the owner labels.
4. Send a synthetic error containing fake email, phone, token and IP markers; verify none are searchable in the received event.
5. Verify 5xx, payment, Rabbit lag/DLQ, scheduled job, stale backup and certificate alerts in staging.

Do not place request bodies, user IDs, email, phone, tokens, addresses, payment references or exception messages in monitoring tags. Real Sentry receipt, provider-side scrubbing and live notification delivery remain external acceptance gates.
