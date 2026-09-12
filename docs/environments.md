# Koala ortam sözleşmesi

Dalga 1 kararı, ADR-0001'deki tek pre-production ortamını sona erdirir. Local,
staging ve production aynı uygulama imajını, birbirinden tamamen ayrılmış veri
ve sağlayıcı kaynaklarıyla çalıştırır.

İlk lansmanda “mevcut altyapıyı production'a çevirme”, mevcut Dokploy uygulama
kaydı ile production domainlerinin terfi ettirilmesi demektir; pre-production
database, queue veya media state'inin production'a taşınması değildir. Bu sınır
ve geçiş sonrası staging modeli [ADR-0016](adr/0016-promote-the-deployment-not-its-state.md)
ile sabitlenmiştir.

| Kaynak | Local | Staging | Production |
| --- | --- | --- | --- |
| API | yerel Spring Boot | `https://api-staging.iskoala.com` | `https://api.iskoala.com` |
| Web | `http://localhost:3000` | `https://staging.iskoala.com` | `https://iskoala.com` |
| Veri | yerel PostgreSQL | yalnız staging PostgreSQL | yalnız production PostgreSQL |
| Kuyruk | yerel RabbitMQ | ayrı vhost ve kullanıcı | ayrı vhost ve kullanıcı |
| Depolama | yerel/test | ayrı bucket veya staging prefix | ayrı production bucket |
| Ödeme | iyzico Sandbox | iyzico Sandbox | iyzico Live |
| Adres | geliştirme anahtarı | ayrı kotalı staging anahtarı | ayrı production anahtarı |
| E-posta | allowlist/yakalama | açıkça seçilmiş `UNRESTRICTED` veya `ALLOWLIST` | kontrollü canlı teslimat |

## Staging zorunlulukları

- `SERVICE_ENVIRONMENT=staging`
- `WEB_BASE_URL=https://staging.iskoala.com`
- `AUTH_LINK_PUBLIC_BASE_URL=https://staging.iskoala.com`
- `AUTH_LINK_APP_SCHEME=koala-staging`
- `PAYMENTS_RETURN_BASE_URL=koala-staging://`
- `IYZICO_BASE_URL=https://sandbox-api.iyzipay.com`
- `MAIL_DELIVERY_MODE` açıkça seçilir. Mevcut Dokploy staging ortamı
  `UNRESTRICTED` kullanır; başlangıç bu seçimi kabul eder ve alıcı kapısının
  kapalı olduğunu `MAIL_DELIVERY_GUARD` ile uyarır.
  `ALLOWLIST` seçilirse liste dışındaki teslimat reddedilir; boş
  `MAIL_ALLOWED_RECIPIENTS` SMTP çağırmaz ve açılış `WARN` yazar. Tanıtım sitesi
  iletişim formu (`POST /public/contact`) `SUPPORT_MAILBOX` (varsayılan
  `info@iskoala.com`) adresine düşer.
- `STOMP_STAGING_ORIGIN=https://staging.iskoala.com`
- DB, RabbitMQ, S3 ve Google anahtarları production ile ortak olamaz

`SERVICE_ENVIRONMENT` ile `IYZICO_BASE_URL` artık açılışta karşılıklı
doğrulanıyor: staging canlı Iyzico ortamına, production sandbox'a bağlanamaz ve
eşleşmeyen yapılandırmada uygulama hiç açılmaz. Kural ve operasyon adımları
`docs/runbooks/payments.md` içinde.

## Production zorunlulukları

Aşağıdaki değerlerin hiçbiri sır değildir; `docs/secrets-inventory.md`'nin
disiplini burada da geçerli — **parola, anahtar ve token değeri bu tabloya
girmez**, yalnızca adı geçer ve değeri Dokploy secret store'da yaşar.

| Değişken | Production'da beklenen | Sapmada ne olur |
| --- | --- | --- |
| `SERVICE_ENVIRONMENT` | `production` | tanınmayan ad açılışı **durdurur** |
| `CORS_ALLOWED_ORIGINS` | **boş** (ADR-0011) | dolu ise `WARN`; tam origin olmayan değer açılışı **durdurur** |
| `APP_TRUSTED_PROXIES` | yalnız BFF/ingress IP veya CIDR | boş ise `WARN`; birleşimi bir adres ailesinin tamamını kapsayan değer açılışı **durdurur** |
| `SPRINGDOC_ENABLED` | `false` | açık ise `WARN` |
| `SERVICE_VERSION` | tek tanım, dağıtılan `v*` / Maven `pom` sürümüyle aynı | bilinçli boş (`SERVICE_VERSION=`) ise `WARN`; tanımsızsa imajdaki Maven sürümü |

`app.deployment` bu tablonun kapısıdır ve `SERVICE_ENVIRONMENT`'ı okuyan
**üçüncü** bağlama noktasıdır; diğer ikisi ECS log alanı ve `iyzico.deployment`.
Kontrol yalnız `production`'da çalışır, `local` ve `staging`'de hiç ateşlenmez —
tek istisna `CORS_ALLOWED_ORIGINS`'in biçim kuralıdır, o her ortamda koşar.

Biçim kuralı **çalışan hiçbir değeri düşürmez**: sonda `/` taşıyan bir origin
reddedilmez, filtrenin kendi yaptığı kırpmayla normalize edilir
(`https://iskoala.com/` → `https://iskoala.com`). Durduran şekiller — tam `*`,
alt alan joker'i, şemasız/yollu/sorgulu/fragment'li değerler — bugün zaten
hiçbir tarayıcıya izin vermiyor olurdu; biri `*` yüzünden her preflight'ta 500
üretir, kalanı sessizce hiçbir şeyle eşleşmez.

Açılışta bir `INFO` satırı dört değerin **etkin** hâlini yazar
(`PRODUCTION_CONFIG_DRIFT baseline: …`), her sapma ayrıca bir `WARN` satırı
üretir. Aynı değişkenin Dokploy'da iki kez tanımlanması süreç içinden
görülemez — ortam değişkeninde son tanım kazanır ve uygulamaya tek değer
ulaşır; `baseline` satırındaki `version=` alanı hangi tanımın kazandığını
söyler, Dokploy'daki satırlarla karşılaştırılır.

Açılış düşerse bile `baseline` satırı **yazılmış olur**: durduran kural en sonda
koşar, yani etkin değerler her hâlükârda logda görünür.

Kuralları `ProductionConfigurationGuardTest` iki yönde de sabitliyor; hangisinin
durdurup hangisinin uyardığı ve gerekçesi `CLAUDE.md` §2ax'te. Bu tablonun
guard'ın gerçek kural listesinden ayrışmadığını `ProductionConfigurationContractTest`
doğruluyor — kuralı olmayan satır da, satırı olmayan kural da testi kırar.

Mail teslimatı bu tabloya **eklenmedi**: CORS kataloğunu bozmamak için ayrı
`MailDeliveryEnvironmentGuard` (`MAIL_DELIVERY_GUARD`) okur. Staging ve
production `UNRESTRICTED` ile başlar ve `WARN` yazar; boş ALLOWLIST local dışında
`WARN` yazar. Alıcı adresi loga girmez. Gerekçe `CLAUDE.md` §2ay.

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
deploy öncesi yedek, sonrası dar health/readiness smoke uygulanır. İlk lansman
geçişi günlük deploy değildir; hedef, geri dönüş ve sağlayıcı kapıları
[`launch-cutover.md`](runbooks/launch-cutover.md) yordamıyla ayrıca yürütülür.
