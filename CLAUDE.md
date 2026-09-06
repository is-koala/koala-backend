# koala

İşgücü pazaryeri backend'i. Spring Boot 4.1 / Java 25 / PostgreSQL / Liquibase / RabbitMQ / Maven wrapper.

Bu dosya projeyi konu konu anlatır: mimari, alınmış kararlar ve gerekçeleri, tuzaklar. Bir konuya dokunmadan önce ilgili başlığı oku.


## 0. Nerede kaldık — oturum devri

**Son durum (30.08.2026):** 156 endpoint, 64 entity, 97 test dosyası, 44 Liquibase changelog, 536 mesaj anahtarı. `./mvnw verify` — 604 birim testi yeşil; entegrasyon testleri Docker gerektirir.

**Tamamlanan fazlar:** 0 (test zemini), 1 (environment + CI/CD), 2 (güvenlik blokerleri), 3 (Spring Boot 4), 3B (MapStruct), 4 (mesaj katmanı), 5 (kod kalitesi — K-21/K-23 hariç), 5B (UserPrincipal), 6 (loglama + zaman + hız sınırı), 7 (ödeme), 7B (payout/refund/bank account), 8 (API yüzeyi — kısmi), 8C (istemci düzeltmeleri), 9 (yasal — kısmi), 9C (soft delete), 10 (mesajlaşma + değerlendirme + engelleme), 10B (iptal + uyuşmazlık + şikayet + timesheet), 8D (istemci profil yüzeyi + kategori seed), 8B (kalan API yüzeyi), 12 (rozetler + iş tamamlama), 11A (otomatik tamamlama), 11B (ödeme dayanıklılığı), 15 (API yüzeyi tutarlılığı), 12B (rozet yönetimi), 13 (WebSocket), 16 (çalışma zamanı ayarları), 17 (depolama → S3), 11C (olgunlaşma), 18 (mimari denetim), 19 (hata sözleşmesi + doğrulama + güvenlik), 19B (hata zarfı zenginleştirme), 21 (medya galerisi), 22 (birleşik işlerim ucu), 23 (şirket takibi + işveren istatistikleri), 24 (API dokümantasyonu + e-posta şablonları), 25 (prod kurulumu).

**Sıradaki fazlar:** 7C (Iyzico kalanları), 20 (referans veri), 9B (yasal kalanlar), 11D (çok örneklilik). Ayrıntı §7'de.

**Çalışma kuralları:**
- Değişiklikler feature branch'te küçük Conventional Commit'lerle gönderilir ve PR açılır;
  doğrudan `main` dalına push edilmez ve alakasız değişiklikler commit'e alınmaz.
- Koda **yorum satırı yazma** (bkz. §1). Gerekçe buraya yazılır.
- Her değişiklikten sonra test yazılır; düzeltmeden **önce** kırmızıya döndüğü görülür.
- Ana kodda imza değiştirdiysen `rm -rf target/test-classes` — Maven bayat test sınıfını yeniden derlemez, `NoSuchMethodError` alırsın.

**Prod durumu (30.08.2026):** Dokploy üzerinde Postgres 17, RabbitMQ ve uygulama ayakta. `api.iskoala.com`. Son etiket `v0.4.9` — kimlik doğrulama, SMS/push tüketicileri, sürüm kapısı ve çip doğrulaması dahil. Ayrıntı §9'da.

**Kullanıcı tarafında bekleyen işler:**
1. **Springdoc varsayılanı kapalıdır.** `.env.example` yerel geliştirme için bilinçli olarak `SPRINGDOC_ENABLED=true` verir; production'da dokümantasyon gerekiyorsa aynı değer açıkça eklenir, verilmezse Swagger UI ve API docs açılmaz.
2. **Gerçek kartla çip dökümü** — kimlik kartının hangi CSCA'ya zincirlendiği, DG11 ve DG15'in varlığı yalnızca böyle öğrenilir (§2aq2).
3. **Iyzico sandbox anahtarları** — ödeme akışı uçtan uca hiç denenmedi (§2x, Faz 7C).
4. **Firebase servis hesabı** — `PushService`'in FCM uygulaması için (§2s).
5. **SMS sağlayıcı anahtarı** — `SMS_API_KEY` boşken telefon doğrulama kodu üretilir ama teslim edilmez.
6. **Dokploy'dan `QA_MARKETPLACE_SEED_VERSION`, `QA_WORKER_EMAIL`, `QA_WORKER_PASSWORD`, `QA_EMPLOYER_EMAIL`, `QA_EMPLOYER_PASSWORD` kaldırılmalı** — seed kaldırıldı (ADR-0008), bu değişkenler işlevsiz ve parola taşıyorlar.
7. **İlk admin ve iş kategorisi kataloğu** — taze bir veritabanında ilk yönetici `ADMIN_BOOTSTRAP_EMAIL` verilip uygulama yeniden başlatılarak doğar (§2f3); değişken ilk admin oluştuktan sonra kaldırılmalı. Kategori seed'i kaldırıldı (§2m). Prod ve taze veritabanları kategorisiz; kategori ilanın zorunlu alanı olduğu için katalog `POST /admin/job-categories` ile kurulmadan **hiç ilan açılamaz**. Mobil istemcinin ikon eşlemesi `code` üzerinden gittiği için eski kodların (`PHOTOGRAPHY`, `WAITER`, `PET_CARE`, `DESIGN`, `BARISTA`) yeniden verilmesi gerekebilir.
8. **R2'deki mevcut nesnelerin S3'e kopyalanması** (§2ab), yasal metinlerin avukattan gelmesi, ve prod'da WebSocket'in `wss://` üzerinden servis edildiğinin teyidi (§2ai).

