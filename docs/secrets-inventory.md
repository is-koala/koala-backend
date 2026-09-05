# Secret inventory

This inventory records logical names and controls only. It must never contain a credential value. `Doğrulanmadı` means provider-side evidence is still an external acceptance gate.

## Google API keys

| Logical name | Purpose | Environment | Storage | Required restriction | Owner | Last rotation |
| --- | --- | --- | --- | --- | --- | --- |
| GOOGLE_KEY_01_BACKEND_PLACES_DEV | Places + Geocoding backend | Development | Development secret store | Server egress IP; only Places API (New) and Geocoding API | Platform | Doğrulanmadı |
| GOOGLE_KEY_02_BACKEND_PLACES_STAGING | Places + Geocoding backend | Staging | Staging secret store | Staging egress IP; only Places API (New) and Geocoding API | Platform | Doğrulanmadı |
| GOOGLE_KEY_03_BACKEND_PLACES_PROD | Places + Geocoding backend | Production | Production secret store | Production egress IP; only Places API (New) and Geocoding API | Platform | Doğrulanmadı |
| GOOGLE_KEY_04_ANDROID_MAPS_DEV | Android Maps SDK | Development | Development mobile build secret store | Android package and development signing SHA-1; only Maps SDK for Android | Mobile | Doğrulanmadı |
| GOOGLE_KEY_05_ANDROID_MAPS_STAGING | Android Maps SDK | Staging | Staging mobile build secret store | Staging package and signing SHA-1; only Maps SDK for Android | Mobile | Doğrulanmadı |
| GOOGLE_KEY_06_ANDROID_MAPS_PROD | Android Maps SDK | Production | Production mobile build secret store | Production package and release signing SHA-1; only Maps SDK for Android | Mobile release | Doğrulanmadı |
| GOOGLE_KEY_07_IOS_MAPS_DEV | iOS Maps SDK | Development | Development mobile build secret store | Development bundle identifier; only Maps SDK for iOS | Mobile | Doğrulanmadı |
| GOOGLE_KEY_08_IOS_MAPS_STAGING | iOS Maps SDK | Staging | Staging mobile build secret store | Staging bundle identifier; only Maps SDK for iOS | Mobile | Doğrulanmadı |
| GOOGLE_KEY_09_IOS_MAPS_PROD | iOS Maps SDK | Production | Production mobile build secret store | Production bundle identifier; only Maps SDK for iOS | Mobile release | Doğrulanmadı |
| GOOGLE_KEY_10_WEB_MAPS_PROD | Maps JavaScript | Production | Production web build secret store | Exact HTTPS referrers; only Maps JavaScript API | Web | Doğrulanmadı |

### ADR-0007 binary control

Mobile/web Maps keys are public identifiers that may enter their intended client binary, but application/API restrictions, quotas and budget alerts are mandatory. `GOOGLE_KEY_01`–`03` are server credentials and must never enter a mobile or web bundle. No database, payment, object-storage, mail, SMS, JWT, Keycloak, Sentry auth or release private key may enter any client binary.

## Backend and platform secrets

| Logical name | Purpose | Environments | Storage | Restriction | Owner | Last rotation |
| --- | --- | --- | --- | --- | --- | --- |
| `DB_PASSWORD` | Application database login | dev/staging/prod | Application secret store | Per-environment restricted database role | Platform | Doğrulanmadı |
| `RABBITMQ_PASSWORD` | Queue broker login | dev/staging/prod | Application secret store | Per vhost/environment | Platform | Doğrulanmadı |
| `JWT_SECRET` | Access-token signing | staging/prod | Application secret store | Backend runtime only | Backend security | Doğrulanmadı |
| `KEYCLOAK_CLIENT_SECRET` | Admin staff OIDC client | staging/prod when enabled | Identity secret store | Confidential client and exact redirects | Identity | Doğrulanmadı |
| `IYZICO_API_KEY`, `IYZICO_SECRET_KEY` | Payment API | sandbox/production | Payment secret store | Separate sandbox/production merchant scope | Payments | Doğrulanmadı |
| `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` | Media object access | staging/prod | Media secret store | Media bucket prefix only | Platform | Doğrulanmadı |
| `BACKUP_ACCESS_KEY`, `BACKUP_SECRET_KEY` | Backup object access | staging/prod | Backup runner secret store | Separate principal; backup bucket only | Platform | Doğrulanmadı |
| `BACKUP_KMS_KEY_ID` | Backup encryption key alias | staging/prod | Backup runner configuration | Decrypt limited to recovery role | Platform security | Doğrulanmadı |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Transactional email | staging/prod | Mail secret store | Per-environment sender | Backend | Doğrulanmadı |
| `SMS_API_KEY`, `SMS_API_HASH` | Transactional SMS | staging/prod | Messaging secret store | Per-environment/provider scope | Backend | Doğrulanmadı |
| `SENTRY_DSN`, `SENTRY_AUTH_TOKEN` | Error ingestion/release upload | staging/prod | Runtime/CI secret stores | DSN per project; auth token CI only | Platform | Doğrulanmadı |
| `APPLE_CONNECT_API_KEY`, `APPLE_CONNECT_PRIVATE_KEY` | iOS release upload | production release | Mobile CI secret store | Upload scope only | Mobile release | Doğrulanmadı |
| `PLAY_SERVICE_ACCOUNT_JSON` | Android release upload | production release | Mobile CI secret store | Play upload role only | Mobile release | Doğrulanmadı |

Every row requires an accountable owner, creation/last-rotation date, expiry where supported, provider resource alias and evidence link in the private operations system. Those fields are intentionally not invented in this repository.

## Repository-history evidence

On 2026-09-06, `git log --all --format='%H' -- .env` returned no commit and `git ls-files` returned only `.env.example` among environment files. The names-only history audit reported five candidate commits across AWS access, AWS secret, JWT, Google API and payment secret categories; likely placeholders/references still require value-safe review by the security owner. No matched line or value was emitted.
