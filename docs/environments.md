# Koala ortam sözleşmesi

Dalga 1 kararı, ADR-0001'deki tek pre-production ortamını sona erdirir. Local,
staging ve production aynı uygulama imajını, birbirinden tamamen ayrılmış veri
ve sağlayıcı kaynaklarıyla çalıştırır.

| Kaynak | Local | Staging | Production |
| --- | --- | --- | --- |
| API | yerel Spring Boot | `https://api-staging.iskoala.com` | `https://api.iskoala.com` |
| Web | `http://localhost:3000` | `https://staging.iskoala.com` | `https://iskoala.com` |
| Veri | yerel PostgreSQL | yalnız staging PostgreSQL | yalnız production PostgreSQL |
| Kuyruk | yerel RabbitMQ | ayrı vhost ve kullanıcı | ayrı vhost ve kullanıcı |
| Depolama | yerel/test | ayrı bucket veya staging prefix | ayrı production bucket |
| Ödeme | iyzico Sandbox | iyzico Sandbox | iyzico Live |
| Adres | geliştirme anahtarı | ayrı kotalı staging anahtarı | ayrı production anahtarı |
| E-posta | allowlist/yakalama | `MAIL_DELIVERY_MODE=ALLOWLIST` | kontrollü canlı teslimat |

## Staging zorunlulukları

- `SERVICE_ENVIRONMENT=staging`
- `WEB_BASE_URL=https://staging.iskoala.com`
- `AUTH_LINK_PUBLIC_BASE_URL=https://staging.iskoala.com`
- `AUTH_LINK_APP_SCHEME=koala-staging`
- `PAYMENTS_RETURN_BASE_URL=koala-staging://`
- `IYZICO_BASE_URL=https://sandbox-api.iyzipay.com`
- `MAIL_DELIVERY_MODE=ALLOWLIST`; allowlist dışındaki teslimat reddedilir
- DB, RabbitMQ, S3 ve Google anahtarları production ile ortak olamaz

Staging web `X-Robots-Tag: noindex, nofollow, noarchive` ile tümden kapatılır ve
Dokploy proxy katmanında HTTP Basic Auth arkasında tutulur. Parola ve anahtarlar
yalnız Dokploy secret store'da yaşar; issue, log veya repoya yazılmaz.

## Dağıtım

Backend ve web üretim dağıtımı yalnız doğrulanmış `v*` etiketiyle tetiklenir.
Staging ve production ayrı Dokploy servisleri, domainleri, persistent volume'ları
ve secret setleri kullanır. Staging smoke testi kayıt → ilan → Başvuru akışında
oluşan kayıtların production veritabanında bulunmadığını doğrular. Production
deploy öncesi yedek, sonrası dar health/readiness smoke uygulanır.
