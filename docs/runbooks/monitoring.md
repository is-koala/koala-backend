# Backend monitoring runbook

`/actuator/health/liveness` and `/actuator/health/readiness` are public load-balancer probes with no details. `/actuator/metrics` and `/actuator/prometheus` are exposed by Actuator but remain behind the existing admin authorization rule. Prometheus rules live in `ops/monitoring/prometheus-alerts.yml`.

The application emits `koala.backend.errors` with only bounded `code` and exception `type` tags. Sentry is disabled by default. When enabled, the backend sends a generic event with those two allowlisted tags; request, user, IP, throwable, breadcrumbs, extras, contexts and raw messages are removed by the strict sanitizer.

Before enabling Sentry externally:

1. Create separate staging and production projects and DSNs in the secret store.
2. Disable default PII and IP-address storage in the provider project.
3. Route critical alerts to the on-call channel and assign the owner labels.
4. Send a synthetic error containing fake email, phone, token and IP markers; verify none are searchable in the received event.
5. Verify 5xx, payment, Rabbit lag/DLQ, scheduled job, stale backup and certificate alerts in staging.

Do not place request bodies, user IDs, email, phone, tokens, addresses, payment references or exception messages in monitoring tags. Real Sentry receipt, provider-side scrubbing and live notification delivery remain external acceptance gates.
