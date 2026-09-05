# Koala test ortamı stratejisi

> Karar güncellemesi (5 Eylül 2026): Bu rapordaki ayrı staging/Mailpit/seçici seed temizliği önerileri Q25, Q27, Q33-B, Q34-A, Q35-A ve Q36-C cevaplarıyla değiştirilmiştir. Mevcut API lansmana kadar test ortamıdır; gerçek SMTP iki kontrollü alıcıyla kullanılır; lansmanda mevcut DB sıfırlanır. Güncel karar [ADR-0001](../adr/0001-use-current-stack-until-launch.md), uygulama sentezi [gerçek API test akışı](../plans/real-api-test-flow.md), operasyon adımları ise [İstanbul QA marketplace veri paketi](../plans/qa-marketplace-seed.md) belgesidir. Aşağıdaki metin araştırma tarihindeki alternatif ve gerekçeleri korur.

Tarih: 5 Eylül 2026
Kapsam: Flutter mobil uygulama, Spring Boot API, PostgreSQL, RabbitMQ/e-posta, Google Maps Platform ve iyzico

## Karar özeti

**Günlük geliştirme ve manuel kullanıcı akışı testi production API üzerinde yapılmamalı.** Koala için doğru hedef, production ile aynı uygulama kodunu çalıştıran fakat kendi veritabanı, kuyrukları, depolaması ve sağlayıcı kimlikleri bulunan bir **staging** ortamıdır. Production yalnız deploy sonrası dar kapsamlı smoke test için kullanılmalıdır.

Buradaki kritik ayrım şudur:

- **Gerçek backend/API**, mobilin sabit liste veya in-memory fake yerine gerçek Spring Boot uçlarına istek atması ve sonucun gerçek PostgreSQL'e yazılmasıdır.
- **Production ortamı**, gerçek kullanıcıların, gerçek e-postaların ve gerçek paranın bulunduğu canlı işletim alanıdır.
- Staging; gerçek backend, gerçek API sözleşmesi, gerçek PostgreSQL, gerçek RabbitMQ, gerçek Google servisleri ve iyzico'nun gerçek sandbox ortamını birlikte kullanabilir. Bu nedenle staging verisi sentetik olsa bile uygulama akışı “dummy API” değildir.