**Açık kalan kararlar** (ayrıntı §7'de):
- **Rozet eşikleri** yer tutucu (5 / 25 tamamlanmış iş). Yapı hazır, sayı ürün kararı.
- **Otomatik tamamlama süresi** varsayılan 3 gün. Sayı ürün kararı.
- **Kaç örnek çalışacak?** Hız sınırı, otomatik tamamlama ve mutabakat zamanlayıcıları tek örnek varsayıyor.
- **Iyzico mutabakatı sandbox'ta uçtan uca doğrulanmadı** — prod öncesi teyit şart.
- **Presence** (Faz 13) ve **şirket seviyesinde sohbet** (Faz 14) ürün kararı bekliyor.
- Faz 9B'deki her şey hukuk tarafından gelecek karara bağlı.

---

## 0b. API sözleşmesi kuralları

Faz 15'te yüzey baştan geçirildi. Bunlar **kural**, `ApiConventionTest` bunları derleme zamanında değil test zamanında koruyor:

- **Yönetim işlemleri `/admin/**` altında, istisnasız.** `@PreAuthorize` kullanılmadığı, tüm yetki `SecurityConfig`'te path matcher olduğu için prefix tek satırlık koruma sağlıyor: `.requestMatchers("/admin/**").hasRole("ADMIN")`. Prefix olmasaydı her yeni admin ucunu tek tek korumayı hatırlamak gerekirdi ve unutulan uç sessizce her `USER`'a açık kalırdı. **Test, `hasRole("ADMIN")` taşıyan her kuralın yolunun `/admin` ile başladığını doğruluyor.**
  - **Bu kapının hangi token'ı kabul ettiği ayrı bir sorudur** ve cevabı tek yerde yazılıdır: [ADR-0010](docs/adr/0010-split-staff-identity-from-koala-authorization.md) §Geçiş. Marketplace `ROLE_ADMIN` ile Keycloak `koala-staff` kimliğinin hangi aşamada geçerli olduğunu, `ROLE_ADMIN` yolunun ömrünü ve kesim koşullarını orada oku — buraya kopyalama.
- **İç içelik daima üst kaynaktan aşağı.** `/job-assignments/{id}/reviews`, `/users/{id}/badges` — asla `/reviews/job-assignments/{id}` değil.
- **Durum değiştiren işlem kendi POST ucunu alır.** `/respond`, `/complete`, `/cancel`, `/confirm`. Tek bir `PATCH .../status` ucuna hepsini yığmak yerine, her geçişin `SecurityConfig`'te kendi satırı olsun diye.
- **Uç yolları sondaki eğik çizgi taşımaz** (§4'teki tuzak), **aynı fiil+yol iki handler'da olamaz.**
- **DTO adı HTTP fiilini ya da yolu taşımaz.** `JobsJobIdApplicationsPostResponseDto` değil `JobApplicationDto`. Uç taşındığında ad yalan olmasın diye. Ad 32 karakteri aşamaz.
- **DTO yalnızca `request`/`response` (ve `common`) altında durur.**

**Faz 15'te değişen yollar** — eski istemci bunlara göre güncellenmeli:

| Eski | Yeni |
|---|---|
| `GET /reports`, `POST /reports/{id}/review` | `/admin/reports`, `/admin/reports/{id}/review` |
| `GET /disputes/pending`, `POST /disputes/{id}/resolve` | `/admin/disputes`, `/admin/disputes/{id}/resolve` |
| `POST /payouts/job-assignments/{id}/release\|refund` | `POST /admin/job-assignments/{id}/release\|refund` |
| `GET /payouts/me` | `GET /users/me/payouts` |
| `POST /reviews/job-assignments/{id}` | `POST /job-assignments/{id}/reviews` |
| `GET /reviews/users/{id}[/summary]` | `GET /users/{id}/reviews`, `GET /users/{id}/rating-summary` |
| `GET /payments/job-assignments/{id}` | `GET /job-assignments/{id}/payments` |
| `POST /job-assignments/{id}/payment-session/initiate` | `POST /job-assignments/{id}/payment-session` |
| `PATCH /job-assignments/{id}/status` | `POST /job-assignments/{id}/respond` |
| `PATCH /job-applications/{id}` | `POST /job-applications/{id}/respond` |
| `POST /conversations/jobs/{jobId}` | `POST /jobs/{jobId}/conversation` |
| `POST /user-verifications/{id}` | `POST /verifications/{id}/confirm` |
| `PUT /users/me/identity-verification` | `POST /users/me/identity-verification` |
| `/company-contact-invitations/{id}` | `/contact-invitations/{id}` |
| `GET /notifications/track/{id}.png` | `GET /notification-tracking/{id}.png` |

**Yeniden adlandırılan 16 DTO** (JSON alan adları değişmedi, yalnızca sınıf adları): `JobAssignmentsAssignmentIdPaymentSessionInitiatePostResponseDto` → `PaymentSessionDto`, `...StatusPatchResponseDto` → `JobAssignmentDto`, `...StatusPatchRequestDto` → `AnswerAssignmentDto`, `JobApplicationsApplicationIdPatchResponseDto` → `JobApplicationDetailDto`, `...PatchRequestDto` → `AnswerApplicationDto`, `JobsJobIdApplicationsPostResponseDto` → `JobApplicationDto`, `...GetResponseDto` → `JobApplicantDto`, `...PostRequestDto` → `CreateJobApplicationDto`, `CompaniesCompanyIdJobsPostRequestDto` → `CreateCompanyJobDto`, `...PostResponseDto` → `CompanyJobDto`, `UsersMeJobsPostRequestDto` → `CreateIndividualJobDto`, `...PostResponseDto` → `IndividualJobDto`, `UsersMeIdentityVerificationRequestDto` → `VerifyIdentityDto`, `UserMeChangePasswordPutRequestDto` → `ChangePasswordDto`, `UserMePatchRequestDto` → `UpdateProfileDto`, `CompanyContactWithoutCompanyDto` → `CompanyContactDto`. `CreateUserDto`/`UpdateUserDto` `user/request` altına taşındı.

**Mimari kararlar backend'de.** Frontend önerileri dinlenir ama yüzeyin son hâline burası karar verir; istemci buna uyar.

---

## 1. Kod yapısı kuralları

Bunlar tercih değil, uyulması istenen kurallar:

- **Yorum satırı yok.** Javadoc dahil, istisnasız. Bir şeyin neden öyle olduğu açıklanacaksa buraya, CLAUDE.md'ye yazılır — koda değil.
- **Helper'lar `core/helpers` altında.** `business/helpers` diye bir yer yok.
- **DTO paketleri sadece `request` / `response`.** Enum'lar DTO katmanında değil, `entities` altında.
- **Controller'lar `webAPI` altında düz.** Özellik başına alt klasör yok.
- **`@PreAuthorize` kullanılmıyor.** Tüm yetkilendirme tek yerde: `core/configs/SecurityConfig`.
- **Bir manager yalnızca kendi aggregate'inin DAO'suna uzanır** (§2ae). Diğer aggregate'e erişim onun `Service`'i üzerinden; salt okumada döngü çıkıyorsa istisna listeye gerekçesiyle yazılır.
- **Bean döngüsü çözülür, gizlenmez.** `@Lazy`, alan enjeksiyonu ya da `spring.main.allow-circular-references` **kullanılmaz** — döngü çıkarsa bağımlılık yönü ya da sorumluluk değişir.
- **Girdi doğrulaması DTO'da** (§2ah). Manager'da null/blank kontrolü yapılmaz; çapraz alan kuralı `@AssertTrue` ile DTO'ya yazılır.
- **Id'ler `UUID`.** `String` alıp `UUID.fromString` ile çevirmek yok; `@PathVariable UUID` doğrudan alınır.
- **Exception yüzeyi yedi semantik tipten ibaret** (§2ag). Entity başına exception sınıfı açılmaz.
- **Locale açık verilir.** `toUpperCase()`/`toLowerCase()` locale'siz çağrılmaz.
- **Logger `@Slf4j` ile gelir**, elle `LoggerFactory` çağrılmaz.

Katmanlar: `webAPI` → `business/abstracts` (arayüz) + `business/concretes` (…Manager) → `dataAccess` (…Dao) → `entities`. Sabitler `core/constants`, mapper'lar `core/mappers`, konfigürasyon `core/configs`.

- **Mapper'lar elle yazılmaz.** `core/mappers` altındaki her şey MapStruct arayüzü; gövdeyi derleyici üretir. Dosyada `new XDto()` görüyorsan yanlış yerdesin.

## 2. Derleme ve çalıştırma

```bash
./mvnw verify
```

Birim testleri `*Test.java` (Surefire, Docker gerektirmez), entegrasyon testleri `*IT.java` (Failsafe, Testcontainers ile gerçek Postgres ayağa kaldırır). Docker açık değilse `*IT` testleri patlar, `mvn test` yine de çalışır.

**Annotation processor yolu açıkça verilmek zorunda.** JDK 23'ten beri javac, yalnızca classpath'te duran işlemcileri kendiliğinden çalıştırmıyor. `pom.xml` içindeki `maven-compiler-plugin` → `annotationProcessorPaths` bloğu bu yüzden var; bu blok silinirse Lombok hiçbir getter üretmez ve proje yüzlerce "cannot find symbol" hatasıyla derlenmez. MapStruct eklendiğinde aynı bloğa ikinci bir `path` girecek.

**JaCoCo 0.8.15 gerekiyor.** Daha eski sürümlerin ASM'i Java 25 bytecode'unu (class file major version 69) okuyamıyor ve her test "Unsupported class file major version 69" ile düşüyor.

**Testcontainers sürümü elle yönetiliyor.** Boot 4 Testcontainers'ı BOM'una almıyor; `testcontainers-bom` import'u `dependencyManagement` içinde duruyor, silinirse test bağımlılıkları sürümsüz kalır.

## 2b. Spring Boot 4 geçişinde neyin nereye taşındığı

Boot 4 auto-configuration'ı teknoloji başına ayrı modüllere böldü. Bir sınıf "kayboldu" gibi görünüyorsa büyük ihtimalle modülü eksik:

| Sınıf | Eski yeri | Yeni yeri |
|---|---|---|
| `RestTemplateBuilder` | `spring-boot-starter-web` içinde | `spring-boot-starter-restclient`, paket `org.springframework.boot.restclient` |
| `LiquibaseAutoConfiguration` | `spring-boot-autoconfigure` | `spring-boot-starter-liquibase` — düz `liquibase-core` yetmiyor, **şema oluşmadan Hibernate validate patlıyor** |
| `SimpleRabbitListenerContainerFactoryConfigurer` | `org.springframework.boot.autoconfigure.amqp` | `org.springframework.boot.amqp.autoconfigure` |
| `AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet` | `spring-boot-webmvc-test`, paket `org.springframework.boot.webmvc.test.autoconfigure` |

**Giden HTTP istemcisi elle sabitlendi.** Boot 4'te `RestTemplateBuilder` varsayılan olarak `JdkClientHttpRequestFactory` (yani `java.net.http.HttpClient`) kuruyor; Boot 3'te `SimpleClientHttpRequestFactory` idi. **Iyzico bu istemciye boş gövdeli `text/html` 200 dönüyor** — aynı URL, aynı başlıklar, aynı gövde `SimpleClientHttpRequestFactory` ile normal JSON dönerken. Sonuç: alt üye işyeri kaydı 502'ye düşüyor ve ödeme akışı hiç başlamıyordu.

Ham `java.net.http.HttpClient` ile aynı istek çalışıyor, yani sorun JDK istemcisinin kendisi değil, Spring'in fabrikasının ürettiği istekte; bayt seviyesinde farkı adlandıramadım. `AppConfig` bu yüzden fabrikayı açıkça `SimpleClientHttpRequestFactory` olarak veriyor (bağlantı 10 sn, okuma 30 sn zaman aşımıyla — varsayılanı sonsuz, askıda kalan sağlayıcı Tomcat iş parçacığını süresiz tutardı). `OutboundHttpClientTest` hem seçimi hem Boot'un varsayılanının hâlâ JDK istemcisi olduğunu sabitliyor; Boot varsayılanı değiştirirse test kırılır ve karar yeniden gözden geçirilir.

**Jackson 3'e geçildi.** `com.fasterxml.jackson.databind` → `tools.jackson.databind`. Anotasyonlar (`@JsonIgnore`, `@JsonFormat`) eski pakette kalıyor, yalnızca `databind` ve `core` taşındı. Jackson 3 exception'ları unchecked: `JsonProcessingException` → `tools.jackson.core.JacksonException`.

**Jackson 3'ün bir varsayılanı değişti.** `FAIL_ON_NULL_FOR_PRIMITIVES` artık açık geliyor; primitive bir alan JSON'da yoksa istek reddediliyordu. `application.yaml` içinde kapatıldı ki yükseltme mevcut istemci sözleşmesini bozmasın. Doğrusu bu alanları `Boolean` + `@NotNull` yapmak — Faz 5'te iki DTO için yapıldı, kalanlar (`isEmployer`, `experienceRequired`, `insuranceRequired`, `salary`, `minAge`, `maxAge`) bekliyor.

**spring-retry artık gerekmiyor.** Spring Framework 7 kendi retry'ını getiriyor; `RetryInterceptorBuilder.stateful()` API'si değişti — `maxAttempts` yerine `maxRetries`, `RetryTemplate` yerine `org.springframework.core.retry.RetryPolicy`.

## 2c. MapStruct

Mapper'lar `@Mapper(componentModel = "spring")` arayüzleri, gövdeleri derleme zamanında `…MapperImpl` olarak üretiliyor. Birim testinde üretilen sınıf doğrudan kurulabilsin diye `injectionStrategy = InjectionStrategy.CONSTRUCTOR` kullanılıyor — alan enjeksiyonu olsaydı testte reflection gerekirdi.

**`unmappedTargetPolicy=ERROR` açık.** DTO'ya yeni bir alan eklendiğinde eşlemesi yazılmazsa **derleme durur**. Sessizce null kalmasındansa derlemenin patlaması tercih edildi; bu ayarı `pom.xml` içindeki `compilerArgs`'tan kaldırma.

**R2 URL'leri `MediaUrlResolver` üzerinden.** `R2Properties`'e ihtiyaç duyan tek yer orası; mapper'lar `uses` ile ona bağlanıyor ve `@Named("imageToPublicUrl")` niteleyicisiyle çağırıyor.

**Bu geçiş iki NPE'yi yapısal olarak kapattı.** Elle yazılmış mapper'larda null denetimi rastgeleydi: `JobCategoryMapper` ve `CompanyMapper` null girdide patlıyordu. En ciddisi `JobAssignmentMapper` idi — `job.getCompany()` bireysel ilanlarda null olduğu için `PATCH /job-assignments/{id}/status` **her bireysel iş kabulünde çöküyordu**. MapStruct'ın ürettiği kod null'ı null'a eşliyor.

## 2d. Mesajlar ve hata sözleşmesi

Servis ve controller katmanı düz anahtar kullanır: `"{user.not.found}"`. Çözümleme tek yerde, `core/helpers/MessageResolver` içinde; **anahtar bulunamazsa anahtarın kendisi döner**, patlamaz.

İki giriş noktası var:
- **Başarılı yanıtlar** — `MessageResolvingAdvice` (bir `ResponseBodyAdvice`) gövdedeki `Result` nesnesinin `message` alanını yazmadan önce çözer. Bu yüzden controller'larda çözümleme kodu yok.
- **Hatalar** — `GlobalExceptionHandler` her yanıtı `messageResolver` üzerinden geçirir.

**Katalog testle korunuyor.** `MessageCatalogTest` kaynak kodu tarayıp kullanılan her `{anahtar}` için hem `messages_tr` hem `messages_en` karşılığı olduğunu doğrular. Yeni bir sabit eklenip karşılığı yazılmazsa test kırmızıya döner — 206 anahtar elle takip edilemez.

**Hata zarfı — matmuhbackend deseninden alındı, iki ayrı kod alanıyla:**

```json
{
  "success": false,
  "httpStatus": "NOT_FOUND",
  "errorCategory": "RESOURCE_NOT_FOUND",
  "errorCode": "PAYMENT_NOT_FOUND",
  "message": "Ödeme kaydı bulunamadı.",
  "resource": "callback",
  "resourceId": null,
  "path": "/payments/callback",
  "correlationId": "70fbd6d0-…",
  "timeStamp": "2026-08-28T03:10:35",
  "details": [{ "field": "email", "message": "E-posta boş olamaz." }]
}
```

**`errorCategory` kapalı küme, `errorCode` spesifik.** İstemci `errorCategory` üzerinde `switch` yazabilir (`ErrorCategory` enum'u 16 değer, hepsi tanımlı); `errorCode` ise mesaj anahtarından türediği için hangi kaynağın bulunamadığını söyler (`PAYMENT_NOT_FOUND` vs `USER_NOT_FOUND`). İkisini birden vermek, matmuh'un sabit katalog yaklaşımıyla koala'nın türetilmiş kodunun birleşimi — kapalı küme davranış için, spesifik kod log ve hata ayıklama için.

**`resource` / `resourceId` istek yolundan türetilir.** `RequestResourceResolver` yolun son kaynak segmentini ve varsa UUID'sini çıkarır; istemci "neyin bulunamadığını" ayrıca ayrıştırmak zorunda kalmaz.

**`details` yalnızca doğrulama hatalarında.** `{field, message}` çiftleri, mesajları çözümlenmiş hâlde — istemci ham anahtar görmez.


**HTTP kodu exception'ın kendisinde.** `KoalaException` bir `HttpStatus` taşır; alt sınıflar constructor'da belirler (404, 403, 409, 401, 502). `errorCode` sınıf adından türetilir: `UserNotFoundException` → `USER_NOT_FOUND`. Eskiden hepsi ayrımsız 400 dönüyordu.

**İstemciye iç detay sızmaz.** Beklenmeyen hatalarda gövdeye sabit bir metin yazılır, exception yalnızca correlation ID ile loglanır.

## 2e. Para

**Para her yerde `BigDecimal`, ölçek 2.** `Job.salary` ve `JobAssignment` üzerindeki tüm tutarlar `NUMERIC(19,2)`. `double`/`Float` ile para tutmak yasak — `Float` 50.000 TL üzerinde kuruş garantisi vermiyordu.

Hesap tek yerde: `core/helpers/CommissionCalculator`. Girdi doğrulaması, `HALF_UP` yuvarlama ve bileşenlerin toplamının tahsil edilen tutara eşit olması orada garanti ediliyor.

**Uygulanan oran kayda yazılıyor.** `JobAssignment` artık `worker_payout`, `commission_amount`, `commission_rate` ve `total_charged` taşıyor. Oran çalışma zamanı ayarından okunuyor (§2ac); değiştiğinde geçmiş kayıtlar hangi oranla hesaplandığını korur — mutabakat bunsuz yapılamaz.

## 2f. Kimlik ve principal

**`SecurityContext`'te JPA entity'si taşınmaz.** Principal `core/security/UserPrincipal` — id, e-posta ve rollerden ibaret bir `record`. Parola hash'i taşımaz.

`SecurityService` üç kapı sunar:
- `getAuthenticatedUserId()` — principal'dan okur, **veritabanına gitmez**. Yalnızca kimlik karşılaştırması yapan yerler bunu kullanır.
- `getAuthenticatedUser()` — her çağrıda entity'yi **taze okur**.
- `getSystemUser()` — denetim kayıtları için.

Eskiden `getAuthenticatedUserFromContext()` ve `getAuthenticatedUserFromDatabase()` diye iki metot vardı ve **farklı gerçeklikler döndürüyorlardı**: biri istek başında SecurityContext'e konmuş detached nesneyi, diğeri veritabanındakini. `userDao.save(contextUser)` körlemesine merge ettiği için araya giren güncelleme sessizce eziliyordu. İki metot tek metoda indirildi; bu sınıf hata artık yapısal olarak mümkün değil.

**`AuditorAwareImpl` referans kullanır.** `getReferenceById` ile proxy alır, böylece her denetimli kayıtta fazladan `SELECT` atılmaz.

**Back-office personeli bu principal'in dışındadır.** `UserPrincipal` marketplace kimliğidir; personel kendi kimlik sağlayıcısında ve kendi principal tipinde yaşar. `/admin/**`'ın hangi token'ı kabul ettiği ve personel aktörünün servis katmanına nasıl geçirildiği [ADR-0010](docs/adr/0010-split-staff-identity-from-koala-authorization.md) §Geçiş'te.

**`SessionAuthFilter` servlet konteynerine kaydedilmez.** `@Component` olduğu için Spring Boot onu güvenlik zincirinin dışında bir kez daha kaydediyordu; `SecurityConfig` bunu kapalı bir `FilterRegistrationBean` ile iptal ediyor. Bugün davranış farkı yok — `OncePerRequestFilter` ikinci çalıştırmayı zaten atlıyordu. Gerekçe ve `/admin/**` ikinci zinciri geldiğinde neyi patlatacağı ADR-0010 §Geçiş'te; `SessionAuthFilterRegistrationIT` sabitliyor.

## 2f2. Rol satırları — anahtar, tekillik ve rol verme kaydı

`authorities` tablosu üç yapısal eksikle canlıya çıkmıştı: birincil anahtarı yoktu (ADR-0010 bunu yazmış ama düzeltmemişti), `(user_id, role)` üzerinde tekillik kısıtı yoktu ve rolü kimin ne zaman verdiği hiçbir yerde durmuyordu. `ROLE_ADMIN` şirket onaylıyor, escrow'dan para salıyor ve çalışma zamanı ayarlarını değiştiriyor; "bu kişi ne zaman ve kimin eliyle admin oldu" sorusunun veritabanında cevabı yoktu.

`2026/09/06-34-authorities-key-and-grant-provenance.xml` altı changeset'te bunu kapatıyor ve **sıra kritik**: önce mükerrer temizliği, sonra kısıtlar. Java tarafı `Set<Role>` olduğu için uygulamadan bakınca tekrar görünmüyordu ama veritabanı aynı satırı iki kez kabul ediyordu; canlıda tek bir mükerrer çift varsa temizlik adımı olmadan `uk_authorities_user_role` "could not create unique index" ile patlar ve **Liquibase açılışta durduğu için uygulama hiç ayağa kalkmaz.** `AuthoritiesDuplicateMigrationIT` tam bunu sabitliyor: changeset'leri geri alıp mükerrer satır yazıyor ve changelog'u yeniden koşuyor — temizlik kısıttan sonraya alınırsa test kırmızıya döner.

**Birincil anahtar `(user_id, role)` bileşiği, ayrı bir `id` sütunu değil.** Yüzey sütunu eklemek `@ElementCollection` eşlemesini bozardı; bileşik anahtar hem mevcut eşlemeyi olduğu gibi bırakıyor hem tekilliği garanti ediyor.

**`granted_at` `TIMESTAMPTZ`.** Issue metni `TIMESTAMP` diyor ama §2h'nin kuralı ve `TimestampTimeZoneIT` başka türlüsüne izin vermiyor — saat dilimsiz kalan tek istisna `jobs.start_date_time`/`end_date_time`.

**İkisi de null bırakılabilir.** Sistem kullanıcısının seed satırının veren kimsesi yok; `NOT NULL` yapmak o satır için uydurma bir değer yazmayı zorlardı. Mevcut satırların `granted_at` değeri migration'da `now()` ile dolduruluyor, `granted_by_id` null kalıyor.

**`uk_authorities_user_role` birincil anahtarla teknik olarak fazlalık** — Postgres'te ikinci bir benzersiz indeks demek. Kısıt adıyla issue'da isteniyor ve kullanıcı başına bir-iki satır taşıyan bir tabloda maliyeti ihmal edilebilir olduğu için bırakıldı; tekilliği zaten `pk_authorities` sağlıyor, ileride sadeleştirilmek istenirse düşürülebilir.

### `@ElementCollection` ölçüldü: yalnızca koleksiyon değiştirilince siliyor

Issue'nun kritik doğrulaması `AuthoritiesGrantProvenanceIT` ile gerçek Postgres'te ölçüldü, sonuç ikiye ayrılıyor:

| Yazma biçimi | Hibernate ne yapıyor | `granted_at` / `granted_by_id` |
|---|---|---|
| `user.getAuthorities().add(rol)` | yalnızca yeni satır için `INSERT` | **korunuyor** |
| `user.setAuthorities(yeniKume)` | tüm satırlar `DELETE`, hepsi yeniden `INSERT` | **siliniyor** |

Yani delete-then-insert davranışı koşulsuz değil: koleksiyon **örneği** değiştiğinde Hibernate onu dereference edilmiş sayıp toptan siliyor, yerinde mutasyonda ise satır bazlı çalışıyor.

**Karar: `@ElementCollection` bölünmedi, `UserAuthority` entity'si açılmadı.** Gerekçe:

1. Kayıp yalnızca toptan değiştirmede oluşuyor ve bugün ana koddaki tek `setAuthorities` çağrısı `AuthManager`'daki **yeni** kullanıcı kaydı — henüz veritabanında satırı olmadığı için kaybedecek bir kaydı yok.
2. Ayrı entity'ye geçmek `User.authorities` tipini değiştirir; `UserPrincipal`, `UserMapper.roles` eşlemesi, `SessionDao` entity graph'i, `ImageManager` ve `CvManager` yetki kontrolleri bu tipe bakıyor. Kimlik doğrulamanın tam ortasında, rol verme ucu henüz yazılmamışken yapılacak bir değişiklik değil.
3. Bileşik anahtar zaten `UserAuthority`'nin sağlayacağı tekilliği veriyor; kalan tek fark provenance'ın hangi yazma biçiminde hayatta kaldığı — ve bu bir kural yazarak kapanıyor.

**Kural: kayıtlı bir kullanıcıya rol verirken `setAuthorities` kullanılmaz, `getAuthorities().add(...)` kullanılır.** Rol verme ucu yazılırken (ayrı issue) `granted_at`/`granted_by_id` açık bir `UPDATE` ile doldurulacak; Hibernate bu sütunları eşlemediği için kendiliğinden yazmıyor.

`AuthorityGrantSafetyTest` bu kuralı yapısal olarak koruyor: ana kodda `AuthManager` dışında bir `setAuthorities` çağrısı belirirse test kırmızıya döner ve buraya yönlendirir. Muafiyet listesi de kontrol ediliyor — `AuthManager`'daki çağrı kalkarsa liste bayatlamış demektir.

## 2f3. Rol verme uçları ve ilk admin

`ROLE_ADMIN` şirket onaylıyor, escrow'dan para salıyor ve çalışma zamanı ayarlarını değiştiriyordu ama **onu veren hiçbir uç yoktu**: prod'da yeni bir admin ancak psql'e girip elle `INSERT INTO authorities` yazılarak doğuyordu. İz bırakmıyor, paylaşılan psql erişimi gerektiriyor ve yanlış `user_id` yazıldığında sessizce yanlış kişiyi admin yapıyordu.

`AdminController` altında üç uç açıldı — `GET`, `POST /admin/users/{userId}/roles` ve `DELETE /admin/users/{userId}/roles/{role}`. Ayrı bir controller açılmadı: yol `/admin/users` altında ve o yüzey zaten `AdminController`'da. `SecurityConfig`'te üçü için ayrı satır var; `/admin/**` prefix'i zaten koruyor ama sözleşme açık satırı tercih ediyor (§0b).

**Bu uçların ömrü ADR-0010 §Geçiş'te yazılı.** `/admin/**` bugün yalnız marketplace `ROLE_ADMIN` kabul ediyor ve bu geçici bir köprü; kesimden sonra bu üç uç **kalır**, yalnız personel kimliğiyle korunur. Gerekçenin tek kaynağı o ADR, buraya kopyalanmıyor.

**Üçünün izinleri §2au'da.** `GET` → `user.read`, `POST`/`DELETE` → `user.role.manage`. `role.manage` neden kullanılmadığı ve iznin neden yalnız `OWNER`'da olduğu orada.

**Rol verme kaydı ayrı bir `UPDATE` ile yazılıyor.** §2f2'nin kuralı gereği koleksiyon yerinde mutasyona uğruyor (`getAuthorities().add(...)`), ama Hibernate `granted_at`/`granted_by_id` sütunlarını eşlemediği için onları kendiliğinden yazmıyor. `UserDao` üzerinde iki native `@Modifying` sorgusu var: veren belliyken ikisini birden yazan, bootstrap'ta yalnız zamanı yazan. İki ayrı metot olmasının nedeni teknik: native sorguda null bir `UUID` parametresi bağlandığında Hibernate parametre tipini çıkaramıyor; `granted_by_id` zaten null doğduğu için bootstrap yolunda ona hiç dokunulmuyor.

**Okuma da native.** Provenance sütunları eşlenmediği için JPQL onlara uzanamıyor. `findRoleGrants` `authorities` tablosunu `users` ile `LEFT JOIN`'leyip arayüz projeksiyonu döndürüyor. **Takma adlar çift tırnak içinde**: Postgres tırnaksız takma adı küçük harfe katlar ve `grantedAt` alanı eşleşmez; tırnak bunu deterministik yapıyor.

**Yazma sonrası açık `saveAndFlush`.** Rol kaldırıldığında `save` yetmiyor — `DELETE` transaction sonunda gidiyor ve aynı transaction içinden native/JDBC okuyan her şey satırı hâlâ görüyor. Verme yolunda zaten `@Modifying(flushAutomatically = true)` flush'u zorluyor; iki yolun da aynı davranması için ikisi de `saveAndFlush` kullanıyor.

**Domain kuralları ve neden bu sırayla:**

| Kural | Yanıt | Neden |
|---|---|---|
| Bilinmeyen `userId` | 404 | `getById` zaten böyle davranıyor |
| Sistem kullanıcısı (`00000000-…-0001`) | 409 | seed satırı, kimliği yok, girişi yok |
| `ROLE_USER` bu uçtan alınamaz | 409 | marketplace üyeliğinin taşıyıcısı o |
| Kullanıcı rolü zaten taşımıyor | 200, sessiz | silme idempotent |
| Admin kendi `ROLE_ADMIN`'ini alamaz | 409 | kendini kilitleme |
| Sistemdeki son `ROLE_ADMIN` alınamaz | 409 | sıfır adminli sistem uçlardan kurtarılamaz |
| Zaten var olan rol verilir | 200, ikinci satır yok | bileşik anahtar zaten engellerdi, kural onu 409'a düşürmüyor |

**Son admin kuralı bugün HTTP üzerinden erişilemez, ve bu bilinçli.** İsteği yapan kişi `ROLE_ADMIN` taşımak zorunda; hedef başkasıysa sistemde en az iki admin var, hedef kendisiyse bir üstteki kural önce ateşliyor. Yani kural bugün ölü görünüyor. Yine de duruyor çünkü ADR-0010'un kesiminden sonra isteği yapan kişi **marketplace admini olmayacak** — personel kimliğiyle gelen biri son `ROLE_ADMIN`'i alabilir hâle gelir ve kural o gün asıl işini yapar. Bu yüzden testi birim seviyesinde (`RoleGrantRulesTest`), sayım mock'lanarak yazıldı; uç testi yalnızca kuralın yanlış ateşlemediğini doğruluyor.

**Sayımdan sistem kullanıcısı düşülüyor.** `countUsersWithRole` `user_id <> '00000000-…-0001'` taşıyor. Düşülmeseydi seed satırı sayıyı daima ≥1 tutar ve kural hiçbir zaman ateşlemezdi; oysa o hesabın parolası düz metin `'N/A_PASSWORD'`, yani geçerli bir BCrypt hash'i değil ve **o hesapla giriş yapılamıyor**. Kurtarıcı bir admin değil.

**Sistem kullanıcısının id'si artık tek yerde:** `core/constants/SystemUser`. Öncesinde `SecurityManager` ve `AuditorAwareImpl` aynı UUID'yi ayrı ayrı yazıyordu; üçüncü kopya yerine sabit çıkarıldı. `ID_TEXT` derleme zamanı sabiti olduğu için native sorgu metnine de aynı kaynaktan giriyor.

### İlk admin — seçenek (a), açılışta çalışan runner

Issue iki yol öneriyordu: **(a)** `ADMIN_BOOTSTRAP_EMAIL` verildiğinde açılışta o hesaba `ROLE_ADMIN` yazan idempotent bir `ApplicationRunner`, **(b)** `scripts/` altında parametreli bir psql runbook. **(a) seçildi.**

Gerekçe: (b) elle SQL yazmayı ortadan kaldırmıyor, yalnızca kopyalanacak bir metin veriyor — yanlış `user_id` yazma riski ve paylaşılan psql erişimi ihtiyacı olduğu yerde kalır, üstelik çalıştırıldığına dair uygulama tarafında hiçbir iz kalmaz. (a)'da hedef **e-posta ile** seçiliyor, işlem loglanıyor ve idempotent olduğu için ikinci açılışta hiçbir şey yazmıyor. Kabul kriteri de bunu istiyor: taze bir veritabanında **elle SQL yazmadan, belgelenmiş tek bir adımla** ilk admin oluşturulabilmeli.

**Tek adım şu:** `ADMIN_BOOTSTRAP_EMAIL` değişkenine, kayıt akışını normal biçimde tamamlamış bir hesabın e-postası yazılır ve uygulama yeniden başlatılır. Açılışta o hesap `ROLE_ADMIN` alır ve `POST /admin/job-categories` çağrılabilir hâle gelir — ADR-0009 gereği kategori kataloğu yalnız oradan kurulabildiği ve kategori ilanın zorunlu alanı olduğu için ilk admin **ürünün açılmasının önkoşulu**. İlk admin doğduktan sonra değişken kaldırılmalı; sonraki roller uçlardan veriliyor.

**Kullanıcı yoksa uygulama düşmez, uyarı loglanır.** `bootstrapAdminRole` `{user.not.found.by.email}` fırlatıyor, runner onu — ve beklenmeyen her `RuntimeException`'ı — yakalayıp `WARN` yazıyor. Yanlış yazılmış bir e-posta yüzünden prod'un açılmaması, elde edilecek katılıktan pahalı. E-posta `LogMasks.email` ile maskeli loglanıyor (§2i).

**`granted_by_id` null bırakılıyor.** Rolü veren bir insan yok; sistem kullanıcısını uydurma bir veren olarak yazmak kaydı yanlış yapardı. `granted_at` doluyor, yani "ne zaman" sorusunun cevabı var.

**`NoStartupDataSeedingTest`'e tek muafiyet açıldı.** O test ADR-0008'in kaldırdığı QA seed runner'ının geri gelmesini engelliyor; yasağın konusu **örnek veri üretmek**. Bootstrap runner veri üretmiyor — zaten var olan bir hesaba bir rol satırı yazıyor, hem de ancak açıkça bir ortam değişkeni verildiğinde. `AuthorityGrantSafetyTest`'teki desen kopyalandı: muafiyet listesi `AdminBootstrapRunner.java` ile sınırlı ve ikinci bir test listenin bayatlamadığını doğruluyor. Yeni bir runner eklenirse test yine kırmızıya döner.

**Konfigürasyon `admin.bootstrap.email`**, `AdminBootstrapProperties` ile bağlanıyor. Boş ya da yalnız boşluk içeren değer "verilmemiş" sayılıyor; e-posta aranmadan önce `trim` ediliyor.

## 2g. Entity kimliği

`BaseEntity` UUID tabanlı `equals`/`hashCode` tanımlar ve `final`'dir; alt sınıflar ezemez. `Hibernate.getClass()` kullanılır ki proxy ile gerçek nesne eşit sayılsın. Kimliği atanmamış (henüz kaydedilmemiş) iki nesne **eşit değildir** — bu bilinçli: aksi halde bir `Set`'e eklenen iki yeni kayıt birbirini yutar.

## 2h. Zaman

**Sistem zaman damgaları `Instant`, kolonlar `TIMESTAMPTZ`.** 20 alan dönüştürüldü: `createdAt`/`updatedAt`, oturum ve doğrulama süreleri, başvuru/atama/davet zamanları. Sunucunun saat dilimi değişse bile kayıtlı an kaymaz.

**İki alan bilerek `LocalDateTime` kaldı:** `Job.startDateTime` ve `Job.endDateTime`. Bunlar duvar saati — "salı 09:00'da başlıyor" ifadesi bir ana değil, yerel bir saate işaret ediyor. Kolonları da `TIMESTAMP WITHOUT TIME ZONE`. `TimestampTimeZoneIT` başka hiçbir kolonun saat dilimsiz kalmadığını doğruluyor; yeni bir `LocalDateTime` alanı eklenirse test kırmızıya döner.

**Migration mevcut veriyi `Europe/Istanbul` varsayarak çevirdi.** `ALTER COLUMN ... USING x AT TIME ZONE 'Europe/Istanbul'`. Bu açıkça yazılmasaydı Postgres oturum saat dilimini kullanacak ve geçmiş kayıtlar 3 saat kayacaktı. Liquibase `$$` bloklarını bölmesin diye `splitStatements="false"` şart.

**API sözleşmesi değişti.** Bu alanlar artık `2026-08-27T09:30:00Z` biçiminde, sonunda `Z` ile dönüyor; öncesinde saat dilimsiz yerel zaman dönüyordu.

## 2i. Loglama

**Bir istek = bir satır.** `RequestLoggingFilter` (`http.access` logger'ı) metot, yol, durum, süre ve kullanıcı id'si basar; 4xx WARN, 5xx ERROR. Güvenlik zincirinin dışında durur ki 401/403 de loglansın; SecurityContext o noktada boş olduğu için kullanıcı id'sini `SessionAuthFilter` istek niteliğine (`app.userId`) bırakır. Sağlık uçları loglanmaz.

**Okuma yolu DEBUG.** `Getting/Fetching/Retrieving/Listing` ile başlayan 18 manager logu DEBUG'a indirildi — erişim logu geldikten sonra bunlar gürültüydü. Yazma işlemleri INFO kalır, denetim izi onlar.

**Erişim logu istemci IP'sini taşır.** İstemci IP'si tek yerden gelir: `ClientIpResolver`. Doğrudan bağlantı `getRemoteAddr()` değeridir; `Forwarded` veya `X-Forwarded-For` yalnız doğrudan kaynak `app.trusted-proxies` içindeki bir IP/CIDR ise okunur. Güvenilmeyen istemcinin başlığı yok sayılır. Spring'in başlığı güven sınırı bilmeden tüketmemesi için `forward-headers-strategy: none`; zinciri yalnız bu çözücü işler. BFF gelen yönlendirme başlıklarını kopyalamaz, bağlantı IP'siyle tek değer yazar.

**PII maskelenir.** `core/helpers/LogMasks`: e-posta yerel kısmı maskeli (`y***@gmail.com`, alan adı kalır ki başarısız giriş takip edilebilsin), telefonun son dört hanesi, IBAN'ın baş ve sonu, TCKN hiç. Kimlik gerektiğinde kullanıcı **id**'si yazılır.

**`LogPrivacyTest` bunu yapısal olarak koruyor.** Kaynak kodu tarayıp hiçbir log çağrısının maskelenmemiş e-posta, telefon, IBAN, TCKN veya parola taşımadığını doğrular. Yazıldığında `NotificationManager` içinde gözden kaçmış bir SMS logunu yakaladı.

**Logger `@Slf4j` ile geliyor.** Faz 19'da elle `LoggerFactory.getLogger(...)` çağıran 59 dosya Lombok'un `@Slf4j` anotasyonuna geçti; alan adı her yerde `log`. Öncesinde dört farklı tanım biçimi vardı (instance/static karışık, hatta `this.getClass()` — static bir alanda derlenmiyordu bile). Spring projelerinde yaygın olan biçim bu.

**Akış anlatımı DEBUG, durum değişikliği INFO.** "Creating address…", "Checking if user is a contact", "Starting registration" gibi 12 anlatı logu daha DEBUG'a indi. INFO artık *olan biteni* kaydediyor: hangi kayıt, hangi id, hangi karar. Erişim logu (§ yukarıda) zaten her isteği bir satırla yazıyor.

**Yapısal format opsiyonel.** `SPRING_PROFILES_ACTIVE=json` ile çıktı ECS JSON'una döner. Varsayılan bilerek metin. Seviyeler `APP_LOG_LEVEL`, `ACCESS_LOG_LEVEL`, `SECURITY_LOG_LEVEL` ile ayarlanır.

## 2j. Hız sınırı

**Beş kova var, yazma ve pahalı genel uçların hepsi kapsanıyor.**

| Kova | Kapsam | Varsayılan | Anahtar |
|---|---|---|---|
| `AUTH` | giriş, kayıt akışı, şifre sıfırlama, kimlik doğrulama | 10 / 1 dk | yol + IP + hedef özeti |
| `UPLOAD` | görsel, özgeçmiş, profil fotoğrafı, logo, rozet görseli | 20 / 1 dk | kova + IP |
| `MESSAGE` | mesaj gönderme | 30 / 1 dk | kova + IP |
| `PUBLIC` | ilan listesi/detayı/görselleri ve adres ağ geçidi | 120 / 1 dk | kova + IP |
| `WRITE` | **diğer tüm** POST/PUT/PATCH/DELETE | 60 / 1 dk | kova + IP |

**`WRITE` bilerek yakalayıcı.** Yeni bir yazma ucu eklendiğinde listeye yazılması gerekmez, kendiliğinden kapsanır — `SecurityConfig`'teki `anyRequest().authenticated()` ile aynı mantık: unutulan uç açıkta kalmaz.

**Yazma kovalarının anahtarında yol yok.** `AUTH` yol bazlı kalır ve gövdedeki e-posta/telefon/token gibi hedefin SHA-256 özeti IP'ye eklenir; aynı BFF IP'sindeki bir hesabın başarısız denemesi diğer hesabı kilitlemez ve ham PII sayaç anahtarına girmez. Yükleme, genel okuma ve yazmada anahtar yalnızca kova + IP'dir.

**Anahtar IP, kullanıcı değil.** `RateLimitFilter` güvenlik zincirinin dışında (`HIGHEST_PRECEDENCE + 20`) çalıştığı için o noktada principal henüz yok. Kullanıcı bazlı sınır istenirse filtrenin `SessionAuthFilter`'dan sonraya, güvenlik zincirinin içine taşınması gerekir.

**Diğer okuma uçları sınırlanmıyor.** Genel ilan korpusu ve adres sağlayıcı ağ geçidi `PUBLIC` kovasındadır; kimlikli profil okumaları sınır dışıdır.

Aşıldığında 429 ve `TOO_MANY_REQUESTS` döner.

Sayaç bellekte (`ConcurrentHashMap`), yani **tek örnek varsayımıyla çalışıyor**. Birden fazla örnek çalıştırıldığında sınır örnek başına uygulanır; o noktada Redis'e taşınmalı.

Testlerde `rate-limit.enabled=false`; açıkça test eden sınıflar `@TestPropertySource` ile kendi ayarını verir.

## 2k. Ödeme akışı

**Callback gövdesine güvenilmez.** Iyzico `/payments/callback` ucuna yalnızca bir `token` bırakır; tutar, durum ve işlem kimliği o gövdeden **okunmaz**. `PaymentManager.completeFromCallback` token ile Iyzico'ya sunucudan sorar (`retrieveCheckoutFormResult`) ve kararı o yanıta göre verir. Bu yüzden gelen isteğe imza doğrulaması uygulanmıyor — doğrulama zaten sunucudan sunucuya yapılıyor.

**Her deneme ayrı kayıt.** `Payment` tablosu bir `JobAssignment` için birden çok deneme tutar. `conversation_id` benzersiz ve **her denemede yeniden üretilir**; eskiden `assignmentId` ile eşitlenmişti ve ikinci deneme birincisiyle çakışıyordu.

**Callback artık doğrulanıyor.** Sağlayıcının `paymentStatus`, `fraudStatus`, `paidPrice` ve `currency` değerleri kontrol edilmeden emanet açılıyordu; ayrıntı ve neyin kırık olduğu §2x'te.

**Callback idempotent.** Zaten `SUCCEEDED` olan bir ödemeye ikinci callback geldiğinde sağlayıcıya hiç gidilmez, kayıt değiştirilmez. Iyzico aynı callback'i tekrar gönderirse ikinci kez para hareketi işlenmez.

**Durum geçişleri `PaymentStateMachine`'de.** Para yalnızca ileri gider: `PENDING_COLLECTION → HELD_IN_ESCROW → REFUNDED`. Başarısız deneme `FAILED`'a düşer ve yeniden denenebilir. Ödenmiş bir atama tekrar tahsilata açılamaz; geçersiz geçiş sessizce uygulanmaz, `IllegalStateException` fırlatır.

**Ödeme onaylanınca kırılım atamaya yazılır.** `finalPayout`, `workerPayout`, `commissionAmount`, `commissionRate` ve `totalCharged` o anki değerlerle sabitlenir.

**Emanet garantisi işin başlangıcında zorunlu.** `checkIn` ve `completeAssignment` artık `paymentStatus == HELD_IN_ESCROW` şartı arıyor. Öncesinde ikisi de yalnızca atama durumuna bakıyordu; yani **işveren tek kuruş ödemeden işçi işe başlayabiliyor, çalışabiliyor ve iş tamamlanabiliyordu.** Sonunda `releaseToWorker` "emanette para yok" deyip reddediyordu ve `openDispute` de emanette para şartı aradığı için işçinin başvurabileceği hiçbir yol kalmıyordu. Ödeme sırası değişmedi (işçi onaylar → işveren öder), değişen şey ödemenin **kapı** hâline gelmesi.

**İşçiye çıkış ayrı bir adım.** `HELD_IN_ESCROW` durumundaki para kendiliğinden işçiye geçmez. `PayoutService.releaseToWorker` yalnızca atama `COMPLETED` ise çalışır ve `Payout` kaydı üretip durumu `RELEASED_TO_WORKER` yapar. Aynı atama için ikinci kez çağrılırsa yeni kayıt açılmaz. `refundEmployer` ise yalnızca `HELD_IN_ESCROW` iken mümkündür — işçiye çıkmış para iade edilemez.

**IBAN artık `User` üzerinde değil.** `BankAccount` entity'sinde; birincil hesap `is_primary` ile işaretli, doğrulama `BankAccountManager` içinde. `users.iban` kolonu mevcut veriler `bank_accounts`'a kopyalandıktan sonra düşürüldü — iki kaynak bırakılmadı.

`initiatePaymentSession` bütünüyle `@Transactional`: adres eksikliği ya da Iyzico hatası durumunda `Payment` kaydı da geri alınır, sahipsiz deneme kalmaz.

## 2l. Sayfalama ve ilan araması

**Sayfalama ile koleksiyon fetch'i aynı sorguda olmaz.** Hibernate LIMIT'i veritabanına veremeyip tüm satırları belleğe çeker. Kural: `JobDao.searchIds` önce **yalnızca id'leri** sayfalar, sonra `findAllByIdIn` o id'ler için varlıkları entity graph ile tek sorguda yükler. Sıra `inIdOrder` ile korunur.

**Süresi geçmiş ilanlar aramada görünmez.** `searchIds` `status = OPEN`'a ek olarak `endDateTime >= :activeAfter` şartını taşır (bitiş tarihi olmayan ilanlar hariç tutulmaz). Öncesinde üç hafta önce bitmiş bir ilan hâlâ listeleniyordu; işçi başvuruyor, işveren anlamsız bir başvuru alıyordu. Karşılaştırma duvar saati olduğu için `job-search.zone-id` üzerinden yapılır (§2h ile aynı gerekçe).

`:activeAfter` **null olamaz.** Null bırakılsaydı Postgres parametrenin tipini çıkaramayıp `could not determine data type` verirdi — `:query` için `cast(... as string)` gerektiren tuzağın aynısı. Parametreyi zorunlu yapmak sorunu kaynağında kaldırıyor.

**`JobStatus.IN_PROGRESS` ve `COMPLETED` hiçbir yerde set edilmiyor.** İlan `OPEN` → `CLOSED`/`CANCELLED` dışına çıkmıyor; işveren elle kapatıyor. `Job.headcount` eklendikten sonra da böyle kaldı — gerekçe §2ao2'de: ilanı otomatik `IN_PROGRESS`'e almak onu bu sorgudan düşürür ve sonradan işe alımı imkânsızlaştırırdı. Tarih filtresi bunun en görünür sonucunu (bitmiş ilanın listede kalması) zaten kapatıyor.

**Sıralama beyaz listeye kısıtlı.** `PageableSanitizer` yalnızca `createdAt`, `salary`, `startDateTime`, `title` alanlarına izin verir; listede olmayan bir alan sessizce yok sayılmaz, **400 döner**. Sayfa boyutu 100 ile sınırlı, `Pageable.unpaged()` geldiğinde varsayılana düşer.

**Null parametreler cast edilmek zorunda.** `lower(concat('%', :query, '%'))` içinde `:query` null olduğunda Postgres tipi çıkaramayıp `function lower(bytea) does not exist` hatası veriyor. JPQL'de `cast(:query as string)` şart.

**İstemciden gelen `sort` doğrulanmadan DAO'ya geçirilemez.** Spring Data bilinmeyen bir alan için `InvalidDataAccessApiUsageException` fırlatır ve bu **500**'e düşer — istemci hatasının sunucu hatası gibi görünmesi. Dokümantasyon yazıldıktan sonra Swagger UI'ın `Pageable` için ürettiği `sort=["string"]` örneği bunu prod'da tetikledi; denetimde **dokuz sayfalı uç** korumasız çıktı (şikayetler, bildirimler, uyuşmazlıklar, yetenek havuzu, takip edilen şirketler, admin şirket/kullanıcı listeleri). Hepsi `pageableSanitizer.rejectUnknownSort` ile 400'e çevrildi.

İki katmanlı savunma var: `PageableSortSafetyTest` her `Pageable` alan manager metodunun doğrulama yaptığını derleme sonrası kontrol eder (saf devretmeler gerekçeli muafiyet listesinde), `GlobalExceptionHandler` ise `InvalidDataAccessApiUsageException`'ı yine de 400'e çevirir — gözden kaçan bir yol 500 döndürmesin diye.

**Swagger `Pageable`'ı düz parametre olarak çizmeli.** `@ParameterObject` olmadan springdoc `Pageable`'ı tek bir nesne sanıyor ve `sort` için `["string"]` örneği üretiyor; "Try it out" diyen herkes 500 alıyordu. Sayfalı uçların hepsinde `@ParameterObject @PageableDefault` var.

**Yanıt zarfı `PageDto`:** `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.

## 2m. İstemci sözleşmesi

**Cihaz kimliği `X-Device-Id` başlığından gelir.** Web BFF `X-Client-Platform: web` gönderdiğinde biçimi `[A-Za-z0-9._:-]{8,128}` olan cihaz kimliği login ve refresh için zorunludur; User-Agent yalnız okunabilir etikettir ve `WEB_BROWSER` eşleşmesine katılmaz. Cookie silinirse yeni tarayıcı cihazı oluşur, eski oturum kullanıcı tarafından oturum yüzeyinden kapatılır. Platform başlığı göndermeyen eski mobil sürümlerde User-Agent geçiş davranışı korunur.

`ClientContext.from(request, clientIpResolver)` güvenilir istemci IP'si, User-Agent, cihaz kimliği ve platformu tek nesnede toplar.

**`GET /users/me` istemcinin yönlendirme kararı için gerekeni döner:** `employer`, `emailVerified`, `phoneVerified`, `identityVerified`, `roles` ve `companies[{companyId, companyName, role, approved}]`. Öncesinde `isEmployer` yalnızca tek seferlik kayıt akışında dönüyordu, giriş sonrası kullanıcının işveren mi çalışan mı olduğu öğrenilemiyordu.

**`JobCategory` sabit bir `code` taşır.** İstemci ikon/pin/renk eşlemesini UUID'ye ya da görünen ada değil bu koda bağlar.

**Kodlar İngilizce ve addan bağımsız.** İlk sürüm kodu Türkçe `name`'den türetiyordu (`Fotoğrafçılık` → `FOTOGRAFCILIK`); görünen ad değişince ya da İngilizce dil desteği gelince kod da değişecekti ve mobildeki bütün ikon eşlemeleri kırılacaktı. `27-10` migration'ı bilinen Türkçe kodları İngilizceye çeviriyor. Kural yerinde: kod `[A-Z][A-Z0-9_]{1,63}` biçiminde, ASCII ve görünen addan bağımsızdır; artık `SaveJobCategoryDto` üzerindeki `@Pattern` ile yazma yolunda zorlanıyor.

**Kategori seed'i kaldırıldı, katalog yönetimden kuruluyor.** `27-10`'un yüklediği beş kategori (`PHOTOGRAPHY`, `WAITER`, `PET_CARE`, `DESIGN`, `BARISTA`) `2026/09/06-02-drop-job-category-seed.xml` ile düşürüldü; veritabanı ADR-0008 doğrultusunda kategorisiz geliyor. Kategori ilanın zorunlu alanı olduğu için katalog kurulmadan ilan açılamaz: **boş bir veritabanında ilk iş, `POST /admin/job-categories` ile kategorileri yaratmaktır.** Yüzey `webAPI/JobCategoriesController` altında, `/admin/**` prefix kuralıyla ADMIN'e kısıtlı (§0b).

**Silme changeset'i referans veren satırlara dokunmuyor.** `DELETE`, `jobs`, `job_alerts` ve `job_templates` üzerinde `NOT EXISTS` ile korunuyor. Koşulsuz silinseydi, canlı veritabanında bir ilan bu kategorilerden birine bakıyorken migration yabancı anahtar ihlaliyle düşer ve **Liquibase açılışta patladığı için uygulama hiç ayağa kalkmazdı**. Koşullu silme her iki durumda da doğru: ADR-0008'in teslim ettiği boş veritabanında beşi de gider, veri olan bir veritabanında kullanımdaki kategori kalır ve ilanlar bittiğinde yönetimden silinebilir. `27-10`'un kendisine dokunulmadı — uygulanmış bir changeSet'i değiştirmek mevcut veritabanlarında checksum'ı bozar; taze veritabanı beşini yazıp hemen siliyor, net sonuç aynı.

**Kullanımdaki kategori silinemez ve bu kontrol soft delete'i görmek zorunda.** `DELETE /admin/job-categories/{id}`, üç referans tablosunu tarayıp doluysa 409 (`job.category.in.use`) döner. Kontrol **native** sorgu: `Job`, `JobAlert` ve `JobTemplate` üzerinde `@SQLRestriction("is_deleted = false")` var, dolayısıyla türetilmiş bir `existsByCategoryId` soft delete edilmiş satırı görmez — ama yabancı anahtar o satırı hâlâ tutar ve silme `DataIntegrityViolationException`'a, oradan da 500'e düşerdi. `JobCategoryAdministrationIT` tam bu senaryoyu sabitliyor; sorgu türetilmişe çevrilirse test kırmızıya döner.

**Kod benzersizliği ham veritabanı hatasına bırakılmıyor.** `uk_job_categories_code` ihlali istemciye sızmasın diye `JobCategoryManager` yazmadan önce `existsByCode` / `existsByCodeAndIdNot` ile bakar ve `ConflictException` (409, `job.category.code.already.exists`) fırlatır — `BadgeManager`'daki desenin aynısı.

**ADR-0008 ile bir tutarsızlık kaldı.** ADR metni "yalnız referans verisiyle gelir" listesinde **iş kategorilerini** sayıyor; bu slice o maddeyi geçersiz kılıyor. ADR'ler düzeltilmiyor, üstüne yazılıyor (ADR-0008 de ADR-0001'in seed maddesinin yerine geçmişti) — maddeyi devralacak bir ADR yazılması bekliyor.

**Profil yüzeyi `/users/me` üzerinden okunur.** Yazılıp geri okunamayan üç alan kapatıldı: `experiences`, `maskedIban` (`TR33***4444` — tam IBAN dönmüyor) ve yeni `cvUrl`. Ayrıca `jobTitle` (meslek/ünvan) eklendi ve `educations` yanıta girdi.

**Telefon değişikliği artık sessizce yutulmuyor.** `patchCurrentUser` içindeki blok boştu, yalnızca bir `//TODO` vardı — istek 200 dönüyor, numara değişmiyordu. Şimdi e-posta ile aynı desende: `phoneVerificationService.startPhoneVerification` çağrılıyor, numara **doğrulama tamamlanana kadar uygulanmıyor**, yanıttaki `pendingVerifications` içine `PHONE` girdisi düşüyor. Aynı numara gönderilirse hiçbir şey başlatılmaz. `UserPatchRulesTest` bunu sabitliyor.

**`PhoneVerificationManager` bir süre taslak kaldı.** Kod üretiyor, hiçbir yere yazmıyor ve `null` döndürüyordu; `patchCurrentUser` de o null'ı `pendingVerifications` listesine ekliyordu. Yani yukarıdaki paragraf yazıldığı sırada doğru değildi. Artık `EmailVerificationManager` ile aynı desende: `PhoneVerification` kaydı açılıyor, 15 dakikalık altı haneli kod üretiliyor ve SMS kuyruğa alınıyor. **Ama SMS hâlâ teslim edilmiyor** — aşağıdaki tüketici eksiği geçerli.

**`ConversationDto` liste için gerekeni taşıyor.** Sohbet listesi son mesaj metnini ve okunmamış sayısını çiziyordu, ikisi de yoktu; istemci satır başına ayrı istek atmak zorunda kalıyordu. `lastMessageBody` `Conversation` üzerine **denormalize edildi** (mesaj gönderilirken yazılıyor), `unreadCount` ise sayfadaki tüm konuşmalar için `countUnreadByConversationIds` ile **tek sorguda** toplanıyor. Yani liste iki sorgu: konuşmalar + sayaçlar.

**İki ayrı ilan DTO'su var.** `JobSummaryDto` liste ve harita için, `JobDetailDto` detay sayfası için. İkisi de `addressPrecision` taşır. Tam adres ve gerçek koordinat yalnız ilan sahibi, etkin ataması (`PENDING_CONFIRMATION`/`CONFIRMED`/`IN_PROGRESS`) bulunan işçi veya `addressPublic=true` olan şirket ilanında döner. Tamamlanan/iptal edilen atamalarda adres yeniden gizlenir ve bireysel ilanlarda `addressPublic` değeri dikkate alınmaz. Diğer çağrılarda sokak, bina, kapı, posta kodu ve yön tarifi temizlenir; koordinat iki ondalığa yuvarlanır. `JobAddressVisibility` bu kararı ve maskelemeyi tek yerde tutar. `GET /jobs/{id}` özet değil detay döner.

**Yenileme ucu süresi dolmuş erişim token'ıyla çalışır.** `/auth/refresh-token` zaten *token süresi dolduğu için* çağrılıyor; iki yerde birden reddediliyordu:
1. `SessionAuthFilter` `permitAll` uçlarda bile başlığı ayrıştırıp `ExpiredJwtException` fırlatıyordu → filtre artık `shouldNotFilter` ile bu ucu atlıyor. Uç kendi kimlik doğrulamasını yapıyor, filtrenin katacağı bir şey yok.
2. `extractSessionId` süresi dolmuş token'da patlıyordu → yenileme yolunda `extractSessionIdAllowingExpired` kullanılıyor; `ExpiredJwtException` yakalanıp claim'ler ondan okunuyor.

**İmza toleransı gevşetilmedi.** JJWT imzayı süre kontrolünden önce doğruluyor, yani sahte imzalı token hâlâ reddediliyor. Süresi dolmuş token burada yalnızca **oturum bulucu**; yetkiyi veren şey `refreshTokenHash` eşleşmesi, oturumun aktif olması ve cihaz eşleşmesi. `ExpiredTokenRefreshTest` bu ayrımı sabitliyor.

**`isEmployer` JSON anahtarı açıkça sabitlendi.** Lombok, `private boolean isEmployer` alanı için `isEmployer()` getter ve `setEmployer()` setter üretiyor; Jackson bundan **`employer`** özellik adını çıkarıyordu. İstemci `isEmployer` gönderiyor, Jackson bilinmeyen alanı sessizce yutuyor, bayrak `false` kalıyordu — yani **işveren kaydı hatasız biçimde çalışan olarak açılıyordu.**

Üç DTO'da da (`AuthStartRegistrationDto`, `AuthSetPasswordDto`, `UserMeResponseDto`) `@JsonProperty("isEmployer")` ile anahtar sabitlendi. Ayrıca istek DTO'sundaki alan `Boolean` + `@NotNull` yapıldı: eksik gönderilirse artık 400 dönüyor, sessizce çalışan olarak kaydedilmiyor.

**Ders:** primitive `boolean` alanlarda Lombok/Jackson ad türetmesine güvenme, `@JsonProperty` ile açıkça yaz. `EmployerFlagJsonContractTest` bu üç DTO'nun anahtarını sabitliyor.

**Harita için bbox filtresi.** `minLatitude`/`maxLatitude`/`minLongitude`/`maxLongitude` arama kriterlerinde; il/ilçe filtresiyle birlikte kullanılabilir.

## 2n. Yasal katman

**Rıza kaydı geçmişe dönük üretilemez** — bu yüzden kayıt akışının parçası. `POST /auth/complete-registration` gövdesinde `consents[{agreementVersionId, granted}]` bekler; zorunlu sözleşmelerden biri onaylanmamışsa kayıt tamamlanmaz.

`ConsentRecord` **kimin, hangi metnin hangi sürümünü, ne zaman, hangi IP ve istemciden** onayladığını tutar. Reddedilen onay da kaydedilir (`granted = false`) — "sormadık" ile "sorduk, reddetti" ayrımı denetimde gerekiyor.

`AgreementVersion` sürümlü: `type + version + locale` benzersiz. Metin değişince yeni sürüm eklenir, eskisi `retiredAt` ile emekliye ayrılır; kimin hangi sürümü onayladığı korunur. Başlangıç verisi olarak dört sözleşme yüklü (kullanım koşulları, gizlilik, açık rıza zorunlu; ticari ileti isteğe bağlı).

**Hesap silme düz `DELETE` değil.** `AccountDeletionRequest` talebi kuyruğa alır ve **açık yükümlülük varken reddeder**: escrow'da bekleyen para ya da devam eden iş varsa silinemez. Fatura ve ödeme kayıtlarının saklanma yükümlülüğü olduğu için asıl işlem anonimleştirme olacak — o adım henüz yazılmadı, talep kaydı ve engelleme mantığı hazır.

**Bu maddelerin hukuki ayrıntısı avukatla netleştirilmeli.** Buradaki iş veri modelinin doğru şeyi tutması; metinlerin içeriği ve İYS entegrasyonu ayrı.

## 2o. Mesajlaşma ve değerlendirme

**Mesajlaşma platform içinde tutulur.** Taraflar telefon paylaşıp dışarı çıkarsa komisyon kaybedilir; bu yüzden konuşma ilana bağlı bir kayıt.

`Conversation` = ilan + işveren + işçi. `(job_id, worker_id)` kısmi benzersiz indeksle tekil — aynı ilanda aynı işçiyle ikinci konuşma açılmıyor.

**Üyelik `conversation_participants` tablosunda, `employer`/`worker` sütunlarında değil.** İkili sohbet artık iki katılımcılı bir konuşmadan ibaret; `kind` alanı `DIRECT` ya da `GROUP`. `employer` her iki türde de ilan sahibini gösterir, `worker` yalnızca `DIRECT`'te doludur ve ikili tekilliği o sağlar. `hasParticipant` **yalnızca** katılımcı listesine bakar — bir süre eski sütunlara düşen bir yedek dal vardı ve `addParticipant` onu görüp "zaten üye" sanarak işvereni gruba hiç eklemiyordu. İki kaynak tutmanın bedeli buydu; `29-01` migration'ı mevcut satırları katılımcı tablosuna aktarıyor.

**Grup kendiliğinden kurulur: grup = ilanın ekibi.** Bir atama `CONFIRMED` olduğunda ilanın grubu yoksa açılır ve işçi eklenir. İşveren elle grup kurmaz, kimseyi elle eklemez.

**Atama tarafı mesajlaşmayı tanımıyor.** `JobAssignmentManager` `MessagingService`'e bağlansaydı `JobAssignmentManager → MessagingService → JobApplicationService → JobAssignmentService` döngüsü çıkardı (denendi, görüldü). Bunun yerine `AssignmentConfirmedEvent` yayınlanıyor, `CrewConversationListener` yakalıyor. Derleme zamanı bağımlılığı yok; dinleyici hata alırsa yalnızca uyarı loglanır, atama düşmez.

**Grupta engelleme uygulanmaz.** İkili sohbette iki yönlü engel yerinde duruyor, ama grup aynı işte çalışan insanların iş kanalı — orada engel iletişimi keserdi. Ürün kararı.

**Mesajlaşmanın bir penceresi var.** İş bittikten `messaging.grace-days-after-job` (7) gün sonra sohbet **salt okunur** olur: geçmiş okunur, yeni mesaj gönderilemez (409). Amaç ilişkinin süresiz açık kalıp platform dışına taşınmasını önlemek. `endDateTime` olmayan ilanlarda pencere hiç kapanmaz. `ConversationDto.closed` bunu istemciye söyler; karşılaştırma duvar saati olduğu için `messaging.zone-id` üzerinden yapılır.

**Konuşma açmak için arada bir başvuru olmalı.** `jobApplicationDao.existsByJobIdAndUserId` kontrolü var; rastgele bir kullanıcı yabancı bir ilan üzerinden mesaj başlatamıyor. Yalnızca iki taraf okuyup yazabiliyor — taraf olmayan **404** alıyor (403 değil; konuşmanın varlığını da sızdırmamak için).

**Engelleme iki yönlü çalışır.** `UserBlock` tek yönlü kayıt ama `assertNotBlocked` her iki yönü de kontrol ediyor: A B'yi engellediyse B de A'ya yazamıyor. Engelleme idempotent, kendini engellemek reddediliyor.

**Değerlendirme yalnızca tamamlanmış işte.** `AssignmentStatus.COMPLETED` şartı var, her taraf bir kez yazabiliyor (`(job_assignment_id, author_id)` kısmi benzersiz indeks), puan 1-5 arası — hem uygulama katmanında hem `CHECK` kısıtıyla.

**`RatingSummary` biriktirir, yeniden hesaplamaz.** `reviewCount` ve `ratingSum` artırılıp ortalama türetiliyor; her profil görüntülemesinde toplama sorgusu atılmıyor. Ortalama `NUMERIC(3,2)`, `HALF_UP`.

## 2p. İptal, uyuşmazlık, şikayet ve puantaj

**İptal politikası tek yerde: `core/helpers/CancellationPolicy`.** Girdisi taraf + tahsil edilen tutar + işin başlangıcına kalan süre; çıktısı oran, ceza ve iade. Saf hesap, Spring'siz test edilir.

Kurallar (`cancellation.*` env'lerinden ayarlanır):
- **İşçi iptal ederse** işveren her koşulda tam iade alır. İşçiden para tutmuyoruz; yaptırımı itibar tarafında.
- **İşveren `free-window-hours` (24) öncesinde iptal ederse** ceza yok.
- **İşveren pencere içinde iptal ederse** `late-fee-rate` (0,50) kadar ceza keser, kalanı iade edilir. Ceza işçinin boşa ayırdığı günün karşılığı.
- **İş başladıktan sonra** iade yok.

`feeAmount + refundAmount` her zaman tahsil edilen tutara **tam eşit**; test bunu farklı tutarlarda doğruluyor. Emanette para yoksa (`PENDING_COLLECTION`) hiçbir tutar üretilmez ve `refundEmployer` çağrılmaz.

**`Job.startDateTime` duvar saati olduğu için** kalan süre `cancellation.zone-id` (varsayılan `Europe/Istanbul`) üzerinden `Instant`'a çevrilerek ölçülür. Sunucu UTC'de çalışsa da "salı 09:00" Türkiye'deki 09:00'dur.

İptal atama başına tektir (`uk_cancellations_assignment_active`), yalnızca `PENDING_CONFIRMATION`/`CONFIRMED`/`IN_PROGRESS` durumlarında mümkün.

**Açık uyuşmazlık emaneti dondurur.** `PayoutManager.releaseToWorker` artık `disputeDao.existsByJobAssignmentIdAndStatusIn(..., ACTIVE)` kontrolü yapıyor; açık uyuşmazlık varsa para işçiye çıkmaz. Bu kontrol `PaymentStateMachine`'in içine konmadı çünkü state machine para durumunu bilir, uyuşmazlığı bilmez.

Uyuşmazlık yalnızca **emanette para varken** açılabilir, atama başına aynı anda bir tane açık kalabilir (`uk_disputes_assignment_open` kısmi indeksi `status <> 'RESOLVED'` ile). Çözüm admin'de: `RELEASE_TO_WORKER` parayı çıkarır, `REFUND_EMPLOYER` iade eder, `DISMISSED` emanete dokunmaz.

**`SPLIT` çözümü bilerek yok.** `PaymentStateMachine` tek yönlü ve tek sonuçlu: bir atama ya `RELEASED_TO_WORKER` ya `REFUNDED` olur. Kısmi paylaşım eklenirse durum makinesinin de bölünebilir hale gelmesi gerekir; yarım çalışan bir yol bırakmaktansa dışarıda tutuldu.

**Şikayet üç hedeften birine bağlanabilir:** kullanıcı, ilan veya mesaj. Üçü de boşsa 400. Kendini şikayet etmek reddediliyor. Sonuçlanmış (`ACTIONED`/`DISMISSED`) bir şikayet tekrar sonuçlandırılamaz.

**`workHours` artık tahmin değil.** `WorkSession` check-in/check-out çiftini ve isteğe bağlı konumu tutar; kapanışta dakika yazılır ve atamanın `workHours` alanı tüm oturumların toplamından `HALF_UP` ile türetilir. Atama başına aynı anda tek açık oturum var (`uk_work_sessions_open_per_assignment`). Check-in/out yalnızca **işçinin kendisi** yapabilir — işveren yalnızca okuyabilir. İlk check-in atamayı `IN_PROGRESS`'e taşır.

## 2r. Profil uçları

`GET/POST/DELETE /users/me/saved-jobs[/{jobId}]` — kaydedilen ilanlar. Kaydetme idempotent, olmayan ilan 404, kaydedilmemiş ilanı silmek sessizce geçer.

`PUT/DELETE /users/me/cv` — özgeçmiş. Yalnızca PDF ve Word kabul ediliyor, 10 MB sınırı var, R2'ye `files/` altına yazılıyor. Profil fotoğrafından farkı: metadata temizleme yok (görsel değil), tür beyaz listesi var.

`GET/POST/PUT/DELETE /users/me/educations[/{id}]` — eğitim kayıtları. Başkasının kaydına dokunmak **404** döner. Fakülte seçilen üniversiteye, bölüm seçilen fakülteye ait değilse 400 — istemci üç ayrı seçim yapıyor, tutarlılık sunucuda doğrulanıyor.

`GET /reference/universities`, `/universities/{id}/faculties`, `/faculties/{id}/departments` — eğitim seçimi için referans veri, herkese açık.

## 2s. Kendi verim uçları ve yönetim

`GET /users/me/applications` (isteğe bağlı `status` filtresi) ve `GET /users/me/assignments` (`asEmployer=true` ile işveren tarafı). İkisi de sayfalı, sıralama `PageableSanitizer` beyaz listesiyle kısıtlı, entity graph ile tek sorguda ilan + kategori + adres + şirket yükleniyor.

**Oturum listeleme kendi oturumunu işaretler.** `UserPrincipal` artık `sessionId` taşıyor; `GET /users/me/sessions` her satırda `current` bayrağı döndürüyor, `DELETE /users/me/sessions` **istek atan oturum hariç** hepsini kapatıyor. Başkasının oturumunu kapatmaya çalışmak 404 döner.

**K-07 kapandı: oturum yazımı seyreltildi.** `SessionAuthFilter` her istekte `lastAccessedAt` yazıp `save` çağırıyordu — istek başına bir UPDATE. Artık yalnızca son erişim **5 dakikadan eskiyse** yazılıyor (`LAST_ACCESS_WRITE_INTERVAL`). Oturum tazeliği bu çözünürlükte yeterli; asıl süre kontrolü `refreshExpiresAt` üzerinden yapılıyor.

**Push zinciri üç yerden kopuktu, ikisi kapatıldı.** `Device.pushToken` kolonu, cihaz başına dağıtım ve `push.queue`'ya yazan üretici baştan vardı; ama **token'ı yazan hiçbir kod yoktu** — `setPushToken(null)` dışında bir çağrı bulunmuyordu, dolayısıyla `getDevicesByUserIdAndPushTokenNotNull` hep boş dönüyor ve `sendPushToUser` "cihaz yok" deyip çıkıyordu. Yani push kuyruğa bile girmemişti.

`PUT/DELETE /users/me/devices/push-token` bunu kapatıyor. **Hedef gövdeden alınmaz:** token daima oturum sahibinin `X-Device-Id` ile eşleşen cihazına yazılır. Gövdeden `userId` ya da `deviceId` kabul edilseydi saldırgan kendi token'ını kurbanın hesabına bağlayıp **kurbanın bildirimlerini okuyabilirdi**; asıl tehdit bu, kullanıcının kendi bildirimini engellemesi değil (onu zaten telefon ayarından yapabilir).

**Token küresel olarak tekil.** Kayıt sırasında aynı token başka bir cihaz satırında duruyorsa oradan düşürülür. Aksi hâlde ortak telefonda A çıkıp B girdiğinde FCM aynı token'ı verir, A'nın satırında token kalır ve **A'ya giden bildirimler B'nin elindeki telefona düşer.** FCM'in kendi belgelerinin ilk uyarısı budur.

**Pazaryeri akışı bir süre tümüyle sessizdi.** `JobApplicationManager`, `JobAssignmentManager`, `PayoutManager`, `DisputeManager` ve `CancellationManager` hiç bildirim göndermiyordu — başvuru kabul/red, atama teklifi, iş tamamlama, emanet çıkışı, uyuşmazlık ve iptal, çekirdek döngünün hiçbir adımı duyurulmuyordu. `NotificationCategory.APPLICATION_UPDATES` tam bunun için tanımlıydı ve hiç kullanılmamıştı.

**Her etkinlik iki kanaldan gider: push + e-posta.** Olay başına ayrı şablon yerine tek bir `activity-notice-template` var — bunlar kısa işlemsel bildirimler ("Başvurunuz kabul edildi"), her biri için ayrı tasarım gereksiz. Şablon ad, mesaj, ilan başlığı ve uygulamaya dönüş bağlantısı alıyor. Alıcının e-postası yoksa yalnızca push gider; bir kanalın patlaması diğerini durdurmaz, ikisi de ayrı `try/catch` içinde.

**Bildirim yönlendirmesi tek yerde: `ActivityNotificationManager`.** Hiçbir DAO'ya uzanmaz, `NotificationService` ve `MessageResolver` dışında bağımlılığı yoktur; manager'lar `activityNotificationService.applicationAnswered(application)` gibi tek satırla çağırır. Kimin bildirileceği kararı orada: atamaya cevap **işverene**, uyuşmazlık **karşı tarafa**, iptal **iptal etmeyene**, çözüm ve ödeme yapılmadığı için düşen atama **iki tarafa**, değerlendirme **hakkında yazılan kişiye** gider.

**Değerlendirme bildirimi `ReviewManager` içinde, rozet değerlendirmesinden sonra.** Kaydedilmemiş ya da reddedilmiş bir değerlendirme kimseye bildirilmez — çağrı akışın sonunda, `ConflictException` atılan yolların ötesinde. Yazan kişiye bildirim gitmez, yalnızca hakkında yazılana. `ReviewRulesTest` ikisini de sabitliyor. Her gönderim `try/catch` içinde — bildirim teslim edilemese de iş akışı düşmez, iş alarmındaki desenin aynısı.

**Kategoriler yeniden dağıtıldı.** Önceden on üç gönderim noktasının onu `ACCOUNT_SECURITY` idi; şirket daveti ve şirket oluşturma da öyle işaretliydi. Kanal bazlı bildirim tercihi buna dayanacağı için yanlış kategori sonradan veri göçü demekti: kullanıcı pazarlama iletisini kapatmak istediğinde şifre sıfırlamayı da kapatmak zorunda kalırdı. `COMPANY_UPDATES` bu ayrım için eklendi.

**Kategori → kapatılabilirlik tablosu buradan kaldırıldı; tek kaynak `NotificationCategoryKind`.** Tercih katmanı (§2at) geldiğinde bu bilgi enum'a taşındı ve burada ikinci bir kopya bırakmak iki gerçeklik üretiyordu. Hangi kategorinin kapatılabildiği, varsayılanının ne olduğu ve ticari sayılıp sayılmadığı için **§2at'ye** ya da doğrudan enum'a bak.

**`AgreementType.COMMERCIAL_MESSAGE` rızası hâlâ ayrı bir şey.** Kayıtta toplanan bu onay `ConsentRecord`'a yazılıyor ve **kanal tercihine dönüşmüyor**: `MARKETINGS`/`NEWSLETTER` kayıt sırasında sözleşme onaylansa bile **kapalı** kalıyor ve kullanıcı bildirim tercihlerinden açana kadar gönderim yapılamıyor. Bu bilinçli — sözleşme onayı şemsiye izin, kanal bazlı açma ayrı ve açık bir eylem olmalı (§2at). Gönderim kapısı ve rıza kaydı §2at'de. İYS entegrasyonu yapılmadı — kapsamı `docs/research/iys-commercial-message-integration.md` içinde.

**SMS ve push tüketicileri yazıldı (Faz 28).** Öncesinde `RabbitMQConfig` üç kuyruk tanımlıyordu ama yalnızca `EmailConsumer` vardı; SMS ve push mesajları kuyruğa yazılıp orada birikiyordu. Sonuç: telefon doğrulama kodu, iş alarmı, şirket takibi bildirimi ve ilan iptali duyurusu kullanıcıya hiç ulaşmıyordu.

**`SmsConsumer` ticari iletiyi ayırt ediyor.** `NotificationCategory` `MARKETINGS` ya da `NEWSLETTER` ise `isIys=true` gidiyor, diğer her şeyde `false`. Bu bir detay değil: İYS işaretini yanlış göndermek yasal sorun. Bugün tek SMS üreticisi telefon doğrulama (`ACCOUNT_SECURITY`), yani pratikte hepsi işlemsel — ama bayrak kategoriden türediği için pazarlama SMS'i eklendiğinde kendiliğinden doğru gidiyor.

**Yalnızca `raw-sms-template` tanınıyor.** SMS tarafında Thymeleaf'ten geçen bir şey yok; gövde `textContent` parametresinden okunuyor. Tanınmayan şablon adı ya da boş gövde **gönderilmiyor**, `Notification` `FAILED` işaretleniyor — sessizce boş SMS atmaktansa kayda geçmesi tercih edildi.

**Push sağlayıcısı hâlâ yok, ama kuyruk artık tüketiliyor.** `PushService` arayüzü ve varsayılan `UnconfiguredPushService` var; ikincisi `ExternalServiceException` atıyor ve `PushConsumer` bildirimi `FAILED` işaretliyor. Kuyruğu tüketmeden bırakmak değil bu — bilinçli: mesajlar RabbitMQ'da süresiz biriktiğinde broker doluyor, üstelik üç hafta önceki bir bildirimi sonunda teslim etmenin değeri yok. Şimdi durum `Notification` satırında görünür. **Kalan iş tek sınıf:** Firebase kimlik bilgileri geldiğinde `PushService`'in FCM uygulaması.

**Bildirim akışı artık teslimat kaydı değil.** `Notification` hem teslimat kaydı (EMAIL/SMS/PUSH) hem uygulama içi bildirim (`IN_APP`) tutuyor; akış **yalnızca `IN_APP`** döndürüyor.

Önceki hâlinde akış bütün kanalları döndürüyordu ve `ActivityNotificationManager` olay başına iki kayıt yazıyordu — dahası `sendPushToUser` **cihaz başına** bir kayıt açıyor, yani iki telefonu olan kullanıcı tek bir olay için üç satır görüyordu. `unreadCount` de aynı sebeple katlanıyordu. Kanala göre süzmek en az değişiklik olurdu ama anlamı zorlardı: e-posta gönderiminin kaydını uygulama içi bildirim diye göstermek olurdu.

**`IN_APP` kaydı teslimattan bağımsız.** Kuyruğa girmez, doğrudan `SENT` yazılır; push sağlayıcısı çökse de, kullanıcının hiç cihazı olmasa da akış çalışır. Üçü de ayrı `try/catch` içinde — akış kaydı düşse bile e-posta ve push gider.

**`subject` başlık, `body` cümle.** Öncesinde push kaydında `subject` ilan başlığını, e-posta kaydında olayı taşıyordu; hangisi seçilse yarısı eksikti. `IN_APP` ikisini birden taşıyor: `subject` = ilan başlığı, `body` = ne olduğu. Gövde eskiden yalnızca `templateParameters` JSON'ının içindeydi ve DTO'ya çıkmıyordu; artık kendi kolonu var.

**Bildirim tıklanabilir.** `targetType` (`JOB`, `JOB_APPLICATION`, `JOB_ASSIGNMENT`, `CONVERSATION`, `USER`) ve `targetId` `ActivityNotificationManager` tarafından dolduruluyor. Hedefi olmayan bildirimde ikisi de null.

`POST /users/me/notifications/{id}/read` tek bildirimi işaretler; zaten okunmuşsa ya da başkasına aitse **sessizce geçer**, hata dönmez — akış ekranında hata yakalamak gereksiz. Toplu işaretleme yerinde duruyor ve o da yalnızca `IN_APP` satırlarına dokunuyor.

**Şirket uçları:** `GET/PATCH /companies/{id}`, `GET /companies/{id}/contacts`, `DELETE /companies/{id}/contacts/{userId}`. Düzenleme ve yetkili çıkarma yalnızca şirket **ADMIN**'ine açık, üye olmayan 404 alır. **Son ADMIN çıkarılamaz** — yoksa şirket yönetilemez hale gelirdi.

**İki rol var: `ADMIN` ve `STAFF`.** `MANAGER` kaldırıldı — kodda yalnızca `ADMIN` kontrol ediliyordu, yani MANAGER ile STAFF **tamamen aynı** yetkiye sahipti ve davet ekranında farkı olmayan üç seçenek görünüyordu. `28-07` migration'ı mevcut `MANAGER` satırlarını `STAFF`'a çeviriyor. Yetki sınırı: ADMIN şirket ayarları, logo, galeri, yetkili ekleme/çıkarma ve onay; STAFF ilan açar, kapatır, iptal eder ve başvuru cevaplar — ayarlara dokunamaz. Ayrım kodda zaten `requireCompanyAdmin` ile `isUserAContactOfCompany` arasında duruyordu, eksik olan yalnızca üçüncü rolün anlamsızlığıydı.

**`CompanyContact` artık `BaseEntity`.** Denetim kolonları ve soft delete kazandı; üyelikten çıkarma geçmişi silmiyor. `(company_id, user_id)` kısmi benzersiz indeksi eklendi, yani çıkarılan biri yeniden eklenebiliyor.

**Admin uçları `/admin/**` altında, tamamı `ROLE_ADMIN`.** `GET /admin/companies?approved=false` onay bekleyenleri listeler, `POST /admin/companies/{id}/approve` onaylar, `GET /admin/users?query=` kullanıcı arar.

**Şirket onayı bir açıktı.** `CompanyManager` şirketi `approved = false` yaratıyordu ve `setApproved(true)` **hiçbir yerde çağrılmıyordu**; hiçbir şirket onaylanamıyordu. Onay ucu bunu kapatıyor.

## 2t. Iyzico mutabakat tetiği

`approvePaymentSettlement` yazılmıştı ama **hiç çağrılmıyordu** — emanetteki para Iyzico tarafında alt üye işyerine hiç açılmıyordu. Faz 8B bunu bağladı:

- `releaseToWorker` → sağlayıcıda `approve`, yani işçi payı açılır.
- `refundEmployer` → sağlayıcıda `disapprove`, yani iade edilen işte işçi payı açılmaz.

**Çağrının nasıl yapıldığı Faz 11B'de değişti — mekanik artık §2y'de.** Kısaca: senkron çağrı yerine `settlement_tasks` kuyruğu. Buradaki iki bayrak yerinde duruyor: `iyzico.settlementEnabled` hiç kuyruğa girilmemesini, `iyzico.settlement-retry.enabled` kuyruğun işlenmemesini sağlar. Ödemede `providerTransactionId` yoksa da kuyruğa girilmez ve uyarı loglanır.

## 2u. İş tamamlama — mutlu yolun kapalı halkası

**Hiçbir kod atamayı `COMPLETED` yapmıyordu.** `AssignmentStatus.COMPLETED` yalnızca *okunuyordu*: değerlendirme bırakmanın şartı, `PaymentStateMachine.canRelease`'in şartı ve şimdi rozetin şartı. Yani hiçbir iş bitirilemiyor, hiçbir değerlendirme yazılamıyor ve **emanetteki para hiçbir zaman işçiye çıkamıyordu**. Domain kapalı bir halkaydı.

`POST /job-assignments/{id}/complete` bunu açıyor: **ilan sahibi** (ya da şirket yetkilisi) işi tamamlanmış işaretler, `actualCompletionDateTime` damgalanır ve işçinin rozetleri yeniden değerlendirilir. Yalnızca `CONFIRMED`/`IN_PROGRESS` durumundan geçilebilir.

**Cevapsız işverene karşı otomatik tamamlama var (Faz 11A).** `AssignmentAutoCompletionScheduler` saatte bir çalışır; işin `endDateTime`'ından `assignment.auto-completion.grace-days` (varsayılan 3) gün geçmiş, hâlâ `CONFIRMED`/`IN_PROGRESS` ve parası emanette olan atamaları kendiliğinden `COMPLETED` yapıp emaneti işçiye çıkarır. Yoksa işveren hiç onaylamayarak işçinin parasını süresiz tutabilirdi.

**Açık uyuşmazlık otomatik tamamlamayı durdurur** — aynı `disputeDao` kontrolü burada da var, yoksa zamanlayıcı uyuşmazlık kilidini arkadan dolanırdı.

**Her atama kendi transaction'ında işlenir.** Zamanlayıcı önce id listesini okur, sonra her birini ayrı çağırır ve tek tek `try/catch` eder; bir atamadaki hata partinin geri kalanını düşürmez. Adım idempotent: ikinci koşuda durum artık `COMPLETED` olduğu için atlanır.

**`Job.endDateTime` duvar saati olduğu için** karşılaştırma `assignment.auto-completion.zone-id` üzerinden yapılır (§2h ile aynı gerekçe).

**Zamanlayıcı tek örnek varsayar.** Birden fazla örnek çalıştırıldığında ikisi de aynı partiyi görür; adımlar idempotent olduğu için sonuç bozulmaz ama sağlayıcıya gereksiz çağrı gider. Çok örnekli kuruluma geçilirse `ShedLock` benzeri bir kilit gerekir — hız sınırıyla (§2j) aynı kısıt.

Testlerde kapalı: `assignment.auto-completion.enabled=false`.

## 2u2. Ödenmemiş atamanın düşürülmesi

**Ödeme yapılmadan işe başlanamıyor** (§2k'daki emanet kapısı check-in ve tamamlamada zorunlu), ama `CONFIRMED` + ödenmemiş bir atama süresiz bekliyordu. İşçi vardiyayı boş yere tutuyor, işveren hiç ödemeyerek maliyetsiz biçimde takvimini işgal edebiliyordu.

`UnpaidAssignmentExpiryScheduler` saatte bir çalışır; işin `startDateTime`'ına `assignment.unpaid-expiry.hours-before-start` (varsayılan 6) saatten az kalmış, hâlâ `CONFIRMED` ve parası emanete girmemiş atamaları düşürür. `paymentStatus` `PENDING_COLLECTION`, `FAILED` ya da hiç başlatılmamışsa (null) kapsama girer.

**Yalnızca `CONFIRMED` kapsanır.** `PENDING_CONFIRMATION` bir teklif — işçi henüz kabul etmediği için kimsenin takvimi bloke değil, ödeme beklemek de anlamsız. Teklif kendi başına eskir.

**İptal kaydı açılır, para hareketi olmaz.** `CancellationService.expireUnpaidAssignment` bir `Cancellation` yazar: taraf `EMPLOYER` (kusur onda), `cancelledBy` sistem kullanıcısı, ceza ve iade **sıfır**. Kayıt açılmasının nedeni denetim: "atamam neden düştü" sorusunun cevabı bir yerde durmalı, `CANCELLED` statüsü tek başına söylemiyor. `CancellationPolicy` hiç çalıştırılmaz — emanette para yokken hesaplanacak bir şey yok.

**İki tarafa da bildirim gider**, farklı metinlerle: işçiye "işveren ödemeyi tamamlamadı", işverene "ödeme tamamlanmadığı için atama düştü". `assignmentCancelled` yalnızca karşı tarafa gider (§2s), burada kusurlu taraf da bilgilendirilmeli.

**Zamanlayıcı deseni otomatik tamamlamayla aynı** (§2u): id listesi önce okunur, her atama ayrı çağrıda ve `try/catch` içinde işlenir, adım idempotenttir (`existsByJobAssignmentId` ikinci koşuyu durdurur). Tek örnek varsayımı burada da geçerli (§7, Faz 11D).

**Eşik canlıda değiştirilebilir.** `assignment.unpaid-expiry.hours-before-start` `SettingKeys` içinde tanımlı, yani `PUT /admin/settings` ile deploy'suz ayarlanır (§2ac). Testlerde kapalı: `assignment.unpaid-expiry.enabled=false`.

## 2v. Rozetler

Yapı frontend'in önerisiyle kuruldu, **Faz 12B'de kural tanımı da veritabanına indi.**

**Rozet `RatingSummary` gibi materyalize.** Okuma anında hesaplanmıyor; `user_badges` kazanılanı ve **kazanılma tarihini** tutuyor. Kriterler birden çok agrega okuyor, profil her açıldığında bunu yapmak 3-4 sorgu demekti.

**Kazanılan rozet geri alınmaz.** `BadgeService.evaluate` yalnızca *verir*, hiçbir yerde silmez. Ürün kararı: eşik yanlış düşük seçilirse maliyeti "çok kişide var" olur; geri alınabilir olsaydı "insanlar rozetini kaybetti" olurdu ve o geri dönüşü olmayan bir güven hasarı. `BadgeRulesTest` bu monotonluğu sabitliyor. Bir rozet **pasifleştirilebilir** (`is_active = false`) — o andan sonra kimseye verilmez ama sahipleri korur.

**Rozet tanımının tamamı `badges` tablosunda.** Kod, ad, açıklama, aile, kademe, sıra, görsel, kural tipi ve eşikler — hepsi satır. `BadgeCriterion` enum'u **kaldırıldı**, `BadgeProperties` konfigürasyonu **kaldırıldı**.

| Ne | Nerede | Yeni rozet eklemek |
|---|---|---|
| Kod, ad, açıklama, sıra, görsel | `badges` satırı | veri |
| Eşik sayıları | `badges` satırı | veri |
| Kural **tipi** | `badges.rule_type` | veri |
| Kural **çeşidi** (yeni bir hesap) | `BadgeRuleType` + `BadgeManager` | kod |

Yani mevcut bir kural tipini kullanan yeni rozet **deploy istemez**: `POST /admin/badges` + `PUT /admin/badges/{id}/icon`. Yeni bir *hesap çeşidi* (ör. dakiklik oranı) kod ister — genel bir kural motoru **bilerek yazılmadı**, "ortalama ≥ 4,5 **ve** yorum ≥ 20" gibi bileşik koşullar için gereken şey odur ve bakımı ağırdır. Bugün 3 kural tipi var.

**Görsel artık backend'de.** `badges.icon_id` → `media`, R2'ye yükleniyor, yanıtta `iconUrl` dönüyor. Öncesinde yalnızca `code` dönüyordu ve uygulama koddan gömülü PNG'ye eşliyordu — yeni rozet mobil sürüm gerektiriyordu. **`code` yanıtta kaldı**, istemci bildiği rozetler için yerel asset'i tercih edebilir, bilmediği için URL'e düşer.

**Eşiği olmayan rozet verilmez.** `minCount` null ise kural `false` döner ve uyarı loglanır; ayrıca `POST/PUT /admin/badges` eşiksiz kuralı **400 ile reddeder** — eksik konfigürasyonda herkese rozet dağıtmaktansa hiç dağıtmamak.

**Ortalamaya dayalı her kural minimum örneklem ister.** `AVERAGE_RATING_WITH_MINIMUM_REVIEWS` hem `minAverageRating` hem `minReviewCount` olmadan kaydedilemez; yoksa tek 5 yıldızlı yorumu olan kullanıcı 200 işi olanın önüne geçerdi.

**Değerlendirme üç yerde tetikleniyor:** değerlendirme bırakıldığında, atama tamamlandığında, kimlik doğrulandığında. Frontend dördüncü olarak "uyuşmazlık aleyhe sonuçlandı" önerdi ama **monoton bir sistemde o tetikleyicinin etkisi olamaz** — yalnızca rozet geri alabilirdi. Bağlanmadı.

**Başlangıç verisi tek aile: `GEZGIN` (`SILVER` 5 iş / `GOLD` 25 iş).** Bunlar yer tutucu; artık değiştirmek için `PUT /admin/badges/{id}` yeterli.

## 2x. Iyzico protokol denetimi — dokümanla karşılaştırma

Entegrasyon `docs.iyzico.com` referansına karşı satır satır denetlendi. **Dört tanesi ödeme akışını tümüyle çalışmaz hâlde bırakıyordu.**

**Sorgulama ucunun yolu yanlıştı.** Kod `/payment/checkout-form/retrieve` çağırıyordu, dokümandaki yol `/payment/iyzipos/checkoutform/auth/ecom/detail`. İmza `randomKey + uriPath + body` üzerinden üretildiği için **yol yanlış olunca imza da yanlış oluyor** — yani istek hem yanlış adrese gidiyor hem geçersiz imzayla. `completeFromCallback` her seferinde catch bloğuna düşüp ödemeyi `FAILED` işaretliyordu. Emanete hiçbir zaman para girmedi; §2y'nin "sandbox'ta uçtan uca doğrulanmadı" notunun altında bu yatıyormuş.

**`x-iyzi-rnd` başlığı hiç gönderilmiyordu.** IYZWSv2 `randomKey`'i iki yerde ister: imzanın içinde ve bu başlıkta. Yalnızca base64 auth string'ine konuyordu.

**Uç yolları artık `IyzicoEndpoints` içinde sabit** ve `IyzicoProtocolTest` her birini dokümandaki değere karşı doğruluyor. Düz metin olarak çağrı yerlerine dağılmış olmaları hatanın üç ay fark edilmemesinin sebebiydi.

**İade parayı geri göndermiyordu.** `refundEmployer` yalnızca `DISAPPROVE` kuyruğa alıyordu. Iyzico'da disapprove "alt üyeye aktarma" demektir — **para işverenin kartına dönmez, bizde kalır.** İşveren cezasız pencerede iptal ettiğinde tam iade hak ediyor (§2p) ama tek kuruş geri gitmiyordu.

Artık `SettlementOperation.REFUND` var ve `/payment/refund` çağrılıyor (`paymentTransactionId` + `price`, kısmi iade destekli):

| Durum | Kuyruğa giren | Neden |
|---|---|---|
| Tam iade | `REFUND` | satır tümüyle ters çevrilir, disapprove'a gerek yok |
| Kısmi iade (geç iptal cezası) | `REFUND` + `DISAPPROVE` | kalan tutar bizde kalmalı, işçi payı aktarılmamalı |
| İşçiye çıkış | `APPROVE` | değişmedi |

`settlement_tasks.amount` bu yüzden eklendi — `REFUND` dışındaki işlemlerde null.

**Tutar doğrulanmıyordu.** Callback sonrası sağlayıcının döndürdüğü `paidPrice` ve `currency` bizim `totalCharged` ile karşılaştırılmıyordu; sağlayıcı ne derse emanet açılıyordu. Artık uyuşmazlıkta ödeme `FAILED` olur ve log tutarları yazar.

**`fraudStatus` yok sayılıyordu.** Iyzico `0` derse ödeme dolandırıcılık incelemesindedir ve **sonradan geri çevrilebilir**; doküman "yalnızca 1 iken ürünü gönderin" diyor. Öncesinde `0` da `1` gibi işlenip emanet açılıyor, işçi işe başlayabiliyordu. Artık `1` → emanet, `0` → yeni `PaymentAttemptStatus.IN_REVIEW` (emanet açılmaz), `-1` → `FAILED`.

**`IN_REVIEW`'dan çıkış henüz otomatik değil.** Iyzico incelemeyi bitirince webhook gönderir; o tüketici yazılmadı. Bugün ödeme incelemede kalırsa işveren yeniden denemek zorunda. Kayıt `providerTransactionId` ile duruyor, yani el ile çözülebilir.

**Para artık `double` değil.** `price`, `paidPrice`, `subMerchantPrice` `BigDecimal`'e çevrildi — §2e'nin kuralı buraya hiç uygulanmamıştı, oysa iyzico `price` ile sepet kalemlerinin toplamının **tam** eşitliğini arıyor.

**Hata gövdesi artık okunuyor.** `RestTemplate` 4xx'te exception fırlattığı için iyzico'nun `errorCode`/`errorMessage` gövdesi hiç ayrıştırılmıyordu; log'da yalnızca "400 Bad Request" görünüyordu. `HttpStatusCodeException` yakalanıp gövde okunuyor ve gerçek hata kodu loglanıyor.

**Exception yüzeyi §2ag'ye uyduruldu.** `RuntimeException` yerine `ExternalServiceException` (502). Ayrıca `retrieveCheckoutFormResult` **artık reddedilen ödemede exception atmıyor** — sonucu döndürüyor, kararı `PaymentManager` veriyor. Öncesinde "kart reddedildi" ile "sağlayıcıya ulaşılamadı" aynı catch'e düşüyordu.

**`conversationId` artık korelasyon taşıyor.** Approve/disapprove/refund çağrılarında rastgele UUID üretiliyordu; artık ödemenin kendi `conversationId`'si gidiyor, yani iyzico panelindeki kayıt bizdeki `Payment` ile eşleşiyor.

**Hâlâ sandbox'ta uçtan uca doğrulanmadı.** Yukarıdakiler dokümana karşı doğru; gerçek bir sandbox işlemiyle teyit edilmesi gereken tek şey kısmi iade + disapprove ikilisinin sıralamasıdır. `iyzico.settlementEnabled=false` ile kuyruk tümüyle kapalı tutulabilir.

## 2x2. 3DS doğrulaması — `mdStatus` kapısı

**`mdStatus` okunmuyordu ve 3DS doğrulaması geçmemiş ödeme emanet açıyordu.** `paymentStatus = SUCCESS` "sağlayıcı işlemi tamamladı" demek; kart sahibinin bankasında doğrulandığını **söylemiyor**. İkisi ayrışabilir ve iyzico bunu üretecek bir test kartı yayımlıyor: `4131111111111117` → `Success but mdStatus is 0` (`/ek-bilgiler/test-kartlari`). O ödeme bugüne kadar emanete giriyor, işçi işe başlayabiliyor, ters ibraz riski tümüyle bizde kalıyordu.

Artık `completeFromCallback` yalnız `mdStatus = 1` iken emanet açıyor.

**Değerlerin anlamı iyzico'nun kendi dokümantasyonundan** (`/odeme-metotlari/api/3ds/3ds-entegrasyonu` ve İngilizce eşi `/en/payment-methods/api/3ds/3ds-implementation`). Tablo başlığı bilerek "başarısızlık senaryoları" — `1` tabloda hiç yok:

| `mdStatus` | iyzico'nun tanımı |
|---|---|
| `1` | başarılı; tabloda değil, şemada `1: Başarılı` |
| `0` | 3-D Secure imzası geçersiz veya doğrulama |
| `-1` | `0` ile aynı; QNB Finansbank'a özel |
| `2` | kart sahibi veya bankası sisteme kayıtlı değil |
| `3` | kartın bankası sisteme kayıtlı değil |
| `4` | doğrulama denemesi; kart sahibi sonra kayıt olmayı seçmiş |
| `5` | doğrulama yapılamıyor |
| `6` | 3-D Secure hatası |
| `7` | sistem hatası |
| `8` | bilinmeyen kart no |

Aynı sayfaların `/payment/v2/3dsecure/auth` şeması bunu tek cümleyle bağlıyor: `1: Başarılı`, `0,2-8: Başarısız`. `2`, `3` ve `4` bazı hazırlayıcıların "attempted" diye kabul ettiği hâller ama **iyzico kendi belgesinde üçünü de başarısız sayıyor**; biz de öyle sayıyoruz.

**Kapı beyaz liste, kara liste değil.** `mdStatus == 1` yazılı; `0,2..8` dışlansaydı `-1` (yalnız Türkçe tabloda ve OpenAPI `enum`'unda geçiyor, "başarısız" aralığı yazısında geçmiyor) sessizce başarı sayılırdı. Yarın listeye bir değer eklenirse de beyaz liste kendiliğinden güvenli tarafta kalır.

**Alan yoksa ödeme reddediliyor — bilinçli olarak kapalı düşülüyor.** iyzico dört ayrı sayfada "`mdStatus` parametresi sadece 3DS işlemlerinde görünecektir" diyor. Yani alanın yokluğunun iki okuması var: (a) işlem 3DS'siz geçti, (b) yanıt beklediğimiz gibi değil. İkisini ayırt edemiyoruz ve ayırt edemediğimiz durumda **parayı korumaktan yana yanılıyoruz** — §2y2'deki ortam kapısıyla aynı yön. Alan yokken başarı saymak, tam olarak kapatmaya çalıştığımız açığın yeniden açılması olurdu: doğrulanmamış bir işlem emanete girer.

Bedeli açıkça yazılı olsun: **Checkout Form bir gün 3DS'siz bir ödeme geçirirse onu da reddederiz.** iyzico'nun CF'nin 3DS'i hangi kurala göre seçtiğini yazmadığı `docs/runbooks/payments.md` §6.3'te duruyor. Böyle bir işlemde sağlayıcı tarafında para çekilmiş olabilir; operasyonel karşılığı tutar uyuşmazlığıyla aynıdır — panelden `conversation_id` ile elle iade (runbook §1). Canlıya geçişte doğrulanacak ilk şey budur; kalıcı çözüm istenirse `forceThreeDS` (§6.3'te belgelenmemiş olarak işaretli) iyzico'dan yazılı teyit alındıktan sonra gönderilir.

**Bir çelişki not edilmeli.** CF sorgulama yanıtının OpenAPI şeması (`/odeme-metotlari/odeme-formu/cf-entegrasyonu/cf-sorgulama`) `mdStatus`'u **saymıyor**; ama aynı doküman setindeki CF örnek yanıtı (`.../cf-ornek-entegrasyon`) `"mdStatus": 1` içeriyor ve resmî `iyzipay-java` SDK'sında `CheckoutForm`, `PaymentResource`'tan `getMdStatus()`'u miras alıyor. İkisi de iyzico. Alanın geleceğini varsayıyoruz ama şema garanti etmediği için okuma null'a dayanıklı ve karar fail-closed.

**Kapı dolandırıcılık dalından önce.** `mdStatus != 1` ve `fraudStatus == 0` birlikte gelirse ödeme `IN_REVIEW`'da beklemez, `FAILED` olur: `IN_REVIEW`'dan çıkış zaten otomatik değil ve doğrulanmamış bir işlemi "belki sonra onaylanır" diye tutmanın anlamı yok.

**Ret sebebi mesaj anahtarı, ham değer değil.** `payments.failure_reason` istemciye `GET /job-assignments/{id}/payments` ile aynen dönüyor (`PaymentAttemptDto`), yani sağlayıcı detayı için uygun bir yer değil. Kayda `{payment.3ds.verification.failed}` yazılıyor; sayısal `mdStatus` yalnız log satırında (`Payment {} rejected: 3-D Secure verification did not succeed, mdStatus={}`). Operatör için korelasyon yine `conversation_id`.

**Reddedilen deneme sıradan bir başarısız denemedir.** `payments.status = FAILED`, `completed_at` damgalı, atama `PENDING_COLLECTION`/`FAILED` → `FAILED`; emanet açılmaz, atamaya kırılım yazılmaz, `JobAssignmentDao`'ya hiç dokunulmaz. İşveren yeniden deneyebilir. Zaten `SUCCEEDED` olan ödemede idempotanlık kısa devresi önce çalıştığı için ikinci callback sağlayıcıya hiç gitmez (§2k); reddedilen denemede ise kısa devre yok, tekrar sorulur ve sonuç yine `FAILED` olur.

**Sandbox'ta doğrulanmadı.** `4131111111111117` ile gerçek bir sandbox işlemi yapılmadı; kural iyzico dokümantasyonuna ve sahte sağlayıcı yanıtlarına karşı sabitlendi (`PaymentCallbackTest`, `IyzicoProtocolTest`).

## 2y. Mutabakat outbox'ı

Faz 8B'de sağlayıcı çağrısı `PayoutManager` içinden **senkron** yapılıyordu ve hata durumunda tüm işlem geri alınıyordu — yani `Payout` kaydı da yazılmıyordu ve **kimse yeniden denemiyordu**. Iyzico bir dakikalığına düşse para emanette kilitli kalırdı.

Artık `PayoutManager` sağlayıcıyı hiç çağırmıyor; `SettlementService.enqueue` ile `settlement_tasks` tablosuna bir kayıt bırakıyor. `SettlementRetryScheduler` beş dakikada bir vadesi gelenleri işliyor.

**Kuyruk ödeme+işlem başına tekil.** `(payment_id, operation)` kısmi benzersiz indeksi var; aynı ödeme için ikinci bir `APPROVE` açılmıyor, `enqueue` mevcut kaydı döndürüyor. Yani release iki kez çağrılsa da sağlayıcıya iki kez gidilmez.

**Üstel geri çekilme, tavanlı.** `initial-backoff` (1 dk) her denemede ikiye katlanır, `max-backoff` (6 saat) ile sınırlanır. `max-attempts` (8) aşılınca görev `FAILED`'a düşer ve **sessizce durur** — sonsuza kadar sağlayıcıyı dövmez. `FAILED` görevler elle incelenmek üzere tabloda kalır.

**`Payout` ve `Refund` durumunu sağlayıcı yanıtı belirler.** Release anında `Payout` `PENDING` yazılır, görev başarılı olduğunda `PROCESSING`'e geçer. İade tarafında `DISAPPROVE` görevi başarılı olduğunda `Refund` `COMPLETED` olur ve `completedAt` damgalanır. **Görev deneme limitini aştığında ilgili kayıt `FAILED` işaretlenir.**

Bir süre bu yarım kaldı: `markPayoutProcessing` yalnızca `APPROVE` için çalışıyordu, `Refund` sonsuza kadar `PENDING`'de duruyordu ve görev `FAILED`'a düştüğünde `Payout` `PROCESSING`'de kalıyordu. Yani operatör "yolda", "ulaştı" ve "kalıcı olarak başarısız" hâllerini ayırt edemiyordu.

**`PayoutStatus.COMPLETED` bilerek kullanılmıyor.** Sağlayıcı `approve` çağrısı yalnızca talimatın kabul edildiğini söyler; paranın işçinin banka hesabına geçtiğini söyleyen bir geri bildirim yok. O geçiş Iyzico ödeme raporuyla mutabakat gerektiriyor, Faz 11B'nin kalan parçası.

**Bu entegrasyon hâlâ sandbox'ta uçtan uca doğrulanmadı** (protokol denetimi §2x'te yapıldı, dört blocker düzeltildi). `iyzico.settlementEnabled=false` iken hiç kuyruğa girilmez, `iyzico.settlement-retry.enabled=false` iken kuyruk işlenmez — ikisi de prod'a kademeli geçiş için.

## 2y2. Sandbox/canlı ayrımı — açılış kapısı

Sandbox ile canlı arasındaki tek fark `IYZICO_BASE_URL` ve anahtar çiftiydi;
ikisi de serbest metindi. Staging'e yanlışlıkla canlı anahtar yüklemek hiçbir
şey kırmıyordu — test amaçlı bir ilan gerçek parayla ödenirdi ve bunu fark
etmenin yolu ekstreye bakmaktı.

`IyzicoProperties` artık ortamı biliyor. `iyzico.deployment`, `docs/environments.md`
sözleşmesindeki `SERVICE_ENVIRONMENT` değerine bağlı; `@AssertTrue` ikisinin
eşleşmesini açılış şartı yapıyor:

| `SERVICE_ENVIRONMENT` | İzin verilen sağlayıcı ortamı |
|---|---|
| `production` | yalnız `https://api.iyzipay.com` |
| `staging` | yalnız `https://sandbox-api.iyzipay.com` |
| `local` / tanımsız | sandbox veya loopback (`localhost`, `127.0.0.1`, `::1`) |

Dört karar açıklama istiyor:

**Yeni bir değişken uydurulmadı.** Ortam adı zaten `SERVICE_ENVIRONMENT`'ta
duruyordu ve `docs/environments.md` onu staging zorunluluğu olarak yazıyor.
İkinci bir "hangi ortamdayız" değişkeni açmak ikisinin ayrışabileceği bir
gelecek üretirdi.

**Tanınmayan host her ortamda reddediliyor.** Yalnız "canlı mı sandbox mı" diye
bakmak yetmez; `https://api.iyzipay.com.saldirgan.test` iki listeye de girmez ve
sessizce kabul edilirdi. Host tam eşleşme, şema `https` zorunlu. Loopback bunun
istisnası: `IyzicoProtocolTest` sahte bir HTTP sunucusuna konuşuyor ve testler
`local` sayılıyor.

**Tanınmayan ortam adı açılışı düşürüyor.** `SERVICE_ENVIRONMENT=prod` yazılırsa
enum bağlanamaz ve uygulama açılmaz. Alternatifi — bilinmeyeni "production
değil" saymak — production'ı sessizce sandbox'a düşürürdü.

**Tanımsız ortam `local` sayılıyor, `production` değil.** Değişken hiç
verilmezse en dar hak kazanılır: canlı `baseUrl` reddedilir. Yanılma yönü her
zaman parayı korumaktan yana.

`IyzicoProperties.baseUrl` alanının Java varsayılanı da sandbox; yapılandırma
tümüyle kaybolsa bile canlıya düşülmez. Kuralı `IyzicoEnvironmentSplitTest`
on iki kuralla sabitliyor. Operasyon tarafı `docs/runbooks/payments.md`'de.

## 2z. Gerçek zamanlı katman

**WebSocket/STOMP seçildi çünkü yazıyor göstergesi istendi.** Yalnızca "mesaj anında gelsin" isteseydi push + açıkken yoklama yeterdi ve hiç yeni altyapı gerekmezdi; yazıyor göstergesi ve presence çift yönlü kanal ister.

**Kimlik doğrulama `CONNECT` karesinde.** `StompAuthChannelInterceptor` `Authorization: Bearer …` başlığını okur, `JwtService` ile oturumu bulur, `SessionService.findActiveSessionById` ile aktifliğini doğrular ve `UserPrincipal`'ı STOMP kullanıcısı yapar. HTTP tarafındaki `SessionAuthFilter` ile aynı garantiler: kapatılmış oturumun soketi de açılmaz.

**Kanallar:**
- `/topic/conversations/{id}` — `MESSAGE`, `TYPING`, `READ` olayları. Tek zarf: `RealtimeEventDto`.
- `/user/queue/messages` — kullanıcıya özel akış, yalnızca `MESSAGE`.
- `/topic/presence/{userId}` — o kullanıcının `PRESENCE` olayları.
- `/app/conversations/{id}/typing` — istemcinin yazıyor bildirimi. **Sunucu tarafta yetki kontrolü var** (`assertParticipant`), yoksa yabancı biri herhangi bir konuşmaya olay basabilirdi.

**Sohbet listesi için kişisel kuyruk var.** Yeni mesaj hem konuşma topic'ine hem **her iki tarafın** `/user/queue/messages` kuyruğuna gidiyor. Öncesinde yalnızca konuşma topic'i vardı; sohbet listesini canlı tutmak için istemcinin 20 ayrı topic'e abone olması gerekiyordu, o yüzden liste ancak elle tazelenince güncelleniyordu. Kopya iki tarafa da gider — gönderenin *ikinci cihazındaki* liste de güncellensin diye. Sohbeti açık olan istemci aynı olayı iki kanaldan alır, `message.id` ile tekilleştirmesi gerekir.

**Kullanıcı hedefi id ile anahtarlanıyor, e-posta ile değil.** `UserPrincipal` bir `UserDetails` ve `getUsername()` e-posta döndürüyor; `Authentication.getName()` de onu kullandığı için `convertAndSendToUser(userId, …)` hiçbir zaman eşleşmezdi. `RealtimeAuthentication` yalnızca `getName()`'i ezip kullanıcı id'sini döndürüyor. E-posta değişebilir bir tanımlayıcı, yönlendirmeyi ona bağlamak istenmedi.

**Presence kullanıcı başına topic'ten yayınlanıyor.** Öncesinde tek bir `/topic/presence` vardı ve herkese açıktı: her kullanıcının çevrimiçi/çevrimdışı değişimi bağlı olan herkese gidiyordu. Ölçeklenmemesi bir yana, asıl sorun **davranış izleme** idi — hiç etkileşime girmediğin bir işçinin ne zaman çevrimiçi olduğunu izleyebiliyordun. Frontend'in önerisi kullanıcı başına topic'ti; ona **abonelik yetkisi** eklendi (aşağıda §2ai).

**Okundu bilgisi de kişisel kuyruğa gidiyor.** `conversationRead` hem konuşma topic'ine hem iki tarafın kuyruğuna yazıyor; kullanıcı sohbeti telefonunda okuduğunda tabletindeki listede rozet düşüyor. Aksi hâlde "okumadığım mesaj var" yanılgısı kalıyordu.

**Yayın hiçbir zaman iş akışını düşürmez.** `RealtimePublisher` her gönderimi `try/catch` eder ve yalnızca uyarı loglar; soket katmanı çökse bile mesaj veritabanına yazılmış olur ve REST'ten okunur. Gerçek zamanlı katman **iyileştirme, tek kaynak değil**.

**Presence bellekte, kullanıcı başına bağlantı kümesi olarak.** İki cihazdan bağlanan kullanıcı birinden çıkınca çevrimiçi kalır; son bağlantı kapanınca çevrimdışı olur ve `lastSeenAt` korunur. Presence olayı yalnızca **durum gerçekten değiştiğinde** yayınlanır, her bağlantıda değil.

**Basit broker kullanılıyor, tek örnek varsayımıyla.** `enableSimpleBroker` uygulama belleğinde çalışır. Çok örnekli kuruluma geçilirse RabbitMQ **zaten kurulu** olduğu için Spring'in STOMP relay'ine (`enableStompBrokerRelay`) geçmek tek konfigürasyon değişikliği — presence haritası da o noktada Redis'e taşınmalı (Faz 11D).

`realtime.enabled=false` ile yayın tümüyle susturulabilir; uçlar ayakta kalır, yalnızca soket trafiği kesilir.

## 2ab. Depolama — S3, matmuhbackend deseni

**R2'ye özel `R2StorageService` kaldırıldı.** Yerine `StorageService` arayüzü ve `storage.type` ile seçilen iki uygulama geldi: `S3StorageService` (AWS S3 ya da S3 uyumlu herhangi bir servis) ve `LocalStorageService` (**varsayılan**, diske yazar). Desen matmuhbackend'den alındı.

**Testler artık depolama konfigürasyonu istemiyor.** Eskiden `R2ClientConfig` açılışta `URI.create(endpoint)` çağırdığı için `r2.*` değerleri olmadan context düşüyordu; şimdi `storage.type=local` varsayılanı sayesinde hiçbir bulut ayarı gerekmiyor. Test kaynaklarından beş satır silindi.

**Asıl kazanç gizlilik.** Öncesinde her nesne `r2.publicUrl + "/" + key` ile **kalıcı ve herkese açık** bir adres alıyordu — özgeçmişler ve kimlik doğrulama belgeleri dahil. Kimlik belgesi fotoğrafı özel nitelikli kişisel veridir; tahmin edilemez bir URL yeterli koruma değildir. Artık üç önek üç ayrı davranış:

| Önek | İçerik | Erişim |
|---|---|---|
| `images/` | profil fotoğrafı, logo, rozet görseli | CDN üzerinden doğrudan, herkese açık |
| `public/` | giriş istemeyen belgeler (sözleşme metinleri) | nesne özel kalır, `/uploads/public/**` kısa ömürlü imzalı adrese yönlendirir |
| `files/` | özgeçmiş, kimlik belgesi | nesne özel kalır, `/uploads/files/**` **kimlik doğrulaması ister**, sonra imzalı adrese yönlendirir |

İmzalı adresin ömrü `storage.signed-url-minutes` (varsayılan 3 dk). Yönlendirme uçları `StorageDownloadController`'da; tanınmayan önek 400 döner, yani depo anahtarı uydurup rastgele nesne çekilemez.

**Özel dosyada kimlik doğrulaması yetmez, sahiplik de kontrol edilir.** `/uploads/files/**` `SecurityConfig`'te `hasAnyRole("USER","ADMIN")` — yani **giriş yapmış herkes**. Tek koruma anahtarın tahmin edilemez olmasıydı; §2ab'nin "tahmin edilemez URL yeterli koruma değildir" ilkesiyle çelişiyordu. `CvService.assertCanDownload` eklendi: `media.createdBy` istek atanla eşleşmiyorsa ve kişi ADMIN değilse **404** döner (403 değil — dosyanın varlığı da sızmasın diye). Görsellerde bu kontrol yok, olmamalı da; onlar zaten CDN'den herkese açık.

Yan kazanç: `Media` soft delete edildiğinde `@SQLRestriction` yüzünden `findByFileUrl` onu bulamıyor, yani **silinen özgeçmiş artık indirilemiyor.** Öncesinde satır silinse bile anahtarı bilen indirebiliyordu. Depodaki nesne hâlâ duruyor — kalıcı silme Faz 9B'ye bağlı.

**Bugün `files/` altında yalnızca özgeçmiş var.** Kimlik doğrulaması KPS üzerinden ad/soyad/TCKN/doğum yılı ile yapılıyor, belge yüklenmiyor. §2ab'de kimlik belgesi kategorisi tanımlı ama henüz o yola yazan bir uç yok.

**`/uploads/**` yönlendirme yapmak zorunda değil.** İlk sürümde üç uç da `temporaryUrl`/`urlFor` sonucuna 302 atıyordu. `STORAGE_PUBLIC_URL` boşken `urlFor("images/x.png")` zaten `{apiUrl}/uploads/images/x.png` üretiyor — yani uç **kendine** yönlendiriyor ve tarayıcı sonsuz döngüye giriyordu. Aynısı `storage.type=local` için üç ucun üçünde birden geçerliydi: `LocalStorageService.temporaryUrl` de aynı adresi döndürüyor, `/uploads/**` için statik kaynak işleyicisi de yok. Yani yerel depolama hiçbir zaman dosya servis edememişti; R2/S3 + CDN ile çalışıldığı için fark edilmemişti.

Artık karar depoya ait: `StorageService.supportsSignedUrls()` true ise imzalı adrese 302, değilse `readFile` ile **baytlar akıtılır**. İçerik tipi anahtarın uzantısından (`MediaTypeFactory`) türetilir. `StorageDownloadRulesTest` ucun kendi `/uploads/` yoluna yönlendirmediğini sabitliyor.

**Anahtar orijinal adı taşımaz.** `StorageKeys.newKey` `<önek><uuid>.<uzantı>` üretir. Öncesinde `images/<uuid>-<dosya adı>` yazılıyordu, yani "kimlik_on_yuz.jpg" gibi bir ad URL'e sızıyordu. Uzantı beyaz listeye tabi: 10 karakterden uzun ya da alfanümerik olmayan uzantı düşürülür (`kotu.pdf%00exe` → uzantısız).

**Orijinal ad yalnızca `Content-Disposition`'da yaşıyor**, RFC 5987 biçiminde: hem ASCII'ye indirgenmiş `filename` hem UTF-8 kodlu `filename*`. Yani "Özgeçmiş.pdf" indirilirken adını koruyor.

**`StorageKeys.sanitize` yol geçişine kapalı.** Türkçe harfler ASCII'ye katlanıyor (`â`, `î`, `û` dahil), art arda noktalar tek noktaya iniyor ve baştaki nokta/alt çizgiler siliniyor — `../../etc/passwd` girdisi `etc_passwd`'ye düşüyor. Bugün yalnızca indirme adı için kullanılıyor ama ileride yol üretiminde kullanılırsa güvenli olsun diye; `StorageKeysTest` bunu sabitliyor.

**Yükleme doğrulaması tek yerde: `UploadValidator`.** Uzantı beyaz listesi ve boyut sınırı klasör türüne göre `upload.*` altından geliyor. Öncesinde `ImageManager` 10 MB'ı elle kontrol ediyor, `CvManager` kendi MIME listesini tutuyordu — ikisi de artık aynı yoldan geçiyor. **MIME yerine uzantı** kontrol ediliyor: `Content-Type` istemciden gelir ve sahtelenebilir.

**Kimlik bilgisi zorunlu değil.** `S3ClientConfig` statik anahtar verilmemişse `DefaultCredentialsProvider`'a düşer, yani EC2/ECS üzerinde IAM rolü ile anahtarsız çalışır. `storage.endpoint` verilirse S3 uyumlu servisler (R2 dahil) aynı kodla çalışmaya devam eder — geri dönüş yolu açık.

**Mevcut veriler taşınmalı.** Kayıtlardaki `file_url` değerleri anahtarı tutuyor ve önekler (`images/`, `files/`) korundu, yani şema değişikliği gerekmiyor; ancak **nesnelerin R2'den S3'e kopyalanması gerekiyor.** Bu bir kod işi değil, tek seferlik bir `aws s3 sync`.

## 2ac. Çalışma zamanı ayarları

Politika sayıları artık deploy istemiyor. `app_settings` tablosu ve `GET/PUT /admin/settings[/{key}]` ile üç değer canlıda değiştirilebiliyor:

| Anahtar | Ne yapar | Varsayılan |
|---|---|---|
| `assignment.auto-completion.grace-days` | iş bitiminden kaç gün sonra otomatik tamamlansın | 3 |
| `cancellation.free-window-hours` | işverenin cezasız iptal penceresi | 24 |
| `cancellation.late-fee-rate` | pencere içinde iptalde kesilen oran | 0,50 |
| `assignment.unpaid-expiry.hours-before-start` | ödenmemiş atama iş başlangıcına kaç saat kala düşer | 6 |
| `app.minimum-version` | bu sürümün altındaki istemci 426 alır; boşsa kapı kapalı | boş |
| `commission.rate` | işveren ücretine eklenen hizmet bedeli oranı | 0,075 |
| `app.minimum-version.ios` | iOS eşiği; yoksa tekil eşiğe düşer | boş |
| `app.minimum-version.android` | Android eşiği; yoksa tekil eşiğe düşer | boş |
| `app.minimum-version.web` | bağımsız web eşiği; boşsa yalnız web kapısı kapalı | boş |

**Konfigürasyon hâlâ varsayılan.** `SettingsService` önce tabloya bakar, satır yoksa `application.yaml` değerine düşer. Yani tablo boş bir veritabanı da doğru çalışır ve env ile ilk değer verilebilir.

**Yalnızca `SettingKeys` içinde tanımlı anahtarlar yazılabilir.** `PUT /admin/settings/rastgele.anahtar` 400 döner — tablo serbest bir anahtar-değer deposu değil, tanımlı bir politika yüzeyi. Değer sayısal olmak zorunda.

**Bozuk değer patlatmaz.** Tabloda sayısal olmayan bir şey varsa uyarı loglanır ve varsayılana düşülür; yanlış bir elle güncelleme iptal hesabını ya da zamanlayıcıyı durdurmaz.

**Komisyon oranı da bu tabloya taşındı.** Eskiden yalnızca `COMMISSION_RATE` env'iydi ve değiştirmek konteyner yeniden başlatmak demekti — ürünün en gelir-belirleyici sayısı, ondan daha az kritik olan iptal cezası oranından daha zor değişiyordu. Taşımak güvenli, çünkü ADR-0002 fiyatı ilan yayınlanırken donduruyor ve uygulanan oran `Job`, `JobAssignment` ve `Payment` satırlarına yazılıyor (§2e): oran değişse bile geçmiş kayıtlar hangi oranla hesaplandıklarını korur, mutabakat bozulmaz. `CommissionCalculator` her hesapta tabloya bakar, satır yoksa `application.yaml` değerine düşer; `COMMISSION_RATE` artık yalnızca **başlangıç değeri**.

**Oran anahtarlarının aralığı doğrulanıyor.** `commission.rate` ve `cancellation.late-fee-rate` için "sayısal mı" kontrolü yetmiyordu: `1.5` kabul edilseydi işveren işçi ücretinin 2,5 katını öderdi, negatif oran komisyonu iadeye çevirirdi. İkisi de `[0, 1]` aralığında olmak zorunda; dışarısı `setting.value.out.of.range` ile 400 döner. Saat ve gün anahtarları bu kuraldan etkilenmez.

**`AppSetting` artık `BaseEntity`'yi genişletiyor — elle dört alan eklenmedi.** Tablo dört sütundan ibaretti; bir ayarı kimin ne zaman değiştirdiğinin tek izi bir `log.info` satırıydı ve log döngüsü dolduğunda kayboluyordu. İki seçenek vardı: `BaseEntity`'yi genişletmek (denetim + soft delete sütunları birlikte gelir) ya da `created_at`/`updated_at`/`created_by_id`/`updated_by_id` alanlarını elle eklemek. `BaseEntity` seçildi çünkü (1) elle eklemek `@CreatedBy`/`@LastModifiedBy` + `@EntityListeners(AuditingEntityListener.class)` bloğunu kopyalamak demekti ve denetlenen her satırın aynı görünmesi kuralını bozardı, (2) `deleted_by_id` bedava geldiği için ileride bir silme ucu eklenirse hard delete kazası olmaz, (3) üç fazladan sütunun maliyeti beş satırlık bir tabloda yok. Bedeli: `@SQLDelete` + `@SQLRestriction` zorunlu oldu (§8) ve `setting_key` üzerindeki benzersizlik kısmi indeks değil — soft delete edilen bir satır anahtarı işgal etmeye devam eder. Bugün silme ucu olmadığı için bu bir sorun değil; eklenirse indeks `uk_users_email_active` gibi kısmileştirilmeli.

## 2ad. Olgunlaşma özellikleri (Faz 11C)

**`experiences` artık düz metin değil.** `User.experiences` bir `String` kolonuydu; istemci kayıt listesi çizmek istiyordu ve çizemediği için Tecrübe bölümünü profilden kaldırmıştı. Artık `Experience` entity'si: pozisyon, şirket, çalışma türü, şehir, başlangıç/bitiş, açıklama. **Bitiş tarihi null ise deneyim hâlâ sürüyor demek.** Başlangıç gelecekte olamaz, bitiş başlangıçtan önce olamaz.

Migration mevcut serbest metni kaybetmiyor: boş olmayan her değer `title = 'Aktarılan deneyim'` olan tek bir kayda dönüştürülüyor, sonra kolon düşürülüyor. Kullanıcı bunu düzenleyip düzeltebilir.

`GET /users/{userId}/experiences` **herkese açık** — işveren, başvuran işçinin deneyimini görebilmeli.

**Müsaitlik iki parçalı: haftalık şablon + tarih istisnaları.** `availability_slots` "her pazartesi 09:00-17:00" der, `availability_exceptions` "14 Eylül'de müsait değilim" der. İstisna tarih başına tekil (`uk_availability_exceptions_user_date`), aynı gün ikinci kez kaydedilirse mevcut satır güncellenir.

**Aralıklar toptan değiştirilir, eklenmez.** `PUT /users/me/availability` mevcut tüm aralıkları silip yenilerini yazar — istemci takvimi bir bütün olarak gönderiyor, tek tek diff tutmak zorunda kalmıyor. Aynı gün içinde **çakışan aralık reddedilir**; farklı günlerde aynı saatler çakışma sayılmaz.

**Talent pool işveren başına.** `(employer_id, worker_id)` kısmi benzersiz indeksi var; aynı işçiyi ikinci kez eklemek mevcut kaydın notunu günceller. Kendini eklemek reddedilir, listede olmayan birini çıkarmak sessizce geçer. Şirket bağlantısı isteğe bağlı — bir işveren birden çok şirket adına çalışabiliyor.

**İş şablonu ilandan kopyalanır, sıfırdan yazılmaz.** `POST /users/me/job-templates` gövdesinde `jobId` alır ve o ilanın alanlarını kopyalar. **Sahiplik kontrolü var:** ilanın sahibi ya da ilanın şirketinin yetkilisi olmayan biri şablon çıkaramaz — yoksa herkes başkasının ilan içeriğini kopyalayabilirdi. Tarih, adres ve durum kopyalanmaz; onlar her ilana özel.

**İş alarmı ilan oluşturulurken tetiklenir.** `JobManager` kayıttan sonra `jobAlertService.notifyMatching(savedJob)` çağırıyor. Eşleşme kriterleri: kategori, ilan türü, minimum ücret, şehir, ilçe — hepsi isteğe bağlı ama **en az biri zorunlu**, yoksa alarm her ilanda ateşlenirdi. Kullanıcı başına `job-alert.max-per-user` (10) sınırı var.

**Alarm bildirimi iş akışını düşürmez.** Her alarm ayrı `try/catch`'te; bildirim gönderilemese bile ilan oluşturma başarılı olur. İlan sahibi kendi ilanı için bildirim almaz. `job-alert.enabled=false` ile tümüyle susturulabilir (testlerde kapalı).

**Eşleşme bugün senkron ve tam tarama.** `findAllByActiveTrue()` ile tüm aktif alarmlar okunup bellekte filtreleniyor. Alarm sayısı büyüdüğünde bu sorgu SQL'e taşınmalı ya da RabbitMQ'ya devredilmeli — üretici altyapısı zaten var.

## 2ae. Aggregate sınırı — bir manager yalnızca kendi DAO'suna uzanır

Faz 18'de tüm kod bu kurala göre geçirildi: **bir manager yalnızca kendi aggregate'inin repository'sini kullanır**, başka aggregate'e erişim onun `Service`'i üzerinden olur. 20 ihlalden 7'ye indi.

**Kalan 7 tesadüf değil, bilinçli.** Katı uygulama Spring'de bean döngüsü üretiyor — denenip görüldü:

```
JobAssignmentManager → DisputeService → JobAssignmentService     (döngü)
PayoutManager        → SettlementService → PayoutService          (döngü)
```

Bu yüzden kural iki parçalı:
- **Komutlar** (yazma, iş kuralı tetikleme) daima diğer aggregate'in `Service`'i üzerinden.
- **Salt okuma sorguları**, servis üzerinden gitmek döngü yaratıyorsa doğrudan DAO ile yapılabilir — ve `AggregateBoundaryTest` içindeki listeye **gerekçesiyle** yazılır.

Muaf olan yedi yer ve nedenleri testte yazılı: `SecurityManager→UserDao` (altyapı, ters yön zaten bağlı), `BadgeManager→User/JobAssignment/RatingSummaryDao` (üç manager da `BadgeService`'e bağlı), `PayoutManager→JobAssignment/DisputeDao`, `SettlementManager→PayoutDao`.

**Döngüyü kırmak için bir iş akışı da taşındı.** Otomatik tamamlamadaki uyuşmazlık kontrolü `JobAssignmentManager`'dan `AssignmentAutoCompletionScheduler`'a çıktı — iki aggregate'i koordine eden şey artık *orkestratör*, aggregate'in kendisi değil. Emanet çıkışındaki uyuşmazlık kilidi (asıl para kapısı) `PayoutManager`'da yerinde duruyor.

**Yeni servisler:** `AcademicReferenceService` (üniversite/fakülte/bölüm referans verisi `EducationManager`'dan ayrıldı), ve delegasyon için `UserService.getReference`, `JobAssignmentService.getDetailedById/countCompletedForWorker/hasMoneyInEscrowFor/hasActiveWorkFor`, `DisputeService.hasActiveDispute`, `PaymentService.getSettledPaymentFor`, `MessagingService.getMessageById`, `JobApplicationService.hasApplied`, `JobCategoryService.getById`, `CompanyContactService`'e dört metot.

`AggregateBoundaryTest` üç şeyi doğruluyor: kural dışına çıkan manager yok, artık gerekmeyen muafiyet listede durmuyor, her muafiyetin yazılı gerekçesi var.

## 2af. N+1 denetimi

**Sistematik bir sızıntı vardı.** `SafeUserDto` her yerde `profilePictureUrl` çiziyor ve `User.profilePicture` lazy bir `@OneToOne`. Entity graph'ler kullanıcıyı çekiyordu ama fotoğrafını çekmiyordu — 20 satırlık bir sohbet listesi 40 fazladan sorgu atıyordu. Aynısı `Badge.icon` için de geçerliydi.

Düzeltilen yerler: konuşma listesi ve tekil konuşma, değerlendirme listesi, mesaj listesi, rozet listesi, atama listeleri (`worker.profilePicture`), başvuran listesi, uyuşmazlık listeleri, ilan sahibinin/şirketin ilan listeleri.

**`EagerFetchCoverageIT` bunu yapısal olarak koruyor.** Liste ya da sayfa döndüren her entity graph'i tarıyor: bir `User`/`Badge`/`Company` çekiliyorsa onun lazy eşlikçisi (`profilePicture`/`icon`/`logo`) de çekilmiş olmalı. Yanıt DTO'su o alanı çizmiyorsa muafiyet listesine **gerekçesiyle** yazılır — bugün 12 muafiyet var, hepsi "DTO bu alanı çizmiyor" gerekçeli. Tekil sorgular kapsam dışı, çünkü orada çarpan yok.

Test yazıldığında bir de **eksik uç** buldu: `MessagingService.getMyBlocks()` yıllardır serviste duruyordu ama controller'ı yoktu — engelleyip kaldırabiliyordunuz ama kimi engellediğinizi göremiyordunuz. `GET /users/me/blocks` eklendi.

**Bilinçli olarak dokunulmayan:** `JobAlertManager.matchingAlerts` tüm aktif alarmları okuyup bellekte filtreliyor. Bu bir N+1 değil (tek sorgu) ama alarm sayısı büyüdüğünde sorgunun kendisi ağırlaşır; SQL'e taşınması Faz 11 sonrası bir iş.

## 2ag. Hata sözleşmesi — yedi semantik tip

**34 exception sınıfı 7'ye indi.** Entity başına `XNotFoundException` açmak yerine anlam başına bir tip var: `ResourceNotFoundException` (404), `ForbiddenException` (403), `ConflictException` (409), `UnauthorizedException` (401), `ValidationException` (400), `ExternalServiceException` (502), `TooManyRequestsException` (429). Hepsi `KoalaException`'dan türer ve `HttpStatus` taşır.

**`errorCode` artık mesaj anahtarından türüyor, sınıf adından değil.** `{payment.not.found}` → `PAYMENT_NOT_FOUND`. Sınıf sayısı azaldı ama **kod granülerliği arttı**: eskiden `ResourceNotFoundException` her yerde `RESOURCE_NOT_FOUND` dönüyordu, şimdi hangi kaynağın bulunamadığı koddan okunuyor. Anahtar taşımayan mesajlarda sınıf adına düşülür.

**Türkçe locale hatası buradan çıktı.** `toUpperCase()` varsayılan locale kullanıyordu; Türkiye'de çalışan bir JVM'de `i → İ` olduğu için istemciye `VALİDATİON` gidiyordu. Aynı tuzak 13 yerde vardı ve bir kısmı **işlevsel hataydı**: `JobType.valueOf(type.toUpperCase())` — istemci `"individual"` gönderdiğinde Türkçe locale `"İNDİVİDUAL"` üretip `valueOf`'u patlatıyordu.

Kural: **makineye bakan metinde `Locale.ROOT`, insana bakan Türkçe metinde açık `Locale.of("tr","TR")`.** Kimlik doğrulamaya gönderilen ad/soyad ve profil adı büyütmesi bilerek Türkçe locale kullanıyor (`ilker` → `İlker` doğru olan). `LocaleSafetyTest` locale'siz hiçbir `toUpperCase()`/`toLowerCase()` kalmadığını doğruluyor.

## 2ah. Doğrulama nerede yapılır

**Girdi doğrulaması DTO'da, iş kuralı manager'da.** Manager katmanındaki null/blank kontrolleri kaldırıldı; hepsi Bean Validation'a taşındı:

- Tek alan: `@NotBlank`, `@NotNull`, `@Min`, `@PastOrPresent`, `@FutureOrPresent`.
- **Çapraz alan:** DTO üzerinde `@AssertTrue` metodu. Şikayetin en az bir hedefi olması, rozet kuralının eşiğinin dolu olması, bitiş tarihinin başlangıçtan sonra olması, iş alarmının en az bir kriteri olması, yaş aralığının sıralı olması, müsaitlik aralığının bitişinin başlangıçtan sonra olması — hepsi böyle.

Kazanç sadece temizlik değil: DTO'da yakalanan hata **400 + alan adı + mesaj** olarak döner (`details[{field, message}]`), manager'dan atılan ise yalnızca bir mesaj. İstemci hangi alanın bozuk olduğunu artık biliyor.

**Manager'da kalanlar bilinçli:** çakışan müsaitlik aralığı (koleksiyon içi ilişki), tekrar eden kayıt, durum geçişi — bunlar girdi şekli değil, iş kuralı. Kaynağın var olup olmadığı da manager'da kalır (`ResourceNotFoundException`).

`RequestValidationTest` bu kuralların DTO seviyesinde çalıştığını ve mesajların çözümlenebilir anahtar olduğunu doğruluyor.

## 2ai. Gerçek zamanlı katmanın güvenliği

`CONNECT` karesi baştan beri doğrulanıyordu, ama **`SUBSCRIBE` doğrulanmıyordu.** Giriş yapmış herhangi biri `/topic/conversations/{herhangi-bir-id}`'ye abone olup başkalarının mesajlarını okuyabilirdi. Kapatıldı:

- `/topic/conversations/{id}` — abonelik anında **taraf olma kontrolü**; taraf değilse 403, konuşma yoksa 403 (varlığını da sızdırmamak için).
- `/topic/presence/{userId}` — abonelik anında **aynı konuşmayı paylaşma kontrolü**; kendi durumunu herkes izleyebilir, başkasınınkini yalnızca onunla konuşması olan. Global `/topic/presence` **kaldırıldı**.
- `/user/**` — broker'ın kendi kullanıcı kapsamına bırakılır.
- **Tanınmayan hedef reddedilir**, sessizce geçilmez. Yeni bir topic eklenirse buraya da yazılmalı.

Ayrıca taşıma sınırları kondu: mesaj 64 KB, gönderim tamponu 512 KB, gönderim zaman aşımı 10 sn — kötü niyetli bir istemcinin belleği şişirmesini engeller.

**TLS bu katmanın dışında.** Soket `wss://` üzerinden servis edilmeli; şifreleme reverse proxy'de sonlanıyor. Uygulama `ws://` ile de çalışır, bu yüzden **prod'da proxy'nin TLS sonlandırdığından emin olunmalı** — kod bunu zorlayamaz.

`StompSubscriptionSecurityTest` on bir senaryoyu sabitliyor, `RealtimeFanoutTest` yedi tanesini.

## 2aj. İki dil tek yerden

`Accept-Language` başlığı `AcceptHeaderLocaleResolver` ile okunur, başlık yoksa **Türkçe** varsayılır. Çözümleme tek yerde: `MessageResolver`.

**Her metin iki dilde olmak zorunda.** `MessageCatalogTest` kaynak kodu tarayıp kullanılan her `{anahtar}` için hem `messages_tr` hem `messages_en` karşılığı olduğunu doğrular — biri eksikse test kırmızıya döner.

**Doğrulama mesajları da aynı yoldan geçer.** DTO anotasyonlarındaki `message = "{alan.bos.olamaz}"` değerleri Bean Validation tarafından ham bırakılır; `GlobalExceptionHandler` her alan mesajını `messageResolver`'dan geçirir. Yani `details[].message` istemciye ham anahtar değil, istenen dilde metin olarak gider. `ErrorEnvelopeIT` bunu `Accept-Language: en` ve `tr` ile ayrı ayrı doğruluyor.

**Loglar İngilizce, istemciye giden metinler iki dilli.** Log satırları operasyon içindir ve tek dilde tutulur; kullanıcıya görünen her şey katalogdan gelir.

## 2ak. Medya galerisi

İşçi ilanı ücret, konum, saat ve **mekânın nasıl bir yer olduğuna** bakarak seçiyor; garsonluk/barista gibi işlerde mekân fotoğrafı gerçek bir karar girdisi. Öncesinde ilanın hiç görseli yoktu, şirketin tek logosu vardı.

**İki tablo, tek aggregate.** `company_images` ve `job_images` aynı `media` satırına işaret eden birleştirme kayıtları: `sort_order`, `is_cover`, `caption`. İkisini de `GalleryManager` yönetiyor — `AggregateBoundaryTest` içinde iki DAO'lu aggregate olarak tanımlı, `MessagingManager` gibi.

**Aynı fotoğraf iki kez yüklenmiyor.** `PUT /jobs/{jobId}/images` gövdesindeki `imageId` ya ilanın mevcut galerisinden ya da **ilanın şirketinin galerisinden** seçilebilir; ikisinde de olmayan bir id 404 döner. Yani şirket mekânını bir kez yükler, her ilanda yeniden seçer. Bireysel ilanların şirketi olmadığı için (hayvan bakımı, özel etkinlik) `JobImage` kendi başına da durur.

**Sahiplik kontrolü galeride değil, sahibinde.** `GalleryManager` `JobService`/`CompanyService`'e hiç bağlı değil — `Job` ve `Company` **nesnesini** parametre olarak alır. Yetkiyi `JobManager.requireJobEditor` ve `CompanyManager.requireCompanyAdmin` verir. Bu bilinçli: aksi hâlde `JobManager → GalleryService → JobService` döngüsü çıkardı, çünkü liste kartındaki kapak görselini `JobManager` istiyor.

**Kapak listede ayrı sorgu.** `JobSummaryDto.coverImageUrl` mapper'da `ignore` edilir, `JobManager.withCovers` sayfadaki tüm ilanlar için `findAllByJobIdInAndCoverTrue` ile **tek sorguda** doldurur. Galeriyi liste sorgusunun entity graph'ine koymak iki nedenle yanlıştı: `findAllByCreatorId` sayfalı ve koleksiyon fetch'i LIMIT'i belleğe taşır (§2l), üstelik kart yalnızca kapağı çiziyor. Detayda ise `findWithDetailsById` galeriyi graph ile çekiyor — tekil sorgu, çarpan yok.

**Kapak kendiliğinden atanır.** İlk yüklenen görsel kapak olur; kapak silinince sıradaki devralır; hiçbiri işaretlenmemişse ilk sıradaki kapak yapılır. DTO seviyesinde aynı görselin iki kez listelenmesi ve birden fazla kapak seçilmesi reddedilir (`@AssertTrue`). Veritabanına tekil kapak indeksi **konmadı** — toplu güncellemede Hibernate'in flush sırası eski ve yeni kapağı aynı anda `true` bırakabiliyor.

**Şikayet hedeflerine görsel eklendi.** Dükkân camındaki telefon numarası, duvardaki WhatsApp afişi — komisyon modelinin dayandığı şey iletişimin platformda kalması (§2o). `Report.image` bu yüzden var; `DELETE /admin/images/{imageId}` görseli bulunduğu **tüm** galerilerden düşürür ve medya satırını soft delete eder.

**Sınırlar `upload.max-company-images` / `upload.max-job-images` (10).** Uzantı ve boyut zaten `UploadValidator`'dan geçiyor (§2ab), yeni doğrulama yolu açılmadı. Görseller `images/` önekinde, yani CDN'den doğrudan servis edilir.

**Uçlar üst kaynaktan aşağı (§0b):** `GET/POST/PUT /companies/{id}/images`, `DELETE /companies/{id}/images/{imageId}` ve `/jobs/{id}` altında aynı dörtlü. `GET` ikisinde de herkese açık — ilan zaten `permitAll`.

## 2al. Birleşik "işlerim" görünümü

Başvuru ile atamayı ayrı aggregate tutmak doğru — farklı durum makineleri, farklı yetki kuralları. Ama istemcideki "İşlerim" ekranı tek liste çiziyordu ve iki uç iki tur demekti.

**`GET /users/me/work` bir okuma projeksiyonu, yeni bir aggregate değil.** `MyWorkManager` hiçbir DAO'ya uzanmaz; iki servisi çağırıp `WorkItemDto` listesine katlar. Her satır `kind: APPLICATION | ASSIGNMENT` ile etiketli ve kart alanlarını (`id`, `job`, `status`, `occurredAt`) düzde taşır — istemci sıralamak ve çizmek için dallanmaz; ham kayıt `application` / `assignment` alanlarından birinde durur, diğeri null.

**Sayfalama yok, tavan var.** `limit` (varsayılan 50) 1–100 arasına **kırpılır**, reddedilmez; iki listeden `limit` kadar çekilip birleşik liste `occurredAt` azalanına göre sıralanır ve yine `limit` ile kesilir. İki tablo üzerinden gerçek sayfalama `UNION ALL` native projeksiyonu ister; v1 yeterli gelirse hiç gerekmeyecek. "Hepsini gör" için sayfalı `/users/me/applications` ve `/users/me/assignments` yerinde duruyor.

**`asEmployer=true` başvuru döndürmez.** İşverenin "başvurularım"ı yok; kendi ilanına gelen başvurular `GET /jobs/{jobId}/applications` altında. Bu yüzden o modda `JobApplicationService`'e hiç gidilmez.

**Bu faz iki uçtaki eşlemeyi de manager'a taşıdı.** `getMyApplications` / `getMyAssignments` artık `Page<Entity>` değil `PageDto<...Dto>` döndürüyor; controller'daki mapper çağrıları kalktı. Kazanç sadece tutarlılık değil: kapak görselini dolduran `galleryService.fillJobCovers` çağrısı tek yere indi, üç uç da (arama, işlerim, başvurularım) aynı yoldan kapak alıyor.

## 2am. Şirket takibi ve işveren istatistikleri

**Takip, iş alarmının (§2ad) şirket bazlı hâli.** `company_follows` tek satır: kim, hangi şirket. `(company_id, follower_id)` kısmi benzersiz indeksiyle tekil, takip idempotent, takip etmediğin bir şirketi bırakmak sessizce geçer. Şirket ilan açtığında `JobManager` `jobAlertService.notifyMatching` ile birlikte `companyFollowService.notifyFollowers` çağırıyor; ilan sahibi kendi ilanı için bildirim almaz ve tek bir teslimat hatası döngüyü düşürmez — alarm bildirimindeki desenin aynısı. `company-follow.enabled=false` ile tümüyle susturulur.

**Sahiplik kontrolü yine sahibinde.** `CompanyFollowManager` `CompanyService`'e bağlı değil, `Company` **nesnesini** alır; `CompanyManager` şirketi bulur ve devreder. Aksi hâlde `CompanyManager → CompanyFollowService → CompanyService` döngüsü çıkardı, çünkü `CompanyDto` artık `followerCount` ve `following` taşıyor (ikisi de mapper'da `ignore`, `getCompanyDetail` içinde dolduruluyor). Galeride (§2ak) alınan kararın aynısı.

**İstatistikler türetilir, saklanmaz.** `GET /users/{userId}/employer-stats` açılan ilan, tamamlanan iş, ortalama puan, yorum sayısı ve üyelik tarihini döndürür. `StatisticsManager` hiçbir DAO'ya uzanmaz — dört servisi çağıran bir orkestratör, `MyWorkManager` (§2al) ile aynı şekil. `RatingSummary` yoksa ortalama `0` döner, null değil. Uç **herkese açık**: işveren profilini gören işçi bu sayıları görebilmeli.

**Anonim değerlendirme — karar tersine döndü.** Önceki gerekçe "itibar sistemi kimin yazdığını gizlediği anda hesap verebilirliğini kaybeder" idi. Bunu karşılayan tasarım şu: **`author_id` veritabanında her zaman dolu kalıyor**, yalnızca yanıt katmanında gizleniyor. Yani `(job_assignment_id, author_id)` tekilliği, taraf başına tek değerlendirme kuralı ve kötüye kullanım incelemesi olduğu gibi duruyor; kaybolan tek şey **profili gezen üçüncü kişilerin** yazarı görmesi.

Asıl gerekçe frontend'den geldi ve doğru: bir daha çalışmayacağı bir işvereni dürüstçe puanlayan işçi, adının görünmesinden çekiniyor. Anonimliğin olmaması bu kişiyi ya sessiz kalmaya ya yalan söylemeye itiyordu — itibar sistemine asıl zarar veren buydu.

**Anonimlik puanı geçersiz kılmaz.** Anonim değerlendirme de `RatingSummary` ortalamasına ve `badgeService.evaluate` çağrısına giriyor.

**Gizleme mapper'da, tek yerde.** `ReviewMapper` iki eşleme metodu taşıyor (`toNamedReviewDto` / `toAnonymousReviewDto`) ve `toReviewDto` bayrağa göre birini seçiyor. `ReviewDto` bir `record` olduğu için `@AfterMapping` kullanılamıyor; ifade tabanlı eşleme de MapStruct'ın `userMapper` alanını üretmesine bağlı olurdu ve o alan yalnızca ihtiyaç duyulduğunda üretiliyor — bu yüzden iki metotlu biçim seçildi.

**Anonimlik iki taraf arasında değil, dışarıya karşı çalışıyor.** Bir atamanın yalnızca iki tarafı var; değerlendirmeyi alan kişi hangi işten geldiğini gördüğü an yazarı da bilir. Ürün tarafında söz buna göre verilmeli — istemci ekranında bu not düşülüyor.

**Sonradan değiştirilemez:** güncelleme ucu yok ve eklenmeyecek.

## 2an. E-posta şablonları

**Şablon adı kodda düz metin, dosya `resources/templates/emails` altında.** `EmailConsumer` `templateEngine.process("emails/" + templateName, context)` çağırıyor; ad tutmazsa Thymeleaf `TemplateInputException` fırlatır, `Notification` `FAILED`'a düşer ve **kimse haberdar olmaz** — kullanıcı e-postayı beklemeye devam eder.

Bu tam olarak yaşandı: kodda on bir şablon adı geçiyordu, dizinde üç dosya vardı. E-posta değiştirme doğrulaması, şirket oluşturma bildirimleri ve altı davet e-postası — sekiz akış sessizce hiç e-posta göndermiyordu.

**Ortak gövde `emails/layout.html` fragment'ında.** Her şablon `th:replace="~{emails/layout :: page(~{::content})}"` ile yalnızca kendi içeriğini yazıyor; başlık, logo, destek bloğu, adres ve takip pikseli tek yerde. Marka değişince sekiz dosya düzenlenmiyor.

**`raw-sms-template` ve `raw-push-template` HTML değil.** SMS ve push için Thymeleaf'ten geçen bir tüketici yok; bu adlar yalnızca `Notification` kaydında duruyor. Şablon testinin muafiyet listesinde bu yüzden varlar.

**Takip pikseli adresi Faz 15'ten beri yanlıştı.** `EmailConsumer` `apiUrl() + "/api/notifications/track/{id}.png"` üretiyordu; ne `/api` öneki var (§0b), ne de o yol — uç `/notification-tracking/{id}.png` olarak yeniden adlandırılmıştı. Yani her e-postadaki takip pikseli 404 dönüyordu ve açılma oranı ölçülemiyordu.

**Stil tırnağı tuzağı.** `style='... font-family: "Manrope" ...'` geçerli, `style="... font-family: "Manrope" ..."` değil — ikincisi ayrıştırma hatası verir. `EmailTemplateTest` her şablonu gerçekten işleyerek bunu yakalıyor.

**`EmailTemplateTest` üç şeyi koruyor:** kaynak kodda geçen her şablon adının dosyası var, her şablon gerçek `SpringTemplateEngine` ile hatasız işleniyor, ve hiçbir yerde `apiUrl() + "/api/` kalıntısı kalmıyor.

## 2ao. API dokümantasyonu

**Swagger prod'da kapalı.** `SPRINGDOC_ENABLED=false` hem `/v3/api-docs`'u hem `/swagger-ui.html`'i kapatır. Açık bırakılırsa 146 ucun tamamı, DTO şemaları ve hangi alanın zorunlu olduğu herkese görünür — saldırgana keşif hediyesi. Geliştirme ve istemci ekibi için varsayılan `true`.

**Global güvenlik gereksinimi yok, uç bazında veriliyor.** OpenAPI'de global bir `security` tanımlıysa bir ucu açık işaretlemenin tek yolu `security: []` yazmaktır; swagger-core boş diziyi serileştirmede düşürdüğü için bu ifade edilemiyor. Bu yüzden `OpenApiConfig.commonErrorResponses` her korumalı uca bearer gereksinimini ve 401/403 yanıtlarını tek tek ekliyor, açık uçlara hiç eklemiyor.

**Açık uç işareti `@SecurityRequirements`.** `@Operation(security = {})` işe yaramıyor: springdoc bunu "belirtilmemiş" ile aynı görüyor, çünkü anotasyonun varsayılanı da boş dizi. Ayrı bir `@SecurityRequirements` anotasyonu `handlerMethod.hasMethodAnnotation` ile kesin ayırt ediliyor.

**Hata yanıtları tekrar yazılmıyor.** 401, 403 ve 500 customizer'dan gelir; anotasyonlarda yalnızca uca özel kodlar (400, 404, 409, 502…) yazılır ve hepsi `ErrorResult` şemasına bağlanır.

**Swagger arayüzü iki yerden yavaşlıyordu.** Sunucu tarafında `swagger-ui-bundle.js` (~1,4 MB) **6,7 saniyede** servis ediliyordu çünkü `server.compression` kapalıydı; gzip açılınca dosya dörtte birine iniyor. Kalan ~49 saniye tarayıcı tarafıydı: Swagger UI varsayılan olarak tüm etiketleri açık çiziyor ve sayfanın altında **her DTO şemasını** render ediyor. `doc-expansion: none` ve `default-models-expand-depth: -1` bunu kapatıyor; belge zaten 61 ms'de üretiliyordu, darboğaz hiç sunucuda değildi.

Sıkıştırma yalnızca Swagger için değil: `application/json` de kapsamda, yani mobil istemcinin her yanıtı da küçülüyor.

**Üç yapısal test koruyor.** `ApiDocumentationTest`: her ucun `@Operation`'ı ve `@ApiResponse`'u var, her controller'da `@Tag` var, ve **dokümandaki yetki bilgisi `SecurityConfig` ile birebir uyuşuyor** — bir uç `@SecurityRequirements` taşıyorsa `permitAll()` olmalı, taşımıyorsa olmamalı. `OpenApiDocumentIT` gerçek context'te `/v3/api-docs`'u çekip belgenin kurulduğunu, bearer şemasının tanımlı olduğunu, korumalı ucun 401/403 belgelediğini ve açık ucun token istemiyor göründüğünü doğruluyor.

**`CompanyContactsController` silindi** — hiçbir uç taşımayan, yalnızca bir servis enjekte eden ölü sınıftı.

## 2ao2. Kontenjan — sayı var, kapı yok

`Job.headcount` **isteğe bağlı bir hedef sayı**, bir kota değil. Null bırakılabilir; bırakıldığında hiçbir davranış değişmez.

**Hiçbir yerde zorlanmıyor, bilerek.** Kontenjan dolduğunda başvuru kabulü reddedilmez, ilan kapanmaz, iş başladıktan sonra da işe alım yapılabilir. Gerekçe ürün tarafından: ihtiyaç iş sırasında değişir — biri gelmez, iş büyür, ekibe sonradan katılan olur. Sunucunun "3 dedin, 4. kişiyi alamazsın" demesi işvereni platformun dışına iter. `JobHeadcountRulesTest` bu **yokluğu** sabitliyor: dört testin dördü de "engellenmiyor" doğruluyor, yani ileride buraya bir kapı konursa test kırmızıya döner.

**Sonradan alınan kişi ekibe de katılır.** `CrewConversationListener` atama `CONFIRMED` olduğunda tetikleniyor ve tarih kontrolü yok (§2o); iş başladıktan sonra işe alınan biri de gruba girer. Mesajlaşma penceresi yalnızca `endDateTime` + grace ile kapanır.

**`hiredCount` türetilir, saklanmaz.** `CONFIRMED`/`IN_PROGRESS`/`COMPLETED` atamalar sayılır — teklif edilmiş ama kabul edilmemiş (`PENDING_CONFIRMATION`) kişi "alınmış" sayılmaz. Listede `countHiredByJobIds` ile **tek sorguda** toplanıyor (`JobManager.enrich`, kapak ve başvuru sayısıyla aynı yerde, §2ap); detayda tekil `countByJobIdAndStatusIn`.

**Detay eşlemesi controller'dan manager'a taşındı.** `GET /jobs/{id}` artık `jobService.getJobDetail(jobId)` çağırıyor; `hiredCount` mapper'da `ignore`, manager dolduruyor. §2al'deki kararın aynısı — eşleme controller'da kalsaydı sayıyı doldurmanın yeri olmazdı.

**`JobStatus.IN_PROGRESS` ve `COMPLETED` hâlâ kullanılmıyor, ve kontenjan bunu değiştirmiyor.** İlan `IN_PROGRESS`'e alınsaydı aramadan düşerdi (`searchIds` `status = OPEN` istiyor, §2l) ve **sonradan işe alım imkânsızlaşırdı** — yukarıdaki kuralla doğrudan çelişir. İlan `OPEN` → `CLOSED`/`CANCELLED` dışına çıkmıyor; kapatma işverenin elinde kalıyor. İstemci "2/3 alındı" göstergesini `hiredCount`/`headcount` ile kendisi çiziyor.

## 2ap. İşveren yüzeyi

**`JobSummaryDto.applicationCount` toplu sorguyla dolar.** İstemci ilan listesini çizip her ilan için ayrı `GET /jobs/{id}/applications` atıyordu — 10 ilanı olan işverende 1+10 istek. Sayı artık `countByJobIds` ile **tek sorguda** toplanıyor ve `JobManager.enrich` içinde kapak görselleriyle birlikte dolduruluyor. Yani sayfa başına iki ek sorgu: kapaklar + sayılar.

`JobManager → JobApplicationDao` doğrudan; `JobApplicationManager` zaten `JobService`'e bağlı olduğu için servis üzerinden gitmek ters yön döngü olurdu (§2ae kuralı, muafiyet `AggregateBoundaryTest`'te gerekçeli).

**`POST /jobs/{jobId}/cancel` kapatmadan farklı.** `close` yalnızca yeni başvuruyu durdurur, `cancel` ilanı `CANCELLED` yapar ve başvuranlara bildirim gönderir. Kayıt silinmez — başvuru geçmişi korunmalı, bu yüzden `DELETE` tercih edilmedi.

**İptal, devam eden atama varken reddedilir.** `PENDING_CONFIRMATION`, `CONFIRMED` ya da `IN_PROGRESS` bir atama varsa 409 döner. Aksi hâlde ilan iptali, emanetteki parayı ve iptal politikasını (§2p) arkadan dolanan ikinci bir yol olurdu; o durumda atamanın kendi iptal akışı kullanılır.

**`GET /users/{userId}` kimlik doğrulaması ister, anonime açık değil.** Deneyim, değerlendirme ve rozet uçları `permitAll` ama temel profil (`about`, il/ilçe) bir adım daha kişisel; giriş yapmış kullanıcıya açık. **Tam adres hiçbir zaman dönmez**, yalnızca il ve ilçe.

**`cvUrl` ilişkiye bağlı.** Profil sahibinin kendisi ya da o kişinin başvurduğu bir ilanın işvereni görür; diğerlerinde `null` kalır. Aynı kural indirme yolunda da var (§2ab), yani adres sızsa bile dosya inmez — iki katman.

**Bu uç `PublicProfileManager`'da, `UserManager`'da değil.** `UserManager → JobApplicationService → JobService → CompanyService → UserManager` döngüsü çıkıyordu. Okuma projeksiyonu ayrı bir orkestratöre alındı; `MyWorkManager` (§2al) ve `StatisticsManager` (§2am) ile aynı şekil: hiçbir DAO'ya uzanmaz, servisleri çağırır.

**Bireysel ilan sigorta zorunluluğu taşıyamaz — bilerek.** `createIndividualJob` `insuranceRequired`'ı sabit `false` yazıyor ve `requiresInsurance` işaretli bir kategoride bireysel ilan hiç açılamıyor. SGK yükümlülüğü işverene ait; bir günlük iş için bireysel işverenden bunu bekleyemeyiz. Bu yüzden `CreateIndividualJobDto`'da alan yok, olmayacak.

**`CompanyDto.approved` alan adı `approved`, `isApproved` değil.** `CompanyMembershipDto.approved` zaten öyle; aynı kavramın iki adı olmasın diye. §2m'deki Lombok/Jackson tuzağı burada geçerli değil çünkü DTO alanı `approved`, türetme belirsizliği yok.

**`PUT/DELETE /companies/{companyId}/logo`** profil fotoğrafıyla aynı kalıpta; logo yalnızca şirket kurulurken verilebiliyordu, sonradan hiç değiştirilemiyordu.

## 2aq. Kimlik doğrulama — sunucu artık doğrulamıyor

**NVİ KPS kapandı.** `https://tckimlik.nvi.gov.tr/service/kpspublicv2.asmx` bugün 302 ile hata sayfasına yönlendiriyor, o da 404. `RestTemplate` yönlendirmeyi takip ettiği için `postForObject` 404 HTML'ini string olarak döndürüyordu; içinde başarı işareti olmadığından `isVerified=false` çıkıyor ve **her kullanıcıya `IDENTITY_VERIFICATION_FAILED` (400)** dönüyordu.

Bu hata biçiminin kötülüğü şurada: "senin bilgilerin yanlış" ile "servis ölü" ayırt edilemiyordu. Ve `assertReadyToWork`'ün ilk şartı kimlik olduğu için sonuç **hiç kimsenin hiçbir işe başvuramaması** idi — tek bir ucun bozulması değil, başvuru zincirinin tamamının kopması.

**KPS çağrısı kaldırıldı, `KimlikPaylasimSistemiService` silindi.** Doğrulama artık istemcide yapılıyor: NFC ile çipten okuma (ICAO 9303), yüz eşleştirme ve canlılık testi. Sunucu gelen bilgiyi kaydediyor.

**Bunun ne olduğu açıkça yazılmalı: bu bir güvenlik düşüşü.** Önceden kararı sunucu veriyordu, artık istemci veriyor. Uygulamayı değiştiren biri kontrolleri hiç çalıştırmadan uca istek atabilir ve **başkasının TCKN'siyle Iyzico alt üye işyeri açtırabilir** — para o hesaba gider. Üç katmanın cihazda ne kadar iyi olduğu bunu değiştirmez, çünkü sunucu o katmanların çalıştığına dair hiçbir kanıt görmüyor.

Yine de doğru karar: alternatif, uygulamanın tümüyle ölü kalması. Ama **geçici olduğu unutulmamalı** — kalıcı çözüm Faz 27 (Passive Authentication).

**Passive Authentication'dan sonra kalan artık risk.** Çip doğrulaması "kart gerçek" der, "kartı tutan kişi sahibidir" demez — onu kuran şey yüz eşleştirmesi ve o cihazda. Yani elinde **başkasının gerçek kartı** olan biri, uygulamayı değiştirerek o kimlikle kayıt olabilir. Sonucu iş değil para: alt üye işyeri **kurbanın TCKN'siyle** ama **saldırganın IBAN'ıyla** açılır.

**Doğrulanan kimlik alanlarının kaynağı çip, istek gövdesi değil.** Bayrak açıkken `tcIdentityNumber` ve `birthDate` doğrudan DG1'den yazılıyor; istemcinin gönderdiği değer yalnızca karşılaştırma için kullanılıyor. Doğrulamadan sonra bu alanlar `patchCurrentUser` tarafından zaten kilitleniyor (`IDENTITY_VERIFIED_CANNOT_CHANGE_*`), yani bir kez çipten yazılıp sabitleniyorlar.

**Ad soyad DG1'den alınmaz — alınırsa bozulur.** MRZ ASCII'ye katlanmış (`ÇAĞRI` orada `CAGRI`) ve üçüncü satır 30 karakterde kırpılıyor. Oradan yazmak kullanıcının adını geri dönüşsüz bozardı. Doğru kaynak **DG11** (Additional Personal Details, `5F0E` alanı): adı ulusal karakterlerle taşıyor ve hash'i SOD'da olduğu için DG1 ile aynı şekilde doğrulanabiliyor.

DG11 **opsiyonel**: gönderilirse hash'i doğrulanır ve ad oradan yazılır; gönderilmezse istekteki ad korunur ve MRZ ile önek uyumu kontrolü devreye girer. TCKK'da DG11'in bulunup bulunmadığı karta göre değişiyor (LDS 1.7'de DG1 dışındaki gruplar opsiyonel), bu yüzden akış iki durumda da çalışıyor. Kurcalanmış DG11 reddediliyor.

**Ad soyad ayrıca çiple karşılaştırılıyor, ve bu iyzico zinciri için şart.** Iyzico dokümanı alt üye işyeri kaydında *"Submerchant IBAN. Must be consistent with contactName/contactSurname"* diyor ve `contactName` bizde kullanıcının yazdığı addan geliyor. Ad çiple doğrulanmazsa saldırgan kurbanın TCKN'siyle **kendi adını** kaydettirebilir; o zaman kendi IBAN'ı iyzico'nun ad-IBAN kuralını sağlar ve para kurbanın TCKN'si altında saldırgana gider. Ad kontrolü bu boşluğu kapatıyor: kayıtlı ad karttakiyle aynı olmak zorunda.

Karşılaştırma **önek uyumuna izin veriyor**: MRZ üçüncü satırı 30 karakter, uzun adlar orada kırpılıyor. Tam eşitlik arasaydık uzun adlı kullanıcı kilitlenirdi. Türkçe karakterler de karşılaştırmadan önce ASCII'ye katlanıyor (`Çağrı` → `CAGRI`), çünkü MRZ zaten öyle basılıyor.

**Geriye kalanı kapatan kontrol yüz eşleştirmesi değil, IBAN sahipliği.** Bugün `savePrimaryAccount` yalnızca IBAN'ın biçimini doğruluyor (`IbanValidator`, mod-97); hesabın kime ait olduğu hiç sorulmuyor, `accountHolderName` kullanıcının kendi adından türetiliyor. IBAN'ın doğrulanmış kimliğe ait olduğu teyit edilirse para yolu, kartı kimin tuttuğundan bağımsız olarak kapanır. İşlem başına biyometrik veri taşımadan, sabit maliyetsiz.

**KKB IBAN Doğrulama Hizmeti** tam bunu yapıyor: IBAN + TCKN verip eşleşip eşleşmediğini söylüyor. Ama erişim KKB üyeliğine bağlı ve üyelik esas olarak bankalar/finansal kurumlar için; banka dışı bir şirketin doğrudan erişebildiği belirsiz — KPS'deki durumun aynısı. **Araştırıldı, yapılmadı; Faz 7C ile birlikte değerlendirilmeli.**

**Telafi edici tek koruma: TCKN benzersizliği.** `uk_users_tc_identity_number_active` kısmi benzersiz indeksi (soft delete deseniyle, §8). Bu **uydurmayı değil yeniden kullanımı** engelliyor — bir TCKN yalnızca bir hesaba bağlanabiliyor. Migration `preConditions onFail="HALT"` ile mevcut veride çakışma varsa deploy'u durduruyor; sessizce yarım uygulanmasındansa açılışın durması tercih edildi. Çakışma denetimi:

```sql
SELECT tc_identity_number, count(*) FROM users
WHERE is_deleted = false AND tc_identity_number IS NOT NULL
GROUP BY tc_identity_number HAVING count(*) > 1;
```

**`tcDocumentNumber` artık saklanıyor.** Öncesinde toplanıp KPS'ye gönderiliyor ama **hiçbir yere yazılmıyordu** — `User` üzerinde alan yoktu. "Kanıt olarak değerli" demenin karşılığı olması için `users.tc_document_number` eklendi.

**Kimlik doğrulama artık her iki tarafta da zorunlu:**

| Eylem | Kontrol | Mesaj anahtarı |
|---|---|---|
| İşe başvurmak | vardı (§2ar) | `APPLICANT_IDENTITY_NOT_VERIFIED` |
| Şirket kurmak | vardı | `USER_MUST_VERIFY_IDENTITY` |
| **Bireysel ilan açmak** | **eklendi** | `JOB_INDIVIDUAL_EMPLOYER_IDENTITY_NOT_VERIFIED` |
| **Ödeme oturumu açmak** | **eklendi** | `JOB_ASSIGNMENT_EMPLOYER_IDENTITY_NOT_VERIFIED` |

**Ödemedeki kontrol savunma amaçlı, ikinci sırada.** Asıl kapı ilan açma anında — hata kullanıcıya en erken ve en ucuz noktada söylenmeli. İşveren ilanı yayınlayıp başvuruları toplayıp birini seçtikten *sonra* "kimliğini doğrulamamışsın" demek en kötü zamanlama, üstelik karşı tarafta işe alındığını sanan bir işçi bırakıyor. Ödemedeki kontrol yalnızca `null` bir TCKN'nin hiçbir koşulda Iyzico'ya gitmemesini garantiliyor; şirket ilanları VKN kullandığı için o yola dokunmuyor.

**TCKN yanıtta maskelendi.** `UserMeResponseDto.tcIdentityNumber` → `maskedTcIdentityNumber`, dolu olduğunda `***********`, boşken `null`. §2i'nin "TCKN hiç" kuralı yanıt gövdesine de uygulandı; istemci zaten yalnızca "dolu mu" bilgisini kullanıyor, tam değere ihtiyacı yok.

## 2aq2. Passive Authentication — kanıt sunucuda doğrulanıyor

§2aq'daki güvenlik düşüşünü kapatan katman. İstemcinin "doğrulandı" demesi yetmiyor: çipin imzalı çıktısını gönderiyor, sunucu onu doğruluyor.

**Neden yalnızca birinci katman sunucuya taşındı.** Yüz eşleştirme ve canlılık cihazda kalıyor — bu bir maliyet kararı değil, veri kararı. DG2 çipteki yüz fotoğrafı, yani KVKK'nın **özel nitelikli kişisel verisi**; onu sunucuya alıp bir yüz tanıma servisine göndermek açık rıza ve (yurtdışı servis ise) sınır ötesi aktarım yükümlülüğü doğurur. Kazanç ise sınırlı: çipe hiç dokunmayan saldırgan zaten SOD imzasını üretemiyor. Kritik olan tek iddia "kart sahte değil ve veri değiştirilmedi" ve onu kuran şey SOD imzası. O imzayı üretebilmenin tek yolu gerçek bir çipe fiziksel olarak dokunmak — yani uygulamayı değiştiren kişi bile ancak **elinde fiziksel kartı olan bir TCKN'yi** doğrulatabiliyor.

**Doğrulama zinciri** (`PassiveAuthenticationVerifier`):

1. `sod` bir CMS `SignedData` olarak ayrıştırılır (başta `0x77` application tag'i varsa soyulur)
2. İmzacının sertifikası gövdeden çıkarılır, `SignerInformation.verify` ile imza doğrulanır
3. Sertifika `CertPathValidator` ile **CSCA güven çıpasına** zincirlenir
4. eContent `LDSSecurityObject` olarak ayrıştırılır, DG1 hash'i ve digest algoritması okunur
5. Gelen `dg1`'in hash'i o algoritmayla hesaplanıp karşılaştırılır
6. DG1'den MRZ çıkarılır (TD1, üç satır × 30 karakter): TCKN, doğum tarihi ve ad soyad okunur
7. Üçü de istek gövdesindeki değerlerle karşılaştırılır

**TCKN MRZ'nin neresinde:** ICAO 9303 Part 5'e göre TD1 üst satırında 16-30. karakterler "optional data" ve Türk kimlik kartında TCKN oraya yazılıyor. Ayırıcı `<` ile başlayan da başlamayan da kabul ediliyor; dolgu karakterleri atıldıktan sonra alanın **tam 11 hane** kalması şart.

**Not (j) tuzağı.** Belge numarası 9 karakterden uzunsa ICAO, kontrol hanesinin yerine dolgu koyup numaranın devamını **optional data'nın başına** yazdırıyor. O durumda alan TCKN gibi görünen haneler içerebilir. Bu yüzden ayrıştırıcı önce 15. karakteri kontrol ediyor: dolgu ise alan TCKN taşımıyor demektir ve okuma reddediliyor. Türk kimlik kartında belge numarası 9 karakter, yani bu durum oluşmuyor — ama kanonik ICAO örneği tam olarak bu şekilde ve yanlış okumak başkasının belge numarasını TCKN sanmak olurdu.

TD1 olmayan bir MRZ reddediliyor — pasaportta (TD3) zaten TCKN yok.

**Ham kanıt saklanmıyor, bilerek.** Yakup `IdentityVerification`'ı önerdi ama DG1 tüm MRZ'yi (ad, soyad, belge no, uyruk, cinsiyet, son geçerlilik) taşıyor, SOD ise DG2'nin yani yüz fotoğrafının hash'ini. Bunları saklamak KVKK açısından gereksiz veri biriktirmek olur. Bunun yerine **sonucun kanıtı** saklanıyor: `users.identity_evidence_digest` (SOD'un SHA-256'sı) ve `users.identity_document_signer` (imzalayan sertifikanın subject'i). Denetimde "şu tarihte, şu imzalayanla, şu parmak iziyle doğruladık" cevabı verilebiliyor; çip dökümü tutulmuyor.

**Varsayılan kapalı ve fail-closed.** `identity.passive-authentication.enabled=false` iken davranış §2aq'daki gibi. Açıkken `sod`+`dg1` **zorunlu**; eksikse `IDENTITY_EVIDENCE_REQUIRED`. Güven çıpası yüklenmemişse hiçbir şey doğrulanmıyor — açılışta hata loglanıyor ve her doğrulama reddediliyor. Yani "açtım ama sertifika koymayı unuttum" hâli sessizce herkesi geçirmiyor.

**CSCA sertifikaları imajın içinde geliyor.** `src/main/resources/csca/` altında 12 Türkiye sertifikası duruyor (48 KB) ve classpath'ten yükleniyor. Bunlar **açık anahtar, sır değil** — repoda durmalarının sakıncası yok, üstelik hangi sürümde hangi çıpalara güvenildiği git geçmişinden okunabiliyor.

Bu tercih bilinçli: dizinden okusaydı prod'da volume bağlamak ve dosyaları elle kopyalamak gerekirdi, üstelik unutulduğunda hata açılışta değil ilk doğrulamada çıkardı. `identity.passive-authentication.trust-anchor-directory` hâlâ duruyor ama artık **ek** çıpa yüklüyor, tek kaynak değil — test ortamında ya da NVİ'den ayrı bir sertifika gelirse diye.

Liste üç ayda bir güncelleniyor; `scripts/fetch-csca-anchors.sh` çalıştırılıp commit edilmesi yeterli. Telefona gömülemez — istemcide doğrulama zaten istemciye güvenmek demek.

**Sertifika iptali (CRL) kontrol edilmiyor.** `PKIXParameters.setRevocationEnabled(false)`. ICAO PA pratiğinde yaygın olan bu, ama eksiklik olarak bilinsin: iptal edilmiş bir Document Signer sertifikası hâlâ geçer. Kapatmak CRL dağıtım noktalarına erişim ve tazelik yönetimi ister.

**CSCA sertifikaları BSI master list'inden geliyor.** ICAO PKD indirmesi üyelik istiyor; Almanya BSI'ın yayınladığı master list kamuya açık ve ticari kullanıma izinli. `scripts/fetch-csca-anchors.sh` listeyi indirip imzasını açıyor, `C=TR` olanları DER olarak çıkarıyor. **Mayıs 2026 listesinde Türkiye'nin 12 CSCA sertifikası var:** dördü `CN=CSCA Turkey, O=Turkey Gov` (2013 ve 2016), sekizi `CN=Passport CSCA Turkey` (2018, 2021, 2024). Liste üç ayda bir güncelleniyor, betik düzenli çalıştırılmalı.

**Sertifikaların sekizi explicit EC parameters kullanıyor** ve JDK'nın varsayılan `CertificateFactory`'si bunları `Only named ECParameters supported` ile reddediyor — üstelik reddedilenler en güncel olanları. Bu yüzden hem `CscaTrustStore` hem zincir doğrulaması **açıkça BouncyCastle sağlayıcısını** kullanıyor. Varsayılan sağlayıcıyla 12 sertifikanın yalnızca 4'ü yükleniyordu; sessizce eksik güven kümesiyle çalışmak, doğru kartı reddetmek demekti.

**Açık soru: kimlik kartı hangi CSCA'ya zincirleniyor?** Listedeki sertifikaların çoğu adında "Passport" taşıyor. TCKK ICAO 9303 uyumlu bir eMRTD ama Document Signer'ının bu CSCA'lardan birine bağlanıp bağlanmadığı **doğrulanmadı**. Bağlanmıyorsa `UNTRUSTED_DOCUMENT_SIGNER` alınır ve NVİ'den kimlik kartına özel CSCA istenmesi gerekir. **Gerçek bir kartla test edilecek ilk şey bu.**

**Gerçek kart dökümü tek komutla denenir:**

```bash
KOALA_CHIP_DIR=/tmp/chip KOALA_CSCA_DIR=/opt/koala/csca ./mvnw test -Dtest=RealChipVerificationTest
```

`KOALA_CHIP_DIR` içinde `sod.b64`, `dg1.b64` ve (varsa) `dg11.b64` beklenir — base64, tek satır. İki değişken de verilmezse test sessizce atlanır, yani CI'ı etkilemez. Çıktısında iki teşhis satırı var: **`ds issuer`** kimlik kartının hangi CSCA'ya zincirlendiğini söyler (doğrulama başarısız olsa bile), **`kartta`** ise SOD'un listelediği veri gruplarını yazar — yani DG11'in (ulusal karakterli ad) ve DG15'in (Active Authentication) o kartta bulunup bulunmadığı tek koşuda görünür.

**Passive Authentication tek başına kopyalamayı engellemez.** Bir kez okunan SOD ve DG1 sabittir; kaydeden biri sonsuza kadar yeniden gönderebilir. Bu yüzden **Active Authentication** eklendi.

`POST /users/me/identity-verification/challenge` sunucunun ürettiği 8 baytlık tek kullanımlık sayıyı döndürür; istemci onu çipe imzalatır ve `dg15` + `challenge` + `activeAuthenticationSignature` ile geri gönderir. Özel anahtar çipten hiç çıkmadığı için kopya kart bu imzayı üretemez.

**Sayı tek kullanımlık ve süreli.** `identity_challenges` tablosunda tutulur, `consumed_at` damgalanır, `challenge-validity-minutes` (10) sonra geçersizdir. İmza doğrulanamazsa satır tüketilmiş sayılmaz — başarısız deneme kullanıcının sayısını yakmaz.

**İki imza şeması destekleniyor:** RSA için ISO 9796-2 (mesaj kurtarmalı, ICAO'nun tarif ettiği biçim) ve ECDSA için düz imza. Hangisinin kullanıldığı karta göre değişiyor, ikisi de deneniyor.

**Doğrulama anahtarına neden güveniyoruz:** `DG15`'in hash'i SOD'da, yani Passive Authentication zaten onu imzalı kabul ediyor. Uydurma bir DG15 gönderen kişi SOD hash kontrolüne takılır.

**Varsayılan zorunlu değil** (`identity.passive-authentication.active-authentication-required=false`): TCKK'da DG15'in bulunduğu henüz gerçek kartla doğrulanmadı. İmza gelirse doğrulanır, gelmezse geçilir. Kart DG15 taşıdığı teyit edilince bayrak açılmalı — o noktaya kadar tekrar oynatma açığı duruyor.

**Challenge yönetimi `IdentityChallengeManager`'da, `UserManager`'da değil.** `AggregateBoundaryTest` `UserManager`'ın `IdentityChallengeDao`'ya uzanmasını reddetti; muafiyet eklemek yerine ayrı manager açıldı — `EmailVerificationManager` / `PhoneVerificationManager` ile aynı desen.

**Gerçek bir kartla doğrulanmadı.** Kripto zinciri testte **sentetik bir PKI ile uçtan uca kanıtlandı** — `TestChip` kendi CSCA'sını ve Document Signer'ını üretiyor, gerçek bir CMS `SignedData` imzalıyor, DG1'i TD1 biçiminde kuruyor. Gerçek Türk kimlik kartının SOD'unun bu kodla ayrıştığı **prod öncesi bir kartla teyit edilmeli**; bayrak o teyide kadar kapalı kalmalı.

## 2ar. Başvuru kapısı ve iptal önizlemesi

**Hazır olmayan işçi artık başvuramıyor.** Öncesinde kimlik doğrulaması, adres ya da IBAN'ı olmayan biri başvurabiliyordu; hata ancak **işveren başvuruyu kabul ederken** patlıyordu, çünkü alt üye işyeri kaydı o anda açılıyor. Yani işveren, kendi eksiği olmayan bir şey yüzünden hata alıyor ve düzeltemiyordu.

`applyToJob` üç şartı sırayla kontrol ediyor: **kimlik doğrulama → adres → IBAN.** Sıra keyfi değil, işçinin düzeltme sırası: kimlik doğrulanmadan IBAN istemek anlamsız. Her biri kendi mesaj anahtarını taşıyor, yani `errorCode` istemciye hangi ekrana yönlendireceğini söylüyor (`APPLICANT_IDENTITY_NOT_VERIFIED`, `APPLICANT_ADDRESS_REQUIRED`, `APPLICANT_BANK_ACCOUNT_REQUIRED`).

**Şartlar `createSubMerchantDtoForWorker`'ın kontrol ettikleriyle birebir aynı** — kapı ile asıl doğrulama ayrışırsa hata yine kabul anında patlar. Buraya bir alan eklenirse oraya da eklenmeli.

`JobApplicationManager` bu iş için `BankAccountService`'e bağlandı; `BankAccountManager` yalnızca kendi DAO'suna uzandığı için döngü çıkmıyor.

**İptal önizlemesi kayıt açmaz.** `GET /job-assignments/{id}/cancellation-preview` iptal *etmeden* kesintiyi döndürür: `feeRate`, `feeAmount`, `refundAmount`, `noticeMinutes`, `cancellableNow`. `CancellationPolicy` zaten saf bir hesap (§2p), yani yeni kural ya da tablo gerekmedi.

**Uygun olmayan durumda 409 değil `cancellableNow: false` döner.** Önizleme bir karar değil, bir gösterge; istemci butonu buna bakarak kapatıyor ve hata yakalamak zorunda kalmıyor. Taraf olmayan yine **404** alır — `cancel` ile aynı, atamanın varlığı sızmasın diye.

**Önizleme bağlayıcı değil.** Kutu açıkken cezasız pencere kapanırsa gösterilen tutar ile kesilen tutar farklı olabilir. Bunu kilitlemek için önizlemeye bir geçerlilik süresi ve iptalde doğrulama gerekirdi; fark saniyeler mertebesinde olduğu için yapılmadı.

## 2ar2. Başvurunun geri çekilmesi

`POST /job-applications/{applicationId}/withdraw`. Öncesinde `JobApplicationsController` tek uç barındırıyordu ve o da **işverenin** cevabıydı; başvuran tarafında hiçbir çıkış yolu yoktu. İşi başka yerden alan ya da yanlış ilana başvuran kullanıcı `APPLIED` durumunda asılı kalıyor, işveren de aslında müsait olmayan bir adayı kabul edebiliyordu — kabul anında `JobAssignment` doğduğu ve işveren ödeme oturumu açtığı için boşluk doğrudan ödeme akışına dokunuyordu.

**Geri çekme bir durum değişikliğidir, soft delete değil.** `JobApplication` `@SQLDelete` + `@SQLRestriction` taşıyor (§8); geri çekme bunlara hiç dokunmaz, yalnızca `status`'ü `WITHDRAWN` yapar. Kayıt tabloda kalır, `is_deleted` `false` kalır, işverenin başvuru listesinde `WITHDRAWN` olarak görünmeye devam eder. `ApplicationWithdrawalIT` bunu gerçek Postgres'te satır sayısı ve `is_deleted` üzerinden doğruluyor — ikisi karıştırılırsa başvuru geçmişi sessizce kaybolurdu.

**Yalnızca sahibi, yalnızca `APPLIED` durumundan.** Başkasının başvurusu **404** alır (403 değil — kaynağın varlığı sızmaz, §0b). `ACCEPTED` geri çekilemez: orada artık bir `JobAssignment` var ve iptalin kendi akışı, kendi ceza politikası işliyor (§2p). `ACCEPTED` / `REJECTED` / `WITHDRAWN` için **409**.

**Geri çekilmiş başvuru artık kabul edilemez.** `respondToApplication` `WITHDRAWN` durumunda `ConflictException` ile **409** döner ve bunun kendi anahtarı var (`job.application.already.withdrawn`); "zaten yanıtlanmış" mesajı burada yalan olurdu. Bu kapı olmadan geri çekme yalnızca bir etiket olurdu — işveren yine kabul eder, atama ve ödeme oturumu yine doğardı.

**Yeniden başvuru kararı: `WITHDRAWN` bir başvuru yeniden başvuruyu engellemez.** Mükerrer başvuru kapısı `existsByJobIdAndUserId`'den `existsByJobIdAndUserIdAndStatusNot(jobId, userId, WITHDRAWN)`'a daraltıldı. Gerekçe: engelleseydi geri çekme kullanıcı için tek yönlü bir kapı olurdu — fikrini değiştiren ya da yanlışlıkla geri çeken kişi ilan hâlâ `OPEN` iken bir daha başvuramaz, tek çaresi işverene mesaj atmak olurdu; bu da geri çekme düğmesini kullanılmaz derecede korkutucu yapardı. Kötüye kullanım riski (başvur–geri çek–başvur döngüsü) düşük: her yeni başvuru yeni bir satır, yani işverenin gördüğü geçmiş bozulmuyor ve gerekirse ileride sayıya bağlı bir kural eklenebilir. Karar `ApplicationWithdrawalTest` ve `ApplicationWithdrawalIT` içinde iki yönlü test edildi: `WITHDRAWN` engellemiyor, `APPLIED` engellemeye devam ediyor.

**`hasApplied` bilerek daraltılmadı.** O metot mesajlaşma kapısını besliyor (`MessagingManager.assertMayConverse`): "bu ilanla ilişkisi var mı?" sorusunun cevabı. Daraltılsaydı başvurusunu geri çeken kullanıcı ile işveren arasındaki mevcut sohbet aniden **403** dönerdi — oysa geri çekmenin sebebini konuşmak tam da o anda gerekiyor. İki kontrol farklı soruları soruyor: biri "şu an canlı bir başvurusu var mı", diğeri "bu ilana hiç dokundu mu".

**`answeredDateTime` geri çekme anını da taşıyor.** Ayrı bir `withdrawnDateTime` kolonu açılmadı; alan başvurunun terminal duruma geçtiği anı ifade ediyor ve `MyApplicationDto` üzerinden zaten dönüyor. Yeni kolon migration gerektirir, sıralama kümesine (`MY_APPLICATIONS_SORTABLE`) ikinci bir zaman alanı sokar ve istemcide iki alanın hangisine bakılacağı sorusunu doğururdu.

**Migration gerekmedi.** `job_applications.status` `VARCHAR(255)` ve check constraint taşımıyor; enum'a değer eklemek şema değişikliği istemiyor.

**İşverene bildirim gidiyor.** `ActivityNotificationService.applicationWithdrawn` mevcut altyapıyı kullanıyor (in-app + push + e-posta, hepsi ayrı ayrı try/catch içinde). Alıcı `job.getCreator()` — `assignmentAnswered` ile aynı işveren tanımı.

**Mobil istemci bu ucu kullanmadan önce enum'unu genişletmeli.** `koala-mobile` `ApplicationStatus.values.firstWhere` ile ayrıştırıyor; `WITHDRAWN` bilinmeyen bir değerken sunucu bunu döndürürse istemci düşer. Sıra: önce istemcide enum, sonra düğme.

## 2as. Asgari istemci sürümü kapısı

`/v1` bilerek eklenmedi (§9). Yerine `MinimumAppVersionFilter`: istemci `X-App-Version` gönderir, eşiğin altındaysa **426 Upgrade Required** alır ve `errorCategory: CLIENT_TOO_OLD` ile birlikte `X-Minimum-App-Version` başlığı döner — istemci kullanıcıya hangi sürüme çıkması gerektiğini söyleyebilsin diye.

**Eşik `app_settings` içinde, yani deploy'suz değişir.** Eski bir istemciyi aniden engellemek tam da deploy beklemeden yapılmak istenen şey. Ama §2ac'deki ayar yüzeyi sayısal değer zorunlu tutuyordu; `app.minimum-version` için doğrulama sürüm biçimine göre yapılıyor (`AppVersion.isParsable`), diğer anahtarlarda sayısal kuralı aynen duruyor.

**Eşik platforma göre seçilir.** iOS ve Android kendi eşiği yoksa tekil `app.minimum-version` değerine düşer. Başlıksız eski mobil çağrı da tekil eşiği kullanır. Web kendi eşiğini kullanır ve boş web eşiği yalnız web kapısını kapatır; böylece mobil zorunlu sürümü web'i düşürmez.

**Karşılaştırma sayısal, alfabetik değil.** `1.10.0` `1.9.0`'dan büyüktür — string karşılaştırması bunu ters çevirirdi. `+build7` gibi ekler ve eksik bileşenler (`1.4` = `1.4.0`) tolere ediliyor.

**Platform eşiği varken bozuk sürüm kapıyı atlayamaz.** Parse edilemeyen veya eksik web/iOS/Android sürümü 426 alır. Başlıksız eski mobil çağrı geriye uyumluluk için geçer. Muaflar: sağlık uçları, **`/payments/callback`**, `/uploads/**`, takip pikseli ve Swagger; tüm platformlarda geçer.

**Filtre güvenlik zincirinin dışında** (`HIGHEST_PRECEDENCE + 15`), hız sınırından önce: eski istemcinin isteği kimlik doğrulamaya kadar hiç gitmemeli.

## 2at. Bildirim tercihleri ve ticari ileti izni

§2s'de "Faz 9B'de gelecek" diye işaretlenen kanal bazlı tercih burada. Öncesinde `notification-preferences`
araması hiç sonuç vermiyordu: kullanıcı hiçbir e-postayı ya da push'u kapatamıyordu.

**Kapatılabilirlik kategorinin kendisinde, dağınık `if`'lerde değil.** `NotificationCategoryKind` üç
değer taşıyor ve üç soruyu birden cevaplıyor: kapatılabilir mi, varsayılanı ne, ticari ileti mi.

| Kind | Kategoriler | Kapatılabilir | Varsayılan | Ticari |
|---|---|---|---|---|
| `TRANSACTIONAL` | `ACCOUNT_SECURITY`, `APPLICATION_UPDATES`, `IDENTITY_VERIFICATION` | **hayır** | açık | hayır |
| `ALERT` | `JOB_ALERTS`, `COMPANY_UPDATES` | evet | **açık** | hayır |
| `COMMERCIAL` | `MARKETINGS`, `NEWSLETTER` | evet | **kapalı** | evet |

Yeni bir kategori eklendiğinde tek karar `kind` seçmek; varsayılanı, kapatılabilirliği ve rıza kaydı
gerekip gerekmediği oradan türüyor. §2s'deki tablo bu enum'a taşındı, artık dokümantasyon değil kod.

**Satırlar override, tam matris değil.** `notification_preferences` yalnızca kullanıcının *değiştirdiği*
kombinasyonu tutar; satırı olmayan kombinasyon `kind`'ın varsayılanına düşer. Yeni bir kategori eklendiğinde
mevcut kullanıcılar için veri göçü gerekmiyor — kayıt anında 21 satır açan tasarımın tersi.

**İşlemsel bildirimin kapatılamaması iki yerde, ikisi de bilinçli.** `UpdateNotificationPreferencesDto` üzerindeki
`@AssertTrue` istemciye 400 + alan mesajı döndürür (§2ah'nin çapraz alan kuralı); `isAllowed` ise
`kind.isOptional()` değilse **satıra hiç bakmadan** `true` döner. Birincisi API sözleşmesi, ikincisi garanti:
veritabanına elle yazılmış ya da göçten kalmış bir satır bile ödeme bildirimini susturamaz.

**Kapı tek yerde: `NotificationManager`.** `sendEmail`, `sendPushToUser` ve `sendInApp` gönderimden önce
`notificationPreferenceService.isAllowed(...)` soruyor. `ActivityNotificationManager`, `JobAlertManager`,
`CompanyFollowManager` — hepsi zaten buradan geçtiği için tek bir kontrol hepsini kapsıyor ve yeni bir
gönderim noktası eklendiğinde tercihi kontrol etmeyi hatırlamak gerekmiyor. Bastırılan gönderim `null`
döner, `Notification` satırı **açılmaz** ve kuyruğa girmez.

**Alıcısı olmayan e-posta hiçbir zaman bastırılmaz.** Şirket daveti gibi akışlar henüz Koala kullanıcısı
olmayan bir adrese gidiyor; `recipient == null` olduğunda tercih sorulmaz.

**Abonelikten çıkma bağlantısı e-postaya kendiliğinden biniyor, ama yalnızca ticari iletide.** `sendEmail`,
kategori `COMMERCIAL` ise `unsubscribeUrl`'i template parametrelerine ekler ve `emails/layout.html` altbilgisi
onu çizer. Gönderen tarafın bağlantıyı eklemeyi hatırlaması gerekmiyor.

**Neden `optional` değil de `COMMERCIAL`:** ilk hâli kapatılabilir her kategoriye bağlantı koyuyordu ve bu
`COMPANY_UPDATES`'i de kapsıyordu — o kategorinin taşıdığı tek şey şirket kurulumu ve **yetkili daveti**
(`CompanyContactInvitationManager`, `DispatchPriority.CRITICAL`). Eyleme çağıran bir davetin altına "tek tıkla
çıkabilirsin" yazmak yanlış: davet pazarlama değil. Issue #31 de bağlantıyı "pazarlama e-postalarında" diye
istiyor. `JOB_ALERTS` bugün yalnızca push gönderiyor, yani pratikte kaybedilen bir şey yok; iş alarmı
e-postası yazıldığında bu sınır yeniden değerlendirilmeli.

**Açık kalan gerilim:** `COMPANY_UPDATES` kapatılabilir (§2s'deki tablo öyle diyor) ama içinde yetkili daveti
var; kullanıcı bu kategoriyi kapatırsa davetten e-postayla haberdar olmaz. Kategorinin "şirket haberi"
(kapatılabilir) ve "üyelik eylemi" (işlemsel) diye ikiye ayrılması gerekiyor — bu issue'nun kapsamı dışında,
takip issue'su açıldı.

**Token mekanizması parola sıfırlamanın aynısı** (§ `PasswordResetTokenManager`): 32 bayt rastgele,
URL-safe; veritabanında yalnız SHA-256 özeti. `PasswordResetTokenGenerator` **yeniden kullanıldı** — sınıf
`core/security` altında duran, parola sıfırlamaya özgü hiçbir mantık taşımayan genel bir üreteç; ikinci bir
kopya çıkarmak asıl kötü olurdu. Adı tarihsel.

**Ama tek kullanımlık değil, süreli.** Parola sıfırlamadan ayrıldığı tek nokta bu. Gerekçe: eylem idempotent
(bir kanalı kapatmak), ayrıcalık vermiyor ve token **yalnızca kapatabiliyor, hiçbir şeyi açamıyor**. Tek
kullanımlık yapılsaydı bağlantıya ikinci kez tıklayan kullanıcı hata görürdü; sızan bir token'ın yapabileceği
en kötü şey ise zaten kapalı olan bir kanalı tekrar kapatmak. Sızma penceresini süre sınırlıyor:
`UNSUBSCRIBE_TOKEN_VALIDITY_MS`, varsayılan 90 gün — pazarlama e-postası geç okunur.

**Uç `POST`, `GET` değil.** §0b'nin "durum değiştiren işlem kendi POST ucunu alır" kuralı burada da geçerli;
ayrıca e-posta istemcilerinin bağlantı ön-tarayıcıları GET atıyor ve kullanıcı adına abonelikten çıkarırdı.
E-postadaki bağlantı web uygulamasının `/bildirim-tercihleri/cikis?token=...` sayfasına gidiyor, sayfa
açılışta `POST /notification-preferences/unsubscribe` çağırıyor — parola sıfırlama bağlantısının şekliyle
aynı. Kullanıcı için hâlâ tek tık ve giriş gerekmiyor.

**Ticari ileti izni `consent_records`'a yazılmıyor, kendi tablosunda.** `ConsentRecord` bir
`AgreementVersion`'a **zorunlu** olarak bağlı; ADR-0008 sonrası boş veritabanında hiç sözleşme satırı yok ve
tercih ekranını sözleşme kataloğunun kurulmuş olmasına bağlamak kırılgan olurdu. `commercial_message_consents`
`ConsentRecord`'un alanlarını birebir taşıyor (`granted`, `recorded_at`, `ip_address`, `user_agent`) ve
üstüne `channel` ile `source` ekliyor — İYS kanal bazında soruyor. İlişki şu: sözleşmeye verilen onay
şemsiye (`AgreementType.COMMERCIAL_MESSAGE`), kanal bazlı açma/kapama kanıtı bu tablo.

**Çekilen izin de kaydediliyor**, verilen izinle aynı özenle — §2n'deki "sormadık ile sorduk-reddetti ayrımı"
gerekçesinin aynısı. Abonelikten çıkma bağlantısıyla gelen kayıt `source = UNSUBSCRIBE_LINK` taşıyor.

**SMS tercih yüzeyinde yok, bilerek.** `NotificationChannel.SMS` `preferable = false`; issue #31 kanal
listesini `EMAIL`/`PUSH`/`IN_APP` olarak veriyor ve ticari SMS İYS'nin asıl konusu. Bayrağın açılması İYS
dilimine ait (`docs/research/iys-commercial-message-integration.md`).

**İYS entegrasyonu bu dilimde yapılmadı.** Kapsam, zamanlama, gerekçe ve açık kalan hukuk sorusu
`docs/research/iys-commercial-message-integration.md` içinde; takip issue'su Dalga 7 altında.

---

## 2au. Personel yetkilendirmesi — `/admin/**` ikinci zinciri

`/admin/**`'ın hangi kimliği kabul ettiği burada değil, [ADR-0010](docs/adr/0010-split-staff-identity-from-koala-authorization.md) §Geçiş'te yazılıdır. Burada yalnız mekanizma anlatılır.

**İki zincir var, ikisi de `SecurityFilterChain` bean'i.** `StaffSecurityConfig` `@Order(1)` ile `/admin/**`'ı alır, `SecurityConfig` `@Order(2)` ile geri kalanı. Sıralama açıkça verilmek zorunda: iki zincirli bir uygulamada bean tanım sırası güvenilir değildir. `SecurityConfig`'teki `/admin/**` → `hasRole("ADMIN")` satırı duruyor ama artık erişilmiyor; kesim o satırın silinmesidir ve `ApiConventionTest` onu bugün hâlâ arıyor.

**Kimlik ayrımı bearer token çözücüsünde yapılır.** Marketplace token'ı bir OAuth2 bearer JWT değil; `iss` doğrulanmıyor, geçerliliği `sessions` satırına bağlı. `StaffBearerTokenResolver` token'ın `iss` claim'ini **doğrulamadan** okur ve güvenilen issuer listesinde değilse token'ı resource server'a hiç vermez — istek `SessionAuthFilter`'a düşer. Doğrulama `JwtIssuerAuthenticationManagerResolver` üzerinden yapılır; allow-list orada, `iss`'e körü körüne güvenilmez.

**Resource server kendi `ProviderManager`'ını kullanır.** `HttpSecurity`'nin paylaşılan yöneticisi kullanılsaydı, başarısız bir JWT doğrulaması `ProviderManager`'ın parent'ına düşer, parent global `authenticationManager` bean'i olduğu için kendine döner ve **süresi dolmuş her token `StackOverflowError` üretirdi**. Süresi geçerli token'larda bu hata görünmez; testte yakalandı.

**JWKS tembel çekilir.** `NimbusJwtDecoder.withJwkSetUri(...)` açılışta ağa çıkmaz; `withIssuerLocation(...)` çıkardı ve `auth.iskoala.com` çözülmediği için uygulama açılmazdı. `koala.staff.issuer-uri` boşken zincir OAuth2 bloğunu hiç kurmaz.

**Anotasyon yoksa reddedilir.** `StaffPermissionAuthorizationManager` isteği `RequestMappingHandlerMapping` üzerinden handler'a bağlar ve `@RequiresPermission` arar; yoksa reddeder. Bu yüzden eşleşmeyen bir `/admin/...` yolu 404 değil 403 döner — güvenlik kararı fail-closed verilir. `RequestMappingHandlerMapping` `ObjectProvider` ile alınır; doğrudan enjekte edilseydi güvenlik zinciriyle bean döngüsü çıkardı.

**İzinler token'a girmez.** Token yalnız `realm_access.roles` taşır; rol→izin çözümü her istekte veritabanından, DAG aşağı gezilerek yapılır. Tanınmayan rol adı ve tanınmayan izin kodu **loglanıp atlanır** — bilinmeyen bir kod yüzünden uygulamanın açılmaması rollback'i imkânsızlaştırırdı. Bunu `StaffRoleGraphSeedTest` CI'da yakalar.

**`StaffPermission` metadata taşır.** Her sabit `code`, `mutating` ve `sensitivity` (`NORMAL`/`PERSONAL_DATA`/`FINANCIAL`) verir; denetim kaydı bunları okuyarak neyin yazılacağına ve satırın ne kadar saklanacağına karar veriyor (§2av). `@RequiresPermission` ayrıca non-GET uçlarda bir `action` adı ister. `AdminPermissionMappingIT` dört şeyi birden sabitliyor: her `/admin` mapping'i anotasyon taşır, adı geçen her izin gerçek bir sabittir, her non-GET bir eylem adı verir ve **bir yazma ucu okuma izniyle korunamaz**. İzin adı vermeyen tek anotasyon `/admin/me`'dedir; test bunun başka yerde kullanılmamasını da kontrol eder.

**Roller ADR-0010 gereği Keycloak'ta, izinler Koala'da.** Rol grafiği `06-20-staff-authorization.xml` ile seed'lenir çünkü Keycloak kapısı açıldığı anda arkası boş olmamalıdır. Grafik `koala-admin/docs/keycloak-setup.md` §3'teki composite rollerle birebir aynıdır ve görev ayrılığı (OWNER dışında hiçbir rol hem trust hem finans dalını kapsayamaz) testle sabitlenir.

**Bilinen 12 izne üç tane eklendi.** `content.moderate` (şikayet incelemesi ve görsel kaldırma), `badge.manage`, `settings.manage`. Sebep: `AdminPermissionMappingIT` her yönetim ucunun bir izin adı vermesini şart koşuyor ve bu üç yüzey listedeki hiçbir izne düşmüyordu. Alternatif, şikayet incelemesini `dispute.resolve` ile korumak olurdu; o da `DISPUTE_HANDLER`'a görsel silme yetkisi verirdi.

**Dördüncüsü `user.role.manage`.** #35'in açtığı üç uç (§2f3) bu dilim indikten sonra izinsiz kaldı; anotasyon yoksa zincir reddettiği için `UserRoleGrantIT`'in sekiz HTTP testi 403 aldı — kapı doğru çalıştı, eksik olan izin adıydı. `GET /admin/users/{userId}/roles` mevcut `user.read`'e düşüyor; `POST` ve `DELETE` yeni `user.role.manage` (mutating, `NORMAL`) ile korunuyor, denetim eylem adları `user.role.grant` ve `user.role.revoke`.

**`role.manage` bilinçli olarak kullanılmadı.** `role.manage` personel tarafındaki rol→izin DAG'ını yönetir; `user.role.manage` ise marketplace kullanıcısına `ROLE_ADMIN` verir. İkisi aynı izin olsaydı, personel rollerinin izinlerini düzenleyebilen biri aynı yetkiyle marketplace admini basabilirdi; `keycloak-setup.md` §3'ün görev ayrılığı kuralı tam burada delinirdi. Gerekçe ADR-0010'un `staff.manage`/`role.manage` ayrımını yapma gerekçesiyle aynı. `user.read_pii` de uygun değildi: rol listesi kişisel veri değil, ayrıca okuma izni yazma ucunu koruyamıyor (`AdminPermissionMappingIT`).

**`user.role.manage` yalnız `OWNER`'a seed'lendi.** Marketplace `ROLE_ADMIN` basmak sistemdeki en geniş etkili işlem; `staff.manage` ve `role.manage` de aynı sebeple yalnız `OWNER`'da. Destek ya da güven dalına verilseydi, kendi hesabına admin rolü yazıp bütün izin grafiğini atlamak tek istek olurdu. `SUPPORT_AGENT` rol listesini zaten `user.read` üzerinden görebiliyor; yazma ayrı bir karar olarak `OWNER`'da bırakıldı.

**Personel tabloları `BaseEntity`'yi genişletmez.** `BaseEntity` `created_by_id`/`updated_by_id` üzerinden `users`'a yabancı anahtar kurar; ADR-0010 iki yönde de bağ kurulmamasını şart koşuyor.

**Para taşıyan metotlara anotasyon konmaz.** `PayoutService`/`PayoutManager` metotlarını otomatik tamamlama zamanlayıcısı boş bir `SecurityContext` ile çağırıyor; anotasyon konsaydı zamanlayıcı sessizce dururdu. Personel tarafı `StaffPayoutManager` facade'ından geçer: aktörü parametre olarak alır, izni denetler, sonra korumasız iç metodu çağırır. Aktör `null` ise istek köprü aşamasındaki marketplace `ROLE_ADMIN`'dendir.

---

## 2av. Admin denetim kaydı — ikiye bölünmüş iz

Öncesinde admin eylem kaydı **yoktu**. `created_by_id`/`updated_by_id` üzerine yazılıyor, okuma hiç kaydedilmiyordu; `app_settings`'in denetim kolonu §2ac ile geldiğinde bile "kim değiştirdi" sorusunun cevabı tek bir `log.info` satırıydı ve log döngüsü dolunca kayboluyordu. `cancellation.late-fee-rate` — doğrudan para etkileyen sayı — iz bırakmadan değiştirilebiliyordu.

**İki tablo, iki saklama süresi.** Ayrım ilk satır yazılmadan yapıldı; sonrası yasal süre baskısı altında veri migrasyonu olurdu.

| | Tablo | İçerik | Süre |
|---|---|---|---|
| **Çekirdek** | `admin_audit_entries` | kim, hangi izin, hangi eylem, hangi kayıt id'si, ne zaman, izin/ret, `mutating`, `sensitivity` | 24 ay — `FINANCIAL` ise **10 yıl** |
| **Detay** | `admin_audit_entry_details` | istek gövdesi, IP, user agent, önce/sonra değerler | 6 ay ya da KVKK silme talebi |

Detay yabancı anahtarı `ON DELETE CASCADE`: çekirdek gidince detay da gider. Ters yön serbesttir ve KVKK silme talebinin yaptığı tam olarak budur — detay silinir, çekirdek kalır.

**Süreler satır yazılırken damgalanıyor.** `retain_until` ve `purge_after` birer kolon; temizlik işi tek bir `where` cümlesi oluyor ve "bu satır ne kadar kalacak" sorusu satıra bakılarak cevaplanıyor. Süreyi çalışma zamanında hesaplasaydık, `audit.retention.*` değeri değiştiğinde geçmiş satırların ömrü de geriye dönük değişirdi.

**Yazma, yetki kararının verildiği yerde.** `StaffPermissionAuthorizationManager` kararı verdikten sonra koşulsuz olarak `StaffAuditRecorder.observe(...)` çağırıyor. Loglamayı unutmanın tek yolu `@RequiresPermission`'ı kaldırmak, o da isteği zaten reddettirir (§2au). Kapsam kararı manager'lara dağılmadı: tek kural `core/helpers/AuditScope` içinde ve birim testi var.

**Satır istek sonunda tek seferde yazılıyor, bilinçli olarak.** `StaffAuditFilter` kaydı yetki katmanından alıp `finally` bloğunda yazıyor. Sebep: istek gövdesi ancak handler onu okuduktan sonra elde edilebiliyor (`ContentCachingRequestWrapper` okunmayanı önbelleğe almaz) ve önce/sonra değerleri o ana kadar bilinmiyor. Filtre yetki filtresinden **önce** zincire giriyor, yani reddedilen istekte de çalışıyor. Garanti kaybolmuyor: kaydı üreten çağrı hâlâ yetki kararının içinde, filtre yalnızca boşaltma mekanizması.

**Kapsam kuralı — `AuditScope.isAuditable`:**

1. Reddedilen istek **daima** yazılır (`allowed = false`). Yetki yükseltme denemesini görünür kılan tek şey o satır.
2. Her değişiklik yazılır. `mutating` izin sabitinden okunur **ve** metot GET değilse geçerlidir; `GET /admin/companies` `company.approve` iznini taşıyor ama hiçbir şeyi değiştirmiyor.
3. Hassas okuma yazılır: izinlerin en yükseği `NORMAL` değilse (`PERSONAL_DATA` ya da `FINANCIAL`), filtre olmasa bile.
4. Tek bir kaydı hedefleyen okuma yazılır (yolda değişken varsa).
5. Filtreli arama yazılır. `/admin/users` araması `lower(email) LIKE %query%` çalıştırıyor: bir e-posta yazmak o kişinin Koala kullanıcısı olduğunu **doğruluyor**. Yanıt DTO'su temiz (`SafeUserDto`) ama **sorgunun kendisi kişisel veri.**
6. Geri kalan yazılmaz — filtresiz liste yüklemesi loglanmaz. `page`, `size` ve `sort` filtre sayılmaz; sayfa çevirmek arama değildir.

**Kimliksiz istek denetlenmiyor.** Anonim bir `/admin/**` isteği 401 alır ve satır açmaz: kaydedilecek bir aktör yok, üstelik herkesin tabloyu doldurabilmesi olurdu. Anotasyonu olmayan (yani eşleşmeyen) bir `/admin/...` yolu da yazılmaz — `AdminPermissionMappingIT` her gerçek mapping'in anotasyon taşıdığını garanti ettiği için böyle bir istek var olmayan bir uca gidiyordur.

**Denetim kaydını okumak da denetleniyor ve özyinelemiyor.** `audit.read` `PERSONAL_DATA` olduğu için `GET /admin/audit-log` üçüncü kurala giriyor. Özyineleme diye bir şey oluşmuyor çünkü denetim yazması bir veritabanı `INSERT`'ü, yeni bir HTTP isteği değil: güvenlik zinciri istek başına bir kez değerlendiriliyor, yani okuma **tam olarak bir** satır üretiyor. Üstelik satır yanıt üretildikten sonra yazıldığı için okuma **kendi satırını hiçbir zaman görmüyor**. Büyüme çarpan değil toplama: iki okuma iki satır. `AdminAuditTrailIT` ikisini de sabitliyor — ilk okuma boş sayfa döndürüyor, ikincisi tek satır görüyor ve tabloda iki satır oluyor.

**Aktör alanları — `koala-admin` için nullability sözleşmesi.** `actorDisplayName` daima dolu. `actorStaffId` iki durumda null: token geçerli ama `staff_members` kaydı yok (o zaman ad Keycloak subject'i olur, §2au), ve istek köprü aşamasındaki marketplace `ROLE_ADMIN`'den geliyor (o zaman marketplace kullanıcı id'si yazılır, kaydı olmayan test principal'lerinde null kalır). `resourceId` **null yerine boş string**: liste ya da arama isteğinin hedeflediği tek bir kayıt yoktur ve panelin null denetimi yapmak zorunda kalması istenmedi. `permission` izin adı vermeyen tek anotasyonda (`/admin/me`) `none`, `action` ise okuma uçlarında izin koduna düşer.

**Saklama işi `AdminAuditRetentionScheduler`'da**, günde bir. Önce detay, sonra çekirdek siliniyor; ikisi ayrı adım çünkü detayı süresi dolduğunda düşürmek çekirdeğe dokunmadan yapılmalı. Hata koşuyu düşürmez, loglanır. `audit.retention.enabled=false` ile tümüyle susturulur (testlerde kapalı). Zamanlayıcı tek örnek varsayıyor (§7, Faz 11D) ama silme idempotent olduğu için ikinci örnek zarar vermez.

**KVKK silme talebi `LegalManager.requestAccountDeletion` üzerinden tetikleniyor.** Talep kabul edildiğinde o kullanıcının id'sini `resource_id` olarak taşıyan satırların **detayı** siliniyor, çekirdek kalıyor. Açık yükümlülük yüzünden reddedilen talep hiçbir şey silmiyor. **Sınırı yazılı olsun:** eşleşme `resource_id` üzerinden, yani `/admin/users/{id}/...` satırlarını yakalıyor; o kişinin adı başka bir kaydın istek gövdesinde geçiyorsa o detay bu yolla silinmez. Kapsamı genişletmek gövde içinde arama demek ve bugün gerekmedi.

**Önce/sonra değerleri `StaffAuditContext.capture` ile veriliyor.** Bugün tek çağıran `SettingsManager.update`; issue'nun şikayet ettiği `cancellation.late-fee-rate` artık iz bırakmadan değiştirilemiyor. Yeni bir yazma ucunun önce/sonra taşıması isteniyorsa manager'a tek satır ekleniyor, denetim satırı zaten yazılıyor.

**İstek gövdesi yalnızca JSON'da ve 8 KB'a kadar önbelleğe alınıyor.** Multipart yükleme (rozet görseli) sarmalanmıyor — akışı tamponlamak yüklemeyi iki kez belleğe almak olurdu. Reddedilen istekte gövde hiç okunmadığı için `requestBody` null kalır: yapılmamış bir yazmanın gövdesini saklamanın değeri, reddedilen her isteğin gövdesini tamponlama maliyetinden düşük.

**Async dispatch bugün kapsamda değil.** `OncePerRequestFilter` varsayılan olarak async dispatch'te çalışmaz, yani `/admin/**` altına bir gün `DeferredResult`/`Callable` döndüren bir uç eklenirse satır handler bitmeden yazılır ve önce/sonra değerleri kaçar. Bugün böyle bir uç yok; eklenirse filtre `shouldNotFilterAsyncDispatch` üzerinden gözden geçirilmeli.

**Denetim yazması patlarsa istek düşmüyor, `ERROR` loglanıyor.** Satır o noktada yanıt zaten istemciye gitmiş oluyor; exception fırlatmak başarılı bir 200'ü sonradan 500'e çeviremez, yalnızca gürültü üretirdi. Bu bilinen bir boşluk: veritabanı hatası satır kaybettirir. "Unutmak imkânsız" garantisi kod yolu içindir, veritabanı dayanıklılığı için değil.

**Sorgu üç yerde Postgres'in null tip çıkarımından kaçıyor.** `cast(:x as Instant)` HQL'de tanınmıyor (`unrecognized cast target type`), `cast(:x as Boolean)` ise geçerli HQL ama null'ı `bytea` olarak gönderip `cannot cast type bytea to boolean` veriyor. Bu yüzden `from`/`to` boşken sınır değerlere (`Instant.EPOCH` ve `9999-12-31`) düşüyor ve `allowed` için ayrı bir `filterByOutcome` bayrak parametresi var. Metin süzgeçleri §2l'deki `cast(:query as string)` deyimini kullanıyor.

**Denetim tabloları `BaseEntity`'yi genişletmez ve soft delete taşımaz.** §2au'nun gerekçesi (personel tarafı `users`'a yabancı anahtar kurmaz) burada da geçerli; ayrıca saklama işinin işi zaten **fiziksel** silmek. Soft delete edilen bir denetim satırı saklama süresini hiç doldurmazdı.

**Detay 1:1 ilişkisi `@MapsId` ile çekirdeğin anahtarını paylaşıyor** ve okuma sorgusu `left join fetch` kullanıyor: sayfa başına 20 satır için ikinci bir sorgu turu açılmıyor (§2af). To-one fetch join sayfalamayı belleğe taşımıyor, o tuzak koleksiyon fetch'ine ait (§2l).

---

## 3. Testler

`src/test/resources/application.properties` testlerin ayağa kalkması için gereken minimum değerleri sağlar. Buradan bir satır silmeden önce neye yaradığını bil:

- `jwt.secret` — `JwtService` açılışta Base64 çözülmüş anahtarı HS256 için en az 32 bayt bekliyor, kısa anahtarda context düşer.
- ~~`r2.*`~~ — **artık gerekmiyor.** `storage.type=local` varsayılanı sayesinde testler hiçbir bulut ayarı istemiyor (§2ab).
- `app.api-url` / `app.web-base-url` ve `payments.return-base-url` — doğrulanmış yapılandırmalardır, boş bırakılamaz.
- `spring.mail.*` — `EmailConsumer` constructor'ında `JavaMailSender` istiyor. Bu değerler yoksa Boot mail bean'ini hiç oluşturmaz ve context "No qualifying bean of type JavaMailSender" ile düşer. Testlerde gerçek SMTP'ye bağlanılmaz.
- `spring.rabbitmq.listener.*.auto-startup=false` — testlerde RabbitMQ'ya bağlanılmıyor.
- `spring.datasource.hikari.maximum-pool-size=4`, `minimum-idle=0`, `idle-timeout=10000` — aşağıdaki bağlantı bütçesi.

Datasource'u `AbstractIntegrationTest` içindeki Testcontainers sağlar. Konteyner `static` ve tüm entegrasyon testleri aynı örneği paylaşır; sınıf başına yeni Postgres kaldırmak paketi dakikalarca uzatıyor.

**Bağlantı bütçesi: her önbelleğe alınan context tek Postgres kabından pay alır.** Spring TestContext her farklı yapılandırma için ayrı bir context açar ve kapatmaz — `@DynamicPropertySource` metot kümesi, `@MockitoBean` seti, `@TestPropertySource`, `@ActiveProfiles` ve `@AutoConfigureMockMvc` hepsi ayrı bir önbellek anahtarı üretir. Her context kendi Hikari havuzunu kurar; **Hikari varsayılanı `maximumPoolSize=10` ve `minimumIdle` ona eşit**, yani boşta duran bir context bile on bağlantıyı tutmaya çalışır. `postgres:17` kabı varsayılan `max_connections=100`, `superuser_reserved_connections=3` ile geliyor — sıradan kullanıcıya 97 kalıyor.

Bu sessiz bir tavandır ve **çarpma bir gün tutmaz**: #35 ile paket on context açıyordu (100 istek, 97 arz — sınırın hemen dibi, hâlâ yeşil), personel dilimi `AbstractStaffIntegrationTest` ile ikinci bir `@DynamicPropertySource` kümesi getirince on birinciyi açtı ve tavan aşıldı. Belirti yanıltıcıydı: `/admin/**` ile hiç ilgisi olmayan `PasswordResetIT` beş testinde "Failed to load ApplicationContext" verdi — o context sırada **sonuncu** yüklendiği için bağlantısız kalan oydu. Gerçek sebep yığının dibindeki `PSQLException: FATAL: sorry, too many clients already`; log'daki `ConnectException: Connection refused` satırları RabbitMQ'ya ait ve `main`'de de var, ilgisiz.

Çözüm havuzu küçültmek, kabı büyütmek değil: context sayısını kontrol edemiyoruz (36 `*IT` sınıfı, her yeni test tabanı bir tane daha açabilir), havuz boyutunu edebiliyoruz ve MockMvc testleri tek iş parçacıklı, dörtten fazlasını hiç kullanmıyor. `minimum-idle=0` boştaki context'in havuzunu doldurmasını engelliyor, `idle-timeout` da tuttuğunu geri veriyor. `IntegrationTestConnectionBudgetTest` aritmetiği sabitliyor: yirmi context bütçesi × havuz boyutu ≤ 97. Havuz kapağını kaldıran ilk commit'te kırmızıya döner.

**Yetkilendirme testleri saf birim testi.** `JobApplicationAuthorizationTest` ve `PaymentSessionAuthorizationTest` Spring context'i kaldırmadan, mock'lanmış bağımlılıklarla manager'ı doğrudan kurar. İkisi de yazıldığında kırmızıydı: yabancı bir kullanıcı hem başvuru kabul edebiliyor hem ödeme oturumu başlatabiliyordu. Yeni bir yetki dalı eklerken bu iki dosyaya karşılık gelen test yazılmalı.

**Faz 10/10B testleri de saf birim testi.** `MessagingAuthorizationTest`, `ReviewRulesTest`, `AssignmentOperationsAuthorizationTest`, `ReportRulesTest` ve `CancellationPolicyTest` Spring context'i kaldırmadan çalışır. Yeni bir yetki dalı ya da para kuralı eklerken karşılığı bu dosyalara yazılır.

**Faz 11C testleri.** `WorkerToolsRulesTest` on yedi kural sabitliyor: deneyim sahipliği ve tarih tutarlılığı, müsaitlik aralıklarının toptan değişmesi ve çakışma reddi, geçmiş tarihe istisna yasağı, talent pool'da kendini ekleme yasağı ve idempotanlık, şablonun başkasının ilanından çıkarılamaması, kriteri olmayan alarmın reddi ve alarm sayısı sınırı. `UserProfileContractTest` deneyimin artık yapılandırılmış döndüğünü doğruluyor.

**Kontenjan, ödenmemiş atama ve değerlendirme bildirimi testleri.** `JobHeadcountRulesTest` dört kural sabitliyor, hepsi bir **yokluğu** koruyor: kontenjansız ilan da işe alır, kontenjanın üstüne çıkmak engellenmez, kontenjan dolunca ilan kapanmaz, iş başladıktan sonra da işe alım yapılabilir. `UnpaidExpiryRulesTest` dokuz kural: ödenmemiş atama düşer, başarısız ödeme denemesi de düşer, emanetteki para dokunulmaz kalır, kabul edilmemiş teklife dokunulmaz, kayıt işveren tarafına sıfır tutarla yazılır, iki tarafa da bildirim gider, ikinci koşu zararsız, bir atamadaki hata partiyi düşürmez, kapalıyken hiç çalışmaz. Değerlendirme bildirimi `ReviewRulesTest` içinde: puan verildiğinde gider, reddedilen değerlendirmede hiç gitmez.

**Duvar saati kuran testler yapılandırılmış zaman dilimini kullanmak zorunda.** `Job.startDateTime` ve `endDateTime` duvar saati (§2h); `CancellationPolicy`, otomatik tamamlama ve ödenmemiş atama zamanlayıcısı bunları **`Europe/Istanbul` varsayarak** `Instant`'a çeviriyor. Test `LocalDateTime.now().plusHours(2)` yazarsa sonuç makinenin saat dilimine bağlı olur: geliştirme makinesi Istanbul'da yeşil, **CI runner'ı UTC'de kırmızı** — üç saat geriye kayan değer geçmişe düşüp cezasız pencereyi geç iptal cezasına çevirir. `CancellationPreviewTest` tam olarak böyle kırıldı.

Kural: bu tür testlerde başlangıç zamanı `LocalDateTime.now(POLICY_ZONE)` üzerinden kurulur. Şüphedeysen `TZ=UTC ./mvnw verify` ile çalıştır — CI'ın gördüğü budur.

**Active Authentication testleri.** `ActiveAuthenticationTest` yedi kural — sentetik RSA ve EC çip anahtarlarıyla: RSA çip sunucunun sayısını imzaladığını kanıtlar, EC çip de öyle, başka bir sayı için üretilmiş imza reddedilir, **yakalanmış imza yeni sayıya karşı geçmez**, bozulmuş imza reddedilir, anahtar taşımayan DG15 reddedilir, başka çipin anahtarı doğrulamaz. `IdentityChallengeRulesTest` altı kural: sayı 8 rastgele bayt ve saklanır, iki sayı asla aynı olmaz, tanınmayan sayı reddedilir, süresi dolan reddedilir, imza tutmayınca sayı tüketilmiş sayılmaz, başka kullanıcının sayısı bulunamaz.

**Paketlenmiş çıpa testleri.** `BundledTrustAnchorsTest` üç kural: imaj Türkiye çıpalarıyla geliyor, explicit EC parametreli sertifikalar da yükleniyor (varsayılan sağlayıcı bunları düşürüyordu), ve en az bir çıpa bugün hâlâ geçerli — hepsi süresi dolmuşsa liste yenilenmeli demektir ve test kırmızıya döner.

**Faz 27 testleri.** `PassiveAuthenticationTest` dokuz kural — hepsi **sentetik ama gerçek** bir PKI'ya karşı: `TestChip` kendi CSCA'sını ve Document Signer'ını üretip gerçek bir CMS `SignedData` imzalıyor. Sabitlenenler: geçerli çip okuması imzalı kimliği döndürür, aynı SOD aynı parmak izini verir, kurcalanmış DG1 reddedilir, tanınmayan ülkenin imzası reddedilir, bozulmuş imza reddedilir, SOD olmayan çöp reddedilir, güven çıpası yokken hiçbir şey doğrulanmaz, MRZ etiketli DG1'den ayrıştırılır, TD1 olmayan alan reddedilir. Ayrıca DG11 için dört kural: ad ulusal karakterlerle DG11'den okunur, kurcalanmış DG11 reddedilir, DG11 yokken doğrulama yine geçer, adı olmayan DG11 ölümcül değil. Ve yedi MRZ kuralı: kart sahibinin adı iddia edilenle eşleşmeli, Türkçe karakter katlanır, 30 karakterde kırpılan uzun ad kilitlemez, TCKN ayırıcıdan sonra okunur, ayırıcısız yerleşim de okunur, kırpılmış belge numarası TCKN sanılmaz, 11 haneden farklı optional data reddedilir. `IdentityVerificationRulesTest` yedi kural kazandı: bayrak kapalıyken kanıt istenmez, açıkken zorunlu olur, doğrulanan çip kanıtı hesaba damgalar, çiple uyuşmayan TCKN ve doğum tarihi reddedilir, kriptografik hata kendi kodunu taşır, base64 olmayan kanıt kriptografiye hiç gitmez.

**Bildirim akışı testleri.** `NotificationFeedRulesTest` dokuz kural: kayıt hem başlığı hem cümleyi taşır, hedefini taşır, teslimat beklemeden `SENT` olur, akış yalnızca `IN_APP` döndürür, rozet yalnızca onu sayar, toplu işaretleme yalnızca ona dokunur, tek bildirim okundu işaretlenir, zaten okunmuş olan değişmez, başkasının bildirimi işaretlenmez. `ActivityNotificationRulesTest` üç kural kazandı: her olay akışa bir kez düşer, hedefini taşır, akış kaydı patlasa da diğer kanallar gider.

**Faz 28 testleri.** `SmsConsumerTest` yedi kural: kuyruktaki mesaj teslim edilip `SENT` işaretlenir, ticari kategori İYS bayrağını taşır, işlemsel taşımaz, sağlayıcı hatası kaybolmaz `FAILED` olur, kaydı olmayan görev hiç gönderilmeden düşer, gövdesi üretilemeyen görev boş SMS atmaz, tanınmayan şablon tahmin edilmez. `MinimumAppVersionTest` on bir kural: güncel ve daha yeni istemci geçer, eski 426 alır, yanıt gerekli sürümü taşır, başlıksız ve ayrıştırılamayan sürüm engellenmez, sağlık ucu ve sağlayıcı callback'i hiç engellenmez, boş eşik kapıyı kapatır, sürümler sayısal karşılaştırılır, ayar satırı yoksa `application.yaml` değerine düşülür.

**Kimlik kapısı testleri.** `IdentityVerificationRulesTest` altı kural: doğrulama artık hiçbir dış servise bağlı değil, ad Türkçe locale ile büyütülüyor, belge numarası saklanıyor, başka hesapta kayıtlı TCKN 409 alıyor, ikinci doğrulama hâlâ reddediliyor, rozet/bildirim/Iyzico senkronizasyon event'i çalışmaya devam ediyor. `EmployerIdentityGateTest` üç kural: doğrulanmış bireysel işveren ilan açabilir, doğrulanmamış açamaz, kapı adres oluşturulmadan önce çalışır. `PaymentSessionAuthorizationTest` iki kural kazandı: doğrulanmamış bireysel işveren ödeme başlatamaz ve alt üye işyeri hiç açılmaz, şirket ilanı bu kapıdan etkilenmez.

**Faz 26 testleri.** `OutboundHttpClientTest` giden HTTP istemcisinin JDK olanı olmadığını ve Boot varsayılanının hâlâ o olduğunu sabitliyor. `ApplicationReadinessTest` altı kural: hazır işçi başvurabilir, kimliği doğrulanmamış başvuramaz, adressiz başvuramaz, IBAN'sız başvuramaz, boş IBAN eksik sayılır, kimlik IBAN'dan önce kontrol edilir. `CancellationPreviewTest` sekiz kural: işçi kesintisiz tam iade görür, işveren pencere içinde kesintisiz, pencere dışında geç iptal oranıyla, iş başladıysa iade yok, zaten iptal edilmiş ve tamamlanmış atama `cancellableNow: false` döner (409 değil), önizleme hiçbir iz bırakmaz, taraf olmayan 404 alır. `AnonymousReviewMappingTest` ve `ReviewRulesTest` anonim değerlendirmeyi sabitliyor: adlı değerlendirme yazarını taşır, anonim taşımaz, liste eşlemesi de gizler, anonim kayıt yazarını veritabanında tutar, puanı ortalamaya girer, ikinci değerlendirme anonim de olsa 409 alır.

**Iyzico protokol testleri.** `IyzicoProtocolTest` on kural sabitliyor — sahte bir HTTP sunucusuna karşı gerçek `RestTemplate` ile: uç yolları dokümandaki değerlerden sapmıyor, sorgulama doğru adrese gidiyor, her istekte `x-iyzi-rnd` var, başlıktaki rastgele anahtar imzalanan anahtarla aynı, imza `randomKey + yol + gövde` üzerinden üretiliyor, reddedilen ödeme exception değil sonuç döndürüyor, işlem kimliği ve tutar `itemTransactions[0]`'dan okunuyor, `status: failure` gövdesi 502'ye çevriliyor, 400 ile dönen hata gövdesi yine de ayrıştırılıyor, iade ondalık tutarla gidiyor. `PaymentCallbackTest` beş yeni kural kazandı: reddedilen ödeme emanet açmaz, dolandırıcılık reddi düşer, inceleme `IN_REVIEW`'da bekler, eksik tahsilat reddedilir, farklı para birimi reddedilir. `PayoutReleaseTest` iade yolunu sabitliyor: tam iade karta döner ve disapprove gerektirmez, kısmi iade her ikisini de kuyruğa alır.

**Faz 25 testleri.** `EmployerSurfaceRulesTest` beş kural sabitliyor: profil sokak adresini asla taşımaz, yabancı `cvUrl` görmez, başvurulan işveren görür, kişi kendi bağlantısını her zaman görür, özgeçmiş yoksa alan null kalır. `RateLimitBucketTest` yedi kural: okuma sınırlanmaz, yüklemeler kaynaklar arası tek bütçeden düşer, kovalar birbirini tüketmez, mesajın kendi bütçesi var, listelenmemiş yazma ucu da kapsanır, giriş yol bazlı kalır, iki istemci bütçe paylaşmaz. `UploadValidatorTest` sıkıştırma bombası savunmasını ve uzantı beyaz listesini sabitliyor.

**Faz 24 testleri.** `ApiDocumentationTest` dört kural sabitliyor: her uçta `@Operation`, her uçta `@ApiResponse`, her controller'da `@Tag`, ve dokümandaki açık/korumalı ayrımının `SecurityConfig` ile uyuşması. `OpenApiDocumentIT` belgeyi gerçekten üretip beş özelliğini doğruluyor. `EmailTemplateTest` şablon adı–dosya eşleşmesini, her şablonun işlenebildiğini ve `/api` kalıntısı kalmadığını koruyor.

**Faz 23 testleri.** `CompanyFollowRulesTest` dokuz kural sabitliyor: ikinci takip kayıt açmaz, ilk takip kaydedilir, takip edilmeyeni bırakmak sessiz, bireysel ilan kimseye bildirmez, ilan sahibi kendi ilanı için bildirilmez, bir teslimat hatası kalanları düşürmez, özellik tümüyle susturulabilir, işveren istatistikleri değerlendirme yokken sıfıra düşer ve varken `RatingSummary`'den okur.

**Faz 22 testleri.** `MyWorkFeedTest` yedi kural sabitliyor: iki liste tek etiketli zaman çizelgesinde birleşir, en yeni önce gelir, her satır yalnızca kendi yükünü taşır, işveren modunda başvuru servisi hiç çağrılmaz, `limit` birleşik listeyi keser, saçma bir `limit` reddedilmez kırpılır, sıfır yine de bir satır ister.

**Faz 21 testleri.** `GalleryRulesTest` on üç kural sabitliyor: ilk görsel kapak olur, ikincisi kapağı çalmaz, sınır aşımı 409, ilan galerisi şirket galerisinden seçebilir, yabancı görsel 404, bireysel ilanın seçecek şirket galerisi yok, kapaksız istek yine de kapak üretir, kapak silinince sıradaki devralır, listeden düşen kayıt silinir, kapak URL'leri tek sorguda toplanır, aynı görsel iki kez listelenemez, iki kapak seçilemez.

**Komisyon oranı çalışma zamanı testleri.** `CommissionRateRuntimeIT` üç kural sabitliyor: tabloda satır yokken env varsayılanı uygulanır, satır yazıldığında yeni oran **yeniden başlatmadan** geçerli olur, ve oran değiştikten sonra daha önce fiyatlanmış `jobs`/`job_assignments`/`payments` satırlarının `commission_rate` sütunu değişmez — bu sonuncusu ADR-0002'yi koruyor. `SettingsAdministrationIT` altı kural: aralık dışı komisyon ve iptal cezası oranı 400 alır, aralık içi 200, ayarı değiştiren admin `updated_by_id`'ye yazılır, `updated_at` dolar ve `GET /admin/settings` bunu döndürür. `SettingsRulesTest` aralık kurallarını birim düzeyinde de sabitliyor; sınırların kendisi (0 ve 1) kabul edilir, saat anahtarları kuraldan etkilenmez.

Birim testlerde `CommissionCalculator` artık `FallbackSettings.commissionCalculator(...)` ile kuruluyor — hesap `SettingsService`'e bağlı olduğu için her çağrı yerinde bir sahte kurmak yerine tek yerden veriliyor.

**Faz 16/17 testleri.** `StorageKeysTest` anahtar üretimini, uzantı beyaz listesini, Türkçe ad katlamasını, yol geçişi savunmasını ve önek→URL eşlemesini sabitliyor — özgeçmişin CDN'e düşmediğini `UserProfileContractTest` doğruluyor. `SettingsRulesTest` tanımsız anahtarın reddedildiğini ve bozuk değerin varsayılana düştüğünü sabitliyor.

**Faz 12B/13 testleri.** `BadgeRulesTest` artık kural veritabanından okunduğu için genişledi: pasifleştirilen rozet verilmiyor ama sahipleri koruyor, eşiksiz kural hiçbir şey vermiyor, eşiksiz kural kaydedilemiyor, kod çakışması reddediliyor ve **kodda karşılığı olmayan uydurma bir rozet yalnızca satır eklenerek veriliyor**. `PresenceRulesTest` çok cihazlı bağlantı sayımını sabitliyor.

**Faz 19B testleri.** `ErrorEnvelopeIT` zarfın sekiz özelliğini sabitliyor: kategori + spesifik kod birlikte, `resource` yoldan türüyor, mesaj `Accept-Language`'a uyuyor, bilinmeyen dil ham anahtar döndürmüyor, doğrulama hataları alan listesiyle geliyor. Bir davranışı da belgeliyor: **bilinmeyen uç 404 değil 401 döner** — güvenlik zinciri önce çalıştığı için hangi uçların var olduğu sızmaz.

**Faz 19 testleri.** `ErrorCodeTest` (yedi tipli yüzey, mesaj anahtarından türeyen kod), `LocaleSafetyTest` (locale'siz büyük/küçük harf yok), `RequestValidationTest` (çapraz alan kuralları DTO'da), `StompSubscriptionSecurityTest` (abonelik yetkisi).

**Faz 18 testleri.** `AggregateBoundaryTest` (manager→DAO sınırı, gerekçeli muafiyetler), `EagerFetchCoverageIT` (liste sorgularında lazy eşlikçi zorunluluğu). `LogPrivacyTest` genişletildi: artık yalnızca getter'ları değil, **değişken/parametre üzerinden** geçen kişisel veriyi de yakalıyor — yazıldığı anda iki gerçek sızıntı buldu (`JavaMailSenderManager` ham e-posta, `PhoneVerificationManager` ham telefon).

**Denetim kaydı testleri (§2av).** `AuditScopeTest` on dört kural sabitliyor: her değişiklik yazılır, reddedilen istek yazılır, filtresiz liste yüklemesi yazılmaz, filtreli arama yazılır, tek kayıt okuması yazılır, hassas okuma filtresiz de yazılır, sayfalama parametresi filtre değildir, boş parametre filtre değildir, GET mutating izin altında bile değiştirmez, `sensitivity` izinlerin en yükseğidir, kaynak türü `/admin` altındaki koleksiyondur, kayıt id'si yolun ilk değişkenidir, hedefsiz istek null yerine boş string verir. `AdminAuditRetentionSchedulerTest` dört kural: her koşuda iki pencere de uygulanır, kapalıyken hiçbir şey silinmez, patlayan koşu zamanlayıcıdan kaçmaz, varsayılan süreler 24/120/6 aydır.

`AdminAuditTrailIT` on dokuz kural — gerçek HTTP üzerinden: izin verilen değişiklik aktör/izin/eylem/kayıt ile yazılır, detay gövde-IP-istemciyi taşır, `cancellation.late-fee-rate` artık iz bırakmadan değişmez, reddedilen istek `allowed = false` yazar, filtreli kullanıcı araması yazılır, filtresiz yükleme yazılmaz, sayfa çevirmek arama değildir, tek kayıt okuması yazılır, finansal satır 10 yıl normal satır 2 yıl saklanır, anonim istek tabloyu doldurmaz, köprü admini de denetlenir, denetim kaydını okumak yazılır ama kendi satırını döndürmez, ikinci okuma tek satır ekler, uç `audit.read` istiyor ve reddi de yazılıyor, çekirdek alanların hepsi dönüyor, detay silinince çekirdek dönmeye devam ediyor, beş süzgecin hepsi sunucuda daraltıyor, aktör süzgeci bir kişinin yaptıklarını buluyor, tanınmayan sıralama alanı 400 dönüyor.

`AdminAuditStorageIT` bölünmeyi gerçek Postgres'te doğruluyor (çekirdek detay silinince kalır, cascade ters yönde çalışır, iki yarı kendi tarihini taşır); `AdminAuditRetentionIT` saklama ve KVKK silmesini (10 yıl / 24 ay / 6 ay damgaları, süresi dolan detay çekirdeği bırakır, süresi dolan çekirdek detayı götürür, penceredeki satıra dokunulmaz, silme talebi yalnız o kişinin detayını siler, ikinci silme zararsız). KVKK tetiği `LegalConsentTest` içinde: kabul edilen talep silme çağırır, engellenen talep hiçbir şey silmez.

**`ApiConventionTest` yüzeyi yapısal olarak koruyor.** Kaynak kodu tarayıp §0b'deki altı kuralı doğruluyor: admin kuralı `/admin` dışında bir yol gösteremez, aynı fiil+yol iki handler'da olamaz, yol sondaki eğik çizgi taşıyamaz, DTO adı HTTP fiili içeremez, 32 karakteri aşamaz, `request`/`response` dışında duramaz. Yeni bir uç eklerken bedava koruma.

**Faz 11A/11B testleri.** `AutoCompletionRulesTest` (vadesi geleni tamamlar ve emaneti çıkarır, açık uyuşmazlıkta durur, emanette para yoksa dokunmaz, ikinci koşu zararsız, bir atamadaki hata partiyi düşürmez) ve `SettlementRetryTest` (kuyruğa alma idempotan, geri çekilme üstel ve tavanlı, deneme limitinde durur, başarıda `Payout` `PROCESSING`'e geçer).

**Faz 12 testleri.** `BadgeRulesTest` rozet monotonluğunu (kazanılan geri alınmaz), idempotansı, eşik konfigüre değilken hiçbir şey verilmediğini ve seed satırı eksikken çökmediğini sabitliyor. İş tamamlama yetkisi `AssignmentOperationsAuthorizationTest` içinde.

**Faz 8B/8D testleri.** `MyWorkRulesTest` (oturum sahipliği ve kendi oturumunu koruma), `CompanyAdministrationRulesTest` (şirket yetkisi, son admin, onay), `ProfileEndpointRulesTest` (eğitim sahipliği, kaydedilen ilan), `UserPatchRulesTest` (telefon doğrulama akışı), `UserProfileContractTest` (`/users/me` alanları), `JobRequestPrimitiveContractTest` (eksik primitive alanlar). `JobCategorySeedIT` kaldırıldı: seed gittikten sonra beş kategoriyi sabitlemek yanlış gerçeği koruyordu; yerine `LiquibaseMigrationIT.theJobCategorySeedIsGone` boşluğu, `JobCategoryAdministrationRulesTest` (on bir kural) ve `JobCategoryAdministrationIT` (üç kural) yönetim yüzeyini sabitliyor.

**`JobRequestPrimitiveContractTest` yazıldığında derlenmiyordu** — `getExperienceRequired()` yoktu çünkü alan primitive `boolean`'dı. Bu, `isEmployer` tuzağının aynısıydı: `@NotNull` primitive üzerinde hiçbir şey yapmaz, eksik alan sessizce `false` olur. `experienceRequired`, `insuranceRequired`, `minAge`, `maxAge` sarmalayıcı tipe çevrildi ve `salary` `@NotNull` kazandı; `maxAge < minAge` da artık 400.

**`MapperCharacterizationTest` eşleme davranışını sabitliyor.** Spring context'siz, üretilen `…MapperImpl` sınıflarını doğrudan kurar. MapStruct geçişinden önce yazıldı ve iki testi kırmızıydı; geçiş ikisini de yeşile çevirdi.

**`ErrorContractIT` HTTP sözleşmesini koruyor.** Yetkisiz erişimin 403, eksik Authorization başlığının 401 döndüğünü doğrular. İkisi de eskiden 500 dönüyordu — biri yanlış `AccessDeniedException` import'u yüzünden, diğeri null başlıkta `substring(7)` çağrıldığı için.

**Başvuru geri çekme testleri.** `ApplicationWithdrawalTest` on üç kural sabitliyor (§2ar2): sahibi `APPLIED` başvurusunu geri çeker, başkasınınki **404**, bilinmeyen kimlik 404, `ACCEPTED` ve `REJECTED` **409**, iki kez geri çekme 409, kayıt silinmez ve `delete` hiç çağrılmaz, işverene bildirim gider, geri çekilmiş başvuru kabul de red de **edilemez**, `WITHDRAWN` yeniden başvuruyu engellemez ama `APPLIED` engeller, `hasApplied` daraltılmadığı için mesajlaşma kapısı açık kalır. `ApplicationWithdrawalIT` aynı davranışı gerçek Postgres'te dört kuralla doğruluyor: satır tabloda kalır ve `is_deleted` `false`'tur, `APPLIED` süzgecinden düşüp `WITHDRAWN` süzgecinde görünür, işverenin listesinde durur, mükerrer başvuru kapısı yalnızca canlı başvuruda kapanır.

**`EntityGraphPathsIT` neden var.** `@EntityGraph` içindeki `attributePaths` derleyicinin denetlemediği düz metin; yanlış bir yol ancak o sorgu çalıştırıldığında patlıyor. `JobAssignmentDao` içinde `job.creator.addresses` yazıyordu, oysa `User.address` tekil — `PATCH /job-assignments/{id}/status` her çağrıda çöküyordu ve kimse fark etmemişti. Test tüm DAO'ları tarayıp her yolu JPA metamodel'ine karşı doğruluyor. Yeni bir entity graph eklerken bu test bedava koruma sağlıyor.

**Bildirim tercihi testleri (§2at).** `NotificationPreferenceRulesTest` on dört kural: işlemsel kategori satır ne derse desin açık kalır, alarm kullanıcı kapatana kadar açık, ticari kullanıcı açana kadar kapalı, bir kanalı kapatmak diğerlerine dokunmaz, alıcısı bilinmeyen gönderim susturulmaz, karar yazılır ve çözümlenmiş görünüme yansır, işlemsel satırlar `editable=false` gelir, tercih taşımayan kanal görünümde hiç yok, aynı kanala ikinci karar yeni satır açmaz, ticari izin an/kanal/IP ile kaydedilir, izin çekilişi de kaydedilir, ticari olmayan kategori rıza kaydı bırakmaz, çıkış bağlantısı yalnız kendi kanalını kapatır, çıkış bağlantısı oturum istemez. `NotificationUnsubscribeRulesTest` altı kural: bağlantı token'ı taşır ve veritabanı yalnız özeti, satır hangi kanalı kapatacağını bilir, tanınmayan token reddedilir, süresi dolmuş bağlantı çalışmaz, canlı bağlantı hedefine çözümlenir, aynı bağlantıya ikinci tık zararsız. `NotificationSuppressionTest` on bir kural gönderim kapısını sabitliyor: kapatılan kanal ne kuyruğa girer ne kayıt açar, e-postayı kapatmak push'u susturmaz, ödeme bildirimi tercihe rağmen gider, hesabı olmayan adrese giden e-posta susturulmaz, ticari e-posta çıkış bağlantısı taşır, işlemsel e-posta taşımaz, yetkili daveti taşımaz, iş alarmı taşımaz, bağlantı bindiği e-postanın kategori ve kanalını adlandırır, bastırılan e-posta token bile istemez.

`WorkerToolsRulesTest` bir kural kazandı: eşleşen iş alarmı `NotificationService`'e `JOB_ALERTS` kategorisiyle push olarak veriliyor — yani üretimdeki alarm yolu da kapıdan geçiyor.

**`NotificationPreferenceIT` kabul kriterlerini gerçek Postgres üzerinde kanıtlıyor.** Sekiz kural: iş alarmı e-postasını kapatan kullanıcı gerçekten almıyor (kapatmayan alıyor), kapatma yalnız o kanalı etkiliyor, işlemsel kategoriyi kapatma denemesi 400 dönüyor ve o bildirim yine gidiyor, **e-postanın içindeki çıkış bağlantısı giriş yapmadan çalışıyor ve yalnız kendi kanalını kapatıyor**, uydurma token reddediliyor, ticari izin an/kanal/IP ile iz bırakıyor, ticari ileti kullanıcı istemeden gitmiyor, tercih yüzeyi anonime kapalı.

## 4. Bilinen tuzaklar

- **`ddl-auto=validate`.** Şema yalnızca Liquibase ile değişir. Entity'ye alan eklemek tek başına yetmez, changelog yazılmadan uygulama açılmaz. Bu bilinçli bir karar — bozmayın.
- **`Job.duties` alanı `location` kolonuna eşlenmiş.** Kod "görevler" derken veritabanı "konum" diyor. Rapor yazan ya da elle SQL çalıştıran herkes yanılır.
- **Uç yolları `/api` öneki taşımaz.** API kendi alt alanında (`api.iskoala.com`) servis ediliyor; `api.iskoala.com/api/jobs` kekeleme olur. Eski `/api` önekinden kalma tek referans `EmailConsumer`'daki takip pikseliydi, düşürüldü.
- **Uç yolları sondaki eğik çizgi taşımaz.** Spring Boot 3'ten beri `/companies/` ile `/companies` aynı şey değil. Bir mapping'e eğik çizgi eklersen istemciler 404 alır.
- **Eşleşmeyen yol 404, desteklenmeyen metot 405 döner.** İkisinin de handler'ı var; yoksa `Exception` yakalayıcısına düşüp 500 dönüyorlardı.
- **Eksik ya da tip uyumsuz query parametresi 400 döner.** `MissingServletRequestParameterException` handler'ı yoksa 500 dönüyordu.
- **Gövdesi okunamayan istek 400 döner.** `HttpMessageNotReadableException` handler'ı `GlobalExceptionHandler`'da; eskiden bozuk JSON 500 döndürüyor ve ayrıştırma hatasının metnini istemciye yansıtıyordu.
- **Kayıt akışı e-posta varlığını açığa vurmaz.** `startRegistration` kayıtlı bir adres için de aynı 200 yanıtını döner, sessizce çıkar. Buraya "bu e-posta zaten kayıtlı" hatası geri eklenirse enumeration açığı geri gelir. Frontend'in mesajı bu yüzden genel olmalı: "Adres kayıtlı değilse doğrulama e-postası gönderildi."
- **Aynı adrese ikinci kayıt denemesi yeniden gönderim sayılır.** Bekleyen kayıt siliniyor, yenisi oluşturuluyor. Eskiden hata dönüyordu ve ilk e-postayı alamayan kullanıcı sıkışıyordu.
- **İstek DTO'larında primitive sayısal/mantıksal alan bırakma.** `@NotNull` primitive'de etkisizdir; eksik alan sessizce `false`/`0` olur. Sarmalayıcı tip + `@NotNull` kullan.
- **Yanıt gövdesindeki `accepted` alanı `Boolean`, `boolean` değil.** Primitive olduğu zaman istemci alanı unutursa sessizce `false` oluyordu; yani bir iş ataması kazara reddediliyordu. Artık eksikse 400 dönüyor.

## 5. Sırlar ve konfigürasyon

Tek konfigürasyon dosyası `src/main/resources/application.yaml` ve **git'te duruyor** — içinde sır yok, her değer `${ENV_ADI:varsayılan}` biçiminde.

Sırlar `.env` dosyasında (gitignore'da). Şablonu `.env.example` git'te duruyor, yeni bir makinede kurulum:

```bash
cp .env.example .env
```

`application.yaml` içindeki `spring.config.import: optional:file:./.env[.properties]` satırı sayesinde `.env` yerel çalıştırmada otomatik okunuyor — IDE'de ayrıca env değişkeni tanımlamaya gerek yok. Docker'da aynı dosya compose'un `env_file` girdisiyle veriliyor, CI'da GitHub Secrets, prod'da Dokploy environment ekranı kullanılıyor.

**Testler `.env`'den yalıtılmış.** `src/test/resources/application.properties` içinde `spring.config.import=` satırı bu bağlantıyı keser; geliştirici makinesindeki gerçek anahtarlar testlere sızmaz.

**Bekleyen iş:** eski `application.properties` içindeki anahtarlar (Gmail parolası, JWT secret, R2 ve Iyzico anahtarları, SMS anahtarları) bir süre imajda taşındı. Hepsi rotate edilmeli.

## 6. Deployment ve sürümleme

`.github/workflows/ci.yml` üç aşamalı:

1. **test** — her push ve PR'da `./mvnw verify`. Testcontainers GitHub runner'ında çalışır.
2. **build** — yalnızca test yeşilse GHCR'ye imaj basar. matmuh'un aksine testler atlanmıyor; kırmızı testte imaj üretilmez.
3. **deploy** — yalnızca `v*` tag'inde Dokploy webhook'unu tetikler.

Sürümleme: `v1.2.3` tag'i → `ghcr.io/<owner>/koala:1.2.3` ve `:latest`. main'e push → `ghcr.io/<owner>/koala:main-<sha7>`, `latest` etiketi güncellenmez. Maven sürümü `0.0.1-SNAPSHOT` sabit; imaj etiketi git tag'inden türetiliyor.

Uygulama root olmayan `koala` kullanıcısıyla, JRE tabanlı imajda çalışıyor. Sağlık kontrolü `/actuator/health/readiness` üzerinden; `/actuator/health/**` herkese açık, kalan actuator uçları ADMIN rolü istiyor.

## 7. Yol haritası

Denetim bulguları `K-01`…`K-42` kodlarıyla numaralandırılmış; commit mesajlarında ve bu dosyada o kodlarla anılıyorlar. Ayrıntılı plan ve bulgu listesi iki artefakt belgesinde tutuluyor.

**Tamamlananlar:** Faz 0 (test zemini), 1 (environment + CI/CD), 2 (güvenlik blokerleri), 3 (Spring Boot 4), 3B (MapStruct), 4 (mesaj katmanı), 5 (kod kalitesi), 5B (UserPrincipal), 6 (loglama + zaman + hız sınırı), 7 (ödeme), 7B (payout/refund/bank account), 8 (API yüzeyi — kısmi), 8C (istemci düzeltmeleri), 9 (yasal — kısmi), 9C (soft delete), 10 (mesajlaşma + değerlendirme + engelleme), 10B (iptal + uyuşmazlık + şikayet + timesheet), 8D (istemci profil yüzeyi + kategori seed), 8B (kalan API yüzeyi), 12 (rozetler + iş tamamlama), 11A (otomatik tamamlama), 11B (ödeme dayanıklılığı), 15 (API yüzeyi tutarlılığı), 12B (rozet yönetimi), 13 (WebSocket), 16 (çalışma zamanı ayarları), 17 (depolama → S3), 11C (olgunlaşma), 18 (mimari denetim), 19 (hata sözleşmesi + doğrulama + güvenlik), 19B (hata zarfı zenginleştirme), 21 (medya galerisi), 22 (birleşik işlerim ucu), 23 (şirket takibi + işveren istatistikleri), 24 (API dokümantasyonu + e-posta şablonları), 25 (prod kurulumu).

**Sıradakiler:**

| Faz | Kapsam | Neye bağlı |
|---|---|---|
| **20** | **Referans veri olgunlaştırma.** Ayrıntı aşağıda | ürün + veri |
| **7C** | **Iyzico kalanları.** Dolandırıcılık incelemesi (`fraudStatus=0`) webhook tüketicisi — `IN_REVIEW`'daki ödeme bugün kendiliğinden çözülmüyor (§2x). Ayrıca kısmi iade + disapprove sıralamasının sandbox'ta teyidi | sandbox erişimi |
| **9B** | Yasal kalanlar: anonimleştirme adımı, `Invoice`, `AuditLog`, İYS entegrasyonu, ETBİS, depolama saklama politikası (§8) | **hukuk** |
| **11D** | **Çok örneklilik.** Beş yerde tek örnek varsayımı: hız sınırı sayacı (§2j), otomatik tamamlama zamanlayıcısı (§2u), mutabakat zamanlayıcısı (§2y), presence haritası ve STOMP basit broker'ı (§2z). **Şu an tek örnek çalışacak, ertelendi** — Redis + `ShedLock` + RabbitMQ STOMP relay | ertelendi |

### İstemcide değişmesi gerekenler (Faz 15'ten sonra biriken)

Bunlar backend işi değil, Yakup'a devredilecek liste:

| Ne | Neden |
|---|---|
| `Job` modeli: `price→salary`, `company→companyName`, `address→cityName+districtName`, `subtitle` kalkar | `JobSummaryDto` ile hizalama |
| `MyJob` ikiye ayrılır | başvuru ve atama farklı durum makineleri; birleşik okuma Faz 22'den gelir |
| `CurrentUser`'a altı alan: `jobTitle`, `experiences` (artık liste), `maskedIban`, `cvUrl`, `educations`, `badges` | `/users/me` genişledi |
| `conversation.dart`'taki üç `⚠️` yorumu | `lastMessageBody`, `unreadCount`, presence **artık var** |
| Bildirim akışı artık `IN_APP` döndürüyor: `NotificationDto` içinde `body`, `targetType`, `targetId` var | §2s |
| `POST /users/me/notifications/{id}/read` tek bildirimi işaretler | §2s |
| `ApiErrorType` HTTP kodundan değil `errorCategory` alanından okunur | §2d |
| Rol yönetimi: `GET/POST /admin/users/{userId}/roles`, `DELETE /admin/users/{userId}/roles/{role}` | §2f3 |
| Faz 15'in yol değişiklikleri | §0b tablosu |
| `JobCategory.code` yorumu | kodlar artık addan türemiyor (§2m) |
| İşlerim ekranı tek uçtan: `GET /users/me/work` | Faz 22 (§2al) |
| İlan kartına `coverImageUrl`, detaya `images` galerisi | Faz 21 (§2ak) |
| Şirket takibi `POST/DELETE /companies/{id}/follow`, `GET /users/me/followed-companies` | Faz 23 (§2am) |
| İşveren istatistikleri `GET /users/{id}/employer-stats` | Faz 23 (§2am) |
| `Review.anonymous` artık var: `POST /job-assignments/{id}/reviews` gövdesinde opsiyonel, `ReviewDto.author` anonimde `null` | §2am |
| İptal kutusunda `GET /job-assignments/{id}/cancellation-preview` ile kesinti gösterilmeli | §2p |
| Başvuru ekranı: kimlik/adres/IBAN eksikse "Başvur" kapalı olmalı, uç 400 döndürüyor | §2ar |
| Bireysel ilan açma ekranı: kimlik doğrulanmamışsa kapalı olmalı (`JOB_INDIVIDUAL_EMPLOYER_IDENTITY_NOT_VERIFIED`) | §2aq |
| Kimlik doğrulamada `sod` + `dg1` (base64) gönderilmeli — bayrak açıldığında zorunlu olacak | §2aq2 |
| `dg11` gönderilirse ad ulusal karakterlerle çipten alınır, gönderilmezse istekteki ad korunur | §2aq2 |
| Çip doğrulaması: `POST /users/me/identity-verification/challenge` → çipe imzalat → `dg15` + `challenge` + `activeAuthenticationSignature` | §2aq2 |
| `UserMeResponseDto.tcIdentityNumber` → `maskedTcIdentityNumber`, değer artık `***********` | §2aq |
| İlan listesinde `applicationCount` var, ilan başına istek atma | §2ap |
| İlan silme değil `POST /jobs/{id}/cancel` | kayıt korunur, başvuranlara bildirim gider (§2ap) |
| Aday kartında `JobApplicantDto.jobTitle` ve `cvUrl` | §2ap |
| `CompanyDto.approved` (`isApproved` değil) | `CompanyMembershipDto` ile aynı ad |
| Şirket logosu `PUT/DELETE /companies/{id}/logo` | §2ap |
| Aday profili `GET /users/{userId}` | giriş ister, tam adres dönmez, `cvUrl` ilişkiye bağlı (§2ap) |
| Bireysel ilanda sigorta alanı **olmayacak** | ürün kuralı, §2ap |
| İlan formuna isteğe bağlı `headcount`, kartta `hiredCount`/`headcount` göstergesi | §2ao2 — sunucu kotayı zorlamaz, gösterge istemcide |
| Atama `CANCELLED` + iptal sebebi `{cancellation.payment.not.collected}` ise "ödeme yapılmadı" olarak göster | §2u2 |
| Bildirim tercihleri ekranı: `GET/PUT /users/me/notification-preferences`; `editable=false` satırlar kapatılamaz | §2at |
| Web'de `/bildirim-tercihleri/cikis?token=...` sayfası açılışta `POST /notification-preferences/unsubscribe` çağırmalı — giriş istemez | §2at |
| Sohbet listesi `/user/queue/messages`'a abone olur | tek akış, 20 topic yerine (§2z) |
| Presence aboneliği `/topic/presence/{userId}` | global `/topic/presence` **kaldırıldı** (§2z) |
| Aynı mesaj iki kanaldan gelebilir, `message.id` ile tekilleştir | sohbet açıkken hem topic hem kişisel kuyruk (§2z) |

**Şirket detayından mesaj gönderme** istemcide duruyor ama **yapılmayacak** — konuşma ilana bağlı kalıyor, başvuru şartı korunuyor. Tasarımdan çıkarılmalı.

### Faz 20 — referans veri olgunlaştırma

**Adres tarafı.** Bugünkü ülke/il/ilçe üçlüsü tek ülkeli bir pazaryeri için doğru yapı; eksikler şunlar:

1. **Mahalle yok.** Türk adresi `il / ilçe / mahalle / cadde / no / daire` şeklinde. Bugün ilçede duruluyor, gerisi tek bir `addressLine` metninde. İşçinin mesafeye bakıp işi kabul ettiği bir üründe mahalle ayrımı gerekiyor.
2. **Arama isimle yapılıyor.** `findByNameAndCityAllIgnoreCase` — "Beşiktaş" / "Besiktas" / "BEŞİKTAŞ" farkı kırılgan, üstelik Türkçe locale tuzağıyla (§2ag) birleşince daha da riskli. **İstemci id göndermeli, isim değil** — bu bir sözleşme değişikliği.
3. **`is_active` yok.** İlçeler birleşir, bölünür, adı değişir. Satırı silmek geçmiş adresleri sahipsiz bırakır; kategorilerde yapıldığı gibi (§2m) pasifleştirme gerekiyor.

**Eğitim tarafı — burada tersi sorun var, fazla modellenmiş.** 223 üniversite / 2.326 fakülte / 11.250 bölüm CSV'den yüklenmiş ve `Education` bu üçlüye zorunlu FK ile bağlı. Sonuç:
- listede olmayan bölüm girilemiyor,
- yurt dışı eğitim hiç girilemiyor,
- bölüm adı değişince kayıt kırılıyor,
- **YÖK kodu yok** — kategori kodlarında yapılan şeyin (§2m) aynısı burada da gerekiyor: isim değil sabit kod.

**Önerilen yön:** referans tabloları **otomatik tamamlama için** kalsın, ama `Education` üniversite/fakülte/bölüm adlarını **denormalize metin olarak da** tutsun ve FK'lar **isteğe bağlı** olsun. LinkedIn/Indeed deseni bu; "listede yok" hâli çalışır. Ayrıca her satıra YÖK kodu ve `is_active` eklenmeli.

**Neden bekliyor:** ikisi de hem veri işi (mahalle CSV'si, YÖK kodu eşlemesi) hem istemci sözleşmesi değişikliği (isimden id'ye geçiş). Ürün tarafında "mahalle sorulsun mu", "yurt dışı eğitim girilebilsin mi" kararları verilmeden başlanmamalı.

**Verilmiş kararlar:**

- **Şirket seviyesinde sohbet — yapılmayacak.** Konuşma ilana bağlı kalıyor, başvuru şartı korunuyor.
- **Presence ve yazıyor göstergesi — yapıldı** (Faz 13).
- **Rozet eşikleri, otomatik tamamlama süresi** — karar değil, veri; `/admin` uçlarından deploy'suz değişiyor.
- **Depolama** — AWS S3, matmuhbackend deseniyle (§2ab).
- **Örnek sayısı** — şimdilik tek; geçiş noktaları kodda işaretli.
- **Hata zarfı** — matmuhbackend deseni alındı, iki kod alanıyla genişletildi (§2d).
- **İlan ve şirket görselleri — yapıldı** (Faz 21, §2ak). İlanın galerisi varsayılan olarak şirket galerisinden seçiliyor.
- **Şirket takibi — yapıldı** (Faz 23, §2am). Takip edilen şirket ilan açınca push gidiyor, `company-follow.enabled` ile kapatılabilir.
- **Anonim değerlendirme — yapılmayacak** (§2am).

**Sandbox'ta doğrulanması gereken:** Iyzico akışı uçtan uca test edilmedi. Protokol dokümana karşı denetlendi ve dört blocker düzeltildi (§2x), ama gerçek bir sandbox işlemiyle görülmesi gerekenler: ödeme → emanet → işçiye çıkış zinciri, ve **kısmi iade + disapprove ikilisinin sıralaması** — doküman marketplace'te bu ikisinin birlikte kullanımına dair sıra vermiyor. Faz 7C.


## 8. Soft delete

**Hiçbir denetimli kayıt fiziksel olarak silinmez.** `BaseEntity` `isDeleted` / `deletedAt` / `deletedBy` taşır; her entity `@SQLDelete` ile `DELETE`'i `UPDATE ... SET is_deleted = true`'a çevirir ve `@SQLRestriction("is_deleted = false")` ile silinmişler varsayılan sorgulardan düşer.

**Kolonlar migration'da tablo adı sabitlenmeden eklendi.** `created_at` taşıyan her tablo taranıyor — `BaseEntity` alt sınıflarının tamamı bu şekilde yakalanıyor, yeni tablo eklendiğinde changelog'a elle isim yazmaya gerek yok. **Ama yalnızca o migration'dan önce var olan tablolar.** `app_settings` sonradan (28-03) ve `created_at`'siz doğduğu için taramaya yakalanmamıştı; `BaseEntity`'ye taşınırken (§2ac) denetim ve soft delete sütunlarını kendi changelog'uyla (`2026/09/06-03`) aldı.

**Kapsam dışı bırakılanlar ve gerekçeleri:**
- `PendingRegistration` — **bilerek hard delete.** Kaydını hiç tamamlamamış birinin e-postasını süresiz saklamak koruma değil, gereksiz veri tutma. Kayıt akışı zaten sil-ve-yeniden-oluştur mantığıyla çalışıyor.
- `CompanyContact` ve `JobCategory` — `BaseEntity` alt sınıfı değiller, denetim kolonları yok. `CompanyContact` bir üyelik kaydı ve silinmesi geçmiş kaybı demek; ileride `BaseEntity`'ye taşınmalı.
- `AdminAuditEntry` ve `AdminAuditEntryDetail` — **bilerek hard delete.** Saklama işinin işi zaten fiziksel silmek; soft delete edilen bir denetim satırı süresini hiç doldurmazdı (§2av).
- `EmailVerification`, `PhoneVerification`, `IdentityVerification` tek tabloyu (`user_verifications`) paylaşıyor; `@SQLDelete` kök sınıf `UserVerification` üzerinde, alt sınıflarda ayrıca yok. Aynı durum `Media` hiyerarşisi için de geçerli.

**Benzersizlik kısmi indekslerle çözüldü.** `uk_users_email_active` ve `uk_bank_accounts_iban_active` yalnızca `is_deleted = false` satırları kapsıyor — hesabını silen kullanıcı aynı e-postayla yeniden kaydolabiliyor.

**Mutabakat silinmiş kayda ulaşabilir.** `@SQLRestriction` yalnızca JPA sorgularını etkiliyor; native SQL tüm satırları görüyor. Admin ve mutabakat sorguları bu yolu kullanır.

**Depodaki nesneler artık silinmiyor.** `ImageManager.deleteImage` eskiden satırı ve R2 nesnesini birlikte siliyordu — geri dönüşsüz. Şimdi yalnızca satır soft delete ediliyor, nesne duruyor. **Kalıcı politika Faz 9B'de:** kimlik belgesi fotoğrafları özel nitelikli kişisel veri, profil fotoğrafı ve logo saf kişisel veri, işe/ödemeye bağlı belgeler saklama yükümlülüğü altında — kategori bazlı saklama süresi ve temizlik işi orada tanımlanacak.

**`SoftDeleteCoverageTest` bunu yapısal olarak koruyor.** Yeni bir entity eklenip `@SQLDelete` unutulursa test kırmızıya döner; bilinçli bir istisnaysa testteki listeye yazılır. `SoftDeleteBehaviourIT` ise davranışı gerçek Postgres'te doğrular: satır tabloda kalıyor, JPA sorgusundan düşüyor, native SQL hâlâ görüyor.

## 9. Prod kurulumu (Dokploy)

**Üç ayrı Dokploy servisi**, repodaki `docker-compose.yml` değil. Compose'daki `nginx-network` Dokploy'un ağı değil ve `koala-app` servisi imajı VPS'te build ediyor; CI zaten GHCR'ye basıyor. Compose yerel geliştirme için duruyor.

| Servis | Tip | Kaynak |
|---|---|---|
| `koala-db` | Database → PostgreSQL | `postgres:17` |
| `koala-rabbitmq` | Application → Docker | `rabbitmq:4.1.3-management` |
| `koala-app` | Application → Docker | `ghcr.io/yustyy/koala:latest` |

**Postgres tuzağı — yaşandı.** Resmi imaj `POSTGRES_DB`'yi yalnızca veri dizini boşken oluşturur. Servis bir kez farklı bir adla ayağa kalktıysa sonradan adı değiştirmek env değişkenini değiştirir ama **volume'un içinde o veritabanını yaratmaz**; Dokploy'un bağlantı kutusu doğru adı gösterir, uygulama `FATAL: database ... does not exist` alır. Çözüm ya eksik veritabanını elle yaratmak ya volume'u silip yeniden ilkletmek.

**RabbitMQ node adı sabitlenmeli.** Dokploy uygulamaları Swarm üzerinde çalışıyor ve konteyner hostname'i her deploy'da değişiyor; RabbitMQ verisini `/var/lib/rabbitmq/mnesia/rabbit@<hostname>` altında tuttuğu için boş node olarak açılır. `RABBITMQ_NODENAME=rabbit@localhost` bunu kapatır. Topoloji zaten `RabbitMQConfig` bean'lerinden yeniden kurulur; kaybolabilecek tek şey kuyrukta bekleyen teslim edilmemiş mesajlardır.

**Delayed-message eklentisi hiç gerekmiyordu.** `rabbitmq.Dockerfile` `rabbitmq_delayed_message_exchange` kuruyordu ama `RabbitMQConfig` yalnızca `TopicExchange` ve durable kuyruk tanımlıyor; kaynak kodun hiçbir yerinde `x-delayed-message` yok. Dosya silindi, compose resmi imaja bağlandı.

**Açılış 84 saniye sürüyor** — Yauaa user-agent kuralları tek başına ~22 saniye. `Dockerfile` healthcheck `start-period` bu yüzden 150 saniye; 60 saniyelik pencerede konteyner hazır olmadan `unhealthy` işaretlenip döngüye girebiliyordu.

**Portlar dışarı açılmaz.** Ne 5432 ne 5672 ne 15672. Yönetim arayüzüne SSH tüneliyle bakılır.

**Sürümleme etiketle.** `v1.2.3` → `ghcr.io/<owner>/koala:1.2.3` ve `:latest`; Dokploy `:latest` çeker, CI `deploy` job'ı webhook'u tetikler. main'e push yalnızca `main-<sha7>` üretir, `latest`'i güncellemez.

**`/api` öneki yok ve olmayacak.** API kendi alt alanında servis ediliyor. **URL sürümlemesi (`/v1`) de bilinçli olarak eklenmedi:** tek first-party istemci için iki API sürümünü paralel bakımda tutmak gerçekçi değil. Bunun yerine **asgari istemci sürümü kapısı** var (Faz 28, §2as). API dışarı açılırsa (B2B entegrasyon) karar değişir ve `/v1` zorunlu hâle gelir.

## Agent skills

### Issue tracker

İşler ve spesifikasyonlar `is-koala/koala-backend` GitHub Issues üzerinde takip edilir. Ayrıntılar için `docs/agents/issue-tracker.md`.

### Triage labels

Beş varsayılan triage etiketi kullanılır. Ayrıntılar için `docs/agents/triage-labels.md`.

### Domain docs

Tek-bağlam düzeni kullanılır: kökte `CONTEXT.md`, kararlar `docs/adr/` altında tutulur. Ayrıntılar için `docs/agents/domain.md`.
