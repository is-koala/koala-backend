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
- `STOMP_STAGING_ORIGIN=https://staging.iskoala.com`
- DB, RabbitMQ, S3 ve Google anahtarları production ile ortak olamaz

`SERVICE_ENVIRONMENT` ile `IYZICO_BASE_URL` artık açılışta karşılıklı
doğrulanıyor: staging canlı Iyzico ortamına, production sandbox'a bağlanamaz ve
eşleşmeyen yapılandırmada uygulama hiç açılmaz. Kural ve operasyon adımları
`docs/runbooks/payments.md` içinde.

## STOMP handshake origin'leri

ADR-0011 gereği REST CORS allowlist'i boştur ve web origin'i oraya **eklenmez**;
tarayıcının doğrudan konuştuğu tek yüzey STOMP handshake'idir ve onun allowlist'i
ayrıdır. Değerler ortam başına verilir, yalnız tam origin eşleşmesi kabul edilir
ve boş bırakılan değer o ortamın tarayıcı bağlantısını **tümüyle kapatır**:

| Değişken | Local | Staging | Production |
| --- | --- | --- | --- |
| `STOMP_PRODUCTION_ORIGIN` | boş | boş | `https://iskoala.com` |
| `STOMP_STAGING_ORIGIN` | boş | `https://staging.iskoala.com` | boş |
| `STOMP_DEVELOPMENT_ORIGIN` | `http://localhost:3000` | boş | boş |

Origin başlığı taşımayan native (mobil) bağlantılar bu listeden etkilenmez.
Soket `wss://` üzerinden servis edilmeli; TLS reverse proxy'de sonlanır ve kod
bunu zorlayamaz (CLAUDE.md §2ai).

Staging web `X-Robots-Tag: noindex, nofollow, noarchive` ile tümden kapatılır ve
Dokploy proxy katmanında HTTP Basic Auth arkasında tutulur. Parola ve anahtarlar
yalnız Dokploy secret store'da yaşar; issue, log veya repoya yazılmaz.

## Dağıtım

Backend ve web üretim dağıtımı yalnız doğrulanmış `v*` etiketiyle tetiklenir.
Staging ve production ayrı Dokploy servisleri, domainleri, persistent volume'ları
ve secret setleri kullanır. Staging smoke testi kayıt → ilan → Başvuru akışında
oluşan kayıtların production veritabanında bulunmadığını doğrular. Production
deploy öncesi yedek, sonrası dar health/readiness smoke uygulanır.