Bu ayrım sektör pratiğiyle uyumludur: OWASP, UAT ortamını production yapılandırmasına en yakın ortam olarak tarif ederken gerçek veri yerine test verisi kullanılmasını özellikle ayırır. [OWASP Web Security Testing Guide — integration ve UAT ortamları](https://owasp.org/www-project-web-security-testing-guide/v42/2-Introduction/#functional-testers-security-tests) iyzico da Sandbox'ın Live ortamın davranışını gerçek fonları etkilemeden taklit ettiğini, iki ortamın farklı URL ve kimlik bilgileri kullandığını açıkça belirtir. [iyzico Live vs Sandbox](https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox)

Koala için önerilen nihai yapı:

| Alan | Local | Staging | Production |
| --- | --- | --- | --- |
| Mobil yapı | `dev` flavor | `staging` flavor | `production` flavor |
| API | Yerel gerçek Spring Boot | `https://api.staging.iskoala.com` | `https://api.iskoala.com` |
| Veritabanı | Kalıcı yerel PostgreSQL | Ayrı staging PostgreSQL | Ayrı production PostgreSQL |
| Veri | Yeniden üretilebilir sentetik veri | 100+ gerçekçi sentetik kayıt | Gerçek kullanıcı verisi; toplu seed yok |
| E-posta | Mailpit'te yakalanır | Varsayılan yakalama; kontrollü adreslere seçmeli gerçek teslim | Canlı SMTP teslimatı |
| Google Maps | Development anahtarları | Staging anahtarları | Production anahtarları |
| iyzico | Sandbox | Sandbox, uçtan uca ödeme testleri | Live; yalnız kontrollü smoke |
| Depolama | Yerel disk | Ayrı test bucket/prefix | Ayrı production bucket |
| Sıfırlama | İstenildiğinde | Kontrollü ve tekrarlanabilir | Otomatik/toplu sıfırlama yasak |

## Mevcut çalışma ağacının fotoğrafı

Bu bölüm 5 Eylül 2026 tarihindeki yerel çalışma ağacını tarif eder. Bazı dosyalar henüz commit edilmemiş olduğundan, bu raporla birlikte ilgili değişikliklerin ayrıca gözden geçirilmesi gerekir.

1. Mobil API adresi compile-time `API_BASE_URL` ile değiştirilebiliyor, ancak değer verilmezse doğrudan `https://api.iskoala.com` kullanılıyor. Bu, debug çalıştırmada yanlışlıkla production'a yazma riskidir. [Mobil `ApiConfig`](../../../koala-mobile/lib/product/network/api_config.dart)
2. Backend tek `application.yaml` içinde dış ortam değişkenleri kullanıyor; `JPA_DDL_AUTO` varsayılanı `validate`, demo seed varsayılanı kapalı ve iyzico base URL varsayılanı sandbox. [Backend `application.yaml`](../../src/main/resources/application.yaml) Spring Boot aynı kodun farklı ortamlarda çalışması için YAML, environment variable ve komut satırı gibi dış kaynakları destekler. [Spring Boot externalized configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
3. `QaMarketplaceSeeder`, açıkça seçilen `istanbul-v1` sürümünde doğrudan PostgreSQL'e deterministik UUID'lerle 120 iş arayan, 30 işveren, 30 şirket ve 180 ilan yazıyor; başvuru, atama, konuşma, mesaj, değerlendirme, bildirim ve ödeme görünüm durumlarını da ilişkilendiriyor. [Backend `QaMarketplaceSeeder`](../../src/main/java/com/exskylab/koala/core/qa/QaMarketplaceSeeder.java) Entegrasyon testi seed'in tek seferlik olduğunu, manuel ilerlemeyi koruduğunu, iki rol hesabının public login API üzerinden giriş yaptığını ve temel durum kümelerinin DB'de bulunduğunu doğruluyor. [Backend `QaMarketplaceSeedIT`](../../src/test/java/com/exskylab/koala/dataAccess/QaMarketplaceSeedIT.java)
4. Finansal görünüm satırları Iyzico çağrısından değil doğrudan SQL ile üretilir; provider token/işlem kimlikleri ve submerchant key alanları bilerek boş tutulur. Bu satırlar ekran durumlarını test eder, gerçek ödeme entegrasyonunun geçtiğini kanıtlamaz. Kontrollü iki QA hesabı sentetik finansal atamalara bağlanmaz. [Backend `QaMarketplaceSeeder.seedPayment`](../../src/main/java/com/exskylab/koala/core/qa/QaMarketplaceSeeder.java)
5. Mobil runtime'da otomatik demo giriş veya kaynak koda gömülü QA parolası bulunmaz. Kontrollü hesap bilgileri kullanıcı tarafından normal giriş ekranına yazılır; test double'ları yalnız test katmanındadır.
6. Harita anahtarı Android'de `local.properties`, iOS'ta `Secrets.xcconfig` üzerinden alınıyor; bu dosyaların repo dışında tutulması doğru yönde bir başlangıçtır. [Android build ayarı](../../../koala-mobile/android/app/build.gradle.kts) [iOS AppDelegate](../../../koala-mobile/ios/Runner/AppDelegate.swift) Buna karşılık her iki platformun uygulama kimliği hâlâ `com.example.koala`; Google'ın bundle/package kısıtları ve mağaza dağıtımı kurulmadan önce nihai kimliklere geçilmelidir. [Android application ID](../../../koala-mobile/android/app/build.gradle.kts) [iOS bundle ID](../../../koala-mobile/ios/Runner.xcodeproj/project.pbxproj)
7. Backend workflow'u her push/PR'da test çalıştırıyor, ancak Dokploy deploy'u yalnız `v*` tag'inde tetikliyor. Staging deploy'u için bugün ayrı job veya environment yok. [Backend GitHub Actions workflow](../../.github/workflows/ci.yml)

## 1. Ortam modeli

### 1.1 Local

Local ortam hızlı geliştirme ve hata ayıklama içindir, fakat yine gerçek Koala backend'ini kullanır:

- Spring Boot, PostgreSQL ve RabbitMQ mevcut Docker Compose ile çalışır.
- iOS Simulator `http://localhost:8080`, Android Emulator `http://10.0.2.2:8080` kullanır. Android Emulator dokümanında `10.0.2.2`, geliştirme makinesinin loopback adresine özel alias olarak tanımlanır. [Android Emulator ağ adresleri](https://developer.android.com/studio/run/emulator-networking-address)
- Veritabanı volume ile kalıcı olabilir; gerektiğinde yalnız local hedef doğrulandıktan sonra yeniden kurulabilir.
- Sentetik marketplace seed'i açık olabilir.
- E-posta, gerçek RabbitMQ consumer ve gerçek template rendering üzerinden Mailpit'e teslim edilir; internete çıkmaz.
- Google Maps development anahtarları gerçek SDK istekleri yapar.
- iyzico sandbox isteğe bağlıdır; ödeme dışındaki geliştirmede settlement worker kapatılabilir.

### 1.2 Staging

Staging, iki emülatörle yapılacak ana manuel test ortamıdır:

- Production ile aynı container image çalışır; yalnız config/secrets farklıdır.
- Kendine ait PostgreSQL, RabbitMQ vhost veya instance, depolama bucket/prefix'i, mail yakalama alanı ve sağlayıcı anahtarları bulunur.
- API ve callback adresleri geçerli TLS ile `api.staging.iskoala.com` altında çalışır. iyzico Checkout Form callback URL'si geçerli SSL sertifikası ister. [iyzico Checkout Form Initialize](https://docs.iyzico.com/en/payment-methods/checkoutform/cf-implementation/cf-initialize)
- Marketplace seed'i staging DB'ye yazılır ve tüm mobil ekranlar bu gerçek API'den okur.
- Google Maps gerçek development/staging projesine, ödeme ise iyzico Sandbox'a gider.
- E-posta varsayılan olarak yakalanır; yalnız QA ekibinin kontrolündeki adreslere seçmeli olarak gerçek SMTP relay yapılır.
- Staging app adı ve ikonu production'dan gözle ayırt edilir.

### 1.3 Production

Production yalnız gerçek müşteriler içindir:

- `DEMO_SEED_ENABLED=false` zorunludur ve uygulama açılış kontrolü bunun tersinde startup'ı reddetmelidir.
- Production DB, queue, storage ve credentials staging ile hiçbir şeyi paylaşmamalıdır.
- Google production anahtarları ve iyzico Live kimlikleri kullanılır.
- Toplu sentetik kullanıcı/şirket/ilan yüklenmez.
- Deploy sonrası smoke; health/readiness, iki kontrollü hesabın login'i, referans verilerin okunması ve izin verilen sınırlı akışlarla sınırlandırılır.
- Gerçek ödeme smoke'u otomatik veya her deploy'da yapılmaz; gerektiğinde operasyon onaylı, izlenebilir ve iade adımı tanımlı ayrı bir runbook ile yürütülür.

## 2. Backend yapılandırması ve fail-closed korumalar

Spring Boot profile-specific `application-{profile}.yaml` dosyalarını destekler ve profil dosyasındaki değerler ortak dosyanın üstüne yazılır. [Spring Boot profile-specific files](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.profile-specific) Profiller `spring.profiles.active` ile etkinleştirilebilir. [Spring Boot profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html)

Koala için öneri:

```text
src/main/resources/
  application.yaml              # ortak, sırsız varsayılanlar
  application-local.yaml        # güvenli local varsayılanları
  application-staging.yaml      # güvenli staging kuralları
  application-production.yaml   # fail-closed production kuralları
```

Secret değerleri bu dosyalara yazılmamalı; Dokploy/GitHub environment secrets veya platform secret mount'undan gelmelidir. Spring Boot environment variable ve configuration tree kaynaklarını doğrudan destekler. [Spring Boot environment variables ve configuration trees](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables) [Spring Boot configuration trees](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.configtree)

Her ortam açılışta tek bir `EnvironmentSafetyValidator` benzeri kontrolle doğrulanmalıdır:

| Kontrol | Local | Staging | Production |
| --- | --- | --- | --- |
| `SERVICE_ENVIRONMENT` | `local` | `staging` | `production` |
| DB host/name | local allowlist | staging allowlist | production allowlist |
| Demo/synthetic seed | açık olabilir | açık olabilir | **mutlaka kapalı** |
| iyzico URL/credential türü | sandbox | **sandbox** | **live** |
| Mail modu | capture | capture/allowlist | live |
| Storage | local/test | staging bucket | production bucket |
| Springdoc | açık olabilir | yalnız kontrollü erişim | kapalı |
| Callback/API URL | local/tunnel | staging domain | production domain |

Validator yalnız uyarı loglamamalı; ödeme ortamı, seed veya DB hedefi uyuşmuyorsa uygulama başlamamalıdır. Böylece yanlış bir Dokploy environment değişkeni veri yazdıktan sonra değil, bağlantı kurulmadan önce fark edilir.

CI/CD tarafında `staging` ve `production` ayrı GitHub deployment environment olarak tanımlanmalıdır. GitHub environments; deployment branch sınırı, onay kuralı ve environment'a özel secret erişimi sağlayabilir. [GitHub deployment environments](https://docs.github.com/en/actions/concepts/workflows-and-actions/deployment-environments) Private repository'de kullanılabilen koruma özellikleri GitHub planına göre değişebildiği için organizasyon planı ayrıca doğrulanmalıdır. [GitHub environment yönetimi ve private repo notları](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments)

Önerilen deploy hattı:

```text
PR -> test/build
main -> immutable image + staging deploy -> staging smoke
vX.Y.Z tag + insan onayı -> production deploy -> production smoke
```

## 3. Mobil flavor stratejisi

Flutter, Android product flavor'larını ve iOS Xcode scheme/configuration'larını farklı API key, app name, icon ve logging ayarları için resmi olarak destekler. [Flutter Android flavors](https://docs.flutter.dev/deployment/flavors) [Flutter iOS flavors](https://docs.flutter.dev/deployment/flavors-ios)

Koala'da üç flavor önerilir:

| Flavor | Önerilen görünen ad | Önerilen application/bundle ID | API |
| --- | --- | --- | --- |
| `dev` | `Koala Dev` | `com.iskoala.koala.dev` | local, açıkça verilen URL |
| `staging` | `Koala Staging` | `com.iskoala.koala.staging` | `https://api.staging.iskoala.com` |
| `production` | `Koala` | `com.iskoala.koala` | `https://api.iskoala.com` |

Bunlar önerilen yeni kimliklerdir; Apple/Google hesaplarında kayıt açılmadan önce ekip tarafından kesinleştirilmelidir. Flutter'ın iOS flavor rehberi farklı scheme'lere farklı bundle ID vererek staging ve production uygulamalarını birbirini etkilemeden test etmeyi destekler. [Flutter iOS distinct bundle identifiers](https://docs.flutter.dev/deployment/flavors-ios#add-distinct-bundle-identifiers)

Kurallar:

1. `flutter run` yanlışlıkla production'a gitmemelidir. Geliştirici varsayılanı `dev` veya `staging` olmalı; production build açıkça `--flavor production` istemelidir.
2. API URL, ortam adı ve Maps key seçimi flavor tarafından belirlenmelidir; uygulama içinden kullanıcıya serbest sunucu değiştirme alanı verilmemelidir.
3. Dart compile-time declarations farklı backend seçmek için uygundur; `String.fromEnvironment` değeri derleme yapılandırmasından okur ve değer yoksa default'a düşer. [Dart `String.fromEnvironment`](https://api.dart.dev/dart-core/String/String.fromEnvironment.html) [Dart compilation environment declarations](https://dart.dev/libraries/core/environment-declarations)
4. Buna rağmen URL için sessiz production default'u kaldırılmalı; bilinmeyen/missing ortam build veya startup hatası vermelidir.
5. Backend health/info cevabı `environment=staging|production` taşımalı; mobil derlenen ortamla cevap uyuşmazsa login/yazma akışından önce açık hata göstermelidir.
6. `DemoBootstrap` ve sabit demo parolası runtime `lib/` ağacından çıkarılmalıdır. Staging hesap bilgileri mağaza binary'sine gömülmemeli, insan tarafından girilmeli veya yalnız test runner'a secret olarak sağlanmalıdır.

## 4. Gerçekçi sentetik veri stratejisi

NIST, test ortamında kullanılan kişisel verinin production ile aynı düzeyde korunması gerektiğini; sentetik/anonymized veri kullanımının test faydasını korurken PII riskini azaltabildiğini belirtir. [NIST SP 800-122 — test ortamındaki PII ve sentetik veri](https://tsapps.nist.gov/publication/get_pdf.cfm?pub_id=904990) Koala için production kopyası almak yerine sıfırdan üretilmiş, İstanbul'a uygun sentetik kayıtlar kullanılmalıdır.

### 4.1 Üç veri katmanı

1. **Referans veri:** ülke, il, ilçe, üniversite, kategori ve yasal sözleşme gibi ortamlar arası tutarlı kataloglar Liquibase ile gelir.
2. **Marketplace dataset:** profil, şirket, ilan, başvuru, atama, sohbet ve değerlendirmeler deterministik seed işiyle gerçek tablolara yazılır.
3. **Kritik yol verisi:** kayıt, e-posta doğrulama, ilan oluşturma, başvuru, kabul ve ödeme gibi akışlar seed SQL'iyle değil gerçek API ve sandbox sağlayıcı üzerinden yeniden oluşturulur.

Bu modelde mobil her zaman gerçek API'den okur. Seed yalnız DB'nin başlangıç durumunu üretir; mobilde fake repository veya test API bulunmaz.

### 4.2 Mevcut seed'in korunacak tarafları

Mevcut dataset talep edilen ölçeği aşıyor: 150 kullanıcı, 30 şirket, 180 ilan ve bağlı marketplace hareketleri üretir. [Backend `QaMarketplaceSeeder`](../../src/main/java/com/exskylab/koala/core/qa/QaMarketplaceSeeder.java) Deterministik kimlikler ve sürüm sahiplenme kaydı tekrar çalıştırmayı güvenli kılar; entegrasyon testi ikinci çağrının manuel ilerlemeyi değiştirmediğini doğrular. [Backend `QaMarketplaceSeedIT`](../../src/test/java/com/exskylab/koala/dataAccess/QaMarketplaceSeedIT.java)

Korunması gereken senaryolar:

- Açık, kapalı, devam eden, iptal edilmiş ve tamamlanmış ilanlar.
- Başvuru, kabul ve ret durumları.
- Tüm atama durumları.
- Sohbet, okunmuş/okunmamış mesaj, değerlendirme ve bildirimler.
- Şirket üyeliği, kaydedilen ilan ve takip ilişkileri.
- İstanbul ilçelerine dağılmış koordinatlar.

### 4.3 Seed'in staging öncesi sertleştirilmesi

1. Seed `QaMarketplaceSeeder` ve yalnız `istanbul-v1` seçiliyken oluşan `QaMarketplaceSeedRunner` olarak ayrıştırıldı; normal uygulama açılışında çalışmaz.
2. Mevcut altyapının `SERVICE_ENVIRONMENT=production` adı lansmana kadar pre-production alanını tarif ettiği için koruma bu ada bağlanmadı. Exact seed sürümü seçimi, tek seferlik DB kaydı ve lansman reseti kullanılır.
3. Dataset sürümü `qa_marketplace_seed_versions` tablosunda transaction içinde atomik olarak sahiplenilir. Aynı sürüm tekrarlandığında hiçbir seed kaydı güncellenmez.
4. Bulk sentetik kullanıcıların e-postaları teslim edilemeyen `qa.iskoala.invalid` namespace'ini kullanır. `.invalid` testte kesin geçersiz adlar, `example.com` ise örnek/dokümantasyon için ayrılmıştır. [RFC 2606 reserved domains](https://www.rfc-editor.org/info/rfc2606/)
5. Yalnız iki manuel QA hesabı gerçekten kontrol edilen mailbox'lara bağlanır; diğer 148 hesabın internete e-posta üretmesi hem geçersiz domain hem SMTP allowlist ile engellenir.
6. Medya URL'leri ortamın `API_URL` origin'inden üretilir ve public `/qa-assets/**` uçlarından sunulur. [Backend `QaMarketplaceSeeder`](../../src/main/java/com/exskylab/koala/core/qa/QaMarketplaceSeeder.java)
7. Finansal görünüm kayıtlarında provider token, submerchant key ve payment/transaction ID alanları boş bırakılır. Kontrollü QA hesapları bu atamalara bağlanmaz; satırlar Iyzico reconciliation/settlement kanıtı sayılmaz.
8. “Ödeme başarılı” kabul testi yalnız gerçek iyzico Sandbox Initialize → hosted form → callback → Retrieve zincirinden gelen yeni bir kayıt üzerinde geçmelidir.
9. Sabit TC kimlik ve IBAN değerleri yalnız sandbox/staging'de kalmalı; production'a ulaşmaları startup veya DB constraint/guard ile engellenmelidir.

### 4.4 İki manuel staging hesabı

Önerilen roller:

- `qa-worker@iskoala.com` — iş arayan.
- `qa-employer@iskoala.com` — şirket veya bireysel işveren senaryolarını yöneten hesap.

Adreslerin gerçekten ekip kontrolünde olup olmadığı kurulurken doğrulanmalıdır. Her hesap farklı güçlü parola kullanmalı; parola repo, issue, PR, uygulama binary'si ve bu dokümana yazılmamalıdır. İnsan testi için ekip parola yöneticisinde, otomasyon için staging GitHub Environment secret'larında tutulmalıdır. GitHub environment secret'ları ilgili job environment'a erişene kadar job'a verilmez. [GitHub Actions secrets](https://docs.github.com/en/actions/concepts/security/secrets)

Uygulanan modelde bilinen demo adresi veya kaynak-kod parolası korunmadı. İki kontrollü adres ve farklı güçlü parolaları yalnız Dokploy secret ayarlarından alınır; mobil runtime normal giriş ekranını kullanır.

## 5. E-posta teslimatı

“Gerçek e-posta akışı” üç ayrı şeyi içerir:

1. Backend event'i ve RabbitMQ mesajı gerçekten üretiyor mu?
2. Consumer gerçek template'i render edip SMTP'ye teslim ediyor mu?
3. Mesaj internet üzerinden hedef inbox'a ulaşıyor mu?

İlk ikisi her local/staging testte gerçek çalışabilir; üçüncü adım kontrollü bir deliverability smoke olmalıdır.

### Local

Docker Compose'a Mailpit eklenmeli; backend `MAIL_HOST=mailpit`, `MAIL_PORT=1025` kullanmalıdır. Mailpit varsayılan olarak SMTP'yi 1025, web UI'ı 8025 portunda açar ve yakalanan mesajları API/UI üzerinden inceleyebilir. [Mailpit configuration](https://mailpit.axllent.org/docs/configuration/) Mailpit API, otomatik integration testte belirli alıcı/subject/link doğrulaması yapmak için kullanılabilir. [Mailpit integration testing](https://mailpit.axllent.org/docs/integration/)

### Staging

- Tüm mesajlar önce staging Mailpit'te yakalanmalıdır.
- UI/API yalnız ekip erişimine açık olmalı; Mailpit UI basic auth ve HTTPS/proxy desteği sağlar. [Mailpit UI security configuration](https://mailpit.axllent.org/docs/configuration/http/)
- Seçili QA adreslerine manuel veya pattern bazlı relay yapılabilir. Mailpit relay yapılandırması allowed/blocked recipient regex'leri ve seçmeli relay desteği sunar. [Mailpit SMTP relaying](https://mailpit.axllent.org/docs/configuration/smtp-relay/)
- Bulk seed adreslerine hiçbir koşulda otomatik relay yapılmamalıdır.
- Kayıt doğrulama ve parola sıfırlama E2E testi, kontrollü iki mailbox'a gerçek teslimle ayrıca çalıştırılmalıdır.

### Production

- Yalnız production SMTP credential ve production From/Reply-To kimliği kullanılmalıdır.
- Staging ile aynı app password/SMTP kullanıcısı paylaşılmamalıdır.
- Gmail SMTP kısa vadede kullanılacaksa app password yalnız 2-Step Verification açık hesaplarda çalışır; Google app password kullanımını genel olarak önermediğini ve hesap parolası değişince app password'leri iptal ettiğini belirtir. [Google App Passwords](https://support.google.com/accounts/answer/185833)
- Uzun vadede bounce/complaint görünürlüğü ve ortam izolasyonu sağlayan transaction e-mail servisi değerlendirilmelidir; Spring'in SMTP yüzeyi korunabilir.

## 6. Google Maps ve adres servisleri

Google, API key'lerin uygulama ve API kısıtlarıyla sınırlandırılmasını, her uygulama için ayrı key kullanılmasını ve client-side ile server-side trafiğin ayrılmasını önerir. [Google Maps Platform security guidance](https://developers.google.com/maps/api-security-best-practices)

Koala için her ortamda üç ayrı anahtar önerilir:

1. iOS Maps SDK key.
2. Android Maps SDK key.
3. Backend Places/Geocoding web-service key.

Üç ortam için toplam dokuz bağımsız key, kullanım ve maliyetin hangi ortam/platformdan geldiğini açıkça ayırır. Bu sayı Google'ın zorunlu kuralı değil, “her app için ayrı key” ve client/server ayrımı önerisinin Koala ortamlarına uygulanmış halidir.

Kısıtlar:

- iOS key yalnız ilgili flavor bundle ID'sine ve Maps SDK for iOS'a açık olmalıdır. Google iOS restriction için yetkili bundle ID eklenmesini tarif eder. [Google Maps key iOS restriction](https://developers.google.com/maps/api-security-best-practices#set-an-application-restriction-for-an-api-key)
- Android key yalnız ilgili application ID + imzalama sertifikası parmak izine ve Maps SDK for Android'a açık olmalıdır. [Google Maps Android restrictions](https://developers.google.com/maps/api-security-best-practices#android-apps)
- Backend key mobile binary'ye konmamalı; yalnız gerekli Places/Geocoding API'lerine ve mümkünse sabit egress IP'lerine kısıtlanmalıdır. Google server-side web-service key'lerinin istemciye açılmamasını ve IP/API restrictions kullanılmasını önerir. [Google Maps web-service key protection](https://developers.google.com/maps/api-security-best-practices#protect-web-service-api-keys)
- Development, staging ve production Google Cloud projeleri veya en azından key/kota/bütçe ayrımları ayrı olmalıdır.
- Kullanılmayan API'ler kapatılmalı ve key bazında metrik/uyarı izlenmelidir. [Google Maps key usage monitoring](https://developers.google.com/maps/api-security-best-practices#check-your-api-key-usage)

Mevcut beyaz harita problemi gerçek ve kısıtlı bir iOS Maps key sağlanmadan çözülemez; daha ayrıntılı akış ve adres mimarisi için [mobil konum araştırması](../../../koala-mobile/docs/research/location-and-map.md) kullanılmalıdır. Önce `com.example.koala` kimliklerinden nihai flavor kimliklerine geçmek, sonra key restrictions üretmek gerekir; aksi halde key'ler ikinci kez taşınır.

## 7. iyzico Sandbox ve ödeme testi

iyzico Live ve Sandbox mimari olarak aynı akışı sunar, fakat farklı base URL ve tamamen farklı credentials kullanır; Sandbox gerçek fonları etkilemeden işlem senaryolarını taklit eder. [iyzico Live vs Sandbox](https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox) iyzico'nun test kartları yalnız Sandbox'ta kullanılabilir ve başarılı, yabancı kart ve belirli hata senaryoları sağlar. [iyzico test cards](https://docs.iyzico.com/en/add-ons/test-cards)

Bu nedenle kesin politika:

- Local ve staging: `https://sandbox-api.iyzipay.com` + yalnız sandbox credentials.
- Production: `https://api.iyzipay.com` + yalnız live credentials.
- Ortam/credential çifti uyuşmuyorsa backend başlamaz.
- Mobil hiçbir iyzico secret taşımaz; Checkout Form session'ı backend açar.
- Staging callback URL'si staging API'ye, production callback'i production API'ye gider.

Checkout Form iki adımdır: Initialize bir hosted `paymentPageUrl` ve token üretir; Retrieve token ile finansal sonucu getirir. [iyzico Checkout Form API](https://docs.iyzico.com/en/getting-started/preliminaries/api-reference-beta/payment-methods/checkoutform) Doğrudan `paymentPageUrl` yönlendirmesi iyzico'nun belgelenmiş kullanım şekillerinden biridir. [iyzico Checkout Form implementation](https://docs.iyzico.com/en/payment-methods/checkoutform/cf-implementation)

Staging ödeme kabul kapısı:

1. Response signature, idempotency ve nesne yetkisi gibi mevcut P0 bulguları kapanır. Ayrıntılar: [iyzico mobil checkout araştırması](./iyzico-mobile-checkout.md).
2. İki manuel staging hesabı gerçek API üzerinden atama üretir.
3. İşveren mobil WebView içinden sandbox test kartıyla Checkout Form'u tamamlar.
4. Callback ve Retrieve sonucu gerçek staging DB'de yeni payment/attempt kaydı oluşturur.
5. Success, provider decline, 3DS, tekrar callback, ağ belirsizliği, refund, partial refund ve payout/settlement senaryoları kaydedilir.
6. Aynı idempotency key ile tekrar çağrının ikinci tahsilat/session üretmediği doğrulanır.
7. Ancak bu kapı yeşil olduktan sonra Live credentials production'a verilir.

Seed edilmiş `qa-state-fixture-*` konuşma kimlikli satırlar UI state testi olarak kalabilir; provider alanları boş olduğu için reconciliation/settlement kanıtı sayılmaz ve finansal kabul testleri bunları dışlamalıdır. [Backend QA payment seed'i](../../src/main/java/com/exskylab/koala/core/qa/QaMarketplaceSeeder.java)

## 8. PostgreSQL güvenliği ve veri yaşam döngüsü

PostgreSQL, birbirinden ayrılması gereken kullanıcı/projeler için ayrı database ve ayarlanmış authorization önerir; tek bağlantı yalnız seçilen database'e erişir. [PostgreSQL database isolation](https://www.postgresql.org/docs/16/manage-ag-overview.html) Koala ortamları yalnız schema ile ayrılmamalı, tercihen ayrı PostgreSQL servis/cluster; en azından ayrı database, volume, credentials ve network policy kullanmalıdır.

Her ortam için roller:

- `koala_<env>_app`: uygulamanın minimum runtime DML izinleri.
- `koala_<env>_migrator`: yalnız deploy/migration sırasında Liquibase DDL yetkileri.
- `koala_<env>_readonly`: kontrollü teşhis/raporlama.
- Yönetici/superuser credential uygulamaya verilmez. PostgreSQL superuser'ın erişim kontrollerini aştığını ve dikkatli kullanılması gerektiğini belirtir. [PostgreSQL role attributes](https://www.postgresql.org/docs/17/role-attributes.html)

Veri kuralları:

1. Production DB dump'ı staging'e doğrudan restore edilmez.
2. Gerçek veriyle yeniden üretim zorunluysa ayrı onaylı süreçte geri döndürülemez anonimleştirme uygulanır; çıktı production verisi gibi korunur ve süresi sonunda silinir. NIST, test ortamındaki PII'nin production düzeyinde korunması gerektiğini belirtir. [NIST SP 800-122](https://tsapps.nist.gov/publication/get_pdf.cfm?pub_id=904990)
3. Staging yalnız sentetik olduğundan migration + seed ile kontrollü yeniden kurulabilir. Reset komutu, hedef environment ve DB adını iki kez doğrulamadan çalışmamalıdır.
4. Production'da `JPA_DDL_AUTO=validate` korunmalı; migration yalnız Liquibase/migrator rolüyle ilerlemelidir. Mevcut config bu doğrultudadır. [Backend JPA/Liquibase config](../../src/main/resources/application.yaml)
5. Production için düzenli backup ve gerçek restore tatbikatı tanımlanmalıdır. PostgreSQL `pg_dump` çalışan database'ten tutarlı logical backup üretebilir; yüksek güvenilirlikte WAL arşivi point-in-time recovery sağlar. [PostgreSQL `pg_dump`](https://www.postgresql.org/docs/17/app-pgdump.html) [PostgreSQL PITR](https://www.postgresql.org/docs/17/continuous-archiving.html)
6. Staging backup'ları production backup'larıyla aynı bucket/prefix veya credential'ı paylaşmamalıdır.

## 9. Production smoke hesapları

Production'da geniş sentetik dataset yerine en fazla iki kontrollü hesap önerilir:

- `smoke-worker@iskoala.com`
- `smoke-employer@iskoala.com`

Bu adresler ancak gerçekten ekip tarafından kontrol ediliyorsa kullanılmalıdır. Parolalar birbirinden farklı olmalı ve parola yöneticisi/production deployment secret'ında tutulmalıdır.

İlk aşamada production smoke yalnız şu işlemleri yapmalıdır:

- Readiness/health kontrolü.
- Login ve refresh token.
- `/users/me` ve referans veri okumaları.
- Yetkisiz erişimin reddedildiği negatif kontrol.
- WebSocket bağlantı/ayrılma kontrolü.

İlan, başvuru, sohbet, bildirim ve ödeme üretmek production müşterilerini etkileyebilir. Bu yazma smoke'ları ancak backend'de `internalTest` kapsamı ve tüm public search/alert/recommendation/e-mail yüzeylerinde server-side filtreleme tasarlandıktan sonra açılmalıdır. O zamana kadar production hesabıyla görünür ilan veya mesaj oluşturulmamalıdır.

Gerçek ödeme smoke'u gerekiyorsa her deploy'a bağlı otomasyon değil; onaylı insan tetiklemeli bir runbook olmalıdır. Runbook işlem kimliğini, beklenen tutarı, callback/reconciliation sonucunu, iade/settlement adımını ve kapanış kanıtını kaydetmelidir.

## 10. İki emülatörle önerilen ana E2E akışı

Ana manuel test production değil staging üzerinde şöyle yürür:

```text
Koala Staging backend + staging PostgreSQL + seed
          |
          +-- iPhone A: qa-employer hesabı
          |      şirket/bireysel profil -> gerçek ilan -> başvuru kabulü
          |
          +-- iPhone B: qa-worker hesabı
                 harita -> gerçek API ilanı -> başvuru -> sohbet
          |
          +-- iyzico Sandbox Checkout Form
                 ödeme -> callback -> Retrieve -> staging DB
          |
          +-- Mailpit / seçmeli SMTP relay
                 kayıt, reset ve aktivite e-postaları
```

Kabul ölçütleri:

- Mobil runtime'da fake/dummy repository seçeneği yoktur.
- Her oluşturulan kullanıcı, şirket, ilan, başvuru, atama, mesaj ve ödeme durumu staging PostgreSQL'de sorgulanabilir.
- Seed verileri ile gerçek API üzerinden sonradan oluşturulan akış verileri ayırt edilebilir.
- İki rol aynı anda ayrı simulator'da oturum açabilir.
- Google Maps key rejection logu olmadan gerçek harita karoları açılır.
- Adres autocomplete/resolve/pin akışı gerçek backend ve gerçek Google provider ile tamamlanır.
- Kayıt ve parola sıfırlama e-postaları önce capture'da, ayrıca kontrollü gerçek inbox'ta doğrulanır.
- iyzico ödeme kabul testi yalnız Sandbox'ın ürettiği gerçek token/transaction sonucu üzerinden yeşil olur.
- Production DB'ye veya live iyzico'ya staging build'den erişilemez.

## 11. Uygulama sırası ve commit sınırları

Her satır ayrı, geri alınabilir commit/issue sınırı olmalıdır:

1. **Credential güvenliği:** Daha önce sohbet veya başka yüzeylerde paylaşılan tüm production credential'ları rotate et; hiçbir değer rapora/repo'ya yazılmaz. GitHub, tespit edilen credential sızıntısında ilgili credential'ın derhal rotate edilmesini önerir. [GitHub secret scanning remediation](https://docs.github.com/en/code-security/concepts/secret-security/secret-scanning#secret-scanning-alerts-and-remediation) AWS açığa çıkmış access key için etkilenen key'in en kısa sürede silinmesini önerir. [AWS exposed access key guidance](https://docs.aws.amazon.com/awssupport/latest/user/security-checks.html#exposed-access-keys)
2. **Backend environment guardrails:** local/staging/production profilleri, safety validator ve config matrix testleri.
3. **Staging altyapısı:** ayrı Dokploy app, PostgreSQL, RabbitMQ, storage ve TLS domain.
4. **Mobil flavors:** dev/staging/production scheme ve product flavor'ları; nihai app IDs, farklı ad/ikon, prod default'un kaldırılması.
5. **Sentetik dataset sertleştirme:** one-shot seed, environment guard, seed manifest, ortam-doğru asset URL'leri, sentetik provider durumlarının izolasyonu.
6. **E-posta test katmanı:** local/staging Mailpit, API assertions, seçmeli QA relay, production SMTP izolasyonu.
7. **Google Maps anahtar matrisi:** platform/ortam bazlı key ve restrictions; iOS/Android smoke.
8. **iyzico staging kabulü:** P0 ödeme düzeltmeleri, app içi WebView, Sandbox E2E ve reconciliation testleri.
9. **Production smoke tasarımı:** iki minimal hesap, internal-test filtreleri ve yazma/ödeme runbook'u.

## Sonuç

**Şu anda “daha gerçekçi olsun” diye production API'ye geçmek doğru değil.** Production; veri kirliliği, gerçek kullanıcıya bildirim/e-posta, gerçek ödeme ve geri döndürülmesi zor state risklerini aynı anda açar. En doğru ara adım, staging kurulana kadar local gerçek backend + kalıcı PostgreSQL + mevcut sentetik dataset ile çalışmak; ardından mobil `staging` flavor'ını ayrı gerçek API/DB'ye bağlamaktır.

Staging hazır olduğunda Koala'nın manuel testi production kadar gerçekçi olur: mobil gerçek Spring Boot API'ye gider, bütün kayıtlar gerçek PostgreSQL'dedir, Google Maps gerçek servistir, iyzico gerçek Sandbox'tır ve e-posta zinciri gerçek RabbitMQ/SMTP üzerinden çalışır. Değişen yalnız riskli dış etkilerin production kullanıcıları ve gerçek fonlardan izole edilmesidir.
