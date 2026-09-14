# koala

İşgücü pazaryeri backend'i. Spring Boot 4.1 / Java 25 / PostgreSQL / Liquibase / RabbitMQ / Maven wrapper.

Bu dosya projeyi konu konu anlatır: mimari, alınmış kararlar ve gerekçeleri, tuzaklar. Bir konuya dokunmadan önce ilgili başlığı oku.

## 0. Nerede kaldık — oturum devri

**Son durum (09.09.2026):** 214 endpoint. Tam doğrulama komutu `./mvnw verify`; entegrasyon testleri Docker gerektirir. Entity, test, Liquibase ve mesaj envanterleri büyüdükçe kaynak ağacından ölçülür; burada kopya sayaç tutulmaz.

**Tamamlanan fazlar:** 0 (test zemini), 1 (environment + CI/CD), 2 (güvenlik blokerleri), 3 (Spring Boot 4), 3B (MapStruct), 4 (mesaj katmanı), 5 (kod kalitesi — K-21/K-23 hariç), 5B (UserPrincipal), 6 (loglama + zaman + hız sınırı), 7 (ödeme), 7B (payout/refund/bank account), 8 (API yüzeyi — kısmi), 8C (istemci düzeltmeleri), 9 (yasal — kısmi), 9C (soft delete), 10 (mesajlaşma + değerlendirme + engelleme), 10B (iptal + uyuşmazlık + şikayet + timesheet), 8D (istemci profil yüzeyi + kategori seed), 8B (kalan API yüzeyi), 12 (rozetler + iş tamamlama), 11A (otomatik tamamlama), 11B (ödeme dayanıklılığı), 15 (API yüzeyi tutarlılığı), 12B (rozet yönetimi), 13 (WebSocket), 16 (çalışma zamanı ayarları), 17 (depolama → S3), 11C (olgunlaşma), 18 (mimari denetim), 19 (hata sözleşmesi + doğrulama + güvenlik), 19B (hata zarfı zenginleştirme), 21 (medya galerisi), 22 (birleşik işlerim ucu), 23 (şirket takibi + işveren istatistikleri), 24 (API dokümantasyonu + e-posta şablonları), 25 (prod kurulumu).

**Sıradaki fazlar:** 7C (Iyzico kalanları), 20 (referans veri), 9B (yasal kalanlar), 11D (çok örneklilik). Ayrıntı §7'de.

**Çalışma kuralları:**
- Değişiklikler feature branch'te küçük Conventional Commit'lerle gönderilir ve PR açılır;
  doğrudan `main` dalına push edilmez ve alakasız değişiklikler commit'e alınmaz.
- Koda **yorum satırı yazma** (bkz. §1). Gerekçe buraya yazılır.
- Her değişiklikten sonra test yazılır; düzeltmeden **önce** kırmızıya döndüğü görülür.
- Ana kodda imza değiştirdiysen `rm -rf target/test-classes` — Maven bayat test sınıfını yeniden derlemez, `NoSuchMethodError` alırsın.
- Ajanın kapısı bu Mac: Docker, Temurin 25, `./mvnw verify -Dkoala.test.latch-timeout=30`. GitHub kuyruğunu bekleme. Mac org runner değil. Üretim imajı `scripts/publish-ghcr-image.sh --latest` → `ghcr.io/is-koala/koala`. GitHub yalnız `v*` etikette (veya workflow_dispatch) org self-hosted runner'da Dokploy webhook atar.

**Prod durumu (30.08.2026):** Dokploy üzerinde Postgres 17, RabbitMQ ve uygulama ayakta. `api.iskoala.com`. Son etiket `v0.4.9` — kimlik doğrulama, SMS/push tüketicileri, sürüm kapısı ve çip doğrulaması dahil. Ayrıntı §9'da.

**Kullanıcı tarafında bekleyen işler:**
1. **Springdoc varsayılanı kapalıdır.** `.env.example` yerel geliştirme için bilinçli olarak `SPRINGDOC_ENABLED=true` verir; production'da dokümantasyon gerekiyorsa aynı değer açıkça eklenir, verilmezse Swagger UI ve API docs açılmaz.
2. **Gerçek kartla çip dökümü** — kimlik kartının hangi CSCA'ya zincirlendiği, DG11 ve DG15'in varlığı yalnızca böyle öğrenilir (§2aq2).
3. **Iyzico sandbox anahtarları** — ödeme akışı uçtan uca hiç denenmedi (§2x, Faz 7C).
4. **Firebase servis hesabı** — `PushService`'in FCM uygulaması için (§2s).
5. **SMS sağlayıcı anahtarı** — `SMS_API_KEY` boşken telefon doğrulama kodu üretilir ama teslim edilmez.
6. **Dokploy'dan `QA_MARKETPLACE_SEED_VERSION`, `QA_WORKER_EMAIL`, `QA_WORKER_PASSWORD`, `QA_EMPLOYER_EMAIL`, `QA_EMPLOYER_PASSWORD` kaldırılmalı** — seed kaldırıldı (ADR-0008), bu değişkenler işlevsiz ve parola taşıyorlar.
7. **İlk personel hesabı** — taze bir veritabanında ilk `OWNER` personeli `BOOTSTRAP_OWNER_EMAIL` + `BOOTSTRAP_OWNER_PASSWORD` verilip uygulama yeniden başlatılarak doğar (§2au §Personel girişi). Hesap "ilk girişte parola değiştir" bayrağıyla gelir; **parola değiştirildikten sonra iki değişken de Dokploy'dan silinmelidir** — `BOOTSTRAP_OWNER_PASSWORD` bir sırdır. Tablo boş değilken bootstrap hiçbir şey yazmaz. **`/admin/**` artık yalnız personel token'ı kabul ediyor:** marketplace `ROLE_ADMIN` köprüsü kapandı, yani bu hesap açılmadan panele hiç girilemez.
8. **İlk admin ve iş kategorisi kataloğu** — taze bir veritabanında ilk yönetici `ADMIN_BOOTSTRAP_EMAIL` verilip uygulama yeniden başlatılarak doğar (§2f3); değişken ilk admin oluştuktan sonra kaldırılmalı. Kategori seed'i kaldırıldı (§2m). Prod ve taze veritabanları kategorisiz; kategori ilanın zorunlu alanı olduğu için katalog `POST /admin/job-categories` ile kurulmadan **hiç ilan açılamaz**. Mobil istemcinin ikon eşlemesi `code` üzerinden gittiği için eski kodların (`PHOTOGRAPHY`, `WAITER`, `PET_CARE`, `DESIGN`, `BARISTA`) yeniden verilmesi gerekebilir.
9. **1,01 TL IBAN sahipliği doğrulaması şimdilik kapı değil (#127).** Akış kodda durur (`BANK_VERIFICATION_COLLECTION_IBAN` boşken başlatılamaz) ama alt üye işyeri ve emanet çıkışı birincil IBAN'ın **varlığına** bakar, doğrulanmış olmasına değil. Kimlik: TCKK çipi veya belge fotoğrafı. Havale doğrulaması çok sonraya.
10. **R2'deki mevcut nesnelerin S3'e kopyalanması** (§2ab), yasal metinlerin avukattan gelmesi, ve prod'da WebSocket'in `wss://` üzerinden servis edildiğinin teyidi (§2ai).
11. **Dokploy'da dört değer düzeltilmeli (#66).** `CORS_ALLOWED_ORIGINS` boşaltılmalı (ADR-0011), `APP_TRUSTED_PROXIES` web yayına çıkmadan önce yalnız BFF/ingress IP-CIDR'ıyla tanımlanmalı, `SPRINGDOC_ENABLED=false` yapılmalı, `SERVICE_VERSION` tekilleştirilmeli. Dördü de depodan görülemiyor; kontrol indi ve sapmayı açılış logunda görünür kılıyor (§2ax) ama **düzeltmiyor**. Beklenen değerler `docs/environments.md` §Production zorunlulukları'nda.

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

- **Yönetim işlemleri `/admin/**` altında, istisnasız.** Prefix tek satırlık koruma sağlıyor, ama o satır artık `SecurityConfig`'te değil: `StaffSecurityConfig` `/admin/**`'ı `securityMatcher` ile alıyor ve kimlik doğrulama uçları dışındaki **her** isteği `staffPermissionAuthorizationManager`'a veriyor (§2au). Prefix olmasaydı her yeni admin ucunu tek tek korumayı hatırlamak gerekirdi ve unutulan uç sessizce her `USER`'a açık kalırdı; şimdi tersi geçerli — anotasyonu olmayan uç fail-closed reddediliyor. **Test, `SecurityConfig`'te ADMIN'e kısıtlayan her kuralın yolunun `/admin` ile başladığını doğrulamaya devam ediyor — `hasRole("ADMIN")` ve tek rolü ADMIN olan `hasAnyRole("ADMIN")`, ikisi de.** İki yazım anlamca aynıdır; tarama birini görüp diğerini görmezse geriye korunduğu **sanılan** bir alan kalır (#72). Çok rollü `hasAnyRole("USER", "ADMIN")` ADMIN'e kısıtlama değildir, kapsam dışıdır.
  - **Kesim yapıldı (#98).** [ADR-0010](docs/adr/0010-split-staff-identity-from-koala-authorization.md) §Geçiş'in marketplace `ROLE_ADMIN` köprüsü kapandı: `SecurityConfig`'teki `/admin/**` → `hasRole("ADMIN")` satırı **silindi**, `koala.staff.bridge.open` varsayılanı **`false`** oldu ve `StaffPayoutManager`'daki aktörsüz para dalı **kaldırıldı**. `/admin/**` bugün yalnız Koala'nın kendi personel token'ını kabul ediyor; marketplace `ROLE_ADMIN` taşıyan bir kullanıcı 403 alıyor. Anahtar duruyor ve bir aksilikte `STAFF_MARKETPLACE_BRIDGE_OPEN=true` ile **yalnız HTTP kapısı** geri açılabiliyor — para dalı geri açılamaz, çünkü silindi. Gerekçenin tek kaynağı [ADR-0014](docs/adr/0014-keep-staff-identity-in-koala.md), buraya kopyalanmıyor.
- **`/admin/**` altındaki bir metot `securityService.getAuthenticated*` çağıramaz — log satırında bile.** `StaffPrincipal` bir `UserPrincipal` değildir; `SecurityManager` personel kimliği görünce artık `IllegalStateException` atıyor (eskiden `{user.not.authenticated}` → **404**'tü ve "kaynak yok" gibi görünüyordu, #95). Aktörü `@AuthenticationPrincipal StaffPrincipal actor` ile parametre olarak al: `actor == null` köprü yolu (marketplace `ROLE_ADMIN`), doluysa personel yolu. Kullanıcı kimliğine bağlı alanlar yalnız köprü yolunda damgalanır — personelin `users` tablosunda satırı yoktur (ADR-0014). Karar aggregate'i personel aktör için ayrı bir ilişki taşıyorsa o ilişki `StaffAccountService` üzerinden damgalanır; taşımıyorsa "kim yaptı" sorusunu admin denetim kaydı (§2av) cevaplar.
  - **Kuralı `AdminStaffPrincipalGuardIT` koruyor** (#88'in dersi: yazılı kural test değildir): her `/admin/**` mapping'inin handler gövdesinden `\w+Service.\w+(` çağrıları çıkarılıyor, servis alanının gerçek bean sınıfı Spring'den çözülüyor ve o manager metodunun kaynağında tuzak aranıyor. `StaffPrincipal` parametresi alan bir metotta `actor == null` dalındaki çağrı köprü yolu sayılıp geçiliyor. **Tarama bir seviye derin — handler → manager metodu; manager başka bir manager'ı ya da kendi özel yardımcısını çağırır ve tuzak orada olursa test görmez**, o zincirler `CompanyAdministrationRulesTest` / `RoleGrantRulesTest` gibi davranış testlerine emanet. İkinci bir test taramanın en az 30 manager metoduna ulaştığını ve #95'in dört metodunu gördüğünü doğruluyor, yani çözümleme bozulursa değişmez sessizce boşa koşmuyor.
  - **Kullanıcı ve personel aktör alanları birbirinin alternatifi.** `authorities.granted_by_id`, `disputes.resolved_by_id`, `reports.reviewed_by_id` `users`'a yabancı anahtar taşır; personelin orada satırı yoktur (ADR-0014). Köprü yolu User alanını, uyuşmazlık ve şikayet kararlarının personel yolu ise sırasıyla `resolved_by_staff_id` ve `reviewed_by_staff_id` alanını damgalar. Eski denetim satırındaki `allowed` yalnız yetkilendirme sonucudur, handler'ın kararı yazdığını kanıtlamaz; bu yüzden Liquibase User aktörü olmayan tarihsel kararları tahmin ederek geri doldurmaz ve yeni StaffMember alanları nullable kalır. Check constraint aynı kararda iki aktör türünün birden dolmasını engeller. `grantRole` ayrı StaffMember kolonu taşımadığı için personel yolunda `granted_by_id` boş kalır ve aktörü denetim kaydı cevaplar.
  - **Aktörü marketplace kullanıcısıyla karşılaştıran kurallar personel yolunda vakumda kalır.** `revokeRole`'ün "kendi `ROLE_ADMIN`'ini alamazsın" kuralı hedefi aktörle karşılaştırıyor; personel aktör hiçbir zaman bir `users` satırı olmadığı için hedefle çakışamaz. Kural `actor == null` dalına alındı — koşulsuz çalıştırmak `securityService`'e uzanmak, yani yukarıdaki tuzağa düşmek demekti. Son admini koruyan sayım ise koşulsuz, iki yolda da çalışıyor.
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

**Yeniden adlandırılan 16 DTO** (JSON alan adları değişmedi, yalnızca sınıf adları): `JobAssignmentsAssignmentIdPaymentSessionInitiatePostResponseDto` → `PaymentSessionDto`, `...StatusPatchResponseDto` → `JobAssignmentDto`, `...StatusPatchRequestDto` → `AnswerAssignmentDto`, `JobApplicationsApplicationIdPatchResponseDto` → `JobApplicationDetailDto`, `...PatchRequestDto` → `AnswerApplicationDto`, `JobsJobIdApplicationsPostResponseDto` → `JobApplicationDto`, `...GetResponseDto` → `JobApplicantDto`, `...PostRequestDto` → `CreateJobApplicationDto`, `CompaniesCompanyIdJobsPostRequestDto` → `CreateCompanyJobDto`, `...PostResponseDto` → `CompanyJobDto`, `UsersMeJobsPostRequestDto` → `CreateIndividualJobDto`, `...PostResponseDto` → `IndividualJobDto`, `UsersMeIdentityVerificationRequestDto` → `VerifyIdentityDto`, `UserMeChangePasswordPutRequestDto` → `ChangePasswordDto`, `UserMePatchRequestDto` → `UpdateProfileDto`, `CompanyContactWithoutCompanyDto` → `CompanyContactDto`. `CreateUserDto`/`UpdateUserDto` `user/request` altına taşındı, sonra ölü çıkıp silindi (§2ah2).

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

**Failsafe entegrasyon testleri iki yeniden kullanılan JVM'e bölünür; `IdentityMultipartBudgetIT` ayrı ve yeniden kullanılmayan tek JVM'de çalışır; gerçek broker isteyen dört IT (`EmailRetryIT`, `RabbitLogContextIT`, `NotificationOutboxRabbitIT`, `UserProfileSyncOutboxRabbitIT`) üçüncü execution'da `forkCount=1` ile koşar.** Tek uzun JVM bütün Spring context'lerini biriktirerek Raspberry Pi CI makinesinin heap'ini tüketiyordu. İki normal fork toplam bellek tüketimini 8 GiB makinenin sınırında tutarken test süresini kısaltır. Gerçek 40–43 MiB gövdeler üreten `IdentityMultipartBudgetIT` ana paketten hariç tutulur ve yalnız kendi execution'ında bir kez çalışır. Broker IT'leri iki-fork paketle örtüşürse her sınıf kendi `rabbitmq:*-management` kabını JVM ömrü boyunca tutardı; Pi 5 dört çekirdek, dört Erlang VM + iki Postgres + iki Spring JVM aynı anda koşunca Postgres yeni bağlantıyı 70 saniyede açamaz ve Hikari `total=1, active=1` ile düşer — 8 GiB yetmez diye değil, CPU/IO kuyruğu diye. Dördü `TestRabbit` üzerinden tek `rabbitmq:4.1.3` (management yok) paylaşır ve AMQP `newConnection` kabul edene kadar bekler; port açılınca kuyruk ilan etmek `Connection reset` üretir. Include/exclude ayrımı kaldırılırsa test ya iki kez koşar ya da CI yeniden bellek taşmasına / broker çakışmasına döner. `TestRabbitTest` listeyi pom.xml'e bağlar.

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

**Jackson 3'ün bir varsayılanı değişti.** `FAIL_ON_NULL_FOR_PRIMITIVES` artık açık geliyor; primitive bir alan JSON'da yoksa istek reddediliyordu. `application.yaml` içinde kapatıldı ki yükseltme mevcut istemci sözleşmesini bozmasın. Doğrusu bu alanları `Boolean`/`Integer`/`Double` + `@NotNull` yapmak. İş ilanı bayrakları, `isEmployer`, `gpa`, galeri `cover`/`sortOrder` ve rozet `sortOrder` sarıldı. Tek bilinçli primitive kalan `CreateCompanyJobDto.addressPublic`: eski istemciler alanı göndermez, varsayılan `false`'tur ve `JobRequestPrimitiveContractTest` bunu kilitler. Envanter `RequestDtoPrimitiveContractTest` — yeni primitive yazma alanı allowlist'siz eklenemez (#179). Yanıt tarafında aynı bayraklar (`JobDetailDto`/`JobSummaryDto`/`CompanyJobDto`/`IndividualJobDto` üzerindeki `insuranceRequired`/`experienceRequired`/`minAge`/`maxAge`) sarıldı: eşlenmezse JSON artık `false`/`0` değil `null` olur. Kalan primitive sayaçlar ve `addressPublic` `JobResponsePrimitiveContractTest` allowlist'inde (#183).

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

**Çözümleme zarfın en üstüyle sınırlı: `MessageResolvingAdvice` iç içe DTO alanlarına dokunmuyor.** Advice
gövdeyi `Result`'a cast edip yalnız onun `message` alanını çözüyor; `data` içindeki bir DTO'nun kendi `String`
alanına yazılmış `{anahtar}` istemciye **ham** gidiyor. Bu bir kusur değil kapsam — gövdeyi refleksiyonla
gezip her `String`'i çözmek, kullanıcının yazdığı bir metin tesadüfen `{...}` biçiminde olduğunda onu da
değiştirirdi. Kusur olan şey, kapsamın hiçbir yerde yazılı olmamasıydı: `VerificationInfoDto` tam bu tuzağa
düşmüş ve istemciye `{email.verification.message}` göndermişti (is-koala/koala-backend#46).

**İki test kapsamı sabitliyor.** `ResponseMessageResolutionTest` önce davranışı pinliyor — advice zarfın
`message`'ını `Accept-Language`'a göre çözüyor, iç içe DTO alanını **olduğu gibi bırakıyor** — sonra
`core/dtos` altındaki her `response`/`common` sınıfını tarayıp hiçbirinin ne `"{anahtar}"` literali ne bir
`*Messages.SABIT` referansı taşımadığını doğruluyor. Yani bir sonraki DTO aynı tuzağa düştüğü anda derleme
değil test kırmızıya dönüyor. **Taramanın kör noktası yazılı olsun (§3'teki #72 dersi):** yalnız `core/dtos`
altındaki `response`/`common` yollarına bakıyor ve yalnız iki yazımı tanıyor — anahtarı çalışma zamanında
birleştiren ya da DTO dışında bir sınıfta kuran bir kod görünmez. `request` DTO'ları bilerek dışarıda:
oradaki `message = "{...}"` Bean Validation'ın anahtarı ve `GlobalExceptionHandler` onu zaten çözüyor (§2aj).

**Taramanın yanlış pozitif yönü de yazılı olsun (is-koala/koala-backend#126).** Tarama kaynak
metnini okur, davranışı değil. Bir yanıt DTO'su sabiti kurulum anında açıkça çözse —
`messageResolver.resolve(SomeMessages.FOO)` — test yine kırmızı verir. Bu bilinçli: advice iç içe
alanı çözmediği için DTO'ya `MessageResolver` enjekte etmek zaten yanlış katman, ve regex'i
"çözülmüş" yazımı tanıyacak şekilde daraltmak çok satırlı ya da dolaylı bir çağrıyı sessizce
kaçırırdı. Gerçek bir muafiyet gerekirse dosya adı
`ResponseMessageResolutionTest` içindeki `FILES_THAT_RESOLVE_MESSAGE_CONSTANTS_AT_CONSTRUCTION`
kümesine yazılır; küme boşken tarama her `*Messages.SABIT` referansını yasaklar. Bayat muafiyet
`everyResolvedConstantExemptionStillCarriesAMessageConstant` ile kırmızıya döner.

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
  "requestId": "70fbd6d0-…",
  "timeStamp": "2026-08-28T03:10:35",
  "details": [{ "field": "email", "message": "E-posta boş olamaz." }]
}
```

**`errorCategory` kapalı küme, `errorCode` spesifik.** İstemci `errorCategory` üzerinde `switch` yazabilir (`ErrorCategory` sabitlerinin tamamı tanımlı); `errorCode` ise mesaj anahtarından türediği için hangi kaynağın bulunamadığını söyler (`PAYMENT_NOT_FOUND` vs `USER_NOT_FOUND`). İkisini birden vermek, matmuh'un sabit katalog yaklaşımıyla koala'nın türetilmiş kodunun birleşimi — kapalı küme davranış için, spesifik kod log ve hata ayıklama için. Yayımlanan kaynak OpenAPI şemasıdır: `OpenApiDocumentIT.theErrorCategorySchemaListsEveryJavaConstant` `/v3/api-docs` içindeki enum listesini Java sabitleriyle birebir tutar, böylece mobil sözleşme testi şema/Java kaynağına bağlanabilir (is-koala/koala#85).

**`resource` / `resourceId` istek yolundan türetilir.** `RequestResourceResolver` yolun son kaynak segmentini ve varsa UUID'sini çıkarır; istemci "neyin bulunamadığını" ayrıca ayrıştırmak zorunda kalmaz.

**`details` yalnızca doğrulama hatalarında.** `{field, message}` çiftleri, mesajları çözümlenmiş hâlde — istemci ham anahtar görmez.

**HTTP kodu exception'ın kendisinde.** `KoalaException` bir `HttpStatus` taşır; alt sınıflar constructor'da belirler (404, 403, 409, 401, 502). `errorCode` sınıf adından türetilir: `UserNotFoundException` → `USER_NOT_FOUND`. Eskiden hepsi ayrımsız 400 dönüyordu.

**İstemciye iç detay sızmaz.** Beklenmeyen hatalarda gövdeye sabit bir metin yazılır, exception yalnızca `requestId` ile loglanır. Zarftaki istek kimliğinin tek adı `requestId` (§2i).

**Hata gövdesi `Accept` ne olursa olsun `application/json` (is-koala/koala-backend#256).** `SecurityExceptionConfig`
kimlik ve yetki hatalarını `HandlerExceptionResolver` üzerinden `GlobalExceptionHandler`'a taşıyor; handler bir
`ResponseEntity<Result>` döndürdüğü için gövde içerik müzakeresinden geçiyordu ve JSON kabul etmeyen bir `Accept`
(yalnız `text/html` gönderen tarayıcı/bot probları) `HttpMediaTypeNotAcceptableException` üretiyordu. Sonucu iki
katlıydı: `ExceptionHandlerExceptionResolver` her istekte `Failure in @ExceptionHandler … handleAuthentication`
diye WARN + tam yığın izi basıyor, istemci ise gövdesiz ve `Content-Type`'sız bir 401 alıyordu — durum kodu
`HttpEntityMethodProcessor` gövdeyi yazmadan önce set edildiği için 401 kalıyor, ama zarf hiç yazılmıyordu.
`build` artık `ResponseEntity.contentType(APPLICATION_JSON)` ile içerik tipini sabitliyor; Spring, yanıtta önceden
set edilmiş somut bir `Content-Type` görünce müzakereyi hiç yapmıyor
(`AbstractMessageConverterMethodProcessor.writeWithMessageConverters`, `isContentTypePreset`). Yani hata zarfı bir
sözleşme: istemci ne isterse istesin JSON. `HttpMessageConverter`'a elle yazmak yerine bu yol seçildi, çünkü tek
satır ve bütün handler'ları — güvenlik giriş noktasından, `SessionAuthFilter`/`StaffAuthFilter`/`RateLimitFilter`'ın
aynı resolver'a verdiği hatalardan ve controller'lardan geleni — tek yerden kapsıyor. Eşleşmeyen yol + anonim istek
için handler ayrıca log yazmıyor: erişim logu (§2i) 4xx'i zaten WARN ile tek satır yazıyor, ikinci bir satır
gürültü olurdu. `ErrorResponseContentTypeTest` (standalone MockMvc; `text/html`, `*/*;q=0`, `image/png` ve
tarayıcı biçimli Accept; kimlik, yetki ve alan hatası) ve `ErrorEnvelopeIT` (`POST /` + `Accept: text/html` →
401 JSON zarfı, o iş parçacığında yığın izi yok ve tek WARN satırı `http.access`'ten; `GET /users/me` aynı) sabitliyor.

## 2d2. Var olma gizlemesi — eşitlenen şey yanıt, durum kodu değil

Depo beş ayrı yerde bir kaynağın **var olduğunu** sızdırmamak için 403 yerine 404 döndürmeyi seçmişti (§2o,
§2ab, §2p, §2ar2). Karar doğruydu ama **eksik uygulanmıştı**: `errorCode` mesaj anahtarından türüyor (§2d) ve
hata zarfıyla istemciye gidiyor, yani aynı 404'ü farklı anahtarla döndüren iki dal hâlâ ayırt edilebiliyordu.
UUID deneyen saldırgan "sana ait değil" anlamına gelen bir kod aldığında kaynağın **var olduğunu** öğreniyordu.

**Neden bugüne kadar görülmedi: sıralama.** Gizleme kararları önce alındı, hata zarfının `errorCode` alanı
Faz 19/19B'de eklendi. O ekleme daha önce kapatılmış her sızıntıyı sessizce yeniden açtı — doğru bir kararın
sonradan eklenen bir mekanizmayla geçersiz kılınması, ve bunu hiçbir şeyin haber vermemesi (is-koala/koala-backend#77,
#67'nin tekrar eden dersi).

**Kural: gizleme yanıt düzeyindedir, durum kodu düzeyinde değil.** Eşitlenmesi gereken şey istemcinin
görebildiği **her şey** — durum, `errorCode`, `message`, `errorCategory`, `resource`, `resourceId`.

**Biçim tek yerde: `core/helpers/Concealment`.** `Concealment.notFound(anahtar, aktör, kaynakId, gerekçe)`
gizlenen yanıtı üreten tek nokta; **kaynağın kendi "bulunamadı" anahtarını** taşıyan bir
`ResourceNotFoundException` döndürür ve gerçek gerekçeyi `log.warn` ile yazar. Anahtar başarısız kontrolün
değil eksik kaynağın adını taşır — `TIMESHEET_NOT_THE_WORKER` değil `JOB_ASSIGNMENT_NOT_FOUND`.

**Ayrım sunucu logunda korunuyor.** Yardımcının tek işlevsel katkısı bu: anahtar birleştiği için operasyonun
iki hâli ayırt edebilmesinin başka yolu kalmıyor. Değiştirilen dalların çoğunda önceden **hiç log satırı
yoktu**; şimdi hepsinde var.

| Uç | Gizlenen dal | Ortak anahtar |
|---|---|---|
| `GET /conversations/{id}`, `GET/POST /conversations/{id}/messages`, `/read` | taraf değil | `messaging.conversation.not.found` |
| `PUT/DELETE /users/me/educations/{id}` | başkasının kaydı | `education.not.found` |
| `PUT/DELETE /users/me/experiences/{id}` | başkasının kaydı | `experience.not.found` |
| `GET/DELETE /users/me/job-templates/{id}` | başkasının şablonu | `job.template.not.found` |
| `POST /job-assignments/{id}/cancel`, `GET .../cancellation[-preview]` | taraf değil | `job.assignment.not.found` |
| `POST /job-assignments/{id}/respond` | işçisi değil | `job.assignment.not.found` |
| `POST /job-assignments/{id}/check-in`, `check-out` | işçisi değil | `job.assignment.not.found` |
| `GET /job-assignments/{id}/work-sessions` | taraf değil | `job.assignment.not.found` |
| `POST /job-assignments/{id}/complete` | işvereni değil | `job.assignment.not.found` |
| `POST /job-assignments/{id}/reviews` | taraf değil | `job.assignment.not.found` |

Kullanılmayan hâle gelen dokuz anahtar üç katalogdan da düşürüldü (`education.not.owner`,
`experience.not.owner`, `job.template.not.owner`, `cancellation.not.a.participant`, `timesheet.not.the.worker`,
`timesheet.not.a.participant`, `review.not.a.participant`, `job.assignment.only.employer.can.complete`,
`job.assignment.not.worker.for.assignment`) —
duran bir anahtar sonradan yeniden çağrılabilir.

**İstemciye görünen davranış değişiklikleri.** `check-in`/`check-out` uçlarında **işveren** artık 404
alıyor: eskiden de 404 alıyordu ama `TIMESHEET_NOT_THE_WORKER` koduyla, şimdi `JOB_ASSIGNMENT_NOT_FOUND`.
Aynısı `complete` ucunda **işçi** için geçerli. Kod bazlı dallanan istemci bunu güncellemeli;
`JobAssignmentOperationsController`'daki `@ApiResponse` blokları koddaki gerçek davranışa hizalandı (iki uçtaki
403 satırı kaldırıldı, `check-out`'un "açık oturum yok" satırı 404'ten 409'a çekildi — kod baştan beri
`ConflictException` atıyordu). `respond` ucunda işçi olmayan aktörün önceki 403/409 ayrımı da
durumdan bağımsız `JOB_ASSIGNMENT_NOT_FOUND` 404'e birleştirildi.

**Sıra da yanıtın parçası: yetkilendirme durum kontrolünden önce gelir (§2ar3).** Üç yerde tersti ve durum
eşitlense bile sızıntı sürüyordu: `completeAssignment` yabancıya önce `PAYMENT_NOT_SECURED` (**409**)
döndürüyordu — hem atamanın var olduğunu hem ödeme durumunu söylüyordu; `leaveReview` de aynı şekilde
`WORK_NOT_COMPLETED` (**409**) döndürüyordu. `answerToAssignment` ise terminal kaydı yabancıya
`ALREADY_ANSWERED` (**409**), bekleyen kaydı `NOT_WORKER_FOR_ASSIGNMENT` (**403**) ile açığa çıkarıyordu.
Üçünde de yetki kontrolü öne alındı.

**`resource` / `resourceId` zaten eşitti — varsayılmadı, ölçüldü.** `RequestResourceResolver` ikisini de
istek yolundan türetiyor (§2d), yani aynı uca giden iki istek aynı `resource`'u üretiyor ve `resourceId`
istemcinin kendi gönderdiği id oluyor; bilgi taşımıyor. `ExistenceConcealmentIT` bunu her uçta doğruluyor.

**STOMP yüzeyi zaten temizdi (§2ai).** `authoriseConversation` `findById(...).map(...).orElse(false)` yazıyor,
yani var olmayan konuşma ile taraf olmadığın konuşma **aynı** `ForbiddenException(SUBSCRIPTION_NOT_ALLOWED)`
dalına düşüyor: hem 403 hem `MESSAGING_SUBSCRIPTION_NOT_ALLOWED` ortak. Taranıp doğrulandı, değiştirilmedi.

**İki yapısal koruma var ve neyi yakalayamadıkları yazılı.**

`ExistenceConcealmentIT` **yanıtı** sabitliyor: gizlenen uçlarda yabancı bir kullanıcı aynı isteği iki kez atıyor —
rastgele bir UUID'ye ve gerçekten var olan ama başkasına ait bir kaynağa. İki yanıt gövdesi, istemcinin kendi
gönderdiği id yer tutucuya çevrildikten ve istek başına zorunlu olarak değişen iki alan (`timeStamp`,
`requestId`) düşüldükten sonra **birebir aynı** olmak zorunda. Sekiz alanı birden sabitliyor, kırmızıya
dönme biçimi de öğretici. **Yakalayamadığı:** listesinde
olmayan uçlar ve sonradan eklenen uçlar.

`ExistenceConcealmentRulesTest` **kaynak kodu** tarıyor ve testin göremediği yerleri kapatıyor: `business/concretes`
altındaki hiçbir metot iki farklı 404 anahtarı döndüremez, `Concealment.notFound`'a verilen her anahtar
`NOT_FOUND` ile biter, ve gizlenen yanıt tek yerde üretilir (yardımcıdaki `log.warn` kaldırılırsa da kırmızı).
**Yakalayamadığı üç şey var, ve en önemlisi ikincisi:**

1. Farklı kaynakları adlandıran meşru çift — `PayoutManager#releaseToWorker` atama ile payout kaydını ayrı ayrı
   arıyor. Muafiyet listesinde, ve `everyExemptionStillDescribesARealMethodThatNamesTwoResources` liste
   bayatlarsa kırmızıya dönüyor (§2ae'deki desenin aynısı).
2. **Sınıflar arasına yayılan çift.** Yükleyici başka bir serviste, gizleyen dal manager'da olduğunda tarama
   ikisini aynı metotta görmüyor — atama yüzeyinin tamamı böyle (`jobAssignmentService.getDetailedById` yükler,
   manager gizler). O aile bugün yalnız `ExistenceConcealmentIT` ile korunuyor.
3. Yanıtın exception dışı kanalları: sıralama, zamanlama farkı, gövde boyutu.

**`DisputeManager` bilerek dokunulmadan bırakıldı ve sızıntı orada duruyor.** `openDispute` ile
`getForAssignment` atamayı `getDetailedById` ile yüklüyor (`job.assignment.not.found`) ama taraf olmayana
`dispute.not.a.participant` atıyor — yukarıdaki 2 numaralı boşluğun canlı örneği. Dosya eşzamanlı olarak
is-koala/koala-backend#64 kapsamında değiştiriliyordu; iki dalın aynı satırlara dokunması yerine düzeltme o
issue'ya ya da bir devama bırakıldı. Düzeltmesi tek satırlık: iki dal da `Concealment.notFound` ile
`JobAssignmentMessages.NOT_FOUND` atmalı.

**`GET /jobs/{jobId}/applications` de dışarıda bırakıldı, gerekçesi §2ar3'te yazılı.** O uç şirket ilanında
hâlâ `ValidationException` (**400**) atıyor, yani bugün hiçbir şey gizlemiyor; 400'ü 404'e çekmek `koala-web`'in
sözleşmesini kırabileceği için kendi issue'sunu bekliyor. Anahtarı eşitlemek durum kodu ayrışıkken tek başına
işe yaramazdı.

**Gizleme gerekmeyen iki yer tarandı ve bilerek olduğu gibi bırakıldı.** `MessagingManager.assertMayConverse`
ve `JobTemplateManager.assertCanCopy` yabancıya ilanın kendi anahtarından farklı bir anahtar döndürüyor; ama
ilan **herkese açık** bir kaynak (`GET /jobs/{jobId}` `permitAll`), yani orada gizlenecek bir varlık yok.
`CompanyManager.requireCompanyMember`/`requireCompanyAdmin` ise saf `exists…` sorgusu olduğu için var olmayan
şirket ile üyesi olmadığın şirketi zaten ayırt ettirmiyor (§2s2'deki liste ucuyla aynı gerekçe);
`CvManager.assertCanDownload`, `AvailabilityManager`, `JobAlertManager`, `SessionManager`, `PaymentManager`,
`GalleryManager`, `CompanyContactInvitationManager` ve `JobApplicationManager` taramada **zaten doğru** çıktı.

**Giriş ucu de aynı kusurun bir örneğiydi ve aynı kuralla kapatıldı (is-koala/koala-backend#42).**
`AuthManager.login` doğrudan `userService.getByEmail` çağırıyordu: kayıtlı olmayan adres **404
`USER_NOT_FOUND_BY_EMAIL`**, kayıtlı adres yanlış parolayla **401 `AUTH_EMAIL_OR_PASSWORD_MISMATCH`**
dönüyordu. Saldırgan hangi adreslerin kayıtlı olduğunu adres adres öğrenebiliyordu. Artık iki dal da tek
`UnauthorizedException(AuthMessages.EMAIL_OR_PASSWORD_MISMATCH)` — durum, `errorCode`, `message`,
`errorCategory` ortak. Gizleme deseni `requestPasswordReset` ile aynı: `ResourceNotFoundException`
yakalanır, ayrım `log.warn` ile sunucuda kalır.

**Süre de yanıtın parçası, bu yüzden hesap yokken de parola doğrulanıyor.** Kullanıcı bulunamadığında
doğrudan dönseydi istek ölçülebilir biçimde erken biterdi ve aynı bilgi zamanlama üzerinden sızardı —
yukarıdaki üçüncü boşluğun ("yanıtın exception dışı kanalları") canlı hâli. `passwordEncoder.matches`
hesap yokken de çağrılıyor; karşılaştırılan hash `AuthManager` kurulurken
`passwordEncoder.encode(UUID.randomUUID().toString())` ile üretiliyor.

**Sahte hash bilerek sabit bir dize değil.** Gömülü bir bcrypt sabiti, kodlayıcı yarın Argon2'ye
çevrildiğinde `matches`'ı işi yapmadan anında `false` döndürürdü: doğru kod, sonradan gelen bir
mekanizmayla sessizce geçersiz — #77'nin ve #67'nin tekrar eden dersi. Enjekte edilen kodlayıcıdan
türetmek bu ihtimali yapısal olarak kapatıyor.

**Başarısız deneme artık hesap yokken de kaydediliyor.** `login_attempts.user_id` zaten nullable;
satır `email_attempted`, IP ve `User-Agent` taşıyor, `failure_reason` iki dalı sunucuda ayırt ediyor
(`No account for email` / `Invalid email or password`). Bu tablo hiçbir uçtan istemciye dönmüyor;
iki okuyucusu var: hesap silmede `PostgresAccountAnonymizer` ve saklama işi `LoginAttemptManager`
(§2aw). Bu satırların süresiz saklanmaması da §2aw'nin konusu.

**İki koruma var.** `LoginEnumerationIT` yanıtı sabitliyor — `ExistenceConcealmentIT` deseniyle iki
gövde, `timeStamp` ve `requestId` düşüldükten sonra birebir aynı olmak zorunda — ve deneme kaydının
yazıldığını doğruluyor. `LoginConcealmentTest` parola doğrulamasının hesap yokken de çalıştığını ve
sahte hash'in kodlayıcıdan geldiğini sabitliyor; sabite dönülürse kırmızıya döner.

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

**Back-office personeli bu principal'in dışındadır.** `UserPrincipal` marketplace kimliğidir; personel `StaffPrincipal` tipinde yaşar ve kimliği de artık Koala'da (ADR-0014). İki token aynı `JWT_SECRET` ile imzalanıyor ama **audience claim'iyle ayrılıyor**: marketplace erişim token'ı audience taşımaz (`requireAccessAudience` doluysa reddeder), personel token'ı `koala-staff` taşır (`extractStaffIdentity` başka bir şey kabul etmez). Yani bir marketplace token'ı hiçbir koşulda `StaffPrincipal` üretemez ve bir personel token'ı `SessionAuthFilter`'dan geçemez; ayrım tek bir claim'de ve iki yönde de test edilmiş (§2au).

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

**Bir istek = bir satır.** `RequestLoggingFilter` metot, yol, durum, süre ve kullanıcı id'si basar; 4xx WARN, 5xx ERROR. Güvenlik zincirinin dışında durur ki 401/403 de loglansın; SecurityContext o noktada boş olduğu için kullanıcı id'sini `SessionAuthFilter` istek niteliğine (`app.userId`) bırakır. Sağlık uçları loglanmaz.

**Erişim logunun adı gerçekten `http.access` — bir süre değildi.** Filtre `@Slf4j` kullanıyordu, yani logger adı sınıfın FQCN'iydi ve `logging.level.http.access: ${ACCESS_LOG_LEVEL:INFO}` satırı **var olmayan bir logger'ı** adlandırıyordu: erişim logunun seviyesi `APP_LOG_LEVEL` ile birlikte oynuyor, `ACCESS_LOG_LEVEL` sessizce yok sayılıyordu. Bu bölüm de aynı yanlışı yazıyordu — ölü config'in belgelenmiş hâli. Filtre artık `LoggerFactory.getLogger("http.access")` ile adlandırılmış bir logger kullanıyor; §1'in "logger `@Slf4j` ile gelir" kuralının **tek istisnası** bu, çünkü adlandırılmış bir logger'ı Lombok üretemez. `LoggingConfigurationTest` ayarın gerçek bir logger'ı gösterdiğini, `AccessLogTest` satırın o logger'dan çıktığını sabitliyor.

### İstek kimliğinin tek adı: `requestId`

**`correlationId` tamamen kaldırıldı — yarım geçişin kendisi kırıcıydı.** Araştırmanın hükmü net (`docs/research/logging-and-request-correlation.md`): `correlationId` mesajlaşma terimidir ve sahibi olan her spesifikasyon (EIP, JMS `JMSCorrelationID`, AMQP 1.0, Spring Integration) **aynı değerin birden fazla mesajda görünmesini** şart koşar. Koala'da değer her istekte yeniden üretiliyor ve hiçbir yere taşınmıyordu, yani fiilen bir request id'ydi. #116 doğru adı ekledi ama eskisini geçiş için aynı değerin kopyası olarak bıraktı; tek kavramın iki adı olması, adlandırmayı düzeltmek için yapılan işi geçersiz kılıyordu. Artık zarfta `correlationId`, yanıtta `X-Correlation-Id` yok — `requestId` ve `X-Request-Id` var.

**Şimdi yapmanın bedeli sıfırdı.** Mobil uygulama mağazalarda değil, web ve admin paneli dağıtılmadı; kaldırma hiçbir canlı istemciyi kırmıyor. Bir sürüm beklemek gerçek bir kırıcı değişiklik üretecekti.

**Gelen `X-Correlation-Id` başlığı da kaldırıldı — yeniden adlandırılmadı.** Öncesinde istemcinin gönderdiği değer, regex'ten geçtiği sürece, doğrudan `correlationId` oluyordu; #116 bunu kesip değeri ayrı bir `clientCorrelationId` MDC alanına yazmaya başladı. O alan hiçbir kararı beslemiyordu ve hiçbir istemci başlığı göndermiyordu — `koala-mobile/lib/product/network/api_response.dart` yalnızca bir yorum satırında anıyor, okumuyor. Kullanılmayan bir alanı yeni bir adla korumak, korunacak bir yüzey uydurmak demekti: bakılması gereken bir MDC anahtarı, sürdürülmesi gereken bir doğrulama regex'i, açıklanması gereken bir kavram — hepsi hiç kimsenin doldurmadığı bir alan için. `RequestIdFilter` artık istemci başlığına hiç bakmıyor, `requestId` her zaman sunucuda üretiliyor; log forging yüzeyi de böylece kalmadı, çünkü loga giren tek id sunucunun ürettiği UUID. Bir istemci ileride kendi kimliğini taşımak isterse bu **ayrı ve yeni** bir karardır — o gün alanın gerçek bir kullanıcısı, kendi adı ve kendi doğrulama kuralı olur.

**Sunucu tarafındaki her yer birlikte değişti:** MDC anahtarları (`LogContext`), `logback-spring.xml` deseni (`%X{requestId:-}`), `CorsConfig`'in expose ettiği başlık listesi (`X-Request-Id`) ve `OpenApiConfig`'in 500 açıklaması. Kibana sorguları ve log tabanlı alarmlar `requestId` alanına bakmalı.

**AMQP `correlation-id` özelliği yerinde ve adı orada doğru.** `LogContextPublishPostProcessor` mesajın AMQP `correlation-id` özelliğine `requestId`'yi koymaya devam ediyor: bu koala'nın değil AMQP protokolünün alan adı ve değer orada gerçekten birden fazla mesajda görünüyor (araştırma §7.2). `git grep -i correlation -- src/` bugün yalnızca Spring AMQP'nin `getCorrelationId`/`setCorrelationId` çağrılarını ve kaldırmayı sabitleyen `RequestIdIT` iddiasını gösterir; koala'nın kendi sözlüğünde `correlation` diye bir kavram kalmadı.

**İstemci tarafı aynı turda geçirildi.** `koala-web` (is-koala/koala-web#77), `koala-admin` (is-koala/koala-admin#16) ve `koala-mobile` (is-koala/koala#78) `requestId` okuyor; üç depoda da `git grep -i correlation` boş dönüyor. Geçiş sırasız yapılabildi çünkü #116 önce `requestId`'yi **ekledi**, bu değişiklik ise `correlationId`'yi sonra kaldırdı — arada her iki ad da zarftaydı, yani hangi depo önce inerse insin kırılma olmadı. `koala-admin` alanı hata ekranlarında kullanıcıya gösterdiği için (destek ekibi backend log satırını bununla buluyor) etiketi de düzeltildi: `Correlation ID:` → `İstek numarası:`.

### MDC

**Dokuz alan, hepsi düz — noktasız.** `requestId`, `userId`, `sessionId`, `deviceId`, `method`, `path`, `status`, `durationMs`, `clientIp`. Anahtarlar ve temizlik tek yerde: `core/logging/LogContext`. `RequestIdFilter` en dışta durup istek bitince hepsini siliyor, yani havuzdan gelen Tomcat iş parçacığı bir sonraki isteğe bayat bağlam taşımıyor.

**Anahtarların düz olması bilinçli, ve ECS'in bir tuzağı var.** Spring Boot'un ECS formatlayıcısı MDC çiftlerini `ContextPairs.nested(...)` ile ekliyor: **anahtarda nokta varsa iç içe JSON üretiyor.** Yani `trace.id` doğru ECS alanını verir, `traceId` düz bir alan olarak çıkar ve ECS alanı **değildir**. Koala bugün `traceId` üretmiyor (§Kapsam dışı) ve seçilen anahtarlar ECS alan adları değil, o yüzden hepsi düz. `EcsStructuredLoggingTest` bunu gerçek encoder'la ölçüyor: `requestId` düz çıkıyor, `trace.id` `{"trace":{"id":"…"}}` oluyor. İleride ECS alan adlarına geçilecekse anahtara nokta koymak yeterli — ama o gün dashboard alan adları da değişir.

**`status` ve `durationMs` yalnızca erişim satırında dolu.** İkisi de istek bitmeden bilinmiyor; `RequestLoggingFilter` bunları `finally` bloğunda, satırı yazmadan hemen önce koyuyor.

### MDC'nin süreç sınırlarını geçmesi

**MDC bir `ThreadLocal`, yani hiçbir yere kendiliğinden gitmez.** Kuyruğa atılan bir e-posta ya da gece çalışan bir cron, isteği tetikleyen kullanıcıyla log üzerinden ilişkilendirilemiyordu.

**RabbitMQ'da iki yön de bağlandı.** `LogContextPublishPostProcessor` (RabbitTemplate'in `beforePublishPostProcessors`'ı) taşınabilir alanları (`requestId`, `userId`, `sessionId`, `deviceId`) `x-koala-log-*` başlıklarına yazıyor ve AMQP `correlation-id` özelliğine `requestId`'yi koyuyor — AMQP'nin bu alan adı burada doğru, çünkü değer gerçekten birden fazla mesajda görünüyor (araştırma §7.2). `LogContextListenerAdvice` dört tüketicinin hepsinde (e-posta, SMS, push, iyzico senkronizasyonu) bağlamı geri kuruyor ve iş bitince siliyor. Başlıksız gelen — yani bu değişiklikten önce kuyruğa girmiş — mesaj bağlamsız kalmıyor, kendi `requestId`'sini alıyor.

**Advice, yeniden deneme sarmalayıcısının dışında.** `factory.setAdviceChain(logContextListenerAdvice, retryInterceptor)` sırası bilinçli: bağlam bir teslimatın tüm deneme turlarını kapsıyor, her denemede yeniden kurulmuyor.

**Zamanlanmış işler `taskScheduler` üzerinden ve kendi `requestId`'lerini alıyor.** `ScheduledLogContextDecorator` her koşuya taze bir id veriyor ve bitişte MDC'yi temizliyor. On zamanlayıcının hepsi kapsanıyor — tek tek sarmalamak yerine dekoratör kullanıldı, böylece yeni bir `@Scheduled` metodu eklendiğinde hatırlanacak bir şey kalmıyor.

**Bunu yaparken bir tuzak çıktı ve düzeltildi: `@Scheduled` işleri STOMP kalp atışı zamanlayıcısını paylaşıyordu.** Spring Boot'un `taskScheduler` bean'i `@ConditionalOnMissingBean({TaskScheduler.class, ScheduledExecutorService.class})` ile geliyor; WebSocket mesaj brokeri (§2z) `messageBrokerTaskScheduler` adında bir `TaskScheduler` yayımladığı için Boot'un bean'i **hiç oluşturulmuyordu** ve on cron işi brokerin tek iş parçacığında çalışıyordu. Sonucu yalnız loglama değil: uzun süren bir mutabakat koşusu STOMP kalp atışlarını geciktirip soketleri düşürebilirdi. `ScheduledLogContextConfig` artık `taskScheduler` bean'ini açıkça tanımlıyor; `ScheduledJobLogContextIT` ikisinin ayrı olduğunu sabitliyor. `ThreadPoolTaskSchedulerCustomizer` ile çözülemezdi — Boot o customizer'ı hiç oluşturmadığı bir bean'e uygulayamaz.

### Yapısal format ve desen

**`SPRING_PROFILES_ACTIVE=json` ile çıktı ECS JSON'una döner.** Encoder Spring Boot'un kendi `StructuredLogEncoder`'ı (`<format>ecs</format>`); üçüncü parti encoder yok ve gerekmiyor. Varsayılan bilerek metin.

**Appender'lar artık profil bloklarının içinde.** İkisi de dışarıdaydı ve `json` profili kapalıyken Logback her açılışta `Appender named [CONSOLE_JSON] not referenced. Skipping further processing.` uyarısı basıyordu — başlangıç çıktısında sürekli duran, hiçbir şey ifade etmeyen bir satır. `LoggingConfigurationTest` her appender'ın tanımlandığı profil bloğunda referans edilmesini şart koşuyor.

**Konsol deseni tarih ve saat dilimi taşıyor.** `%d{HH:mm:ss.SSS}` yalnız saat basıyordu: iki günlük bir log dökümünde satırların hangi güne ait olduğu, UTC çalışan bir konteynerde ise hangi saat diliminde olduğu okunamıyordu. Desen Spring Boot'un kendi `LOG_DATEFORMAT_PATTERN` değişkenine bağlandı (varsayılanı `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`), yani `LOG_DATEFORMAT_PATTERN` ile ortamdan değiştirilebiliyor.

**Seviyeler `APP_LOG_LEVEL`, `ACCESS_LOG_LEVEL`, `SECURITY_LOG_LEVEL`, `USER_AGENT_LOG_LEVEL` ile ayarlanır.**

**`/actuator/loggers` ADMIN korumalı açık.** Seviye değiştirmek için yeniden dağıtım gerekmiyor. Ayrı bir güvenlik kuralı yazılmadı: `/actuator/**` zaten `hasRole("ADMIN")` ve `/actuator/**`'ın kimlik modeli ayrı bir kararın (is-koala/koala-backend#49) konusu, burada yeni bir yetki modeli icat edilmedi.

**Yauaa `WARN`'a çekildi.** `nl.basjes` user-agent kural setini yüklerken açılışın ~22 saniyesini INFO satırlarıyla dolduruyor (§9). `USER_AGENT_LOG_LEVEL` ile geri açılabilir.

### İstisnalar

**`log.error` yakalanan istisnayı logger'a vermek zorunda.** 96 `log.error` çağrısının yalnız beşi bunu yapıyordu; 29'u `e.getMessage()` geçiyor, birkaçı istisnaya hiç dokunmuyordu. SLF4J yığın izini **yalnızca son argüman bir `Throwable` ise** basar, yani bu satırların hepsi "bir şey patladı" deyip nerede patladığını yutuyordu — üç para taşıyan zamanlayıcı (mutabakat, ödeme doğrulama, ödenmemiş atama düşürme) dahil. 43 çağrı düzeltildi: mesajdaki `Error: {}` yer tutucusu kaldırıldı ve istisna nesnesi son argüman olarak geçirildi.

**`EmailConsumer` bir istisnayı gerçekten yutuyordu.** `log.error("... ID: {}. Aborting request.", emailTaskDto.id(), e.getMessage())` — bir yer tutucu, iki argüman. SLF4J fazladan argümanı `Throwable` sanmadığı (o bir `String`) için sessizce atıyordu; bildirimin neden bulunamadığı hiçbir yere yazılmıyordu. `UserManager`'da da aynı sınıftan ikinci bir hata vardı, ters yönde: `"... UserId: "` yer tutucusu unutulmuş, argüman kaybolmuştu.

**`LogDiagnosabilityTest` ikisini de yapısal olarak koruyor.** Kaynak kodu tarıyor (yorumları ve metin bloklarını maskeleyerek): bir `catch` bloğunun içindeki her `log.error` yakalanan değişkeni son argüman olarak vermek zorunda, ve her log çağrısının yer tutucu sayısı argüman sayısına eşit olmak zorunda — sondaki `Throwable` hariç. Üçüncü bir test taramanın boşa koşmadığını (400'den fazla çağrı, 40'tan fazla catch içi çağrı gördüğünü) doğruluyor.

**`ExternalServiceException` (502) yığın izi taşımıyor.** Kendi yığın izi hiçbir zaman bilgi vermiyordu — fırlatıldığı satırı zaten `errorCode` söylüyor — ama `GlobalExceptionHandler` 5xx'i `log.error(..., ex)` ile bastığı için her sağlayıcı hatası tam bir yığın izi üretiyordu. `UnconfiguredPushService` her push'ta bu istisnayı fırlattığı için (§2s) gürültü sabit bir maliyetti, üstelik yukarıdaki `log.error` düzeltmesi tüketici tarafında bunu **çoğaltacaktı**. İstisna artık `writableStackTrace = false` ile kuruluyor: tek satır çıkıyor, gerçek teşhis ise fırlatma noktasındaki `log.error(..., e)` satırında duruyor.

**Okuma yolu DEBUG.** `Getting/Fetching/Retrieving/Listing` ile başlayan 18 manager logu DEBUG'a indirildi — erişim logu geldikten sonra bunlar gürültüydü. Yazma işlemleri INFO kalır, denetim izi onlar.

**Erişim logu istemci IP'sini taşır.** İstemci IP'si tek yerden gelir: `ClientIpResolver`. Doğrudan bağlantı `getRemoteAddr()` değeridir; `Forwarded` veya `X-Forwarded-For` yalnız doğrudan kaynak `app.trusted-proxies` içindeki bir IP/CIDR ise okunur. Güvenilmeyen istemcinin başlığı yok sayılır. Spring'in başlığı güven sınırı bilmeden tüketmemesi için `forward-headers-strategy: none`; zinciri yalnız bu çözücü işler. BFF gelen yönlendirme başlıklarını kopyalamaz, bağlantı IP'siyle tek değer yazar.

### Kişisel veri

**PII maskelenir.** `core/helpers/LogMasks`: e-posta yerel kısmı maskeli (`y***@gmail.com`, alan adı kalır ki başarısız giriş takip edilebilsin), telefonun son dört hanesi, IBAN'ın baş ve sonu, TCKN hiç. Kimlik gerektiğinde kullanıcı **id**'si yazılır.

**`LogPrivacyTest` bunu yapısal olarak koruyor.** Kaynak kodu tarayıp hiçbir log çağrısının maskelenmemiş e-posta, telefon, IBAN, TCKN veya parola taşımadığını doğrular. Yazıldığında `NotificationManager` içinde gözden kaçmış bir SMS logunu yakaladı.

**Yasaklı liste genişletildi, çünkü koruma sanıldığı yerde yoktu (#13).** Test yazıldığında yalnız e-posta, telefon, IBAN, TCKN ve parolayı tanıyordu; ad, soyad, doğum tarihi, cinsiyet, serbest metin profil alanları ve token accessor'ları listede hiç yoktu. Üç gerçek sızıntı tam bu boşluktan geçiyordu ve test yeşildi: `UserManager.patchCurrentUser` ad/soyad/doğum tarihi/cinsiyeti **hem eski hem yeni değeriyle** `INFO`'da yazıyordu — yani prod'da açık seviyede —, `AuthManager.verifyRegistrationToken` kayıt tamamlama token'ını `DEBUG`'da yazıyordu ("id" diyen bir mesajın altında; argüman bir bearer token'dı), `CompanyManager.addCompany` şirket adını `INFO`'da yazıyordu. Listeye isim, doğum tarihi, cinsiyet, adres bileşenleri ve serbest metin profil alanları (`about`, `qualifications`, `interests`) eklendi. `*Token` accessor'ları tek tek yazılmadı, `get[A-Za-z]*[Tt]oken[A-Za-z]*\(\)` deseniyle yasaklandı — yarın eklenecek bir token getter'ı listeye kimse yazmasa da yakalanır. Doğrulama kodu ayrı bir isim istemedi: kod `UserVerification.token` alanında duruyor, yani zaten aynı desenin altında.

**Değer silindi, maskelenmedi.** Üç sızıntının hiçbirinde değerin kendisi teşhise yaramıyordu. `UserManager`'da satırlar duruyor ama artık yalnız *hangi alanın* değiştiğini ve kullanıcı id'sini yazıyor: "adı A'dan B'ye değişti" bir hata ayıklamada işe yaramaz, "şu kullanıcı ad alanını değiştirmek istedi" yarar. `AuthManager`'da argüman düştü ve mesaj düzeltildi — token bir id değildi; kaydın kimliği bir satır sonra `pendingRegistration.getId()` olarak zaten yazılıyor. `CompanyManager`'da satırın tamamı silindi: bir sonraki satır aynı olayı `user.getId()` ile zaten kaydediyor. `LogMasks`'e yeni bir maske eklenmedi, çünkü maskelenmiş bir ad da teşhis değeri taşımıyor; maske ancak alan hem kişisel hem de ayırt edici olarak gerekliyse (e-posta, telefon, IBAN) anlamlı.

**Değişken taraması artık argümanları gerçekten ayırıyor.** Eski sezgisel argüman listesini `call.lastIndexOf('"')` ile kesiyordu: bir argüman kendi metin sabitini taşıdığı anda (`log.info("{} {}", email, "n/a")`) ondan önceki her şey taramanın dışında kalıyordu; virgülsüz birleştirme (`log.info("... " + email)`) de görülmüyordu. Şimdi çağrıdan metin sabitleri çıkarılıyor, kalan kod üst seviye virgüllerden bölünüyor ve her argüman tek tek inceleniyor. Yan kazanç: `LogMasks.` muafiyeti de argüman seviyesine indi — önceden bir çağrıda tek bir maskeli argüman görmek çağrının tamamını muaf kılıyordu, yani maskeli ve ham argümanı yan yana taşıyan bir satır sessizce geçerdi. İki sentetik test bölmenin kırmızıya dönebildiğini gösteriyor, üçüncüsü taramanın en az 100 çağrıda argüman çözebildiğini sabitliyor — kırmızıya dönebildiği gösterilmemiş bir koruma doğrulanmamıştır (#72). Ad ve adres gibi isimler `x.getId()` biçimindeki referanslarda muaf: `address` değişkenini loglamak sızıntı, `address.getId()` yazmak değil.

**#13 iki ayrı dalda ilerledi.** PII sızıntıları ve `LogPrivacyTest` genişletmesi bu alt başlıkta; `requestId`, yapısal JSON, MDC ve teşhis edilebilirlik yukarıdaki alt başlıklarda. İkisi ayrı PR'larda birleşti.

**Logger `@Slf4j` ile geliyor.** Faz 19'da elle `LoggerFactory.getLogger(...)` çağıran 59 dosya Lombok'un `@Slf4j` anotasyonuna geçti; alan adı her yerde `log`. Öncesinde dört farklı tanım biçimi vardı (instance/static karışık, hatta `this.getClass()` — static bir alanda derlenmiyordu bile). Spring projelerinde yaygın olan biçim bu.

**Akış anlatımı DEBUG, durum değişikliği INFO.** "Creating address…", "Checking if user is a contact", "Starting registration" gibi 12 anlatı logu daha DEBUG'a indi. INFO artık *olan biteni* kaydediyor: hangi kayıt, hangi id, hangi karar. Erişim logu (§ yukarıda) zaten her isteği bir satırla yazıyor.

**Kapsam dışı: `micrometer-tracing` + OpenTelemetry.** `traceId`/`spanId` W3C Trace Context'in terimleridir ve **servisler arası** izleme içindir; ikinci bir servis olmadan eklenen bağımlılık karşılıksız kalır. Gerçek dağıtık izleme geldiğinde `traceparent` başlığı kabul edilir ve MDC anahtarları `trace.id`/`span.id` biçiminde (noktalı, yukarıdaki tuzak) yazılır.

## 2j. Hız sınırı

**Yedi kova var, yazma ve pahalı genel uçların hepsi kapsanıyor.**

| Kova | Kapsam | Varsayılan | Anahtar |
|---|---|---|---|
| `AUTH` | giriş, kayıt akışı, şifre sıfırlama, kimlik doğrulama, **personel girişi/yenilemesi/parola değişimi** | 10 / 1 dk | yol + IP + hedef özeti |
| `UPLOAD` | görsel, özgeçmiş, profil fotoğrafı, logo, rozet görseli | 20 / 1 dk | kova + IP |
| `MESSAGE` | mesaj gönderme | 30 / 1 dk | kova + IP |
| `REALTIME_CREDENTIAL` | WebSocket bağlantı credential'ı üretme | 10 / 1 dk | kova + IP |
| `PUBLIC` | ilan listesi/detayı/görselleri ve adres ağ geçidi | 120 / 1 dk | kova + IP |
| `SITEMAP` | sitemap manifesti ve en çok 50.000 satırlık shard projection'ı | 30 / 1 dk | kova + IP |
| `WRITE` | **diğer tüm** POST/PUT/PATCH/DELETE — ve tek okuma istisnası olarak `GET /payments/callback` (§2k) | 60 / 1 dk | kova + IP |

**`WRITE` bilerek yakalayıcı.** Yeni bir yazma ucu eklendiğinde listeye yazılması gerekmez, kendiliğinden kapsanır — `SecurityConfig`'teki `anyRequest().authenticated()` ile aynı mantık: unutulan uç açıkta kalmaz.

**Yazma kovalarının anahtarında yol yok.** `AUTH` yol bazlı kalır ve gövdedeki e-posta/telefon/token gibi hedefin SHA-256 özeti IP'ye eklenir; aynı BFF IP'sindeki bir hesabın başarısız denemesi diğer hesabı kilitlemez ve ham PII sayaç anahtarına girmez. Yükleme, genel okuma ve yazmada anahtar yalnızca kova + IP'dir.

**Anahtar IP, kullanıcı değil.** `RateLimitFilter` güvenlik zincirinin dışında (`HIGHEST_PRECEDENCE + 20`) çalıştığı için o noktada principal henüz yok. Kullanıcı bazlı sınır istenirse filtrenin `SessionAuthFilter`'dan sonraya, güvenlik zincirinin içine taşınması gerekir.

**Diğer okuma uçları sınırlanmıyor.** Genel ilan korpusu ve adres sağlayıcı ağ geçidi `PUBLIC`, sitemap
projection'ı ondan bağımsız `SITEMAP` kovasındadır; kimlikli profil okumaları sınır dışıdır. Tek istisna
`GET /payments/callback`: sağlayıcı dönüşünün tarayıcı yenilemesi olduğu için POST ile aynı `WRITE` kovasından
sayılır (§2k), yoksa anonim bir GET ile ödeme kaydı okuma yolu sınırsız kalırdı.

Aşıldığında 429 ve `TOO_MANY_REQUESTS` döner.

**Soket tarafında iki bütçe daha var ve bunlar yukarıdaki tablonun parçası değil.** `RateLimitFilter` bir servlet filtresi; STOMP kareleri ona hiç uğramıyor, dolayısıyla `Bucket` enum'unda karşılıkları yok ve **429 dönmüyorlar**. Sayaç `StompAuthChannelInterceptor` içinden, aynı `RateLimiter` üzerinden düşülüyor:

| Bütçe | Kapsam | Varsayılan | Anahtar |
|---|---|---|---|
| `stomp-subscribe` | STOMP `SUBSCRIBE` kareleri | 30 / 1 dk | önek + STOMP oturum id'si |
| `stomp-message` | STOMP `SEND` kareleri | 60 / 1 dk | önek + STOMP oturum id'si |

**Anahtar STOMP oturumudur, IP değil** — issue'nun istediği sınır "bağlantı başına". `SUBSCRIBE` bütçesi yetki kontrolünden **önce** tükeniyor, yani bir bot veritabanına hiç dokunmadan durduruluyor.

**İki bütçenin aşım davranışı farklı, ve fark bilinçli.** `SUBSCRIBE` aşımı `TooManyRequestsException` fırlatır; bu §2ai'deki yola girip `REALTIME_TOO_MANY_FRAMES` koduyla o aboneliğe ret karesi olarak döner, bağlantı ayakta kalır. `SEND` aşımında ise kare **sessizce düşürülür** (`preSend` null döndürür): karenin cevaplanabileceği bir abonelik id'si yok, `ERROR` göndermek soketi kapatırdı ve bu tam da bu issue'nun kapatmaya çalıştığı davranış olurdu. Bugün tek `@MessageMapping` yazıyor göstergesi; tuşa basıldıkça olay üreten bir istemcinin dakikada 60'ı aşması olağan, ve bunun bedeli bütün gerçek zamanlı oturumu kaybetmek olamaz. Yazıyor göstergesi zaten en iyi çaba — §2z'nin "gerçek zamanlı katman iyileştirme, tek kaynak değil" kuralının aynısı.

Sayaç bellekte (`ConcurrentHashMap`), yani **tek örnek varsayımıyla çalışıyor**. Birden fazla örnek çalıştırıldığında sınır örnek başına uygulanır; o noktada Redis'e taşınmalı.

**Sayaç artık gerçekten temizleniyor.** `RateLimiter.evictExpired` baştan beri yazılıydı ama **hiçbir yerden çağrılmıyordu**; süresi dolmuş her pencere haritada kalıyordu. IP anahtarlarında bu yavaş bir sızıntıydı, gerçek zamanlı bütçeler geldiğinde hızlandı: anahtar STOMP oturumu olduğu için **her yeni WebSocket bağlantısı kalıcı bir satır** bırakırdı. `RateLimiterEvictionScheduler` on dakikada bir süpürüyor; adım idempotent olduğu için çok örnekli kurulumda da zararsız.

Testlerde `rate-limit.enabled=false`; açıkça test eden sınıflar `@TestPropertySource` ile kendi ayarını verir.

## 2k. Ödeme akışı

**Callback gövdesine güvenilmez.** Iyzico `/payments/callback` ucuna yalnızca bir `token` bırakır; tutar, durum ve işlem kimliği o gövdeden **okunmaz**. `PaymentManager.completeFromCallback` token ile Iyzico'ya sunucudan sorar (`retrieveCheckoutFormResult`) ve kararı o yanıta göre verir. Bu yüzden gelen isteğe imza doğrulaması uygulanmıyor — doğrulama zaten sunucudan sunucuya yapılıyor.

**Her deneme ayrı kayıt.** `Payment` tablosu bir `JobAssignment` için birden çok deneme tutar. `conversation_id` benzersiz ve **her denemede yeniden üretilir**; eskiden `assignmentId` ile eşitlenmişti ve ikinci deneme birincisiyle çakışıyordu.

**Bir atamanın en fazla bir aktif denemesi vardır.** Aktif küme `INITIATED`, `VERIFY_PENDING` ve `IN_REVIEW`; `uk_payments_one_active_attempt` kısmi benzersiz indeksi bu invariant'ı gerçek yarışta da korur. Ön kontrol yalnızca hızlı hata içindir; doğruluk veritabanındadır. Constraint çevirisi hata mesajı metnine değil Hibernate'in `constraintName` alanına bakar. `ActivePaymentAttemptConstraintIT` iki gerçek transaction'ı aynı anda koşturup birinin başarı, diğerinin `PAYMENT_ATTEMPT_IN_PROGRESS` 409 aldığını sabitler.


**İkinci istek 409 alır, var olan checkout adresi geri dönmez (#103).** İki davranış tartışıldı: (a) açık denemenin `paymentPageUrl`'ini geri dönmek, (b) `PAYMENT_ATTEMPT_IN_PROGRESS` ile reddetmek. **(b) seçildi.** (a) yalnızca Iyzico oturumu hâlâ geçerliyse doğru cevaptır; oturumun ömrünü biz belirlemiyoruz ve süresi dolmuş bir adresi "işte ödeme sayfan" diye geri vermek, işvereni ölü bir sayfaya gönderip ikinci kez denemesine yol açar — çözmeye çalıştığımız çift tahsilatın ta kendisi. Ayrıca `paymentPageUrl` yalnız initialize yanıtında gelir; onu geri dönebilmek için ham sağlayıcı adresini saklamak gerekirdi, oysa provider token bilinçli olarak public yanıttan çıkarıldı (§2k sonu). 409 + `errorCode` istemciye kararı bırakır: aynı atamada zaten canlı bir tahsilat var, yeni sayfa açma.

**Yarışı kapatan şey uygulama kontrolü değil, indekstir.** `startAttempt` üç katman taşıyor: atama satırında `findByIdForUpdate` kilidi, `hasPendingAttemptForAssignment` ön kontrolü ve `uk_payments_one_active_attempt`. İlk ikisi hızlı ve okunur hata içindir; **doğruluğun tek kaynağı indekstir.** Ön kontrol tek başına yarışı kapatmaz — iki istek de `exists` sorgusundan `false` alıp ikisi de insert'e gidebilir. `ActivePaymentAttemptConstraintIT` bunu `pg_sleep(0.5)` tetikleyicisiyle insert penceresini genişletip iki gerçek transaction'ı aynı anda koşturarak ölçüyor; biri `SUCCESS`, diğeri `PAYMENT_ATTEMPT_IN_PROGRESS` alıyor ve tabloda tek aktif satır kalıyor.

**Sağlayıcıya hiç ulaşamamış deneme 15 dakika sonra süpürülür (#103).** `startAttempt` satırı `REQUIRES_NEW` ile hemen commit ediyor; süreç bu commit ile `attachProviderToken`/`failInitiation`/`markInitiationUncertain` arasında ölürse (deploy sırasında pod'un gitmesi, OOM, `Error`) geriye `status=INITIATED`, `provider_token=null`, `next_verification_at=null` bir satır kalır. Bu satırı **hiçbir otomatik yol kapatamıyordu**: `reconcileAttempt` token'sız satırda erken dönüyor, scheduler ise yalnız `next_verification_at` dolu satırları tarıyor. Aktif deneme indeksi de doğru çalıştığı için sonuç, o atamada ödemenin **kalıcı ve görünmez biçimde** bloke olmasıydı — bir kusuru kapatan kısıt, ikinci bir kusurun kalıcılığını sağlıyordu. `PaymentVerificationScheduler` artık her turda `findStuckInitiationIds` ile `INITIATED` + token'sız + `created_at` 15 dakikadan eski satırları buluyor ve `abandonStuckInitiation` bunları `VERIFY_PENDING` + `{payment.initiation.abandoned}` yapıyor. Eşik `iyzico.payment-verification.initiation-deadline`; meşru bir initiation en kötü ihtimalle iki sağlayıcı çağrısı kadar sürer (10 sn bağlantı + 30 sn okuma, §2b), 15 dakika bunun çok üstünde.

**Süpürme satırı `FAILED` yapmaz, `VERIFY_PENDING` yapar — ve blokeyi kaldırmaz.** Token'sız bir satır "initialize hiç çağrılmadı" ile "initialize çağrıldı, token döndü, biz yazamadan öldük" durumlarını ayırt edemez; ikincisinde işverenin önünde canlı bir checkout sayfası vardır ve para çekilmiş olabilir. Bu yüzden satır terminal yapılmıyor: §2k'nin "tahsilat ihtimali dışlanamadıkça yeni checkout açma" kuralı burada da geçerli. `provider_submerchant_key`'in dolu olup olmamasına bakıp "initialize'a hiç gelinmemiş" satırları otomatik `FAILED` yapmak da düşünüldü ve **reddedildi**: bu, süpürücüyü `JobAssignmentManager.initiatePaymentSession` içindeki çağrı sırasına gizlice bağlardı ve sıra değiştiğinde para yolunda sessizce yanlışa dönerdi.

**Kazanç blokenin kalkması değil görünür olması — ama "görünür" ölçülmüş bir iddia olmak zorunda (#128 incelemesi).** İlk hâlde değildi: süpürme satırı `INITIATED`'dan çıkarınca ona ulaşan **hiçbir** otomatik yol kalmıyordu (`findDueVerificationIds` `next_verification_at is not null` istiyor, süpürücü onu boş bırakıyor), runbook'a eklenen SQL `status='INITIATED'` diyerek tam da süpürülmüş satırı **kaçırıyordu**, alarm regex'inde `abandoned` yoktu ve `VERIFY_PENDING` kuyruğunu listeleyen bir personel ucu yok. Geriye tek bir `log.warn` kalıyordu; yani süpürme sadece kovayı değiştiriyordu. Görünürlük şimdi iki somut yoldan kuruluyor ve **ikisi de testle bağlı**:

1. **Alarm — olayın insana ulaşma yolu.** `KoalaPaymentInitiationAbandoned`, `koala_payment_operations_total{operation="initiation",outcome="abandoned"}` serisini izliyor. `OperationalRunbookContractTest`, sayacı `PaymentMetrics` üzerinden gerçekten yazdırıp **kaydedilen etiketleri okuyarak** alarm dosyasında o seriyi izleyen bir kural arıyor; Java'daki `outcome` adı değişirse ya da kural silinirse test kırmızıya döner. Alarm kritik, çünkü kuyruk temizlenmezse atama iş başlangıcını geçip asılı kalır (aşağıya bak).
2. **Runbook kuyruğu — alarm geldiğinde koşulacak sorgu.** `docs/runbooks/payments.md` içindeki SQL artık süpürülmüş satırı (`VERIFY_PENDING` + `{payment.initiation.abandoned}` + token'sız) seçiyor. `ActivePaymentAttemptConstraintIT` bu sorguyu **belgeden okuyup gerçek Postgres'te koşuyor** ve süpürülen satırı döndürdüğünü, sonucu bilinmeyen bir denemeyi ise döndürmediğini iddia ediyor. Sorgu ile süpürücü ayrışırsa test kırmızıya döner — "runbook'a yazdım" ile "sorgu satırı buluyor" arasındaki fark #128'in en ağır bulgusuydu.

**`next_verification_at` bilerek boş bırakılıyor; mutabakat işine devretmek reddedildi.** Süpürülen satırda token yoktur, `reconcileAttempt` da token'sız satırda erken döner — zamanlayıcıya vermek satırı sonsuza kadar boşa seçtirir ve kuyruğu gürültüyle doldururdu. Görünürlük bu yüzden zamanlayıcıya değil alarm + runbook kuyruğuna dayanıyor; IT bunu iki yönlü sabitliyor (satır `findDueVerificationAttemptIds`'de **yok**, runbook sorgusunda **var**).

**İki olay veritabanında tek kolonla ayrılıyor: `failure_reason`.** `abandonStuckInitiation` ile `markInitiationUncertain` aynı üçlüyü üretiyordu (`VERIFY_PENDING` + `{payment.verification.pending}` + token `null`); operatör "initialize çağrıldı mı" ayrımını yapamıyordu, oysa terminal yapmama gerekçesi **tam olarak** o ayrıma dayanıyor. Süpürme artık `{payment.initiation.abandoned}` yazıyor: sağlayıcıya hiç gidilmedi. `{payment.verification.pending}` ise "gidildi, yanıtı okuyamadık" anlamında kalıyor — orada tahsilat ihtimali daha yüksektir ve triyaj farklıdır. Ayrım `failure_reason`'a konuldu çünkü tek kolon, SQL'den görünür, runbook'tan sorgulanabilir ve `PaymentAttemptDto` üzerinden işverene de çevrilmiş metin olarak ulaşır.

**`verification_attempts` süpürmede artmıyor ve bu bilinçli.** Sayaç sağlayıcıya yapılan imzalı retrieve denemelerini sayar; süpürülen satır için yapılmış bir çağrı yoktur, `1` yazmak olmayan bir sağlayıcı çağrısını raporlamak olurdu ve runbook'un "`verification_attempts=8` → otomatik tavan doldu" dalını bulanıklaştırırdı. Runbook triyajı bu yüzden `0` + `next_verification_at is null` için ayrı bir dal taşıyor.

**`setCompletedAt(null)` / `setNextVerificationAt(null)` kaldırıldı — ölü atamaydı.** Metot yalnız `INITIATED` + `provider_token is null` satırları işliyor; `startAttempt` iki alanı da yazmıyor, o satıra `next_verification_at` yazan tek yol olan `attachProviderToken` ise aynı anda token yazıyor ve muhafız o satırı zaten eliyor. İki alan giriş noktasında **kanıtlanabilir biçimde** boş; atama kopyala-yapıştır kalıntısıydı ve "burada bir şey sıfırlanıyor" izlenimi vererek muhafızın gücünü gizliyordu.

**Süpürme atama satırını kilitliyor, çünkü para yolundaki her yol kilitliyor.** `abandonStuckInitiation` önce `findJobAssignmentIdById` + `lockAssignment`, sonra `findWithDetailsById` — `reconcileAttempt` ve `completeFromCallback` ile birebir aynı sıra. Eski hâli kilitsizdi ve yalnızca `findWithDetailsById`'nin `PESSIMISTIC_WRITE` taşıması sayesinde kazara idempotentti; kaza, kilitlenme sırası kadar dayanıklı değildir ve `startAttempt` atama kilidini tutarken süpürme aynı atamayı yazabiliyordu. Kalıba uyduruldu; `PaymentMonitoringTest` kilidin alındığını doğruluyor.

**Aktif küme tek bir Java sabitidir ve indekse testle bağlıdır.** `PaymentAttemptStatus.ACTIVE` artık tek doğruluk kaynağı; `PaymentManager.PENDING_STATUSES` ona işaret ediyor. Eskiden küme iki yerde ayrı ayrı biliniyordu (Java listesi ve `uk_payments_one_active_attempt` SQL'i) ve hiçbir test ikisini bağlamıyordu — **Java'ya bir durum eklenip indekse eklenmemesi tam da bu PR'ın kapatmaya çalıştığı çift tahsilat deliğini açardı ve sessiz kalırdı.** `ActivePaymentAttemptConstraintIT` iki taraftan da bağlıyor: `theIndexGuardsExactlyTheStatusSetJavaTreatsAsActive` indeks tanımından çıkardığı kümenin `ACTIVE` ile **birebir eşit** olduğunu (`contains` değil, eşitlik) iddia ediyor; `everyStatusJavaCallsActiveIsAlsoBlockedByTheDatabaseAtInsertTime` ise `ACTIVE`'deki her durumu sırayla gerçek satır olarak yazıp üstüne ikinci denemenin veritabanınca reddedildiğini ölçüyor. `ACTIVE`'e `SUCCEEDED` eklenip indekse dokunulmadığında ikisi de kırmızıya dönüyor.

**Süpürülen satırı olan atama süresi dolmuş sayılıp iptal edilemez (#128 incelemesi).** `UnpaidAssignmentExpiryScheduler`, iş başlangıcına 6 saat kalan ödenmemiş atamaları `expireUnpaidAssignment` ile kapatıyor; sorgu (`findUnpaidExpiringIds`) açık deneme sormuyordu ve iptal `CancellationParty.EMPLOYER` + `refundAmount = ZERO` ile yazılıyordu. Sonuç: para çekilmiş olabileceği için **bilerek** terminal yapmadığımız satır, altı saat sonra işverenin suçuna yazılmış sıfır iadeli bir iptalin arkasında öksüz kalıyordu — terminal yapmama kararının bütün değeri orada buharlaşıyordu. `expireUnpaidAssignment` artık atama kilidini tuttuğu yerde `paymentService.hasPendingAttemptForAssignment` soruyor ve açık/belirsiz bir deneme varsa `false` dönüp hiçbir şey yazmıyor. Kontrol sorguya değil metoda konuldu: karar orada veriliyor ve kilit orada tutuluyor, sorgu ile iptal arasında açılan bir deneme aksi hâlde kaçardı. Atamanın asılı kalması bilinçli bedeldir; onu çözen şey yukarıdaki alarm + runbook kuyruğudur, bu yüzden `KoalaPaymentInitiationAbandoned` kritik.

**Zamanlayıcı iki ayrı süpürme koşuyor, iki ayrı adı var.** `runDueVerifications()` hem takılı initiation'ları hem de mutabakatı koşuyordu; ad birini anlatıyordu. `sweepAbandonedInitiations()` ve `reconcileDueAttempts()` diye ayrıldı. Sıra önemsiz ve bu bir tesadüf değil: süpürme yalnız token'sız satırlara, mutabakat yalnız `next_verification_at` dolu (yani token almış) satırlara bakar — iki küme kesişmez. Ayrılma aynı zamanda `tasks_scheduled_execution_seconds_count{outcome=ERROR}` üzerinde iki bağımsız görev veriyor.

**`canInitiate` → `acceptsPayment` (#103).** Ad, çağıranı yanıltıyordu: metot "bu atama tahsilata uygun mu" (durum `CONFIRMED`, ödeme durumu `PENDING_COLLECTION`/`FAILED`) sorusunu cevaplıyor, oysa adı "yeni bir deneme başlatılabilir mi" diye okunuyordu. İkisi ayrı sorudur ve `PENDING_COLLECTION` ilk denemeden **sonra da** atamanın durumu olduğu için ad, açık bir denemenin varlığını sorduğunu sanan bir çağıranı sessizce yanlış yönlendirirdi. "Açık deneme var mı" sorusunun tek sahibi `hasPendingAttemptForAssignment` ve indekstir.
**Rezervasyon sağlayıcıdan önce ve ayrı transaction'da yazılır.** `startAttempt(REQUIRES_NEW)`, alt üye işyeri ve checkout initialize çağrılarından önce commit olur. Böylece iki HTTP isteği aynı anda Iyzico'ya gidemez. Initialize sonucu belirsizse satır geri alınmaz; `VERIFY_PENDING` olur ve yeni checkout'u engeller. Token henüz alınamadığı için bu özel durum `conversation_id` ile panelden elle mutabakat ister. Alt üye işyeri çağrısı gibi checkout'un hiç başlamadığı bilinen erken hata ise rezervasyonu terminal `FAILED` yapar.

**Callback artık doğrulanıyor.** Sağlayıcının `paymentStatus`, `fraudStatus`, `paidPrice` ve `currency` değerleri kontrol edilmeden emanet açılıyordu; ayrıntı ve neyin kırık olduğu §2x'te.

**Belirsiz doğrulama başarısızlık değildir.** Retrieve ağ/HTTP/imza hatası veya imzalı yanıttaki immutable snapshot uyuşmazlığı `FAILED` yapılıp yeni ödeme açmaz; `VERIFY_PENDING` durumunda aktif kalır. Token'ı olan satırlar `PaymentVerificationScheduler` tarafından üstel geri çekilmeyle en çok 8 kez yeniden sorulur. Otomatik tavan dolunca satır aktif kalır, `next_verification_at=null` olur ve manuel mutabakat bekler. `IN_REVIEW` aynı döngüyle tekrar sorgulanır.

**Callback idempotent.** Zaten `SUCCEEDED` olan bir ödemeye ikinci callback geldiğinde sağlayıcıya hiç gidilmez, kayıt değiştirilmez. Iyzico aynı callback'i tekrar gönderirse ikinci kez para hareketi işlenmez.

**Callback CORS filtresinin dışındadır ve GET karşılığı vardır (#252).** Iyzico ödeme formu bitince tarayıcıyı `Origin: https://sandbox-api.iyzipay.com` (canlıda `api.iyzipay.com`) başlığıyla **üst düzey form POST**'una yönlendirir; Spring `CorsFilter` `Origin` taşıyan her isteği CORS isteği sayar ve izin listesinde olmayan kaynağı `403 Invalid CORS request` ile keser — `SecurityConfig`'in `permitAll` kuralına hiç gelinmiyordu, kullanıcı `api.iskoala.com` üzerinde beyaz sayfa görüyordu. `CorsConfig`, gerçek zamanlı el sıkışmada yaptığı gibi `/payments/callback` için yapılandırma döndürmez (`null`); CORS başlığı da yazılmaz, yol yalnızca filtrenin kapsamı dışında kalır. Iyzico izin listesine **eklenmedi**: sorun liste değil filtrenin kapsamıdır ve callback zaten sunucudan sunucuya doğrulanır (yukarısı). Sayfa yenilemesi ya da tarayıcının POST'u GET'e çevirmesi için `GET /payments/callback?token=` var: tokenı **tüketmez**, sağlayıcıya **gitmez**, atama kilidi almaz; `findRecordedOutcome` ile kaydın son durumunu okuyup aynı sonuç sayfasına 302 döner. Bilinmeyen token `status=failed` ile aynı sayfaya gider, `assignmentId` taşımaz (404/401 yok). Hız sınırında GET de POST ile aynı `WRITE` kovasından sayar; günlük satırı kanalı (POST/GET) söyler, token günlüğe yazılmaz. `CorsContractTest`, `RateLimitBucketTest`, `PaymentsControllerRedirectTest` ve `PaymentCallbackIT` (Iyzico `Origin` başlığıyla gerçek satır üzerinde) sabitler.

**Durum geçişleri `PaymentStateMachine`'de.** Para yalnızca ileri gider: `PENDING_COLLECTION → HELD_IN_ESCROW → REFUNDED`. Başarısız deneme `FAILED`'a düşer ve yeniden denenebilir. Ödenmiş bir atama tekrar tahsilata açılamaz; geçersiz geçiş sessizce uygulanmaz, `IllegalStateException` fırlatır.

**Ödeme onaylanınca kırılım atamaya yazılır.** `finalPayout`, `workerPayout`, `commissionAmount`, `commissionRate` ve `totalCharged` o anki değerlerle sabitlenir.

**Emanet garantisi işin başlangıcında zorunlu.** `checkIn` ve `completeAssignment` artık `paymentStatus == HELD_IN_ESCROW` şartı arıyor. Öncesinde ikisi de yalnızca atama durumuna bakıyordu; yani **işveren tek kuruş ödemeden işçi işe başlayabiliyor, çalışabiliyor ve iş tamamlanabiliyordu.** Sonunda `releaseToWorker` "emanette para yok" deyip reddediyordu ve `openDispute` de emanette para şartı aradığı için işçinin başvurabileceği hiçbir yol kalmıyordu. Ödeme sırası değişmedi (işçi onaylar → işveren öder), değişen şey ödemenin **kapı** hâline gelmesi.

**İşçiye çıkış ayrı bir adım.** `HELD_IN_ESCROW` durumundaki para kendiliğinden işçiye geçmez. `PayoutService.releaseToWorker` yalnızca atama `COMPLETED` ise çalışır ve `Payout` kaydı üretip durumu `RELEASED_TO_WORKER` yapar. Aynı atama için ikinci kez çağrılırsa yeni kayıt açılmaz. `refundEmployer` ise yalnızca `HELD_IN_ESCROW` iken mümkündür — işçiye çıkmış para iade edilemez.

**IBAN artık `User` üzerinde değil.** `BankAccount` entity'sinde; birincil hesap `is_primary` ile işaretli, doğrulama `BankAccountManager` içinde. `users.iban` kolonu mevcut veriler `bank_accounts`'a kopyalandıktan sonra düşürüldü — iki kaynak bırakılmadı.

**Banka hesabı HTTP yüzeyi koleksiyon tabanlı.** `GET/POST /users/me/bank-accounts`, `POST /users/me/bank-accounts/{accountId}/primary`, `POST /users/me/bank-accounts/{accountId}/verification` (§2k2) ve `DELETE /users/me/bank-accounts/{accountId}` yalnızca oturum sahibinin kayıtlarına çalışır. DTO ham IBAN taşımaz; `maskedIban`, `id`, `primary` ve ilerideki sahiplik doğrulama akışına ayrılmış `verificationStatus` döner. İlk etkin hesap otomatik birincildir. Veritabanındaki kısmi benzersiz indeks aktif ve silinmemiş hesaplarda kullanıcı başına en fazla bir birincil kaydı garanti eder; kullanıcıya ait yazmalar transaction-scoped advisory lock ile seri hale getirildiği için eşzamanlı birincil seçimleri de bu invariant'ı bozmaz.

**Eski profil yazma sözleşmesi geçiş boyunca korunuyor.** `PATCH /users/me { iban }` yeni bir IBAN gelirse mevcut birincil kaydı yerinde günceller, aynı IBAN ikincil hesaplarda zaten varsa yeni kayıt açmadan onu birincil yapar. `GET /users/me` içindeki `maskedIban` her zaman koleksiyonun mevcut birincil hesabından üretilir; `users.iban` geri getirilmez ve ikinci bir doğruluk kaynağı oluşmaz. Yeni koleksiyon ucuna yalnızca Türkiye IBAN'ı (`TR`, 26 karakter, mod-97) kabul edilir; ham değer yanıta, hata mesajına, telemetry'ye veya log'a yazılmaz.

**`verificationStatus` artık ileriye uyumlu bir veri sözleşmesi değil, para yolunun kapısı** — geçişleri, zaman aşımı, yeniden deneme ve kapının yeri §2k2'de.

`PaymentSessionDto` yalnız güvenilir `paymentPageUrl` ve fiyat kırılımını taşır. Iyzico provider token'ı dahili korelasyon sırrıdır; public yanıttan çıkarıldı ve veritabanında `uk_payments_provider_token` ile benzersizdir.

## 2k2. IBAN sahipliği doğrulaması — para yolunu kapatan kontrol

Zarar üç halkalı (`docs/research/uzaktan-kimlik-dogrulama.md` §7.1): kimlik iddiası, kartın gerçekliği ve
**paranın gideceği hesabın sahipliği**. Üçüncüsü hiç sorulmuyordu: `IbanValidator` yalnızca uzunluk, ülke kodu
ve mod-97 kontrol hanesine bakıyor, yani "bu IBAN geçerli bir IBAN mı" sorusunu cevaplıyor — "bu IBAN senin mi"
sorusunu değil. Elinde başkasının gerçek kartı olan biri kurbanın TCKN'siyle ama **kendi IBAN'ıyla** alt üye
işyeri açtırabiliyordu.

**Yön (a) seçildi: kullanıcı kendi kayıtlı IBAN'ından Koala'ya küçük tutarlı bir transfer yapar.** Issue iki yön
öneriyordu; (b) — Koala'dan kullanıcıya gönderim + dönen hesap sahibi adının karşılaştırılması — bankadan
dönen adı okuma yeteneği gerektiriyor ve o yetenek bugün elimizde olduğu **doğrulanmış değil**. (a)'da kanıt
kendiliğinden sağlam: gönderen IBAN kayıtlı IBAN'la eşleşmezse doğrulama geçmez, üçüncü tarafa bağımlılık yok.
Bu, BDDK m.7/3 ve MASAK Tebliğ 19 m.6/4'ün "çip doğrulanamadığında ilk finansal hareket kişinin kendi banka
hesabından yapılsın" çözümünün kopyası.

**Durum modeli dört değerli, ve `is_verified` boolean'ı bilerek kullanılmadı.** "Hiç denenmedi" ile "denendi,
tutmadı" ayrımı gerekiyor (§2n'deki rıza kaydı gerekçesinin aynısı): `UNVERIFIED` (hiç denenmedi),
`PENDING` (transfer bekleniyor), `VERIFIED` (gönderen IBAN eşleşti), `EXPIRED` (denendi, tutmadı ya da süresi
doldu). `bank_accounts` üzerine beklenen tutar, tekil referans, başlangıç/bitiş anı, doğrulanma anı, harcanan
deneme sayısı ve `account_holder_name_verified` bayrağı eklendi.

**Süresi dolmuş `PENDING` veritabanında beklemez, okunurken hesaplanır.** `BankAccountVerificationPolicy.effectiveStatus`
`PENDING` + `expiresAt` geçmişte ise `EXPIRED` döndürüyor; kalıcı geçiş bir sonraki başlatmada yazılıyor. Bir
zamanlayıcı eklenmedi — süpürme işi olmadan da hem kapı hem yanıt doğru davranıyor ve tek örnek varsayımına
bir zamanlayıcı daha eklemeye değmedi (§7, Faz 11D).

**Zaman aşımı 72 saat, deneme bütçesi 5.** İkisi de `bank.verification.*` altından, yani `.env` ile
ayarlanabiliyor. Beklenen tutar `1.01`–`1.99` TL arasında rastgele bir kuruş; sabit tutar seçilmedi çünkü
kuruş hanesi referansın yanında ikinci bir eşleştirme kanıtı veriyor. Tutar `BigDecimal`, ölçek 2 (§2e).
Referans `KOALA-` + 8 karakter, karışabilen harfler (I, O, 0, 1) alfabede yok ve `verification_reference`
üzerinde kısmi benzersiz indeks var.

**Yeniden deneme kuralı: bekleyen bir doğrulama varken yeniden başlatmak yeni deneme harcamaz** — aynı tutar ve
referans döner (idempotent). Deneme ancak `UNVERIFIED` ya da `EXPIRED` durumundan başlatıldığında harcanır.
Gönderen IBAN ya da tutar tutmazsa o deneme `EXPIRED`'a düşer ve kullanıcı bütçesi bitene kadar yeniden
başlatabilir. Başarısız deneme **kalıcı olmalı**, yoksa bütçe hiç tükenmezdi: `confirmVerification`
`@Transactional(noRollbackFor = ValidationException.class)` taşıyor, aksi hâlde reddi yazan `UPDATE` de geri
alınırdı.

**IBAN değişince doğrulama düşer, ve bu kural entity'nin içinde.** `BankAccount.setIban` değeri
karşılaştırıyor ve değiştiyse `forgetVerification()` çağırıyor: durum, referans, tutar, süreler, doğrulanma anı,
deneme sayısı, hesap sahibi adı ve adın doğrulanmışlık bayrağı birlikte sıfırlanıyor. Kural manager'a yazılsaydı
ikinci bir yazma yolu eklendiği gün sessizce atlanabilirdi — **doğru bir kararın sonradan eklenen bir
mekanizmayla geçersiz kılınması** bu depodaki tekrar eden kusur (§2d2, #77). Hibernate `@Id` alan üzerinde
olduğu için alan erişimi kullanıyor, yani veritabanından yükleme bu setter'a uğramıyor ve doğrulama
hidrasyonda kaybolmuyor (`BankAccountIT` ve `IbanOwnershipGateIT` bunu gerçek Postgres'te doğruluyor).
Deneme sayısının da sıfırlanması bilinçli: yeni IBAN yeni bütçe hak ediyor, aynı IBAN yeniden yazıldığında ise
`Objects.equals` yüzünden hiçbir şey sıfırlanmıyor.

**`accountHolderName` artık kullanıcının kendi adından türetilmiyor.** Alan yalnızca doğrulama bir ad
döndürdüğünde doluyor: personel ekstredeki hesap sahibi adını `senderName` ile bildirirse yazılıyor ve
`account_holder_name_verified` `true` oluyor; bildirmezse alan `null` kalıyor ve bayrak `false` — yani
"doğrulanmamış" hâli, uydurma bir değerle değil **boşlukla** temsil ediliyor.

### Transferin sisteme nasıl bildirildiği — seçilen yol ve eksik kalan

**En basit çalışan yol seçildi: elle personel onayı.** `POST /admin/bank-account-verifications/{reference}/confirm`,
ekstredeki gelen transferin gönderen IBAN'ını, tutarını ve varsa hesap sahibi adını alıyor; karşılaştırmayı
Koala yapıyor. Uydurma bir banka entegrasyonu yazılmadı.

Uç yeni bir `StaffPermission` ile korunuyor: `bank.account.verify` (`FINANCIAL`, mutating), seed'de yalnız
`FINANCE_OFFICER`'a veriliyor (§2au'daki görev ayrılığı: ekstre okumak finans dalının işi). Mevcut bir iznin
altına sokulmadı — `payout.release` para çıkarmayı, `payment.read` okumayı adlandırıyor; ikisi de bu eylem
değil. Uç `/admin/**` altında olduğu için denetim kaydına giriyor (§2av).

**Eksik kalanlar, açıkça:**

1. **Banka bildirimi yok.** Koala'nın tahsilat hesabına gelen transferi kimse otomatik okumuyor; personel
   ekstreye bakıp ucu çağırmak zorunda. Banka API'si ya da MT940/CAMT ekstre beslemesi geldiğinde aynı
   `confirmVerification` çağrısı bir tüketiciden yapılabilir — servis imzası bunu zaten kaldırıyor.
2. **Tahsilat IBAN'ı henüz yok.** `BANK_VERIFICATION_COLLECTION_IBAN` boşken doğrulama hiç başlatılamıyor
   (502, `bank.account.verification.unavailable`) ve **hiçbir işçi emanetten para alamıyor**. Değer girilmeden
   canlıya çıkılamaz; `.env.example` bunu yazıyor.
3. **Gelen transferin iadesi düşünülmedi.** Doğrulama tutarı Koala'da kalıyor; kullanıcıya geri gönderilmesi ya
   da ilk hakedişine eklenmesi ürün kararı.
4. ~~**Alt üye işyeri kaydındaki IBAN'ın tazelenmesi.**~~ **Kapandı (#109).** Profil güncellemesi artık
   yalnız doğrulanmış IBAN'ı kuyruğa yazıyor; gerekçesi aşağıdaki başlıkta.
5. **KKB IBAN Doğrulama Hizmeti** erişimi gelirse bu akışın yerini alabilir ya da onu tamamlayabilir; başvuru
   insan işi ve #71'de duruyor.

### Kapı nereye kondu ve neden

**Karar (#127):** `releaseToWorker` birincil IBAN yoksa reddeder; sahipliğin 1,01 TL ile kanıtlanmış olması şimdilik şart değil. 1,01 TL akışı kodda durur, kapı değildir.

**`JobApplicationManager.assertReadyToWork` bilerek değiştirilmedi.** Orada bugün yalnız IBAN'ın *varlığına*
bakılıyor (§2ar) ve öyle kalıyor. Gerekçe: başvuruyu engellemek kullanıcıyı **zararın doğmasından çok önce**
durdurur — henüz kimse ona para göndermeyecekken bankasına gönderir, üstelik hiç iş alamayacak bir kullanıcı
için doğrulama transferi yaptırmak boşa iş. Zarar parayla doğuyor, kapı da tam orada kesiyor. Araştırmanın
kendi ifadesi de bu: "işçinin **ilk emanet çıkışı** serbest bırakılmadan önce".

**Alt üye işyeri kaydı birincil IBAN'ı yazar (#127).** 1,01 TL sahiplik kanıtı şimdilik bu kaydı durdurmaz. Kimlik hâlâ `isIdentityVerified` ister.

**Bunun bedeli yazılı olsun (#127, 2026-09-11):** 1,01 TL havale doğrulaması şimdilik **zorunlu değil**. Alt üye işyeri ve emanet, dosyadaki birincil IBAN'a gider. Kimlik TCKK veya belge fotoğrafı. Havale çok sonraya; `findVerifiedPrimaryAccount` durur, kapı olarak kullanılmaz.

### Sağlayıcıdaki kayıt yalnız doğrulanmış IBAN'ı gösterir (#109)

**Kapı indikten sonra profil güncellemesi yolu açık kalmıştı.** `UserProducerService.convertToEventDto`
`findPrimaryIban` okuyordu, yani doğrulama durumuna hiç bakmıyordu. İşçi doğrulanmış IBAN A ile alt üye
işyeri açtırıp sonra IBAN'ını B ile değiştirdiğinde `BankAccount.setIban` doğrulamayı doğru biçimde
düşürüyor, ama bir sonraki profil güncellemesi **B'yi** Iyzico'ya senkronize ediyordu. Para akmıyordu —
`releaseToWorker` kapısı tutuyor. Kaybolan şey Koala'nın kaydı ile sağlayıcının kaydı arasındaki
**tutarlılık**tı: mutabakatta iki taraf farklı IBAN gösteriyor ve Koala'nın hangi hesabı bildiği artık
sağlayıcıya bakarak anlaşılamıyor. Iyzico tarafında Koala'nın görmediği bir yolla ödeme tetiklenirse
(mutabakat düzeltmesi, elle işlem) para doğrulanmamış hesaba giderdi.

**Karar: son doğrulanmış IBAN gönderilmeye devam eder, doğrulanmış hesap yoksa alan `null` gider.** Kod
artık `findVerifiedPrimaryAccount` okuyor, yani süzgeç `BankAccountVerificationPolicy.isVerified` üzerinden
ve kapının geri kalanıyla aynı yerden geliyor. Gerekçe tek cümlede: **sağlayıcıdaki kayıt, Koala'nın fiilen
para göndermeye razı olduğu hesabı göstermeli.** `releaseToWorker` doğrulanmamış hesaba para vermiyor;
sağlayıcı kaydının da onu göstermesi, mutabakatta iki tarafın aynı şeyi söylemesi demek. Kullanıcının yeni
IBAN'ı doğrulanana kadar sağlayıcıda eskisi kalıyor — **kayıp değil, kastedilen davranış**: doğrulanmamış
hesap Koala için henüz yok hükmünde.

**Doğrulanmış hesap hiç yokken ne olduğu tel seviyesinde yazılı olsun.** Alan `null` gidiyor ve
`IyzicoSynchronizationConsumer` onu olduğu gibi `SubMerchantUpdateRequestDto`'ya geçiriyor;
`IyzicoPaymentService.updateSubMerchant` gövdeyi `HashMap` ile kuruyor ve uygulamada Jackson'ın null atlama
ayarı açık değil, yani istek `"iban": null` taşıyor — alan atlanmıyor. Iyzico'nun bunu "değiştirme" diye mi
okuduğu yoksa reddettiği mi **doğrulanmış değil**; bu, #71'in insan yarısındaki sorunun burada kalan tek
kalıntısı ve sandbox'ta ölçülmeden kapanmaz (§2x, Faz 7C). Kalıntının kapsamı dar: yalnız alt üye işyeri
anahtarı olan bir kullanıcı doğrulanmış IBAN'ını doğrulanmamış bir yenisiyle değiştirdiğinde bu yola giriliyor.
Yine de eski davranıştan **kötü değil**: o hâlde aynı istek doğrulanmamış IBAN'ı sağlayıcıya yazıyordu.

**Senkronizasyonu tümden ertelemek elendi, ve kaynaktan.** Olay yalnız IBAN taşımıyor: `id`, `firstName`,
`lastName`, `email`, `gsmNumber`, `address`, `identityNumber` ve `iyzicoSubMerchantKey` aynı olayda gidiyor.
"IBAN doğrulanana kadar bekle" demek, doğrulanmamış bir IBAN yüzünden **ad, e-posta, telefon ve adres**
değişikliklerinin de sağlayıcıya ulaşmasını engellemek olurdu — kapatmaya çalıştığımızdan daha büyük bir
tutarsızlık. O yüzden kural alan bazında: yalnız `iban` düşüyor, olayın geri kalanı her iki hâlde de gidiyor
ve `UserProfileSyncRulesTest` bunu ayrıca bağlıyor.

**"Alanı hiç gönderme" seçeneği açık bir soruya bağlıydı, seçilen yol değil.** #71'in insan yarısındaki soru
— Iyzico PERSONAL alt üye işyeri için `iban` ve `identityNumber`'ı kendisi doğruluyor mu (§2x, Faz 7C) —
yalnız o seçeneği ilgilendiriyor: alanı hiç göndermemenin mümkün olup olmadığını o belirliyor. Burada her
zaman geçerli, doğrulanmış bir IBAN gidiyor, yani sağlayıcının doğrulama davranışı sonucu değiştirmiyor.
Belirsizlik yalnız **doğrulanmış hesap hiç yokken** kalıyor ve o hâlde zaten gönderilecek geçerli bir değer
yok.

**Kapsam bilerek dar tutuldu: `findPrimaryIban`'ın diğer iki çağrısı değişmedi.** `UserManager` (§2s) onu
`maskedIban` üretmek için okuyor — kullanıcının kendi verisini kendine gösteriyor, doğrulama sorusu orada
yok. `JobApplicationManager.assertReadyToWork` (§2ar) başvuru kapısında yalnız IBAN'ın *varlığına* bakıyor;
bu bilinçli olabilir — doğrulama zaman aldığı için hunideki başvuruyu değil parayı tıkamak — ve ayrı bir
ürün kararı olarak #127'de bekliyor. Kusur sayıp kapatmak, iki paragraf yukarıdaki "kapıyı zararın doğduğu
yere koy" kararını sessizce geri almak olurdu.

### Profil senkronu Rabbit hatasında kaybolmaz (#158)

`UserProducerService.publishUserProfileUpdated` broker hatasını loglayıp yutuyordu: kullanıcı transaction'ı
commit oluyor, Iyzico senkronunun tek tetikleyicisi kayboluyordu. Outbox yoktu.

**Karar: #155 bildirim dispatch outbox'ının aynı sözleşmesi.** Çağıranın gördüğü başarı broker teslimi değil,
DB değişikliği ile exact sync payload'ının aynı transaction'da kalıcı kabulüdür. `UserProducerService` artık
yalnız `UserProfileSyncService.enqueue` çağırır; Rabbit'e buradan gidilmez. Worker üç fazlıdır: kısa
transaction claim + fencing token alır, `ConfirmedRabbitPublisher` mandatory routing ve correlated confirm
ile transaction dışında yayınlar, sonuç yalnız aynı token hâlâ sahibiyse yazılır. Broker outage retry/backoff
ile en fazla yapılandırılmış attempt kadar dener, sonra `BROKER_PUBLISH_EXHAUSTED` ile terminal olur ve
alarm yanar. Stale claim attempt bütçesinden düşülmeden yeniden alınır — publish sonrası process çökmesi
sessiz kayıp değildir. Aynı intent'in yeniden teslimi `processing_claim_id` ile Iyzico'ya ikinci kez gitmez.
#109'un doğrulanmış-IBAN süzgeci enqueue anındaki payload'da duruyor.


## 2l. Sayfalama ve ilan araması

**Sayfalama ile koleksiyon fetch'i aynı sorguda olmaz.** Hibernate LIMIT'i veritabanına veremeyip tüm satırları belleğe çeker. Kural: `JobDao.searchIds` önce **yalnızca id'leri** sayfalar, sonra `findAllByIdIn` o id'ler için varlıkları entity graph ile tek sorguda yükler. Sıra `inIdOrder` ile korunur.

**Şirket detayındaki genel ilan listesi de aynı arama sözleşmesini kullanır.** `GET /jobs?companyId={id}` şirket kimliğini `searchIds` sorgusunda süzer; sonradan ada ya da dönen tek sayfaya göre elemez. Böylece aynı adlı şirketler karışmaz, bireysel ilanlar dışarıda kalır ve şirketin yirmi birinci ilanı normal sayfalama ile okunur. Bu uç herkese açık `OPEN` ilan aramasıdır; yalnız şirket yetkililerine açık olan `GET /companies/{id}/jobs` yönetim listesinin yerine geçmez.

**Tekil atama liste içinde aranmaz.** `GET /job-assignments/{id}` listeyle aynı `MyAssignmentDto` sözleşmesini, adres görünürlüğünü ve kapak zenginleştirmesini kullanır. `AssignmentParticipants` işçiyi, ilan sahibini ve şirketin güncel yetkilisini tek sınırda çözer; diğer kullanıcı ve olmayan kimlik aynı `job.assignment.not.found` 404 yanıtını alır. Güncel şirket yetkilisinin okuma hakkı ona kapalı şirket adresini açmaz: yalnız etkin işçi, ilan sahibi veya `addressPublic` tam konumu görür.

**Süresi geçmiş ilanlar aramada görünmez ve başvuru kabul etmez.** `searchIds` `status = OPEN`'a ek olarak `endDateTime > :activeAfter` şartını taşır (bitiş tarihi olmayan ilanlar aramada hariç tutulmaz). Tam bitiş anı artık kapalıdır. `JobAvailability`, `Clock` ve `job-search.zone-id` ile aynı kuralı başvuru yazımında ve detay cevabındaki `acceptingApplications` alanında uygular; eksik bitiş zamanını başvuru ve detay için fail-closed değerlendirir. Böylece doğrudan detay URL'si aramadan düşmüş bir ilana yazma yolu açmaz, web de aynı boolean ile başvuru ve indeksleme kararını verebilir. Karşılaştırma duvar saati olduğu için `job-search.zone-id` üzerinden yapılır (§2h ile aynı gerekçe).

`:activeAfter` **null olamaz.** Null bırakılsaydı Postgres parametrenin tipini çıkaramayıp `could not determine data type` verirdi — `:query` için `cast(... as string)` gerektiren tuzağın aynısı. Parametreyi zorunlu yapmak sorunu kaynağında kaldırıyor.

**`JobStatus` üç değer:** `OPEN`, `CLOSED`, `CANCELLED` (#115). `IN_PROGRESS` ve `COMPLETED` sözleşmeden düştü — atanmıyorlardı ve ilanı otomatik `IN_PROGRESS`'e almak aramadan düşürüp sonradan işe alımı imkânsızlaştırırdı (§2ao2). İşin sürdüğü/bittiği `AssignmentStatus`'ta durur. İlanı işveren elle kapatır veya iptal eder.

**Sıralama beyaz listeye kısıtlı.** `PageableSanitizer` `createdAt`, `salary`, `startDateTime`, `title` alanlarına, `GET /jobs` `lat/lng` taşıyorsa bir de `distance`'a izin verir (aşağıda, #270); listede olmayan bir alan sessizce yok sayılmaz, **400 döner**. Sayfa boyutu 100 ile sınırlı, `Pageable.unpaged()` geldiğinde varsayılana düşer.

**Tarih aralığı ve mesafe sıralaması (#270).** `GET /jobs` `startFrom`/`startTo` ile `Job.startDateTime`'ı **kapsayıcı** aralıkla süzer ve `lat`/`lng` verildiğinde `sort=distance,asc` ile en yakından uzağa sıralar; her kart `distanceKm` (1 ondalık) taşır. Dört karar açıklama istiyor:

- **Aralık sınırları `Instant` alır, `LocalDateTime` değil.** `startDateTime` duvar saati (§2h); istemcinin gönderdiği an `JobAvailability.wallClock` ile `job-search.zone-id` diliminde duvar saatine çevrilip karşılaştırılır — `CancellationPolicy`'nin `atZone(zone)` kuralının ters yönü ve aynı sorgudaki `:activeAfter` ile aynı dilim. `LocalDateTime` + `@DateTimeFormat(iso = DATE_TIME)` seçilmedi: `LocalDateTime.parse("…T00:00:00Z", ISO_DATE_TIME)` dilim ekini **sessizce yok sayar**, web'in `toISOString()` ile gönderdiği `21:00Z` gece yarısı yerine 21:00 duvar saati olurdu. Dilimsiz değer 400 alır (`details[].field=startFrom`); `AuditLogController.from/to` ile aynı kalıp. Ters aralık ve tek başına `lat` ya da `lng` DTO'daki `@AssertTrue` ile 400 (`job.search.start.range.invalid`, `job.search.location.incomplete`, §2ah); bu iki getter springdoc'un `@ParameterObject` taramasına girmez, `OpenApiDocumentIT` bunu sabitliyor.
- **Mesafe herkese açık konumdan hesaplanır, tam adresten değil (ADR-0003).** `JobDao.PUBLIC_LATITUDE/LONGITUDE`: `addressPublic` şirket ilanı tam koordinat, geri kalanı `round(x, 2)` — `JobAddressVisibility.approximateCoordinate`'in SQL karşılığı. Aksi hâlde `distanceKm` üçgenlemeyle bireysel işverenin ev adresini geri verirdi. `JobSearchDateDistanceIT` bunu iki aynı koordinatlı ilanla ölçüyor: bireysel ilan `0.0 km`, herkese açık şirket ilanı `0.6 km`.
- **Küresel kosinüs yasası düz formülle `select` içinde, PostGIS/`earth_distance` yok** (`git grep postgis` boş). Haversine değil: `6371 · acos(sin φ₁ sin φ₂ + cos φ₁ cos φ₂ cos Δλ)`; `acos` argümanı kayan nokta taşması için `least/greatest` ile [-1, 1]'e kırpılır — ve tam bu yüzden koordinatı `null` olan adres `case`'in **dışında** bırakılır: Postgres `GREATEST(-1, NULL)` null'ı yok sayıp `-1` döndürür, `acos(-1)` de her koordinatsız ilanı 20.015 km'ye (yarım çevre) atardı; `JobSearchDateDistanceIT` ölçtü. Kilometre altı mesafede Haversine'e göre sayısal kararlılığı düşüktür, ama koordinatlar zaten iki ondalığa yuvarlı ve sonuç bir ondalığa yuvarlanıyor; fark görünmez. `searchIds` artık `select j.id as id, <ifade> as distance` döndürüyor (`JobSearchHit` projeksiyonu); Spring Data'nın HQL dönüştürücüsü `as distance` takma adını kaydettiği için `Sort.by("distance")` `order by distance` olarak **öneksiz** çıkıyor — `PageableSanitizer` bu yüzden `distance`'ı sıradan bir alan gibi allowlist'e alıyor. Allowlist **yalnız `lat/lng` verildiğinde** `distance` içerir; onsuz `sort=distance` diğer bilinmeyen alanlar gibi 400 `PAGE_SORT_FIELD_NOT_ALLOWED`. `distance,desc` de kabul edilir (allowlist yön bilmez); koordinatı olmayan ilanın mesafesi `null`dır ve `withDistanceOrdering` `NULLS LAST` yazdığı için iki yönde de en sonda gelir — Postgres varsayılanına (asc'de sonda, desc'de başta) bırakılmaz. Aynı yerde `createdAt DESC` kırıcı olarak eklenir: iki ondalığa yuvarlanmış koordinat aynı hücredeki ilanları eşitler, kırıcı olmadan offset sayfalama satır atlayabilirdi. `distanceKm` Java'da yeniden hesaplanmaz, projeksiyondaki değer 1 ondalığa yuvarlanır; ek sorgu yok.
- **Parametreler null değil, bayrak + sınır değeri.** `:startFrom is null or …` çalışmıyor: pgjdbc `LocalDateTime`'ı tipsiz (`UNSPECIFIED`) gönderir ve `$n is null` tek başına `could not determine data type` verir (§2l'deki `:activeAfter` notunun kardeşi); `cast(:latitude as double)` ise null'ı `bytea` olarak bağlar (§2av'deki `cast(:x as Boolean)` tuzağı). Bu yüzden `startFiltered`/`located` boolean bayrakları var, sınırlar boşken `0001-01-01`/`9999-12-31`'e, koordinatlar `0.0`'a düşer ve mesafe ifadesi `case when :located = true then … else null end` içinde — bayrak kapalıyken satır başına trigonometri de koşmaz.

**Null parametreler cast edilmek zorunda.** `lower(concat('%', :query, '%'))` içinde `:query` null olduğunda Postgres tipi çıkaramayıp `function lower(bytea) does not exist` hatası veriyor. JPQL'de `cast(:query as string)` şart.

**İstemciden gelen `sort` doğrulanmadan DAO'ya geçirilemez.** Spring Data bilinmeyen bir alan için `InvalidDataAccessApiUsageException` fırlatır ve bu **500**'e düşer — istemci hatasının sunucu hatası gibi görünmesi. Dokümantasyon yazıldıktan sonra Swagger UI'ın `Pageable` için ürettiği `sort=["string"]` örneği bunu prod'da tetikledi; denetimde **dokuz sayfalı uç** korumasız çıktı (şikayetler, bildirimler, uyuşmazlıklar, yetenek havuzu, takip edilen şirketler, admin şirket/kullanıcı listeleri). Hepsi `pageableSanitizer.rejectUnknownSort` ile 400'e çevrildi.

İki katmanlı savunma var: `PageableSortSafetyTest` her `Pageable` alan manager metodunun doğrulama yaptığını derleme sonrası kontrol eder (saf devretmeler gerekçeli muafiyet listesinde), `GlobalExceptionHandler` ise `InvalidDataAccessApiUsageException`'ı yine de 400'e çevirir — gözden kaçan bir yol 500 döndürmesin diye.

**Swagger `Pageable`'ı düz parametre olarak çizmeli.** `@ParameterObject` olmadan springdoc `Pageable`'ı tek bir nesne sanıyor ve `sort` için `["string"]` örneği üretiyor; "Try it out" diyen herkes 500 alıyordu. Sayfalı uçların hepsinde `@ParameterObject @PageableDefault` var.

**Yanıt zarfı `PageDto`:** `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.

### İlan sitemap manifesti — değişmez projection (#166)

Public `GET /jobs` sitemap kaynağı değildir. Offset sayfaları hareketli veri kümesinde kayıt atlayıp
yineler, her sayfa başka availability anını görür ve 50.000 URL için yüzlerce istek genel public-job hız
sınırını tüketir. Sitemap'in ayrı interface'i iki GET ucudur:

- `GET /sitemap/jobs/manifest` → `version`, `createdAt`, `expiresAt`, `entryCount`, `shardSize` ve
  `shards[{number,entryCount}]`;
- `GET /sitemap/jobs/manifests/{version}/shards/{shard}` → `version`, `shard` ve yalnız
  `entries[{id,updatedAt}]`. `updatedAt` authoritative kolonun nullable değeridir; tarih uydurulmaz.

Manifest oluşturma `SitemapManifestService` seam'inin arkasında materialized snapshot'tır. Uygun küme
`status = OPEN`, `is_deleted = false`, dolu ve henüz geçmemiş `end_date_time` koşuluyla tek
`REPEATABLE_READ` transaction'ında sayılır ve `(created_at ASC, id ASC)` ordinal'iyle
`sitemap_manifest_entries` tablosuna yazılır. Kapanma, yeniden açılma, süre dolumu, soft delete veya yeni
ilan aynı version'ı değiştiremez. Entry satırının `jobs` yabancı anahtarı bilerek yoktur: ilan sonradan
silinse bile ilan edilmiş version TTL sonuna kadar aynı union'ı vermek zorundadır. Manifest silinince entry
satırları cascade ile gider. Bu iki tablo denetlenen domain kaydı değil, TTL'li türetilmiş cache'tir;
`created_at` manifest version'ının yayın anıdır, audit alanı değildir. Bu yüzden `BaseEntity` soft-delete
kolonlarını taşımazlar: süresi dolan manifest fiziksel silinir ve bağlı projection da fiziksel cascade olur.
Soft delete, eski snapshot'ları ve potansiyel olarak çok büyük entry kümelerini süresiz tutarak bounded-storage
garantisini bozardı.

Çok örnekli stampede iki kademede kapanır. JVM içindeki adil olmayan kısa kilit aynı instance'ın bağlantı
yığmasını önler; PostgreSQL session advisory lock bütün instance'ları tek üreticide birleştirir. Lock,
`REPEATABLE_READ` snapshot'ı başlamadan önce ayrı ve havuz dışı bağlantıda alınır; transaction içindeki
`pg_advisory_xact_lock` snapshot'ı beklemeden önce sabitleyebileceği için kullanılmaz. Kilidi alan çağrı canlı
manifesti yeniden okur; başka instance üretmişse aynı version'ı döndürür.

`refresh_after = expiresAt - refreshBeforeExpiry` yeni index çağrısına yeterli yayın penceresi bırakır.
Yenileme penceresi TTL'nin en fazla yarısı olabilir; böylece aynı anda en fazla iki canlı snapshot tutulur. Bu
andan sonra yeni version üretilebilir, ama eski version `expiresAt` anına kadar bütün ilan edilmiş shard'ları
200 döndürür. Bilinmeyen, süresi dolmuş veya aralık dışı shard aynı
`SITEMAP_MANIFEST_NOT_FOUND` 404'ünü alır; bozuk UUID/int path 400'dür. Bir version'da önceden istenmiş
olmayan shard sonradan doğamaz, bu yüzden 404 cache zehirlenmesi union'ı eksiltemez.
Shard ucundaki canlı-manifest doğrulaması ile ordinal aralığı aynı salt-okunur `REPEATABLE_READ`
transaction'ındadır; eşzamanlı expiry temizliği bu iki sorgu arasında entry'leri cascade silip 200 boş shard
üretemez.

Bellek sınırı allocation'dan önce doğrulanır: `shardSize` ve `maxShards` ayrı ayrı en fazla 50.000,
`entryCount` en fazla çarpımlarıdır. Her shard ordinal aralığıyla PK indeksinden okunur; manifest oluşturma
ve shard okuma sorgu sayısı veri büyüklüğünden bağımsızdır. `statement_timeout`, `lock_timeout` ve Spring
transaction timeout'u sınırlıdır. Operasyonlar `koala.sitemap.manifest.operations` sayacında
`operation=manifest|shard`, `outcome=hit|created|served|not_found|failed` etiketleriyle genel public-job
telemetrisinden ayrılır. Hem manifest hem shard IP başına varsayılan 30/dk olan ayrı `SITEMAP` hız sınırı
kovasını paylaşır; genel `PUBLIC` ilan bütçesini tüketmez. Yapılandırma `sitemap.manifest.*`, ortam
değişkenleri `SITEMAP_MANIFEST_*` altındadır.

## 2m. İstemci sözleşmesi

**Cihaz kimliği `X-Device-Id` başlığından gelir.** Web BFF `X-Client-Platform: web` gönderdiğinde biçimi `[A-Za-z0-9._:-]{8,128}` olan cihaz kimliği login ve refresh için zorunludur; User-Agent yalnız okunabilir etikettir ve `WEB_BROWSER` eşleşmesine katılmaz. Cookie silinirse yeni tarayıcı cihazı oluşur, eski oturum kullanıcı tarafından oturum yüzeyinden kapatılır. Platform başlığı göndermeyen eski mobil sürümlerde User-Agent geçiş davranışı korunur.

`ClientContext.from(request, clientIpResolver)` güvenilir istemci IP'si, User-Agent, cihaz kimliği ve platformu tek nesnede toplar.

**`GET /legal/account-deletion` yanıtında `blockedReason` reddedilmiş talepte ret gerekçesini taşımaya devam eder.** koala-mobile
yalnız bu alanı okuyor (`account_deletion.dart` `isBlocked`, `account_deletion_view.dart`); #283 ret gerekçesini kendi sütununa
(`rejection_reason`, ayrıca `rejectionReason` alanı) taşıdığında katılımcı görünümü `LegalMapper.participantBlockedReason` ile
`REJECTED` talepte `blockedReason`'ı ret gerekçesiyle doldurmayı sürdürüyor; `rejectedByStaffId` ve `blockers` katılımcıya
gitmez (`AdminListSearchIT.aDeletionRequestShowsItsBlockersAndThenTheStaffMemberWhoRejectedIt`). İstemci `rejectionReason`'a
geçtiğinde bu geri uyumluluk kaldırılabilir; ondan önce `blockedReason`'ın anlamını daraltmak kullanıcıdan gerekçeyi saklar.

**`GET /users/me` istemcinin yönlendirme kararı için gerekeni döner:** `employer`, `emailVerified`, `phoneVerified`, `identityVerified`, `roles` ve `companies[{companyId, companyName, role, approved}]`. Öncesinde `isEmployer` yalnızca tek seferlik kayıt akışında dönüyordu, giriş sonrası kullanıcının işveren mi çalışan mı olduğu öğrenilemiyordu.

**`JobCategory` sabit bir `code` taşır.** İstemci ikon/pin/renk eşlemesini UUID'ye ya da görünen ada değil bu koda bağlar.

**Kodlar İngilizce ve addan bağımsız.** İlk sürüm kodu Türkçe `name`'den türetiyordu (`Fotoğrafçılık` → `FOTOGRAFCILIK`); görünen ad değişince ya da İngilizce dil desteği gelince kod da değişecekti ve mobildeki bütün ikon eşlemeleri kırılacaktı. `27-10` migration'ı bilinen Türkçe kodları İngilizceye çeviriyor. Kural yerinde: kod `[A-Z][A-Z0-9_]{1,63}` biçiminde, ASCII ve görünen addan bağımsızdır; artık `SaveJobCategoryDto` üzerindeki `@Pattern` ile yazma yolunda zorlanıyor.

**Kategori seed'i kaldırıldı, katalog yönetimden kuruluyor.** `27-10`'un yüklediği beş kategori (`PHOTOGRAPHY`, `WAITER`, `PET_CARE`, `DESIGN`, `BARISTA`) `2026/09/06-02-drop-job-category-seed.xml` ile düşürüldü; veritabanı ADR-0008 doğrultusunda kategorisiz geliyor. Kategori ilanın zorunlu alanı olduğu için katalog kurulmadan ilan açılamaz: **boş bir veritabanında ilk iş, `POST /admin/job-categories` ile kategorileri yaratmaktır.** Yüzey `webAPI/JobCategoriesController` altında, `/admin/**` prefix kuralıyla ADMIN'e kısıtlı (§0b).

**Silme changeset'i referans veren satırlara dokunmuyor.** `DELETE`, `jobs`, `job_alerts` ve `job_templates` üzerinde `NOT EXISTS` ile korunuyor. Koşulsuz silinseydi, canlı veritabanında bir ilan bu kategorilerden birine bakıyorken migration yabancı anahtar ihlaliyle düşer ve **Liquibase açılışta patladığı için uygulama hiç ayağa kalkmazdı**. Koşullu silme her iki durumda da doğru: ADR-0008'in teslim ettiği boş veritabanında beşi de gider, veri olan bir veritabanında kullanımdaki kategori kalır ve ilanlar bittiğinde yönetimden silinebilir. `27-10`'un kendisine dokunulmadı — uygulanmış bir changeSet'i değiştirmek mevcut veritabanlarında checksum'ı bozar; taze veritabanı beşini yazıp hemen siliyor, net sonuç aynı.

**Kullanımdaki kategori silinemez ve bu kontrol soft delete'i görmek zorunda.** `DELETE /admin/job-categories/{id}`, üç referans tablosunu tarayıp doluysa 409 (`job.category.in.use`) döner. Kontrol **native** sorgu: `Job`, `JobAlert` ve `JobTemplate` üzerinde `@SQLRestriction("is_deleted = false")` var, dolayısıyla türetilmiş bir `existsByCategoryId` soft delete edilmiş satırı görmez — ama yabancı anahtar o satırı hâlâ tutar ve silme `DataIntegrityViolationException`'a, oradan da 500'e düşerdi. `JobCategoryAdministrationIT` tam bu senaryoyu sabitliyor; sorgu türetilmişe çevrilirse test kırmızıya döner.

**Kod benzersizliği ham veritabanı hatasına bırakılmıyor.** `uk_job_categories_code` ihlali istemciye sızmasın diye `JobCategoryManager` yazmadan önce `existsByCode` / `existsByCodeAndIdNot` ile bakar ve `ConflictException` (409, `job.category.code.already.exists`) fırlatır — `BadgeManager`'daki desenin aynısı.

**ADR-0008 ile bir tutarsızlık kaldı.** ADR metni "yalnız referans verisiyle gelir" listesinde **iş kategorilerini** sayıyor; bu slice o maddeyi geçersiz kılıyor. ADR'ler düzeltilmiyor, üstüne yazılıyor (ADR-0008 de ADR-0001'in seed maddesinin yerine geçmişti) — maddeyi devralacak bir ADR yazılması bekliyor.

**Şirket `website` ve `description` sütunları DTO vaadiyle aynı genişlikte (is-koala/koala-backend#117).** `UpdateCompanyRequestDto` `website`'ı 512, `description`'ı 4000 kabul ediyordu; `companies` sütunları `VARCHAR(255)` idi. Doğrulama geçiyor, `INSERT`/`UPDATE` 500 veriyordu. **Yön (a): sütunlar genişletildi**, çünkü 255 karakterlik bir şirket açıklaması pazaryeri için dar ve DTO zaten 4000 vaat ediyordu; sözleşmeyi küçültmek (b) kırıcı olurdu. `CreateCompanyRequestDto` aynı tavanı taşımıyordu — oluşturma yolu aynı 500'ü üretebilirdi — `@Size` oraya da yazıldı. `CompanyFieldWidthRulesTest` tavanları DTO ve `@Column(length)` üzerinden bağlıyor.

**Aynı sınıfın tarayıcısı `DtoColumnWidthRulesTest` (is-koala/koala-backend#178):** domain klasörü → aynı adlı entity, `@Size(max)` > `@Column(length)` kırmızı. İlk yakalanan `CreateJobApplicationDto.notes` (2000) / `job_applications.notes` (255) idi; sütun 2000'e çekildi — başvuru notu kapak mektubudur, 255 dar.

**Profil yüzeyi `/users/me` üzerinden okunur.** Yazılıp geri okunamayan üç alan kapatıldı: `experiences`, `maskedIban` (`TR33***4444` — tam IBAN dönmüyor) ve yeni `cvUrl`. Ayrıca `jobTitle` (meslek/ünvan) eklendi ve `educations` yanıta girdi.

**Telefon değişikliği artık sessizce yutulmuyor.** `patchCurrentUser` içindeki blok boştu, yalnızca bir `//TODO` vardı — istek 200 dönüyor, numara değişmiyordu. Şimdi e-posta ile aynı desende: `phoneVerificationService.startPhoneVerification` çağrılıyor, numara **doğrulama tamamlanana kadar uygulanmıyor**, yanıttaki `pendingVerifications` içine `PHONE` girdisi düşüyor. Aynı numara gönderilirse hiçbir şey başlatılmaz. `UserPatchRulesTest` bunu sabitliyor.

**`PhoneVerificationManager` bir süre taslak kaldı.** Kod üretiyor, hiçbir yere yazmıyor ve `null` döndürüyordu; `patchCurrentUser` de o null'ı `pendingVerifications` listesine ekliyordu. Yani yukarıdaki paragraf yazıldığı sırada doğru değildi. Artık `EmailVerificationManager` ile aynı desende: `PhoneVerification` kaydı açılıyor, 15 dakikalık altı haneli kod üretiliyor ve SMS kuyruğa alınıyor. **Ama SMS hâlâ teslim edilmiyor** — aşağıdaki tüketici eksiği geçerli.

**`VerificationInfoDto.message` kaldırıldı, çözülmedi (is-koala/koala-backend#46).** Alan iki ayrı sebeple
gösterilemez durumdaydı: taşıdığı değer ham anahtardı (`{email.verification.message}`) ve advice iç içe
alanlara dokunmuyor (§2d), üstelik anahtarın karşılığı `E-posta doğrulama kodunuz: {0}` — hiç doldurulmayan
bir yer tutucu. Issue iki yol öneriyordu: sunucuda **çözüp yer tutucuyu doldurmak** ya da alanı **kaldırmak**.
Doldurmak seçilemezdi, çünkü `{0}`'ın karşılığı doğrulama kodunun kendisi: kodu `PATCH /users/me` yanıtına
yazmak, kanalın sahipliğini kanıtlatmak için var olan doğrulamayı tümüyle geçersiz kılardı — kodu almak için
e-postaya ya da telefona erişmek gerekmezdi. Kaldırmanın bedeli sıfır: `koala-web` alanı tip tanımında
sayıyor ama hiç okumuyor, mobil ise metni kendi yazıyor ve alanı "arayüzde gösterilmemeli" diye
işaretlemişti (is-koala/koala#34). `VerificationMessages`'taki iki sabit silindi ve öksüz kalan iki anahtar
(`email.verification.message`, `phone.verification.message`) üç katalogdan da düşürüldü — duran bir anahtar
sonradan yeniden çağrılabilir (§2ah2'deki gerekçenin aynısı). `verification.phone.code.sms` **düşmedi**: SMS
gövdesini `PhoneVerificationManager` hâlâ ondan kuruyor.

**Yerine `expiresAt` geldi ve tipi `Instant`.** §2h'nin ayrımı burada nettir: doğrulama kodunun ömrü bir
**an**, duvar saati değil. Kod `Instant.now().plus(15, ChronoUnit.MINUTES)` ile üretiliyor ve
`EmailVerification`/`PhoneVerification` entity'lerinde zaten `Instant` olarak, `TIMESTAMPTZ` kolonda duruyor;
`LocalDateTime` seçmek "15 dakika sonra" ifadesini sunucunun saat dilimine bağlar ve istemci geri sayımı üç
saat kaydırırdı. Alan doğrudan entity'den okunuyor, yani sunucunun yazdığı an ile istemcinin okuduğu an aynı
kaynaktan geliyor. Şema değişmedi — kolon zaten vardı, yalnız DTO'ya çıkmıyordu.

**Kazanılan şey mobildeki sayaç.** İstemci geri sayımı **ekranın açıldığı andan** başlatmak zorundaydı ve
kullanıcı ekrana geç girerse sayaç gerçekle kayıyordu; bu yüzden sayaç bilgilendirme amaçlı bırakılmış ve
giriş kapatılmamıştı — tek gerçek kaynak sunucunun 400'üydü. `expiresAt` geldiğine göre
`verification_code_page` sayacı gerçek ana bağlanabilir.

**`ConversationDto` liste için gerekeni taşıyor.** Sohbet listesi son mesaj metnini ve okunmamış sayısını çiziyordu, ikisi de yoktu; istemci satır başına ayrı istek atmak zorunda kalıyordu. `lastMessageBody` `Conversation` üzerine **denormalize edildi** (mesaj gönderilirken yazılıyor), `unreadCount` ise sayfadaki tüm konuşmalar için `countUnreadByConversationIds` ile **tek sorguda** toplanıyor. Yani liste iki sorgu: konuşmalar + sayaçlar.

**İki ayrı ilan DTO'su var.** `JobSummaryDto` liste ve harita için, `JobDetailDto` detay sayfası için. İkisi de `addressPrecision` taşır. Tam adres ve gerçek koordinat yalnız ilan sahibi, etkin ataması (`PENDING_CONFIRMATION`/`CONFIRMED`/`IN_PROGRESS`) bulunan işçi veya `addressPublic=true` olan şirket ilanında döner. Tamamlanan/iptal edilen atamalarda adres yeniden gizlenir ve bireysel ilanlarda `addressPublic` değeri dikkate alınmaz. Diğer çağrılarda sokak, bina, kapı, posta kodu ve yön tarifi temizlenir; koordinat iki ondalığa yuvarlanır. `JobAddressVisibility` bu kararı ve maskelemeyi tek yerde tutar. `GET /jobs/{id}` özet değil detay döner.

**İlan özetinin revizyon zamanı kalıcı kayıttan gelir.** `JobSummaryDto.updatedAt`, `Job`'un `BaseEntity.updatedAt` alanından MapStruct ile doğrudan eşlenir; arama isteğinin saatiyle üretilmez. Alan `Instant` JSON sözleşmesine göre UTC ISO-8601 (`2026-09-09T10:11:12Z`) döner ve eski kayıtlarda `null` olabilir. Bütün özet liste yolları aynı `JobMapper.toJobSummaryDto` yüzeyinden geçtiği için arama, kaydedilen ilanlar, şirket ilanları ve iç içe başvuru/atama özetleri aynı yetkili değeri taşır. Web sitemap'i `lastModified` için bu alanı kullanır; alan adres görünürlüğü kararını veya herkese açık gizlilik sınırını değiştirmez.

**Yenileme ucu süresi dolmuş erişim token'ıyla çalışır.** `/auth/refresh-token` zaten *token süresi dolduğu için* çağrılıyor; iki yerde birden reddediliyordu:
1. `SessionAuthFilter` `permitAll` uçlarda bile başlığı ayrıştırıp `ExpiredJwtException` fırlatıyordu → filtre artık `shouldNotFilter` ile bu ucu atlıyor. Uç kendi kimlik doğrulamasını yapıyor, filtrenin katacağı bir şey yok.
2. `extractSessionId` süresi dolmuş token'da patlıyordu → yenileme yolunda `extractSessionIdAllowingExpired` kullanılıyor; `ExpiredJwtException` yakalanıp claim'ler ondan okunuyor.

**İmza toleransı gevşetilmedi.** JJWT imzayı süre kontrolünden önce doğruluyor, yani sahte imzalı token hâlâ reddediliyor. Süresi dolmuş token burada yalnızca **oturum bulucu**; yetkiyi veren şey `refreshTokenHash` eşleşmesi, oturumun aktif olması ve cihaz eşleşmesi. `ExpiredTokenRefreshTest` bu ayrımı sabitliyor.

**Kimlik doğrulama uçları genel kaynak semantiğini istemci sözleşmesine çevirir.** Genel oturum ve bekleyen kayıt sorguları bulunamayan kaynağı 404 olarak ifade etmeye devam eder; ancak `/auth/refresh-token` için eksik ya da kapalı oturum, istemcinin yeniden giriş yapmasını gerektiren bir kimlik doğrulama reddidir ve 401 `AUTH_SESSION_NOT_FOUND_OR_INACTIVE` döner. `/auth/verify-registration-token` ise bilinmeyen veya süresi dolmuş token'ı aynı düzeltilebilir istek ailesinde tutar: sırasıyla 400 `PENDING_REGISTRATION_NOT_FOUND_BY_TOKEN` ve 400 `AUTH_REGISTRATION_TOKEN_EXPIRED`. Süresi dolmuş bekleyen kayıt yine silinir.

**Auth OpenAPI yanıtları çalışma zamanındaki tüm hata ailelerini sayar.** Kayıt, giriş, yenileme, çıkış ve parola sıfırlama uçlarının uca özel 400/404/413/426/429 kümeleri `OpenApiDocumentIT` ile sabitlenmiştir; 401/403/500 ortak customizer'dan gelir. Yeni bir auth doğrulaması, filtre kapısı ya da oran sınırı eklendiğinde annotation da aynı değişiklikte güncellenir. `ErrorContractIT` özellikle yukarıdaki route-a özgü durum ve hata kodu eşleşmelerini uçtan uca korur.

**Büyük auth gövdesi de standart hata zarfını kullanır.** `RateLimitFilter` 16 KiB sınırını aşan JSON'u controller'a ve oran sınırlayıcıya ulaşmadan 413 ile keser; boş yanıt dönmez, yedi tiplik exception yüzeyini genişletmeden `ValidationException.requestBodyTooLarge` üzerinden `REQUEST_BODY_TOO_LARGE` / `REQUEST_MALFORMED` taşıyan `ErrorResult` üretir. Böylece OpenAPI'deki 413 şeması ile gerçek yanıt ayrışmaz ve istemci hata ayrıştırıcısı bu filtre yolunda özel durum taşımak zorunda kalmaz.

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

`Conversation` = ilan + işveren + işçi. `(job_id, worker_id)` kısmi benzersiz indeksle tekil — aynı ilanda aynı işçiyle ikinci konuşma açılmıyor. `POST /jobs/{jobId}/conversation` bu yüzden **200** döner, 201 değil: ikinci çağrı yeni kayıt açmaz, mevcut olanı verir.

**Üyelik `conversation_participants` tablosunda, `employer`/`worker` sütunlarında değil.** İkili sohbet artık iki katılımcılı bir konuşmadan ibaret; `kind` alanı `DIRECT` ya da `GROUP`. `employer` her iki türde de ilan sahibini gösterir, `worker` yalnızca `DIRECT`'te doludur ve ikili tekilliği o sağlar. `hasParticipant` **yalnızca** katılımcı listesine bakar — bir süre eski sütunlara düşen bir yedek dal vardı ve `addParticipant` onu görüp "zaten üye" sanarak işvereni gruba hiç eklemiyordu. İki kaynak tutmanın bedeli buydu; `29-01` migration'ı mevcut satırları katılımcı tablosuna aktarıyor.

**Konuşma açmak için ödeme emanette olmalı; grup ödeme ile kurulur (#257).** Ürün kuralı: iş arayan ile işveren,
işveren ödemeyi yapmadan mesajlaşamaz — platform dışına çıkma riski tam da o aşamada. Öncesinde `DIRECT` için tek
şart başvurunun varlığıydı (`jobApplicationService.hasApplied`) ve grup atama `CONFIRMED` olur olmaz — yani
`PENDING_COLLECTION`'da — kuruluyordu. Artık iki yüzeyin de kapısı aynı yüklem: **(ilan, işçi) çiftinin son
ataması `CANCELLED`/`REJECTED` değil ve `paymentStatus` `HELD_IN_ESCROW` ya da `RELEASED_TO_WORKER`.** Yüklem tek
yerde, `core/helpers/MessagingAvailability`: `reasonOf(JobAssignment)` üç değerden birini döndürür —
`PAYMENT_REQUIRED` (atama yok ya da ödenmemiş: `null`/`PENDING_COLLECTION`/`FAILED`), `CLOSED` (atama
`CANCELLED`/`REJECTED`, ödeme `REFUNDED` ya da aşağıdaki pencere kapanmış), `AVAILABLE`. `MessagingManager` kapıyı,
`JobAssignmentMapper` istemci ipucunu aynı sınıftan okuyor; kural ikinci bir yere kopyalanmaz.

**`POST /jobs/{jobId}/conversation` ve ikili sohbette `POST /conversations/{id}/messages`:** `PAYMENT_REQUIRED` →
**403 `MESSAGING_PAYMENT_REQUIRED`** ("Mesajlaşma, işveren ödemeyi tamamladığında açılır."), `CLOSED` → **409
`MESSAGING_WINDOW_CLOSED`** (pencerenin anahtarıyla aynı; iptal edilen sohbet de "kapalı"dır, ayrı bir kod
icat edilmedi). Sıra: taraf kontrolü → ödeme kapısı → engel kontrolü; ödenmemiş bir çiftte engel hiç sorulmaz.
İlanla hiçbir ilişkisi olmayan kullanıcı ödemeden önce de sonra da **aynı** 404 `MESSAGING_NOT_A_PARTICIPANT`
alır (§2d2'de "ilan herkese açık, gizlenecek varlık yok" gerekçesiyle olduğu gibi bırakılan dal);
`MessagingPaymentGateIT.anOutsiderStillGetsTheSameNotFoundBeforeAndAfterPayment` iki zarfı karşılaştırıyor.
`messaging.no.relationship.with.job` anahtarı ve `JobApplicationService.hasApplied` düştü — tek çağıranı bu kapıydı.

**`ConversationDto.closed` ikili sohbette artık atama durumunu da taşır.** `DIRECT` için `closed = reasonOf(son
atama) != AVAILABLE`, `GROUP` için yalnız pencere. Hesap **okuma anında**, `closedAt` gibi bir sütun **yok**:
iptal/ret/iade üç ayrı yerden yazılıyor (`CancellationManager` iki yol, `JobAssignmentManager.answerToAssignment`,
`PayoutManager.refundEmployer`) ve bir sütun her birinin olayını dinlemeye bağlı olurdu — dinleyicinin kaçırdığı
yol sohbeti sessizce açık bırakırdı (#77'nin dersi). Okuma anında hesap yeni bir iptal yolu eklendiğinde de
kendiliğinden doğru; bedeli liste başına **tek** toplu sorgu (`JobAssignmentService.findLatestByJobsAndWorkers`,
sayfadaki ilan ve işçi id kümeleriyle) — satır başına sorgu yok, çünkü sayfa zaten `job` ve `worker`'ı yüklüyor
ve atamanın aynı entity'leri persistence context'ten çözülüyor.

**"Son atama" `createdAt`'e göre seçilir, `assignmentDateTime`'a göre değil.** Aynı (ilan, işçi) çiftinin birden
çok ataması olabilir — reddedilen bir teklifin ardından ikinci bir teklif — ve kapı **en yenisine** bakar: eski
`REJECTED` + yeni ödenmiş → `AVAILABLE`, eski ödenmiş + yeni `REJECTED` → `CLOSED`. Ölçüt `assignment_date_time`
olamazdı çünkü **üretim kodu o sütunu hiç yazmıyor** (kabul yolu yalnız `status`/`paymentStatus` değiştiriyor);
ona göre sıralamak her satırı `null` görüp rastgele birini seçerdi. Tanım **tek**: `JobAssignmentManager.OLDEST_FIRST`
(`createdAt`, null'lar en başa — damgasız satır damgalı bir satırı hiçbir zaman geçemez) hem tekil
`findLatestByJobAndWorker` hem toplu `findLatestByJobsAndWorkers` tarafından kullanılıyor; iki DAO sorgusu
(`findAllByJobIdAndWorkerId`, `findAllByJobIdInAndWorkerIdIn`) grafiksiz ve `CollectionQueryWithoutGraphTest`
allowlist'inde — kapı yalnız durum ve ödeme alanlarını okuyor. `LatestAssignmentLookupTest` iki tanımın aynı satırı
seçtiğini, `MessagingPaymentGateIT.theLatestAssignmentOfThePairDecides…` iki senaryoyu HTTP üzerinden sabitliyor. Aynı sebeple **ödeme kapısından önce açılmış
ikili sohbetler için migration yazılmadı**: arkalarında ödenmiş atama olmadığı için `closed=true` ve gönderim
403 dönüyor; ödeme gelirse kendiliğinden açılıyorlar
(`aDirectConversationLeftOverFromBeforeThePaymentGateIsReadOnly`). Test ortamı, veri kaybı yok.

**Grup kendiliğinden kurulur: grup = ilanın ödenmiş ekibi + işveren tarafı.** `PaymentManager.settle`
`HELD_IN_ESCROW` geçişinde `AssignmentPaidEvent(jobId, workerId, assignmentId)` yayımlar; `CrewConversationListener`
onu yakalayıp `joinJobCrew` çağırır: ilanın grubu yoksa açılır ve işçi eklenir, varsa yalnız eklenir. Aynı ilanda
ikinci/üçüncü çalışanın ödemesi geldikçe gruba katılır; ödemesi gelmeyen işçi ne grupta ne ikili sohbette. İşveren
elle grup kurmaz, kimseyi elle eklemez. **İşveren tarafı = ilan sahibi + şirket yetkilileri:** şirket ilanında grup
kurulurken o anki bütün `company_contacts` satırları katılımcı olur (`CompanyContactService.getContactsOfCompany`).
Sonradan eklenen yetkili `CompanyContactAddedEvent` ile şirketin **penceresi açık** gruplarına katılır, çıkarılan
yetkili `CompanyContactRemovedEvent` ile hepsinden çıkar (`joinCompanyCrews`/`leaveCompanyCrews`). İki olay
`CompanyContactManager`'dan yayımlanıyor: `CompanyContactManager → MessagingService → CompanyContactService` döngü
olurdu. **İlan sahibi gruptan hiçbir olayla çıkarılmaz** — `leaveJobCrew` ve `leaveCompanyCrews` `employer`'ı
atlıyor; şirketten çıkarılsa bile ilan onun.

**Kapanış: atama `CANCELLED`/`REJECTED` ya da ödeme `REFUNDED` olunca işçi gruptan çıkar.** Üç yazma yolu da
`AssignmentClosedEvent(jobId, workerId, assignmentId)` yayımlıyor (`REJECTED` hiç ödenmemiş bir atamadır ve grupta
zaten yoktur; olay yine de yayımlanıyor, `leaveJobCrew` idempotent). Katılımcı satırı soft delete edilir
(`orphanRemoval` + `@SQLDelete`), geçmiş okunabilir kalır; ikili sohbet yukarıdaki okuma-anı hesabıyla
`closed=true` olur. **7 günlük pencere aynen duruyor** (aşağıda) ve `endDateTime` olmayan ilanda hiç kapanmıyor.
`2026/09/13-01` migration'ı mevcut gruplardan ödenmemiş işçileri düşürüyor ve bitmemiş şirket ilanlarının
gruplarına o günkü yetkilileri ekliyor — ikisi de idempotent `NOT EXISTS`.

**Atama tarafı mesajlaşmayı tanımıyor, ve olaylar commit'ten sonra işleniyor.** `JobAssignmentManager`
`MessagingService`'e bağlansaydı döngü çıkardı — eskiden `JobAssignmentManager → MessagingService →
JobApplicationService → JobAssignmentService` (denendi, görüldü), bugün `MessagingManager` doğrudan
`JobAssignmentService`'e bağlı olduğu için daha da kısa. Ters yön yok; `PaymentManager`, `CancellationManager`,
`PayoutManager` ve `CompanyContactManager` yalnız olay yayımlıyor. Dört olay da
`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` ile dinleniyor ve dört
`MessagingService` metodu `@Transactional(propagation = REQUIRES_NEW)`. Neden eski `AssignmentConfirmedEvent`'in
düz `@EventListener` kalıbı değil: o kalıpta dinleyici **yayımlayanın transaction'ının içinde** koşuyordu ve
`joinJobCrew`'un `@Transactional`'ı ona katılıyordu — içeride bir `RuntimeException` çıkarsa Spring
transaction'ı rollback-only işaretler ve dinleyicideki `catch` bunu **geri alamaz**: commit
`UnexpectedRollbackException` ile düşer. Eski belgede yazan "dinleyici hata alırsa yalnızca uyarı loglanır,
atama düşmez" cümlesi bu yüzden doğru değildi. Yeni tetikleyici **para**: sağlayıcının tahsil ettiği bir
ödemenin kaydı bir sohbet grubunun kurulamamasına bağlanamaz. Bedeli yazılı olsun: grup kurulumu commit'ten
sonra kendi transaction'ında koşuyor ve orada düşerse **yalnız `WARN` kalır** (yığın iziyle), ödeme kaydı
durur. `REQUIRES_NEW` zorunlu — `AFTER_COMMIT` fazında yayımlayanın transaction'ı tamamlanmıştır ve
`REQUIRED` ile katılan bir yazma **sessizce commit edilmez**. `CrewConversationListenerTest` dört dinleyicinin
fazını ve dört metodun yayılımını sabitliyor; `fallbackExecution` transaction'sız bir çağırandan gelen olayın
düşürülmemesi için.

**Grupta gönderim de gönderenin atamasını yeniden sorar.** Üyelik olaylarla düşüyor (aşağıda) ama olay
dinleyicisi commit'ten sonra kendi transaction'ında koşuyor ve düşerse **yalnız `WARN` kalıyor** — yani iadesi
yapılmış bir işçi grupta üye kalabilir. Üyelik tek başına yazma hakkı vermesin diye `POST /conversations/{id}/messages`
`GROUP` dalında gönderen işveren tarafı değilse (ilan sahibi ya da şirket yetkilisi değilse) ikili sohbetle **aynı
yüklem** koşuyor: `(ilan, gönderen)` çiftinin son ataması `MessagingAvailability.reasonOf` ile okunuyor, `CLOSED` →
409 `MESSAGING_WINDOW_CLOSED`, `PAYMENT_REQUIRED` → 403 `MESSAGING_PAYMENT_REQUIRED`
(`MessagingManager.assertCrewMemberMayMessage`). Ek maliyet tek sorgu (`findLatestByJobAndWorker`), gönderim
başına; işveren tarafı için hiç sorulmuyor. Okuma serbest kalıyor — geçmiş görünür. `MessagingPaymentGateIT.
aRefundedWorkerLeftInTheCrewByAFailedListenerStillCannotWriteThere` dinleyicinin başarısız olduğu hâli işçiyi
JDBC ile gruba geri yazarak taklit ediyor.

**Grupta engelleme uygulanmaz.** İkili sohbette iki yönlü engel yerinde duruyor, ama grup aynı işte çalışan insanların iş kanalı — orada engel iletişimi keserdi. Ürün kararı.

**Yeni mesaj push'u: alıcılar = katılımcılar − gönderen, gövde mesajı taşımaz (#267).** Mesaj bir süre yalnız
STOMP ile duyuruluyordu; uygulama arka planda ya da kapalıyken hiçbir şey gitmiyordu. Artık `MessagingManager.sendMessage`
kayıttan sonra `activityNotificationService.messageSent(saved)` çağırır ve yönlendirme kararı §2s'deki kurala uygun
olarak `ActivityNotificationManager`'da: `DIRECT`'te karşı taraf, `GROUP`'ta gönderen dışındaki bütün katılımcılar.
Alıcı kümesi ayrıca süzülmez — ödeme kapısı ve engel zaten **gönderimi** reddediyor (ikili sohbette iki yönlü engel
403 verir, mesaj hiç yazılmaz), grupta engel uygulanmadığı için orada da uygulanmaz; katılımcı listesi kimin
duyacağını belirleyen tek kaynaktır. Hesabı silinmiş alıcıyı `sendPushToUser`'ın kendi `isActiveUser` kontrolü eler.
**Kategori `MESSAGES`, `kind = ALERT`:** kapatılabilir, varsayılanı açık, ticari değil. Kullanıcı
`PUT /users/me/notification-preferences` ile `MESSAGES/PUSH`'u kapatırsa `NotificationManager` kapısı satırı hiç
açmaz (§2at'nin tek kapısı, ayrı bir kontrol yazılmadı). Tercih görünümünde `MESSAGES/EMAIL` ve `MESSAGES/IN_APP`
anahtarları da çıkar ama bugün hiçbir şey göndermez — `JOB_ALERTS` ile aynı durum.
**Payload:** `title` = gönderenin adı soyadı (ikisi de boşsa "Koala"), `body` = "Yeni mesaj"/"New message"
(`activity.message.received`), `data` = `type=MESSAGE`, `conversationId`, `messageId` (+ sunucunun her push'a
eklediği `notificationId`). **Mesaj metni push'a girmez** — bilinçli: FCM gövdesi kilit ekranında, bildirim
geçmişinde ve Google/Apple sunucularında düz metin durur; iş ilişkisi içindeki yazışma KVKK'da kişisel veri.
"İlk 80 karakter" seçeneği bu yüzden elendi; istemci `conversationId` ile sohbeti açıp metni REST'ten alır.
Gönderen sistem kullanıcısı değil, **insan gönderen** (`isSystemPush=false`): `notifications.sender_id` mesajı
yazan kişi olur ve hesabını sildiğinde `PostgresAccountAnonymizer` `sender_id` üzerinden başlığı (adını) da temizler.
**Outbox aynı transaction:** push satırı `Notification` outbox'ına (§2s "Notification satırı publish outbox'ıdır")
mesajla aynı transaction'da yazılır; mesaj commit olmadan push yok, push hazırlığı düşerse mesaj da düşer.
`ActivityNotificationManager`'ın diğer dallarındaki `try/catch` burada bilerek yok: `sendPushToUser`
`@Transactional` ve çağıranın transaction'ına katılıyor, içinden çıkan `RuntimeException` transaction'ı
rollback-only işaretler — yakalamak mesajı kurtarmaz, yalnız yanıltıcı bir log bırakır (§2o'daki
`UnexpectedRollbackException` dersi). Grup fan-out'u önce `preparePushRecipients` ile alıcı kilitlerini sıralı
alır (§2s'deki kilit protokolü), sonra alıcı başına `sendPushToUser`. `ConversationMessagingIT` üç kuralı HTTP
üzerinden sabitliyor (tek alıcı ve gönderene yok; tercih kapalıysa satır yok ama mesaj var; grupta N−1),
`ActivityNotificationRulesTest` payload ve kanal seçimini.

**`GET /conversations/{id}/messages?after=<messageId>` — kopukluk sonrası artımlı çekim (#267).** `after`
verilince yalnız o mesajdan sonra gönderilenler döner, sıra `sentAt` **artan** (eşitlikte `id`), aynı `page`/`size`
ile (`MessageDao.findAllAfter`). Sıra sabit olduğu için bu kipte `sort` **verilemez**: `rejectUnknownSort` boş
kümeyle çağrılır, herhangi bir `sort` 400 `PAGE_SORT_FIELD_NOT_ALLOWED` alır — sessizce yok saymak yerine
(`PageableSortSafetyTest` her sayfalı metodun bu kapıdan geçmesini istiyor). Çapa `findByIdAndConversationId` ile
**bu konuşmada** aranır: başka konuşmanın mesajı ile hiç var olmayan id **aynı** 400
`MESSAGING_AFTER_UNKNOWN` alır (§2d2; ayrım yalnız `log.warn`'da). Boş sayfa yerine 400 seçildi çünkü boş sayfa
"yeni mesaj yok" ile "çapan geçersiz" hâllerini birleştirirdi — moderasyonla silinmiş bir çapayı tutan istemci
sonsuza dek yakalandığını sanırdı; 400 ona listeyi baştan çekmesini söyler. Taraf olmayan `after` verse de
verilmese de aynı 404'ü alır: taraf kontrolü çapadan önce.

**`GET /conversations/{id}` — listedeki satırın aynısı.** Bildirimden gelen istemci listeyi çekip aramak
zorundaydı. Uç `ConversationDto`'yu liste ile aynı hesapla döner (`unreadCounts`, `closedFlags`, `membersOf` tek
elemanlı listeyle çağrılır; ikinci bir hesap yolu yok). Taraf olmayan `messaging.conversation.not.found` ile aynı
404 (`ExistenceConcealmentIT`).

**Grupta "kim gördü" katılımcı başına damgayla okunur, mesaj başına listeyle değil (#281).** `messages.read_at`
tek sütun: ikili sohbette "karşı taraf okudu" demek, grupta ilk okuyan herkes adına dolduruyordu ve ikinci
okuyanın izi hiç kalmıyordu. `conversation_participants.last_read_at` (`2026/09/14-02`) bunu kişiye bağlar:
`POST /conversations/{id}/read` çağıranın katılımcı satırını `now()`'a çeker (`Conversation.stampRead`), ikili
sohbetteki `read_at` yazımı ve `unreadCount` hesabı **olduğu gibi** durur (`markConversationRead` her iki türde
de koşar; grupta `unreadCount` hâlâ paylaşılan `read_at`'e bakar — kişi bazlı sayım ayrı iş). Migration
ikili sohbetlerin damgasını `read_at`'lerden geri doldurur (katılımcının damgası = karşı tarafın mesajlarındaki
en büyük `read_at`); grupta hangi okuyanın yazdığı bilinemediği için orada `null` kalır. Backfill'in `rollback`'i
**yalnız kendi yazdığını** siler: aynı `MAX(read_at)` hesabını yeniden kurup damgası hâlâ o değere eşit satırları
`null` yapar — uygulamanın sonradan `now()` ile ilerlettiği damga eşit olamayacağı için korunur. STOMP `READ` olayı
`userId` = okuyan ve yeni alan `lastReadAt` (satıra yazılan damganın aynısı) taşır; ayrı bir `readerId` alanı
açılmadı, `userId` zaten her olay tipinde "olayın öznesi". Olay `marked > 0` **ya da** damga son mesajı yeni
geçtiyse yayımlanır (`lastMessageAt > önceki damga`) — grupta ikinci okuyanın `marked` değeri sıfırdır, olay
yine gider; aynı kişinin arka arkaya ikinci çağrısı sessizdir (`MessagingAuthorizationTest` üç hâli sabitliyor).
**`readBy` yok:** istemci "görüldü"yü `participants[].lastReadAt >= message.sentAt` ile hesaplar; mesaj başına
okuyan listesi N+1 olurdu.

**`ConversationDto.participants[]` artık `ConversationParticipantDto` — `SafeUserDto` değil.** Alanlar `id`
(kullanıcı id'si, `MessageDto.senderId` ile eşleşir), `firstName`, `lastName`, `profilePictureUrl`, `role`
(`EMPLOYER` | `COMPANY_CONTACT` | `WORKER`), `joinedAt`, `lastReadAt`, `left`. **Ayrılanlar listede kalır,
`left=true`** — eski mesajların adı çözülsün diye; katılımcı entity'si `@SQLRestriction("is_deleted = false")`
taşıdığı için bunlar JPA koleksiyonundan gelemez, `ConversationDao.findParticipantRows` **native** bir okuma
modeli: sayfadaki bütün konuşmalar için **tek** sorgu, `users` ve `media` join'ini kendisi yapar (silinmiş
hesabın satırı anonimleştirilmiş adıyla — "Anonim Kullanıcı" — döner, `@SQLRestriction` devre dışı), aynı kişi
çıkıp yeniden girdiyse `distinct on` aktif satırı seçer. Önceki `membersOf` her satırda lazy `participants`
yüklüyordu — liste sayfasında satır başına bir sorgu; şimdi sayfa başına bir. **Rol sütun değil, okuma anında
hesap:** ilan sahibi → `EMPLOYER`; `DIRECT`'te diğer taraf → `WORKER`; grupta `(ilan, kullanıcı)` için
`job_assignments` satırı varsa `WORKER`, yoksa `COMPANY_CONTACT` — grup tam bu iki yoldan kuruluyor (ödenmiş
atama / `company_contacts`), sütun eklemek eski binary'nin `INSERT`'ini kırardı ve şirketten sonradan çıkarılan
yetkilinin rolü de doğru kalır. Liste **yalnız taraflara** görünür (`loadParticipating` önce koşar); taraf
olmayan `POST /read` de aynı 404'ü alır ve damga bırakmaz (`ExistenceConcealmentIT`). `MessageDto` `senderName`
(ad soyad, ikisi de boşsa `null`; `DisplayNames.of`, push başlığıyla aynı yardımcı) ve `senderProfilePictureUrl`
taşır — **gönderen de aynı katılımcı okuma modelinden çözülür, entity'den değil.** İlk tur `sender` +
`sender.profilePicture`'ı mesaj sayfasının grafiğine koymuştu; `Message.sender` `nullable = false` ve `User`
`@SQLRestriction` taşıdığı için Hibernate 7 join'i INNER üretiyor (§2y3'teki #231 tuzağı) ve hesabını silmiş
gönderenin mesajı sayfadan **düşüyordu** — `countUnreadByConversationIds` onu saymaya devam ederken (rozet 3,
sayfa 2). İki sayfa sorgusu artık **grafsız** (`CollectionQueryWithoutGraphTest` allowlist'inde gerekçesiyle),
mapper `sender`'dan yalnız `getId()` okur (proxy, sorgusuz) ve ad/fotoğraf `findParticipantRows`'un o konuşma
için döndürdüğü satırlardan gelir — sayfa başına **bir** ek sorgu, silinmiş gönderen "Anonim Kullanıcı" ve
avatar `null` ile sayfada kalır, katılımcı listesiyle aynı kaynak. Gönderim de aynı yoldan: `sendMessage`
`MessageDto` döner (REST yanıtı ve STOMP `MESSAGE` olayı aynı nesne). `ConversationMessagingIT` altı kuralı HTTP üzerinden sabitliyor: grup damgası kişi bazlı ve
ikinci okuyanın damgası kendi satırına, ikili `read_at` + damga, ayrılan `left=true` ve mesajının adı çözülüyor
(ayrılan konuşmayı artık okuyamaz), silinmiş gönderenin mesajı anonim adıyla sayfada ve `unreadCount` ile
`totalElements` eşit (`after` kipinde de), gönderen adı/fotoğrafı, yabancının 404'ü damga bırakmıyor.

**Mesajlaşmanın bir penceresi var.** İş bittikten `messaging.grace-days-after-job` (7) gün sonra sohbet **salt okunur** olur: geçmiş okunur, yeni mesaj gönderilemez (409). Amaç ilişkinin süresiz açık kalıp platform dışına taşınmasını önlemek. `endDateTime` olmayan ilanlarda pencere hiç kapanmaz. `ConversationDto.closed` bunu istemciye söyler; karşılaştırma duvar saati olduğu için `messaging.zone-id` üzerinden yapılır. Hesap `MessagingAvailability.isWindowClosed` içinde, `MessagingManager`'da değil — mapper da aynı pencereyi okuyor.

**İstemci ipucu atama yanıtlarında.** `MyAssignmentDto` (`GET /users/me/assignments`, `GET /users/me/work`
içindeki `assignment`, `GET /job-assignments/{id}`, `GET /jobs/{jobId}/assignments`, `POST /job-assignments/{id}/respond`
ve `/complete` — `JobAssignmentDto` #282 ile silindi, respond de aynı sözleşmeyi döndürüyor, §2ba) ve
`ShortJobAssignmentDto` (`JobApplicationDetailDto.assignment` —
`POST /job-applications/{id}/respond` ve `/withdraw`) `messagingAvailable` (boolean) ve
`messagingAvailableReason` (`PAYMENT_REQUIRED` | `CLOSED` | `AVAILABLE`) taşıyor; MapStruct `uses =
MessagingAvailability.class` ile `@Named` niteleyicilerden dolduruyor, ek sorgu yok. İstemci düğmeyi ödeme
öncesi bununla gizler. `AdminAssignmentDto` bilerek dışarıda — personel mesajlaşmıyor.

**Yalnızca taraflar okuyup yazabiliyor** — taraf olmayan, var olmayan bir konuşmanın **yanıtının aynısını** alıyor: 404 ve aynı `messaging.conversation.not.found` anahtarı (§2d2). Durumu eşitlemek yetmez, `errorCode` de aynı olmak zorunda; ayrım yalnız sunucu logunda.

**Engelleme iki yönlü çalışır.** `UserBlock` tek yönlü kayıt ama `assertNotBlocked` her iki yönü de kontrol ediyor: A B'yi engellediyse B de A'ya yazamıyor. Engelleme idempotent, kendini engellemek reddediliyor.

**Değerlendirme yalnızca tamamlanmış işte.** `AssignmentStatus.COMPLETED` şartı var, her taraf bir kez yazabiliyor (`(job_assignment_id, author_id)` kısmi benzersiz indeks), puan 1-5 arası — hem uygulama katmanında hem `CHECK` kısıtıyla.

**`RatingSummary` biriktirir, yeniden hesaplamaz.** `reviewCount` ve `ratingSum` artırılıp ortalama türetiliyor; her profil görüntülemesinde toplama sorgusu atılmıyor. Ortalama `NUMERIC(3,2)`, `HALF_UP`.

**Anonim değerlendirme kararı ve gizlemenin mapper'da tek yerde oluşu §2am'de** ("Anonim değerlendirme — karar tersine döndü" ve devamı); aşağıdaki iki paragraf onun üzerine kurulu.

**"Değerlendirdim mi" bilgisinin doğruluk kaynağı sunucu (#293).** İstemciler bunu cihazda (SharedPreferences)
tutup 409'a kadar düğmeyi yeniden gösteriyordu. `MyAssignmentDto.myReview {id, rating, comment, createdAt}`
görüntüleyenin **kendi** yazdığı değerlendirme (yoksa null), `counterpartyReviewed` karşı tarafın yazıp yazmadığı —
yalnız varlık, içerik ve yazar yok. Karşı taraf işçi merkezli tanımlanır: yazar işçiyse işçi tarafı, değilse
işveren tarafı; görüntüleyen şirketin başka bir yetkilisiyse aynı taraftaki meslektaşının değerlendirmesi ne
`myReview` ne `counterpartyReviewed` sayılır. Liste yolunda sayfa başına **tek** ek sorgu
(`ReviewDao.findAllByJobAssignmentIdIn`, `JobAssignmentManager.linkedRecordsOf`; `author.id`/`jobAssignment.id`
proxy kimliği, `CollectionQueryWithoutGraphTest` allowlist'inde). `JobAssignmentManager → ReviewDao` §2ae
muafiyeti: `ReviewManager` zaten `JobAssignmentService`'e bağlı, servis üzerinden gitmek döngü olurdu.

**`ReviewDto.jobAssignmentId` yalnız yazarın aldığı POST yanıtında dolu, herkese açık listede null.** İki
profilde aynı atama kimliğini görmek anonim yazarı açığa çıkarırdı: A'nın profilindeki anonim değerlendirme ile
B'nin profilindeki adlı değerlendirme aynı `jobAssignmentId`'yi taşısaydı üçüncü kişi anonim yazarın B olduğunu
çıkarırdı. `ReviewMapper.toOwnReviewDto` (`toOwnNamedReviewDto`/`toOwnAnonymousReviewDto`, `jobAssignment.id`'den)
kimliği ekler; `toReviewDto`/`toReviewDtoList` `ignore` ile boş bırakır.

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

**İptal iadesi atamanın "son iadesi" değildir.** İptal akışı ürettiği `Refund` kaydını aynı transaction içinde `cancellations.refund_id` ile doğrudan bağlar; cevap da yalnız bu bağlantının durumunu taşır. Controller iptalden sonra `refunds` tablosuna ikinci bir sorgu atmaz. Böylece sonradan açılan bir uyuşmazlık ya da personel iadesi, geçmiş iptalin yaşam döngüsü diye gösterilemez. Migration öncesi iptallerde güvenilir bir nedensellik anahtarı bulunmadığı için `refund_id` bilinçli olarak nullable'dır ve eski kaydın `refundStatus`/`refundCompletedAt` alanları `null` dönebilir; tutar veya zamana bakarak tahmin yapılmaz.

**İptal para alanları JSON'da exact decimal string'dir.** `CancellationPreviewDto` ve `CancellationDto` içindeki `feeRate`, `feeAmount` ve `refundAmount` `BigDecimal.toPlainString()` eşdeğeriyle çıkar; bilimsel gösterim kullanılmaz ve veritabanındaki ölçek korunur. Hesap ve kalıcılık katmanında para yine `BigDecimal`'dır (§2e).

**Açık uyuşmazlık emaneti dondurur.** `PayoutManager.releaseToWorker` artık `disputeDao.existsByJobAssignmentIdAndStatusIn(..., ACTIVE)` kontrolü yapıyor; açık uyuşmazlık varsa para işçiye çıkmaz. Bu kontrol `PaymentStateMachine`'in içine konmadı çünkü state machine para durumunu bilir, uyuşmazlığı bilmez.

Uyuşmazlık yalnızca **emanette para varken** açılabilir, atama başına aynı anda bir tane açık kalabilir (`uk_disputes_assignment_open` kısmi indeksi `status <> 'RESOLVED'` ile). Çözüm admin'de: `RELEASE_TO_WORKER` parayı çıkarır, `REFUND_EMPLOYER` iade eder, `DISMISSED` emanete dokunmaz.

**Uyuşmazlık çözümü parayı hareket ettiriyorsa finansal izin de ister.** `dispute.resolve` `NORMAL` hassasiyetinde ve tek başına `RELEASE_TO_WORKER`/`REFUND_EMPLOYER` çalıştırabiliyordu: `payout.release` ve `payment.refund`'ın **hiçbirine** sahip olmayan bir uyuşmazlık görevlisi emanetten gerçek para çıkarabiliyordu — [ADR-0014](docs/adr/0014-keep-staff-identity-in-koala.md) §Rol grafiği'ndeki görev ayrılığı kuralının tam ihlali, üstelik iki finansal iznin 10 yıllık denetim izini de atlanarak. `DisputeManager` artık parayı `PayoutService` yerine `StaffPayoutService` facade'ından geçiriyor ve kararın **yönüne göre** izin istiyor: `RELEASE_TO_WORKER` → `payout.release`, `REFUND_EMPLOYER` → `payment.refund`, `DISMISSED` → hiçbiri.

**Eşleme `DisputeResolution` enum'unda (`getMoneyPermission`), controller anotasyonunda değil.** `@RequiresPermission` statiktir ve `containsAll` ile çalışır; gövdeye bağlı bir kural oraya yazılamaz. Yazılabilseydi bile üç sonucu birden kapsayan tek bir liste `DISMISSED` için de finansal izin isterdi ve güven dalını zorunlu olarak finans dalına taşırdı — kaçınılmak istenen şeyin aynısı.

**Para hareketi çözümden ayrılmadı, bilinçli.** Issue ikinci yol olarak "çözüm yalnız durumu değiştirsin, para ayrı bir uçtan hareket etsin" öneriyordu. Ayırmak koala-admin#6'nın **inmiş** sözleşmesini bozardı: o ekran tek çağrıda hem çözümü hem para hareketini yapıyor ve sonucu onay kutusunda yazıyor. Ayrılsaydı panel iki çağrılık bir akışa geçmek zorunda kalır ve arada kalan bir uyuşmazlık "çözülmüş ama parası kıpırdamamış" durumda bırakılabilirdi — bugün var olmayan bir yarım hâl. İzin istemek sözleşmeyi hiç değiştirmiyor: aynı uç, aynı gövde, aynı sonuç; yalnız finansal izni olmayan çağıran 403 alıyor. **Panelde değişmesi gereken tek şey** 403'ün gösterimi: uyuşmazlık görevlisi `DISMISSED` dışındaki iki düğmeyi kullanamıyor.

**Yetkilendirme durum kontrolünden önce.** Finansal izin kontrolü `ALREADY_RESOLVED` 409'undan **önce** çalışıyor (§2ar3'teki kuralın aynısı): izni olmayan çağıran uyuşmazlığın zaten çözülmüş olup olmadığını hata kodundan öğrenemiyor.

**Çözüm aktörü kendi kimlik aggregate'ine damgalanıyor.** `securityService.getAuthenticatedUser()` bir `StaffPrincipal` gördüğünde `{user.not.authenticated}` fırlatıyordu; yani personel kimliğiyle gelen bir çözüm bu tek satır yüzünden **404 alıyordu** ve uç pratikte yalnız marketplace `ROLE_ADMIN` ile çalışıyordu. Artık köprü yolu `resolved_by_id` User ilişkisini, personel yolu `resolved_by_staff_id` StaffMember ilişkisini yazar ve diğerini temizler. StaffMember ilişkisi `StaffAccountService` sınırından çözülür; uyuşmazlık aggregate'i personel deposuna doğrudan uzanmaz. Aynı tür alanlar şikayet kararında da geçerlidir ve şikayet satırı karar transaction'ı boyunca pessimistic write lock ile tutulur; iki moderatör aynı açık kaydı başarıyla sonuçlandıramaz.

**Para sonucunun denetim kaydı da finansal kararın kendisini adlandırır.** Controller'ın statik `dispute.resolve` anotasyonu isteği önce kapıda korur; `DisputeManager`, sonuç para hareketi doğuruyorsa bekleyen kaydı `StaffAuditContext.reclassify` ile `payout.release` / `dispute.resolve.release` veya `payment.refund` / `dispute.resolve.refund` olarak yeniden sınıflandırır. Böylece hem başarılı hem reddedilen finansal denemeler `FINANCIAL` hassasiyetinde 10 yıl kalır; finansal izni olmayan çağrı `allowed=false` olur. `DISMISSED` yeniden sınıflandırılmaz ve normal uyuşmazlık kararı olarak kalır.

**`payment.read`'in arkasında artık bir uç var: `GET /admin/job-assignments/{assignmentId}`.** Uyuşmazlık ve emanet ekranlarının ihtiyaç duyduğu okumayı veriyor — taraflar, ilan ve şirket adı, atama ve ödeme durumu, fiyat dökümü. Öncesinde panel emanetteki tutarı hiçbir yerden okuyamıyordu (`DisputeDto` tutar taşımıyor) ve geçici bir sözleşme uydurmak zorunda kalmıştı. Uç `payment.read` istiyor, `finance.read` değil: ikisi de bugün `FINANCE_ANALYST`'te duruyor ama `payment.read` tek bir ödemenin/atamanın okunmasını adlandırıyor, `finance.read` toplu finans raporlamasına ayrıldı. **Sonucu görev ayrılığının kendisi:** `DISPUTE_HANDLER` uyuşmazlığı görür ama emanetteki tutarı göremez; tutarı görmek finans dalına aittir. Panel bu ucu finans rolüyle ya da `OWNER` ile çağırmalı, diğer rollerde tutar alanını gizlemeli. Uç `FINANCIAL` hassasiyetli bir tekil okuma olduğu için denetim kaydına giriyor ve satırı 10 yıl saklanıyor (§2av, kural 3 ve 4).

**`finance.read`'in arkasında `GET /admin/finance/summary` var.** Tekil atama değil toplu rapor: `from` dahil / `to` hariç aralıktaki `SUCCEEDED` ödemelerin işveren hacmi, Koala hizmet bedeli ve çalışan hak edişi; aynı aralıkta `COMPLETED` hakediş toplamı; `PENDING`+`PROCESSING` hakedişler dönem filtresi olmadan anlık yükümlülük. İkisi de yoksa tüm geçmiş → şimdi (UTC). Tutarlar ondalık metin. `DISPUTE_HANDLER` 403. `AdminPermissionMappingIT` her `StaffPermission` sabitinin en az bir uca bağlı olduğunu da tarıyor — izin tohumda ölü kalamaz.

**`POST /admin/job-assignments/{id}/release` artık gerekçe alabiliyor.** Gövde **isteğe bağlı** (`ReleaseRequestDto.reason`, en fazla 512 karakter), yani koala-admin#6'nın gövdesiz çağrısı çalışmaya devam ediyor. Gerekçe denetim kaydının `requestBody` alanına düşüyor (§2av) ve aktörsüz köprü uyarı logunda görünüyor. `payouts` tablosuna kolon **eklenmedi**: issue'nun şikayeti gerekçenin hiçbir yere iletilememesi ve denetim gövdesinin boş kalmasıydı, ikisini de gövdeyi kabul etmek kapatıyor. Zorunlu yapmak inmiş panel sözleşmesini kırardı; zorunluluk panel gövdeyi göndermeye başladıktan sonra ayrı bir karar.

**`SPLIT` çözümü bilerek yok.** `PaymentStateMachine` tek yönlü ve tek sonuçlu: bir atama ya `RELEASED_TO_WORKER` ya `REFUNDED` olur. Kısmi paylaşım eklenirse durum makinesinin de bölünebilir hale gelmesi gerekir; yarım çalışan bir yol bırakmaktansa dışarıda tutuldu.

**Şikayet en az bir hedefe bağlanır:** kullanıcı, ilan, mesaj veya görsel. Hepsi boşsa 400; geriye uyumluluk
için birden fazlası birlikte verilebilir. Kendini şikayet etmek reddediliyor. Sonuçlanmış
(`ACTIONED`/`DISMISSED`) bir şikayet tekrar sonuçlandırılamaz.

**Mesaj şikayetinin yetkisi mesajlaşma aggregate'inde ve tek sorgudadır.** `POST /reports` mesaj kimliği
aldığında karar istemcinin gönderebileceği `reportedUserId` ya da `conversationId` değerine dayanmaz;
`MessageDao` mesajı kalıcı `message → conversation → active participant` ilişkisi üzerinden şikayet eden
için pessimistic write lock ile yükler. Olmayan, soft-delete edilmiş, konuşması silinmiş veya çağıranın aktif
katılımcısı olmadığı mesaj aynı `MESSAGING_MESSAGE_NOT_FOUND` 404'üne düşer. Şikayet edilen kullanıcı da
saklanan mesajın göndereninden türetilir ve eski istemcinin birlikte verdiği `reportedUserId` ezilir; gönderen
kendi mesajını şikayet ederse 400 alır. Eski istemcinin mesajla birlikte verdiği ilan/görsel hedefleri ise
korunur. Yükleme, sahiplik
kontrolü ve şikayet kaydı `ReportManager.createReport` transaction'ında kaldığı için istemcinin mesaj
geçmişini sayfa sayfa taramasına gerek yoktur.

**Mesaj şikayetinin doğal idempotency anahtarı `(reporter_id, message_id)` çiftidir.** Aynı canonical hedefler
(kalıcı mesaj, ondan türeyen gönderen ve varsa legacy ilan/görsel), normalize gerekçe ve açıklamayla retry ilk
`Report` satırını ve kimliğini, karar `ACTIONED`/`DISMISSED` olmuşsa bile döndürür; aynı anahtarın farklı
canonical payload'la kullanımı 409 `REPORT_IDEMPOTENCY_CONFLICT` olur. Mesaj satırındaki lock
eşzamanlı retry'ları sıralar, `uk_reports_active_reporter_message` kısmi benzersiz indeksi kalıcı savunmayı
sağlar. Migration tarihsel aktif duplicate görürse kayıt silerek tahmin yürütmez; operatör uzlaştırana kadar
HALT eder. Tekillik indeksinden önce eski mesaj şikayetlerinin boş ya da istemciden gelmiş `reported_user_id`
değerleri kalıcı `messages.sender_id` ile canonical hale getirilir.

### Şikayet inceleme yüzeyi — iki DTO, bir sınır (#63)

**Moderasyon yüzeyi kullanıcı yüzeyiyle aynı DTO'yu kullanmaz.** `/admin/reports**` `AdminReportDto` /
`AdminReportDetailDto` döndürür, `/reports` ve `/reports/me` eskisi gibi `ReportDto`. Issue "`ReportDto`'ya
şikayet eden ve `reviewedBy` eklensin" diyordu; **eklenmedi, bilerek**: aynı DTO `GET /reports/me` yanıtında
da duruyor ve `reviewedBy` oraya konsaydı şikayeti açan kullanıcı **kararı veren moderatörün adını ve
fotoğrafını** okuyabilecekti. Moderatör kimliğinin şikayet edene sızması taciz yüzeyidir; ayrım DTO
sınırında çözüldü. `AdminReportDto` eski `ReportDto` alanlarının **hepsini** taşır (`reportedUserId` dahil,
inmiş panel kırılmasın diye), üzerine `reporter`, `reportedUser`, `reviewedBy` (üçü de `SafeUserDto`) ve
`reportedUserReportCount` ekler.

**Tele ne çıkacağına içerik değil ihtiyaç karar verdi.** Kimlik her yerde `SafeUserDto` — id, ad, soyad,
kullanıcı adı, profil fotoğrafı. E-posta, telefon, TCKN ve IBAN moderasyon kararı için gerekmediği için
yüzeyin hiçbir yerinde yok; `ReportModerationPrivacyTest` bunu `AdminReportDto` ve `AdminReportDetailDto`
altında **geçişli** tarayarak sabitliyor (bir gün `SafeUserDto` zenginleşirse ya da yerine daha geniş bir DTO
konursa kırmızıya döner, ve testin kırmızıya dönebildiği sentetik bir sızıntı vakasıyla gösteriliyor — §3'teki
#72 dersi). Loglara kimlik yine **kullanıcı id'si** olarak giriyor (§2i); şikayet açıklaması, mesaj gövdesi ve
ilan başlığı hiçbir log satırına yazılmıyor — kullanıcı yazımı serbest metin, içinde üçüncü kişilerin verisi
olabilir.

**Hedefin okunabilir içeriği yalnızca detayda.** `GET /admin/reports/{reportId}` `{report, content}` döndürür;
`content` ilan başlığını, mesaj gövdesini ve görsel URL'ini taşır. Kuyruk satırları **hedefin içeriğini**
taşımaz — yalnızca UUID'sini; şikayet edenin kendi yazdığı `description` kuyrukta kalmaya devam ediyor
(eskiden de oradaydı, triyaj onsuz yapılamıyor). Ayrımın gerekçesi: kuyruk operatörün hiçbir vakayı açmadan
yüklediği toplu görünüm ve **şikayet edilen mesajın gövdesi şikayet edenin değil, üçüncü bir kişinin
yazısıdır** — her sayfa yüklemesinde toptan dışarı vermek gereksiz. Vakayı açmak tekil okumadır ve tekil
okuma denetim kaydına girer (§2av, `AuditScope` kuralı 3) — yani içeriğe kimin baktığı yazılı kalıyor.
Bunun görünür yan etkisi: `status` zorunlu olmaktan çıktığı için **süzgeçsiz kuyruk yüklemesi artık denetim
satırı yazmıyor** (§2av'nin "filtresiz liste yüklemesi yazılmaz" kuralı); süzgeçli her arama yazılıyor.

**Geçmiş sayısı sayfadan değil tablodan geliyor.** `reportedUserReportCount` şikayet edilen hakkındaki **tüm**
kayıtların sayısı (bulunduğu satır dahil), sayfa kapsamı değil. Kuyruk sayfası çekildikten sonra sayfadaki
şikayet edilen id'leri için **tek** bir `group by` sorgusu atılıyor (`ReportDao.tallyByReportedUser`); satır
başına sorgu yok. `ReportModerationIT` sayfa boyutunu bire indirip sayının yine iki geldiğini doğruluyor —
panelin bugün gösterdiği "bu sayfada N kez" işaretinin sessizce bu alana dönüşmesi böyle engelleniyor.

**Süzgeçlerin hepsi isteğe bağlı, `status` dahil.** Verilmezse tüm durumlar döner. `reason`, `targetType`
(`USER`/`JOB`/`MESSAGE`/`IMAGE`) ve `reportedUserId` de var; sonuncusu koala-admin#7'nin "aynı kullanıcı
hakkındaki şikayetleri grupla" maddesini karşılıyor. Sorgu `r.reportedUser.id` yolunu kullanıyor: Hibernate
bunu `@ManyToOne`'ın FK kolonuna indirdiği için **join açmıyor**, yani şikayet edileni olmayan satırlar
süzgeçsiz kuyruktan düşmüyor — varsayılmadı, `aReportWithoutAReportedUserStaysInTheUnfilteredQueue` ile
ölçüldü. `reported_user_id` üzerine indeks eklendi (`idx_reports_reported_user`): hem süzgeç hem sayım
sorgusu bu kolondan geçiyor.

**`POST /admin/reports/{id}/review` personel kimliğiyle çalışmıyordu — 404 dönüyordu.** §2p'nin yukarıda
`DisputeManager` için anlattığı tuzağın aynısı: `securityService.getAuthenticatedUser()` bir
`StaffPrincipal` görünce `{user.not.authenticated}` fırlatıyor, yani personel kimliğiyle gelen bir
moderatör şikayeti **hiç karara bağlayamıyordu**. `review` artık `StaffPrincipal actor` alıyor; köprü yolu
`reviewed_by_id` User ilişkisini, personel yolu `reviewed_by_staff_id` StaffMember ilişkisini damgalıyor ve
diğer ilişkiyi temizliyor. StaffMember, aggregate sınırını delmeden `StaffAccountService` üzerinden
çözülüyor. **Panelin bilmesi gereken:** mevcut `reviewedBy` DTO alanı geriye dönük olarak yalnız köprü
kararını gösterir; personel aktörün kanonik kaynağı StaffMember ilişkisi ve admin denetim kaydıdır.

**Karar yanıtı artık kuyruk satırının tamamı.** `review` `AdminReportDto` döndürüyor (eskiden `ReportDto`);
panel karardan sonra listeyi tazelemek için ikinci bir istek atmak zorunda değil. Alanlar eklendi, hiçbiri
kaldırılmadı.

**Karar gerekçesi zorunlu oldu.** `ReviewReportDto.note` `@NotBlank` taşıyor (`{report.review.note.required}`);
yalnız boşluktan oluşan gerekçe de reddediliyor. Panel istemcide zaten en az 10 karakter şart koşuyordu,
sunucu tarafı boş gerekçeyi kabul etmeye devam ediyordu.

**#63'ün 6. maddesi bilerek dışarıda.** Askıya alma ucunun gerekip gerekmediği issue'da **ürün kararı** olarak
işaretli; ilan ve mesaj kaldırma uçları da kabul kriterlerinde yok ve `JobService`/`MessagingService`
yüzeylerine dokunuyor. Bu PR şikayeti **okunabilir** yaptı, yaptırımı değil: operatör hâlâ "haklı" dediğinde
uygulayacak bir uç bulamıyor (görsel için `DELETE /admin/images/{imageId}` var). Kendi issue'sunu hak ediyor.

**`workHours` artık tahmin değil.** `WorkSession` check-in/check-out çiftini ve isteğe bağlı konumu tutar; kapanışta dakika yazılır ve atamanın `workHours` alanı tüm oturumların toplamından `HALF_UP` ile türetilir. Atama başına aynı anda tek açık oturum var (`uk_work_sessions_open_per_assignment`). Check-in/out yalnızca **işçinin kendisi** yapabilir — işveren yalnızca okuyabilir. İlk check-in atamayı `IN_PROGRESS`'e taşır.

**Çevrimdışı puantaj olayı kalıcı ve idempotent.** Yeni istemci `clientEventId` ile `capturedAt` alanlarını birlikte gönderir; eski konum-only gövde sunucu zamanlı davranışını korur. Yeni sözleşmede ham cihaz zamanı, ilk alım zamanı, ücret hesabındaki etkili zaman ve plausibility bayrakları ayrı saklanır. Aynı atama/işlem kimliği aynı normalize payload ile tekrarlandığında ilk `WorkSession` döner; değiştirilmiş payload 409'dur. Atama başına transaction advisory lock eşzamanlı istekleri sıralar; check-out retry kapanmış oturumu da bulur. Varsayılan 72 saat geçmiş/5 dakika gelecek penceresi `timesheet.*` ayarlarıyla değişir; ilan penceresi dışı ve monotonik olmayan zamanlar reddedilmeden işaretlenip güvenli sınıra çekilir. Tehdit modeli ve ayrıntılı zaman kararı [ADR-0013](docs/adr/0013-record-offline-timesheet-events-before-reconciling-them.md)'tedir.

### Uyuşmazlık kanıt ekleri — seçilen yükleme akışı ve taramanın sınırı (#102)

**Doğrulanmış multipart seçildi, presigned upload + finalize seçilmedi.** Issue ikisini de öneriyordu. Presigned
yol backend'i büyük dosya trafiğinden kurtarır ama bu depoda **doğrulanamaz bir ikinci kapı** kurardı: finalize
adımı nesne sahipliğini ve checksum'ı `HeadObject` ile doğrulamak zorunda, o doğrulamanın tek gerçek uygulaması
S3'te, ve **varsayılan depo `local`** — `LocalStorageService` PUT imzalayamaz (§2ab). Yani güvenliğin bağlı olduğu
tek satır CI'da hiç koşmayan bir dalda kalırdı; üstüne bekleyen (`PENDING`) satır, yetim nesne temizliği ve
finalize edilmeyen yüklemeler için ayrı bir yaşam döngüsü gerekirdi. Multipart'ta doğrulama her depo
uygulamasının paylaştığı tek yoldan geçiyor ve iş tek bir transaction: dosya boyutu 10 MB ile, uyuşmazlık başına
adet 5 ile sınırlı olduğu için backend'in taşıyıcı olması ölçülebilir bir maliyet değil. Presigned'a geçilecekse
şartı yazılı: `StorageService`'e imzalı PUT eklenmeden ve finalize'ın sahiplik+checksum kontrolü gerçek bir
S3'e karşı test edilmeden geçilmez.

**Kanıt `files/dispute-evidence/` altında, ve bu iç içelik bilinçli.** Önek `files/` ile başladığı için
`StorageKeys.isPrivate` true — nesne özel kalıyor ve CDN'e hiç düşmüyor. Alt önek bucket lifecycle kuralının
kanıt dosyalarına, geri kalan `files/` içeriğine dokunmadan bağlanabilmesi için var. **`/uploads/files/**` bu
nesneleri servis edemez**: o yol sahipliği `media` tablosundan çözüyor (`CvService.assertCanDownload`), kanıtın
`media` satırı yok, dolayısıyla 404. Yani genel indirme yolu varsayılan olarak kapalı; kanıt yalnız uyuşmazlık
kapsamındaki uçlardan iniyor.

**Yetki uyuşmazlığın kendisinden okunuyor, dosyadan değil.** `DisputeManager` önce uyuşmazlığı buluyor, sonra
`AssignmentParticipants` ile atamanın tarafı olup olmadığına bakıyor. Taraf olmayanın aldığı yanıt var olmayan
uyuşmazlığınkiyle **aynı anahtar** (`{dispute.not.found}`) — §2d2'nin kuralı. Kanıt eki yalnız `RESOLVED`
olmayan uyuşmazlığa ekleniyor; çözülmüş uyuşmazlık listelenip indirilmeye devam ediyor, çünkü karar sonrası
dosyayı okumak hâlâ meşru.

**Adet sınırı satır kilidinin arkasında.** `attachEvidence` `findByIdForUpdate` ile uyuşmazlık satırını kilitliyor,
sayımı ondan sonra yapıyor. Eşzamanlı iki yükleme sıraya giriyor; sınır `upload.max-evidence-per-dispute` (5).
Boyut `upload.max-evidence-size-mb` (10), uzantı beyaz listesi `upload.evidence-extensions`
(`pdf,png,jpg,jpeg,webp`).

**İçerik tipi istemciden alınmıyor.** `Content-Type` başlığı sahtelenebilir (§2ab); saklanan tip uzantıdan
`MediaTypeFactory` ile türetiliyor. Ayrıca `UploadValidator` kanıt için **baytlara bakıyor**: `pdf` iddia eden
dosya `%PDF` sihirli baytıyla başlamak zorunda, görsel iddia eden dosya `ImageIO` ile açılabilmek zorunda. `.exe`
zaten uzantı beyaz listesinden düşüyor.

**Yükleme yarıda kalırsa ne satır ne nesne kalıyor.** Depo yazması patlarsa satır hiç açılmıyor. Satır yazması
patlarsa nesne yetim kalırdı; `attachEvidence` bunun için transaction'a bir `afterCompletion` kaydediyor ve
commit dışındaki her sonuçta nesneyi siliyor. Kural testle sabit (`anObjectWrittenBeforeAFailedCommitIsRemoved…`)
ve mekanizma çıkarıldığında kırmızıya döndüğü ölçüldü.

**DTO depo anahtarı taşımıyor, indirme ayrı uçtan.** `DisputeEvidenceDto` yalnız kimlik, yükleyen, ad, tip,
boyut, SHA-256 ve zaman taşıyor. Personel liste ucu kullanıcı sözleşmesini genişletmez; ayrı
`StaffDisputeEvidenceDto` döndürür. `AssignmentParticipants` yükleme kapısında yalnız atamanın taraflarını kabul
ettiği için kanıttaki yükleyen atamanın
işçisiyse `WORKER`, değilse `EMPLOYER` olur; böylece sonradan şirket irtibat listesinden çıkarılan bir yükleyenin
tarihsel tarafı da değişmez. İndirme
`GET /disputes/{id}/evidence/{evidenceId}/download` (taraflar) ve
`GET /admin/disputes/{id}/evidence/{evidenceId}/download` (personel, `dispute.resolve`) uçlarından; depo imzalı
adres destekliyorsa `storage.signed-url-minutes` (3 dk) ömürlü imzalı adrese 302, desteklemiyorsa baytlar
akıtılıyor. İki yolda da `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff` ve
`Cache-Control: no-store` var — kanıt tarayıcıda çizilmiyor. İmzanın ömrü gerçek bir `S3Presigner` ile ölçülüyor
(`X-Amz-Expires=180`), kaynak taramasıyla değil.

**`SignedFileResponses` `StorageDownloadController.serve`'ün bilinçli ikizi.** İkisi de "imzalı adres varsa 302,
yoksa baytları akıt" kararını veriyor ama **disposition politikaları farklı**: özgeçmiş ve görseller `inline`
kalmalı, kanıt her zaman `attachment`. Ortak bir yardımcıya indirmek `StorageDownloadController`'ın
constructor'ını değiştirir ve `StorageDownloadRulesTest`'i de sürükler; kazanç, iki farklı politikayı tek
metotta bayrakla yönetmenin maliyetini karşılamadı. Üçüncü bir çağıran çıkarsa birleştirilir.

**Zararlı içerik taraması bu PR'da yok ve yapılamaz.** Depoda antivirüs/ICAP/karantina altyapısı yok; eklenmedi.
Yapılan şey tür, boyut, adet, sihirli bayt kontrolü, adın anahtara girmemesi ve indirmenin `attachment` olması.
**İyi biçimlenmiş ama zararlı bir PDF ya da görsel bugün saklanıyor ve servis ediliyor.** Gerçek tarama harici
bir servis (S3 tetikli tarayıcı ya da ICAP ağ geçidi) ve satırda bir karantina durumu ister; kendi issue'sunu
hak ediyor. Ayrıntı `docs/runbooks/dispute-evidence.md`'de.

**KVKK silmesi yeni bir mekanizma kurmadı, ADR-0012'nin yoluna bağlandı.** `PostgresAccountAnonymizer.removeMedia`
artık `dispute_evidence` anahtarlarını da toplayıp **aynı** `PersonalMediaDeletionOutbox`'a yazıyor: tek sorgu
kümesi, tek `enqueue`, commit sonrası tek `process`. Açık uyuşmazlık zaten anonimleştirmeyi bloke ediyor
(ADR-0012), yani zamanlayıcı koştuğunda vaka kapalı. Silinen kapsam **hem kullanıcının yüklediği kanıt hem
kullanıcının taraf olduğu atamanın uyuşmazlıklarındaki kanıt** — `disputes.reason`'ın bugünkü davranışının
aynısı. Gerekçe: aynı satırdaki serbest metin iki taraf için de zaten siliniyor; daha tanımlayıcı olan dosyayı
bırakıp anlatıyı silmek tutarsız olurdu. `storage_key` ve `checksum_sha256` bu yüzden nullable.

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

**Firebase adapter kodda, fakat insan kabulü bitene kadar kapalıdır.** `firebase-admin` doğrudan pinlidir; adapter açıkça JDK tabanlı `NetHttpTransport` kullanır ve Firebase/Google API Client yolundaki Apache 4/5 transport bağımlılıkları dışlanır. AWS S3'ün kendi runtime Apache 4 istemcisi bu yoldan bağımsızdır. Bu sınır uygulamanın Spring `RestTemplate` varsayılanını sağlayıcı eklenmeden önceki gibi korur. ADC OAuth istekleri de aynı JDK transport üzerinde bağlantı/okuma/yazma timeout'ları ve sıfır iç retry ile sınırlandırılır; credential yenileme denemesi claim network zarfına dahildir. `FIREBASE_PUSH_ENABLED` varsayılanı `false` olduğu için yerel ve deploy ortamları ADC aramaz, `UnconfiguredPushService` güvenli sabit hata koduyla teslimatı sonlandırır. Özellik açıldığında project ID zorunludur, ADC yüklenemiyorsa veya credential'ın bildirdiği proje farklıysa uygulama açılışta düşer. Credential değeri kodda, logda veya image'da tutulmaz; salt-okunur secret mount'a işaret eden `GOOGLE_APPLICATION_CREDENTIALS` kullanılır. Gerçek Android/iOS cihaz, APNs key ve Firebase proje eşleşmesi `docs/plans/provider-setup.md` içindeki harici yayın kapısıdır.

**Rabbit yalnız tetikleyici, `Notification` push teslimatının doğruluk kaynağıdır.** Başlık, gövde, data JSON, cihaz, attempt, sonraki deneme ve fenced processing claim kalıcıdır; ham push token snapshot'ı bildirim satırına hiç yazılmaz. JPA callback yeni kodu, veritabanı trigger'ı da rolling deploy sırasında çalışan eski binary'yi kapsar; trigger kurulduktan sonra yapılan ikinci purge kurulum aralığında yazılmış olabilecek PUSH snapshot'larını temizler. Yeni push kaydı cihazları okumadan önce yalnız alıcı kullanıcı kilidini alır; fan-out alıcıları distinct UUID sırasında kilitler ve hesabın aktifliğini aynı transaction'da yeniden doğrular. Ortak mutation kilidi bildirim fan-out'unda alınmaz: yüzlerce alıcının outbox kayıtları hazırlanırken alakasız bir kullanıcının token kaydı, logout'u veya anonimleştirmesi beklemez. Aynı alıcıdaki anonimleştirme ise kullanıcı kilidinde sıralanır; ya yeni kaydı sonradan temizler ya da gönderim silinmiş hesabı görüp kayıt açmaz. Consumer queue'daki token/title/body/data snapshot'ını kullanmaz; notification ID ile kısa transaction'da satırı kilitler. Provider çağrısından hemen önce aynı token'ın session advisory lock'ını beklemeden dener; kilit doluysa FCM'e dokunmadan fenced claim'i `TOKEN_SERIALIZATION_BUSY` gerekçesi ve kısa backoff ile `QUEUED` durumuna döndürür, bu çakışmayı provider attempt sayısından düşer. Kilidi alırsa sahipliği aynı connection üzerinden tekrar doğrular ve FCM bitene kadar token kaydını serialize eder. Token taşınmış veya logout ile temizlenmişse FCM çağrısı yapılmaz. Token kaydetme, yenileme, logout, invalid-token temizliği ve hesap anonimleştirme önce ortak mutation kilidini, ardından kullanıcı ve değişen eski/yeni token kilitlerini sıralı alır; cihaz find/create yolu kullanıcı ve mevcut token kilitleri altında hesabın hâlâ aktif olduğunu yeniden doğrular, bu yol sonradan mutation kilidi istemediği için kilit döngüsü kurmaz. Token başka cihaza taşınırken immediate partial unique index nedeniyle eski sahiplerin `NULL` güncellemeleri yeni sahibin atamasından önce flush edilir; Hibernate'in managed entity sırası bu protokolün yerine geçmez. Rolling deploy trigger'ı eski binary'nin tam-satır metadata güncellemesinde veritabanındaki güncel tokenı korur; eski token-only clear/rotation ise ortak mutation ve hem eski hem yeni token kilitleri altında çalışır. Eski binary'nin anonimleştirme başlangıcı audit trigger'ıyla ortak mutation + kullanıcı sırasına alınır, böylece bildirim scrub'ından sonra yeni özel veri yazılamaz. Anonimleştirme kullanıcının gönderdiği bildirimlerin hedef cihaz token'larını da kilitler. Aktif satırlardaki partial unique index son DB savunmasıdır; migration belirsiz eski duplicate grupların hiçbir sahibini tahmin etmeyip tüm token'ları temizler.

**Provider çağrısı DB transaction'ı dışında, completion claim ID ile fenced'dir.** Böylece yavaş FCM isteği row lock tutmaz; duplicate/concurrent Rabbit teslimi `PROCESSING` claim'i geçemez, stale worker yeni claim'in sonucunu ezemez. Claim timeout, pinli SDK'nın network-attempt zarfından uzun olmak zorundadır. Transient typed FCM hatası attempt sayısını kalıcı tutarak jitter'lı exponential backoff ve varsa en fazla `max-provider-deferral` kadar `Retry-After` ile yeniden `QUEUED` olur; terminal hata yalnız sabit sanitize kod saklar. SDK mesajı kabul edip response/DB commit öncesi process ölürse dış sistem exactly-once garanti etmez: server caller data'sından sonra reserved `notificationId` yazar, mobil bu ID'yi bounded kalıcı store'da dedupe etmelidir.

**Push DLQ transport/poison hataları içindir, provider retry kuyruğu değildir.** Beklenen FCM sonuçları DB state machine içinde ACK'lenir. Beklenmeyen runtime hatası claim'i yeniden alınabilir bırakıp listener'a taşar; stateless sınırlı Rabbit retry sonunda `RepublishMessageRecoverer` ayrı `push.queue.dlq`'ya yollar. Canlıda arguments'sız yaratılmış `push.queue` declaration'ına DLX argument eklenmedi; aksi hâlde Rabbit `PRECONDITION_FAILED` ile deploy'u kırardı. Scheduler daha önce provider denemesi yapılmış (`push_attempt_count > 0`) due/stale teslimatları ve yalnız `failure_reason = 'TOKEN_SERIALIZATION_BUSY'` olan due kayıtları sürer. İkinci istisna attempt'i sıfıra geri alınmış ilk Rabbit teslimatının token kilidi çakışmasında kaybolmaması içindir; düz attempt=0 satırı scheduler'a girmez. İlk publish ve publish-loss recovery genel notification outbox'ının sorumluluğudur. Böylece outbox publisher ile provider scheduler aynı ilk PUSH satırını ayrı ayrı claim etmez.

**E-posta, SMS ve push kuyruğa doğrudan yazılmaz; `Notification` satırı aynı zamanda kalıcı publish outbox'ıdır.** Üretici transaction yalnızca `QUEUED` bildirimle exact Rabbit payload'ını, önceliği ve ilk çalışma anını birlikte commit eder. Çağıranın gördüğü başarı broker teslimi değil, bu iki verinin atomik ve kalıcı kabulüdür. `HIGH` ile `NORMAL` aynı dayanıklılık sözleşmesine sahiptir; `HIGH` yalnız due batch içinde önce sahiplenilir. Broker o anda kapalıyken `NORMAL` sessizce kaybolmaz, `HIGH` da DB/Rabbit arasında sahte bir atomiklik iddia etmez.

**Publish worker üç fazlıdır.** Kısa DB transaction atomik claim ve rastgele fencing token alır, Rabbit publish DB transaction'ı dışında tamamlanır, sonuç ayrı kısa transaction'da yalnız aynı token hâlâ sahibiyse yazılır. Rabbit gönderimi mandatory routing kullanır; correlated publisher ACK gelmeden ve basic.return ile `NO_ROUTE` olmadığı görülmeden `published_at` yazılmaz. Böylece exchange var olduğu halde binding ya da queue kaybolduğunda sessiz başarı üretilmez. İki instance aynı bildirimi publish etmez. Retry bütçesini yalnız broker'ın yönlendirmediği bilinen publish hataları tüketir; broker onayından sonraki `completeDispatch` DB hatası publish hatası diye yakalanmaz, claim'i yerinde bırakıp scheduler'a taşar. Publish sonrası process çökmesiyle aynı belirsizliği taşıyan stale claim terminal yapılmaz ve bütçeden düşülerek aynı notification ID ile yeniden publish edilir. Geçici broker hatası exponential backoff ve bounded jitter ile en fazla yapılandırılmış attempt sayısı kadar denenir; sonrasında bildirim `FAILED / BROKER_PUBLISH_EXHAUSTED` olur. Ham exception, broker adresi ve kimlik bilgisi `failure_reason`'a yazılmaz. Exact payload'ı bulunmayan eski `QUEUED` kayıt tahmin edilmez; grace period sonunda `FAILED / LEGACY_DISPATCH_PAYLOAD_UNAVAILABLE` ile görünür biçimde kapatılır.

**Publish taraması shared scheduler'ı kullanmaz.** Tek iş parçacıklı ayrı tetikleyici, sabit boyutlu worker havuzuna her notification ID'yi ayrı görev olarak verir; bounded queue dolduğunda caller-runs backpressure uygulanır ve iş düşürülmez. Outbox satırı oluşturan isteğin `requestId`, `userId`, `sessionId` ve `deviceId` değerlerini payload ile birlikte saklar. Worker her publish için yalnız o satırın context'ini kurar ve sonunda önceki MDC'yi geri yükler; eski satırlar her denemede yeni bir `requestId` alır, aynı scheduler taramasının kimliğini paylaşmaz. Outbox backlog sayısı, en eski kaydın yaşı ve terminal broker publish hataları etiketsiz sabit gauge'larla DB'den yenilenir; notification veya kullanıcı kimlikleri metric label olmaz.

**E-posta, SMS ve push consumer idempotency kapısı token'lı `QUEUED → PROCESSING` delivery lease'idir.** Yanlış kanal ile `SENT`, `FAILED` ve `SUPPRESSED` tekrar provider'a gitmez; canlı `PROCESSING` claim'i başarı diye ACK edilmez, Rabbit yeniden dener. Claim sonucu DB cevabı kaybolsa veya process çökerse lease süresi dolan `PROCESSING` kaydı yeni fencing token ile geri alınabilir. Complete/fail/suppress/retry geçişleri yalnız aynı token'a ait delivery kolonlarını dar SQL ile günceller; `published_at`, publish claim'i, exact payload, öncelik ve attempt sayısını entity save ile geri ezmez. E-posta transient hatasında claim tekrar `QUEUED` bırakılır; başarılı sonraki deneme eski retry nedenini temizler. Bounded Rabbit retry bittikten sonra custom recoverer önce sabit `EMAIL_DELIVERY_EXHAUSTED` durumunu kalıcılaştırır, ardından DLQ publish için de mandatory routing ve correlated confirm bekler. DB terminalizasyonu veya DLQ confirm başarısızsa kaynak mesaj ACK edilmez; terminalleşmiş fakat henüz DLQ'ya onaylı yazılamamış e-posta tekrar provider'a gitmeden recovery yoluna döner. Push'ın provider hata sınıflandırması ve token silme politikası Firebase provider işine aittir. SMTP/SMS/FCM provider isteği kabul ettikten fakat `SENT` yazılmadan önce process çökerse provider idempotency anahtarı olmadan mutlak exactly-once garantisi mümkün değildir; lease ve fencing, belirsizliği süreyle sınırlar ve eski worker'ın yeni sonucu bozmasını engeller.

**Bildirim akışı artık teslimat kaydı değil.** `Notification` hem teslimat kaydı (EMAIL/SMS/PUSH) hem uygulama içi bildirim (`IN_APP`) tutuyor; akış **yalnızca `IN_APP`** döndürüyor.

Önceki hâlinde akış bütün kanalları döndürüyordu ve `ActivityNotificationManager` olay başına iki kayıt yazıyordu — dahası `sendPushToUser` **cihaz başına** bir kayıt açıyor, yani iki telefonu olan kullanıcı tek bir olay için üç satır görüyordu. `unreadCount` de aynı sebeple katlanıyordu. Kanala göre süzmek en az değişiklik olurdu ama anlamı zorlardı: e-posta gönderiminin kaydını uygulama içi bildirim diye göstermek olurdu.

**`IN_APP` kaydı teslimattan bağımsız.** Kuyruğa girmez, doğrudan `SENT` yazılır; push sağlayıcısı çökse de, kullanıcının hiç cihazı olmasa da akış çalışır. Üçü de ayrı `try/catch` içinde — akış kaydı düşse bile e-posta ve push gider.

**`subject` başlık, `body` cümle.** Öncesinde push kaydında `subject` ilan başlığını, e-posta kaydında olayı taşıyordu; hangisi seçilse yarısı eksikti. `IN_APP` ikisini birden taşıyor: `subject` = ilan başlığı, `body` = ne olduğu. Gövde eskiden yalnızca `templateParameters` JSON'ının içindeydi ve DTO'ya çıkmıyordu; artık kendi kolonu var.

**Bildirim tıklanabilir.** `targetType` (`JOB`, `JOB_APPLICATION`, `JOB_ASSIGNMENT`, `CONVERSATION`, `USER`) ve `targetId` `ActivityNotificationManager` tarafından dolduruluyor. Hedefi olmayan bildirimde ikisi de null.

`POST /users/me/notifications/{id}/read` tek bildirimi işaretler; zaten okunmuşsa ya da başkasına aitse **sessizce geçer**, hata dönmez — akış ekranında hata yakalamak gereksiz. Toplu işaretleme yerinde duruyor ve o da yalnızca `IN_APP` satırlarına dokunuyor.

**Şirket uçları:** `GET/PATCH /companies/{id}`, `GET /companies/{id}/contacts`, `DELETE /companies/{id}/contacts/{userId}`. Düzenleme ve yetkili çıkarma yalnızca şirket **ADMIN**'ine açık, üye olmayan 404 alır. **Son ADMIN çıkarılamaz** — yoksa şirket yönetilemez hale gelirdi.

**Şirket adresi `PATCH /companies/{id}` ile değişir (#288).** `UpdateCompanyRequestDto.address` isteğe bağlı `@Valid AddressWriteDto`; verilirse `AddressService.createAddress` **yeni** satır açar ve `company.address` ona bağlanır — adres satırı yerinde güncellenmez, çünkü `createAddress` hizmet alanı ve referans tutarlılığını oluşturma yolunda doğruluyor ve aynı kapıdan geçilmesi isteniyor. Eski satır `Company.address`'in `orphanRemoval = true` + `Address` `@SQLDelete`'i ile **soft-delete** olur (ayrı silme çağrısı yok); adres satırları her zaman tek sahiplidir (`createAddress` sahip başına taze satır açar), yani başka kaydın adresi düşmez. Yalnız şirket **ADMIN**'i, `STAFF` diğer PATCH alanlarıyla aynı 404'ü alır (`requireCompanyAdmin`, §2d2); eksik `districtId` DTO'da 400 `details[].field=address.districtId`. Geçmiş ödemeler etkilenmez — Iyzico fatura adresi ödeme oturumu açılırken `company.getAddress()`'ten okunur (`JobAssignmentManager.fillBuyerAndAddressInfo`), yani sonraki oturum yeni adresi kullanır. `JobCompanyDto`'ya adres **eklenmedi** (#159). `CompanyAddressUpdateIT` beş kuralı gerçek Postgres'te sabitliyor (yeni satır + eski satır `is_deleted`, STAFF 404, 400 alan hatası, adressiz PATCH adresi korur, sonraki ödeme oturumunun `billingAddress`'i yeni adres).

**İki rol var: `ADMIN` ve `STAFF`.** `MANAGER` kaldırıldı — kodda yalnızca `ADMIN` kontrol ediliyordu, yani MANAGER ile STAFF **tamamen aynı** yetkiye sahipti ve davet ekranında farkı olmayan üç seçenek görünüyordu. `28-07` migration'ı mevcut `MANAGER` satırlarını `STAFF`'a çeviriyor. Yetki sınırı: ADMIN şirket ayarları, logo, galeri, yetkili ekleme/çıkarma ve onay; STAFF ilan açar, kapatır, iptal eder ve başvuru cevaplar — ayarlara dokunamaz. Ayrım kodda zaten `requireCompanyAdmin` ile `isUserAContactOfCompany` arasında duruyordu, eksik olan yalnızca üçüncü rolün anlamsızlığıydı.

**`CompanyContact` artık `BaseEntity`.** Denetim kolonları ve soft delete kazandı; üyelikten çıkarma geçmişi silmiyor. `(company_id, user_id)` kısmi benzersiz indeksi eklendi, yani çıkarılan biri yeniden eklenebiliyor.

**Admin uçları `/admin/**` altında, tamamı `ROLE_ADMIN`.** `GET /admin/companies?status=PENDING` onay bekleyenleri listeler, `POST /admin/companies/{id}/approve` onaylar, `POST /admin/companies/{id}/reject` reddeder (§2s4), `GET /admin/users?query=` kullanıcı arar.

**Şirket onayı bir açıktı.** `CompanyManager` şirketi `approved = false` yaratıyordu ve `setApproved(true)` **hiçbir yerde çağrılmıyordu**; hiçbir şirket onaylanamıyordu. Onay ucu bunu kapatıyor; ret ucu, gerekçe ve karar bildirimi §2s4'te.

**Davet ucu üçüncü kişinin TCKN'sini artık istemiyor.** `POST /companies/{companyId}/contact-invitations`
gövdesi bugüne kadar davet **edilen** kişinin T.C. kimlik numarasını istiyordu ve o alanın tek işi
`inviteContactToCompany` içindeki bir **teyit** kontrolüydü: gönderilen numara, bulunan kullanıcının kayıtlı
numarasıyla karşılaştırılıyordu. Kimlik **eşleştirme** anahtarı hiçbir zaman o alan değildi — davet edilen
kişi `userService.getByEmail` ile bulunuyor. Alan, `companyContact.tcIdentityNumber.notBlank` anahtarı ve
öksüz kalan `user.tc.identity.does.not.match` üç katalogdan da düşürüldü (§2aj). **Yeni gövde iki alan:**
`{"email": "...", "role": "ADMIN" | "STAFF"}`.

**Gerekçe: bir kullanıcıdan başkasının kimlik numarasını istemek o veriyi isteyenin istemcisine taşıyor.**
§2i'nin "TCKN hiç loglanmaz" ve §2aq'nun "yanıtta maskelenir" kurallarıyla aynı yön — değer istemciye
girdiği anda log'a, Sentry olayına ve ölçümlemeye açılıyor. `koala-web` davet formunu bu yüzden bilerek
yazmamıştı; `server-only.test.ts` `tcIdentityNumber` dizesinin yalnız `viewer.ts` içinde geçmesini zorluyor.
Karşılığında verdiği güvence de zayıftı: **davet zaten kabul gerektiriyor**, yani yanlış adrese gönderilen
bir davet karşı taraf kabul etmedikçe hiçbir şey yapmıyor. Teyidi ikinci onay adımı, yazılan bir numaradan
daha güçlü yapıyor.

**Kimliği doğrulanmamış kişi hâlâ yetkili yapılamıyor, ve bunu bir test koruyor.** Davet edilen kişinin
`tcIdentityNumber`'ı null ise davet yine **400** `USER_MUST_VERIFY_IDENTITY` ile reddediliyor; bu kural
kaldırılan teyitten bağımsız. `CompanyInvitationSendingRulesTest`'teki
`someoneWhoseIdentityIsNotVerifiedStillCannotBeMadeAContact` onu ileride "kalıntı" sanılıp silinmesin diye
sabitliyor — kontrol kaldırılınca kırmızıya döndüğü denendi ve görüldü.

**Kullanıcı sayımı incelendi, uç bilerek olduğu gibi bırakıldı.** `getByEmail` kayıtlı olmayan bir adres için
**404** `USER_NOT_FOUND_BY_EMAIL` üretiyor, yani uç davet edene "bu e-posta Koala'da kayıtlı mı" sorusunu
cevaplıyor — is-koala/koala-backend#42'nin (giriş ucu) kardeşi. **Gizlenmedi, ve sebebi §2d2'nin kendi
kuralı:** gizleme yanıt düzeyindedir, tek bir anahtarın düzeyinde değil. Burada eşitlenecek iki *hata* dalı
yok; sinyali veren şey **başarı** — kayıtlı bir adres 201 (ya da 409/400) döndürüyor, kayıtlı olmayan
döndüremiyor. 404'ün anahtarını `Concealment.notFound` ile şirketin anahtarına çekmek tek bir bit bile
saklamaz, yalnızca şirket yöneticisine şirketinin bulunamadığını söyleyen bir yalan üretirdi. Sinyali
gerçekten kaldıran tek tasarım, davetin **kayıtlı olmayan bir adrese de** açılabilmesi olurdu; o, davet
kanalı yüzeyidir (is-koala/koala-backend#85) ve bu değişikliğin kapsamı dışında.

**Yüzeyin darlığı kabul gerekçesinin ikinci ayağı.** Uca ulaşmak `requireCompanyAdmin`'den geçmeyi, yani
şirketin **ADMIN** yetkilisi olmayı gerektiriyor ve o kontrol metodun ilk satırı (§2s2). Şirket yöneticisi
olmanın yolu şirket kurmaktan geçiyor, şirket kurmak ise `addCompany`'de `isIdentityVerified` istiyor
(§2aq): her deneme çiple doğrulanmış gerçek bir kimliğe bağlı ve `WRITE` kovasında IP başına dakikada 60
istekle sınırlı (§2j). #42'den farkı bu — orası anonim, burası kimliği doğrulanmış ve dar.

## 2s2. Yetkili daveti — kim listeler, kim okur, kim cevaplar

Davet yüzeyi bugüne kadar hiçbir istemcide açılmamıştı; web ilk istemci olurken dört ayrı yerde ya
yetkilendirme yanlış katmandaydı ya belge kodla çelişiyordu (issue #68).

**Liste ucu platform rolüne kilitliydi.** `SecurityConfig` `GET /companies/{companyId}/contact-invitations`
için `hasAnyRole("ADMIN")` yazıyordu; oradaki `ADMIN` `Role.ROLE_ADMIN`, yani **platform** yöneticisi —
şirketin `CompanyContactRole.ADMIN`'i değil. Sıradan bir işveren davet gönderebiliyor ama gönderdiği daveti
göremiyordu, istek filtrede 403 ile duruyordu. Doğru denetim servis katmanında zaten yazılıydı ve
**erişilemiyordu**. Kural kardeş uçlardaki kalıba çekildi: filtre `USER, ADMIN`'e açık, yetki servis
katmanında (§0b).

**Sözleşme — üç uç, üç ayrı taraf kümesi:**

| Uç | Kim geçer | Geçmeyen ne alır |
|---|---|---|
| `GET /companies/{id}/contact-invitations` | yalnız şirketin `ADMIN` yetkilisi | **404** `USER_NOT_ASSOCIATED_WITH_COMPANY` |
| `GET /contact-invitations/{id}` | davet edilen, daveti gönderen, şirketin `ADMIN` yetkilisi | **404** `COMPANY_CONTACT_INVITATION_NOT_FOUND` |
| `POST /contact-invitations/{id}` | yalnız davet edilen | **404** `COMPANY_CONTACT_INVITATION_NOT_FOUND` |

`STAFF` yetkilisi listeyi de tekil daveti de göremez: davet üyelik yönetimidir ve §2s'e göre o `ADMIN`'in işi.

**Durum kodunu eşitlemek yetmez, anahtar da aynı olmak zorunda.** §2o/§2ab/§2p'nin 404 kuralının amacı
kaynağın *varlığını* sızdırmamak; ama §2d gereği `errorCode` mesaj anahtarından türüyor ve hata zarfıyla
istemciye gidiyor. İki dal aynı 404'ü döndürse bile farklı anahtar kullanıyorsa `errorCode` onları ayırt
eder — UUID deneyen saldırgan "sana ait değil" anlamına gelen bir kod aldığında **kaynağın var olduğunu**
öğrenir. Bu yüzden "davet yok" ile "davet senin değil" dalları **aynı anahtarı** taşıyor. Liste ucunda ayrı
bir dal yok: `isUserAdminOfCompany` saf bir `exists…` sorgusu olduğu için var olmayan şirket de üyesi
olmadığın şirket de aynı yanıtı üretiyor. Depo genelindeki tarama is-koala/koala-backend#77.

**`inviteContactToCompany` artık `requireCompanyAdmin` kullanıyor.** Metot kendi ADMIN kontrolünü
`company.getContacts()` üzerinde elle kuruyordu, oysa sınıfta aynı işi yapan bir yardımcı vardı ve o
`ResourceNotFoundException` atıyordu. **İstemciye görünen sonuç: davet gönderme ucu yetkisiz çağrıda artık
403 değil 404 dönüyor** — sınıfın diğer altı ucuyla (logo, galeri) aynı davranış. Kontrol metodun ilk satırı:
yetkilendirme daima durum kontrolünden önce (§2ar3), yani yabancı biri davet edilecek kişinin var olup
olmadığını da öğrenemiyor.

**Bekleyen davet tekil.** `PENDING` bir davet dururken aynı kişiye ikinci davet **409**
(`COMPANY_CONTACT_INVITATION_ALREADY_PENDING`); öncesinde her çağrı yeni bir kayıt ve `HIGH` öncelikli
yeni bir e-posta üretiyordu ve kayıtların hepsi ayrı ayrı kabul edilebiliyordu. Cevaplanmış davet yeni daveti
engellemez. "Kullanıcı zaten yetkili" dalı da `ValidationException`'dan `ConflictException`'a çekildi (§2ag):
ikisi de durum çakışması, girdi şekli hatası değil.

**Davet DTO'sunda e-posta alanı kendi anahtarını kullanıyor.** `InviteContactToCompanyDto.email`
`{companyContact.firstName.notBlank}` mesajını taşıyordu — boş e-posta gönderen kullanıcıya "Ad boş olamaz"
gidiyordu. Alan artık `@Email` de taşıyor; kimsenin kullanmadığı `companyContact.firstName.notBlank` anahtarı
üç katalogdan da düşürüldü, `user.not.associated.with.invitation` de öyle (o dal artık 404 anahtarını
kullanıyor).

**Düzeltmeyi sabitleyen şey `CompanyContactInvitationAccessIT`.** Hata filtre zincirindeydi; manager'ı
doğrudan çağıran birim testleri düzeltme öncesi kodda da geçerdi, yani hiçbir şeyi sabitlemezlerdi. IT
`MockMvc` ile gerçek zinciri koşuyor ve **yalnız `ROLE_USER` taşıyan** şirket yöneticisiyle 200 alıyor;
`SecurityConfig` satırı geri alınırsa test 403 ile kırmızıya döner (denendi, görüldü). Aynı sınıf `STAFF`
yetkilisini gerçek bir `company_contacts` satırıyla modelliyor ve yabancının aldığı 404'ün rastgele bir
UUID'nin 404'ünden **`errorCode` dahil** ayırt edilemediğini kanıtlıyor.

**Entity üzerindeki türetilmiş erişimci `get` öneki almaz — bu bir tuzak, tercih değil.** Getter zincirini
gizlemek için `CompanyContactInvitation`'a `companyId()` eklendi. İnceleme bunu `getCompanyId()` yapmayı
önerdi (bean adlandırması), denendi ve **`GET /companies/{id}/contact-invitations` 500 dönmeye başladı**:
Spring Data türetilmiş sorguları JavaBean özelliklerinden çözüyor, `getCompanyId()` bir `companyId` özelliği
doğuruyor ve `findByCompanyId` artık `company.id` yerine `c.companyId` üretiyor — Hibernate böyle bir
öznitelik bulamayınca `UnknownPathException` atıyor. Aynı risk Jackson ve MapStruct için de var. Kural:
alanı olmayan türetilmiş bir erişimciye entity'de bean adı verme; `Conversation.hasParticipant` ve
`isGroup` de aynı sebeple fiil biçiminde.

**`ApiConventionTest` bir kural daha koruyor.** `noCompanyScopedRuleIsLockedToThePlatformAdminRole`,
`/companies` ya da `/contact-invitations` altındaki hiçbir kuralın yalnız `ADMIN` rolüne kilitlenmediğini
doğruluyor — şirket yetkisi servis katmanında denetlenir. `ADMIN_RULE` #72 ile `hasAnyRole("ADMIN")`
yazımını da görecek biçimde genişletildi; genişleyince `PLATFORM_ADMIN_ONLY_RULE` ile birebir aynı desen
oldular ve tek bir `ADMIN_ONLY_RULE` sabitinde birleştiler. Aynı kuralı iki ayrı desenle taramak,
desenlerden birinin sessizce geride kalması demekti.

## 2s3. Davetin kullanıcıya ulaşması — `IN_APP` kanalı ve e-posta konuları

§2s2 daveti *okunabilir* yaptı; bu bölüm daveti **ulaşılabilir** yapıyor (is-koala/koala#41). Öncesinde
davet gönderiliyor ama davet edilen kişiye hiçbir yoldan ulaşmıyordu:

- `inviteUserToCompany` yalnız `notificationService.sendEmail` çağırıyordu, yani kayıt `NotificationChannel.EMAIL`
  olarak yazılıyordu; `GET /users/me/notifications` ise **yalnız `IN_APP`** döndürüyor (§2s). Uygulama içi
  bildirim listesi davetten hiç haberdar olmuyordu.
- Davet e-postası *"Daveti Koala uygulamasındaki bildirimler bölümünden kabul edebilir ya da reddedebilirsin"*
  diyordu; orada hiçbir şey yoktu. `CompaniesController` de Swagger'da *"uygulama içi bildirim gider"*
  yazıyordu — gitmiyordu.
- Bildirime düşse bile tıklanamazdı: `NotificationTargetType` şirket ya da davet tanımıyordu.

**`NotificationTargetType.COMPANY_CONTACT_INVITATION` eklendi.** Enum `@Enumerated(EnumType.STRING)` ile
yazıldığı için veri göçü gerekmedi; yeni sabit yalnız yeni satırlarda görünüyor. Mobil ve web tarafındaki
karşılığı yazılmadan bu satır istemcide `none`'a düşer ve tıklanamaz kalır — is-koala/koala#40 ve
is-koala/koala-web#31'in listesinde.

**Davet artık e-postanın yanında bir akış kaydı da üretiyor:** alıcı davet edilen kişi, `subject` şirketin
adı, `body` i18n anahtarından çözülmüş metin, `targetType` yeni sabit, `targetId` davetin kimliği.
`NotificationManager.sendInApp` bu alanları zaten kaydediyordu. Sonuç: davet edilen kişi kimliği hiçbir
yerden kopyalamadan `GET /contact-invitations/{targetId}` ile davetine ulaşabiliyor.

**Fan-out `ActivityNotificationService`'e taşınmadı, desen yerinde uygulandı — gerekçesi şu.** O sınıfın her
ucu `notify(...)` üzerinden geçiyor ve **üç kanala birden** gönderiyor: in-app, push, e-posta. Davet ise
kendi şablonuna, kendi parametrelerine ve `DispatchPriority.HIGH`'a sahip özel bir e-posta gönderiyor.
`notify` çağırmak o e-postanın yanına ikinci bir düz metin e-postası ve bir push daha koyardı — yani
kullanıcı tek olay için üç yerine beş teslimat alırdı. `ActivityNotificationManager`'a yalnız in-app gönderen
bir istisna metot eklemek de o sınıfın tek kuralını (her olay üç kanala gider) bozardı. Bu yüzden yalnız
`inApp` bacağının deseni — `try/catch` içinde, `NotificationService` üzerinden — `CompanyContactInvitationManager`
içinde tekrarlandı. §2ae'nin aggregate sınırı korunuyor: başka aggregate'in DAO'suna uzanılmıyor.

**`try/catch`'in sınırı yazılı olsun.** Akış kaydının yazılamaması daveti ve e-postayı düşürmüyor; ama çağrı
`CompanyManager.inviteContactToCompany`'nin `@Transactional` sınırı içinde olduğu için hata bir veritabanı
hatasıysa transaction rollback-only işaretlenir ve yakalamak commit'i kurtarmaz. Aynı sınır
`ActivityNotificationManager` için de geçerli (§2s); ayrı bir kural icat edilmedi.

**Üç davet e-postasının konusu `null`'dı.** `SendEmailDto.setSubject` hiç çağrılmıyordu; `NotificationManager`
şablon adı verildiğinde konuyu `sendEmailDto.getSubject()`'ten okuyup `notification.setSubject(...)` ile
kaydettiği için hem gönderilen e-postanın konusu hem `notifications.subject` sütunu boş kalıyordu. Davet,
cevap makbuzu ve davet edene giden bildirim — üçü de artık i18n anahtarından gelen konu taşıyor; kabul ve ret
ayrı anahtarlar kullanıyor.

**E-posta şablonundaki cümle artık doğru.** *"Bildirimler bölümünden kabul edebilirsin"* bu değişiklikten
sonra gerçeği söylüyor, o yüzden metne dokunulmadı. **E-postaya bağlantı konmadı:** derin bağlantı sözleşmesi
üç depoyu birden ilgilendiriyor (`koala-mobile/docs/deep-links.md` kanonik host tablosu, `koala-web`'in
`AppDestination` birleşimi ve `.well-known` yolları) ve o sözleşme güncellenmeden bağlantı üretmek kırık bir
yol açardı.

**#52 bu işin önkoşuluydu.** `COMPANY_UPDATES` `ALERT` iken kullanıcı kategoriyi kapatabiliyordu ve
`sendInApp` de aynı kapıdan geçtiği için (§2at) yeni akış kaydı da susturulabilirdi — çıkmaz sokak aynen geri
gelirdi. Kategori `TRANSACTIONAL` olduğu için `isAllowed` satıra hiç bakmadan `true` dönüyor.

## 2s4. Şirket onay kuyruğu — ret ucu, gerekçe ve karar bildirimi

§2s onay ucunu kapatmıştı; kuyruğun geri kalanı açıktı (is-koala/koala-backend#62). Ret ucu yoktu, yani
reddedilmesi gereken bir başvuru kuyrukta **sonsuza kadar** kalıyordu; onay gerekçesi hiçbir yere yazılamıyordu;
`CompanyManager.approveCompany` `notificationService`'i **hiç çağırmıyordu**, yani şirket sahibi onaylandığını
öğrenemiyordu; ve kuyruk yalnız `approved` bayrağıyla süzülebiliyordu, adla ya da vergi numarasıyla aranamıyordu.

**Durum tek kolonda: `companies.approval_status`.** `is_approved` düşürüldü (`06-62` migration'ı `true` olanı
`APPROVED`, kalanını `PENDING` yazıp kolonu siliyor; rollback bloğu geri kuruyor). İki değerli bir bayrak üç
durumu anlatamaz ve bayrağı bırakıp yanına enum eklemek "iki gerçeklik" demekti — reddedilen şirketin
`is_approved` değeri de `false` olurdu, yani kuyruk sorgusu onu bekleyenlerin arasında görmeye devam ederdi.
İstemci sözleşmesi korundu: `CompanyDto.approved` ve `/users/me` içindeki `companies[].approved` hâlâ boolean,
ikisi de `CompanyApprovalStatus.isApproved()` üzerinden türetiliyor.

**Karar asimetrik: ret geri alınabilir, onay alınamaz.** `PENDING`/`REJECTED` → `APPROVED` serbest (şirket
belgesini düzeltip yeniden değerlendirilebilsin diye), `APPROVED` → `REJECTED` **409**
(`admin.company.approved.not.rejectable`). Gerekçe blast radius: reddetmek bugün hiçbir şeyi geri almıyor,
onayı geri almak ise şirketin açık ilanlarını, süren atamalarını ve emanetteki parasını ilgilendirir ve bu dilim
onların hiçbirini modellemiyor. Onayı sessizce `PENDING`'e çevirmek operatöre şirketin **durdurulduğunu**
düşündürürdü; oysa durdurmazdı (aşağıdaki bulgu). Gerçek "askıya alma" kendi issue'sunu hak ediyor.
İkinci ret de 409 (`admin.company.already.rejected`) — gerekçeyi güncellemek karar vermek değildir.

**Onay gövdesi isteğe bağlı, ret gövdesi zorunlu.** `ApproveCompanyDto.note` opsiyonel (`@Size(max = 1000)`),
`RejectCompanyDto.reason` `@NotBlank` + `@Size(max = 1000)` — doğrulama §2ah gereği DTO'da, yani eksik gerekçe
**400 + alan adı** döndürüyor. `approve` ucu `@RequestBody(required = false)` taşıyor: panel bugün gövdesiz
çağırıyor ve o çağrı kırılmadan çalışmaya devam ediyor. Karar `approval_note` + `approval_decided_at`
kolonlarına yazılıyor; `updated_at` bu iş için yeterli değildi, şirketin herhangi bir düzenlemesi onu kaydırır.

**Kararın bildirimi `ActivityNotificationManager`'da, §2s'in kuralı gereği.** `companyApprovalDecided(company)`
alıcıyı orada seçiyor: yalnız şirketin **`ADMIN`** yetkilileri: `STAFF` ilan açar, üyelik ve onay §2s'e göre
`ADMIN`'in işi. Kategori `COMPANY_UPDATES`, yani §2at'ye göre `TRANSACTIONAL` — tercih ekranından susturulamaz;
onaylandığını öğrenemeyen bir şirket sahibi ne yapacağını bilemezdi. Hedef `NotificationTargetType.COMPANY`,
`targetId` şirketin kendisi.

**Gerekçe bildirimin gövdesine konmadı, bilinçli.** Bildirim yalnız kararı söylüyor ("Şirket başvurunuz
reddedildi. Gerekçeyi şirket sayfasından görebilirsiniz"), gerekçe `GET /companies/{id}` üzerinden okunuyor.
İki sebep: operatörün serbest metni e-postaya ve push başlığına birebir girseydi, hem şablonun tonunu
kontrol edemezdik hem `activity-notice-template`'in tek parametreli yapısını olay başına özelleştirmek
gerekirdi (§2s'in "olay başına ayrı şablon yerine tek şablon" kararının tersi).

**Gerekçeyi kimin okuyabildiği yanıt düzeyinde ayrılıyor.** `GET /companies/{companyId}` **her oturumlu
kullanıcıya** açık (`hasAnyRole("USER","ADMIN")`), yani `approvalNote`'u mapper'a bağlamak operatörün
notunu herkese açardı. Mapper alanı `ignore` ediyor; `getCompanyDetail` şirketin yetkilisiyse **sonradan**
dolduruyor — `followerCount`/`following` ile aynı desen. `approvalStatus` herkese açık kalıyor: bir şirketin
onaylı olup olmadığı zaten `approved` ile de görünüyordu.

**Yönetim kuyruğu artık `CompanyDto` döndürmüyor, `CompanyReviewDto` döndürüyor.** İki sebep: gerekçe yukarıdaki
ayrım yüzünden ortak DTO'da duramaz, ve `CompanyDto` satır başına `companyContacts` + `images` çiziyordu — 20
satırlık bir kuyruk için sayfalamayı bozmadan çekilemeyecek iki koleksiyon (§2l). İnceleme satırı kuyruğun
istediği alanlardan ibaret: ad, vergi numarası, iletişim, tür, durum, gerekçe, karar anı, `createdAt` ve
`createdBy` (`SafeUserDto`).

**`createdBy` liste ucunda eager çekiliyor.** `CompanyDao.search` `@EntityGraph({"createdBy",
"createdBy.profilePicture"})` taşıyor; ikincisi olmadan `SafeUserDto.profilePictureUrl` satır başına bir sorgu
daha atardı ve `EagerFetchCoverageIT` zaten kırmızıya dönerdi (§2af). İkisi de to-one, yani sayfalama
veritabanında kalıyor.

**Süzgeç `approved` yerine `status`.** `GET /admin/companies?status=PENDING|APPROVED|REJECTED&query=...`,
varsayılan `PENDING`. Boolean bayrak kalsaydı reddedilen şirket `approved=false` kuyruğunda görünmeye devam
ederdi — issue'nun "kuyruktan düşsün" maddesi tam olarak bu. Varsayılanın `PENDING` olması, eski istemcinin
`?approved=false` çağrısının **hâlâ doğru** sayfayı almasını sağlıyor; `approved=true` çağıran bir istemci
varsa `status=APPROVED`'a geçmeli. `query` ad **ve** vergi numarasında arıyor, `cast(:query as string)`
deyimiyle (§2l), boş/boşluk gönderildiğinde süzgeç uygulanmıyor. Sıralama beyaz listesi `createdAt`/`name`
olarak duruyor ve `rejectUnknownSort` 400 döndürüyor (§2l).

**Bulgu: onay bugün hiçbir kapıyı kapatmıyor.** `approveCompany` dışında `isApproved()` çağıran **tek bir satır
yoktu**; `JobManager.createCorporateJob` şirketin onaylı olup olmadığına hiç bakmıyor. Onay ucunun Swagger
açıklaması "Onaylanana kadar şirket ilan yayınlayamaz" diyordu ve bu **yanlıştı** — açıklama gerçek davranışa
çekildi. Kapıyı gerçekten kurmak (onaysız şirketin ilan açamaması) mevcut akışları ve testleri etkileyen ayrı
bir ürün kararı; bu dilimde yapılmadı.

**Şirket belgesi kavramı eklenmedi.** Issue'nun beşinci maddesi ürün kararı olarak açık bırakılmıştı; karar
"hayır, bu dilimde değil": belge yüklemek hangi belge türlerinin isteneceği, kimin görebileceği ve KVKK saklama
süresinin ne olduğu sorularını açar (§2av'nin ayrımı). Galeri bugün galeri olarak etiketli kalıyor.

**Sabitleyen testler.** `CompanyAdministrationRulesTest` karar makinesini ve yönlendirmeyi birim düzeyinde
tutuyor (ret gerekçeyi yazar ve kuyruktan düşürür, onay notu kırpılmış olarak kaydeder, onaylanmış şirket
reddedilemez, reddedilmiş şirket iki kez reddedilemez ama onaylanabilir, iki karar da bildirim tetikler,
reddedilen karar hiçbir bildirim üretmez, bilinmeyen sıralama alanı 400, gerekçeyi yalnız şirketin kendi
insanı okur). `ActivityNotificationRulesTest` alıcı seçimini sabitliyor (yalnız `ADMIN` yetkilileri,
`COMPANY_UPDATES` kategorisi, akış kaydı şirketi gösteriyor, onay ile ret aynı cümleyi taşımıyor).
`CompanyApprovalIT` dokuz kuralı gerçek HTTP ve gerçek Postgres üzerinde doğruluyor — ret ucu, gerekçesiz ret
400, onaylanmış şirketin 409'u, gövdesiz onayın hâlâ çalışması, ada ve vergi numarasına göre arama, kuyruk
satırının `createdAt`/`createdBy` taşıması, bilinmeyen sıralama alanının 400 dönmesi, iki kararın da akışa
ayrı cümlelerle düşmesi ve gerekçenin yabancıya görünmemesi.

## 2s5. Personel liste araması — yedi uçta `query` (#254)

Personel panelinde yalnız `GET /admin/users` ve `GET /admin/companies` `query` alıyordu; atama, kimlik, IBAN,
hakediş, şikayet, uyuşmazlık ve hesap silme kuyrukları yalnız durum/sayfa süzgeci tanıyordu ve kayıt bulmak sayfa
sayfa gezmek demekti (13.09.2026 cihaz testi: "admin sayfalarında arama yok"). Yedi uç da artık aynı sözleşmeyle
`query` alıyor.

**Sözleşme tek yerde: `core/helpers/StaffSearchQuery.normalize`.** Değer kırpılır; kırpılmış hâli 2 karakterden
kısaysa (boş dahil) **süzgeç uygulanmaz** — `null` DAO'ya gider ve `cast(:query as string) is null` dalı her satırı
geçirir (§2l'deki mevcut kalıp); 100 karakteri aşarsa `ValidationException` → **400 `ADMIN_SEARCH_QUERY_TOO_LONG`**.
Uzunluk kırpmadan **sonra** ölçülür: iki yanı boşluklu 101 karakter geçer, 101 harf geçmez. Kural manager'da
uygulanıyor, DTO'da değil, çünkü ortada DTO yok — `@RequestParam String`; `PageableSanitizer.rejectUnknownSort`
ile aynı yer ve aynı istisna tipi. `users`/`companies`/`staff` aramalarının uzunluk kuralı **değiştirilmedi**
(oralarda hâlâ yalnız kırpma var); yedi uca eklenen kural yeni davranış, eskilerini bu turda daraltmak istemci
sözleşmesi kararı.

**Aranan alanlar uç başına (issue tablosunun asgarisi, iki yerde bilinçli genişletme):**

| Uç | `query`'nin arandığı alanlar |
|---|---|
| `GET /admin/job-assignments` | atama id, ilan başlığı, işveren (ilan sahibi) ve çalışanın ad-soyadı ile e-postası |
| `GET /admin/identity-verifications` | kullanıcının profil ad-soyadı ve e-postası, **belgede beyan edilen ad-soyad** (kuyruk satırı `IdentityReviewDto.firstName/lastName` olarak onu çiziyor; personel gördüğünü aramalı) |
| `GET /admin/bank-account-verifications` | hesap sahibinin ad-soyadı ve e-postası, doğrulama referansı |
| `GET /admin/payouts` | hakediş id, atama id, alıcının ad-soyadı ve e-postası |
| `GET /admin/reports` | şikayet edenin ve edilenin ad-soyadı ile e-postası, şikayete konu ilanın başlığı |
| `GET /admin/disputes` | uyuşmazlık id, atama id, tarafların ad-soyadı ve e-postası — çalışan, ilan sahibi **ve açan** (`openedBy` şirket ilanında ilan sahibinden farklı bir yetkili olabilir, §2p) |
| `GET /admin/account-deletions` | talep sahibinin e-postası |

Id alanları `cast(x.id as string) like lower(concat('%', :query, '%'))` ile aranıyor — tam UUID de önek de
bulunur, büyük harfli yapıştırma `lower` ile tolere edilir. Süzgeçler mevcut durum süzgeçleriyle `and` ile
kesişiyor (`AdminListSearchIT` her uçta ölçüyor); `GET /admin/job-assignments`'ın bugün durum süzgeci yok, orada
kesişecek tek şey sayfalama.

**Türkçe duyarsızlık `lower()` ile sağlanamıyordu — ölçüldü, `unaccent` eklendi.** Testlerin ve prod'un imajı
`postgres:17` (`en_US.utf8`, libc): `lower('İLKER') = 'ilker'` (simple case mapping, `İ → i`) ama
`lower('IŞIK') = 'işik'` — noktasız `ı` ile yazılan `ışık` **bulunamıyor**, çünkü libc `I`'yı Türkçe kuralla
değil İngilizce kuralla küçültüyor; `ş/s`, `ç/c`, `ğ/g`, `ö/o`, `ü/u` da hiç katlanmıyordu. Issue'nun istediği
"büyük-küçük ve Türkçe karakter duyarsız" bu yüzden `unaccent(lower(x)) like unaccent(lower(concat('%', :q, '%')))`
ile kuruluyor: `unaccent('İ') = 'I'`, `unaccent('ı') = 'i'`, `Işık`↔`ışık`↔`ISIK`, `Çağlayan`↔`caglayan` hepsi
eşleşiyor (`postgres:17` kabında ölçüldü; IT'de `sukru ozturk` → `Şükrü Öztürk`, `ışıl çağlayan` → `Işıl Çağlayan`).
Sıra `unaccent(lower(x))`: iki sıra da bu vakalarda aynı sonucu veriyor, seçilen sıra okunanı.

**Uzantı Liquibase ile geliyor (`2026/09/13-02-unaccent-extension.xml`, `CREATE EXTENSION IF NOT EXISTS unaccent`).**
`unaccent` PostgreSQL 13'ten beri **trusted** uzantı — veritabanının sahibi süper kullanıcı olmadan da kurabiliyor;
`postgres:17` imajı contrib'i taşıyor, Testcontainers ve Dokploy aynı imajı kullanıyor. Uzantı yoksa `CREATE
EXTENSION` açılışta patlar ve uygulama kalkmaz — çalışma anında her aramanın 500 vermesine tercih edildi.
`pg_trgm` **eklenmedi**: issue onu yalnız ölçüm gerektirirse istiyor, kuyruklar bugün binlerce satırın altında ve
`unaccent` STABLE olduğu için ifade indeksi zaten doğrudan kurulamazdı.

**HQL `unaccent`'i `UnaccentFunctionContributor` ile tanıyor** (`META-INF/services/org.hibernate.boot.model.FunctionContributor`,
`core/configs`). Kayıtsız bir fonksiyon HQL'de `function('unaccent', x)` ile çağrılabilirdi ama dönüş tipi `Object`
kalır ve `lower()`/`like` argüman doğrulamasından geçmezdi; contributor tek argümanlı, `STRING` dönen bir
fonksiyon olarak kaydediyor. **Uzantı ile contributor birlikte yaşıyor:** biri kalkarsa arama sorguları açılışta
değil ilk istekte kırılır — ikisini birden düşürmek gerekir.

**`users`, `companies` ve `staff` aramaları bu turda `lower()` ile kaldı — bilerek ve bir test yüzünden.**
Üçünü aynı katlamaya çekmek denendi: `CompanyApprovalIT.theQueueIsSearchableByNameAndByTaxNumber` `yalanci`
sorgusunun `Yalancı Kahve Ltd.`'yi **bulmamasını** açıkça sabitliyor, yani şirket aramasında katlamanın yokluğu
yazılı bir beklenti. Issue'nun kapsamı yedi uç; kapsam dışı bir ucun davranışını değiştirip onun testini de
düzeltmek bu PR'ın kararı olamazdı. Sonuç panel içinde iki arama davranışı: yedi kuyruk `Işık`'ı `ışık` ile
bulur, kullanıcı/şirket/personel araması bulmaz. Üçünü hizalamak (ve o testin beklentisini çevirmek) ayrı bir
karar; mekanizma hazır — DAO'daki `lower(x)`'i `unaccent(lower(x))` yapmak yeterli.

**Soft-delete edilmiş ilişkiler satırı düşürmüyor, yalnız eşleşmiyor.** Her arama JPQL'inde ilişkiler açık
`left join` ile bağlanıyor (`left join p.worker worker`); Hibernate 7 `@SQLRestriction("is_deleted = false")`'ı bu
join'in **ON** cümlesine yazıyor, yani hesabını silmiş işçinin hakedişi `query` yokken kuyrukta kalıyor (§2y3'ün
#231 kuralı) ve `query` varken yalnız ad/e-posta dalı eşleşmiyor — id ile hâlâ bulunuyor. Örtük yol (`p.worker.email`)
`nullable = false` FK'da **INNER** join üretir ve satırı düşürürdü; bu yüzden hiçbir arama dalı örtük yol
kullanmıyor. Yükleme grafikleri değişmedi: `PayoutDao.searchForStaff` yalnız `bankAccount` çekiyor, alıcı hâlâ
`UserService.getAllByIdIn` ile ikinci toplu sorguda geliyor; `PayoutQueueIT`'in "sayfa başına 3 statement" ölçümü
ve `PayoutSettlementReconciliationIT`'in "komşuları gitmiş satır kuyrukta kalır" kuralı olduğu gibi geçiyor.

**`IdentityVerificationDao`'nun üç türetilmiş listesi tek `search(status, query, pageable)` oldu.** Durum
süzgeci `cast(:status as string) = 'PENDING' and ...` dallarıyla JPQL'de; `EagerFetchCoverageIT`'in üç muafiyeti
tek satıra indi. `AccountDeletionRequestDao.findAll/findAllByStatus` de tek `search`'e indi ve DTO kullanıcıyı
çizmediği için grafiksiz — `CollectionQueryWithoutGraphTest` allowlist'inde gerekçesiyle. `ReportDao.search`'ün
`@EntityGraph`'ı sorgunun **altına** alındı; tarayıcı 400 karakterlik pencerede artık grafiği görüyor ve
`ReportDao.search` allowlist'ten düştü — kural değişmedi, görünürlüğü düzeldi.

**İzin yüzeyi değişmedi.** Yedi uç aynı `@RequiresPermission` ile duruyor; `query` yalnız satırları daraltıyor,
kimin görebildiğini genişletmiyor (`AdminListSearchIT.searchDoesNotWidenWhoMayReadAQueue`: güven dalı finans
kuyruklarını, finans dalı güven kuyruklarını `query` ile de göremiyor). `query` §2av'nin 5. kuralı gereği **filtre**
sayılıyor: `query` taşıyan her liste isteği denetim satırı yazıyor, süzgeçsiz sayfa çevirme yazmıyor — arama
metninin kendisi kişisel veri olabilir, gerekçe `/admin/users` için yazılanla aynı.

**Panel tarafı is-koala/koala-admin#94.**

## 2s6. Personel detay uçları — hakediş, ödeme, ilan ve zengin atama (#255)

Panel listelerden detaya inemiyordu (13.09.2026: "ödeme detayı yok"): `GET /admin/payouts` vardı ama tekil hakediş
yoktu, ödeme denemeleri yalnız kullanıcı ucundaydı (`GET /job-assignments/{id}/payments`, taraf kontrolü
`securityService` üzerinden — personel için çağrılamaz, §0b), ilan uçları hiç yoktu ve `AdminAssignmentDto` bağlı
kayıtları özetlemiyordu. Beş uç eklendi, altıncısı zenginleşti:

| Uç | İzin | DTO |
|---|---|---|
| `GET /admin/payouts/{payoutId}` | `payout.reconcile` (facade üzerinden, liste gibi) | `AdminPayoutDetailDto` |
| `GET /admin/job-assignments/{assignmentId}/payments` | `payment.read` | `List<AdminPaymentDto>` |
| `GET /admin/payments/{paymentId}` | `payment.read` | `AdminPaymentDto` |
| `GET /admin/jobs?status=&query=` | **`job.read`** (yeni) | `PageDto<AdminJobDto>` |
| `GET /admin/jobs/{jobId}` | `job.read` | `AdminJobDetailDto` |
| `GET /admin/job-assignments/{assignmentId}` | `payment.read` (değişmedi) | `AdminAssignmentDetailDto` (eski alanlar + `payments`, `payout`, `disputes`, `reports`) |

**Bağlı kayıt özetleri tek şekil: `LinkedRecordDto(id, status, createdAt)`.** Atama detayı ödeme denemelerini,
hakedişi (yoksa `null`), uyuşmazlıkları ve şikayetleri bununla listeler; panel her satırdan kendi detay ucuna
bağlanır. Şikayet kapsamı **bu atamanın ilanına** bağlı ve **taraflardan birinin** açtığı şikayetler
(`job = ilan` ve `reporter ∈ {işçi, ilan sahibi}`). İlan hakkında üçüncü kişinin şikayeti ilan detayına aittir;
aynı iki tarafın **başka bir ilandaki** şikayeti de o atamaya girmez (`AdminDetailReadIT` ikisini de ölçüyor). Liste satırı `AdminAssignmentDto` **değişmedi**; detay ayrı kayıt tipi, çünkü
özetler sayfa başına dört ek sorgu demek ve liste onları çizmiyor.

**Bileşim `AdminDetailManager` orkestratöründe** (`MyWorkManager`/`StatisticsManager` deseni, §2al/§2am): hiçbir
DAO'ya uzanmaz, `JobAssignmentService.getDetailedById` + `PaymentService.getAttemptsForStaff/getForStaff` +
`PayoutService.findForAssignment/getRefundsForPayments` + `DisputeService.findAllForAssignment` +
`ReportService.findAllBetweenAssignmentParties` çağırır. `JobAssignmentManager`'a konamazdı —
`JobAssignmentManager → DisputeService → JobAssignmentService` döngüsü (§2ae); `PaymentManager` iadeleri
`RefundDao`'dan okuyamazdı — o `PayoutManager`'ın. Yeni servis metotlarının hiçbiri `securityService`'e uzanmıyor
(`AdminStaffPrincipalGuardIT` bir seviye tarıyor, ikinci seviye elle tarandı). `JobAssignmentService.getAdminDetail`
silindi; tek çağıranı bu uçtu.

**Hakediş detayı `PayoutManager.getStaffDetail`'de ve kuyrukla aynı kurallara uyuyor (§2y3).** Grafik yalnız
`bankAccount` + `payment` (ikisi de opsiyonel); `worker` grafikte yok ve `UserService.getAllByIdIn` ile ayrıca
okunuyor — hesabını silen işçinin hakedişi 404 değil `worker: null` ile döner. Atama `JobAssignmentDao.
findWithDetailsForPaymentById` ile ayrıca yükleniyor (`PayoutManager → JobAssignmentDao` mevcut muafiyet).
`settlementTasks` = hakedişin ödemesine bağlı `settlement_tasks` satırları (`SettlementService.getTasksForPayment`:
işlem, durum, `attempts`, `nextAttemptAt`, `lastError`, sağlayıcı referansları) — issue'nun "mutabakat kayıtları /
deneme geçmişi"; `paymentAttempts` atamanın ödeme denemelerinin `LinkedRecordDto` özeti. Denetim satırları
**bilerek yok**: `audit.read` ayrı bir `PERSONAL_DATA` izin, finans dalına o kapıyı buradan açmak görev ayrılığını
delerdi. IBAN `LogMasks.iban` ile maskeli, tam IBAN gövdeye girmiyor (`AdminDetailReadIT` ölçüyor).

**Ödeme DTO'su sağlayıcı referanslarını taşır, sırları taşımaz.** `conversationId`, `providerPaymentId`,
`providerTransactionId`, `verificationAttempts`, `nextVerificationAt`, tutar dökümü, `failureReason`, `refunds`
(`AdminRefundDto`: tutar, durum, gerekçe, `providerRefundId`). **`providerToken` ve `providerSubmerchantKey`
DTO'da yok** — ilki checkout oturumunun sırrı (§2k "provider token dahili korelasyon sırrıdır"), ikincisi alt üye
işyeri anahtarı; ikisi de panelin işine yaramaz. Liste ve tekil detay aynı DTO; iadeler sayfa başına tek
`findAllByPaymentIdInOrderByCreatedAtDesc` ile (`CollectionQueryWithoutGraphTest` allowlist'inde — DTO refund
satırından yalnız `payment.id` okur; `SettlementTaskDao.findAllByPaymentIdOrderByCreatedAtAsc` de aynı listede ve
tarayıcının 400 karakterlik penceresine komşu grafik girmesin diye dosyanın başında). **Ödeme DTO'sundaki
taraflar `SafeUserDto`** — kardeşi `AdminAssignmentDetailDto` ile aynı `payment.read` altında aynı şekil, e-posta
yok; e-posta yalnız `job.read` (`PERSONAL_DATA`) yüzeyinde ve issue'nun açıkça istediği hakediş detayında
(`StaffPartyDto(id, firstName, lastName, email)`, `payout.reconcile`).

**`job.read` yeni izin: `PERSONAL_DATA`, non-mutating, seed'de `SUPPORT_AGENT`** (`14-01-job-read-staff-permission.xml`;
`MODERATOR`/`DISPUTE_HANDLER`/`COMPANY_APPROVER`/`TRUST_LEAD`/`OWNER` kapsama yoluyla alır, finans dalı almaz —
görev ayrılığı korunuyor). Hassasiyet `PERSONAL_DATA` çünkü detay **tam adresi** ve işveren e-postasını taşıyor;
bireysel işverenin adresi ev adresidir. ADR-0003'ün yaklaşık konum kuralı kullanıcı yüzeyi içindir, personel
istisnadır: `AdminJobDetailDto.address` `AddressMapper.toAddressDto` ile **maskesiz** gider, `JobAddressVisibility`
hiç çağrılmaz. Sonucu §2av kural 3: her `/admin/jobs` isteği denetim satırı yazar. `user.read`'e (`NORMAL`,
`SafeUserDto`) yaslanmak e-posta ve adresi `NORMAL` bir iznin arkasına koyardı; `user.read_pii` yalnız `TRUST_LEAD`'de
ve ilanı destek görevlisi de görebilmeli.

**İlan araması `StaffSearchQuery` sözleşmesiyle (§2s5):** ilan id'si, başlık, şirket adı, işverenin ad-soyadı ve
e-postası, `unaccent(lower(...))`; `status` listesi verilmezse **her durum**. Grafik `category, creator, company,
company.address, address, address.city, address.district` — `Job.category` ve `Company.address` **eager** to-one
olduğu için grafiğe alınmadan satır başına ikincil select üretirlerdi; `creator.profilePicture`/`company.logo`
`EagerFetchCoverageIT` muafiyetinde (DTO çizmiyor). Başvuru ve atama sayıları sayfa başına iki `group by`
(`countByJobIds`, `countHiredByJobIds` **tüm** `AssignmentStatus` ile — `assignmentCount` işe alınanı değil her
atamayı sayar, `hiredCount` değil). `AdminDetailReadIT.theJobListIsBuiltWithoutAQueryPerRow` `size=1` ile `size=2`
sayfanın statement sayısını eşitliyor ve lazy fetch'i sıfırlıyor.

**Var/yok ve izin (§2d2).** Personel uçlarında taraf yok; gizlenecek şey izinsiz personele karşı varlık. Zincir
fail-closed olduğu için izinsiz aktör gerçek id'ye de rastgele id'ye de **aynı 403 zarfını** alır
(`AdminDetailReadIT.assertRefusedIdentically`, `timeStamp`/`requestId` düşülerek birebir); izinli aktör olmayan
kayda kaynağın kendi anahtarıyla 404 alır (`PAYOUT_NOT_FOUND`, `PAYMENT_NOT_FOUND`, `JOB_ASSIGNMENT_NOT_FOUND`,
`JOB_NOT_FOUND`). Hakediş detayı `StaffPayoutManager.getForStaff(actor, id)` facade'ından geçer (kuyrukla aynı iki
katman); diğer dördü `@RequiresPermission` ile yeterli, para hareketi yok.

**Panel tarafı is-koala/koala-admin#95.**

---

## 2s7. Personel DTO'ları — zaman çizelgesi, kullanıcı bağlamı, süzgeçler, karar veren personel (#283)

Panel taraması (14.09.2026): personel nesne sayfalarında başvuru→kabul→ödeme→mesai→tamamlanma→hakediş
çizelgesi kurulamıyor, `GET /admin/users/{id}` üç alanla bağlamsız, şirket nesnesinde yetkililer/ilanlar
yok, kararı veren personel hiçbir DTO'da görünmüyordu. Veri entity'lerdeydi; eksik olan DTO ve süzgeçlerdi.
Yeni yazma ucu **yok**; iki mevcut yazma ucu isteğe bağlı bir alan kazandı (aşağıda).

| Uç | İzin | Eklenen |
|---|---|---|
| `GET /admin/job-assignments/{id}` | `payment.read` | `AdminAssignmentDetailDto`: `createdAt`, `updatedAt`, `expectedCompletionDateTime`, `employerType`, `notes`, `application{id,status,applicationDateTime,answeredDateTime,rejectionReason}`, `workSessions[]` (`WorkSessionDto`, plausibility bayraklarıyla), `cancellation{id,cancelledByParty,cancelledBy(SafeUserDto),reason,noticeMinutes,feeRate,feeAmount,refundAmount,refundId,cancelledAt}`, `reviews[]{id,authorId,subjectId,rating,comment,anonymous,createdAt}` |
| `GET /admin/job-assignments` | `payment.read` | `workerId`, `employerId`, `jobId` süzgeçleri |
| `GET /admin/users/{id}` | `user.read` (+ `user.read_pii` → e-posta/telefon) | yanıt `SafeUserDto` → **`AdminUserDetailDto`** |
| `GET /admin/jobs` | `job.read` | `companyId`, `employerId` süzgeçleri |
| `GET /admin/jobs/{id}` | `job.read` | `AdminJobDetailDto`: `cancelledAt`, `cancelReason`, `creator` (`SafeUserDto`) |
| `GET /admin/companies`, `/{id}`, `approve`, `reject` | `company.approve` | `CompanyReviewDto`: `companyContacts[]`, `website`, `description`, `approvedByStaffId`, `jobCount` |
| `GET /admin/payments/{id}`, `/admin/job-assignments/{id}/payments` | `payment.read` | `AdminPaymentDto.payoutId`; `refunds[].origin` (`CANCELLATION|DISPUTE|STAFF`), `refunds[].originId` |
| `GET /admin/payouts/{id}` | `payout.reconcile` | `AdminPayoutDetailDto`: `bankAccountId`, `releaseOrigin`, `settledAt`, `reconciledByStaffId` |
| `GET /admin/payouts` | `payout.reconcile` | `workerId` süzgeci |
| `GET /admin/disputes`, `/{id}`, `resolve` | `dispute.resolve` | `DisputeDto`: `openedBySide` (`WORKER|EMPLOYER`), `resolvedByStaffId` (yalnız personel görünümünde) |
| `GET /admin/disputes` | `dispute.resolve` | `userId` süzgeci (işçi, ilan sahibi ya da açan) |
| `GET /admin/reports`, `/{id}`, `review` | `content.moderate` | `AdminReportDto`: `jobAssignmentId`, `reviewedByStaffId` |
| `GET /admin/reports` | `content.moderate` | `reporterId` süzgeci (mevcut `reportedUserId`'nin yanına) |
| `GET /admin/account-deletions`, `/{id}`, `reject` | `user.read_pii` / `user.deletion.manage` | `AccountDeletionDto`: `rejectionReason`, `rejectedByStaffId`, `blockers[]{type,id}` |
| `GET /admin/identity-verifications`, `/{id}`, `approve`, `reject` | `identity.review` | `IdentityReviewDto.reviewedByStaffName` |
| `GET /admin/bank-account-verifications` | `bank.account.verify` | `IbanPendingDto.status`, `attemptsLeft` |

**`GET /admin/users/{id}` PII ayrımı tek uçta, iki izinle.** Uç `user.read` ile korunur; `email` ve `phoneNumber`
yalnız aktör `user.read_pii` taşıyorsa dolar, taşımıyorsa **aynı gövde** `null` ile döner — ayrı bir uç açılmadı
çünkü panelin "PII izinli bölüm"ü aynı sayfadadır (koala-admin#84). Denetim izi bunun için dürüst kalmalıydı:
PII döndüğünde `AdminDetailManager.getUserDetail` `StaffAuditContext.reclassify(USER_READ_PII, "user.read_pii", true)`
çağırır, yani satır `PERSONAL_DATA` sınıfında ve o izin koduyla yazılır; PII dönmeyen okuma `user.read` olarak kalır
(`AdminDetailReadIT.theUserDetailCarriesContextAndRevealsContactDetailsOnlyWithThePiiPermission` iki satırı da
ölçüyor). §2p'deki uyuşmazlık çözümünden sonra `reclassify`'ın ikinci çağıranı budur. `counts` sekiz sayaç
(`applications`, `assignmentsAsWorker`, `assignmentsAsEmployer`, `payments`, `payouts`, `disputes`,
`reportsReceived`, `reportsMade`) ve her biri kendi servisinden tek `count` sorgusu; tekil detay olduğu için çarpan
yok. `companies[]{id,name,role}` `CompanyContactService.getMembershipsOf`, `badges[]` `BadgeService.getBadgesOf`,
`identityReviewStatus` kullanıcının **son** kimlik incelemesinin durumu (`IdentityReviewDto.statusOf` — kuyruk
satırıyla aynı kural), `pendingDeletionRequestId` `REQUESTED` durumdaki son talep.

**Bileşim yine `AdminDetailManager`'da (§2s6).** Kullanıcı detayı, şirket inceleme sayfası ve ödeme/iade
kaynağı orada kuruluyor; orkestratör hiçbir DAO'ya uzanmıyor, `Timesheet/Cancellation/Review/CompanyContact/
Badge/IdentityDocumentReview/Legal/Company/Job` servislerinin yeni `*ForStaff`/`count*`/`find*` metotlarını çağırıyor.
Bu metotların hiçbiri `securityService`'e uzanmıyor (`AdminStaffPrincipalGuardIT`). Şirket incelemesi
`CompanyManager`'a konamazdı: `jobCount` `JobService`'ten gelir ve `JobManager → CompanyService` bağı zaten var
(§2ae döngü). `approve`/`reject` uçları `companyService.approveCompany/rejectCompany`'yi **doğrudan** çağırmaya devam
ediyor (`AdminStaffPrincipalGuardIT` o dördünü tarama içinde arıyor) ve yanıtı `adminDetailService.getCompany`
ile yeniden okuyor.

**Şirket yetkilileri ayrı sorguyla geliyor, grafiğe alınmıyor.** `Company.contacts` ve `Company.images` ikisi de
`List` (bag); ikisini aynı `@EntityGraph`'e koymak `MultipleBagFetchException`. `CompanyDao.search` grafiği
`images`'ı zaten taşıyor; yetkililer sayfa başına tek `CompanyContactDao.findAllByCompanyIdIn` (grafik `user`,
`user.profilePicture`) ve ilan sayıları tek `JobDao.countByCompanyIds` `group by` ile geliyor. `approvedByStaffId`
yeni `companies.approved_by_staff_id` sütunu; **onayda da redde de** kararı veren personel yazılır (alanın adı
issue sözleşmesinden, anlamı "kararı veren"). Köprü aktörüyle verilen kararda `null`.

**İade kaynağı okuma anında türetiliyor, sütun yok.** `Refund` tablosunda kaynak alanı yok ve eklemeye gerek
kalmadı: `PaymentStateMachine.canRefund` yalnız `HELD_IN_ESCROW`'dan izin verdiği için bir ödemenin **en fazla bir**
iadesi olur. Sıra: `cancellations.refund_id` iadeyi gösteriyorsa `CANCELLATION` + iptal id'si (kesin, yabancı
anahtar); değilse atamanın **`RESOLVED` + `REFUND_EMPLOYER`** uyuşmazlığı varsa `DISPUTE` + uyuşmazlık id'si (uyuşmazlık
çözümü iadeyi zorunlu yarattığı için o iade budur); ikisi de değilse `STAFF` + `null` (`POST /admin/job-assignments/
{id}/refund`). Sayfa başına üç ek sorgu (iadeler, iptaller, uyuşmazlıklar) ve hakediş; `thePaymentListIsBuiltWithoutAQueryPerAttempt`
deneme sayısıyla büyümediğini ölçüyor. `payoutId` atamanın hakedişi bu ödemeye bağlıysa dolar, diğer denemelerde `null`.

**Hakediş kaynağı ise sütun: `payouts.release_origin` (`PayoutReleaseOrigin`: `AUTO_COMPLETION|EMPLOYER_COMPLETION|DISPUTE|STAFF`).**
Türetilemezdi — zamanlayıcının ve personelin çıkışı aynı izi bırakır. `PayoutService.releaseToWorker(id, origin)`
dört çağıranından açıkça alır: `JobAssignmentManager.autoCompleteAssignment` → `AUTO_COMPLETION`, `completeAssignment`
(işveren yolu, `releaseToWorkerUnlessDisputed` üzerinden, #289) → `EMPLOYER_COMPLETION`, `DisputeManager`
→ `DISPUTE`, `PayoutsController` → `STAFF` (`StaffPayoutService.releaseToWorker(actor, id, reason, origin)`). Sütun
`VARCHAR(32)`, CHECK kısıtı yok; yeni değer için changelog gerekmedi.
Mevcut satırlar için changelog `RESOLVED`+`RELEASE_TO_WORKER` uyuşmazlığı olan hakedişleri `DISPUTE` yazar,
kalanlar **`null` kalır** — geçmişte personel mi zamanlayıcı mı çıkardı bilinmiyor, uydurulmadı. `reconciledByStaffId`
yeni `reconciled_by_staff_id` sütunu, `PayoutService.confirmSettlement(id, evidence, reconciledByStaffId)` ile
facade'dan gelen aktörle yazılır. `settledAt` **ayrı sütun değil** `completedAt`'in kendisi: hakedişte `completed_at`'i
yazan tek yer mutabakat onayı ve oraya ekstredeki `settledAt` yazılır; ikinci bir sütun sessizce ayrışırdı (§2d2'nin
kusur sınıfı). `bankAccountId` grafikteki `bankAccount`'tan.

**İlan iptali iz bırakıyor.** `jobs.cancelled_at` iptalde damgalanır; `cancel_reason` için `POST /jobs/{id}/cancel`
**isteğe bağlı** gövde kazandı (`CancelJobDto.reason`, en çok 500; `{job.cancel.reason.too.long}`) — yeni uç değil,
mevcut ucun gövdesi. İstemciler göndermezse `cancelReason` `null` kalır; mobil/web'in göndermesi ayrı iş.
`creator` `AdminJobDetailDto`'da `SafeUserDto` (kullanıcı adı ve profil fotoğrafı; `employer` alanı e-postalı
`StaffPartyDto` olarak duruyor, ikisi aynı kişi). `AdminDetailReadIT.theJobDetailNamesItsCreatorAndCancellation` iptali
SQL ile değil `POST /jobs/{id}/cancel` ile yapar: gövdesiz → `cancelReason` null, boşluk gerekçe → null, 501 karakter →
400 (`details[].field=reason`), dolu gerekçe → personel detayında görünür.

**Hesap silme talebi.** Ret artık `rejection_reason` + `rejected_by_staff_id` yazıyor; eskiden ret gerekçesi
`blocked_reason`'a yazılıyordu (bir mesaj anahtarı kolonuna serbest metin). Changelog `REJECTED` satırlarda gerekçeyi
taşır ve `blocked_reason`'ı boşaltır; `LegalService.rejectDeletionRequest(actor, id, reason)` aktörü alır.
**Katılımcı sözleşmesi değişmedi (§2m):** `toAccountDeletionDto` `REJECTED` talepte `blockedReason`'ı ret gerekçesiyle
doldurur, çünkü mobil yalnız o alanı okuyor. `blockers[]{type,id}` **yalnız `REQUESTED`** talepte ve **canlı**
hesaplanır: `LegalService.findDeletionBlockers` sayfa başına dört sorgu (`PaymentDao.findPendingLinksForUsers`,
`JobAssignmentDao.findMoneyLinksForUsers`/`findActiveLinksForUsers`, `DisputeDao.findActiveLinksForUsers`), hepsi
`PartyLink(id, workerId, employerId)` projeksiyonu; tipler `DeletionBlockerType` (`PENDING_PAYMENT|ESCROW|ACTIVE_WORK|
DISPUTE`) ve her tip kendi mesaj anahtarını taşır. **Taksonomi tek yerde:** zamanlayıcının `findBlockedReason`'ı aynı
`blockersOf` sonucunun ilk engelinden türer; eski dört boolean predicate (`hasPendingPaymentForUser`,
`hasMoneyInEscrowFor`, `hasActiveWorkFor`, `hasActiveDisputeForUser`) ve DAO `exists*` sorguları silindi, etkin iş
durumları `JobAssignmentManager.ACTIVE_WORK_STATUSES` sabitinde. `blockedReason` personel görünümünde eski anlamını
koruyor: zamanlayıcının **son denemesinde** bulduğu ilk engelin metni. Kullanıcı yüzeyi (`LegalController`)
`rejectedByStaffId`'siz ve `blockers`'sız döner; personel yüzeyi `toStaffAccountDeletionDto`, bileşimi
`AdminDetailManager.deletionRequests` (sayfa başına tek `findDeletionBlockers`;
`AdminListSearchIT.theDeletionQueueResolvesBlockersWithoutAQueryPerRow`).

**Aynı DTO, iki görünüm: `DisputeDto`.** `openedBySide` açanın atamanın işçisi olup olmadığından türer
(`DisputeMapper.openedBySide`, `EvidenceUploaderSide` mantığıyla aynı). `resolvedByStaffId` yalnız `toStaffDisputeDto`'da
dolar; kullanıcının `GET /job-assignments/{id}/disputes` çağrısı personel id'si taşımaz. `AdminReportDto.jobAssignmentId`
şikayete konu ilanda, şikayet edenin ya da şikayet edilenin işçi olduğu **son** atama
(`JobAssignmentService.findLatestByJobsAndWorkers`, sayfa başına tek sorgu; §2s6'nın "atamanın şikayetleri" kuralının
tersi). `reviewedByStaffId` `Report.reviewedByStaff` proxy id'si. `IdentityReviewDto.reviewedByStaffName`
`AdminDetailManager.identityReviews` içinde `StaffAccountService.displayNamesOf` ile sayfa başına tek `findAllById`;
görünen ad boşsa personel e-postası (§2av'nin `actorDisplayName` kuralıyla aynı). Kimlik inceleme durumu kuralı
(`APPROVED`/`REJECTED`/`PENDING`) entity'de: `IdentityVerification.reviewStatus()`; DTO ve kullanıcı sayacı onu çağırır,
`IdentityVerificationDao.search` JPQL'i aynı üç koşulu metin olarak taşır (#254). Kullanıcı sayacı `disputes` ile
`/admin/disputes?userId=` aynı predicate'i kullanır: işçi, ilan sahibi **ya da açan** (`DisputeDao.countInvolving`). `IbanPendingDto.status` `BankAccountVerificationPolicy.effectiveStatus`,
`attemptsLeft` `BankVerificationProperties.maxAttempts`'e göre (`BankAccountService.verificationAttemptsAllowed`).

**Süzgeçler `StaffSearchQuery` sözleşmesine eklenir, onu değiştirmez.** Her id süzgeci `(:x is null or … = :x)` ile
sorguya girer, `status` ve `query` ile **kesişir** (`AdminListSearchIT.theIdFiltersNarrowEachQueueAndIntersectWithTheSearch`).
`AuditScope.hasFilter` sayfalama dışı her parametreyi süzgeç saydığı için id süzgeçli liste okuması denetim satırı
yazar (`anIdFilterIsAnAuditedSearch`); §2av kural 5 kendiliğinden kapsıyor. Bu turda `/admin/payments` diye bir
liste ucu açılmadı; kullanıcı detayındaki `payments` sayacı `/admin/job-assignments?employerId=` üzerinden atamaya
iniyordu — liste ucu #301 ile geldi, aşağıda.

**Panel tarafı is-koala/koala-admin#100.**

### Şirket kimliği, kullanıcı bağlamı, tarih süzgeçleri ve `/admin/payments` (#301)

Panel denetimi (14.09.2026, koala-admin#105/#108/#110/#111): şirket adı hiçbir personel DTO'sunda bağlanamıyordu,
kullanıcı sayfası kimlik incelemesini açtıramıyor ve IBAN/oturum bağlamı taşımıyordu, listelerde tarih aralığı
yoktu, takılı ödemeler görünmüyordu. Yeni yazma ucu **yok**.

| Uç | İzin | Eklenen |
|---|---|---|
| `GET /admin/job-assignments`, `/{id}`, `/admin/payments/{id}`, `/{id}/payments`, `/admin/payouts/{id}` | değişmedi | `companyId` (`companyName`'in yanına; bireysel ilanda ikisi de `null`). `StaffPayoutDto` liste satırına **eklenmedi**: satır ilanı hiç çizmiyor ve grafiğe `jobAssignment` almak §2y3'ün INNER join regresyonunu geri getirirdi |
| `GET /admin/users/{id}` | `user.read` | `identityReviewId` (son incelemenin id'si; `identityReviewStatus` ile aynı satırdan, `IdentityDocumentReviewService.findLatestFor`), `sessionSummary{activeSessionCount,lastLoginAt,lastDevicePlatform}` |
| `GET /admin/users/{id}/bank-accounts` | **`user.read_pii`** | `List<AdminBankAccountDto>`: `id`, `maskedIban` (`LogMasks.iban`), etkin `verificationStatus` (`BankAccountVerificationPolicy.effectiveStatus`), `primary`, `accountHolderNameVerified`, `verifiedAt`, `createdAt` |
| `GET /admin/bank-account-verifications` | `bank.account.verify` | `userId` süzgeci |
| `GET /admin/job-assignments` | `payment.read` | `status` (çoklu; verilmezse her durum), `from`/`to` |
| `GET /admin/payouts`, `/admin/reports`, `/admin/disputes` | değişmedi | `from`/`to` |
| `GET /admin/payments` | `payment.read` | **yeni**: `status` (çoklu `PaymentAttemptStatus`; verilmezse her durum), `PageDto<AdminPaymentDto>` |
| `GET /admin/finance/summary` | `finance.read` | `stuckPaymentCount`, `overduePayoutCount` |

**Oturum özeti `Session` tablosundan, cihaz kimliği olmadan.** `SessionManager.summarizeForStaff`: etkin oturum sayısı
`countByDevice_User_IdAndIsActive`, son giriş ve platform en yeni oturumun `createdAt`'i ve `device.type`'ı
(`findFirstByDevice_User_IdOrderByCreatedAtDesc`, grafik `device`). `clientDeviceId`, oturum/cihaz id'si, IP adresi ve
refresh hash'i DTO'ya girmiyor (`AdminDetailReadIT.theUserDetailNamesItsLatestIdentityReviewAndSummarisesSessionsWithoutDeviceIdentity`
gövdede beşini de arıyor). Tekil detay olduğu için iki sorgu çarpan üretmiyor.

**Banka hesabı listesi `user.read_pii` altında ve ham IBAN asla.** Uç `user.read` ile **korunmuyor**: kullanıcı
detayındaki e-posta/telefon gibi "aynı gövde, boş alan" seçilmedi çünkü IBAN'ın maskeli hâli bile hesap sahipliği
bilgisidir ve liste ayrı bir sayfa (koala-admin#108). `PERSONAL_DATA` olduğu için her okuma denetim satırı yazar
(`AdminDetailReadIT.theBankAccountsOfAUserAreMaskedAndOpenOnlyToThePiiPermission` satırın `user.read_pii` olduğunu ve
`SUPPORT_AGENT`/`FINANCE_OFFICER`'ın var olan ile olmayan kullanıcıya **aynı 403 zarfını** aldığını ölçüyor; izinli aktör
olmayan kullanıcıya `USER_NOT_FOUND_BY_ID` alır). Liste `findAllByUserIdAndActiveTrueOrderByPrimaryAccountDescCreatedAtAsc`
— kullanıcının kendi listesiyle aynı sorgu; silinen hesap `@SQLRestriction` ile zaten görünmez, `is_active=false`
yalnız anonimleştirmede yazılıyor. Bekleyen doğrulamanın referansı ve tutarı bu DTO'da **yok**; o bilgi
`bank.account.verify` yüzeyinin (`/admin/bank-account-verifications?userId=`) — iki izin iki yüzey, birleştirilmedi.

**Tarih süzgeci tek şekil: `CreatedAtRangeDto` (`@ParameterObject`) → `core/helpers/CreatedAtRange`.** Dört liste ucu
(`/admin/job-assignments`, `/admin/payouts`, `/admin/reports`, `/admin/disputes`) `from`/`to`'yu aynı parametre
nesnesinden alır (`Instant` + `@DateTimeFormat(iso = DATE_TIME)`; dilimsiz değer 400, §2l'nin `startFrom` kararı;
`OpenApiDocumentIT.theStaffListsDocumentTheSharedCreatedAtRange…` dördünde de düz `from`/`to` parametresi ve Türkçe
açıklama arıyor). Boş sınır `EPOCH`/`9999-12-31`'e düşer (`CreatedAtRange.of`); `StaffAuditManager`'ın kendi
sabitleri kaldırıldı, o da aynı yardımcıyı kullanır — sentinel tek yerde. JPQL'de
`x.createdAt >= :from and x.createdAt <= :to` — `:x is null` dalı yok, çünkü pgjdbc `Instant`'ı tipsiz gönderir (§2l).
**İki sınır da dahil**, `AuditLogController`'ın `<= :to` kuralıyla aynı: panelin `DateRangeFilter`'ı `to`'yu günün
`23:59:59.999`'u olarak gönderiyor (`istanbulDayBoundary`); `AdminListSearchIT.theCreatedAtBoundsAreInclusiveToTheMillisecond`
`to = createdAt`'in satırı aldığını, `to = createdAt − 1 ms`'nin almadığını ölçüyor. Finans özetinin yarı açık
aralığı **değişmedi**, o bir dönem toplamı. Ters aralık doğrulanmıyor, boş sayfa döner (denetim kaydıyla aynı).
Süzülen alan dört uçta da `createdAt`; `status`/`query`/id süzgeçleriyle `and` ile kesişir
(`theStatusAndCreatedAtFilters…`, `theCreatedAtRangeNarrowsPayoutsReportsAndDisputes`). Atama `status`'ü boşken
`AssignmentStatus.values()` bağlanır — `in :statuses` boş listeyi sevmez, `null` dalı da tipsiz parametre tuzağına
düşerdi. Sort allowlist'leri değişmedi; `PageableSortSafetyTest` `AdminDetailManager.searchPayments`'ı saf devretme
olarak muaf tutuyor (doğrulama `PaymentManager.searchForStaff`'ta: `createdAt`, `updatedAt`, `status`,
`nextVerificationAt`).

**`/admin/payments` liste ve tekil detay aynı DTO, aynı bileşim — ve liste sorgusu bilerek grafiksiz.**
`Payment.jobAssignment` ve `JobAssignment.worker` `nullable = false` + `@SQLRestriction` çifti taşır; grafiğe alınsalardı
Hibernate INNER join üretir ve hesabını silmiş (anonimleştirilmiş) işçinin **her** ödemesi — yerleşmiş `SUCCEEDED`
dahil, çünkü silme engeli yalnız bekleyen denemeye ve emanete bakar (§2s7 `blockers`) — listeden düşerdi;
`finance/summary.succeededPaymentCount` ile liste ayrışırdı. §2y3'ün hakediş kuyruğundaki çözümü uygulandı:
`PaymentDao.searchForStaff` yalnız `Payment` çeker (`jobAssignment` lazy proxy, `getId()` sorgusuz), atama özeti sayfa
başına tek projeksiyonla gelir (`JobAssignmentDao.findStaffSummariesByIdIn` → `StaffAssignmentSummary`: atama id, işçi
id, ilan id/başlık, işveren id, şirket id/ad; `a.worker.id` FK'dan okunur, `users`'a join yok), taraflar tek
`UserService.getAllByIdIn` ile (`worker`/`employer` → `SafeUserDto`); silinmiş kullanıcı haritada yoktur ve alan
**`null`** döner, satır ve ilan bilgisi kalır
(`AdminListSearchIT.aDeletedWorkersSettledPaymentStaysOnTheListWithItsAssignmentAndANullWorker`). Tekil `GET
/admin/payments/{id}` ve atama denemeleri aynı mapper'ı entity'den kurulan özetle (`LoadedAssignmentSummary.of`)
besler; oralarda `findForStaffById`/`getDetailedById` grafiği hâlâ `worker` taşır ve silinmiş işçide 404 verir —
tekil okuma, kural değişmedi. Bileşim atamalar arası: iadeler (`findAllByPaymentIdInOrderByCreatedAtDesc`), iptaller
(`findAllByRefundIds`), uyuşmazlıklar (`DisputeService.findAllForAssignments` →
`findAllByJobAssignmentIdInOrderByCreatedAtDesc`, `CollectionQueryWithoutGraphTest` allowlist'inde — dosyanın başında,
tarayıcının 400 karakterlik penceresine komşu grafik girmesin diye; `PaymentDao.searchForStaff` da aynı sebeple
dosyanın başında) ve hakedişler (`findForAssignments`) sayfa başına birer sorgu; `payoutId`/`origin` kuralları
§2s7'dekiyle aynı, yalnız atama başına haritalanıyor. İade → atama eşlemesi `refund.getPayment().getJobAssignment()`
ile **değil**, sayfadaki ödemelerden kurulan `assignmentByPayment` haritasıyla — proxy'yi açmak satır başına sorgu
olurdu. `thePaymentListIsFilteredByStatusRefusedToTheTrustBranchAndBuiltWithoutAQueryPerRow` `size=1` ile `size=3`'ün
statement sayısını eşitliyor. `status` verilmezse her durum (ilan listesiyle aynı); panelin "takılı" karosu
`status=VERIFY_PENDING&sort=createdAt,asc` ile en eskiyi başa alır. Güven dalı 403 (`payment.read` finans dalında),
`FINANCE_ANALYST` okur.

**Finans özetindeki iki sayaç dönemden bağımsız.** `stuckPaymentCount` = `VERIFY_PENDING` ve `next_verification_at is
null` (`PaymentDao.countByStatusAndNextVerificationAtIsNull`): doğrulama zamanlayıcısı yalnız `nextVerificationAt <= now`
satırlarını seçer (`findDueVerificationIds`), sütunu boş kalan deneme hiçbir zamanlayıcıya düşmez — "takılı"nın tanımı
budur, `INITIATED` tarafındaki `findStuckInitiationIds` ayrı bir mekanizma ve sayılmıyor. `overduePayoutCount`
`payoutService.findPayoutsAwaitingSettlementLongerThan(settlementProperties.getOverdueAfter()).size()` — §2y3'ün
göstergesi, runbook kuyruğu ve personel kuyruğuyla **aynı sorgu ve aynı eşik**; `FinanceManager` bunun için
`PayoutSettlementProperties`'i alıyor, ikinci bir gün sabiti yazılmadı
(`AdminFinanceSummaryIT.theSummaryCountsStuckVerificationsAndOverdueSettlementsRegardlessOfThePeriod` sayaç ile kuyruğun
aynı satırı gördüğünü ölçüyor). `stalled-after` kademesi özete alınmadı; panel karosu tek eşik istiyor.

**Panel tarafı is-koala/koala-admin#105, #108, #110, #111.**

---

## 2t. Iyzico mutabakat tetiği

`approvePaymentSettlement` yazılmıştı ama **hiç çağrılmıyordu** — emanetteki para Iyzico tarafında alt üye işyerine hiç açılmıyordu. Faz 8B bunu bağladı:

- `releaseToWorker` → sağlayıcıda `approve`, yani işçi payı açılır.
- `refundEmployer` → sağlayıcıda `disapprove`, yani iade edilen işte işçi payı açılmaz.

**Çağrının nasıl yapıldığı Faz 11B'de değişti — mekanik artık §2y'de.** Kısaca: senkron çağrı yerine `settlement_tasks` kuyruğu. Buradaki iki bayrak yerinde duruyor: `iyzico.settlementEnabled` hiç kuyruğa girilmemesini, `iyzico.settlement-retry.enabled` kuyruğun işlenmemesini sağlar. Ödemede `providerTransactionId` yoksa da kuyruğa girilmez ve uyarı loglanır.

## 2u. İş tamamlama — mutlu yolun kapalı halkası

**Hiçbir kod atamayı `COMPLETED` yapmıyordu.** `AssignmentStatus.COMPLETED` yalnızca *okunuyordu*: değerlendirme bırakmanın şartı, `PaymentStateMachine.canRelease`'in şartı ve şimdi rozetin şartı. Yani hiçbir iş bitirilemiyor, hiçbir değerlendirme yazılamıyor ve **emanetteki para hiçbir zaman işçiye çıkamıyordu**. Domain kapalı bir halkaydı.

`POST /job-assignments/{id}/complete` bunu açıyor: **ilan sahibi** (ya da şirket yetkilisi) işi tamamlanmış işaretler, `actualCompletionDateTime` damgalanır ve işçinin rozetleri yeniden değerlendirilir. Yalnızca `CONFIRMED`/`IN_PROGRESS` durumundan geçilebilir.

**İşveren tamamlaması da emaneti çıkarır (#289).** `completeAssignment`, `markCompleted`'ın hemen ardından aynı transaction içinde `payoutService.releaseToWorkerUnlessDisputed(id, EMPLOYER_COMPLETION)` çağırır (`payouts.release_origin` bu değeri taşır, §2s7) — otomatik tamamlamayla aynı sıra ve kilit (`findByIdForUpdate`). Canlıda bu çağrı yoktu: işveren onaylayınca atama `COMPLETED` oluyor ama para `HELD_IN_ESCROW`'da kalıyor, `payouts`/`settlement_tasks` satırı hiç açılmıyordu; otomatik tamamlama da yalnız `CONFIRMED`/`IN_PROGRESS` taradığı için o atamayı bir daha almıyordu. Açık uyuşmazlık varken tamamlama **yine olur**, yalnız çıkış atlanır ve emanet uyuşmazlık kararına kalır; bu dal `BLOCKS_RELEASE` fırlatıp tamamlamayı geri almaz. Uyuşmazlık kontrolü `JobAssignmentManager`'da değil (§2ae döngüsü), `PayoutManager`'daki "varsa atla" varyantındadır. Yanıt `paymentStatus=RELEASED_TO_WORKER` ve `payout{status=PENDING}` taşır; işçiye `assignmentCompleted` ve `payoutReleased` bildirimleri birlikte gider. İkinci tamamlama 409 `NOT_COMPLETABLE` — bunun için durum kontrolü emanet kontrolünün önüne alındı, yoksa çıkış sonrası `RELEASED_TO_WORKER` olan atama işverene yanlış yere `PAYMENT_NOT_SECURED` derdi (yetki kontrolü yine en önde, §2ar3). **İşçinin birincil IBAN'ı yoksa tamamlama da geri alınır:** `releaseToWorker` 409 `PAYOUT_WORKER_IBAN_NOT_VERIFIED` fırlatır, atama `CONFIRMED`/`IN_PROGRESS` ve para emanette kalır, payout açılmaz — otomatik yolla aynı ve bilinçli: `COMPLETED` olup parası sessizce takılan bir atama üretmektense işverene gürültülü ret; işçi IBAN girince tamamlama tekrar denenir.

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

**Tutar ve immutable snapshot doğrulanmıyordu.** Callback sonrası sağlayıcının döndürdüğü değerler bizim denememizle tam eşleşmeden emanet açılıyordu. Artık resmi response signature doğrulandıktan sonra `conversationId`, token, `basketId`, currency, `price`, `paidPrice`, tek item'ın `itemId`/`price`/`paidPrice`/`subMerchantPrice` değerleri ve provider işlem kimlikleri snapshot'la karşılaştırılır. Uyuşmazlık para çekilmiş olabileceği için terminal değildir: ödeme `VERIFY_PENDING` kalır ve log yalnız uyuşmayan alanın adını yazar.

**`fraudStatus` yok sayılıyordu.** Iyzico `0` derse ödeme dolandırıcılık incelemesindedir ve **sonradan geri çevrilebilir**; doküman "yalnızca 1 iken ürünü gönderin" diyor. Öncesinde `0` da `1` gibi işlenip emanet açılıyor, işçi işe başlayabiliyordu. Artık `1` → emanet, `0` → yeni `PaymentAttemptStatus.IN_REVIEW` (emanet açılmaz), `-1` → `FAILED`.

**`IN_REVIEW` otomatik yeniden sorgulanır.** Webhook tüketicisi hâlâ yok, fakat `PaymentVerificationScheduler` vadesi gelen kaydı aynı imzalı retrieve ucundan tekrar okur. Tavan dolarsa aktif kayıt silinmez ya da retryable yapılmaz; manuel mutabakata bırakılır.

**Para artık `double` değil.** `price`, `paidPrice`, `subMerchantPrice` `BigDecimal`'e çevrildi — §2e'nin kuralı buraya hiç uygulanmamıştı, oysa iyzico `price` ile sepet kalemlerinin toplamının **tam** eşitliğini arıyor.

**Hata gövdesi artık okunuyor.** `RestTemplate` 4xx'te exception fırlattığı için iyzico'nun `errorCode`/`errorMessage` gövdesi hiç ayrıştırılmıyordu; log'da yalnızca "400 Bad Request" görünüyordu. `HttpStatusCodeException` yakalanıp gövde okunuyor ve gerçek hata kodu loglanıyor.

**Exception yüzeyi §2ag'ye uyduruldu.** `RuntimeException` yerine `ExternalServiceException` (502). Ayrıca `retrieveCheckoutFormResult` **artık reddedilen ödemede exception atmıyor** — sonucu döndürüyor, kararı `PaymentManager` veriyor. Öncesinde "kart reddedildi" ile "sağlayıcıya ulaşılamadı" aynı catch'e düşüyordu.

**`conversationId` artık korelasyon taşıyor.** Approve/disapprove/refund çağrılarında rastgele UUID üretiliyordu; artık ödemenin kendi `conversationId`'si gidiyor, yani iyzico panelindeki kayıt bizdeki `Payment` ile eşleşiyor.

**Response signature ayrıca doğrulanır.** IYZWSv2 request imzası yanıtın doğruluğunu kanıtlamaz. Initialize için resmi `conversationId:token`, retrieve için resmi `paymentStatus:paymentId:currency:basketId:conversationId:paidPrice:price:token` sırası secret key ile HMAC-SHA256 doğrulanır; para değerleri resmi kurala göre sondaki sıfırlardan arındırılır. Initialize URL'si de yalnız configured loopback ya da ortamın resmi iyzico HTTPS hostlarından olabilir.

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

Bedeli açıkça yazılı olsun: **Checkout Form 3DS'siz bir ödeme geçirirse onu da reddederiz.** Bu bir varsayım olmaktan çıktı: **14.09.2026 sandbox'ta 3DS'siz SUCCESS geldi** (`paymentStatus=SUCCESS fraudStatus=1 mdStatus=null`, #276) — kapı tasarlandığı gibi kapalı düştü, deneme `VERIFY_PENDING`'de kaldı, emanet açılmadı, kart tarafında tahsilat yapıldı ve işveren aynı atamada yeni checkout açamadı. iyzico CF'nin 3DS'i hangi kurala göre seçtiğini yazmıyor (runbook §6.3); yazılı teyit beklemek para yolunu kapalı tutuyordu.

**Kalıcı çözüm `forceThreeDS`, kapı değil.** `IyzicoPaymentService.initiateCheckoutFormPayment` her CF initialize gövdesine `forceThreeDS: 1` yazar (`PaymentSessionRequestDto.forceThreeDS`, Java `Integer` — dört resmî SDK'daki tip). Anahtar `iyzico.force-three-d-secure` / `IYZICO_FORCE_3DS`, varsayılan `true`; `false` ise alan `@JsonInclude(NON_NULL)` ile gövdeden **tümüyle düşer**, `0` gönderilmez — semantiği yazılı olmayan bir alana `0` yazmak, bugün göndermediğimiz şeyi göndermek olurdu. Bayrak servis katmanında, çağıranda değil: `JobAssignmentManager` ya da yarınki başka bir çağıran unutamaz. Karar BIN bazlı değil, her zaman zorunlu. **Kapı bayraktan bağımsız aynı kalır**: `mdStatus == 1` beyaz listesi ve alan yokken `VERIFY_PENDING` — `IyzicoProtocolTest` gövdeyi (`$.forceThreeDS == 1`; kapalıyken alan yok), `PaymentCallbackTest` kapının değişmediğini sabitliyor. Bayrağın canlıda 3DS'i gerçekten zorladığı canlı işlemle doğrulanacak; doğrulanana kadar `mdStatus=null` satırı olaydır.

**Sıkışan denemenin yolu runbook §1'de** ("3DS'siz SUCCESS ile VERIFY_PENDING'de kalan deneme"): üretimde önce panelden `conversation_id` ile iade, sonra `PaymentManager.fail`'in yazdığının aynısını yazan `update payments … where status = 'VERIFY_PENDING'`. Atamaya dokunulmaz — `VERIFY_PENDING` `job_assignments.payment_status`'u hiç değiştirmez, `PENDING_COLLECTION` kalır ve `PaymentStateMachine.acceptsPayment` bunu (kod yolunun yazacağı `FAILED`'ı da) yeni ödeme için kabul eder. `StuckThreeDSecureAttemptRunbookIT` SQL'i belgeden okuyup gerçek Postgres'te koşturur: kapatmadan önce ikinci checkout `ATTEMPT_IN_PROGRESS`, kapattıktan sonra kabul.

**Tavan sessiz değil.** Bounded reconciliation aynı imzalı sonucu sekiz kez yeniden okuyup aynı karara varır; `scheduleReconciliation` tavanda `next_verification_at = null` yapmakla kalmaz, `Payment {} reached the automatic reconciliation ceiling … requires manual review` WARN satırı ve `koala.payment.operations{operation=callback,outcome=manual_review_required}` sayacını bırakır; sayaç `KoalaPaymentIntegrationFailure` kuralına dahil (`docs/runbooks/alerts.md`). `PaymentCallbackTest` satırı, `PaymentMonitoringTest` sayacı, `OperationalRunbookContractTest` kuralın seriyi izlediğini sabitliyor.

**Bir çelişki not edilmeli.** CF sorgulama yanıtının OpenAPI şeması (`/odeme-metotlari/odeme-formu/cf-entegrasyonu/cf-sorgulama`) `mdStatus`'u **saymıyor**; ama aynı doküman setindeki CF örnek yanıtı (`.../cf-ornek-entegrasyon`) `"mdStatus": 1` içeriyor ve resmî `iyzipay-java` SDK'sında `CheckoutForm`, `PaymentResource`'tan `getMdStatus()`'u miras alıyor. İkisi de iyzico. Alanın geleceğini varsayıyoruz ama şema garanti etmediği için okuma null'a dayanıklı ve karar fail-closed.

**Kapı dolandırıcılık dalından önce.** `mdStatus != 1` ve `fraudStatus == 0` birlikte gelirse ödeme `IN_REVIEW`'a geçmez, `VERIFY_PENDING` kalır. Tahsilat ihtimali dışlanamadığı için bu durum terminal yapılmaz; aktif deneme yeni checkout'u engeller ve bounded reconciliation aynı imzalı sonucu yeniden kontrol eder.

**Ret sebebi mesaj anahtarı, ham değer değil.** `payments.failure_reason` istemciye `GET /job-assignments/{id}/payments` ile aynen dönüyor (`PaymentAttemptDto`), yani sağlayıcı detayı için uygun bir yer değil. Kayda `{payment.3ds.verification.failed}` yazılıyor; sayısal `mdStatus` yalnız log satırında (`Payment {} rejected: 3-D Secure verification did not succeed, mdStatus={}`). Operatör için korelasyon yine `conversation_id`.

**3DS doğrulaması olmayan deneme terminal değildir.** `payments.status = VERIFY_PENDING`, `completed_at = null`; emanet açılmaz ve atamaya kırılım yazılmaz. Tahsilat ihtimali dışlanamadığı için işveren yeni checkout açamaz. Bounded reconciliation aynı imzalı sonucu yeniden kontrol eder, tavan dolunca satır manuel inceleme için aktif ve zamanlanmamış kalır. `SUCCEEDED` ve `FAILED` denemeler terminaldir; tekrar callback geldiğinde sağlayıcıya gidilmeden aynı sonuç döner (§2k).

**Sandbox'ta kısmen doğrulandı.** `4131111111111117` ile gerçek bir sandbox işlemi yapılmadı; `mdStatus=0` kuralı iyzico dokümantasyonuna ve sahte sağlayıcı yanıtlarına karşı sabitlendi (`PaymentCallbackTest`, `IyzicoProtocolTest`). Alan **yokken** kapalı düşme ise 14.09.2026 sandbox'ta gerçek bir işlemle görüldü (yukarıda).

## 2y. Mutabakat outbox'ı

Faz 8B'de sağlayıcı çağrısı `PayoutManager` içinden **senkron** yapılıyordu ve hata durumunda tüm işlem geri alınıyordu — yani `Payout` kaydı da yazılmıyordu ve **kimse yeniden denemiyordu**. Iyzico bir dakikalığına düşse para emanette kilitli kalırdı.

Artık `PayoutManager` sağlayıcıyı hiç çağırmıyor; `SettlementService.enqueue` ile `settlement_tasks` tablosuna bir kayıt bırakıyor. `SettlementRetryScheduler` beş dakikada bir vadesi gelenleri işliyor.

**Kuyruk ödeme+işlem başına tekil.** `(payment_id, operation)` kısmi benzersiz indeksi var; aynı ödeme için ikinci bir `APPROVE` açılmıyor, `enqueue` mevcut kaydı döndürüyor. Yani release iki kez çağrılsa da sağlayıcıya iki kez gidilmez.

**Üstel geri çekilme, tavanlı.** `initial-backoff` (1 dk) her denemede ikiye katlanır, `max-backoff` (6 saat) ile sınırlanır. `max-attempts` (8) aşılınca görev `FAILED`'a düşer ve **sessizce durur** — sonsuza kadar sağlayıcıyı dövmez. `FAILED` görevler elle incelenmek üzere tabloda kalır.

**`Payout` ve `Refund` durumunu sağlayıcı akışı belirler.** Release anında `Payout` `PENDING` yazılır, görev başarılı olduğunda `PROCESSING`'e geçer. İade `PENDING` (talep edildi) başlar; scheduler provider çağrısından hemen önce `RefundProgressManager` ile ayrı bir transaction'da `PROCESSING` (işleniyor) yapar. `REFUND` görevi başarılı olduğunda `Refund` `COMPLETED` olur ve `completedAt` damgalanır. **Görev deneme limitini aştığında ilgili kayıt `FAILED` işaretlenir.**

İade tarafındaki ayrı `PROCESSING` commit'i kullanıcı sözleşmesindeki “talep edildi → işleniyor → iade edildi” ayrımını gerçekten gözlenebilir kılar. Uygulama provider çağrısı sırasında kapanırsa refund `PROCESSING`, settlement görevi `PENDING` kalabilir; kalıcı görev aynı çağrıyı retry eder, tavan dolarsa `FAILED` ile kapanır.

**İşçi hakedişin nereye gittiğini `PayoutDto` üzerinden görür (#294).** Release anında seçilen birincil hesap `payouts.bank_account_id`'ye yazılır ve `GET /users/me/payouts` o hesabın maskeli IBAN'ını (`LogMasks.iban`, ilk 4 + `***` + son 4), `bankAccountId`'yi ve `APPROVE` görevinin yazdığı `provider_reference`'ı döndürür; `PENDING` satırda referans henüz yoktur ve `null` gider. Hangi alanların işçiye açık, hangilerinin personel kanıtı olarak kaldığı §2y3'te.

**`PayoutStatus.COMPLETED`'a giden tek yol banka mutabakatıdır (§2y3).** Sağlayıcı `approve` çağrısı yalnızca talimatın kabul edildiğini söyler; paranın işçinin banka hesabına geçtiğini söylemez. `PROCESSING` bu yüzden terminal değil, **kanıt bekleyen** bir durumdur.

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

## 2y3. Payout mutabakatı — `PROCESSING`'i `COMPLETED` yapan kanıt

**Sorun kovada değil kanıttaydı.** `SettlementManager` başarılı `APPROVE` görevinde payout'u `PROCESSING` yapıyordu ve orada bırakıyordu: `COMPLETED` yazan **hiçbir üretim yolu yoktu** (#101). Sonuç iki taraflıydı — işçi kazanç ekranında hiçbir hakedişi "tamamlandı" göremiyordu, ve `PROCESSING`'de takılan bir satırı kurtaran hiçbir mekanizma yoktu: yeniden denenecek bir sağlayıcı çağrısı yok (talimat zaten kabul edilmiş), o satırı seçen bir zamanlayıcı yok, runbook sorgusu yok, alarm yok. #128'deki takılı `INITIATED` denemesinin ödeme çıkışındaki birebir eşi.

### Kanıt nereden geliyor — üç aday, bugün fiilen bir tanesi var

Issue üç kaynak öneriyordu ve seçim depodaki gerçeğe göre yapıldı:

| Aday | Bugünkü durumu |
|---|---|
| Iyzico gönderim/rapor sorgulama ucu | **Yok.** `IyzicoEndpoints` yedi yol taşıyor (submerchant, CF initialize/retrieve, item approve/disapprove, refund, cancel); gönderim raporu yok. §2x denetimi dokümana karşı satır satır yapıldı, böyle bir uç eklenmedi. |
| Webhook | **Yok.** §2x'in kendi ifadesi: "Webhook tüketicisi hâlâ yok." |
| Kontrollü operasyon içe aktarımı | **Var.** Ekstre/rapor zaten insan eliyle okunuyor — runbook §4 bunu yordam olarak yazıyor. |

**Seçilen: personel onaylı ekstre satırı.** Gerekçe §2k2'nin IBAN sahipliği doğrulamasında verdiği gerekçenin aynısı ve o karar bu depoda zaten uygulanmış durumda: elimizde olduğu **doğrulanmamış** bir sağlayıcı yeteneğinin üzerine adapter yazmak, para yolunda çalıştığı hiç görülmemiş bir kod bırakmak olurdu. Iyzico sandbox anahtarları hâlâ yok (§0), ödeme akışı uçtan uca hiç denenmedi; uydurulmuş bir rapor ucu, `COMPLETED`'ı gerçek kanıta değil **varsayıma** bağlardı. Rapor ucu ya da webhook geldiğinde `PayoutService.confirmSettlement` imzası onu zaten kaldırıyor — tüketici aynı metodu çağırır, kural tek yerde kalır.

### Kanıtın dört bileşeni ve kapının yeri

`POST /admin/payouts/{payoutId}/reconcile` ekstre satırından dört şey ister: `settlementReference` (satırın kendi kimliği), `providerReference`, `amount`, `currency`, `settledAt`. `PayoutManager.confirmSettlement` payout satırını kilitler ve **hepsini mevcut snapshot'la karşılaştırır**; `providerReference` payout'a `markPayoutProcessing` sırasında Iyzico'nun `approve` yanıtından yazılmış olan korelasyon anahtarıdır. Tutar `BigDecimal.compareTo` ile (§2e), para birimi `Locale.ROOT` büyütmesiyle (§2ag) karşılaştırılıyor; `settledAt` payout kaydından **önce** olamaz — talimattan önce gerçekleşmiş bir transfer o talimatın transferi değildir.

**Girdi kuralı DTO'da tanımlı ama kapı metotta — §1'in "manager'da null/blank kontrolü yapılmaz" kuralının bilinçli istisnası.** Kurallar (`@NotBlank`, `@Size`, `@Digits`, `@DecimalMin`, üç harf currency ve kritik olarak `@PastOrPresent`) yalnız `ConfirmPayoutSettlementDto` üzerinde yazılı; ikinci bir kopya yok. Değişen şey **nerede işletildiği**: `confirmSettlement` satırı kilitlemeden önce `jakarta.validation.Validator` ile DTO'yu kendisi doğruluyor. Sebebi bu başlığın kendi seam iddiası — "rapor ucu ya da webhook geldiğinde tüketici aynı metodu çağırır, kural tek yerde kalır". Kural yalnız controller'daki `@Valid` ile işlerken bu iddia yanlıştı: HTTP dışı bir çağıran **gelecek tarihli** bir `settledAt`'i `completed_at`'e yazdırabiliyordu (`firstMismatch` yalnız alt sınırı biliyor, üst sınırı bilmiyor) ve null alanlarda `ValidationException` yerine **NPE** alıyordu. `@Valid` kaldırılmadı: HTTP çağıranı hâlâ `details[{field, message}]` ile alan alan geri bildirim alıyor (§2ah'nin kazancı), metottaki kapı ikinci çağıran için var. Doğrulayıcı ihlalleri alan adına göre sıralayıp ilkini atıyor, yani mesaj deterministik. `PayoutSettlementReconciliationTest`'in `aCallerThatNeverPassedThroughHttp…` testleri HTTP'yi hiç kullanmadan beş alanın hepsini ve gelecek tarihi ölçüyor.

**Uyuşmazlık terminal değil.** Eşleşmeyen kanıt `FAILED` yazmaz: "kanıt uymadı" ile "transfer olmadı" ayrı şeylerdir ve ikincisini yalnızca ekstrenin yokluğu söyler. Satır `PROCESSING` kalır, `koala_payment_operations_total{operation="payout_settlement",outcome="evidence_mismatch"}` artar ve log **hangi alanın** uymadığını yazar. Yön §2k'nin "tahsilat ihtimali dışlanamadıkça terminal yapma" kuralıyla aynı, sadece paranın diğer ucunda.

**Sağlayıcı referansı olmayan payout mutabakat edilemez, bilinçli olarak.** `dispatch` null dönerse payout `provider_reference`'sız kalır; karşılaştırılacak korelasyon anahtarı olmadan "bu ekstre satırı bu hakedişin" iddiası kanıtsızdır. 409 dönüyor ve satır kuyrukta kalıp yükseltiliyor — kanıtsız `COMPLETED` yazmaktansa görünür biçimde asılı kalması tercih edildi.

**İzin ayrı: `payout.reconcile` (`FINANCIAL`, mutating), seed'de yalnız `FINANCE_OFFICER`.** `payout.release` parayı emanetten **çıkarmayı** adlandırıyor, bu eylem ise ekstreyi okuyup gerçekleşmiş bir transferi kaydetmek — §2k2'deki `bank.account.verify` kararının aynısı, aynı gerekçeyle. Görev ayrılığı da bunu istiyor: talimatı veren ile "para gitti" diyen aynı izin olmamalı. Uç `/admin/**` altında olduğu için denetim kaydına giriyor (§2av).

### Garantiler veritabanında, uygulamada değil

Üç invariant'ın hiçbiri Java'ya emanet edilmedi:

- **`chk_payouts_completed_requires_evidence`** — `status = 'COMPLETED'` bir satır `settlement_reference`, `provider_reference` ve `completed_at` olmadan yazılamaz. Kabul maddesinin ("gerçek kanıt gelmeden `COMPLETED` olmaz") doğrudan karşılığı ve yarın eklenecek ikinci bir yazma yolunu da bağlar — §2k2'de `BankAccount.setIban`'ı entity'ye koyma gerekçesinin aynısı, bir seviye aşağıda.
- **`uk_payouts_settlement_reference`** — bir ekstre satırı en fazla bir hakedişi tamamlar. Toplu bir gönderim satırı çıkarsa operatör 409 alır ve yükseltir; yanılma yönü sessiz çift tamamlamaya değil, görünür çakışmaya doğru. **İndeks bilerek `is_deleted` taşımıyor** ve bu deponun diğer kısmi benzersiz indekslerinden (`uk_payments_one_active_attempt`, `uk_authorities_user_role`) ayrıldığı tek nokta: `WHERE ... AND is_deleted = false` yazılsaydı soft-delete edilmiş bir `COMPLETED` hakediş kendi ekstre satırını **serbest bırakırdı** ve aynı banka gönderimi ikinci bir hakedişi kapatabilirdi. Soft delete bir görünürlük kararıdır, para geri gelmez; bir gönderim satırı bir kez harcanır. Diğer indekslerde `is_deleted` doğru, çünkü orada kısıtın konusu "şu anda aktif olan" bir şeydir — burada konusu geçmişte gerçekleşmiş bir para hareketi. `aSoftDeletedPayoutDoesNotReleaseItsStatementLineForASecondPayout` bunu ölçüyor.
- **`chk_payouts_processing_requires_clock`** — `PROCESSING` bir satır `processing_at` olmadan yazılamaz. Bekleme saatinin kaynağı bu kolon; damgasız bir satır kuyruğa hiç girmezdi, yani "görünmez takılı hakediş" bu kapının kapattığı şey.
- **`idx_payouts_awaiting_settlement`** — kısmi indeks, kuyruk sorgusunun taradığı küme. Aynı zamanda kümenin SQL tarafındaki tanımı: `PayoutStatus.AWAITING_SETTLEMENT` ile **eşitliği** `PayoutSettlementReconciliationIT` ölçüyor (`contains` değil). #128'in dersi: küme iki yerde bilinirse ayrışır ve ayrışma para yolunda sessiz kalır.

**Küme dört yerde yazılı ve dördü birden bağlı.** Java listesi, indeks `WHERE`'i, runbook §4 adım 7 sorgusu ve `chk_payouts_processing_requires_clock`. İlk turda eşitlik yalnız Java↔indeks çiftini tutuyordu; enum'a yeni bir durum eklenip Java'ya **ve** indekse tutarlı yazıldığında runbook SQL'i elden geçirilmezse **bütün testler yeşil kalıyordu** — gösterge yeni satırları sayar, alarm yanar, operatörün koşacağı sorgu boş döner. `everyPlaceThatNamesTheAwaitingSettlementSetNamesTheSameSet` dördünü de aynı kümeye bağlıyor; runbook sorgusu belgeden okunup durum sabitleri ayrıştırılıyor. Beşinci yer olan **alarm**, saydığı satır kümesinin runbook sorgusunun döndürdüğü kümeye **eşit** olmasıyla bağlanıyor (`theOverdueGaugeSelectsTheSameRowsAsTheRunbookQueue`) — önceki hâli yalnız **sayıyı** karşılaştırıyordu ve iki farklı satır kümesi aynı sayıyı verebilirdi.

**Gösterge sorgusuna `settlement_reference is null` eklendi ve o satır bilerek doğrudan SQL ile kuruluyor.** Runbook sorgusu bu koşulu taşıyordu, gösterge taşımıyordu. Bugün fark üretmiyor: `confirmSettlement` referansı yazarken durumu da `COMPLETED` yaptığı için ekstre satırı taşıyan bir `PROCESSING` satırı **üretim yolundan oluşmuyor**, yani koşul yalnız sözleşmenin yarısıydı. Testi bu yüzden servisten geçmiyor, satırı `jdbcTemplate` ile kuruyor — ölçtüğü şey ikinci bir yazma yolu geldiği gün iki sorgunun ayrışmaması. Koşul silindiğinde kırmızıya döndüğü ölçüldü; servis üzerinden kurulan bir senaryoyla **dönmüyordu**, yani erişilemez bir dalı bağlamanın tek yolu buydu.

**İdempotentlik iki katmanlı.** Aynı `settlementReference` ikinci kez bildirilirse kayıt okunur ve **değiştirilmeden** döner — `completedAt` yeniden damgalanmaz, sayaç `already_settled` artar. Farklı bir referans zaten tamamlanmış bir hakedişe işlenmek istenirse 409; bu idempotentlik değil çakışmadır ve sessizce geçilmemeli. Uygulama kontrolü hızlı hata içindir, **doğruluğun kaynağı yine indekstir**: iki eşzamanlı transaction aynı referansı iki ayrı hakedişe yazmaya çalıştığında kaybedeni veritabanı reddediyor ve testi bunu `CyclicBarrier` ile örtüştürerek ölçüyor (§3'teki #128 dersi: kilit yarışı sıraya sokuyorsa test yalancı yeşildir). **Yarış `confirmSettlement` üzerinden koşuyor, DAO'ya doğrudan yazarak değil.** İlk hâli DAO'ya yazıyordu; indeksin ısırdığı kanıtlıydı ama `uk_payouts_settlement_reference` → `SETTLEMENT_REFERENCE_TAKEN` (409) eşlemesi **hiçbir testte koşmuyordu**, yani kullanıcının gördüğü cevap kanıtsızdı ve eşleme sessizce silinse test yeşil kalırdı. İki iş parçacığı barrier'ı transaction açıkken ve satırlarını okuduktan sonra geçiyor, sonra ikisi de servisi çağırıyor; kaybeden `PAYOUT_SETTLEMENT_REFERENCE_TAKEN` alıyor.

**Eşzamanlı yazma takımı ortak.** Barrier + iki iş parçacığı + kısıt adı okuma kalıbı `ActivePaymentAttemptConstraintIT` ile birebir aynıydı (Türkçe assert mesajına kadar); `com.exskylab.koala.support.OverlappingWrites`'a çıkarıldı ve iki IT de onu kullanıyor. Aynı turda iki kopya daha kapandı: `PaymentManager.hasConstraint` ile `PayoutManager.hasConstraint` tek bir `core/helpers/ConstraintViolations`'a indi, `OperationalRunbookContractTest`'teki "bu sayaç bir alarmla izleniyor mu" iddiası tek bir yardımcıya indi. Gerekçe: yan yana duran iki "süpür + alarm + runbook" mekanizması üç kopya üretmişti; üçüncüsü geldiğinde aynı üçü bir kez daha üretecekti.

### Takılı satırı ne kurtarıyor

**Hiçbir otomatik yol — ve bu bilinçli.** Sağlayıcı talimatı kabul etmiş durumda; yeniden denenecek çağrı yok, `settlement_tasks` görevi `SUCCEEDED`. Kaydı ilerleten tek şey ekstre satırıdır. Görünürlük bu yüzden #128'deki iki somut yolla kuruluyor ve **ikisi de testle bağlı**:

1. **Alarm.** `PayoutSettlementOverdueScheduler` saatte bir `payout.settlement.overdue-after`'dan (varsayılan 3 gün) eski `PROCESSING` satırları sayıp `koala_payout_settlement_overdue` göstergesine yazıyor; `KoalaPayoutSettlementOverdue` bunu izliyor. `OperationalRunbookContractTest` gösterge adını **kayıt defterinden okuyup** alarm dosyasında o seriyi arıyor, yani metrik adı değişirse ya da kural silinirse test kırmızıya döner.

   **Gecikme saati `processing_at`, `created_at` değil.** `payouts` tablosunda `PROCESSING`'e geçiş anı hiç tutulmuyordu; hem gösterge hem runbook sorgusu `created_at`'e bakıyordu. `created_at` ise `releaseToWorker`'ın `PENDING` satırını açtığı andır — `APPROVE` görevi outbox'ta 8 deneme ve 6 saat tavanla gecikebilir (§2y) **ve runbook §4 adım 1 operatöre kuyruğu dondurmasını söyler**. Yani "3 gündür banka kanıtı bekliyor" diye `severity: critical` sayfalanan bir satır `PROCESSING`'e bir dakika önce girmiş olabilirdi. Yön gürültüye doğruydu ama bu bir **para alarmı**: yanlış sayfalama operatörün alarma güvenini bitirir ve sonraki gerçek satır sessiz kalır. `processing_at` kolonu `markPayoutProcessing` içinde damgalanıyor, veritabanı damgasız `PROCESSING` yazdırmıyor, mevcut satırlar `created_at` ile dolduruldu (yanılma yönü gizlemeye değil görünürlüğe doğru) ve indeks de o kolona taşındı.

   **İki kademe var.** Tek eşikli bir gösterge 4 gün ile 90 gün takılı satırı aynı gösteriyordu ve ilk yükseltme sonuç vermediğinde söyleyecek yeni bir şeyi yoktu — alarm zaten yanıyor olduğu için sessizce susturulmuş bir kanalda 90 günlük bir hakediş yaşayabilirdi. `payout.settlement.stalled-after` (varsayılan 10 gün) ikinci göstergeyi (`koala_payout_settlement_stalled`) ve `KoalaPayoutSettlementStalled` alarmını besliyor; ilk müdahalesi farklı (Iyzico kaydını yeniden aç, finans sorumlusuna devret, issue aç) ve `alerts.md`'de ayrı satırı var — aynı müdahaleyi tekrar eden bir kademe yalnızca gürültü olurdu. Kademe **ayrı bir sorgu değil**: aynı kuyruk sorgusunun daha eski satırları, yani runbook'ta ikinci bir SQL bloğu doğmuyor. Sıra yapılandırmayla bozulamıyor: `stalled-after` `overdue-after`'dan büyük değilse uygulama açılışta düşüyor.
2. **Runbook kuyruğu.** `docs/runbooks/payments.md` §4 adım 7'deki SQL. `PayoutSettlementReconciliationIT` bu sorguyu **belgeden okuyup gerçek Postgres'te koşuyor**: vadesi geçmiş satırı döndürdüğünü, taze `PROCESSING` satırı ile mutabakat edilmiş satırı **döndürmediğini** iddia ediyor.

Sayaç yerine gösterge (`Gauge`) seçildi: soru "kaç kez oldu" değil, "**şu anda** kaç satır asılı". Her koşuda artan bir sayaç aynı satırı defalarca sayardı ve alarm satır kapandıktan sonra da yanardı.

**Eşik üç yerde aynı olmak zorunda ve üçü de testle bağlı.** `payout.settlement.overdue-after` `application.yaml` üzerinden `PAYOUT_SETTLEMENT_OVERDUE_AFTER` ile veriliyor (varsayılan 3 gün), zamanlayıcı onu okuyor ve runbook sorgusu aynı sayıyı yazıyor. `PayoutSettlementConfigurationTest` yaml'ı yükleyip anahtarın alana ulaştığını ölçüyor **ve** runbook metnindeki `interval` değerinin Java varsayılanıyla eşleştiğini doğruluyor: ayrışırlarsa alarm gelir ama operatörün koşacağı sorgu boş döner — #128'in "runbook'a yazdım ile sorgu satırı buluyor arasındaki fark" bulgusunun aynısı. `PayoutSettlementProperties` ayrıca `@Validated` + `@AssertTrue` taşıyor: sıfır ya da negatif pencere açılışı düşürüyor. §2aw'nin dersi — negatif bir pencere `Instant.now().minus(-n)` ile kesimi geleceğe taşır ve her `PROCESSING` satırını vadesi geçmiş sayardı; doğrulanmamış bir properties sınıfı bunu çalışma anında sessizce yapardı.

### Kuyruk satırı ekstre kanıtını taşıyor (#231)

**`GET /admin/payouts` (#211) satırı yalnız `PayoutDto` döndürüyordu ve panel operatörden sistemde zaten kayıtlı olan sağlayıcı referansını elle yeniden yazmasını istiyordu.** Ekstre satırı `providerReference` ile eşleştirildiğine göre (yukarıdaki dört bileşen) kuyruğun onu göstermemesi, insanı bir korelasyon anahtarını kopyalayıcı yapmaktı; alıcı ve banka bağlamı olmadan da "bu ekstre satırı bu hakedişin mi" sorusu güvenle cevaplanamıyordu. Kuyruk artık kendi DTO'sunu döndürüyor: **`StaffPayoutDto`** — `id`, `jobAssignmentId`, `recipient` (`SafeUserDto`: id, ad, soyad, kullanıcı adı, profil fotoğrafı), `maskedIban`, `providerReference`, `settlementReference`, `amount` (ondalık **metin**, §2e), `currency`, `status`, `failureReason`, `processingAt`, `completedAt`, `createdAt`. Panel satırı seçtiğinde `id`, `providerReference`, `amount` ve `currency` formu kendiliğinden doldurur (koala-admin#80 bunu açıkça istiyor); operatör dolan tutar ve para birimini **ekstre satırıyla karşılaştırıp doğrular**, elle yazdığı tek şey ekstre referansı ile `settledAt`'tir. `confirmSettlement` dördünü yine de snapshot'la karşılaştırmaya devam ediyor — ön doldurma kapıyı gevşetmiyor, yalnız kopyalama hatasını kaldırıyor.

**Ayrı DTO, aynı DTO'ya alan eklemek değil — bilinçli; sınır #294 ile yeniden çizildi.** İlk turda `PayoutDto` (`GET /users/me/payouts` ile işçiye, `release`/`reconcile` yanıtlarıyla personele gider) sağlayıcı referansını ve banka bağlamını hiç taşımıyordu. #294 bunun yanlış yerde çizilmiş bir sınır olduğunu gösterdi: işçi kazanç ekranı "para çıkışa alındı" diyor ama **hangi IBAN'a**, hangi referansla ve ne zaman gittiğini söyleyemiyordu — oysa hedef hesap işçinin **kendi** hesabı, sağlayıcı referansı da işçinin bankasıyla konuşurken elinde olması gereken tek anahtar. `PayoutDto` artık `maskedIban` (release anında `payouts.bank_account_id`'ye yazılan hesap; sonradan birincil hesap değişse de payout'unki değişmez), `bankAccountId`, `providerReference`, `releasedAt` (= `createdAt`) ve `settledAt` (= `completedAt`, yalnız `COMPLETED`'da dolu) taşıyor; `createdAt`/`completedAt` istemci sözleşmesi bozulmasın diye yerinde duruyor. **Personel kanıtı olan `settlementReference`, `recipient` ve `processingAt` işçi yüzeyine yine gitmiyor** — ekstre satırı işçinin değil operatörün kimliği, alıcı bloğu kendi kendine dönmenin anlamı yok. §2p'deki `AdminReportDto`/`ReportDto` ayrımı yerinde: yüzey DTO sınırında ayrılıyor, sınırın **neresi** olduğu ise "işçinin kendi parasına ait olan" ile "operatörün mutabakat aracı olan" arasında. `StaffPayoutMappingTest.theWorkerFacingPayoutCarriesItsDestinationAndProviderReferenceButNeverTheStatementLine` ve `PayoutQueueIT.theWorkerFacingListStillNeverCarriesTheStatementLineOrTheRecipientBlock` yeni sınırı iki katmanda sabitliyor; `…IsBuiltWithoutAQueryPerBankAccountOrPayment` işçi listesinin `bankAccount` grafiği düşürüldüğünde kırmızıya döndüğü mutasyonla görüldü. Hesap kaydı yok ya da hesap sonradan silinmişse (`BankAccount` soft-delete; grafik LEFT JOIN, `@SQLRestriction` satırı düşürür) `maskedIban`/`bankAccountId` `null` gider, hata değil; backfill **yapılmadı**: işçinin bugünkü birincil hesabını dünkü payout'a yazmak uydurma olurdu. Durum sözlüğü (PENDING = çıkışa alındı, sağlayıcı onayı bekleniyor; PROCESSING = sağlayıcı onayladı, banka transferi yolda; COMPLETED = banka ekstresiyle doğrulandı; FAILED) OpenAPI'de `PayoutDto` şemasında ve `/users/me/payouts` açıklamasında.

**Maskeleme kuralı yeni değil, `LogMasks.iban`'ın kendisi: ilk 4 + `***` + son 4 (`TR18***2315`).** `BankAccountDto`, `IbanPendingDto` ve `/users/me` `maskedIban` alanı aynı yardımcıyı kullanıyor; kuyruk için ikinci bir maske yazılmadı. Hesabı olmayan (eski) bir payout satırında alan `null` gider, `"***"` değil — `UserManager.toMeDto`'nun kararının aynısı: yokluk boşlukla temsil edilir. Tam IBAN ne yanıt gövdesinde ne herhangi bir log satırında: `PayoutQueueIT.servingTheQueueNeverWritesTheFullIbanToAnyLogLine` kök logger'a `ListAppender` takıp isteği koşuyor ve tam IBAN'ı ya da gövdesini taşıyan tek satır olmadığını ölçüyor; `LogPrivacyTest` yasaklı listesi bu turda `getAccountHolderName()` / `accountHolderName`'i de kazandı (banka verisi, kişisel ad) ve kırmızıya dönebildiği mutasyonla görüldü.

**İzin `payout.reconcile`, ve artık iki katmanda.** Uç zaten `@RequiresPermission(PAYOUT_RECONCILE)` taşıyordu (HTTP kapısı, fail-closed); kuyruk okuması bu turda `StaffPayoutManager.listForStaff(actor, …)` facade'ından geçirildi, yani release/refund/reconcile ile aynı yoldan: aktör `null`, kayıtsız ya da izinsizse manager `ForbiddenException` atıyor ve iç `PayoutService`'e hiç dokunmuyor (`StaffPayoutFacadeTest`, `PayoutAuthorizationSurfaceTest`). "İzni olmayan yalnız hassas alanları görmesin" seçeneği bilerek seçilmedi: `FINANCE_ANALYST` (`payment.read` + `finance.read`) kuyruğun tamamından **403** alıyor, kırpılmış satır değil — yanıtı iki şekle bölmek §2d2'nin kaçındığı ikinci bir yüzey açardı ve `payout.reconcile` zaten ekstreyi okuma yetkisinin adı. `PayoutQueueIT.aFinanceAnalystIsRefusedTheQueueOutrightRatherThanHandedARedactedRow` bunu sabitliyor.

**Alıcı entity graph ile değil, sayfa başına ikinci bir toplu sorguyla geliyor — ve bunun sebebi bir regresyon.** İlk tur `worker` ve `worker.profilePicture`'ı `findAllByStatusIn` grafiğine eklemişti. `Payout.worker` `@JoinColumn(nullable = false)` taşıyor ve `User` `@SQLRestriction("is_deleted = false")`; Hibernate 7 kısıtı to-one join'in **ON** cümlesine yazıyor ve FK null olamadığı için join'i **INNER** üretiyor: `join users w on w.id = p.worker_id and (w.is_deleted = false)`. Sonuç, hesabını silen (anonimleştirilen) işçinin `PROCESSING` hakedişinin kuyruktan **kaybolması** — `koala_payout_settlement_overdue` onu saymaya, runbook §4 adım 7 sorgusu döndürmeye devam ederken. ADR-0012 silmeyi yalnız açık yükümlülükte engelliyor; `RELEASED_TO_WORKER` bir atama yükümlülük değil, yani yol gerçek. `PayoutQueueIT.aPayoutWhoseWorkerDeletedTheirAccountStaysOnTheQueueWithItsEvidence` gerçek `PostgresAccountAnonymizer` ile, `…WorkerRowWasSoftDeletedKeepsItsBankEvidence` ham `is_deleted = true` ile ölçüyor; ikisi de düzeltmeden önce 0 satırla kırmızıydı.

**İki alternatif ölçüldü ve elendi.** (1) Açık JPQL `left join fetch p.worker`: join LEFT oluyor ama satır süzülünce Hibernate `FetchNotFoundException` ("Entity `User` … does not exist") atıyor — `nullable = false` bir FK'nın hedefinin olmaması ona göre bütünlük hatası. (2) `optional = true`: hiçbir şeyi değiştirmiyor, join türünü `@JoinColumn(nullable)` belirliyor. `@NotFound(IGNORE)` denenmedi: şemanın "FK sarkabilir" demesi yalan olurdu ve ilişkiyi her yerde eager yapardı. **Seçilen: grafik yalnız `bankAccount`** (opsiyonel → LEFT JOIN, hesap soft-delete'liyse `null`), `jobAssignment` ve `worker` grafikte yok — mapper ikisinden yalnız `getId()` okuyor ve proxy bunu sorgusuz veriyor —, sayfanın alıcıları `UserService.getAllByIdIn` (`UserDao.findAllByIdIn`, `profilePicture` grafikli) ile **tek toplu sorguda** alınıp `PayoutMapper.toStaffPayoutDto(payout, recipient)`'a açıkça veriliyor. Mapper `payout.getWorker()`'a hiç dokunmuyor: dokunsaydı silinmiş kullanıcının proxy'si `EntityNotFoundException` ile patlardı. `PayoutManager.listForStaff` bu yüzden artık `PageDto<StaffPayoutDto>` döndürüyor (§2al'deki `getMyAssignments` deseni).

**Silinmiş hesabın satırı `recipient: null`, `maskedIban: null` ile döner — anonim ad değil.** `@SQLRestriction` silinmiş kullanıcıyı JPA'dan zaten gizliyor; "Anonim Kullanıcı" yer tutucusunu bir alıcı kimliği gibi sunmak paneli yanıltırdı, `null` ise "bu hesap artık yok, ekstreyi sağlayıcı referansıyla eşleştir" demek. Anonimleştirme banka hesabını da `[anonymized]` IBAN'la soft-delete ettiği için maske de `null`. Kanıtın kendisi — `providerReference`, `amount`, `currency`, `status`, `processingAt` — payout satırında durur ve olduğu gibi döner. Ham `is_deleted` ile yalnız kullanıcı satırı gizlenmişse (`…KeepsItsBankEvidence`) maske korunur.

**Bu sınıfın yapısal koruması artık kuyruğu da bağlıyor.** `theOverdueGaugeSelectsTheSameRowsAsTheRunbookQueue` gösterge ile runbook sorgusunu eşitliyordu; `PayoutSettlementReconciliationIT.theStaffQueueListsEveryRowTheGaugeAndTheRunbookCountEvenWhenItsNeighboursAreGone` üçüncü kümeyi ekliyor: `listForStaff(PROCESSING)`'in döndürdüğü id kümesi, aynı tohumlanmış satırlarda gösterge ve runbook kümesine **eşit** olmak zorunda — işçisi soft-delete edilmiş satır **ve** ataması soft-delete edilmiş satır dahil. `JobAssignment` da aynı `nullable = false` + `@SQLRestriction` çiftini taşıyor; bugün hiçbir üretim yolu bir atamayı soft-delete etmiyor ama grafikten çıkarıldığı için join de yok ve kural aynı testte sabit. Regresyonu yakalayacak olan test buydu; grafik değişikliği bu test varken yapılsaydı ilk turda kırmızıya dönerdi.

**N+1 kapalı ve ölçülü.** Yapısal koruma `EagerFetchCoverageIT`'de (`UserDao.findAllByIdIn` `profilePicture`'ı çekiyor, §2af'nin `SafeUserDto` kuralı); davranışsal kanıt `PayoutQueueIT.theQueueIsBuiltWithoutAQueryPerRecipientOrBankAccount`. Sayfa başına statement sayısı sabit: **sayfa dolmadıkça 2** (hakedişler + banka hesapları tek SELECT, sayfanın alıcıları ikinci SELECT), **dolu sayfada 3** — Spring Data `PageableExecutionUtils.getPage` count sorgusunu yalnız içerik sayfa boyutundan küçükken atlıyor; satır sayısıyla **büyümez**. Test bu yüzden mutlak sayı değil **oran** ölçüyor: altı tohumlanmış hakedişte `size=1` ile `size=5` (ikisi de dolu sayfa) aynı prepared statement sayısını vermek zorunda ve lazy entity fetch **0** olmak zorunda — N+1 olsaydı ikinci sayfa dört statement fazla atardı. İlk PR'dan önceki grafik 3 satırda 6 lazy fetch veriyordu.

**`Statistics` SessionFactory geneli, bu yüzden test ortamında iki süpürme ertelendi.** Sayaç açılışta değil testin içinde `setStatisticsEnabled(true)` ile açılıyor (yeni context anahtarı doğmuyor, §3 bağlantı bütçesi) ama sayaç iş parçacığı ayırmıyor: aynı JVM'de koşan bir zamanlayıcı `clear()` ile okuma arasına düşerse sayı N+1'le ilgisiz bir sebeple artar — CI Pi'nin tam ürettiği şey. `src/test/resources/application.properties` çoğu zamanlayıcıyı kapatıyordu ama iki tanesinin kapatma anahtarı yok ve ikisi de JPA'ya iniyor: `IysConsentRetryScheduler` (30 sn; `IysManager.findDueTaskIds` konfigürasyona bakmadan `expireRetainedRecipients` koşturuyor) ve `PushDeliveryScheduler` (30 sn; `PushDeliveryStore` `NotificationDao` üzerinden okuyor). `iys.retry.fixed-delay=PT24H` ve `push.delivery.fixed-delay=24h` ile ilk ve sonraki koşuları test JVM'inin ömrünün ötesine ertelendi; hiçbir IT bu ikisinin ateşlenmesine dayanmıyor (`IysTaskProcessingIT` servisi doğrudan çağırıyor). Kalan cron'lar (15 dk'lık medya silme JDBC'den geçer, günlük anonimleştirme 02:30) ölçüm penceresine düşemeyecek kadar seyrek.

### Kapsam dışı, açıkça

- **Otomatik yeniden deneme/geri çekilme bu ayakta yok.** Otomatikleştirilen ayak sağlayıcı talimatıdır ve onun retry/backoff'u §2y'deki outbox'ta zaten var (8 deneme, üstel, 6 saat tavan). Ekstre satırı bir insan girdisi; "yeniden deneme" düzeltilmiş kanıtla ikinci çağrıdır. Rapor ucu ya da webhook geldiği gün otomatik ayak `confirmSettlement`'ın üstüne oturur ve geri çekilme oraya yazılır.
- ~~**Mutabakat bekleyen hakedişleri listeleyen bir personel ucu yok.**~~ **#211 ile açıldı, #231 ile kanıt taşır oldu** (yukarıdaki başlık). #128'deki karar ilk turda geçerliydi; panelin kuyruğu ekstreyle eşleştirmesi runbook SQL'ini insana koşturmak demekti ve o karar geri alındı. Alarm + runbook sorgusu duruyor; uç onların yerine değil, yanına geldi.
- **Sandbox'ta uçtan uca doğrulanmadı.** Kural, sahte kanıtlara ve gerçek Postgres'e karşı sabitlendi; gerçek bir Iyzico gönderim raporuyla teyit prod öncesi şart (§7, Faz 7C).

## 2z. Gerçek zamanlı katman

**WebSocket/STOMP seçildi çünkü yazıyor göstergesi istendi.** Yalnızca "mesaj anında gelsin" isteseydi push + açıkken yoklama yeterdi ve hiç yeni altyapı gerekmezdi; yazıyor göstergesi ve presence çift yönlü kanal ister.

**Kimlik doğrulama `CONNECT` karesinde.** `StompAuthChannelInterceptor` `Authorization: Bearer …` başlığını okur, `JwtService` ile oturumu bulur, `SessionService.findActiveSessionById` ile aktifliğini doğrular ve `UserPrincipal`'ı STOMP kullanıcısı yapar. HTTP tarafındaki `SessionAuthFilter` ile aynı garantiler: kapatılmış oturumun soketi de açılmaz.

**Kanallar:**
- `/topic/conversations/{id}` — `MESSAGE`, `TYPING`, `READ` olayları. Tek zarf: `RealtimeEventDto`.
- `/user/queue/messages` — kullanıcıya özel akış, yalnızca `MESSAGE`. Uygulama arka plandayken aynı olay FCM ile gider (§2o "Yeni mesaj push'u").
- `/topic/presence/{userId}` — o kullanıcının `PRESENCE` olayları.
- `/app/conversations/{id}/typing` — istemcinin yazıyor bildirimi. **Sunucu tarafta yetki kontrolü var** (`assertParticipant`), yoksa yabancı biri herhangi bir konuşmaya olay basabilirdi.

**Zarfın beşinci olay tipi `ERROR`.** Reddedilen bir abonelik, denenen hedefin kendi aboneliğine `type: ERROR` ve `error: {destination, errorCode, message}` taşıyan bir `RealtimeEventDto` alır; gerekçe ve mekanik §2ai'de.

**Sohbet listesi için kişisel kuyruk var.** Yeni mesaj hem konuşma topic'ine hem **her iki tarafın** `/user/queue/messages` kuyruğuna gidiyor. Öncesinde yalnızca konuşma topic'i vardı; sohbet listesini canlı tutmak için istemcinin 20 ayrı topic'e abone olması gerekiyordu, o yüzden liste ancak elle tazelenince güncelleniyordu. Kopya iki tarafa da gider — gönderenin *ikinci cihazındaki* liste de güncellensin diye. Sohbeti açık olan istemci aynı olayı iki kanaldan alır, `message.id` ile tekilleştirmesi gerekir.

**Kullanıcı hedefi id ile anahtarlanıyor, e-posta ile değil.** `UserPrincipal` bir `UserDetails` ve `getUsername()` e-posta döndürüyor; `Authentication.getName()` de onu kullandığı için `convertAndSendToUser(userId, …)` hiçbir zaman eşleşmezdi. `RealtimeAuthentication` yalnızca `getName()`'i ezip kullanıcı id'sini döndürüyor. E-posta değişebilir bir tanımlayıcı, yönlendirmeyi ona bağlamak istenmedi.

**Presence kullanıcı başına topic'ten yayınlanıyor.** Öncesinde tek bir `/topic/presence` vardı ve herkese açıktı: her kullanıcının çevrimiçi/çevrimdışı değişimi bağlı olan herkese gidiyordu. Ölçeklenmemesi bir yana, asıl sorun **davranış izleme** idi — hiç etkileşime girmediğin bir işçinin ne zaman çevrimiçi olduğunu izleyebiliyordun. Frontend'in önerisi kullanıcı başına topic'ti; ona **abonelik yetkisi** eklendi (aşağıda §2ai).

**Okundu bilgisi de kişisel kuyruğa gidiyor.** `conversationRead` hem konuşma topic'ine hem bütün katılımcıların kuyruğuna yazıyor; kullanıcı sohbeti telefonunda okuduğunda tabletindeki listede rozet düşüyor. Aksi hâlde "okumadığım mesaj var" yanılgısı kalıyordu. `READ` zarfında `userId` okuyan, `lastReadAt` okuyanın yeni damgası (§2o, #281).

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

**Özel dosyada kimlik doğrulaması yetmez, sahiplik de kontrol edilir.** `/uploads/files/**` `SecurityConfig`'te `hasAnyRole("USER","ADMIN")` — yani **giriş yapmış herkes**. Tek koruma anahtarın tahmin edilemez olmasıydı; §2ab'nin "tahmin edilemez URL yeterli koruma değildir" ilkesiyle çelişiyordu. `CvService.assertCanDownload` eklendi: `media.createdBy` istek atanla eşleşmiyorsa ve kişi ADMIN değilse var olmayan dosyanın **yanıtının aynısı** döner — 404 ve aynı `storage.file.not.found` anahtarı (§2d2). Dosyanın varlığı ne durum kodundan ne `errorCode`'dan sızar. Görsellerde bu kontrol yok, olmamalı da; onlar zaten CDN'den herkese açık.

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

**Döngüyü kırmak için bir iş akışı da taşındı.** Otomatik tamamlamadaki uyuşmazlık kontrolü `JobAssignmentManager`'dan `AssignmentAutoCompletionScheduler`'a çıktı — iki aggregate'i koordine eden şey artık *orkestratör*, aggregate'in kendisi değil. Emanet çıkışındaki uyuşmazlık kilidi (asıl para kapısı) `PayoutManager`'da yerinde duruyor. İşveren tamamlamasında orkestratör yok (tek transaction, §2u); oradaki koşullu çıkış `PayoutService.releaseToWorkerUnlessDisputed` ile yine `PayoutManager`'ın muaf `DisputeDao` erişimine yaslanır, `JobAssignmentManager` uyuşmazlığa dokunmaz.

**Yeni servisler:** `AcademicReferenceService` (üniversite/fakülte/bölüm referans verisi `EducationManager`'dan ayrıldı), ve delegasyon için `UserService.getReference`, `JobAssignmentService.getDetailedById/countCompletedForWorker/hasMoneyInEscrowFor/hasActiveWorkFor`, `DisputeService.hasActiveDispute`, `PaymentService.getSettledPaymentFor`, `MessagingService.getMessageById`, `JobCategoryService.getById`, `CompanyContactService`'e dört metot. `JobApplicationService.hasApplied` bir süre bu listedeydi; tek çağıranı mesajlaşma kapısıydı ve #257 ile kapı ödemeye taşınınca düştü (yerine `JobAssignmentService.findLatestByJobAndWorker` / `findLatestByJobsAndWorkers`).

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

## 2ah2. Parola kuralı — tek tanım, uzunluk öncelikli

**Kural tek yerde: `core/customValidations/ValidPassword` + `PasswordPolicyValidator`, sayılar
`core/constants/PasswordPolicy`.** Parola belirleyen **her** alan yalnız bu anotasyonu taşır; kendi `@Size`,
`@Pattern`, `@NotBlank`'ini yazmaz. Anotasyon null ve boşluğu da reddettiği için ikinci bir kısıt gerekmiyor.

| Uç | Alan |
|---|---|
| `POST /auth/complete-registration` | `AuthCompleteRegistrationDto.password` |
| `POST /auth/reset-password` | `ResetPasswordDto.newPassword` |
| `PUT /users/me/password` | `ChangePasswordDto.newPassword`, `confirmNewPassword` |

**Dördü de davranışla korunuyor.** `PasswordPolicyTest` DTO'yu gerçekten doğrulatarak üç ucu sınıyor;
`PasswordPolicyRulesTest`'in metin taraması ayrıca `core/dtos` altındaki her parola alanını buluyor.
Eskiden tabloda beşinci bir alan vardı — hiçbir uca bağlı olmayan `CreateUserDto.password` — ve tek
koruması taramaydı; sınır burada yazılıydı çünkü "her uç korunuyor" demek, korunduğu sanılan bir alan
bırakmak olurdu (#88). O sınır artık yok: bu başlığın kendi kuralı ("ölü çıkarsa alan tümüyle düşmeli")
işletildi ve sınıf silindi.

**`CreateUserDto` ve `UpdateUserDto` silindi — gerekçe.** İkisinin de koddan çağıranı yoktu. "Referans
yok" tek başına gerekçe sayılmadı; bu varsayım depoda iki kez ters tepti (`USER_NOT_ASSOCIATED_WITH_INVITATION`
silindikten iki gün sonra davet kabul akışında gerekti; `sortableJobFields` "üretimde kullanılmıyor"
diye silindi, oysa testin her arayüz sıralama seçeneğini sunucunun izin verdiği alanlara bağlayan
**sözleşme çıpasıydı**). Bu yüzden silmeden önce dört yere bakıldı: açık issue'lar (hiçbiri anmıyor —
personel yönetimi tarafı dahil), `docs/` altındaki planlar ve ADR'ler (anmıyor), bir testin çıpa olarak
kullanıp kullanmadığı (aşağıda), ve DTO'nun kendi durumu.

**Belirleyici olan sonuncusuydu: `CreateUserDto` yalnız kullanılmıyordu, kullanılamazdı.** `gender` alanı
`Gender` enum'u ve üzerinde `@NotBlank` duruyordu; `@NotBlank` yalnız `CharSequence` doğrular. Bu DTO bir
uca bağlansaydı ilk istekte `UnexpectedTypeException` ile 500 dönerdi. Yani uykuda bekleyen hazır bir
sözleşme değil, hiç koşmamış bir taslaktı — çıpa olabilecek bir şey değil. Mesaj anahtarları da bunu
söylüyordu: `user.firstName.notBlank` camelCase, oysa kataloğun geri kalanı `user.first.name.cannot.be.blank`
düzeninde; terk edilmiş bir adlandırmadan kalma. Kullanıcı yaratma zaten iki adımlı kayıtta
(`AuthStartRegistrationDto` → `AuthCompleteRegistrationDto`), personel yaratma `CreateStaffMemberDto`'da —
ADR-0014'ün "personel oluşturma" maddesini karşılayan uç bu. `UpdateUserDto` ise gövdesi tümüyle boş bir
sınıftı; `UserService` ve `UserManager` onu yalnız `import` ediyordu, hiçbir imzada geçmiyordu. Boş bir
sınıf hiçbir sözleşmeyi sabitleyemez; o iki import da düştü.

**Tarama daraltılmadı.** `PasswordPolicyRulesTest.theScanReachesEveryKnownPasswordField` beklenti
listesinden yalnız `CreateUserDto.java#password` satırı çıktı; taramanın kendisi — `core/dtos` kökü ve alan
deseni — aynı kaldı. Listeden bir isim çıkarmak ile taramanın kapsamını daraltmak farklı şeyler, ve
kapsam da daralmadı: `user/request` hâlâ `ChangePasswordDto`'nun üç alanıyla, `auth/request` üç DTO'yla,
`staff/request` muafiyet listesiyle temsil ediliyor; dört alan adı biçimi (`password`, `newPassword`,
`currentPassword`, `confirmNewPassword`) hâlâ beklentide.

**Dört anahtar üç katalogdan da düştü** (`user.firstName.notBlank`, `user.lastName.notBlank`,
`user.email.notBlank`, `user.gender.notBlank`) — duran bir anahtar sonradan yeniden çağrılabilir.
**`user.email.invalid` düşmedi:** silinen DTO'daki beş anahtardan tek başkasınca da çağrılanı oydu —
`AuthMessages.INVALID_EMAIL` onu taşıyor, `AuthLoginDto`, `AuthStartRegistrationDto` ve `ForgotPasswordDto`
kullanıyor. Anahtarlar tek tek denetlenmeseydi çalışan üç uç sessizce ham anahtar döndürürdü.

**Kural:** en az **12 karakter** (kod noktası), en fazla **72 bayt** (UTF-8), boşluktan ibaret olamaz,
**karakter sınıfı zorunluluğu yok**.

**Neden uzunluk öncelikli.** NIST SP 800-63B zorunlu karmaşıklık kurallarını önermiyor: kullanıcıyı
tahmin edilebilir dönüşümlere itiyorlar. Deponun kendi kuralı bunun canlı örneğiydi —
`^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$` sekiz karakterlik `Parola12`'yi kabul ederken rakamsız kırk karakterlik
bir parolayı reddediyordu. Karmaşıklık kuralı düştü, taban yükseldi.

**Neden 12.** 800-63B'de 8 **SHALL** tabanı, 15 ise **SHOULD** tavsiyesi. 12, tabanın belirgin biçimde
üstünde ama tavsiyenin altında; sapma bilinçli, çünkü parola belirleme huninin son adımı (kayıt tamamlama)
ve 15 orada ölçülebilir kayıp demek. Karar burada yazılı olsun ki bir dahaki sefere unutulmuş değil,
**verilmiş** bir karar olarak tartışılsın.

**Neden üst sınır var ve neden bayt.** `PasswordEncoderConfig` `BCryptPasswordEncoder` kuruyor; Spring
Security'nin `BCrypt`'i 72 baytı aşan parolada `IllegalArgumentException("password cannot be more than 72
bytes")` atıyor. `reset-password` ucunda üst sınır **hiç yoktu**: uzun bir parola kütüphanenin İngilizce iç
mesajını taşıyan bir 400'e dönüşüyordu. Sınır artık DTO'da ve bcrypt bayt saydığı için bayt cinsinden —
Türkçe harfler 2 bayt, yani 72 karakter Türkçe parola sınırı zaten aşıyor.

**Bu sayı kodlayıcıya bağlı, bağı bir test tutuyor.** Kodlayıcı bir gün Argon2 olursa 72 anlamını yitirir;
`PasswordPolicyTest.theRuleIsTwelveCharactersAndSeventyTwoBytesUntilSomeoneDecidesOtherwise` iki sayıyı da
sabitliyor, biri değişirse kırmızıya dönüp bu başlığa geri yolluyor. Katalog mesajının aynı sayıları
yazdığını da ayrı bir test doğruluyor — mesaj "en az 8 karakter" derken kural 12 olamaz.

**Giriş ve `currentPassword` bilerek kapsam dışı.** Onlar kullanıcının **zaten sahip olduğu** parolayı
taşıyor; politikayı oraya uygulamak eski kuralla kaydolmuş herkesi kapıda bırakırdı.
`PasswordPolicyRulesTest` bu iki alanı muafiyet listesinde tutuyor ve liste bayatlarsa kırmızıya dönüyor.

**Asıl koruma yazılı kural değil, tarama.** `PasswordPolicyRulesTest` `core/dtos` altındaki her parola
alanını buluyor ve (a) `@ValidPassword` taşıdığını, (b) kendi `@Size`/`@Pattern`'ini **yazmadığını**
doğruluyor. Kuralın bugün olmaması kadar, yarın üç uçta üç farklı kural olması da kusur —
is-koala/koala-backend#43 açıldığında zaten öyleydi: kayıt tamamlama yalnız `@NotNull`, parola değiştirme
`@Size(min=8)`, parola sıfırlama `@Size(min=8)` + karmaşıklık.

**Katalogdan altı anahtar düştü** (`user.new.password.min.length`, `user.new.password.not.blank`,
`user.confirm.new.password.not.blank`, `user.password.strong`, `user.password.notBlank`,
`user.password.size`) — hepsi eski kuralı anlatıyordu ve duran bir anahtar sonradan yeniden çağrılabilir.
Yerlerine tek anahtar geldi: `user.password.policy`.

**İstemci sözleşmesi değişti.** Taban 8'den 12'ye çıktı, karmaşıklık kuralı kalktı; `koala-web` ve
`koala-mobile` form doğrulamaları buna göre güncellenmeli. Var olan parolalar yeniden doğrulanmıyor, yalnız
yeni belirlenenler kurala tabi.

## 2ai. Gerçek zamanlı katmanın güvenliği

`CONNECT` karesi baştan beri doğrulanıyordu, ama **`SUBSCRIBE` doğrulanmıyordu.** Giriş yapmış herhangi biri `/topic/conversations/{herhangi-bir-id}`'ye abone olup başkalarının mesajlarını okuyabilirdi. Kapatıldı:

- `/topic/conversations/{id}` — abonelik anında **taraf olma kontrolü**; taraf değilse 403, konuşma yoksa 403 (varlığını da sızdırmamak için).
- `/topic/presence/{userId}` — abonelik anında **aynı konuşmayı paylaşma kontrolü**; kendi durumunu herkes izleyebilir, başkasınınkini yalnızca onunla konuşması olan. Global `/topic/presence` **kaldırıldı**.
- `/user/**` — broker'ın kendi kullanıcı kapsamına bırakılır.
- **Tanınmayan hedef reddedilir**, sessizce geçilmez. Yeni bir topic eklenirse buraya da yazılmalı.

Ayrıca taşıma sınırları kondu: mesaj 64 KB, gönderim tamponu 512 KB, gönderim zaman aşımı 10 sn — kötü niyetli bir istemcinin belleği şişirmesini engeller.

**TLS bu katmanın dışında.** Soket `wss://` üzerinden servis edilmeli; şifreleme reverse proxy'de sonlanıyor. Uygulama `ws://` ile de çalışır, bu yüzden **prod'da proxy'nin TLS sonlandırdığından emin olunmalı** — kod bunu zorlayamaz.

**Yetkisiz abonelik artık bağlantıyı düşürmüyor.** Yetki reddi `preSend` içinden bir `ForbiddenException` olarak çıkıyordu ve Spring'in varsayılan davranışı bunu bir STOMP `ERROR` karesine çevirip **soketi `CloseStatus.PROTOCOL_ERROR` (1002) ile kapatıyordu** (`StompSubProtocolHandler.sendErrorMessage`, `finally` bloğu). Sonucu şuydu: izinli bir konuşmaya abone olmuş istemci, ikinci bir izinsiz aboneliği denediği anda **ilk aboneliğini de kaybediyordu**; tek bir yanlış id bütün gerçek zamanlı oturumu düşürüyordu.

`StompErrorFrameHandler` bunu kapatıyor — `StompEndpointRegistry.setErrorHandler` ile takılan bir `StompSubProtocolErrorHandler`. `SUBSCRIBE` reddinde `ERROR` yerine, **başarısız aboneliğin kendi `id`'sine adreslenmiş bir `MESSAGE` karesi** dönüyor; `sendToClient` yalnızca komut `ERROR` olduğunda kapattığı için bağlantı ayakta kalıyor ve istemci reddi tam da denediği aboneliğin callback'inde görüyor.

**Bu, issue'nun lafzıyla STOMP spec'i arasındaki çelişkinin çözümü.** Issue "ilgili aboneliğe hata frame'i dönsün" diyor; ama STOMP 1.2'de `ERROR` karesi tanımı gereği bağlantıyı sonlandırır ve Spring de öyle davranır. İkisi birden olamaz. Seçilen taraf kabul kriteri: bağlantı düşmüyor, hata bilgisi aboneliğe gidiyor.

**Zarf değişmedi, `RealtimeEventDto` genişledi.** Ret karesi de aynı zarfı taşıyor: `type: ERROR` ve `error: {destination, errorCode, message}`. Ayrı bir gövde şekli seçilseydi, `/topic/conversations/{id}` callback'i bazen `RealtimeEventDto` bazen başka bir şey alırdı; istemci `type` üzerinde zaten dallanıyor.

**`CONNECT` reddi hâlâ soketi kapatıyor ve kapatmalı** — kimliği doğrulanmamış bir bağlantıyı açık tutmanın anlamı yok. Değişen şey `ERROR` karesinin **metni**: Spring'in varsayılanı `ex.getMessage()` yazıyordu, yani istemciye `Failed to send message to clientInboundChannel` gibi iç detay gidiyordu. Artık katalogdan çözülmüş metin gidiyor (`{realtime.frame.rejected}`, ya da hata bir `KoalaException` ise onun anahtarı). **Token bu karede hiçbir zaman görünmüyordu** — `MessageDeliveryException` başarısız mesajı yalnızca `toString()`'e ekler, Spring ise `getMessage()` okur; yine de `StompErrorFrameTest` bunu regresyon olarak sabitliyor.

**Asıl kusur origin allowlist'inde değil, CORS filtresindeydi.** `realtime.origins.*` ve `OriginHandshakeInterceptor` baştan doğruydu ama **hiç çalışmıyordu**: `CorsConfig` filtresi `/**` altına kayıtlı ve REST allowlist'i ADR-0011 gereği **boş**, dolayısıyla `Origin` başlığı taşıyan her handshake isteği daha güvenlik zinciri sırasında **403** alıyordu. Yani `STOMP_PRODUCTION_ORIGIN` ne yazarsa yazsın `https://iskoala.com` üzerinden tarayıcı STOMP bağlantısı **kurulamıyordu**; allowlist ölü koddu. Tarayıcı WebSocket handshake'i CORS'a tabi değildir (preflight yok, `Access-Control-Allow-Origin` aranmaz) ama sunucudaki `CorsFilter` `Origin` başlığını görüp reddediyordu.

`CorsConfig` artık gerçek zamanlı uç için **hiç CORS görüşü bildirmiyor** (`getCorsConfiguration` null döner), kararı tümüyle `OriginHandshakeInterceptor`'a bırakıyor. İki allowlist'in ayrı olması ADR-0011'in kararı; bu düzeltme o ayrımı fiilen çalışır hâle getiriyor. `/ws` için CORS yapılandırması **kopyalanmadı** — kopyalansaydı aynı origin listesi iki yerde durur ve ayrışırdı.

**Muafiyet yol bazlı, `Upgrade` başlığına bakmıyor.** Yani `/ws` ve altındaki her istek CORS filtresini atlıyor, yalnızca gerçek handshake değil. Bu kabul edilebilir çünkü o önek **yalnızca** STOMP ucunu servis ediyor; altına ikinci bir handler eklenirse muafiyetin daraltılması gerekir.

**Ret metni her zaman varsayılan dilde.** `MessageResolver` `LocaleContextHolder`'dan okuyor ve soket iş parçacığında `Accept-Language` çözümlenmiş bir istek yok; §2aj'nin "başlık yoksa Türkçe" varsayılanına düşülüyor. İstemcinin dilini soket üzerinden taşımak isteniyorsa `CONNECT` karesine bir dil başlığı eklenip burada okunması gerekir — bugün yapılmadı.

`StompSubscriptionSecurityTest` on bir senaryoyu, `StompErrorFrameTest` ikisini, `StompConnectionRateLimitTest` üçünü ve `RealtimeFanoutTest` yedisini sabitliyor. `RealtimeHandshakeIT` beş kuralı **gerçek soket üzerinden** kanıtlıyor: izinli origin bağlanır, tanınmayan origin handshake'i geçemez, origin taşımayan native istemci bağlanır, geçersiz token oturum açamaz, ve **yetkisiz abonelik bağlantıyı düşürmeden o aboneliğe ret karesi döndürür**.

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

**`POST /images` ADMIN kısıtından çıktı (#72).** Uç `hasAnyRole("ADMIN")` taşıyordu, yolu `/admin` ile başlamıyordu — yani §0b'yi çiğniyordu, ama koruma o yazımı görmediği için fark edilmemişti. İki seçenek tartıldı. **Taşımak** (`POST /admin/images`) göründüğü kadar simetrik değil: `/admin/**` artık `StaffSecurityConfig`'in zinciri (§2au) ve `@RequiresPermission` taşımayan her uç fail-closed reddediliyor, yani uç yeni bir `StaffPermission` isterdi. Yanındaki `DELETE /admin/images/{imageId}` `content.moderate` ile korunuyor çünkü o bir **moderasyon** işlemi — görseli bulunduğu tüm galerilerden düşürüyor; görsel *yüklemek* moderasyon değil ve hiçbir yönetim akışının çağırmadığı bir uç için izin icat etmenin karşılığı yok. **Kısıtı değiştirmek** seçildi, uç `hasAnyRole("USER", "ADMIN")` oldu. Gerekçe üç ayaklı: (1) yüzeydeki bütün yükleme uçları (`/users/me/profile-picture`, `/users/me/cv`, `/companies/{id}/images`, `/jobs/{id}/images`) zaten `USER, ADMIN` ve `RateLimitFilter.UPLOAD_PATHS` `/images`'i tam onların yanına yazmış (§2j) — hız sınırı ucu baştan kullanıcı yüklemesi saymış, kısıt onunla çelişiyordu; (2) yeni bir yetenek açılmıyor, `PUT /users/me/profile-picture` aynı `imageService.uploadImage`'i aynı `UPLOAD` kovasından her kimliği doğrulanmış kullanıcıya zaten veriyor; (3) asimetri kapanıyor — bitişikteki `DELETE /images/{imageId}` `USER, ADMIN` ve sahipliği `ImageManager` denetliyor, yani kullanıcı kendi yüklediği görseli silebiliyor ama hiç yükleyemiyordu. Ucu **silmek** de konuşuldu — iç çağıranı yok — ama yayımlanmış bir yüzeyi kaldırmak istemci sözleşmesi kararıdır, bu issue'nun kapsamı değil. Kararın bir yan etkisi vardı: `ErrorContractIT.aUserReachingAnAdminEndpointIsForbiddenNotAServerError` "admin ucu" olarak `POST /images`'i kullanıyordu ve uç artık admin ucu olmadığı için 400 döndürmeye başladı. Test beklentisi değil **fikstürü** düzeltildi — adının söylediği şeyi yapsın diye artık gerçek admin yüzeyine, `GET /admin/users/{userId}/roles`'a vuruyor; beklentiyi 400'e çekmek, testin koruduğu regresyonu (yetkisiz erişim 500 dönüyordu) sessizce ortadan kaldırırdı.

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

### Anotasyon penceresi satır sayısıyla değil yapıyla sınırlanıyor

**Taramanın penceresi `@*Mapping` satırından geriye **sabit 14 satır** bakıyordu ve önceki ucun sınırında durmuyordu. Tek bir sayı iki yönde birden yanlış cevap veriyordu ve ikisi de ölçüldü:**

- **Sahte yeşil.** `@Operation`'ı **hiç olmayan** bir uç, 14 satır yukarıdaki komşusunun anotasyonlarını kendi penceresinde görüp geçiyordu. Kuramsal değil: bugünkü 212 mapping'in **70'inin** hemen altına belgesiz bir uç eklemek eski kodda dört testi de yeşil bırakıyordu (ölçüldü). Test "her uç belgelenmiş" diyordu ama bunu garanti etmiyordu.
- **Sahte kırmızı.** Anotasyon bloğu uzadığında (`@ApiResponse` eklenince oluyor) `@Operation` pencerenin **dışına** itiliyor ve belgeli bir uç belgesiz sanılıyordu. #124'te tam bu yaşandı ve sonucu daha kötü: ajan pencereyi düzeltmek yerine **açıklamayı kısaltmak** zorunda kaldı — yani ölçüm aracı kendi kusuruna göre kodu şekillendirdi. Bugün beş anotasyon bloğu 14 satırlık pencerenin sınırına dayanmış durumda.

**Pencereyi büyütmek çözüm değil, birinci kusuru büyütür.** İki yön aynı sayının iki ucu; hangi sayı seçilirse seçilsin biri bozuk kalır.

**Sınır artık yapısal: `annotationBlockOf` eşleşen mapping'den geriye tarıyor ve önceki metodun gövde sonuna (`}`) ya da önceki `@*Mapping`'e ulaşınca duruyor.** Aradaki bölge o ucun kendi anotasyon bloğudur; aranan anotasyon orada yoksa uç belgesizdir.

**Hangi sınırın işi yaptığı varsayılmadı, kaynaktan ölçüldü: 212 mapping'in 212'si geriye tararken önce bir `}` satırına çarpıyor, `@*Mapping` hiçbirinde ilk sınır değil.** Yani yükü taşıyan kural kapanan süslü parantez; dosyanın başına ulaşan tek bir mapping bile yok. Önceki mapping kontrolü yine de duruyor, çünkü gövdesi olmayan bir handler yazıldığı gün (`;` ile biten bir bildirim) parantez kuralı önceki ucun anotasyonlarına taşar ve sahte yeşil deliği sessizce geri açılırdı — kapatılmak istenen kusurun ta kendisi. İkinci kural bedava ve bugün hiç ateşlenmiyor.

**İndentasyona bakılmıyor, ve bu da ölçüme dayanıyor:** `UsersController`'daki `@GetMapping("/me")` üç boşlukla girintili, diğer 211'i dörtle. Girintiyi sınır ölçütü yapan bir kural o ucu ilk günden yanlış okurdu. Kural bu yüzden `trim()` sonrası satırın tam olarak `}` olmasına bakıyor; anotasyon blokları `})` ya da `)` ile kapandığı için onlarla karışmıyor.

**Düzeltmenin ilk işi bir uç ortaya çıkarmak oldu:** `POST /admin/badges` (`BadgesController.createBadge`) ne `@Operation` ne `@ApiResponses` taşıyordu ve komşusu `allBadges`'in anotasyonları tam 14 satır yukarıda olduğu için pencereden geçiyordu. Belgelendi (201/400/409). Taramanın kalan 211 ucunda başka eksik çıkmadı ve yetki testi de tek bir sahte kırmızı üretmedi — yani daralan pencere var olan hiçbir belgeyi görünmez yapmadı.

**Bu düzeltme kendi kırmızısını iki yönde de gösterdi (#72'nin dersi: kırmızıya dönebildiği gösterilmemiş koruma doğrulanmamıştır).** Belgesiz bir uç enjekte edildiğinde eski kod yeşil kalıyor, yeni kod suçluyu adıyla yazıyor; 14 satırı aşan bir `@ApiResponse` bloğu eklendiğinde eski kod belgeli ucu belgesiz sanıp kırmızıya dönüyor, yeni kod yeşil kalıyor.

**`CompanyContactsController` silindi** — hiçbir uç taşımayan, yalnızca bir servis enjekte eden ölü sınıftı.

## 2ao2. Kontenjan — kapı kondu, gerekçe para

**Bu başlık bir kararı tersine çeviriyor.** Önceki hâli "sayı var, kapı yok" idi: `Job.headcount` isteğe bağlı bir hedef sayıydı, hiçbir yerde zorlanmıyordu ve `JobHeadcountRulesTest` bu **yokluğu** sabitliyordu. Gerekçesi ürün tarafındaydı ve hâlâ geçerli bir kaygı: ihtiyaç iş sırasında değişir, biri gelmez, iş büyür; sunucunun "3 dedin, 4. kişiyi alamazsın" demesi işvereni platformun dışına iter.

**Kararı değiştiren şey esneklik tartışması değil, tahsilat.** Kapı yokken `headcount = 1` olan bir ilanda işveren beş başvuruyu da kabul edebiliyordu. Her kabul bir `JobAssignment` doğuruyor, her atama işçi tarafından `CONFIRMED`'a çekilebiliyor ve **her biri kendi ödeme oturumunu açıyordu** — yani bir kişilik iş için beş kez tahsilat. Boşluk ayrıca ADR-0003'e dokunuyordu: her fazladan atama tam adresi bir kişiye daha açıyordu (§2m). Esneklik gerekçesi bu iki sonucu karşılamıyor.

**Kapı yalnızca kabul anında, yalnızca `headcount` bildirilmişse.** `JobApplicationManager.assertOpeningAvailable` ilanın dolu olup olmadığını ölçer ve doluysa `ConflictException` (409, `job.application.job.headcount.full`) atar. Reddedilen başvuru `APPLIED` kalır, `JobAssignment` doğmaz.

**`headcount == null` bugünkü davranışı korur: sınırsız.** Null'ı "bir kişi" saymak ayrı bir ürün kararı ve burada verilmedi; null ilanda sayım sorgusu **hiç çalıştırılmaz**. `JobHeadcountRulesTest` bunu iki testle sabitliyor.

**"Yer tutan atama" kümesi adres kümesiyle aynı değil, karıştırma.** Yer tutma `PENDING_CONFIRMATION` + `CONFIRMED` + `IN_PROGRESS` + `COMPLETED`; adres görünürlüğü (§2m) `COMPLETED`'ı **dışlar**. Yani tamamlanmış bir atama yeri doldurmaya devam eder ama adresi artık göstermez. `PENDING_CONFIRMATION`'ın sayıma dahil olması kuralın işe yaramasının şartı: sayılmasaydı işveren beş teklifi aynı anda açıp beşini de onaylatabilir ve sınır hiç işlemezdi. Küme `JobAssignmentManager.SLOT_OCCUPYING_STATUSES` içinde **tek yerde** duruyor; sayım `JobAssignmentService.countOccupiedSlots` üzerinden alınıyor, çünkü `JobApplicationManager` kendi olmayan `JobAssignmentDao`'ya uzanamaz (§2ae).

**Kapı sayıp-sonra-yazıyor, kilit yok — bilinen boşluk.** `countOccupiedSlots` ile `save` arasında ne pessimistic lock ne kısmi benzersiz indeks var. Aynı anda gelen iki kabul isteği de `occupied` değerini aynı okur ve ikisi de geçer; yani kapı **sıralı** çağrıda kontenjanı garanti ediyor, eşzamanlı çağrıda değil. Tüm testler sıralı. Kapatmak için ilan satırının `SELECT ... FOR UPDATE` ile kilitlenmesi (ve §2ae gereği bunun `JobService` üzerinden verilmesi) ya da `job_assignments` üzerinde sayıyı zorlayan bir kısıt gerekir; ikisi de bu dilimin dışında bırakıldı. Pratikte pencere dar — işverenin iki kabule aynı anda tıklaması gerekir — ama **bu boşluk kapının kapatmak için var olduğu çift tahsilatın ta kendisi**, yani kapanmadan "garanti" denmemeli.

**Kontenjan dolunca ilan kapanmaz.** Otomatik kapanma ayrı bir ürün kararı; `reachingTheHeadcountDoesNotCloseTheJob` bu yokluğu korumaya devam ediyor. `hiredCount` de değişmedi — hâlâ `CONFIRMED`/`IN_PROGRESS`/`COMPLETED` sayıyor, yani istemcinin gördüğü "2/3 alındı" göstergesi ile kapının saydığı sayı **bilerek farklı**: gösterge işe alınmış kişiyi sayar, kapı yer tutan teklifi de sayar.

**Sonradan alınan kişi ekibe de katılır — ödemesi gelince.** `CrewConversationListener` atamanın ödemesi `HELD_IN_ESCROW`'a geçtiğinde tetikleniyor ve tarih kontrolü yok (§2o); iş başladıktan sonra işe alınıp ödenen biri de gruba girer. Mesajlaşma penceresi yalnızca `endDateTime` + grace ile kapanır.

**`hiredCount` türetilir, saklanmaz.** `CONFIRMED`/`IN_PROGRESS`/`COMPLETED` atamalar sayılır — teklif edilmiş ama kabul edilmemiş (`PENDING_CONFIRMATION`) kişi "alınmış" sayılmaz (kontenjan kapısının saydığı küme bundan geniş, yukarı bak). Listede `countHiredByJobIds` ile **tek sorguda** toplanıyor (`JobManager.enrich`, kapak ve başvuru sayısıyla aynı yerde, §2ap); detayda tekil `countByJobIdAndStatusIn`.

**Detay eşlemesi controller'dan manager'a taşındı.** `GET /jobs/{id}` artık `jobService.getJobDetail(jobId)` çağırıyor; `hiredCount` mapper'da `ignore`, manager dolduruyor. §2al'deki kararın aynısı — eşleme controller'da kalsaydı sayıyı doldurmanın yeri olmazdı.

**İlan durumu kontenjanla değişmez.** İlan `IN_PROGRESS`'e alınsaydı aramadan düşerdi (`searchIds` `status = OPEN` istiyor, §2l) ve **sonradan işe alım imkânsızlaşırdı**. `JobStatus` bu yüzden üç değer (#115). Kapatma işverenin elinde. İstemci "2/3 alındı" göstergesini `hiredCount`/`headcount` ile kendisi çiziyor.

## 2ap. İşveren yüzeyi

**`JobSummaryDto.applicationCount` toplu sorguyla dolar.** İstemci ilan listesini çizip her ilan için ayrı `GET /jobs/{id}/applications` atıyordu — 10 ilanı olan işverende 1+10 istek. Sayı artık `countByJobIds` ile **tek sorguda** toplanıyor ve `JobManager.enrich` içinde kapak görselleriyle birlikte dolduruluyor. Yani sayfa başına iki ek sorgu: kapaklar + sayılar.

`JobManager → JobApplicationDao` doğrudan; `JobApplicationManager` zaten `JobService`'e bağlı olduğu için servis üzerinden gitmek ters yön döngü olurdu (§2ae kuralı, muafiyet `AggregateBoundaryTest`'te gerekçeli).

**İlanı listeleyen iki uç var ve ikisi ayrı soruya cevap veriyor.** `GET /users/me/jobs` "ben hangi ilanları açtım", `GET /companies/{companyId}/jobs` "bu şirketin hangi ilanları var". Ayrım şu ana kadar yoktu: tek liste ucu `findAllByCreatorId` üzerinden gidiyordu, yani **bir şirketin iki yetkilisi varsa birinin açtığı ilanı diğeri hiçbir listede göremiyordu** — `Job.company` dolu olmasına rağmen "bu şirketin ilanları" sorusunu soracak yol yoktu. Şirket yetkilileri yüzeyi (is-koala/koala-web#30, #31) indiği için ikinci yetkili artık gerçek bir kullanıcı ve kusur kuramsal değil.

**Sorgu yazılmıştı, hiç bağlanmamıştı.** `JobDao.findAllByCompanyId` baştan beri duruyordu ve `git grep` tanımından başka hiçbir çağrı göstermiyordu; ihtiyaç öngörülmüş, uç açılmamış, ölü kaldığı için de kimse fark etmemişti (is-koala/koala-backend#120).

**Var olmayan şirket 404 alır, var olan ama yetkisiz olan 403.** Uç önce `companyService.getCompanyById` çağırıp şirketin varlığını doğruluyor, yetki kapısı ondan sonra geliyor. İlk sürüm bunu yapmıyordu ve gerekçesi tersine de işliyordu: "404'e çekmek yalnızca yanlış bir 'bulunamadı' üretirdi" deniyordu, oysa teslim edilen davranış **olmayan** bir şirket için yanlış bir "bu şirketin yetkilisi değilsiniz" üretiyordu — rastgele bir UUID 403 `JOB_USER_IS_NOT_A_CONTACT_OF_COMPANY` alıyordu, kardeş uç `GET /companies/{companyId}` ise aynı UUID'de 404 veriyordu. Aynı kaynağın varlığı hakkında iki uç iki farklı şey söylüyordu.

**Bu sıra §2d2'nin "yetkilendirme durum kontrolünden önce gelir" kuralını çiğnemiyor, çünkü o kuralın konusu gizlenecek bir varlık.** Şirketin varlığı gizli değil: `GET /companies/{companyId}` her oturumlu kullanıcıya açık ve zaten 404 döndürüyor. Gizlenecek bir bit olmayınca sıralama kuralının koruduğu şey de yok; geriye yalnız kardeş uçla tutarlılık kalıyor. Varlık gizli olsaydı (kaynağa erişim yetkiye bağlı olsaydı) sıra ters olurdu ve iki dal `Concealment.notFound` ile aynı anahtarı taşırdı. **Sıralama doğrulaması hâlâ en sonda:** yabancı bir kullanıcı bozuk bir `sort` gönderdiğinde 400 değil 403 alıyor, olmayan bir şirkete bozuk `sort` gönderen ise 404 — kapı fail-closed sırada. Yetki kontrolü yeni bir mekanizma kurmuyor, `CompanyContactService.isUserAContactOfCompany`'yi çağırıyor — `JobApplicationManager` ve `requireJobEditor` ile aynı desen — ve `ADMIN`/`STAFF` ayrımı yapmıyor: STAFF zaten ilan açıp kapatabiliyor (§2s), açtığını listeleyememesi tutarsız olurdu.

**`sanitize → sorgu → protect → enrich` zinciri tek yerde: `JobManager.listJobs`.** Üç uç (`search`, `getJobsOfCurrentUser`, `getJobsOfCompany`) aynı yedi satırı taşıyordu ve tek farkları DAO çağrısıydı. Somut risk kozmetik değil: **ADR-0003 süzgeci değiştiğinde üç yerin de düzeltilmesi gerekiyordu ve biri unutulursa adres sessizce sızardı** — bu deponun baskın kusur sınıfı (§2d2, §2k2, #77). Yardımcı `Function<Pageable, Page<Job>>` alıyor, sanitizasyonu ve süzgeci kendisi uyguluyor; çağıran yalnız sorgusunu veriyor. `search` id sayfasını `PageImpl` ile sarıp aynı yoldan geçiyor: sayfa üstverisi birebir aynı kalıyor, tek fark **son sayfada arada kaybolan bir ilan** varsa toplam sayının o kadar küçülmesi — eski kod `ids.getTotalElements()`'i olduğu gibi taşıyordu, yenisi yüklenebilen satırı sayıyor.

**`assignedWorker` artık üç uçta da gerçekten hesaplanıyor.** İki liste ucu onu sabit `false` geçiyordu, yani ADR-0003'ün "etkin atamalı işçi" dalı bu uçlarda hiç ateşlenmiyordu: aynı ilan `GET /jobs/{id}` üzerinden `EXACT` dönerken `GET /companies/{companyId}/jobs` üzerinden `APPROXIMATE` dönüyordu. Sızıntı değil — hata gizleme yönündeydi — ama tutarsızdı ve süzgecin tek yere çıkarılması onu parametre olmaktan çıkarmayı zaten kolaylaştırdı: `listJobs` sayfadaki ilanlar için `assignedJobIds`'i koşulsuz hesaplıyor, üç uç da aynı kümeyi kullanıyor. Bedeli sayfa başına bir sorgu; `getJobsOfCurrentUser`'da küme pratikte hep boş (kimse kendi açtığı ilana başvurmuyor) ama kuralı uca göre dallandırmak, tam da kaçınılan sessiz ayrışmayı geri getirirdi.

**ADR-0003'ün kuralı değişmedi, yalnız uygulanışı tek yere indi.** `JobAddressVisibility` `EXACT`'ı ilan sahibine, etkin atamalı işçiye ve `addressPublic` şirket ilanına veriyor; "şirketin herhangi bir yetkilisi" o listede yok, yani ilanı açmayan yetkili adresi herkese açılmamış bir ilanda **yaklaşık** konum görmeye devam ediyor. Değiştirmek `GET /jobs/{id}` ve aramayı da etkilerdi, yani ADR'yi devralacak ayrı bir karar işi.

**Adres iddialarının nerede ölçüldüğü — ve birim testinin neyi ölçemeyeceği.** `JobSummaryDto`'da bulanıklaştırmayı **mapper** yapıyor: `toJobSummaryDto` `addressPrecision`'ı sabit `APPROXIMATE` üretiyor ve koordinatı `qualifiedByName = "approximateCoordinate"` ile iki haneye yuvarlıyor. `protect(...)` özet üzerinde yalnızca **EXACT'a yükseltiyor**, hiçbir şeyi bulanıklaştırmıyor. Sonucu şu: mapper'ı mock'layan bir birim testi bulanıklaştırmayı **ölçemez** — ilk sürümde stub `JobAddressVisibility.approximateCoordinate`'i çağırarak mapper'ı taklit ediyordu, yani `protect(...)` tamamen silindiğinde APPROXIMATE iddiaları yeşil kalıyordu (mutasyonla ölçüldü: yalnız EXACT dalları düşüyordu). Beklenti ölçtüğü üretim kodundan kuruluyordu. Stub artık yalnız `id` taşıyan bir özet döndürüyor: sonuçtaki her adres değeri `protect`'e atfedilebiliyor, EXACT dalı yükseltmeyi, APPROXIMATE dalı da **yükseltmemeyi** (koordinat `null` kalıyor) ölçüyor ve `protect` silindiğinde iki dal da kırmızıya dönüyor. Gerçek bulanıklaştırma `CompanyJobListingIT`'de, gerçek mapper'la ölçülüyor.

**IT üç dalı da HTTP üzerinde sabitliyor:** ilanı açmayan yetkili yaklaşık konum alıyor (`40.99`/`29.03`), `address_public=true` yapılınca kesin konum alıyor, ve **ilanı açan yetkili** `address_public=false` iken kesin konum alıyor. Sonuncusu bir süre yalnız mapper'ı mock'layan birim testinde vardı; PR gövdesi ise dalın uçtan uca ölçüldüğünü ima ediyordu. Üç ucun ortak süzgeci olduğu için `JobAddressVisibilityIT` de `/users/me/jobs`'un `EXACT` dalını bağlıyor — `listJobs`'tan `protect` silindiğinde üç ucun testi birden düşsün diye.

**`POST /jobs/{jobId}/cancel` kapatmadan farklı.** `close` yalnızca yeni başvuruyu durdurur, `cancel` ilanı `CANCELLED` yapar ve başvuranlara bildirim gönderir. Kayıt silinmez — başvuru geçmişi korunmalı, bu yüzden `DELETE` tercih edilmedi.

**İptal, devam eden atama varken reddedilir.** `PENDING_CONFIRMATION`, `CONFIRMED` ya da `IN_PROGRESS` bir atama varsa 409 döner. Aksi hâlde ilan iptali, emanetteki parayı ve iptal politikasını (§2p) arkadan dolanan ikinci bir yol olurdu; o durumda atamanın kendi iptal akışı kullanılır.

**`GET /users/{userId}` kimlik doğrulaması ister, anonime açık değil.** Deneyim, değerlendirme ve rozet uçları `permitAll` ama temel profil (`about`, il/ilçe) bir adım daha kişisel; giriş yapmış kullanıcıya açık. **Tam adres hiçbir zaman dönmez**, yalnızca il ve ilçe.

**`cvUrl` ilişkiye bağlı.** Profil sahibinin kendisi ya da o kişinin başvurduğu bir ilanın işvereni görür; diğerlerinde `null` kalır. Aynı kural indirme yolunda da var (§2ab), yani adres sızsa bile dosya inmez — iki katman.

**Bu uç `PublicProfileManager`'da, `UserManager`'da değil.** `UserManager → JobApplicationService → JobService → CompanyService → UserManager` döngüsü çıkıyordu. Okuma projeksiyonu ayrı bir orkestratöre alındı; `MyWorkManager` (§2al) ve `StatisticsManager` (§2am) ile aynı şekil: hiçbir DAO'ya uzanmaz, servisleri çağırır.

**Bireysel ilan sigorta zorunluluğu taşıyamaz — bilerek.** `createIndividualJob` `insuranceRequired`'ı sabit `false` yazıyor ve `requiresInsurance` işaretli bir kategoride bireysel ilan hiç açılamıyor. SGK yükümlülüğü işverene ait; bir günlük iş için bireysel işverenden bunu bekleyemeyiz. Bu yüzden `CreateIndividualJobDto`'da alan yok, olmayacak.

**`CompanyDto.approved` alan adı `approved`, `isApproved` değil.** `CompanyMembershipDto.approved` zaten öyle; aynı kavramın iki adı olmasın diye. §2m'deki Lombok/Jackson tuzağı burada geçerli değil çünkü DTO alanı `approved`, türetme belirsizliği yok.

**`PUT/DELETE /companies/{companyId}/logo`** profil fotoğrafıyla aynı kalıpta; logo yalnızca şirket kurulurken verilebiliyordu, sonradan hiç değiştirilemiyordu.

### Gelen başvurular — ilanlar arası gelen kutusu (#233)

İşveren başvuruları yalnız ilan bazında (`GET /jobs/{jobId}/applications`) görebiliyordu; on ilanı olan
işveren on ekran geziyordu. `GET /users/me/received-applications` sahibi olduğun bireysel ilanlar ile
yetkilisi olduğun şirketlerin ilanlarına gelen başvuruları **tek sayfalı** listeliyor. Uç
`EmployerToolsController`'da (`/users/me` altındaki işveren araçları), kuralı `SecurityConfig`'in "employer
tool endpoints" bloğunda.

**Ad neden `received-applications`.** `/users/me/applications` **gönderdiğin** başvurular (§2s); işverenin
gelen kutusu onunla karıştırılamayacak bir ad taşımalı. `/users/me/jobs/applications` reddedildi: `/users/me/jobs`
yalnız ilanı **açanın** listesi (`findAllByCreatorId`, §2ap), gelen kutusu ise açmadığın şirket ilanlarını da
kapsıyor — iç içelik yalan söylerdi (§0b). Kardeşleri `saved-jobs`, `followed-companies`, `job-templates` ile
aynı biçim. İki ucun OpenAPI açıklaması birbirine işaret ediyor (gönderilen ↔ gelen), Swagger'da yan yana duran
iki "başvurular" ucu karıştırılmasın diye.

**Kapsam yeni bir kural değil, başvuru uçlarının ilan başına kuralının sorgu hâli.** Başvuruyu gösteren ve
cevaplayan uçlar (`getJobApplicationsByIdJobId`, `assertCurrentUserIsEmployerOf`) ilan tipine göre **tek** soru
soruyor: şirket ilanında "şirketin yetkilisi misin", bireysel ilanda "ilanı sen mi açtın" — şirket ilanında
yaratıcıya hiç bakılmıyor. Yetkili tanımı her yerde `CompanyContactService.isUserAContactOfCompany` =
`company_contacts`'ta silinmemiş bir satır, rol ayrımı yok. "Sahibi **ya da** yetkilisi" diyen tek kural
`JobManager.requireJobEditor` ve o ilanı **düzenlemeyi** yönetiyor, başvuruların görünürlüğünü değil. İlk sürüm
gelen kutusunu ona göre yazmıştı ve ölçülen kusur şuydu: şirket ilanını açıp sonra `company_contacts`'tan
çıkarılan kullanıcı gelen kutusunda o ilanın adaylarını görmeye devam ederken derin bağlantı verilen inceleme
ekranı (`GET /jobs/{jobId}/applications`) 400, `respond` 404 veriyordu — aynı kaynak hakkında iki uç iki farklı
şey söylüyordu (§2ap'nin kusur tanımı). Sayfalı sorgu bu yüzden ilan başına kuralı birebir taşıyor:
`CompanyContactService.getCompanyIdsOfContact` (`CompanyContactDao.findCompanyIdsByUserId`) kullanıcının şirket
kimliklerini **aynı entity** üzerinden veriyor — `@SQLRestriction` çıkarılmış yetkiliyi düşürüyor — ve
`JobApplicationDao.RECEIVED_SCOPE` `(a.job.company is null and a.job.creator.id = :viewerId) or
(a.job.company is not null and a.job.company.id in :companyIds)` diyor. Üyelik tanımı böylece `CompanyContact`
aggregate'inde tek yerde kalıyor; `JobApplicationDao` `company_contacts`'ı hiç tanımıyor. Hiç şirketi olmayan
kullanıcıda liste boş gidiyor ve Hibernate 7 boş `in` listesini `1=0` olarak çiziyor — varsayılmadı,
`aStrangerSeesNothing` ile gerçek Postgres'te ölçüldü. Yüklemin biçimini
`theScopePredicateGrantsCreatorsOnlyIndividualJobsAndContactsOnlyCompanyJobs` birim düzeyinde, davranışını
`aFormerContactLosesTheCompanyJobHeOpenedAndKeepsHisIndividualJobs` gerçek Postgres'te sabitliyor: çıkarılmış
yaratıcı bireysel ilanının adaylarını görür, açtığı şirket ilanınınkileri görmez ve aynı ilanda inceleme ekranı
da onu reddeder; şirketin güncel yetkilisi o ilanı görür.

**Kapsam dışı `jobId`/`companyId` süzgeci 404 değil boş sayfa döndürür ve var olmayan kimlikle aynı yanıttır.**
Süzgeçler kapsam yükleminin **içinde** `and` ile bağlı; ayrı bir "bu ilan senin mi" dalı yok, dolayısıyla
gizlenecek bir bit de yok (§2s2'nin liste ucu gerekçesi, §2d2). `aJobOutsideTheScopeIsIndistinguishableFromAJobThatDoesNotExist`
iki gövdeyi `timeStamp`/`requestId` düşüldükten sonra birebir karşılaştırıyor. Şirket varlığı zaten herkese
açık (§2ap) ama bu uçta onu doğrulayan bir dal açmak yalnız ikinci bir sorgu ve ikinci bir yanıt şekli katardı.

**Varsayılan sıra bekleyenleri öne alır ve kararlıdır:** `APPLIED` önce, sonra `applicationDateTime desc`,
eşitlikte `id desc`. Bu sıra `Sort` ile ifade edilemez (durum alfabetik sıralandığında `ACCEPTED` `APPLIED`'ın
önüne geçer), o yüzden `findReceivedPendingFirst` `order by case when a.status = APPLIED then 0 else 1 end, …`
taşıyor. İstemci `sort` verirse (`applicationDateTime`, `answeredDateTime`, `status` — `/users/me/applications`
ile paylaşılan **tek** `APPLICATION_SORTABLE` sabiti; iki uç aynı entity'nin kolonlarını sıralıyor, küme ikiye
kopyalanırsa biri sessizce genişler) varsayılan **tümüyle** yerini bırakıyor ve `findReceived`'e `id desc`
eklenmiş sıra gidiyor; bilinmeyen alan 400 (`PageableSanitizer`, §2l). İki DAO metodu olmasının sebebi Spring
Data: `@Query` zaten `order by` taşıyorsa `Pageable`'ın sırasını **onun arkasına** ekliyor, yani tek sorguda
istemci sırası hep ikincil kalırdı. İki metot **tek** `RECEIVED_SCOPE` sabitini paylaşıyor — kapsam yüklemi iki
yerde ayrışamaz (bu deponun baskın kusur sınıfı, §2ap `listJobs` gerekçesinin aynısı);
`bothPageQueriesShareTheOneScopePredicate` bunu iki metodun `@Query` anotasyonundan okuyor.

**Satır şekli `ReceivedApplicationDto`:** `id`, `status`, `applicationDateTime`, `answeredDateTime`, `notes`,
`applicant` (`SafeUserDto` — `JobApplicantDto` ile aynı iç DTO, kişisel veri genişletilmedi), `jobId`,
`postingTitle`, `jobStatus`, `companyId`/`companyName` (bireysel ilanda `null`), `assignmentId`. `jobId` + `id`
mevcut başvuru inceleme ekranına, `companyId` şirket bağlamına derin bağlantı için (is-koala/koala-web#147).
**İlan başlığı `jobTitle` değil `postingTitle`:** aynı ekranın kardeş DTO'su `JobApplicantDto.jobTitle` adayın
**mesleğini** (`User.jobTitle`, §2m) taşıyor; iki DTO'da aynı ad iki farklı şey demek olurdu, istemci ikisini
yan yana çiziyor. `cvUrl` bilerek yok: inceleme ekranı zaten veriyor ve gelen kutusu için `user.cv` join'i
boşuna olurdu.

**N+1 ölçüldü, varsayılmadı.** Graf `user`, `user.profilePicture`, `job`, `job.company`, `assignment`. Sonuncusu
ilk sürümde yoktu ve `theNumberOfStatementsDoesNotGrowWithTheNumberOfRows` onu yakaladı: bir satırlık sayfa 5,
beş satırlık sayfa 9 sorgu — `JobApplication.assignment` ters yönlü `@OneToOne(mappedBy)` olduğu için Hibernate
null olup olmadığını öğrenmek üzere satır başına `job_assignments` okuyor (`findAllByJobId`'nin `assignment`
taşımasının sebebi de bu). Şimdi ucun kendisi istek başına **üç** sorgu (üyelik kimlikleri, sayfa, sayım) ve
satır sayısından bağımsız; Hibernate `Statistics`'in ölçtüğü sayı ise **dört**, çünkü `MinimumAppVersionFilter`
her istekte bir `AppSetting` satırı okuyor (§2as) ve o da aynı `SessionFactory`'den geçiyor. İlk sürüm testi
`<= 4` yazıp belgeye "üç" demişti — sayı eşitsizlikle değil ölçülen sabitle sabitlendi:
`theNumberOfStatementsDoesNotGrowWithTheNumberOfRows` 1 ve 5 satırlık sayfayı karşılaştırıyor **ve** beş
satırlık sayfada sayıyı `4`'e eşitliyor; mesajı dördün bileşimini adlandırıyor. Ön koşul: sayım sorgusu yalnız
**dolu** sayfada koşar (Spring Data eksik ilk sayfada sayımı atlar), o yüzden iki istek de tam sayfa boyutu
kadar satır döndürmeli — kurgu değişip sayfa eksik kalırsa ölçüm 3'e düşer ve test bunu "N+1 kapandı" diye
değil eşitsizlik diye bildirir. `EagerFetchCoverageIT` muafiyeti: DTO şirketten yalnız kimlik ve ad çiziyor,
`logo` gerekmiyor.

**Sabitleyen testler.** `ReceivedApplicationsAuthorizationTest` (9) manager'ın ve yüklemin sözleşmesini tutuyor:
kapsam yalnız oturum sahibi + `CompanyContactService`'in verdiği küme, yüklem yaratıcıya yalnız şirketsiz ilanı
ve yetkili kümesine yalnız şirketli ilanı verir, iki sayfa sorgusu tek `RECEIVED_SCOPE`'u taşır, şirketsiz
kullanıcı boş kümeyle gider, şirket süzgeci kapsamı genişletmez, süzgeçler sorguya değişmeden ulaşır, sortsuz
istek bekleyen-önce sorgusuna sıralanmamış gider, istemci sırası tie-breaker alır, bilinmeyen sort sorgu koşmadan
reddedilir. `ReceivedApplicationsIT` (19) gerçek Postgres'te: yaratıcı kendi bireysel ve (hâlâ yetkilisi olduğu)
şirket ilanlarının hepsini görür, ilan açmamış yetkili şirketin başvurularını görür, **çıkarılmış yetkili**
açtığı şirket ilanını kaybeder ve bireysel ilanlarını korur (inceleme ekranı da aynı ilanda onu reddeder),
şirketin güncel yetkilisi çıkarılanın açtığı ilanı görür, yabancı hiçbir şey görmez, diğer işveren yalnız
kendininkini görür, üç süzgeç ve bileşimi, kapsam dışı ilan/şirket süzgecinin bilinmeyenle özdeş boş sayfası,
varsayılan sıra eşitlik dahil, istemci sırası iki yönde, bilinmeyen `status` değeri 400, bilinmeyen sort 400,
sayfa üstverisi, satır alanları (e-posta ve telefon **yok**), ve sorgu sayısının satır sayısından bağımsızlığı.

**`GET /jobs/{jobId}/applications`'ın 400/404 kusuru (§2ar3) buraya taşınmadı** — bu uç hiçbir dalda 404
üretmiyor, gizlenecek bir varlık kararı yok.

## 2aq. Kimlik doğrulama — sunucu artık doğrulamıyor

**NVİ KPS kapandı.** `https://tckimlik.nvi.gov.tr/service/kpspublicv2.asmx` bugün 302 ile hata sayfasına yönlendiriyor, o da 404. `RestTemplate` yönlendirmeyi takip ettiği için `postForObject` 404 HTML'ini string olarak döndürüyordu; içinde başarı işareti olmadığından `isVerified=false` çıkıyor ve **her kullanıcıya `IDENTITY_VERIFICATION_FAILED` (400)** dönüyordu.

Bu hata biçiminin kötülüğü şurada: "senin bilgilerin yanlış" ile "servis ölü" ayırt edilemiyordu. Ve `assertReadyToWork`'ün ilk şartı kimlik olduğu için sonuç **hiç kimsenin hiçbir işe başvuramaması** idi — tek bir ucun bozulması değil, başvuru zincirinin tamamının kopması.

**KPS çağrısı kaldırıldı, `KimlikPaylasimSistemiService` silindi.** Bunun yerine sunucuda Passive Authentication zinciri **yazılı** (`PassiveAuthenticationVerifier`, CSCA çıpa kümesi, challenge ucu) ama **bugün kapalı**: `IDENTITY_PASSIVE_AUTH_ENABLED` varsayılanı `false` ve production'da tanımlı değil. Bayrak kapalıyken `verifyChipEvidence` `IDENTITY_DOCUMENT_REVIEW_REQUIRED` fırlatır — beş beyan alanı (ad, soyad, TCKN, seri no, doğum tarihi) damgalanmaz. Ürün yolu dört TCKK fotoğrafı (yüz, ön, arka, yüzün altında) + personel onayı (is-koala/koala-backend#193); çip PA bu issue'da açılmaz.

**İstemci tarafı.** Flutter (#59, PR #73) NFC + MRZ ile `sod`/`dg1` (ve varsa DG11/DG15) **gönderebiliyor**; alanlar isteğe bağlı, bayrak kapalıyken yok sayılıyor. Yüz eşleştirme ve canlılık **yok**. Web'de NFC yok (koala-web#18); tarayıcıda çip kanıtı uydurulmaz.

**Beş alanlık `POST /users/me/identity-verification` ürün yüzeyi değildir.** Bayrak kapalıyken de checksum'ı geçen TCKN damgalanmaz. Çip kanıtı gönderilse bile PA kapalıyken damga yok — o uç yalnız `IDENTITY_PASSIVE_AUTH_ENABLED` açıkken çalışır. Kimlik damgası ya çipten (bayrak açık) ya personelin TCKK fotoğrafını onayından gelir.

Bayrağı bugün açmamak hâlâ doğru: açılırsa kanıt göndermeyen her istemci `IDENTITY_EVIDENCE_REQUIRED` alır. Belge yolu #193 ile açık: `POST /users/me/identity-documents` dört TCKK fotoğrafını (yüz, ön, arka, yüzün altında — #220) kuyruğa alır, `IDENTITY_REVIEW` izniyle personel `POST /admin/identity-verifications/{id}/approve` deyince `identityVerified` damgalanır. Fotoğraflar `GET /admin/identity-verifications/{id}/documents/{documentId}/download` ile kısa ömürlü imzalı adresten okunur (uyuşmazlık kanıtı kalıbı); kuyruk DTO'su depo anahtarı taşımaz. TCKN benzersizliği durur. Çip PA bu issue'da açılmaz.

**Onay dört belgeyi sunucuda yeniden doğrular (#232).** #220 dört fotoğraf slotunu sözleşmeye koydu ama
`approve` belgelere hiç bakmıyordu: eski iki fotoğraflı `PENDING` kayıt ya da depo anahtarı silinmiş bir
fotoğraf eksik kanıtla onaylanabiliyordu. Bu deponun tekrar eden kusuru (§2d2, §2k2): doğru bir sözleşme,
onu uygulaması gereken karar noktasında sessizce görmezden geliniyordu. `IdentityVerificationManager.approve`
artık `requireOpen`'dan hemen sonra `requireFourDocuments` çalıştırıyor: yüz, TCKK ön, TCKK arka ve TCKK
yüzün altında belgelerinin **dördü de** yoksa `ConflictException` → **409 `IDENTITY_REVIEW_DOCUMENTS_MISSING`**
(`identity.review.documents.missing`, iki dilde). Karar `IdentityReviewDto`'ya ya da istek gövdesine değil
veritabanındaki satıra bakıyor; onay ucu gövde okumuyor, yani dört id iddia eden sahte bir admin formu
sunucudaki kaydı değiştiremiyor. "Belge var" tanımı tek yerde (`isEvidence`: satır **ve** boş olmayan depo
anahtarı) ve personel indirme ucuyla paylaşılıyor — personelin açamadığı fotoğraf kanıt sayılmaz. **Ret yolu
eksik belgede açık kalıyor**, çünkü operatörün eksik kaydı kuyruktan düşürmesinin tek yolu o.

**Onay ve ret artık satırı kilitleyerek karar veriyor.** İki karar da `findById` ile kilitsiz okuyordu; onayla
ret yarışınca ikisi de `PENDING` görüp ikisi de yazıyordu ve `IdentityReviewApprovalIT`'in eşzamanlı testi
düzeltmeden önce tam bunu ölçtü — reddi bekleyen onay, ret commit olduktan sonra üstüne yazıp başarıyordu.
`IdentityVerificationDao.findByIdForUpdate` (`PESSIMISTIC_WRITE`, `ReportDao`/`DisputeDao` kalıbı) geldi;
`approve` ve `reject` durumu kilidin altında yeniden okuyor ve sonradan gelen karar
`IDENTITY_REVIEW_ALREADY_DECIDED` (409) alıyor. `koala-admin#81` iki kodu ayırt etmeli: `DOCUMENTS_MISSING`
"reddet ve yeniden yükleme iste" düğmesine, `ALREADY_DECIDED` kuyruğu tazelemeye yönlendirir.

**Sabitleyen testler.** `IdentityDocumentReviewRulesTest`: dört slotun her biri tek tek eksikken onay reddedilir
ve satır `PENDING` kalır, iki fotoğraflı eski kayıt onaylanamaz ama reddedilebilir, depo anahtarı boşalmış
fotoğraf kanıt sayılmaz, karar kilitli okumadan geçer (`findById` hiç çağrılmaz), retten sonra gelen onay ve
onaydan sonra gelen ret fail-closed. `IdentityReviewApprovalIT` aynı kuralları gerçek HTTP ve Postgres'te
koşuyor — sahte gövde, iki dil, ve kilitte bekleyen onayın ret commit olunca 409 alması dahil.
`IdentityDocumentDownloadContractTest.approvalCarriesNoClientSuppliedEvidence` onay ucunun `@RequestBody`
taşımadığını ve servis imzasının yalnız `(StaffPrincipal, UUID)` olduğunu sabitliyor.

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

**DG11 dış etiketi `0x6B` (ICAO 9303 Part 10); `0x6C` DG12'nindir.** Eski parser ve üç fixture `0x6C` kullanıyordu, yani testler kendi yanlışlarını yeşile boyuyordu. Gerçek TCKK DG11 dosyası `0x6B` ile başlar; etiket uyuşmazsa `AdditionalPersonalDetails.parse` ad döndürmez ve PA sessizce istekteki ada düşer. SOD hash'i ham baytlara baktığı için bu sapma hash doğrulamasını kırmaz, yalnız adı kör eder. Etiketler `DataGroupTags` tablosunda (DG1 `0x61`, DG11 `0x6B`, DG12 `0x6C`, DG14 `0x6E`, DG15 `0x6F`, SOD `0x77`). `IDENTITY_PASSIVE_AUTH_ENABLED` varsayılanı kapalı kalır; bu not bayrağı açmaz.

**TCKN MRZ'nin neresinde:** ICAO 9303 Part 5'e göre TD1 üst satırında 16-30. karakterler "optional data" ve Türk kimlik kartında TCKN oraya yazılıyor. Ayırıcı `<` ile başlayan da başlamayan da kabul ediliyor; dolgu karakterleri atıldıktan sonra alanın **tam 11 hane** kalması şart.

**Not (j) tuzağı.** Belge numarası 9 karakterden uzunsa ICAO, kontrol hanesinin yerine dolgu koyup numaranın devamını **optional data'nın başına** yazdırıyor. O durumda alan TCKN gibi görünen haneler içerebilir. Bu yüzden ayrıştırıcı önce 15. karakteri kontrol ediyor: dolgu ise alan TCKN taşımıyor demektir ve okuma reddediliyor. Türk kimlik kartında belge numarası 9 karakter, yani bu durum oluşmuyor — ama kanonik ICAO örneği tam olarak bu şekilde ve yanlış okumak başkasının belge numarasını TCKN sanmak olurdu.

TD1 olmayan bir MRZ reddediliyor — pasaportta (TD3) zaten TCKN yok.

**Ham kanıt saklanmıyor, bilerek.** Yakup `IdentityVerification`'ı önerdi ama DG1 tüm MRZ'yi (ad, soyad, belge no, uyruk, cinsiyet, son geçerlilik) taşıyor, SOD ise DG2'nin yani yüz fotoğrafının hash'ini. Bunları saklamak KVKK açısından gereksiz veri biriktirmek olur. Bunun yerine **sonucun kanıtı** saklanıyor: `users.identity_evidence_digest` (SOD'un SHA-256'sı) ve `users.identity_document_signer` (imzalayan sertifikanın subject'i). Denetimde "şu tarihte, şu imzalayanla, şu parmak iziyle doğruladık" cevabı verilebiliyor; çip dökümü tutulmuyor.

**Varsayılan kapalı ve fail-closed.** `identity.passive-authentication.enabled=false` iken davranış §2aq'daki gibi. Açıkken `sod`+`dg1` **zorunlu**; eksikse `IDENTITY_EVIDENCE_REQUIRED`. Güven çıpası yüklenmemişse hiçbir şey doğrulanmıyor — açılışta hata loglanıyor ve her doğrulama reddediliyor. Yani "açtım ama sertifika koymayı unuttum" hâli sessizce herkesi geçirmiyor.

**Active Authentication, Passive Authentication olmadan açılamaz (#154).** `identity.passive-authentication.active-authentication-required=true` verilip `identity.passive-authentication.enabled=false` bırakılırsa `IdentityVerificationProperties` doğrulaması uygulamayı açılışta düşürüyor. Böylece `IDENTITY_ACTIVE_AUTH_REQUIRED=true` değerinin çalışma anında sessizce etkisiz kalması mümkün değil; hata yalnızca iki yapılandırma anahtarını söylüyor, güven çıpası dizini gibi dağıtıma özgü değerleri taşımıyor. İki bayrağın varsayılanı da `false`; PA+AA birlikte `true` olduğunda bağlama geçerli.

**Eksik güven çıpasının public sözleşmesi değişmedi, operasyonel görünürlüğü değişti.** Çalışma anındaki `IDENTITY_NO_TRUST_ANCHORS` istemciye yine 400 dönüyor; fakat `GlobalExceptionHandler` bu tek doğrulama kodunu `BackendErrorMonitor`'a iletiyor. Böylece `koala.backend.errors{code="IDENTITY_NO_TRUST_ANCHORS",type="VALIDATION_EXCEPTION"}` sayacı artıyor ve Sentry açıksa aynı hata oraya da gidiyor. Diğer 400 doğrulama hataları operasyonel alarm üretmiyor.

**CSCA sertifikaları imajın içinde geliyor.** `src/main/resources/csca/` altında 12 Türkiye sertifikası duruyor (48 KB) ve classpath'ten yükleniyor. Bunlar **açık anahtar, sır değil** — repoda durmalarının sakıncası yok, üstelik hangi sürümde hangi çıpalara güvenildiği git geçmişinden okunabiliyor.

Bu tercih bilinçli: dizinden okusaydı prod'da volume bağlamak ve dosyaları elle kopyalamak gerekirdi, üstelik unutulduğunda hata açılışta değil ilk doğrulamada çıkardı. `identity.passive-authentication.trust-anchor-directory` hâlâ duruyor ama artık **ek** çıpa yüklüyor, tek kaynak değil — test ortamında ya da NVİ'den ayrı bir sertifika gelirse diye.

Liste üç ayda bir güncelleniyor; `scripts/fetch-csca-anchors.sh` çalıştırılıp commit edilmesi yeterli. Betik `scripts/` altında git'te izleniyor — unutulursa `PassiveAuthenticationPrepTest` kırmızıya döner. Telefona gömülemez — istemcide doğrulama zaten istemciye güvenmek demek.

**Sertifika iptali (CRL) kontrol edilmiyor.** `PKIXParameters.setRevocationEnabled(false)`. ICAO PA pratiğinde yaygın olan bu, ama eksiklik olarak bilinsin: iptal edilmiş bir Document Signer sertifikası hâlâ geçer. Kapatmak CRL dağıtım noktalarına erişim ve tazelik yönetimi ister.

**CSCA sertifikaları BSI master list'inden geliyor.** ICAO PKD indirmesi üyelik istiyor; Almanya BSI'ın yayınladığı master list kamuya açık ve ticari kullanıma izinli. `scripts/fetch-csca-anchors.sh` listeyi indirip imzasını açıyor, `C=TR` olanları DER olarak çıkarıyor. **Mayıs 2026 listesinde Türkiye'nin 12 CSCA sertifikası var:** dördü `CN=CSCA Turkey, O=Turkey Gov` (2013 ve 2016), sekizi `CN=Passport CSCA Turkey` (2018, 2021, 2024). Liste üç ayda bir güncelleniyor, betik düzenli çalıştırılmalı.

**Sertifikaların sekizi explicit EC parameters kullanıyor** ve JDK'nın varsayılan `CertificateFactory`'si bunları `Only named ECParameters supported` ile reddediyor — üstelik reddedilenler en güncel olanları. Bu yüzden hem `CscaTrustStore` hem zincir doğrulaması **açıkça BouncyCastle sağlayıcısını** kullanıyor. Varsayılan sağlayıcıyla 12 sertifikanın yalnızca 4'ü yükleniyordu; sessizce eksik güven kümesiyle çalışmak, doğru kartı reddetmek demekti.

**Kimlik kartı hangi CSCA'ya zincirleniyor — cevaplandı (6 Eylül 2026).** Gerçek bir kartla değil, kartı okuyan bir uygulamanın (ReadID Me) teşhis ekranından öğrenildi: Document Signer'ın issuer'ı `CN=T.C. Seyahat Belgesi Kök Sertifika Hizmet Sağlayıcısı – Sürüm 1, OU=Nüfus ve Vatandaşlık İşleri Genel Müdürlüğü, O=Türkiye Cumhuriyeti İçişleri Bakanlığı, C=TR`.

**Paketlenen ilk 12 çıpanın hiçbiri bu değildi.** Sekizi `CN=Passport CSCA Turkey`, dördü `CN=CSCA Turkey`; hepsi pasaport kökleri. Kimlik kartının kökü ayrı ve BSI master list'inde yok — ReadID de (ICAO master list'iyle) zincir kuramadı, yani eksiklik bizim listemize özgü değildi.

**Kök NVİ'nin kendi açık deposunda yayımlanıyor:** `https://sbdepo.tckk.gov.tr/`. Üçü de indirildi, SHA-256 parmak izleri sayfada yayımlananla doğrulandı ve `csca/` altına kondu:

| dosya | kök | geçerlilik |
|---|---|---|
| `csca-tr-12.der` | Seyahat Belgesi Kök — Sürüm 1 | 2014-08-25 → **2029-08-25** |
| `csca-tr-13.der` | Seyahat Belgesi Kök — Sürüm 2 | 2023-02-14 → 2038-02-10 |
| `csca-tr-14.der` | Seyahat Belgesi Kök **Mavi Kart** — Sürüm 1 | 2022-08-27 → 2037-08-27 |

Sürüm 1'in doğru kök olduğunun ikinci kanıtı tarihte: karttaki DS sertifikası `2029-08-25 07:46:43 GMT`'de bitiyor, `csca-tr-12` de **aynı saniyede**. Bir DS sertifikası kökünden uzun yaşayamaz.

`BundledTrustAnchorsTest.theTravelDocumentRootThatSignsIdentityCardsIsBundled` bu çıpayı SHA-256 parmak iziyle sabitliyor. **Bu testi doğrularken tuzak var:** `target/classes/csca/` bayat kalıyor, çıpayı kaynak ağacından silmek testi kırmızıya döndürmüyor. `rm -rf target/classes/csca` gerekiyor — §1'in `rm -rf target/test-classes` uyarısının kaynak dosyalar için karşılığı.

**`csca-tr-00` ve `csca-tr-03`'ün süresi 2026-07-28'de doldu.** Liste üç ayda bir güncelleniyor; `scripts/fetch-csca-anchors.sh` artık git'te. Geri alma ve tazeleme: `docs/runbooks/identity-passive-authentication.md`.

**Bayrak hâlâ kapalı.** `IDENTITY_PASSIVE_AUTH_ENABLED` açılmıyor: gerçek kart dökümü (#70) ve kanıt gönderen istemci sürümü (koala#59) olmadan her doğrulama ya `UNTRUSTED_DOCUMENT_SIGNER` ya da `EVIDENCE_REQUIRED` ile durur. Test kaynakları da bayrağı açmaz. Açma ve geri alma yolu runbook'ta; geçiş kararı (mevcut `identityVerified` hesapları) hâlâ ürün tarafında, bu yüzden buraya yazılmadı. Bayrak kapalıyken ürün yüzeyi belge incelemesidir (`DOCUMENT_REVIEW_REQUIRED`), beş alanlık form değil.

**Gerçek kartın teşhisinden çıkan diğer cevaplar:** erişim kontrolü **BAC** (PACE şart değil), veri grupları **1, 2, 5, 11, 12, 15** (DG11 ve DG15 ikisi de var), **Active Authentication çalışıyor** (imza doğrulandı), Chip Authentication yok, LDS 1.7, bütün DG hash'leri SOD ile eşleşiyor. Yani `sod`/`dg1`/`dg11`/`dg15` zincirinin tamamı gerçek kartta karşılığını buluyor; eksik olan tek şey çıpaydı.

**Kalan doğrulama:** bu çıpalarla `RealChipVerificationTest`'in gerçekten `SONUC : DOGRULANDI` vermesi henüz görülmedi — ham `sod.b64`/`dg1.b64` dökümü gerekiyor (is-koala/koala-backend#70).

**Gerçek kart dökümü tek komutla denenir:**

```bash
KOALA_CHIP_DIR=/tmp/chip KOALA_CSCA_DIR=src/main/resources/csca ./mvnw test -Dtest=RealChipVerificationTest
```

`KOALA_CHIP_DIR` içinde `sod.b64` ve `dg1.b64` beklenir (`RealChipVerificationTest:28-29` yalnız bu ikisini okur; DG11'in varlığı `kartta` teşhis satırından görünür) — base64, tek satır. İki değişken de verilmezse test sessizce atlanır, yani CI'ı etkilemez. Çıktısında iki teşhis satırı var: **`ds issuer`** kimlik kartının hangi CSCA'ya zincirlendiğini söyler (doğrulama başarısız olsa bile), **`kartta`** ise SOD'un listelediği veri gruplarını yazar — yani DG11'in (ulusal karakterli ad) ve DG15'in (Active Authentication) o kartta bulunup bulunmadığı tek koşuda görünür.

**Passive Authentication tek başına kopyalamayı engellemez.** Bir kez okunan SOD ve DG1 sabittir; kaydeden biri sonsuza kadar yeniden gönderebilir. Bu yüzden **Active Authentication** eklendi.

`POST /users/me/identity-verification/challenge` sunucunun ürettiği 8 baytlık tek kullanımlık sayıyı döndürür; istemci onu çipe imzalatır ve `dg15` + `challenge` + `activeAuthenticationSignature` ile geri gönderir. Özel anahtar çipten hiç çıkmadığı için kopya kart bu imzayı üretemez.

**Sayı tek kullanımlık ve süreli.** `identity_challenges` tablosunda tutulur, `consumed_at` damgalanır, `challenge-validity-minutes` (10) sonra geçersizdir. İmza doğrulanamazsa satır tüketilmiş sayılmaz — başarısız deneme kullanıcının sayısını yakmaz.

**İki imza ailesi destekleniyor ve algoritma seçimi DG14'ten başlıyor (#253).** Gerçek TCKK ile 13 Eylül 2026 cihaz testinde PA geçti, AA `the chip did not sign the server challenge` ile düştü: sunucu ECDSA için yalnız SHA-256 düz, SHA-1 düz ve SHA-256 DER'i deniyordu, DG14'ü hiç okumuyordu. ICAO 9303 Part 11 §6.1'e göre ECDSA tabanlı AA'da özet algoritmasını **DG14 `ActiveAuthenticationInfo.signatureAlgorithm`** (protokol OID `2.23.136.1.1.5`) bildirir. Bugünkü sıra:

1. `VerifyIdentityDto.dg14` (base64, isteğe bağlı) gelmişse `ActiveAuthenticationInfo.from` DG14'ü (`0x6E`, BER uzunluk) açıp `SecurityInfos` içindeki AA girdisinin `signatureAlgorithm` OID'ini okur. Tanınan OID'ler BSI TR-03111 düz imzalar (`ecdsa-plain-SHA1/224/256/384/512/RIPEMD160`) ve X9.62 DER imzalar (`ecdsa-with-SHA1/224/256/384/512`); bildirilen şema **ilk aday** olur. Tanınmayan OID, okunamayan DG14 ya da RSA anahtarında bildirilen EC OID'i doğrulamayı engellemez, yalnız tanı satırına düşer.
2. Ardından anahtar tipine göre yelpaze: EC için SHA-256/1/224/384/512 × (düz `r‖s`, DER) — on aday; düz kodlamada imza uzunluğu tam `2·⌈n/8⌉` bayt olmak zorunda (herhangi bir çift uzunluk değil). RSA için ISO 9796-2 şema 1 × SHA-1/224/256/384/512 × (açık `0xxxCC` trailer, örtük `0xBC` trailer) — on aday; her aday önce M1'i imzadan geri alır (`updateWithRecoveredMessage`), blok **tam kurtarmalı** ise (sentetikte M1 = challenge, kısa nonce'ta M1 = nonce‖challenge) kurtarılan mesajın challenge ile **bitmesini** şart koşup hash'i doğrular, **kısmi kurtarmalı** ise (gerçek kartın yaptığı: M1 kapasite dolusu nonce, M2 = challenge) challenge'ı M2 olarak ekleyip doğrular. Trailer `0xBC` SHA-1'i, `0xxxCC` diğer özetleri işaret ettiği için yanlış özet denemesi anında düşer (`ISOTrailers` uyuşmazlığı istisna atar).

   **Neden her özet için iki `ISO9796d2Signer`.** BC 1.80'de `implicit` bayrağı yalnız üretimde trailer seçmiyor, doğrulamada da `mBuf`'ı boyutluyor: `implicit=true` → `blok − özet − 2`, `implicit=false` → `blok − özet − 3`. İki baytlık açık trailer taşıyan bir imzada M1 `blok − özet − 3` bayt geri alınır; `implicit=true` ile kurulmuş doğrulayıcının `mBuf`'ı bir bayt uzun kaldığı için `isSameAs` dizin dışına taşar ve aday `false` düşer — yani yalnız `implicit=true` deneyen ilk sürümde SHA-224/256/384/512 RSA yolu **ölüydü** (PR #260 incelemesi). Tersi de geçerli: `0xBC` imzada `implicit=false` doğrulayıcının `mBuf`'ı bir bayt kısa kalır ve `arraycopy` taşar. Bu yüzden her özet için iki doğrulayıcı; testler BC ile hem açık trailer'lı (SHA-224/256/384/512, ICAO uzunluğunda nonce) hem `0xBC` (kapasite dolusu ve 100 baytlık nonce) imza üretip ikisinin de geçtiğini sabitliyor.
3. İlk doğrulanan aday yeter; hiçbiri doğrulamazsa `identity.chip.not.present`.

**Anahtar artık JCA `KeyFactory` yerine BC'nin hafif API'siyle okunuyor:** `SubjectPublicKeyInfo` → `PublicKeyFactory.createKey`. Sebep CSCA sertifikalarındaki ile aynı: TCKK ekosistemi **açık parametreli EC anahtarı** kullanabiliyor ve `X509EncodedKeySpec` yolu bu anahtarlarda güvenilmez. Aynı okuma adlı eğriyi (brainpool/NIST/secp) de açık parametreliyi de kabul ediyor; doğrulama `ECDSASigner` ve `ISO9796d2Signer` ile yapılıyor, anahtar bir kez çözülüyor (eskiden `verifyRsa` her denemede yeniden çözüyordu).

**LDS dosya zarfı tek yerde açılıyor: `LdsFiles.unwrap(file, tag)`.** DG15 (`0x6F`), DG14 (`0x6E`) ve SOD (`0x77`) aynı yardımcıdan geçiyor. Eski `unwrapDataGroup15` ofseti doğru hesaplıyordu ama **bildirilen uzunluğu hiç okumuyordu**: dosyanın sonuna kadar her şeyi anahtar sayıyordu, yani zarfın ardındaki dolgu baytı `SubjectPublicKeyInfo` ayrıştırmasını `extra data` ile düşürüyordu; belirsiz uzunluk (`0x80`) ve dört bayttan uzun formlar da sessizce yanlış ofset veriyordu. Şimdi kısa form ve `0x81`/`0x82`/`0x83` doğru çözülüyor, bildirilen uzunluk kadar dilimleniyor, dosyadan uzun bildirim `identity.chip.key.unreadable` (SOD'da `identity.sod.unreadable`) alıyor. **Uzunluk oktedi üçle sınırlı ve karşılaştırma `length > file.length - offset` biçiminde — ikisi de bilinçli.** İlk sürüm dört oktet kabul edip `offset + length > file.length` yazıyordu; `6F 84 7F FF FF FA …` gibi bir girdide toplam `int`'te negatife sarıyor, koruma geçiyor ve `Arrays.copyOfRange` ~2 GiB ayırmaya kalkıp `OutOfMemoryError` fırlatıyordu — `catch (Exception)` sarmalayıcıları da `GlobalExceptionHandler` da bunu yakalamaz, ve SOD zarfı kripto işinden önce açıldığı için oturumu olan herhangi bir kullanıcı `sod`/`dg14`/`dg15` ile tetikleyebilirdi (PR #260 incelemesi). LDS dosyaları 16 MiB'ı aşmadığı için üç oktet yeter; belirsiz uzunluk (`0x80`), `0x84` ve üstü reddediliyor. `LdsFilesTest` üç uzun formu, dolguyu, `0x83` ile taşan uzunluğu, `0x84` reddini ve taşma girdisinin OOM değil `IllegalArgumentException` ile bitmesini tek tek sabitliyor.

**DG14 de SOD'a bağlanıyor — DG15 ile aynı kapı.** `PassiveAuthenticationVerifier.verify` beş parametreli oldu (`sod`, `dg1`, `dg11`, `dg14`, `dg15`); DG14 gönderilmişse hash'i `LDSSecurityObject`'teki 14 numaralı girdiyle karşılaştırılıyor, tutmazsa `identity.data.group.tampered`; SOD DG14 listelemiyorsa `identity.security.info.unsigned`. Ayrı bir anahtar, çünkü hâl farklı: kurcalanmış bilgi ile kartın hiç taşımadığı bilgi. DG14 algoritma seçimini yönlendirir ama doğrulamayı **gevşetmez** — her aday aynı SOD'a bağlı DG15 anahtarıyla kriptografik olarak doğrulanır; bu yüzden DG14 doğrulaması bütünlük kapısıdır, güvenlik sınırı DG15'te kalır. DG14 gönderilmemişse doğrulayıcı hiçbir şey yapmaz (DG15'teki gerekçeyle aynı: "kart taşımıyor" ile "istemci göndermedi" ayırt edilemez). **Not:** ReadID teşhisine göre gerçek TCKK'nın veri grupları 1, 2, 5, 11, 12, 15 — DG14 **yok**; yani TCKK'da algoritmayı yelpaze bulacak, DG14 yolu ileride DG14 taşıyan belgeler için. Mobil tarafı is-koala/koala#158.

**Başarısızlıkta tek tanı satırı, PII'siz.** `IdentityChallengeManager` zaten `Active authentication failed for user {id}: {detail}` yazıyordu; `detail` artık şu biçimde: `the chip did not sign the server challenge (key=EC, curve=brainpoolP256r1, bits=256, signatureBytes=64, dg14=absent, attempted=10)`. Alanlar: anahtar ailesi, eğri adı (adlı eğri BC tablosundan — secp256r1 `prime256v1` olarak görünür —, açık parametreli anahtar eşleşirse `explicit(brainpoolP256r1)`, eşleşmezse `explicit`) ve bit uzunluğu, imza bayt sayısı, DG14 durumu (`absent` / `unreadable` / `no-aa-info` / `<oid> (SHA384withPLAIN-ECDSA)` / `<oid> (unrecognised)` / `<oid> (ignored for RSA)`), denenen aday sayısı. **Challenge, imza baytları ve DG içerikleri bu satıra hiç girmez**; `IdentityChallengeRulesTest` satırı yakalayıp hem alanları hem yokluğu doğruluyor. Okunamayan anahtarda da benzer bir kuyruk var: `(dg15Bytes=N, dg14=…)`. Yusuf gerçek kartla tekrar denediğinde bu satır kartın hangi anahtar/eğri/imza uzunluğuyla geldiğini söyleyecek — sonraki adım o satıra göre atılır.

**Doğrulama anahtarı artık gerçekten SOD'a bağlanıyor — ama bu paragraf iki kez yanlıştı, o yüzden nasıl yapıldığı yazılı.** İlk metin *"DG15'in hash'i SOD'da, yani Passive Authentication zaten onu imzalı kabul ediyor"* diyordu ve kod bunu **yapmıyordu**; ikinci metin kusuru doğru anlatıyordu ama düzeltme is-koala/koala-backend#74 ile indi.

**Ne yapılıyor.** `PassiveAuthenticationVerifier.verify` dört parametreli bir aşırı yükleme kazandı (`sod`, `dg1`, `dg11`, `dg15`; #253 ile araya `dg14` girdi, beş parametreli biçim asıl olan) ve `UserManager.verifyChipEvidence` istekten çözdüğü `dg15`'i artık **oraya** veriyor. Doğrulayıcı, DG1 ve DG11 için yazılmış desenin aynısını uyguluyor: DG15'in hash'i `LDSSecurityObject`'teki 15 numaralı girdiyle karşılaştırılıyor, tutmazsa `IdentityMessages.DATA_GROUP_TAMPERED`. Kontrol PA zincirinin **içinde**, yani `UserManager`'ın `verifyChipPresence`'ı çağırdığı satırdan önce; doğrulanmamış bir anahtar `ActiveAuthentication.verify`'a hiç ulaşmıyor.

**Anahtar gönderilmediğinde doğrulayıcı hiçbir şey yapmıyor, ve bu bilinçli.** "Kart DG15 taşımıyor" ile "istemci DG15 göndermedi" burada ayırt edilemez ve ikisi de saldırı değil; kararı veren tek yer `active-authentication-required` bayrağı (`verifyChipPresence`). **Bayrak kapalıyken DG15'siz bir kart yüzünden kimse reddedilmemeli** — bu, gerçek kart teyidi gelmeden (is-koala/koala-backend#70) akışı bozmamanın şartı.

**Ama DG15 gönderilmiş ve SOD onu imzalamamışsa reddediliyor: `identity.chip.key.unsigned`.** Fail-closed yön bu: aksi hâlde saldırgan SOD'da hiç bulunmayan bir veri grubu göndererek kontrolü atlar — hash karşılaştırması yapılacak bir "beklenen" değer olmadığı için sessizce geçerdi. `DATA_GROUP_TAMPERED`'dan **ayrı bir anahtar** kullanılıyor çünkü iki hâl farklı: biri "gönderdiğin anahtar kartın imzasındakinden başka" (kurcalama), diğeri "kartın imzası hiç anahtar taşımıyor, gönderdiğin şey uydurma". Operatör ve istemci ikisini ayırt edebilmeli.

`ActiveAuthenticationTest`'in *"başka çipin anahtarı doğrulamaz"* kuralı bu boşluğu görmüyordu: sentetik testte anahtar sabit veriliyor, oysa üretimde anahtarı istemci seçiyor. Boşluğu kapatan testler `PassiveAuthenticationTest` içinde ve **düzeltmeden önce yazılıp kırmızıya döndüğü görüldü**: geçerli `sod`+`dg1` ile saldırganın kendi anahtar çiftinden gelen DG15 reddedilir, SOD'un hiç imzalamadığı DG15 reddedilir, kartın kendi DG15'i doğrulanır, DG15 gönderilmeyen okuma (kart taşısın ya da taşımasın) bozulmadan geçer. `IdentityVerificationRulesTest` `UserManager` tarafını sabitliyor: çözülen `dg15` doğrulayıcıya **geçiriliyor**, ve doğrulayıcı reddederse `IdentityChallengeService`'e hiç dokunulmuyor.

**Bayrağın açılması hâlâ bekliyor.** Bu düzeltme #74'ün "Tuzak 2" yarısı; `IDENTITY_ACTIVE_AUTH_REQUIRED=true` için gerçek bir kartla DG15'in varlığının teyidi (is-koala/koala-backend#70) ve PA'nın açılması (is-koala/koala-backend#73) gerekiyor. **Bayrak açılana kadar tekrar oynatma açığı pratikte duruyor** — ama artık açıldığında gerçekten kapanacak.

**Varsayılan zorunlu değil** (`identity.passive-authentication.active-authentication-required=false`): TCKK'da DG15'in bulunduğu henüz gerçek kartla doğrulanmadı. İmza gelirse doğrulanır, gelmezse geçilir. Kart DG15 taşıdığı teyit edilince bayrak açılmalı — o noktaya kadar tekrar oynatma açığı duruyor.

**Challenge yönetimi `IdentityChallengeManager`'da, `UserManager`'da değil.** `AggregateBoundaryTest` `UserManager`'ın `IdentityChallengeDao`'ya uzanmasını reddetti; muafiyet eklemek yerine ayrı manager açıldı — `EmailVerificationManager` / `PhoneVerificationManager` ile aynı desen.

**Gerçek bir kartla doğrulanmadı.** Kripto zinciri testte **sentetik bir PKI ile uçtan uca kanıtlandı** — `TestChip` kendi CSCA'sını ve Document Signer'ını üretiyor, gerçek bir CMS `SignedData` imzalıyor, DG1'i TD1 biçiminde kuruyor. Gerçek Türk kimlik kartının SOD'unun bu kodla ayrıştığı **prod öncesi bir kartla teyit edilmeli**; bayrak o teyide kadar kapalı kalmalı.

## 2ar. Başvuru kapısı ve iptal önizlemesi

**Hazır olmayan işçi artık başvuramıyor.** Öncesinde kimlik doğrulaması, adres ya da IBAN'ı olmayan biri başvurabiliyordu; hata ancak **işveren başvuruyu kabul ederken** patlıyordu, çünkü alt üye işyeri kaydı o anda açılıyor. Yani işveren, kendi eksiği olmayan bir şey yüzünden hata alıyor ve düzeltemiyordu.

`applyToJob` üç şartı sırayla kontrol ediyor: **kimlik doğrulama → adres → IBAN.** Sıra keyfi değil, işçinin düzeltme sırası: kimlik doğrulanmadan IBAN istemek anlamsız. Her biri kendi mesaj anahtarını taşıyor, yani `errorCode` istemciye hangi ekrana yönlendireceğini söylüyor (`APPLICANT_IDENTITY_NOT_VERIFIED`, `APPLICANT_ADDRESS_REQUIRED`, `APPLICANT_BANK_ACCOUNT_REQUIRED`).

**Şartlar `createSubMerchantDtoForWorker`'ın kontrol ettikleriyle birebir aynı** — kapı ile asıl doğrulama ayrışırsa hata yine kabul anında patlar. Buraya bir alan eklenirse oraya da eklenmeli.

`JobApplicationManager` bu iş için `BankAccountService`'e bağlandı; `BankAccountManager` yalnızca kendi DAO'suna uzandığı için döngü çıkmıyor.

**İptal önizlemesi kayıt açmaz.** `GET /job-assignments/{id}/cancellation-preview` iptal *etmeden* kesintiyi döndürür: `feeRate`, `feeAmount`, `refundAmount`, `noticeMinutes`, `cancellableNow`. `CancellationPolicy` zaten saf bir hesap (§2p), yani yeni kural ya da tablo gerekmedi.

**Uygun olmayan durumda 409 değil `cancellableNow: false` döner.** Önizleme bir karar değil, bir gösterge; istemci butonu buna bakarak kapatıyor ve hata yakalamak zorunda kalmıyor. Taraf olmayan yine var olmayan atamanın **yanıtının aynısını** alır — `cancel` ile aynı: 404 ve aynı `job.assignment.not.found` anahtarı (§2d2).

**Önizleme bağlayıcı değil.** Kutu açıkken cezasız pencere kapanırsa gösterilen tutar ile kesilen tutar farklı olabilir. Bunu kilitlemek için önizlemeye bir geçerlilik süresi ve iptalde doğrulama gerekirdi; fark saniyeler mertebesinde olduğu için yapılmadı.

## 2ar2. Başvurunun geri çekilmesi

`POST /job-applications/{applicationId}/withdraw`. Öncesinde `JobApplicationsController` tek uç barındırıyordu ve o da **işverenin** cevabıydı; başvuran tarafında hiçbir çıkış yolu yoktu. İşi başka yerden alan ya da yanlış ilana başvuran kullanıcı `APPLIED` durumunda asılı kalıyor, işveren de aslında müsait olmayan bir adayı kabul edebiliyordu — kabul anında `JobAssignment` doğduğu ve işveren ödeme oturumu açtığı için boşluk doğrudan ödeme akışına dokunuyordu.

**Geri çekme bir durum değişikliğidir, soft delete değil.** `JobApplication` `@SQLDelete` + `@SQLRestriction` taşıyor (§8); geri çekme bunlara hiç dokunmaz, yalnızca `status`'ü `WITHDRAWN` yapar. Kayıt tabloda kalır, `is_deleted` `false` kalır, işverenin başvuru listesinde `WITHDRAWN` olarak görünmeye devam eder. `ApplicationWithdrawalIT` bunu gerçek Postgres'te satır sayısı ve `is_deleted` üzerinden doğruluyor — ikisi karıştırılırsa başvuru geçmişi sessizce kaybolurdu.

**Yalnızca sahibi, yalnızca `APPLIED` durumundan.** Başkasının başvurusu, var olmayan bir başvurunun **yanıtının aynısını** alır: 404 ve aynı `job.application.not.found` anahtarı (§2d2, §0b). `ACCEPTED` geri çekilemez: orada artık bir `JobAssignment` var ve iptalin kendi akışı, kendi ceza politikası işliyor (§2p). `ACCEPTED` / `REJECTED` / `WITHDRAWN` için **409**.

**Geri çekilmiş başvuru artık kabul edilemez.** `respondToApplication` `WITHDRAWN` durumunda `ConflictException` ile **409** döner ve bunun kendi anahtarı var (`job.application.already.withdrawn`); "zaten yanıtlanmış" mesajı burada yalan olurdu. Bu kapı olmadan geri çekme yalnızca bir etiket olurdu — işveren yine kabul eder, atama ve ödeme oturumu yine doğardı.

**Yeniden başvuru kararı: `WITHDRAWN` bir başvuru yeniden başvuruyu engellemez.** Mükerrer başvuru kapısı `existsByJobIdAndUserId`'den `existsByJobIdAndUserIdAndStatusNot(jobId, userId, WITHDRAWN)`'a daraltıldı. Gerekçe: engelleseydi geri çekme kullanıcı için tek yönlü bir kapı olurdu — fikrini değiştiren ya da yanlışlıkla geri çeken kişi ilan hâlâ `OPEN` iken bir daha başvuramaz, tek çaresi işverene mesaj atmak olurdu; bu da geri çekme düğmesini kullanılmaz derecede korkutucu yapardı. Kötüye kullanım riski (başvur–geri çek–başvur döngüsü) düşük: her yeni başvuru yeni bir satır, yani işverenin gördüğü geçmiş bozulmuyor ve gerekirse ileride sayıya bağlı bir kural eklenebilir. Karar `ApplicationWithdrawalTest` ve `ApplicationWithdrawalIT` içinde iki yönlü test edildi: `WITHDRAWN` engellemiyor, `APPLIED` engellemeye devam ediyor.

**`hasApplied` bir süre bilerek daraltılmadı, sonra tümüyle düştü (#257).** O metot mesajlaşma kapısını besliyordu ("bu ilana hiç dokundu mu?") ve daraltılsaydı başvurusunu geri çeken kullanıcı ile işveren arasındaki sohbet aniden 403 dönerdi. Kapı artık başvuruya değil **ödenmiş atamaya** bakıyor (§2o); başvurusu olan ama ödenmemiş kullanıcı zaten mesajlaşamıyor, geri çekmenin sebebini konuşmak da o kanaldan yapılmıyor. Metot ve `JobApplicationDao.existsByJobIdAndUserId` silindi.

**`answeredDateTime` geri çekme anını da taşıyor.** Ayrı bir `withdrawnDateTime` kolonu açılmadı; alan başvurunun terminal duruma geçtiği anı ifade ediyor ve `MyApplicationDto` üzerinden zaten dönüyor. Yeni kolon migration gerektirir, sıralama kümesine (`APPLICATION_SORTABLE`, gelen kutusuyla paylaşılan) ikinci bir zaman alanı sokar ve istemcide iki alanın hangisine bakılacağı sorusunu doğururdu.

**Migration gerekmedi.** `job_applications.status` `VARCHAR(255)` ve check constraint taşımıyor; enum'a değer eklemek şema değişikliği istemiyor.

**İşverene bildirim gidiyor.** `ActivityNotificationService.applicationWithdrawn` mevcut altyapıyı kullanıyor (in-app + push + e-posta, hepsi ayrı ayrı try/catch içinde). Alıcı `job.getCreator()` — `assignmentAnswered` ile aynı işveren tanımı.

**Mobil istemci bu ucu kullanmadan önce enum'unu genişletmeli.** `koala-mobile` `ApplicationStatus.values.firstWhere` ile ayrıştırıyor; `WITHDRAWN` bilinmeyen bir değerken sunucu bunu döndürürse istemci düşer. Sıra: önce istemcide enum, sonra düğme.

## 2ar3. Kabul/ret kararı — sıra, ilan durumu ve tek bildirim

`POST /job-applications/{applicationId}/respond` üç ayrı kusuru birden taşıyordu; üçü de aynı metodun ilk on beş satırındaydı.

**Yetki artık durum kontrollerinden önce çalışıyor.** Eski sıra `findById` → `WITHDRAWN` mı (409) → cevaplanmış mı (400) → **yetki** idi. Yani ilgisiz bir kullanıcı başkasının ilanındaki bir başvuru id'sini denediğinde 404 yerine 400 ya da 409 alıyor, hem başvurunun var olduğunu hem hangi durumda olduğunu öğreniyordu — §0b'nin "yetkisiz kullanıcıya 404, varlık sızmaz" kuralının ihlali. Sıra tersine çevrildi: `findById` → yetki → durum. **Kural genel:** yetkilendirme daima durum kontrolünden önce gelir, yoksa hata kodunun kendisi bir okuma kanalı olur.

**Şirket dalı da 404 atıyor, ve iki dal aynı `errorCode`'u taşıyor.** `assertCurrentUserIsEmployerOf` şirket ilanında `ForbiddenException` (403), bireysel ilanda `ResourceNotFoundException` (404) atıyordu; aynı metot, aynı "yetkin yok" hâli, ilan tipine göre iki farklı kod. Şirket ilanında 403 dönmek o başvurunun var olduğunu doğrulamak demekti.

**Durum kodunu eşitlemek yetmedi.** İki dal 404'e çekildikten sonra bile bireysel dal `job.application.user.not.creator.of.individual.job.to.view.applications` anahtarını taşıyordu; §2d gereği `errorCode` istemciye hata zarfının içinde gidiyor, yani var olmayan bir başvuru `JOB_APPLICATION_NOT_FOUND` alırken başkasının başvurusu `JOB_APPLICATION_USER_NOT_CREATOR_...` alıyordu. **Aynı 404, farklı kod — varlık yine sızıyordu.** İki dal da artık `job.application.not.found` atıyor. `ApplicationDecisionIT` bunu durum koduyla değil `errorCode` ile sabitliyor: yabancının denediği gerçek başvuru ile rastgele bir UUID **ayırt edilemez** yanıt döndürüyor, şirket ilanında da bireysel ilanda da. Eski anahtar `GET /jobs/{jobId}/applications` içinde kullanılmaya devam ediyor.

**Kural:** varlık sızmasını kapatırken durum kodunu eşitlemek yarım iştir; ayırt edici bir `errorCode` de aynı bilgiyi verir.

**Kardeş uçta aynı kusur duruyor ve bilerek dokunulmadı.** `GET /jobs/{jobId}/applications` (`getJobApplicationsByIdJobId`) şirket ilanında yetkisi olmayana hâlâ `ValidationException` (**400**) atıyor, oysa `JobsController` o uç için 404 belgeliyor ve §0b 404 istiyor. Aynı aile, ayrı uç, ayrı istemci sözleşmesi; bu dilimin kapsamı `POST /job-applications/{id}/respond` ile sınırlı tutuldu. Düzeltilmesi tek satır ama `koala-web`'in 400'e bakan bir dalı varsa onu da kırar — kendi issue'sunu hak ediyor.

**"Zaten cevaplanmış" 400 değil 409.** `ValidationException` bir girdi şekli hatasını anlatır; bu bir durum çakışması (§2ah). `JOB_APPLICATION_ALREADY_RESPONDED` anahtarı korundu, yalnız tip değişti. `koala-web#36` ve `#37` zaten 409 bekliyordu.

**İlan durumu artık iki yerde okunuyor, ve ikisi farklı davranıyor:**

| | `CLOSED` ilan | `CANCELLED` ilan |
|---|---|---|
| Yeni başvuru (`applyToJob`) | **409** `job.application.job.not.open` | **409** aynı anahtar |
| Mevcut başvuruya karar (`respondToApplication`) | **geçer** | **409** `job.application.job.cancelled` |

Kapatma yalnızca yeni başvuruyu durdurur — `JobsController` bunu zaten böyle belgeliyordu ama **kod durdurmuyordu**, kapalı ilana başvuru hâlâ 201 dönüyordu. İptal ise kararın kendisini de durdurur: `cancelJob` canlı atama varken iptali reddediyor (§2ap) ama sıra tersine çevrilebiliyordu — önce iptal et, sonra bekleyen bir başvuruyu kabul et, iptal edilmiş ilan üzerinde canlı atama doğsun. Bu kapı onu kapatıyor ve `JobAssignment` hiç oluşmuyor.

**Kural bugünkü gerçeğe göre yazıldı.** `JobStatus` `OPEN`, `CLOSED`, `CANCELLED` (#115). `IN_PROGRESS` / `COMPLETED` ilan durumu değil (§2ao2).

**Kabul edilen işçi tek bildirim alıyor.** Kabul dalı `assignmentOffered` **ve** `applicationAnswered` çağırıyordu; ikisi de aynı kişiye, aynı kategoride (`APPLICATION_UPDATES`), aynı transaction içinde. `notify` her çağrıda in-app + push + e-posta gönderdiği için kabul edilen işçi tek olay için **altı teslimat** alıyordu. Kabul dalında `assignmentOffered` kaldı (atama id'sini taşıdığı için tıklanabilir hedefi doğru), ret dalı `applicationAnswered` kullanmaya devam ediyor. Seçim `ApplicationDecisionRulesTest` ile sabit.

**İstemciye görünen bir yan etki var: kabul bildiriminin tipi değişti.** Kabul dalı artık `applicationAnswered` çağırmadığı için akış kaydı `APPLICATION_ACCEPTED` yerine `ASSIGNMENT_OFFERED` tipiyle düşüyor ve `targetType` `JOB_APPLICATION` değil `JOB_ASSIGNMENT` oluyor. `ActivityMessages.APPLICATION_ACCEPTED` anahtarı kodda duruyor ama **üretimde artık ulaşılamıyor** — `applicationAnswered` yalnız ret dalından çağrılıyor. Bildirimi tipe göre ayrıştıran istemciler (mobil ve web) kabul olayını `ASSIGNMENT_OFFERED` altında beklemeli.

**Adres ifşası bu kararın yan etkisi, ayrı bir yol değil.** Kabul `PENDING_CONFIRMATION` bir atama doğurur ve o an işçiye tam adres açılır (§2m). Yani kontenjan kapısı ve iptal kapısı aynı zamanda birer adres kapısıdır: reddedilen kabul hiçbir adresi açmaz. `ApplicationDecisionIT` bunu uçtan uca sabitliyor — `headcount = 2` ilanda kabul edilen **iki** işçinin ikisi de `GET /jobs/{jobId}` üzerinden tam adresi görüyor (ADR-0003 çoklu atamada da doğru), kontenjan dolu olduğu için reddedilen üçüncü kişi `APPROXIMATE` almaya devam ediyor.

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
| `TRANSACTIONAL` | `ACCOUNT_SECURITY`, `APPLICATION_UPDATES`, `IDENTITY_VERIFICATION`, `COMPANY_UPDATES` | **hayır** | açık | hayır |
| `ALERT` | `JOB_ALERTS`, `MESSAGES` | evet | **açık** | hayır |
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

**Alıcısı olmayan işlemsel e-posta yerel tercih nedeniyle bastırılmaz.** `recipient == null` olduğunda yerel
tercih sorulmaz — henüz
Koala kullanıcısı olmayan bir adrese gönderim yapan akışlar için. Bu boşluk **iki** yerde geçerli:
`AuthManager.startRegistration` (kayıt doğrulaması; `User` satırı henüz yok, ortada yalnızca
`PendingRegistration` var) ve `CompanyManager.sendCompanyCreatedMail`'in şirket adresine giden ilk postası
(aşağıda). **Yetkili daveti bu listede değil:** `CompanyContactInvitationManager` alıcıyı `userService.getByEmail`
ile buluyor, bulamazsa `ResourceNotFoundException` atıyor — davet her zaman kayıtlı bir kullanıcıya gider ve
`setRecipient` çağırır. Bir gönderim noktası eklerken ölçüt "adres kayıtlı bir kullanıcıya ait olabilir mi",
"bu akış davet gibi mi görünüyor" değil.
Ticari `EMAIL`/`SMS` bunun istisnasıdır: gerçek hedef adres/numara İYS'de doğrulanmış, güncel ve aynı
kullanıcıya ait bir izinle eşleşmiyorsa gönderim `NotificationManager` kapısında bastırılır.

**`sendCompanyCreatedMail` iki posta gönderiyor ve yalnızca birinin alıcısı var — bilinçli.** İlki
`company.email` adresine gidiyor: bu adres `CreateCompanyRequestDto` üzerinden serbest metin olarak geliyor,
yalnızca `@Email` ile doğrulanıyor ve hiçbir yerde `User` tablosuyla eşleştirilmiyor (`updateCompany` ile de
değiştirilebiliyor). Tipik değeri `info@sirket.com` gibi bir kurum adresi; arkasında bir Koala hesabı olması
gerekmiyor, olsa bile o hesabın sahibi olduğunu gösteren bir kanıt yok. Bu yüzden `recipient` boş bırakıldı:
doldurmak için adresi `User` tablosunda aramak gerekirdi ve rastlantısal bir eşleşme postayı **başkasının**
tercihine tabi kılardı. İkincisi kurucunun kendi adresine gidiyor; `contact.getUser()` oturum sahibinin ta
kendisi, yani her zaman bilinen bir `User` — orada `setRecipient` **çağrılır**. Asimetri budur ve hata değildir:
ayrım "postanın konusu ne" değil, "hedefin arkasında kimliğini doğrulayabildiğimiz bir kullanıcı var mı".

Kurucuya giden posta bir süre alıcısız gidiyordu; sonucu `notifications.recipient_id`'nin boş kalması ve
`COMPANY_UPDATES` kapatılmış olsa bile postanın gönderilmesiydi. `CompanyCreationNotificationTest` üç kuralı da
sabitliyor — kurucunun postasında alıcı var, şirket adresine gidende yok, ve şirket adresi kurucunun adresiyle
birebir aynı olsa bile yok (adresin eşleşmesi kimlik kanıtı değil).

**Abonelikten çıkma bağlantısı e-postaya kendiliğinden biniyor, ama yalnızca ticari iletide.** `sendEmail`,
kategori `COMMERCIAL` ise `unsubscribeUrl`'i template parametrelerine ekler ve `emails/layout.html` altbilgisi
onu çizer. Gönderen tarafın bağlantıyı eklemeyi hatırlaması gerekmiyor.

**Neden `optional` değil de `COMMERCIAL`:** ilk hâli kapatılabilir her kategoriye bağlantı koyuyordu ve bu
`COMPANY_UPDATES`'i de kapsıyordu — o kategorinin taşıdığı tek şey şirket kurulumu ve **yetkili daveti**
(`CompanyContactInvitationManager`, `DispatchPriority.HIGH`). Eyleme çağıran bir davetin altına "tek tıkla
çıkabilirsin" yazmak yanlış: davet pazarlama değil. Issue #31 de bağlantıyı "pazarlama e-postalarında" diye
istiyor. Geriye kalan tek kapatılabilir kategori `JOB_ALERTS` ve o bugün yalnızca push gönderiyor, yani
pratikte kaybedilen bir şey yok; iş alarmı e-postası yazıldığında bu sınır yeniden değerlendirilmeli.

**`COMPANY_UPDATES` kapatılabilirdi ve bu daveti erişilemez kılıyordu (issue #52).** PR #48'de kategori
`ALERT` işaretlenmişti; kullanıcı `COMPANY_UPDATES`/`EMAIL` kapattığında yetkili davetinden **hiçbir şekilde**
haberdar olamıyordu. `CompanyContactInvitationManager` yalnızca e-posta gönderiyor — `sendInApp` yok, push
yok — ve daveti listeleyen bir uç da yok: `GET /companies/{companyId}/contact-invitations` zaten o şirketin
yetkilisi olmayı, `GET /contact-invitations/{invitationId}` davetin UUID'sini bilmeyi gerektiriyor. Yani
bastırılan e-posta daveti bozmuyor, **erişilemez hâle getiriyordu.**

**Bu paragraf #52'nin çözdüğü günün durumunu anlatıyor; iki cümlesi artık geçmiş zaman.** Liste ucu o gün
platform `ROLE_ADMIN`'e kilitliydi ve şirketin kendi yöneticisi bile listeyi göremiyordu — düzeltmesi §2s2'de.
Davet bugün e-postanın yanında bir `IN_APP` kaydı da üretiyor — §2s3.

**Kategori bölünmedi, yeniden sınıflandırıldı.** İlk düşünülen yön ikiye bölmekti — `COMPANY_UPDATES` haber
olarak `ALERT` kalır, üyelik eylemleri yeni bir işlemsel kategoriye taşınır. Bölünmedi: geriye `COMPANY_UPDATES`
yalnızca `CompanyManager`'daki iki oluşturma postasını yönetir hâlde kalıyordu ve **o ikisi `setRecipient`
çağırmıyor**, yani `recipient == null` kuralı yüzünden zaten hiç bastırılmıyorlar. Sonuç tercih merkezinde
hiçbir şey yapmayan canlı bir anahtar olurdu.

Kategorinin beş gönderim noktasının hepsi işlemsel: bir oluşturma makbuzu, eyleme çağıran bir davet, iki cevap
makbuzu. `ALERT` işaretinin dayandığı "şirket haberi" ayrımı üründe yok — takip edilen şirketin yeni ilanı
zaten `JOB_ALERTS`'ten geçiyor (`CompanyFollowManager`). Sektör standardı da bu yönde: bir organizasyona
katılım daveti hesap/üyelik iletisi sayılır, İYS/KVKK ayrımında ticari ileti değil işlemsel iletidir.

**Sabit yeniden adlandırılmadı, yalnızca `kind` değişti** — veri göçü bu yüzden gerekmedi.
`@Enumerated(EnumType.STRING)` ile yazılmış `notifications.category` ve `notification_preferences.category`
satırları aynı metni taşımaya devam ediyor. Kullanıcının daha önce yazdığı `COMPANY_UPDATES` satırları
kendiliğinden etkisizleşiyor: `isAllowed`, `kind.isOptional()` false ise satıra hiç bakmadan `true` dönüyor —
yukarıdaki "veritabanına elle yazılmış satır bile susturamaz" garantisinin tam olarak işe yaradığı yer.

Bedeli: `PUT /users/me/notification-preferences` artık `COMPANY_UPDATES=false` gönderen isteğe **400** döner.
Tercih ekranı hiçbir istemcide yazılmadığı için bugün bunu gören yok.

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

**Yanıt sözleşmesi iki hata kodu ve veri taşıyan bir başarı zarfı (is-koala/koala-backend#234).** Uç bir süre
tanınmayan ve süresi dolmuş token'ı aynı `400 NOTIFICATION_UNSUBSCRIBE_TOKEN_INVALID` ile karşılıyor, başarıda da
verisiz bir `SuccessResult` dönüyordu; koala-web#54'ün dört durumlu sayfası bununla yazılamıyordu. Şimdi
`UnsubscribeTokenManager.consume` **iki** anahtar atıyor: özeti eşleşen satır yoksa
`UNSUBSCRIBE_TOKEN_INVALID`, satır var ama `expiresAt` geçmişse `UNSUBSCRIBE_TOKEN_EXPIRED` — ikisi de
`ValidationException`, yani durum kodu **400 kalıyor**, yalnız `errorCode` ayrışıyor (kayıt token'ının
`REGISTRATION_TOKEN_EXPIRED` deseni; §2ag'de süresi dolmuş bağlantı için 409'a gidilmedi, çünkü çakışan bir
durum değil kabul edilemeyen bir girdi). **Süresi dolan token ayırt edilebilir, tanınmayan ile bozulmuş token
ayırt edilemez (§2d2):** bozulmuş token'ın SHA-256 özeti hiçbir satırla eşleşmediği için "bilinmiyor" dalına
düşer — aynı durum, aynı kod, aynı mesaj; silinmiş hesabın soft-delete edilmiş token'ı da `@SQLRestriction`
yüzünden aynı dala düşer ve hesabın var olmuş olduğunu söylemez. Süresi dolmuş token ise **gerçek** bir
token'dır: kullanıcı onu bizden almıştır, "yeni e-postadaki bağlantıyı kullan" demenin sızdırdığı bir şey yok.
Ayrım sunucu logunda da var — süresi dolan dal token satırının id'sini ve kullanıcı id'sini `warn` ile yazar,
ham token hiçbir log satırına ya da hata metnine girmez (`LogPrivacyTest` `rawToken`, `getToken()` ve
`getTokenDigest()` biçimlerini yakalıyor; `theUnsubscribeTokenCannotReachALogLineInAnyOfItsShapes` bunun
kırmızıya dönebildiğini gösteriyor). Boş ya da 2048 karakteri aşan token manager'a hiç ulaşmaz:
`UnsubscribeDto` kendi anahtarlarıyla (`notification.unsubscribe.token.required` / `too.long`) 400
`VALIDATION_FAILED` + `details[].field=token` döner (§2ah).

Başarı zarfı artık `SuccessDataResult<UnsubscribedChannelDto>`: `category`, `channel` ve `alreadyDisabled`.
Kategori ve kanal token satırından `NotificationPreferenceMapper` ile eşlenir (manager DTO kurmaz, §2c).
`alreadyDisabled` idempotanlığın görünür yüzü: manager tercih satırını yazmadan önce çözümlenmiş durumu
(`enabledFor`) sorar, kanal zaten kapalıysa **hiçbir şey yazmaz** — tercih satırı yeniden `save` edilmez,
ikinci bir `UNSUBSCRIBE_LINK` rıza kaydı açılmaz, İYS kuyruğuna ikinci bir çekilme girmez — ve `true` döner.
Bu yüzden aynı bağlantıya ikinci tık aynı kategori/kanalla 200 alır; token'ın `lastUsedAt` damgası her tıkta
yenilenir, o tercih değil kullanım izidir. Hiç satırı olmayan ticari kanal (varsayılanı kapalı) da "zaten
kapalı" sayılır: satır açmak override modelini bozar, çekilecek bir izin de yoktur. Kullanıcı arada tercih
merkezinden kanalı yeniden açmışsa bağlantı onu yine kapatır ve o kararı kaydeder. Sabitleyen testler:
`NotificationUnsubscribeRulesTest` (iki kod, aynı durum, bozulmuş = bilinmeyen, süresi dolan kullanılmış
sayılmaz, hata metni token taşımaz), `NotificationPreferenceRulesTest` (DTO kategori+kanalı taşır, ikinci tık
`save`/rıza/İYS çağırmaz, satırsız ticari kanal `alreadyDisabled`, varsayılanı açık alarm kanalı kapanır ve
kapalı kalır), `RequestValidationTest` (DTO anahtarları), `NotificationPreferenceIT` (HTTP üzerinden ikinci tık
tek rıza satırı ve değişmeyen `updated_at`; JDBC ile geçmişe yazılmış `expires_at` → `..._EXPIRED`; bozulmuş
ile uydurma token'ın gövdeleri `timeStamp`/`requestId` düşülünce birebir aynı; `Accept-Language: en` metni;
boş token `details[0].field=token`), `OpenApiDocumentIT` (400 açıklaması iki kodu adlandırır, 200 şeması
`UnsubscribedChannelDto`'ya `$ref` verir, uç güvenliksiz).

**Ticari ileti izni `consent_records`'a yazılmıyor, kendi tablosunda.** `ConsentRecord` bir
`AgreementVersion`'a **zorunlu** olarak bağlı; ADR-0008 sonrası boş veritabanında hiç sözleşme satırı yok ve
tercih ekranını sözleşme kataloğunun kurulmuş olmasına bağlamak kırılgan olurdu. `commercial_message_consents`
`ConsentRecord`'un alanlarını birebir taşıyor (`granted`, `recorded_at`, `ip_address`, `user_agent`) ve
üstüne `channel` ile `source` ekliyor — İYS kanal bazında soruyor. İlişki şu: sözleşmeye verilen onay
şemsiye (`AgreementType.COMMERCIAL_MESSAGE`), kanal bazlı açma/kapama kanıtı bu tablo.

**Çekilen izin de kaydediliyor**, verilen izinle aynı özenle — §2n'deki "sormadık ile sorduk-reddetti ayrımı"
gerekçesinin aynısı. Abonelikten çıkma bağlantısıyla gelen kayıt `source = UNSUBSCRIBE_LINK` taşıyor.

**SMS yerel tercih yüzeyinde yok; ticari SMS İYS kapısından geçer.** `NotificationChannel.SMS`
`preferable = false`; telefonlar sağlayıcı sınırında kanonik `+90` E.164 biçimine çevrilir. İYS kararı,
sağlayıcı kabulünden sonra sorgu sonucu görülene dek tamamlanmış sayılmaz. Outbox, mutabakat ve yerel
kararlar kullanıcı+kanal bazında tek bir newest-wins sırasını paylaşır. Operasyon, silme sırasındaki RET
taşıması ve dış ön koşulların tek kaynağı `docs/runbooks/iys.md` dosyasıdır.

---

## 2au. Personel yetkilendirmesi — `/admin/**` ikinci zinciri

`/admin/**`'ın hangi kimliği kabul ettiği burada değil, [ADR-0010](docs/adr/0010-split-staff-identity-from-koala-authorization.md) §Geçiş'te yazılıdır. Burada yalnız mekanizma anlatılır.

**İki zincir var, ikisi de `SecurityFilterChain` bean'i.** `StaffSecurityConfig` `@Order(1)` ile `/admin/**`'ı alır, `SecurityConfig` `@Order(2)` ile geri kalanı. Sıralama açıkça verilmek zorunda: iki zincirli bir uygulamada bean tanım sırası güvenilir değildir. **Kesim yapıldı (#98):** `SecurityConfig`'teki `/admin/**` → `hasRole("ADMIN")` satırı silindi, `ApiConventionTest` artık o satırın **yokluğunu** ve zincirin `securityMatcher`/`access` çiftini arıyor.

**Kimlik ayrımı audience claim'inde yapılır, ve bu ayrım OIDC'nin yerine geçti.** Keycloak kalktığı için `StaffBearerTokenResolver`, `StaffAuthenticationManagerResolver`, `StaffJwtAuthenticationConverter` ve `StaffTokenProperties` **silindi**; `spring-boot-starter-oauth2-resource-server` bağımlılığı da `pom.xml`'den düştü — depoda `org.springframework.security.oauth2` altından tek bir import kalmadı. Yerine `StaffAuthFilter` var: `Authorization: Bearer` başlığını okur, token'ın audience'ına bakar (`JwtService.looksLikeAStaffToken`), personel token'ı değilse **hiç dokunmaz** ve istek `SessionAuthFilter`'a düşer. Personel token'ıysa `extractStaffIdentity(token, "koala-staff")` ile doğrular; audience tutmuyorsa 401. Marketplace erişim token'ı audience **taşımaz**, dolayısıyla iki yön de kapalıdır ve `StaffAuthenticationIT` bunu iki testle sabitliyor — biri gerçek `JwtService` ile üretilmiş, `sub`'ı gerçek bir personel id'si olan marketplace token'ının `StaffPrincipal` üretemediğini, diğeri personel token'ının `/users/me`'de işe yaramadığını.

**Token'ı Koala imzalıyor, ayrı bir anahtar yok.** `JWT_SECRET` ikisini de imzalar; JWKS, issuer allow-list'i ve ağdan anahtar çekme kalktı, yani açılışta ağa çıkan bir yol da kalmadı.

**Anotasyon yoksa reddedilir.** `StaffPermissionAuthorizationManager` isteği `RequestMappingHandlerMapping` üzerinden handler'a bağlar ve `@RequiresPermission` arar; yoksa reddeder. Bu yüzden eşleşmeyen bir `/admin/...` yolu 404 değil 403 döner — güvenlik kararı fail-closed verilir. `RequestMappingHandlerMapping` `ObjectProvider` ile alınır; doğrudan enjekte edilseydi güvenlik zinciriyle bean döngüsü çıkardı.

**Ne izinler ne roller token'a girmez.** Token yalnız personel id'si, token sürümü ve audience taşır; **rol üyeliği de `staff_member_roles`'tan okunur** (ADR-0010 onu Keycloak'a vermişti, ADR-0014 geri aldı). Rol→izin çözümü her istekte veritabanından, DAG aşağı gezilerek yapılır. Sonucu: **rol iptali anında geçerli** — üyelik satırı silindiği anda bir sonraki istek reddediliyor, token'ın yenilenmesi beklenmiyor (ADR-0014 §Sonuçlar'ın "beklenmedik kazanç"ı; `StaffAuthenticationIT.revokingTheMembershipTakesEffectOnTheVeryNextRequest` sabitliyor). Tanınmayan rol adı ve tanınmayan izin kodu **loglanıp atlanır** — bilinmeyen bir kod yüzünden uygulamanın açılmaması rollback'i imkânsızlaştırırdı. Bunu `StaffRoleGraphSeedTest` CI'da yakalar.

**`StaffPermission` metadata taşır.** Her sabit `code`, `mutating` ve `sensitivity` (`NORMAL`/`PERSONAL_DATA`/`FINANCIAL`) verir; denetim kaydı bunları okuyarak neyin yazılacağına ve satırın ne kadar saklanacağına karar veriyor (§2av). `@RequiresPermission` ayrıca non-GET uçlarda bir `action` adı ister. `AdminPermissionMappingIT` dört şeyi birden sabitliyor: her `/admin` mapping'i anotasyon taşır, adı geçen her izin gerçek bir sabittir, her non-GET bir eylem adı verir ve **bir yazma ucu okuma izniyle korunamaz**. İzin adı vermeyen tek anotasyon `/admin/me`'dedir; test bunun başka yerde kullanılmamasını da kontrol eder.

**Rollerin ne yapabildiği de kimin hangi rolde olduğu da Koala'da.** Rol grafiği `06-20-staff-authorization.xml` ile seed'lenir ve grafik [ADR-0014](docs/adr/0014-keep-staff-identity-in-koala.md) §Rol grafiği ile birebir aynıdır; görev ayrılığı (OWNER dışında hiçbir rol hem trust hem finans dalını kapsayamaz) seed anında `StaffRoleGraphSeedTest` ile, çalışma anında da iki yazma yolunda birden zorlanır — aşağıda §Görev ayrılığı. **Üyelik `staff_member_roles`'ta** (`07-02-staff-login.xml`), birincil anahtarı `(staff_member_id, staff_role_id)`. Ayrıntı aşağıda, §Personel girişi.

**`staff.read` eklendi (#98).** `GET /admin/staff` ve `GET /admin/staff/{id}` bir okuma izni istiyor ve `staff.manage` bir yazma izni — `AdminPermissionMappingIT` bir okuma ucunun yazma izniyle korunmasını kabul ediyor ama tersini etmiyor, ve okuma ucunu `staff.manage` ile korumak listeyi gereksizce dar tutardı. `PERSONAL_DATA` sınıfında (personel e-postası), seed'de yalnız `OWNER`'da — `staff.manage` ile aynı gerekçe.

**Bilinen 12 izne üç tane eklendi.** `content.moderate` (şikayet incelemesi ve görsel kaldırma), `badge.manage`, `settings.manage`. Sebep: `AdminPermissionMappingIT` her yönetim ucunun bir izin adı vermesini şart koşuyor ve bu üç yüzey listedeki hiçbir izne düşmüyordu. Alternatif, şikayet incelemesini `dispute.resolve` ile korumak olurdu; o da `DISPUTE_HANDLER`'a görsel silme yetkisi verirdi.

**Dördüncüsü `user.role.manage`.** #35'in açtığı üç uç (§2f3) bu dilim indikten sonra izinsiz kaldı; anotasyon yoksa zincir reddettiği için `UserRoleGrantIT`'in sekiz HTTP testi 403 aldı — kapı doğru çalıştı, eksik olan izin adıydı. `GET /admin/users/{userId}/roles` mevcut `user.read`'e düşüyor; `POST` ve `DELETE` yeni `user.role.manage` (mutating, `NORMAL`) ile korunuyor, denetim eylem adları `user.role.grant` ve `user.role.revoke`.

**`role.manage` bilinçli olarak kullanılmadı.** `role.manage` personel tarafındaki rol→izin DAG'ını yönetir; `user.role.manage` ise marketplace kullanıcısına `ROLE_ADMIN` verir. İkisi aynı izin olsaydı, personel rollerinin izinlerini düzenleyebilen biri aynı yetkiyle marketplace admini basabilirdi; ADR-0014 §Rol grafiği'ndeki görev ayrılığı kuralı tam burada delinirdi. Gerekçe ADR-0010'un `staff.manage`/`role.manage` ayrımını yapma gerekçesiyle aynı. `user.read_pii` de uygun değildi: rol listesi kişisel veri değil, ayrıca okuma izni yazma ucunu koruyamıyor (`AdminPermissionMappingIT`).

**`user.role.manage` yalnız `OWNER`'a seed'lendi.** Marketplace `ROLE_ADMIN` basmak sistemdeki en geniş etkili işlem; `staff.manage` ve `role.manage` de aynı sebeple yalnız `OWNER`'da. Destek ya da güven dalına verilseydi, kendi hesabına admin rolü yazıp bütün izin grafiğini atlamak tek istek olurdu. `SUPPORT_AGENT` rol listesini zaten `user.read` üzerinden görebiliyor; yazma ayrı bir karar olarak `OWNER`'da bırakıldı.

**Beşincisi `job.read` (#255).** `GET /admin/jobs` ve `GET /admin/jobs/{jobId}`; `PERSONAL_DATA`, non-mutating, seed'de `SUPPORT_AGENT` — yani `SUPPORT_AGENT`'ın etkili izinleri artık `user.read` + `job.read`, onu kapsayan trust dalı ve `COMPANY_APPROVER` da ikisini alır. Gerekçe §2s6'da; rol grafiğini sayan testler (`StaffAuthorizationIT`, `StaffIdentityIT`, `StaffRoleAdministrationIT`) buna göre yazılı.

**Personel tabloları `BaseEntity`'yi genişletmez.** `BaseEntity` `created_by_id`/`updated_by_id` üzerinden `users`'a yabancı anahtar kurar; ADR-0010 iki yönde de bağ kurulmamasını şart koşuyor.

**Para taşıyan metotlara anotasyon konmaz.** `PayoutService`/`PayoutManager` metotlarını otomatik tamamlama zamanlayıcısı boş bir `SecurityContext` ile çağırıyor; anotasyon konsaydı zamanlayıcı sessizce dururdu. Personel tarafı `StaffPayoutManager` facade'ından geçer: aktörü parametre olarak alır, izni denetler, sonra korumasız iç metodu çağırır. Aktör `null` ise istek köprü aşamasındaki marketplace `ROLE_ADMIN`'dendir.

**Aktörsüz para dalı silindi ve geri açılamaz (#98).** `StaffPayoutManager.requirePermission` bir zamanlar aktör `null` iken yalnız `log.warn` yazıp erken dönüyordu; sonra köprü anahtarına bağlandı; şimdi dal yok. `actor == null`, kayıtsız aktör ve izni olmayan aktör **aynı** `ForbiddenException`'a düşüyor ve `log.warn` neyin reddedildiğini yazıyor: izin kodu, atama id'si, tutar (iadede istenen tutar, çıkışta "emanetin tamamı") ve gerekçe. Facade artık `StaffBridgeProperties`'i hiç okumuyor.

**Bu asimetri bilinçli: HTTP kapısı geri açılabilir, para dalı açılamaz.** `koala.staff.bridge.open` hâlâ `StaffPermissionAuthorizationManager`'da okunuyor ve `true` yapılırsa marketplace `ROLE_ADMIN` yeniden `/admin/**`'a girebiliyor — panel bir aksilikte tümüyle erişilemez kalmasın diye. Ama aynı anahtarın parayı da geri açması, "kapanan kapının sessizce geri açılması" demekti; §2k2'nin ve #77'nin dersi tam bu. Bir para operasyonu bugünden sonra **her koşulda** kayıtlı bir personelin izniyle çalışır.

**`MarketplaceBridgeLifetimeTest` kesimi sabitledi.** İlk testi hâlâ `SecurityConfig` satırının varlığı ile aktörsüz dalın varlığını eşitliyor (ikisi de yok, yani eşit); ikinci test ikisinin de geri gelmediğini ve facade'ın anahtarı hiç okumadığını, üçüncüsü `StaffBridgeProperties` varsayılanının kapalı olduğunu ve geri açılabilen tek kapının HTTP kapısı olduğunu doğruluyor. Kapalı köprünün davranışı `StaffPayoutFacadeTest` içinde ayrıca test ediliyor.

**`AdminPermissionMappingIT`'in beşinci değişmezi finansal.** "Yazma ucu okuma izniyle korunamaz"ın karşılığı: **para hareket ettiren bir uç `FINANCIAL` olmayan bir izinle korunamaz.** Test handler'ın gövdesini kaynaktan okuyup para taşıyan bir servis çağrısı arıyor; bulursa uçtan ya kendisinin finansal bir izin adlandırmasını **ya da** personel aktörünü izni denetleyen facade'a geçirmesini istiyor. İki seçenekli olmasının sebebi §2p'deki gövde bağımlılığı: uyuşmazlık çözümü izni sonucun yönüne göre istiyor ve anotasyonda adlandıramıyor. İkinci bir test değişmezin boşa koşmadığını — en az üç para ucu bulduğunu — doğruluyor; kapsadığı yüzey daralırsa o test kırmızıya döner.

### Personel girişi — Koala'nın kendi JWT'si ve `staff_member_roles`

ADR-0014 Keycloak'ı bıraktı; #98 onun kimlik yarısını indirdi. **Yetkilendirme tarafına dokunulmadı:** `StaffAuthorizationManager.resolve(Collection<String> roleCodes)` imzası aynı, rol→izin DAG'ı, `@RequiresPermission`, denetim kaydı ve görev ayrılığı olduğu gibi duruyor. Değişen tek şey o kodların **nereden geldiği** — token yerine üyelik tablosundan.

**Şema (`07-02-staff-login.xml`).** `staff_members.keycloak_subject` düştü; yerine `password_hash` (BCrypt, mevcut `PasswordEncoder` bean'i), `must_change_password`, `failed_login_attempts`, `locked_until` ve `token_version` geldi, `email` benzersiz oldu. `staff_member_roles` üyelik tablosu `(staff_member_id, staff_role_id)` bileşik anahtarıyla açıldı. Veri taşıma yok: ADR-0008 boş veritabanı teslim ediyor ve `staff_members` bugün boştu.

**Uçlar.** `POST /admin/auth/login` (permitAll), `/refresh` (permitAll), `/logout` ve `/password` (authenticated). Yönetim tarafı `/admin/staff` altında: liste ve detay `staff.read`, oluşturma/güncelleme/rol atama/parola atama `staff.manage`. `/admin/auth/**` `@RequiresPermission` taşımıyor ve `AdminPermissionMappingIT` ile `AdminStaffPrincipalGuardIT` bu öneki zaten dışlıyordu; izin kapısı yerine `StaffSecurityConfig`'te açık matcher satırları var.

**Oturum `sessions` tablosunu kullanmıyor.** Marketplace yenileme mekanizması `sessions → devices → users` zinciri üzerinde duruyor ve `devices.user_id` `users`'a yabancı anahtar taşıyor; ADR-0014 personel ile `users` arasında iki yönde de bağ kurulmamasını şart koşuyor. Personel için o zincire satır açmak kararı tersine çevirirdi. **Tek sebep şema değil:** `sessions` ayrıca `created_by_id` ve `updated_by_id` ile doğrudan `users`'a bağlı (JPA denetim kolonları, personel yolunda `AuditorAwareImpl` sistem kullanıcısına düşerdi), ve asıl engel `SessionAuthFilter`'ın marketplace kimliğine göre yazılmış olması. Şemayı tek gerekçe göstermek eksik bir anlatı olurdu.

Yerine **token sürümü** var: `staff_members.token_version` bir sayaç, her token onu `stv` claim'inde taşıyor ve her istekte satırdaki değerle karşılaştırılıyor. Sayaç artınca o personelin **bütün** token'ları (erişim ve yenileme) aynı anda ölüyor. Artıran dört yol: çıkış, kendi parolasını değiştirme, yöneticinin parola ataması, hesabın devre dışı bırakılması. Rol geri alma listede **yok** ve olmasına gerek de yok — izinler token'a gömülmüyor, her istekte `staff_member_roles`'tan çözülüyor.

**Bedeli yazılı olsun: bu, `sessions`'a göre bir gerileme.** Yenileme token'ı döndürülmüyor (rotasyon yok), cihaza bağlı değil, ve tek bir oturumu kapatmanın yolu yok — yalnız hepsi ya hiç. Personel sayısı bir elin parmaklarını geçtiğinde ya da panelden cihaz listesi istendiğinde bu yeniden değerlendirilmeli.

**`{staff.password.change.required}` tanımlı ama henüz kullanılmıyor.** İlk girişte parola değiştirmeden gelen personel `/admin/**`'ın geri kalanından genel `ACCESS_DENIED` alıyor; kapı bir `AuthorizationDecision` döndürdüğü için özel mesaj taşıyamıyor. Bugün bilgiyi giriş yanıtı veriyor (`StaffTokenDto.mustChangePassword`), yani panel ne yapacağını biliyor. Anahtar **ölü değil**, 403 gövdesine bağlanmayı bekliyor; silmeden önce koala-admin#15'in ona ihtiyacı olup olmadığına bakılmalı.

**Erişim token'ı 15 dakika, yenileme 12 saat** (`koala.staff.auth.*`). Yenileme token'ı ayrı bir audience taşıyor (`koala-staff-refresh`), yani erişim token'ı yerine kullanılamıyor — `StaffAuthenticationIT.aRefreshTokenIsNotAnAccessToken` sabitliyor.

**Giriş varlık sızdırmıyor ve bu tek anahtarla sağlanıyor.** Olmayan e-posta, yanlış parola, kilitli hesap ve devre dışı hesap **aynı** `{staff.login.failed}` anahtarını taşıyor, yani `errorCode` de aynı (`STAFF_LOGIN_FAILED`) — §2d2'nin kuralı: eşitlenmesi gereken şey durum kodu değil yanıtın tamamı. Olmayan hesapta da bir BCrypt karşılaştırması koşuyor (sabit bir hash'e karşı) ki zamanlama farkı da kanal olmasın. `StaffLoginIT` üç senaryonun zarfını birebir karşılaştırıyor.

**Kaba kuvvet iki kapıyla karşılanıyor, MFA yok.** `AUTH` kovası (§2j) `/admin/auth/login`, `/refresh` ve `/password` uçlarını kapsıyor — anahtar yol + IP + gövdedeki e-postanın SHA-256 özeti, yani aynı IP'deki bir hesabın denemeleri diğerini kilitlemiyor. Buna ek olarak **hesap kilidi**: `koala.staff.auth.max-failed-attempts` (5) başarısız denemede `locked_until` damgalanıyor ve `lock-duration` (15 dk) boyunca doğru parola da çalışmıyor. Başarısız deneme sayacının kalıcı olması için `login` `@Transactional(noRollbackFor = UnauthorizedException.class)` taşıyor — §2k2'deki IBAN doğrulamasında alınan kararın aynısı; aksi hâlde reddi yazan `UPDATE` de geri alınır ve bütçe hiç tükenmezdi. ADR-0014 MFA'nın yokluğunu **açık bir gerileme** olarak kaydetti ve yeniden açılma koşulu yazılı: panele sahibin dışında ikinci bir kişi girdiği gün.

**İlk giriş kapısı yetki katmanında.** `must_change_password` işaretli bir personel `StaffPermissionAuthorizationManager`'da reddediliyor, yani `/admin/**`'ın **tamamı** 403 — `/admin/me` dahil. Tek istisna `/admin/auth/**`, çünkü o uçlar izin kapısına hiç uğramıyor: bayrağı indiren `POST /admin/auth/password` erişilebilir kalıyor. Parola değişimi token sürümünü de artırdığı için değişimden sonra yeniden giriş gerekiyor.

**Parola sıfırlama yönetici eliyle, e-posta akışı yok.** `staff.manage` taşıyan biri `POST /admin/staff/{id}/password` ile yeni parolayı yazıyor, hesap "ilk girişte değiştir" bayrağı alıyor ve açık token'ları ölüyor. Ek tablo, şablon ve süre sınırı yok — depo sahibinin 6 Eylül 2026 kararı: iki kişilik ekipte token tablosu + şablon + süre sınırı işi çözdüğü probleme göre büyük. E-postayla sıfırlama personel sayısı artınca ayrı issue olacak.

**Üyelik tarafında iki kural var ve ikisi de yazımdan sonra ölçülüyor.** (1) **Kendini kilitleme:** aktör kendi son yönetim üyeliğini bırakamaz ve kendini devre dışı bırakamaz — yazım yapılır, `staff.manage` + `role.manage`'e hâlâ ulaşıp ulaşmadığı canlı olarak yeniden hesaplanır, ulaşamıyorsa 409 (`STAFF_MEMBERSHIP_SELF_LOCKOUT`) ve transaction geri alınır. (2) **Son yönetici:** yazımdan sonra sistemde `staff.manage` ya da `role.manage`'e ulaşan **etkin** personel kalmıyorsa 409 (`STAFF_LAST_ADMINISTRATOR`). İkincisi bugün de erişilebilir: `staff.manage` taşıyıp `role.manage` taşımayan bir aktör, `role.manage`'in tek sahibini devre dışı bırakmaya çalıştığında ateşliyor (`StaffMembershipRulesIT`).

**Rol atama aktörün kendi izinleriyle sınırlı.** `POST/DELETE /admin/staff/{id}/roles/{roleCode}` aktörden **verilen rolün etkili izinlerinin tamamını** istiyor — rol grafiğindeki `requireGrantable` ile aynı gerekçe: sahip olmadığın bir yetkiyi dağıtamazsın, ve görev ayrılığı tersten de delinemez. Her iki yazım da denetim kaydına önce/sonra değerleriyle düşüyor (`active=…; roles=[…]`).

**Bootstrap: iki ortam değişkeni, yalnız boş tabloda.** `BOOTSTRAP_OWNER_EMAIL` + `BOOTSTRAP_OWNER_PASSWORD` birlikte verildiğinde ve `staff_members` **boş** olduğunda `StaffBootstrapRunner` tek bir `OWNER` yaratıyor, "ilk girişte parola değiştir" bayrağıyla, ve denetim kaydına `staff.bootstrap` satırı bırakıyor — sistemin kendi yarattığı ilk hesabın izi. Tablo boş değilse **hiçbir şey yazmıyor ve sessiz de kalmıyor**: `WARN` ile "bootstrap atlandı, N personel var" yazıyor; var olan bir hesabın parolası asla ezilmiyor. Değişkenlerden biri eksikse bootstrap hiç çalışmıyor ve bu bir hata değil. Seçim gerekçesi depo sahibinin 6 Eylül 2026 kararı: elle SQL (parola hash'ini insanın üretmesi, staging'de her sıfırlamada tekrar) ve tek seferlik CLI komutu (konteynere girmeyi gerektirir) elendi; ortam değişkeni Dokploy'dan verilebiliyor ve staging'de tekrarlanabiliyor. `BOOTSTRAP_OWNER_PASSWORD` `docs/secrets-inventory.md`'de ve **kullanıldıktan sonra Dokploy'dan silinmeli**. `NoStartupDataSeedingTest`'in muafiyet listesi bu yüzden ikinci satırını kazandı; runner veri üretmiyor, ancak açıkça iki değişken verildiğinde tek bir hesap açıyor.

**Personel yönetimi kendi aggregate'i.** `StaffAccountManager` `staff_members` + `staff_member_roles` DAO'larına sahip; `StaffAuthorizationManager` `StaffMemberDao`'yu bıraktı ve artık yalnız rol grafiğinin üç tablosuna uzanıyor. Rol koduna göre `StaffRole` çözümü `StaffAuthorizationService.getRoleByCode` üzerinden yapılıyor (§2ae: başka aggregate'e erişim onun servisi üzerinden). `AggregateBoundaryTest` iki satırla güncellendi.

### Görev ayrılığı — tek tanım, iki yol

[ADR-0014](docs/adr/0014-keep-staff-identity-in-koala.md) §Rol grafiği kuralı **bağlayıcı** koyuyor: hiçbir rol
hem trust dalını (`MODERATOR`/`DISPUTE_HANDLER`) hem finans dalını (`FINANCE_*`) birden kapsamamalı — `OWNER`
hariç. **Kural bir süre yalnız seed anında geçerliydi:** onu tutan tek şey `StaffRoleGraphSeedTest` idi ve o test
changelog XML'ini **metin olarak** okuyor — veritabanını değil, bir isteği hiç değil. `src/main` altında kuralı
uygulayan tek satır yoktu, yani `POST /admin/roles/{roleId}/includes` ve `POST /admin/staff/{id}/roles/{roleCode}`
onu denetlemeden kabul ediyordu. Bu deponun izlediği kusur sınıfının tam örneği (§2d2, §2k2, #77): doğru bir
kural, sonradan eklenen bir çalışma zamanı ucu tarafından sessizce geçersiz kılınıyor.

**Kural tek yerde: `core/helpers/StaffSeparationOfDuties`.** Dal tanımı (trust rol kodları, `FINANCE_` öneki),
`OWNER` istisnası ve reddin kendisi orada; sınıf saf, `BankAccountVerificationPolicy` deseninde. **Üç çağıran da
oraya soruyor:** `StaffAuthorizationManager.includeRole` (kapsama), `StaffAccountManager.grantRole` (üyelik) ve
`StaffRoleGraphSeedTest` (seed). Bu bilinçli — aynı kuralın N ayrı yere kopyalanıp testin yalnız bir çifti
bağlaması bu depoda tekrar eden kusur (be#134'te 5, be#135'te 7 yer); üçüncü çağıranın seed testi olması, dal
tanımı değiştiğinde changelog ile çalışma zamanının ayrışmasını da engelliyor.

**`OWNER` istisnası kodda örtük değil, adlarda yazılı:** `OWNER_ROLE_CODE` sabiti, `isOwnerExemption` metodu ve
`exemptBecauseOwnerIsAssigned` yerel değişkeni. §1 yorum yasağı gereği gerekçe koda değil buraya yazılıyor, ama
kuralın **ne olduğu** okunurken kaynak koddan çıkmalı.

**İstisna "atanan" kümede aranıyor, "ulaşılan" kümede değil, ve fark bir deliği kapatıyor.** Kapsamada atanan küme
denetlenen **tek bir rol**, üyelikte personelin **doğrudan** rolleri. Ulaşılan kümeye bakılsaydı `X ← OWNER` yazımı
— X'e her şeyi, yani iki dalı birden veren kapsama — kapanışında `OWNER` göründüğü için muaf sayılırdı; oysa X,
`OWNER` olmadan owner-eşdeğeri bir rol hâline gelirdi ve kuralın yasakladığı şey tam olarak budur. Bugün o yazım
reddediliyor.

**İki kümeyi ayıran şey tip, sıra değil: `StaffBranchReach`.** Kural bir süre iki konumsal `Collection<String>`
alıyordu (`requireBranchesStaySeparate(assigned, reached)`); iki argüman yer değiştirse kod **derlenir** ve — bir
üstteki paragrafın anlattığı — `X ← OWNER` deliği sessizce açılırdı, çünkü `OWNER` o zaman ulaşılan kümede aranırdı.
Kritik olduğu bir paragrafla savunulan bir ayrımı derleyicinin koruyamaması kabul edilmedi. Değer nesnesi iki adımda
kuruluyor (`StaffBranchReach.assignedRole(kod).reaching(ulaşılanlar)`), yani her iki küme çağrı yerinde adıyla
anılıyor ve kurala tek argüman gidiyor.

### Kural kapanışa yukarı da bakıyor

**Kapanış bir süre yalnız kenarın ebeveyninden aşağıya bakıyordu ve değişmez tam o uçtan kırılabiliyordu.** Guard
`closureOf(parent) ∪ closureOf(child)` ile kuruluyordu, yani kontrol edilen küme `{parent}`'tan ibaretti;
**ebeveynin ataları hiç sorulmuyordu**. Ölçüldü: `A → MODERATOR` ve `A → C` yasal biçimde kurulduktan sonra
`C → FINANCE_OFFICER` isteği **200** dönüyordu ve `closure(A)` hem trust hem finans dalını kapsar hâle geliyordu.
Bu, bu deponun izlediği kusur sınıfının kendi içinde tekrarıydı: kural yerinde, kapsamı sessizce dar.

**Doğru kural şu ve uygulanan bu: `P → C` kenarı eklenirken `P`'ye ulaşabilen her rol — yani `ataları(P) ∪ {P}` —
artık `closure(C)`'ye de ulaşır; bu kümedeki `OWNER` olmayan her `A` için `closure(A) ∪ closure(C)` iki dalı birden
kapsamamalı.** Yani `closure(A) ∪ closure(C)` kenarın eklenmiş hâlindeki `closure(A)`'nın ta kendisi.

**Aynı kök nedenin bir de üyelik yansıması vardı ve o da kapandı.** Personelin taşıdığı **boş** bir role sonradan
finans dalı asılınca personel iki dala birden ulaşıyor ve hiçbir yeniden doğrulama yapılmıyordu — üyelik kuralı
yalnız üyelik **verilirken** koşuyordu, grafiğin sonradan değişmesi onu hiç ilgilendirmiyordu. Kenar eklenirken artık
o kenardan etkilenen personeller de doğrulanıyor. **Etkilenen küme sınırlı ve tam olarak yukarıdakiyle aynı:**
`ataları(P) ∪ {P}` kümesindeki rollerden herhangi birini taşıyan personeller — çünkü `P`'ye ulaşan tek yol o roller.

**Yukarı yürüyen sorgu aşağı yürüyenin simetriği:** `staffRoleIncludeDao.findAllWithRolesByChildRoleIdIn`,
`findAllWithRolesByParentRoleIdIn`'in aynası. İkisi de seviye seviye yürüyor, yani sorgu sayısı grafiğin
**derinliğine** bağlı, düğüm sayısına değil.

**Bu bir yazma yolu, o yüzden döngü içinde sorgu atılmıyor.** Ata kümesi bir kez yukarı yürüyerek, o kümenin altındaki
komşuluk haritası bir kez aşağı yürüyerek çıkarılıyor; her ata için kapanış o haritada **bellekte** hesaplanıyor
(`reachedFrom`), rol kodları tek bir `findAllById` ile alınıyor. Üyelik yansımasında da aynı şey: etkilenen
personeller tek sorguda, onların bütün rolleri ikinci bir sorguda, o rollerin altındaki grafik yine tek bir aşağı
yürüyüşle geliyor — personel başına `resolve(...)` çağrılmıyor.

**`closureOf(child)` bir kez hesaplanıyor.** Kısa bir süre aynı çağrı `includeRole` içinde **üç kez** yapılıyordu
(döngü kontrolü, guard, izin kontrolü) ve her biri seviye başına bir sorgu atıyordu. Bugün metodun başında bir kez
hesaplanıp üç yerde de o kullanılıyor.

**İki yolda da kontrol yazımdan önce ama idempotanlık erken dönüşünden sonra, ve iki yol artık aynı sırada.**
Üyelikte guard baştan beri `existsByMemberIdAndRoleId` erken dönüşünden **sonraydı**: zaten var olan bir üyeliğin
yeniden verilmesi bir yazım değil, ve önceden kalmış aykırı bir satır yüzünden no-op çağrının patlaması istenmedi.
Kapsamada ise guard erken dönüşten **önce** koşuyordu ve `StaffRolesController` "İdempotenttir" diye belgelediği
hâlde zaten var olan aykırı bir kenar yeniden POST edildiğinde **409** dönüyordu — issue'nun tarifine göre üretimde
bugün böyle kenarlar var, yani bu canlı veride tetiklenirdi. Sıra üyelik yoluyla eşitlendi: önce yetki kontrolü,
sonra idempotanlık erken dönüşü, sonra guard. Yan kazanç: no-op bir çağrı artık ata ve üyelik taramasını hiç
çalıştırmıyor.

**İki yazma yolu aynı transaction advisory kilidi altında serileşir.** Salt mevcut grafiği ve üyelikleri okuyup
sonra yazmak `READ COMMITTED` altında yeterli değildi: bir rolün grafiğine finans dalı eklenirken aynı rol trust
üyeliği olan bir personele eşzamanlı verilebilirdi ve iki transaction birbirini görmeden ayrı ayrı geçebilirdi.
`StaffAuthorizationService.lockSeparationOfDutiesWrites` hem `includeRole` hem `grantRole` girişinde aynı
`staff-separation-of-duties` anahtarını, authorization aggregate'inin kendi `StaffRoleDao`'su üzerinden
`pg_advisory_xact_lock` ile alır; personel/rol yönetimi düşük frekanslı olduğu için tek global anahtar, parça
kilitlerin yanlış sırada alınmasından doğacak deadlock riskine tercih edildi. Kilit commit/rollback ile bırakılır
ve guard sonraki transaction'ın tamamlanmış yazımını görerek yeniden hesaplanır.

**Aggregate sınırı bir muafiyetle geçiliyor ve gerekçesi bean döngüsü.** Üyelik yansıması için
`StaffAuthorizationManager` `StaffMemberRoleDao`'ya yalnız etkilenen üyelikleri okumak için uzanıyor;
`StaffAccountService` üzerinden gitmek `StaffAccountManager → StaffAuthorizationService` bağının ters yönü
olurdu ve §1 `@Lazy` ile gizlemeyi yasaklıyor.
`AggregateBoundaryTest.EXEMPTIONS` listesinde gerekçesiyle yazılı — §2ae'nin kendi öngördüğü yol — ve
`everyExemptionIsStillNeeded` liste bayatlarsa kırmızıya dönüyor.

### Dal tanımı asimetrik ve bu bir kapsam daralması riski

`isFinanceBranch` bir **önek** kontrolü (`FINANCE_`), `isTrustBranch` ise **kapalı bir liste**
(`MODERATOR`/`DISPUTE_HANDLER`/`TRUST_LEAD`). Ölçüldü: `isFinanceBranch("FINANCE_ANYTHING_NEW")` **true**,
`isTrustBranch("FRAUD_REVIEWER")` **false**. Yani seed'e eklenecek yeni bir finans rolü kurala kendiliğinden girer,
yeni bir trust rolü **sessizce dışarıda kalır** — bu deponun izlediği kusur sınıfının aynısı, üstelik önceki hâlde
`StaffRoleGraphSeedTest` yalnız "en az bir trust, en az bir finans rolü tanınıyor" diyordu: tamlık değil boş-olmama
kontrolü.

**İki dalı simetrik hâle getirmek (ikisi de önek ya da ikisi de liste) reddedildi.** Trust dalını öneke çevirmek
(`TRUST_`) seed'deki üç rolün de yeniden adlandırılmasını, yani changelog'a dokunmayı ve token/panel tarafında rol
kodu değiştirmeyi gerektirirdi; finans dalını kapalı listeye çevirmek ise bugün **çalışan** bir korumayı — yeni bir
`FINANCE_*` rolünün otomatik kapsanmasını — kaldırırdı. İkisi de kuralın kapsamını daraltma riskini ortadan
kaldırmıyor, yalnız yerini değiştiriyor.

**Seçilen: kapalı liste korunuyor, ama seed'deki her rolün bilinçli olarak sınıflandırıldığı testle zorlanıyor.**
`StaffRoleGraphSeedTest.everySeededRoleIsDeliberatelyPlacedInABranchOrOutsideBoth` changelog'daki `staff_roles`
satırlarını okuyor ve testte yazılı sınıflandırma listesiyle **iki yönlü** karşılaştırıyor: seed'de olup listede
olmayan rol kırmızı, listede olup seed'de olmayan rol kırmızı, ve her rolün çalışma zamanı sınıflandırması
(`isTrustBranch`/`isFinanceBranch`/`isOwnerExemption`) listedekiyle **aynı** olmak zorunda. Bugün seed'de on rol var:
üçü trust (`MODERATOR`, `DISPUTE_HANDLER`, `TRUST_LEAD`), ikisi finans (`FINANCE_ANALYST`, `FINANCE_OFFICER`), biri
`OWNER`, dördü bilinçli olarak hiçbir dalda (`CATALOG_EDITOR`, `SUPPORT_AGENT`, `AUDITOR`, `COMPANY_APPROVER`).

Bu seçenek daha güçlü çünkü sınıflandırma kararını **insana bırakıp unutulmasını imkânsız kılıyor**: seed'e
sınıflandırılmamış bir rol eklendiği gün test kırmızıya dönüyor ve ekleyen kişi rolün hangi dala ait olduğuna —
ya da bilinçli olarak hiçbirine ait olmadığına — karar vermek zorunda kalıyor. Simetri tek başına bunu yapmazdı:
`FRAUD_REVIEWER` iki dal da önek olsaydı yine sessizce dışarıda kalırdı. Depoda "belgeyi/seed'i okuyan test" kalıbı
zaten var (`ProductionConfigurationContractTest`, `StaffRoleGraphSeedTest`'in kendisi).

**`TRUST_LEAD` listede kalıyor ve ADR güncellendi.** Kod dalı `MODERATOR`/`DISPUTE_HANDLER`/`TRUST_LEAD` diye
tanımlıyordu, [ADR-0014](docs/adr/0014-keep-staff-identity-in-koala.md):61 ve issue ise yalnız ilk ikisini sayıyordu.
Davranışsal olarak `TRUST_LEAD`'i saymak büyük ölçüde artıklı — kapanış zaten `TRUST_LEAD → MODERATOR + DISPUTE_HANDLER`
kenarlarını izliyor — ama **yazılı kuralı genişletiyordu ve ADR metniyle ayrışıyordu**. Listeden çıkarmak yerine ADR'ye
kapsamı netleştiren bir ek satır yazıldı: `TRUST_LEAD` grafikte o iki rolün kapsayanı, yani kuralın koruduğu ayrımın
trust tarafında; sınıflandırma listesi de artık her seed rolünü adlandırdığı için onu adsız bırakmak yeni bir boşluk
açardı. Karar değişmedi, kapsamı yazıldı — ADR sessizce çelişkide bırakılmadı.

**Anahtar `{staff.separation.of.duties.violated}`, yani `errorCode` `STAFF_SEPARATION_OF_DUTIES_VIOLATED` (409).**
İki yol da aynı kodu döndürüyor — istemcinin ayırt etmesi gereken iki hâl değil, tek kural. `ConflictException`
seçildi çünkü bu bir durum çakışması, girdi şekli hatası değil (§2ag); `ROLE_GRAPH_WOULD_CYCLE` ile aynı sınıf.

**`koala-admin` bu turda değişmedi ve bu bilinçli.** Panel kuralı bugün yalnız teşhis olarak gösteriyor — rozet,
cümle, kapsama seçilirken canlı uyarı — ama seçeneği kapatmıyordu, çünkü backend yazımı kabul ediyordu. Artık
düğme çalıştığında 409 dönüyor; panelin o kodu göstermesi kendi issue'suna ait.

### `/admin/roles` — DAG'ın HTTP yüzeyi

Kurallar (`grantPermission`, `revokePermission`, `includeRole`) #14 ile indi ama **çağıracak uç yoktu**: bir rolün ne yapabildiğini değiştirmek canlı veritabanına elle yazılan bir SQL cümlesiydi. Yedi uç bunu kapatıyor, hepsi `role.manage` ile korunuyor:

| Uç | İzin | Denetim eylemi |
|---|---|---|
| `GET /admin/roles` | `role.manage` | — |
| `GET /admin/roles/{roleId}` | `role.manage` | — |
| `POST /admin/roles/{roleId}/permissions` | `role.manage` | `staff.role.permission.grant` |
| `DELETE /admin/roles/{roleId}/permissions/{permission}` | `role.manage` | `staff.role.permission.revoke` |
| `POST /admin/roles/{roleId}/includes` | `role.manage` | `staff.role.include.add` |
| `DELETE /admin/roles/{roleId}/includes/{includedRoleId}` | `role.manage` | `staff.role.include.remove` |
| `GET /admin/permissions` | `role.manage` | — |

**Servis rol koduyla değil `UUID` ile anahtarlandı.** #14'ün metotları `String roleCode` alıyordu ve yalnız testlerden çağrılıyordu. Uç yolları §1 gereği `@PathVariable UUID` alıyor; koda göre ikinci bir yazma yüzeyi bırakmak aynı işi yapan iki imza demekti, o yüzden imzalar `UUID`'ye çevrildi ve `StaffRoleDao.findByCode` düştü. `resolve(...)` hâlâ kodla çalışıyor — token `realm_access.roles` içinde kod taşıyor, orada id yok.

**Liste ucu DAG'ın tamamını tek istekte veriyor.** Her satır rolün kendi izinlerini **ve doğrudan kapsadığı rolleri** taşıyor; panel grafiği çizmek için rol başına ikinci bir istek atmıyor. Detay ucu buna ek olarak geçişli kapsananları (`effectiveRoles`) ve DAG aşağı çözülmüş etkili izinleri (`effectivePermissions`) veriyor.

**Grafik tek seferde belleğe alınıp orada geziliyor.** `StaffAuthorizationManager.graph()` üç sorgu atıyor (roller, kenarlar, izinler) ve kapanışları bellekte hesaplıyor; rol başına `closureOf` çağırmak on rol için on ayrı tur demekti (§2af). Kenar sorgusu `@EntityGraph({"parentRole", "childRole"})` taşıyor — `childRole` lazy olduğu için kod ve ad okunurken kenar başına bir sorgu daha atılırdı. `resolve(...)`'ın kullandığı `findAllByParentRoleIdIn` graph'sız kaldı: orada yalnız id okunuyor, eager çekmek boşuna iş olurdu. İzin sorgusunun bir zamanlar taşıdığı `@EntityGraph("role")` de aynı gerekçeyle düştü — `directPermissionsOf` yalnız `granted.getRole().getId()` okuyor, lazy proxy bunu sorgusuz cevaplıyor ve `graph()` zaten tüm rolleri aynı persistence context'e almış oluyor; geriye tek fark olarak fetch join kalıyordu, o yüzden `findAllWithRoleByRoleIdIn` tamamen silindi.

**`includedBelow` ile `closureOf` bilerek ayrı duruyor.** İkisi de DAG'ı aşağı geziyor ama farklı girdiyle: `includedBelow` `graph()`'ın belleğe aldığı `childrenOf` haritasında yürüyor ve kökü **dışarıda** bırakıyor (rolün *kapsadıkları*), `closureOf` ise yazım sırasında canlı grafiğe seviye seviye sorgu atıyor ve tohumları **içeriyor** (rolün *ulaştıkları*). Tek metotta birleştirmek ya `graph()`'ı rol başına tura sokardı ya da her yazımın grafiğin tamamını yüklemesini gerektirirdi; ikisi de §2af'nin kaçındığı şey.

**İzin kodu yol değişkeninde metin.** `/admin/settings/{key}` ile aynı biçim; §1'in "id'ler UUID" kuralı doğal anahtarı metin olan kaynakları kapsamıyor. Tanınmayan kod iki yerde reddediliyor: gövdede `GrantStaffPermissionDto`'nun `@AssertTrue`'su ile **400 + alan adı** (§2ah), yolda controller'ın `permissionOf` yardımcısıyla **404** — yol segmenti var olmayan bir alt kaynağı adlandırıyor. `StaffPermission`'a `@JsonValue`/`@JsonCreator` eklenmedi ve global bir `Converter` yazılmadı; ikisi de enum'un her yerdeki serileştirmesini değiştirirdi.

**`GET /admin/permissions` issue'nun listesinde yoktu, bilinçli eklendi.** Panel "izin ekle" kutusunu doldurmak için kodların listesine ihtiyaç duyuyor; olmasaydı `StaffPermission` sabitlerini istemciye kopyalamak ve her yeni izinde panel sürümü çıkarmak gerekirdi. Katalog `mutating` ve `sensitivity` de veriyor — denetim kaydının okuduğu metadata (§2av) panelde de anlamlı: hangi iznin yazma yaptığı ve hangisinin kişisel veri/finans sınıfında olduğu görünüyor. Kapsam dışı bir uç olarak bırakılmadı: 403 taraması yedi ucun **hepsini** deniyor ve uç diğer altısıyla aynı `role.manage` kapısında.

**Katalog controller'da hesaplanmıyor.** İlk yazılışında `StaffPermissionsController` `StaffPermission.values()` çağırıyordu — depoda `.values()` çağıran tek controller ve §1'in katman kuralının ihlali. Sıralı katalog artık `StaffAuthorizationService.getPermissions()` arkasında, `StaffAuthorizationManager` içinde tek seferlik `static final` bir liste. Uç, diğer altısı gibi, iş katmanından okuyor.

**Dört yazım da önce/sonra değeri bırakıyor (§2av).** Çekirdek denetim satırı zaten yazılıyordu ama `resourceId` yolun **ilk** değişkeni, yani yalnız `roleId`; `DELETE .../permissions/{permission}` ve `DELETE .../includes/{includedRoleId}` için hangi iznin ya da hangi kenarın gittiği hiçbir yerde yazmıyordu (DELETE gövdesiz olduğu için `requestBody` de null). Dört metot da yazımdan sonra `StaffAuditContext.capture(...)` çağırıyor ve rolün **doğrudan** durumunu `permissions=[...]; includes=[...]` biçiminde iki kez damgalıyor. Çağrı `requireTheActorKeepsRoleManagement`'tan **sonra**: 409 ile geri alınan bir yazımın "sonra" değeri kaydedilseydi iz olmamış bir değişikliği iddia ederdi. İdempotent no-op (izin zaten var, kenar zaten var) erken dönüyor ve hiçbir şey damgalamıyor — değişmeyen bir satırın önce/sonrası yok. Servis katmanından çağrıldığında (`StaffAuthorizationIT`) `StaffAuditContext.current()` null olduğu için `capture` sessizce hiçbir şey yapmıyor.

**Kapsama kaldırma, kapsama eklemenin aynası.** `excludeRole` de aktörden kapsanan rolün **etkili izinlerinin tamamını** istiyor. Kaldırma yetkiyi daraltıyor, yani "sahip olmadığını veremezsin" gerekçesi doğrudan geçerli değil; ama görev ayrılığı (§2au) tersten de delinebilirdi — yalnız güven dalını taşıyan biri `OWNER`'dan finans dalını koparabilirdi. `role.manage` zaten yalnız `OWNER`'da olduğu için pratikte kısıtlayıcı değil, sınır ileriye dönük duruyor.

**Kural kendini kilitlemeyi engelliyor, "son sahip"i değil — ad buna göre düzeltildi.** Tek cümle: **bir yazımdan sonra düzenlenen rol `role.manage`'i kaybettiyse ve aktör o role kendisi ulaşıyorsa, işlem 409 (`STAFF_ROLE_MANAGE_SELF_LOCKOUT`) ile reddedilir ve transaction geri alınır.** Hem izin geri almada hem kapsama kaldırmada uygulanıyor; ikisinde de karar **yazımdan sonra** veriliyor — kenar silindikten sonra rolün etkili izinleri canlı grafikten yeniden hesaplanıyor, yani kural kapsama kaldırmada da tam.

`requireTheActorKeepsRoleManagement` kalan sahipleri **saymıyor**: `role.manage` taşıyan ikinci bir rol varken bile aktörün kendi ulaştığı rolden izni alması reddediliyor. İlk yazılışında anahtar `ROLE_MANAGE_LAST_HOLDER`, test adı da `theLastRoleManagementHolderCannotRevoke...` idi; ikisi de yalan söylüyordu, çünkü seed'deki `OWNER` da `role.manage` taşıyor, yani senaryoda hep iki sahip vardı ve kanıtlanan şey "kendi ulaştığın rolden alamazsın"dı. Kod doğruydu, ad yanlıştı — anahtar `staff.role.manage.self.lockout`, test adı `roleManagementCannotBeRevokedFromARoleTheActorReachesItself` oldu ve test artık grafikte başka bir `role.manage` sahibi kaldığını da doğruluyor. Aynı geçişte `managesRoles(roleId)` de `reachesRoleManagement(roleId)` oldu: eski ad "bu rol rolleri yönetir" diye de "bu rolü kim yönetir" diye de okunabiliyordu, oysa sorduğu şey tek bir şey — rolün kapanışı `role.manage`'e **ulaşıyor mu**.

**Gerçek bir "son sahip" sayımı artık kurulabiliyor ve kuruldu (#98).** Rol üyeliği `staff_member_roles`'ta olduğu için "sistemde kaç **etkin personel** `role.manage`'e ulaşıyor" sorusu cevaplanabiliyor; sayım `StaffAccountManager.countActiveHoldersOf` içinde ve üyelik tarafındaki her yazımdan sonra çalışıyor (§Personel girişi). Bu, rol grafiği tarafındaki `requireTheActorKeepsRoleManagement`'ı **değiştirmiyor**: o kural hâlâ aktörün kendi erişimi üzerinden ifade ediliyor ve grafiğin `role.manage` satırını kaybetmesini engelliyor. İki kural iki farklı yüzeyi koruyor — biri rolün ne yapabildiğini, diğeri kimin o rolde olduğunu — ve ikisi birden gerekiyor: rol satırını saymak yanlış soruydu (iki rol izni taşıyıp ikisinin de üyesi olmasa sistem yine kilitli kalırdı), üyeliği saymak doğru soru. koala-admin#9'un "son yetkili kendi iznini kaldıramaz" maddesi artık harfi harfine karşılanıyor.

Kural bir üst garantiyi de veriyor: **grafik son `role.manage` satırını hiçbir zaman kaybedemez.** `requireGrantable` aktörden `role.manage` istiyor, yani aktör her zaman o izne ulaşan bir role sahip; izni son taşıyan rolü düzenliyorsa kural ateşleniyor, düzenlemiyorsa grafikte hâlâ başka bir kaynak var. Ayrı bir "grafikte en az bir satır kalsın" kontrolü bu yüzden yazılmadı. Aktörün ulaşmadığı bir rolden `role.manage` alınabilir (`roleManagementMayStillBeRevokedFromARoleTheActorDoesNotReach`); kural fazladan kapatmıyor.

**Rol yaratma/silme ucu açılmadı.** Roller `06-20-staff-authorization.xml` ile seed'leniyor ve [ADR-0014](docs/adr/0014-keep-staff-identity-in-koala.md) §Rol grafiği ile birebir eşleşmek zorunda; grafik görev ayrılığını taşıyor ve panelden serbestçe rol açmak onu ADR'de yazılı olmayan bir şekle sokardı. Issue de rol listesini okuma olarak istiyor.

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

**Aktör alanları — `koala-admin` için nullability sözleşmesi.** `actorDisplayName` daima dolu; personel yolunda görünen ad, o boşsa personelin e-postası. `actorStaffId` personel yolunda **daima dolu** — token ancak var olan bir `staff_members` satırı için üretilebiliyor (§2au). Null kalabildiği tek yer köprü: `STAFF_MARKETPLACE_BRIDGE_OPEN=true` ile geri açılan marketplace `ROLE_ADMIN` yolunda marketplace kullanıcı id'si yazılır, kaydı olmayan test principal'lerinde null kalır. Bootstrap'ın açtığı `staff.bootstrap` satırında aktör, yaratılan hesabın kendisidir ve görünen ad `bootstrap`'tır. `resourceId` **null yerine boş string**: liste ya da arama isteğinin hedeflediği tek bir kayıt yoktur ve panelin null denetimi yapmak zorunda kalması istenmedi. `permission` izin adı vermeyen tek anotasyonda (`/admin/me`) `none`, `action` ise okuma uçlarında izin koduna düşer.

**Saklama işi `AdminAuditRetentionScheduler`'da**, günde bir. Önce detay, sonra çekirdek siliniyor; ikisi ayrı adım çünkü detayı süresi dolduğunda düşürmek çekirdeğe dokunmadan yapılmalı. Hata koşuyu düşürmez, loglanır. `audit.retention.enabled=false` ile tümüyle susturulur (testlerde kapalı). Zamanlayıcı tek örnek varsayıyor (§7, Faz 11D) ama silme idempotent olduğu için ikinci örnek zarar vermez.

**KVKK silme talebi `LegalManager.requestAccountDeletion` üzerinden tetikleniyor.** Talep kabul edildiğinde o kullanıcının id'sini `resource_id` olarak taşıyan satırların **detayı** siliniyor, çekirdek kalıyor. Açık yükümlülük yüzünden reddedilen talep hiçbir şey silmiyor. **Sınırı yazılı olsun:** eşleşme `resource_id` üzerinden, yani `/admin/users/{id}/...` satırlarını yakalıyor; o kişinin adı başka bir kaydın istek gövdesinde geçiyorsa o detay bu yolla silinmez. Kapsamı genişletmek gövde içinde arama demek ve bugün gerekmedi.

**Önce/sonra değerleri `StaffAuditContext.capture` ile veriliyor.** İki çağıran var: `SettingsManager.update` — issue'nun şikayet ettiği `cancellation.late-fee-rate` artık iz bırakmadan değiştirilemiyor — ve `StaffAuthorizationManager`'ın dört rol grafiği yazımı (§2au). Yeni bir yazma ucunun önce/sonra taşıması isteniyorsa manager'a tek satır ekleniyor, denetim satırı zaten yazılıyor. Aynı context'in `reclassify` işlemi ise statik controller anotasyonundan daha özel, gövdeye bağlı izin/aksiyon doğduğunda kullanılır; bugün tek çağıran uyuşmazlığın para hareketi sonucudur (§2p).

**İstek gövdesi yalnızca JSON'da ve 8 KB'a kadar önbelleğe alınıyor.** Multipart yükleme (rozet görseli) sarmalanmıyor — akışı tamponlamak yüklemeyi iki kez belleğe almak olurdu. Reddedilen istekte gövde hiç okunmadığı için `requestBody` null kalır: yapılmamış bir yazmanın gövdesini saklamanın değeri, reddedilen her isteğin gövdesini tamponlama maliyetinden düşük.

**Async dispatch bugün kapsamda değil.** `OncePerRequestFilter` varsayılan olarak async dispatch'te çalışmaz, yani `/admin/**` altına bir gün `DeferredResult`/`Callable` döndüren bir uç eklenirse satır handler bitmeden yazılır ve önce/sonra değerleri kaçar. Bugün böyle bir uç yok; eklenirse filtre `shouldNotFilterAsyncDispatch` üzerinden gözden geçirilmeli.

**Denetim yazması patlarsa istek düşmüyor, `ERROR` loglanıyor.** Satır o noktada yanıt zaten istemciye gitmiş oluyor; exception fırlatmak başarılı bir 200'ü sonradan 500'e çeviremez, yalnızca gürültü üretirdi. Bu bilinen bir boşluk: veritabanı hatası satır kaybettirir. "Unutmak imkânsız" garantisi kod yolu içindir, veritabanı dayanıklılığı için değil.

**Sorgu üç yerde Postgres'in null tip çıkarımından kaçıyor.** `cast(:x as Instant)` HQL'de tanınmıyor (`unrecognized cast target type`), `cast(:x as Boolean)` ise geçerli HQL ama null'ı `bytea` olarak gönderip `cannot cast type bytea to boolean` veriyor. Bu yüzden `from`/`to` boşken sınır değerlere (`Instant.EPOCH` ve `9999-12-31`) düşüyor ve `allowed` için ayrı bir `filterByOutcome` bayrak parametresi var. Metin süzgeçleri §2l'deki `cast(:query as string)` deyimini kullanıyor.

**Denetim tabloları `BaseEntity`'yi genişletmez ve soft delete taşımaz.** §2au'nun gerekçesi (personel tarafı `users`'a yabancı anahtar kurmaz) burada da geçerli; ayrıca saklama işinin işi zaten **fiziksel** silmek. Soft delete edilen bir denetim satırı saklama süresini hiç doldurmazdı.

**Detay 1:1 ilişkisi `@MapsId` ile çekirdeğin anahtarını paylaşıyor** ve okuma sorgusu `left join fetch` kullanıyor: sayfa başına 20 satır için ikinci bir sorgu turu açılmıyor (§2af). To-one fetch join sayfalamayı belleğe taşımıyor, o tuzak koleksiyon fetch'ine ait (§2l).

---

## 2aw. `login_attempts` saklama politikası — iki pencere, iki gerekçe

`login_attempts` süresiz büyüyordu. Tabloyu temizleyen hiçbir şey yoktu ve PR #119'dan sonra (§2d2)
`AuthManager.login` hesap bulunmadığında da satır yazıyor: `user = null`, `email_attempted` giriş formuna
yazılan adres. Varlık gizlemesi için doğru bir karardı, ama saklama tarafıyla birleşince sonucu şuydu —
**hiçbir hesaba ait olmayan bir e-posta adresi, sahibi Koala'yı hiç duymamış olsa bile, süresiz saklanıyordu.**
Aynı doğru-karar-sonradan-geçersizleşiyor deseninin (§2d2, §2k2, #77) bir örneği daha.

**İki pencere var ve süreleri bilerek farklı:**

| Satır | Ölçüt | Süre | Anahtar |
|---|---|---|---|
| Hesaba bağlı | `user_id is not null` | **365 gün** | `login-attempt.retention.attributed-days` |
| Sahipsiz | `user_id is null` | **30 gün** | `login-attempt.retention.unattributed-days` |

**365 gün — güvenlik olayı incelemesinin ufku, denetim izinin değil.** Admin denetim izi 24 ay (§2av) çünkü
orada yasal ispat yükümlülüğü var; giriş denemesi bir yükümlülük kaydı değil, bir teşhis kaydı. Bir hesabın
"geçen yıl da aynı IP'den denenmişti" sorusuna cevap verebilecek kadar uzun, kişisel veriyi gereksiz yere
biriktirmeyecek kadar kısa. Sayı ürün/hukuk kararıdır ve `LOGIN_ATTEMPT_RETENTION_ATTRIBUTED_DAYS` ile
deploy'suz değişir.

**30 gün — çünkü bu satırların veri sahibi Koala kullanıcısı değil.** Sahipsiz satır tanımı gereği hiçbir
hesaba ait değil: adresin sahibi hiç kaydolmamış olabilir, hatta adresi yazan kişi de o adresin sahibi
olmayabilir. Meşru kullanımı dar — kimlik bilgisi püskürtme kampanyalarının incelenmesi, ki bu günler-haftalar
ufkunda bir iştir. Uzun tutmanın bedeli ise net: tablo bir **adres hasadı yüzeyi** hâline geliyor ve dahası
`failure_reason` iki dalı ayırt ediyor (`No account for email` / `Invalid email or password`). Yani #119'un
API yüzeyinde kapattığı sayım kehanetinin cevabı **tablonun içinde** duruyor. Kısa pencere o kehanetin
ömrünü sınırlıyor; #119'un ikinci yarısı budur.

**Sıra tersine çevrilemez: sahipsiz pencere hesaba bağlı pencereden uzun olamaz — ve bu artık açılışta
zorlanıyor.** Bir süre bu cümle yalnız bir iddiaydı: `LoginAttemptRetentionProperties` düz setter'lardan
ibaretti, ne `@Validated` ne `@Min` ne `@PostConstruct` taşıyordu, değerler ise
`LOGIN_ATTEMPT_RETENTION_*_DAYS` ile dışarıdan geliyordu. `unattributed=400, attributed=30` verildiğinde
200 günlük **sahipsiz** satır hayatta kalıyor, hesabın **kendi geçmişi** siliniyordu — ve paket baştan sona
yeşil kalıyordu. `attributed-days=0` bugünün satırlarını süpürüyordu; **negatif bir değer**
`Instant.now().minus(-n)` ile kesimi geleceğe taşıyıp **tabloyu boşaltıyordu**.

Sınıf artık komşularının kalıbını kullanıyor (`IyzicoProperties`, `AddressSearchProperties`,
`AuthLinkProperties` — bu sınıf eklenmeden önce altı properties sınıfı açılışta doğruluyordu): `@Validated`, iki alanda `@Min(1)`,
ve sıra kuralı için bir `@AssertTrue`. Yanlış yapılandırılmış bir ortamda uygulama **hiç açılmıyor**;
çalışma anında sessizce sapmıyor. `LoginAttemptRetentionConfigurationTest` üç kuralın üçünü de ayrı ayrı
tutuyor — `@Validated` düşerse dört test, `@AssertTrue` düşerse yalnız iki sıra testi, `@Min` düşerse yalnız
sıfır ve negatif testleri kırmızıya dönüyor (üçü de ölçüldü). Alt sınır ve sıra kuralı birbirinin yerine
geçmiyor: sıfır testi bilerek **iki** pencereyi birden sıfırlıyor, yoksa sıra kuralı onu yanlış sebeple
yakalar ve `@Min`'in kaldırıldığını göremezdik.

**Kesim hesabı properties'in üstünde, manager'da değil.** `cutoff(retentionProperties.getUnattributedDays())`
imrendiği verinin (*Feature Envy*) üstüne, `Duration` ile (*Primitive Obsession*) taşındı:
`retentionProperties.unattributedCutoff(Instant.now())`. Bağlanan anahtarlar `int ...Days` olarak kaldı —
`LOGIN_ATTEMPT_RETENTION_*_DAYS` yayımlanmış bir yapılandırma sözleşmesi ve `Duration`'a çevirmek onu
kırardı; `AccountDeletionProperties`'in `Duration`'ı yeni bir anahtar olduğu için o kısıtı taşımıyordu.

**Kesim anı çalışma zamanında hesaplanıyor, satıra damgalanmıyor — §2av'nin tersi, ve fark bilinçli.** Denetim
izinde `retain_until` bir kolon, çünkü orada yasal süreyi geriye dönük **kısaltmak** yanlış olurdu. Burada yön
ters: bunlar bir yükümlülük değil bir gizlilik borcu, yani süreyi düşürmek geçmiş satırlara da işlemeli.
Damgalanmış kolon ayrıca migration + backfill isterdi ve asıl sorunu — bugün zaten birikmiş satırları —
çözmezdi.

**Sorgu native SQL, JPQL değil. Sebebi ölçüldü.** `LoginAttempt` `@SQLDelete` + `@SQLRestriction` taşıyor (§8)
ve Hibernate `@SQLRestriction`'ı **bulk JPQL delete'e de uyguluyor**: `delete from LoginAttempt la where ...`
cümlesine `is_deleted = false` ekleniyor, yani **soft delete edilmiş bir satır saklama işini hiçbir zaman
dolduramıyor** ve adresi süresiz taşımaya devam ediyor. Kusur ilk yazımda gerçekten oluştu ve
`anAlreadySoftDeletedRowIsPurgedAsWell` onu kırmızıya döndürdü. Kural §2av'deki denetim tabloları kararının
aynısı, yalnız bu tablo `BaseEntity`'yi bırakamıyor: **saklama işi fiziksel siler**, ve `@SQLRestriction`'ı
atlamanın tek yolu native sorgu.

**Yaş `coalesce(time_stamp, created_at)` ile ölçülüyor.** `time_stamp` nullable ve değeri Java alan
başlatıcısından geliyor; JPA dışından yazılmış bir satırda boş kalabilir ve `time_stamp < :cutoff` o satırı
**hiç seçmezdi** — sessizce süresiz kalan bir satır. `created_at` `NOT NULL`, yani geri düşüş her zaman bir
değer veriyor.

**Zamanlayıcı `AdminAuditRetentionScheduler`'ın birebir eşi** (§2av): günde bir, önce sahipsiz sonra bağlı
pencere, hata koşuyu düşürmez yalnız loglanır, `login-attempt.retention.enabled=false` ile tümüyle susturulur
(testlerde kapalı). Tek örnek varsayıyor (§7, Faz 11D) ama silme idempotent olduğu için ikinci örnek zarar
vermez. Manager yalnız kendi DAO'suna uzanıyor (§2ae).

**Üç kısmi indeks eklendi** (`2026/09/07-03`): iki purge dalı için `coalesce(time_stamp, created_at)` üzerinde
birer ifade indeksi, bir de anonimleştirmenin adres araması için `lower(email_attempted)` — üçü de
`user_id` durumuna göre kısmi, çünkü her sorgu zaten o dalın içinde çalışıyor.

**Terminoloji tek ad: kodda `unattributed`, Türkçe belgede `sahipsiz`.** Bir süre üç ad dolaşıyordu —
Java'da `unattributed`, indeks adında ve zamanlayıcının log satırında `ownerless`, belgede `sahipsiz`.
`ownerless` tekilleştirildi: log satırı ve iki indeks adı (`idx_login_attempts_unattributed_age`,
`idx_login_attempts_unattributed_email`) `unattributed`'a çevrildi. Changelog henüz `main`'e inmediği için
indeks adları aynı changeSet içinde düzeltildi; uygulanmış bir changeSet olsaydı checksum yüzünden
dokunulamazdı (§2m'deki `27-10` kararının aynısı). İngilizce kod adı ile Türkçe belge adının ayrı olması
deponun her yerindeki düzen (İlan/job, Başvuru/application); eşleme `CONTEXT.md`'ye yazıldı ve `ownerless`
orada _Avoid_ olarak duruyor.

**Anonimleştirme tarafındaki boşluk da kapandı.** `PostgresAccountAnonymizer.revokeCredentials`
`login_attempts` satırlarını yalnız `where user_id = ?` ile temizliyordu; hesap silinen kullanıcının adresini
taşıyan **`user_id`'siz** satırlara hiç dokunmuyordu. Cümle artık o satırları da kapsıyor:

```
or (user_id is null and email_attempted is not null
    and lower(email_attempted) = (select lower(email) from users where id = ?))
```

**Karşılaştırma `lower()` ile, çünkü e-posta hiçbir yerde küçük harfe normalize edilmiyor** — `users.email`
kullanıcının yazdığı gibi duruyor, giriş formuna yazılan adres de öyle. **Kuralın iki yarısı var ve bir süre
yalnız biri testle bağlıydı:** `theAddressIsMatchedWithoutRegardToCase` `email_attempted`'ı zaten küçük harfle
yazıp yalnız `users.email` tarafını karıştırıyordu, yani `lower(email_attempted)` → `email_attempted`
mutasyonuyla **dört testin dördü de yeşil kalıyordu**. Bağlanmamış yarı pratikte baskın olan yöndü:
kullanıcı forma `Ali@Example.com` yazıyor. `theAddressTypedIntoTheFormInAnotherCaseIsMatchedToo` o yönü
bağlıyor — `email_attempted` karışık harfli, `users.email` küçük harfli — ve aynı mutasyonla kırmızıya
döndüğü ölçüldü (eski test o mutasyonda hâlâ yeşil kalıyor, yani ikisi ayrı şeyleri tutuyor). Yeni bir
mekanizma kurulmadı: ADR-0012
veritabanı satırları için **yerinde anonimleştirme** diyor ve mevcut cümle zaten `email_attempted`,
`failure_reason`, `ip_address`, `device_name` alanlarını null'lıyor; kapsam genişledi, davranış aynı kaldı.
`PersonalMediaDeletionOutbox` bu yola karışmıyor — outbox yalnızca transaction dışına düşen nesne deposu
silmeleri için var, burada iş tek bir `UPDATE` ve zaten aynı transaction'da.

**Sıra şart: bu cümle `scrubProfile`'dan önce koşmak zorunda.** `revokeCredentials` `users.email`'i alt
sorguyla okuyor, `scrubProfile` ise onu `deleted+<id>@anonymous.invalid` ile eziyor. `anonymize` içindeki adım
sırası (`revokeCredentials` → `scrubProfile`) bugün doğru; ters çevrilirse eşleşme sessizce hiçbir satır
bulmaz.

**İki mekanizmanın sınırı yazılı olsun — biri diğerinin kapatamadığını kapatıyor.** Anonimleştirme yalnız
**o anda var olan** satırları temizliyor. Kullanıcı hesabını sildikten sonra biri eski adresle giriş
denerse, o denemenin `user_id`'si null olur ve **anonimleştirme çoktan koşmuştur**; yeni satır adresi yeniden
taşır. Onu kapatan şey saklama işi, ve sahipsiz pencerenin 30 günde tutulmasının ikinci gerekçesi tam olarak
budur. Ters yön de doğru: saklama işi tek başına, hesabını silen kullanıcının son bir yıllık giriş geçmişini
tabloda bırakırdı. İkisi birlikte gerekiyor.

**§2d2 düzeltildi, uzaktan not düşülmedi.** O bölüm bu tablo için "tek okuyucusu hesap silmede
`PostgresAccountAnonymizer`" diyordu; ikinci okuyucu saklama işi ve cümle artık **§2d2'nin kendi içinde**
düzeltilmiş durumda. Bir süre düzeltme yalnız burada, uzaktan yazılıydı — bayat cümleyi yerinde bırakıp
başka bir bölümden düzeltmek, iki gerçeklik üretmenin belgedeki hâliydi. Cümlenin asıl iddiası (tablo hiçbir
uçtan istemciye dönmüyor) baştan beri geçerliydi ve öyle kaldı.

**Ve daha rahatsız edici bir gerçek: bu tabloyu bugün hiçbir iş kuralı okumuyor.** `LoginAttemptDao` bu
değişiklikten önce tek bir sorgu metodu taşımıyordu; `AuthManager` yalnız yazıyor. §2j'nin kaba kuvvet
kapıları başka yerlerde duruyor — `AUTH` kovası bellekteki `RateLimiter`'da, personel hesap kilidi
`staff_members.failed_login_attempts` kolonunda. Yani `login_attempts` bugün **adli bir iz**, canlı bir
savunma değil. 365 gün bu gerçeğe göre seçildi; tablo bir gün gerçekten kilit kararı besleyecekse süre o
kararın penceresine göre yeniden değerlendirilmeli.

**Testler.** `LoginAttemptRetentionIT` yedi kuralı gerçek Postgres üzerinde sabitliyor: 31 günlük sahipsiz
satır düşer 29 günlük kalır, 200 günlük bağlı satır iki süpürmeden de sağ çıkar, 366 günlük bağlı satır düşer
364 günlük kalır, sahipsiz süpürme 400 günlük bağlı satıra hiç dokunmaz, silme fiziksel olur (soft delete
işaretlenen satır adresi tutmaya devam ederdi), `is_deleted = true` satır da düşer,
`time_stamp` boş olan satır `created_at`'e düşerek yakalanır.

`LoginAttemptRetentionSchedulerTest` **üç** kural: her koşuda iki pencere de uygulanır, bayrak kapalıyken
hiçbir şey silinmez, patlayan koşu zamanlayıcıdan kaçmaz. **İki testi kaldırıldı ve sebebi yazılı olsun.**
`theOwnerlessWindowIsMuchShorterThanTheAttributedOne` `assertEquals(30,…)` + `assertEquals(365,…)` yazdıktan
sonra `assertTrue(30 < 365)` diyordu: ilk ikisi geçtiyse üçüncüsü **hiçbir zaman kırılamaz** — ölçtüğü
sabitten kurulmuş bir beklenti, yani değişmezi tuttuğu sanılan ama tutmayan bir test. Gerçek doğrulama
yukarıda geldiği için beklenti anlamlı yerine, `LoginAttemptRetentionConfigurationTest`'e taşındı.
`theJobIsOnUnlessSomebodyTurnsItOff` de yalnız Java alan başlatıcısını ölçüyordu; yaml kablolamasını
doğrulayan hiçbir test yoktu, çünkü `src/test/resources/application.properties` işi kapatıyor ve entegrasyon
context'i hiçbir zaman açık hâli görmüyor.

`LoginAttemptRetentionConfigurationTest` on kural — `ApplicationContextRunner` ile
(`AddressSearchConfigurationTest` deseni): varsayılanlar açılır ve 30/365'tir, sahipsiz pencere bağlıdan uzunsa
**uygulama açılmaz**, eşit pencereler geçerlidir, sıfır ve negatif pencere açılışı düşürür, negatif pencerenin
kesimi gerçekten geleceğe taşıyacağı ölçülür, kesim verilen andan pencerenin çıkarılmasıdır. Son üçü
**kablolamayı** bağlıyor: `application.yaml` `YamlPropertySourceLoader` ile yüklenip binder'a veriliyor ve
`enabled=true`, `attributed-days=365`, `unattributed-days=30`, cron `0 50 3 * * *` sabitleniyor; belgelenen
üç `LOGIN_ATTEMPT_RETENTION_*` ortam değişkeninin alanlara ulaştığı ölçülüyor; ve sıra kuralının ortam
değişkenlerinden gelen değerlerde de zorlandığı görülüyor. Yaml'ı `ConfigDataApplicationContextInitializer`
ile yüklemek belirsiz olurdu — test `application.properties`'i de sınıf yolunda buluyor ve hangisinin
kazandığı Boot sürümüne bağlı; yaml'ı açıkça yükleyip `addFirst` ile öne koymak deterministik.

`AccountAnonymizationLoginAttemptIT` **beş** kural: `user_id`'siz ama aynı adresi taşıyan satır da anonimleşir,
hesabın adresi karışık harfliyken eşleşir, **giriş formuna karışık harfle yazılan adres de eşleşir**, başka bir
adresi taşıyan satıra dokunulmaz, gerekçe/IP/cihaz adı da temizlenir.

---

## 2ax. Production yapılandırma kapısı — ne durdurur, ne uyarır

Production ortam değişkenleri deponun kendi kararlarıyla dört yerde ayrışmıştı
(#66) ve **hiçbiri depodan görünmüyordu**: `application.yaml:69-73` yalnız
`health,info,metrics,prometheus,loggers` uçlarını açıyor, `/actuator/env` ve
`/actuator/configprops` kapalı. Kapalı olmaları doğru, ama sonucu şu — canlıdaki
hiçbir değer dışarıdan okunamıyor, sapma da görünmüyor. Dört değer Dokploy'da
düzeltilse bile ertesi gün sessizce geri gelebilirdi.

`ProductionConfigurationGuard` bu boşluğu kapatıyor: `app.deployment` production
ise dört değerin etkin hâlini bir `INFO` satırına yazıyor, sapan her değer için
bir `WARN` üretiyor ve iki hâlde açılışı hiç yaptırmıyor.

**Üçüncü bağlama noktası açıldı: `app.deployment`.** `SERVICE_ENVIRONMENT` bu
işten önce yalnız iki yere bağlıydı — ECS log alanı (`application.yaml:99`) ve
`iyzico.deployment` (`:361`). Guard'ı ikincisi üzerinden okumak **güvenlik
kontrolünü ödeme yapılandırmasına bağlamak** olurdu: `IyzicoProperties`'in
`@AssertTrue`'su bir gün gevşetilir ya da o sınıf başka bir kaynağa taşınırsa
CORS ve trusted-proxy kontrolü sessizce onunla birlikte kayardı. `app.deployment`
aynı ortam değişkenini kendi enum'una bağlıyor (`DeploymentProperties.Deployment`),
yani iki tip birbirinden bağımsız ama **tek bir ortam adından** besleniyor.
§2y2'nin "yeni bir değişken uydurulmadı" kararı burada da geçerli: ikinci bir
"hangi ortamdayız" değişkeni açmak ikisinin ayrışabileceği bir gelecek üretirdi.

**Tanınmayan ortam adı açılışı düşürüyor, tanımsız ortam `local` sayılıyor** —
`IyzicoProperties.setDeployment` ile birebir aynı davranış, aynı gerekçeyle:
`SERVICE_ENVIRONMENT=prod` yazılırsa enum bağlanamaz ve uygulama açılmaz;
bilinmeyeni "production değil" saymak production'ı sessizce denetimsiz bırakırdı.

### Ayrımı yöneten tek ilke

**Yanlış değerin, değerin hiç olmamasından kötü olduğu yerde açılış durur; bir
boşluğu açık bırakmaktan ibaret olan sapmada yalnız `WARN` yazılır.**

Bunun doğrudan bir sonucu var ve kasıtlı: **durduran hiçbir kural bugünkü
production'ı kaldıramaz.** Kontrol, dört Dokploy düzeltmesi yapılmadan inebilsin
diye böyle kuruldu. Tersi bir tasarım — "boşsa açılma", "doluysa açılma" — bir
sonraki deploy'da canlıyı kaldırırdı; bir sapmayı görünür kılmak için yapılan iş,
kendi ürettiği kesintiyle ödenmiş olurdu.

Bu cümle iki kural için **iki ayrı biçimde** doğru, ve aradaki fark önemli:

- `APP_TRUSTED_PROXIES` kuralı için doğrudan ölçülebilir — kural yalnız
  production'da koşuyor ve bugünkü prod değerinin geçtiği, boş listenin de dolu
  ama dar listenin de geçtiği testlerde sabit.
- `CORS_ALLOWED_ORIGINS` **şekil** kuralı her ortamda koşuyor ve prod'un gerçek
  değeri depodan görülemiyor, yani "bugünkü değer geçiyor" doğrudan
  doğrulanamaz. Onun yerine daha güçlü olan şey ölçülüyor: **reddedilen her
  şeklin bugün hiçbir tarayıcıya hizmet ediyor olamayacağı**
  (`aRefusedShapeCannotBeServingABrowserToday`, gerçek `CorsConfiguration`
  üzerinde). Çalışan bir değer bu kapıya takılamaz, çünkü takılan şekillerin
  hiçbiri çalışıyor olamaz.

**Sondaki `/` bu yüzden reddedilmiyor, normalize ediliyor.** Spring'in
`CorsConfiguration.addAllowedOrigin`'i de `checkOrigin`'i de `trimTrailingSlash`
çağırıyor: `https://iskoala.com/` listede `https://iskoala.com` olarak duruyor ve
tarayıcının `Origin` başlığıyla **eşleşiyor** (`CorsContractTest`
`aTrailingSlashIsTrimmedByTheFilterItselfSoSuchAnOriginStillMatches`). Yani bu
şekil "sessizce yalan söyleyen" bir allowlist değil, **çalışan** bir allowlist —
onu reddeden bir kural, bugün sorunsuz duran bir prod değerini bir sonraki
deploy'da açılışta düşürürdü. `CorsProperties.setAllowedOrigins` aynı kırpmayı
yapıyor, yani guard ile filtre aynı değeri görüyor. Sonda **iki** `/` (`//`) hâlâ
reddediliyor: Spring bir tane kırpıyor, geriye kalan `/` hiçbir origin'le
eşleşmiyor.

| Kural | Karar | Neden |
|---|---|---|
| `SERVICE_ENVIRONMENT` tanınmayan bir ad | **durdurur**, her ortamda | `DeploymentProperties.Deployment` enum'una bağlanamayan değer — örneğin `prod` — açılışı düşürür. Bilinmeyeni "production değil" saymak production'ı sessizce denetimsiz bırakırdı. Tanımsız değer `local` sayılır; `IyzicoProperties.setDeployment` ile birebir aynı davranış. |
| `CORS_ALLOWED_ORIGINS` tam origin taşımıyor | **durdurur**, her ortamda | Reddedilen her şekil, bugün **hiçbir tarayıcıya hizmet ediyor olamayacak** bir şekil — `aRefusedShapeCannotBeServingABrowserToday` bunu gerçek `CorsConfiguration` üzerinde ölçüyor. Tam `*`, `allowCredentials(true)` ile birlikte `checkOrigin`'in `validateAllowCredentials`'ında **her preflight'ta** `IllegalArgumentException` atar: yapılandırma hatası sıcak yolda 500'e döner. Alt alan joker'i (`https://*.iskoala.com`) fırlatmaz, sessizce `null` döner — `setAllowedOrigins` desen kabul etmiyor, desenler `setAllowedOriginPatterns`'a ait — yani allowlist bir alt alana izin verdiğini söyler ve hiçbir şeyle eşleşmez. Şemasız, yollu, sorgulu, fragment'li ve kullanıcı bilgili şekiller de aynı sessiz yalanı üretir. |
| `APP_TRUSTED_PROXIES` her istemciyi kapsıyor | **durdurur**, production'da | Issue'nun kendi cümlesi: yanlış değer değerin hiç olmamasından kötü. Her çağıranı güvenilir yapan bir liste `ClientIpResolver`'a `X-Forwarded-For`'u okutur ve `RateLimitFilter`'ın anahtarı ile erişim logunun `clientIp` alanı **saldırgan tarafından belirlenir**. Kapsama **semantik** ölçülüyor: tek tek öneklere değil, girdilerin **birleşimine** bakılıyor. |
| production + dolu `CORS_ALLOWED_ORIGINS` | uyarır | ADR-0011 sapması ve **bugünkü prod'un hâli**. Durdurmak canlıyı kaldırırdı. Zarar da sınırlı: `SameSite=Lax` cookie'ler zaten `api.iskoala.com` origin'ine gitmiyor, yani dolu allowlist BFF sınırını sözleşme olmaktan çıkarıp zorlanabilir yapmıyor — yalnız zorlanmamış bırakıyor. |
| production + boş `APP_TRUSTED_PROXIES` | uyarır | Bugün zararsız: mobil doğrudan bağlanıyor, `getRemoteAddr()` gerçek IP. Risk web BFF yayına çıktığında doğuyor (is-koala/koala-web#2) ve o gün değer **zaten** girilmiş olacak. Bugün durdurmak, henüz var olmayan bir riske karşı gerçek bir kesinti almaktır. |
| production + açık `SPRINGDOC_ENABLED` | uyarır | §2ao bunu "saldırgana keşif hediyesi" diye yazıyor ve kapatılması gerekiyor — ama #66 "production'da API dokümantasyonu gerekiyor mu, gerekiyorsa kimlik doğrulamalı ayrı bir yol mu" sorusunu **açık bir ürün kararı** olarak bırakıyor. Guard, sahibine ait bir kararı açılışı düşürerek kendi başına veremez. |
| production + boş `SERVICE_VERSION` | uyarır | Yalnız ECS log alanı; güvenlik etkisi yok. |

**Malformed bir `app.trusted-proxies` girdisi zaten açılışı düşürüyordu** ve
guard onu tekrarlamıyor: `IpNetwork.parse` `IllegalArgumentException` atıyor,
yani `ClientIpResolver` bean'i oluşturulamıyor. Guard yalnız **biçimi geçerli ama
anlamı yıkıcı** olan değeri ekliyor.

**Kapsama semantik ölçülüyor, önek uzunluğuna bakılmıyor.** Sıfır uzunluklu önek
aramak (`0.0.0.0/0`, `::/0`) yalnızca en bilinen yazımı yakalar;
`0.0.0.0/1,128.0.0.0/1` ikilisi bütün IPv4'ü aynı şekilde kapsar ve o kontrolden
geçerdi — sahte bir `X-Forwarded-For` gerçekten kabul edilirdi. `IpNetwork`
girdileri adres ailesine göre ayırıyor, her birini aralığa çeviriyor, sıralayıp
birleştiriyor ve **bir ailenin tamamı örtülüyorsa** durduruyor. Çıplak
`0.0.0.0` bir `/32`'dir, tek adres kapsar ve doğru şekilde geçer.

**Shape kuralı `CorsProperties` üzerinde `@Validated` + `@AssertTrue` ile, yani
`IyzicoProperties:15-17,36-39` deseninin aynısı** — tek prefix'e sığan bir kural
için depo bu kalıbı kullanıyor. Kural `StompOriginProperties.normalize`'ın STOMP
allowlist'ine uyguladığı biçim kuralına çok yakın — iki allowlist ayrı (ADR-0011)
ama "tam origin" tanımı ortak — **tek fark sondaki `/`**: STOMP allowlist'i onu
reddediyor, REST allowlist'i normalize ediyor, çünkü karşısındaki iki mekanizma
farklı davranıyor (`OriginHandshakeInterceptor` kırpmıyor, `CorsConfiguration`
kırpıyor). `CorsProperties.setAllowedOrigins` boş girdileri düşürüyor, yani
`CORS_ALLOWED_ORIGINS=` hiçbir ortamda kapıya takılmıyor.

**Baseline `INFO` her şeyden önce yazılıyor.** Açılışı düşüren kural en sonda
koşuyor; sıra ters olsaydı **durduran turda baseline hiç yazılmazdı** ve operatör
tam da açılış düştüğü anda etkin değerleri göremezdi — guard'ın bütün amacı
görünürlükken. Sıra: baseline `INFO` → sapma `WARN`'ları → durduran kural.

**Kural listesi tek yerde ve dokümanlar oradan doğrulanıyor.** Durdur/uyar
listesi yedi ayrı yerde biliniyordu; yeni bir kural 5–7 dosyaya dokunmayı
gerektiriyor ve dördünü unutmak sessizce geçiyordu.
`ProductionConfigurationGuard.Rule` artık kuralların tek kataloğu (değişken adı +
`REFUSES`/`WARNS`) ve `ProductionConfigurationContractTest` bu katalogdan
başlayarak `docs/environments.md` §Production zorunlulukları tablosunu, bu
bölümün karar tablosunu ve §0'ın 11. maddesini doğruluyor: eksik satır da,
kuralı olmayan fazladan satır da kırmızıya döner. `DRIFT_MARKER` literali de
`docs/environments.md`'den değil sabitten doğrulanıyor. Guard'ın yazdığı `WARN`
satırları değişken adını katalogdan alıyor, ve
`everyWarningTheGuardWritesNamesARuleTheCatalogueDeclares` kataloğa girmemiş bir
uyarıyı yakalıyor. Desen `OperationalRunbookContractTest` ile
`PayoutSettlementConfigurationTest`'ten alındı.

**Ortam bağımlı ve prefix aşan kurallar `@AssertTrue` ile ifade edilemiyor.**
Guard `DeploymentProperties`, `CorsProperties`, `TrustedProxyProperties`
bean'lerini ve springdoc bayrağını Spring `Environment`'tan okuyor —
`ConfigurationProperties` doğrulaması yalnız kendi prefix'ini görebilir, dört
değer dört ayrı prefix'te. Guard bu yüzden bir `InitializingBean`; attığı
`IllegalStateException` context'i düşürüyor, yani gözlenen davranış
`@AssertTrue` ile aynı.

**Değerlerin kopyası çıkarılmadı.** Guard `CorsProperties` ve
`TrustedProxyProperties`'in **gerçek bean'lerini** — `CorsConfig` ve
`ClientIpResolver`'ın kullandığı örnekleri — okuyor, springdoc'u da
`application.yaml`'ın bağladığı `springdoc.api-docs.enabled` /
`springdoc.swagger-ui.enabled` özelliklerinden. Guard'a `${CORS_ALLOWED_ORIGINS:}`
gibi ikinci bir bağlama yazılsaydı, yaml'daki asıl bağlama değiştiği gün kontrol
bayat bir kopyayı doğrulamaya devam ederdi.

**Springdoc'un varsayılanı da kopyalanmıyor, okunuyor.** Özellik hiç
bağlanmadığında springdoc kendini **açık** sayıyor: `SpringDocConfiguration` ve
`SwaggerConfig` `@ConditionalOnProperty(..., matchIfMissing = true)` taşıyor.
Guard'a elle yazılmış bir `FALSE` varsayılanı, yaml bağlaması olmayan bir
context'te tam ters cevabı verirdi — guard "kapalı" derken springdoc açık olurdu,
yani guard'ın önlemek için var olduğu kusurun kendisi. `springdocDefaultFor` bu
iki sınıfın `matchIfMissing` değerini **anotasyondan** okuyor ve
`Environment.getProperty`'ye onu varsayılan olarak veriyor. Okuma constructor'da
yapılıyor, yani springdoc bu anotasyonu kaldırır veya özelliği yeniden
adlandırırsa hata **her ortamda ve her testte** çıkıyor, yalnız production'da
değil.

### Görünür kılınamayan tek madde

**Aynı değişkenin Dokploy'da iki kez tanımlanması süreç içinden görülemez.**
Ortam değişkeninde son tanım kazanır ve uygulamaya **tek** değer ulaşır; ikinci
tanımın var olduğunu söyleyecek hiçbir API yok. Guard bu yüzden `SERVICE_VERSION`
için doğrulama değil **rapor** yazıyor: `INFO` satırındaki `version=` alanı hangi
tanımın kazandığını söylüyor, operatör onu Dokploy ekranındaki satırlarla
karşılaştırıyor. Doğrulanamayan bir şeyi doğruluyormuş gibi göstermektense
ölçülebilir olanı yazmak seçildi.

### Kapsam dışı — CORS ile STOMP katmanlarının sırası

`CorsConfig` filtreyi `/**` için kaydediyor ve STOMP ucu `/ws` onun altında; bu
issue yazıldığında tarayıcı handshake'i `Origin` taşıdığı için servlet
`CorsFilter` `WebSocketConfig`'teki ayrı allowlist'e sıra gelmeden 403
verebiliyordu. **Bugün veremiyor:** `CorsConfig.corsFilter` gerçek zamanlı uç
için `getCorsConfiguration`'dan `null` döndürüyor ve kararı tümüyle
`OriginHandshakeInterceptor`'a bırakıyor (§2ai), `CorsContractTest`'in
`theRealtimeHandshakeIsNotGovernedByTheRestCorsAllowlist` kuralı bunu sabitliyor.
Yani iki katmanın sırası çözülmüş durumda; bu değişiklik ona dokunmuyor ve
is-koala/koala-backend#40'a not düşüldü.

### Kontrolün kapsamadıkları

- **Dört Dokploy değeri hâlâ insan işi.** Guard sapmayı görünür kılıyor,
  düzeltmiyor; `WARN` satırlarını okuyan bir log alarmı da bu depoda yok.
- **`staging` denetlenmiyor.** Kural production'a özgü; staging'in kendi
  zorunlulukları `docs/environments.md`'de yazılı ama guard onlara bakmıyor.
- **Guard yalnız açılışta çalışıyor.** Çalışma zamanında değişebilen tek değer
  `app_settings` üzerinden gelenler (§2ac) ve bu dördü orada değil.

### Testler

`ProductionConfigurationGuardTest` yirmi yedi kural — `SecureRuntimeDefaultsTest`
deseniyle (`ApplicationContextRunner` + `ConfigDataApplicationContextInitializer`),
`@SpringBootTest` olmadan. Her kural iki yönlü: doğru yapılandırmada açılıyor ve
tek bir `WARN` bile yazılmıyor, sapmış yapılandırmada durduruyor ya da uyarıyor.
`WARN` seçilen dört kuralda satırın gerçekten yazıldığı bir `ListAppender` ile
**ölçülüyor** — "uyarır" demek yetmez (`AccessLogTest` deseni).

Kontrol yaml'ın kendi bağlamalarından sürülüyor: testler `SERVICE_ENVIRONMENT`,
`SPRINGDOC_ENABLED`, `SERVICE_VERSION`, `CORS_ALLOWED_ORIGINS` ve
`APP_TRUSTED_PROXIES` **ortam değişkeni adlarını** veriyor, `app.deployment` gibi
iç anahtarları değil. Bağımsızlık da iki yönlü sabit: `SERVICE_ENVIRONMENT=production`
guard'ı ateşliyor (context'te `IyzicoProperties` hiç yokken), `iyzico.deployment=production`
tek başına ateşlemiyor.

**Üç mutasyon üç ayrı katmanda kırmızıya döndürüldü** — saf bir yardımcıyı
bozmak, o yardımcıyı kullanan bağlamanın korunduğunu kanıtlamaz:
`application.yaml`'daki `app.deployment: ${SERVICE_ENVIRONMENT:local}` satırı
sabit `local` yapıldığında on test, catch-all proxy kuralı etkisizleştirildiğinde
üç test, `CorsProperties`'ten `@Validated` düşürüldüğünde dokuz test düşüyor.

## 2ay. SMTP ortam gözlemi — staging UNRESTRICTED çalışır

Paket 1 alıcı allowlist'i SMTP'den önce `SUPPRESSED` işaretliyor. #222 ürün
kararıyla staging ortamında da açıkça seçilmiş `UNRESTRICTED` modu çalışır;
başlangıç koruması bunu engellemek yerine görünür bir uyarı üretir.
`MailDeliveryEnvironmentGuard` aynı `app.deployment` bean'ini okur
(`ProductionConfigurationGuard` gibi ödeme prefix'ine bağlanmaz) ve local'i
olduğu gibi bırakır — mevcut IT'ler `UNRESTRICTED` kullanır.

| Kural | Karar | Neden |
|---|---|---|
| staging + `MAIL_DELIVERY_MODE=UNRESTRICTED` | **uyarır ve başlatır** | Ürün staging'de gerçek posta akışını kullanır; kısıtsız seçim logda görünür kalır. |
| staging/production + ALLOWLIST + boş `MAIL_ALLOWED_RECIPIENTS` | **uyarır** | Teslimat zaten fail-closed; sessiz "mail çalışıyor" yalanı olmasın. Alıcı adresi loga yazılmaz. |
| production + `MAIL_DELIVERY_MODE=UNRESTRICTED` | **uyarır ve başlatır** | Kısıtsız teslimat bilinçli bir ortam seçimidir; servis kullanılabilir kalır. |

İşaret: `MAIL_DELIVERY_GUARD`. Sözleşme `MailDeliveryEnvironmentContractTest`.
Marketplace seed geri gelmez (ADR-0008). Gelen kutu kabulü #4'te insan işi.

---

## 2az. Ters geokod kademeli düşüş ve `precision` (#273)

**İki kademe, tek ek istek.** `GoogleGeocodingAddressProvider.reverse` önce `result_type=street_address|premise` sorar; `ZERO_RESULTS` gelirse aynı pin için bir kez daha `route|sublocality|neighborhood|postal_code` sorar; o da boşsa `Optional.empty()` → `REVERSE_NO_RESULT`. Bir yanıtta birden çok sonuç varsa en ayrıntılı bileşen kümesi seçilir: önce `precision` (`BUILDING` > `STREET` > `NEIGHBORHOOD`), eşitlikte dolu bileşen sayısı, o da eşitse sağlayıcının sırası. Park, kıyı ve site içi noktalarda Türkiye'de bina düzeyi sonuç yok ama cadde/mahalle düzeyi var; ADR-0007 zaten "kullanıcı görüp onaylar" dediği için eksik bina numarası kullanıcıya bırakılır (`needsUserReview=true`).

**`AddressCandidateDto.precision` sağlayıcıdan değil bileşenlerden türer.** `ProviderAddressCandidate.precision()`: bina numarası ya da site adı varsa `BUILDING`, yalnız cadde/sokak varsa `STREET`, aksi hâlde `NEIGHBORHOOD`. Aynı kural autocomplete `resolve` yolunda da dolar; istemci `STREET`/`NEIGHBORHOOD` görünce "bina numarasını girin" ipucu gösterir. Enum `AddressCandidatePrecision` kardeşi `AddressPositionSource` gibi `entities` altında (§1), değerler OpenAPI'de enum olarak görünür (`OpenApiDocumentIT`).

**İl/ilçe olmayan kademeli sonuç `REVERSE_NO_RESULT`, `SEARCH_OUTSIDE_SERVICE_AREA` değil.** `resolveReferences` il/ilçe UUID'sini zorunlu tutar; aday sözleşmesi ilçesiz bir aday tanımlamaz (`AddressWriteV2` de `districtId` ister). Pin zaten `IstanbulServiceArea` kutusundan geçmişken "hizmet alanı dışında" yanlış bir cümle olurdu; doğru cümle "bu pin için adres üretilemedi" ve kullanıcı manuel akışa düşer. Sağlayıcının ilçe **adı** verip Koala referansına eşleyemediği durum (kutu kenarında Kocaeli gibi) eskisi gibi `SEARCH_OUTSIDE_SERVICE_AREA` kalır.

**Mahalle Türkiye yanıtlarında `administrative_area_level_4` olarak gelir (#277).** Google TR sonuçlarında mahalle çoğunlukla `neighborhood` değil `administrative_area_level_4` (`Rumeli Hisarı Mahallesi`, types `[administrative_area_level_4, political]`) tipindedir; eski eşleme (`neighborhood | sublocality_level_2 | sublocality_level_3`) bu yüzden Sarıyer/Okul Caddesi 15 için bina numarası ve posta kodu dolu, mahalle boş bir aday üretiyordu. Bileşen eşlemesi artık tek yerde, `GoogleAddressComponents`: il `administrative_area_level_1 | locality`, ilçe `administrative_area_level_2 | sublocality_level_1`, mahalle `neighborhood | administrative_area_level_4 | sublocality_level_2 | sublocality_level_3`, hiçbiri yoksa `sublocality_level_1` — yalnız `IstanbulServiceArea.canonicalName` ile ilçe adından **farklıysa** (Google `sublocality_level_1`'e çoğu zaman ilçe adını tekrar yazar; o mahalle değildir). Ters geokod (`long_name`/`short_name`) ve autocomplete `resolve` (`longText`/`shortText`) aynı sınıftan geçer; iki sağlayıcı yalnız alan adlarını verir. Mahalle adı ham bırakılır ("… Mahallesi" eki dâhil): Koala'da referans mahalle tablosu yok, `Address.neighborhood` serbest metin; ek kırpması ancak referans veri gelirse anlam kazanır ve o iş ayrı. Testler: `GoogleGeocodingAddressProviderTest` (gerçek Sarıyer yanıtı → mahalle dolu + `BUILDING`; ilçeyi tekrar eden `sublocality_level_1` mahalleye alınmaz; ilçeden farklı `sublocality_level_1` son çare olarak alınır), `GooglePlacesAddressProviderTest` (`resolve` aynı eşleme).

**Sağlayıcı durumu artık logda.** `status` `OK`/`ZERO_RESULTS` dışındaysa (REQUEST_DENIED, OVER_QUERY_LIMIT, INVALID_REQUEST…) tek WARN satırı `status`'u, istenen `result_type` kademesini ve pini **iki ondalıkla** (`LogMasks.coordinates` → `40.98,29.03`, ~1 km) yazar; hassas koordinat, anahtar ve istek URL'si satıra girmez. Taşıma hatasında yalnız istisna sınıfının adı yazılır — Spring'in `ResourceAccessException` mesajı `key=` içeren URL'yi taşır, o yüzden istisna nesnesi logger'a **verilmez** (bu, §2i "istisnayı logger'a ver" kuralının bilinçli istisnası; `log.error` değil `log.warn`). Yanıt istemciye eskisi gibi 502 `SEARCH_PROVIDER_UNAVAILABLE`.

**Bu turda çıkan gerçek kusur: `result_type` çift kodlanıyordu.** URL `UriComponentsBuilder…encode().toUriString()` ile `String` olarak `RestTemplate.exchange`'e veriliyordu; `RestTemplate` string şablonu bir daha kodlar, `%7C` → `%257C` olur ve Google `street_address%7Cpremise` diye tek bir geçersiz tip görürdü. Artık `URI` nesnesi geçiliyor; `GoogleGeocodingAddressProviderTest` istek URI'sinde `%25` olmadığını sabitliyor. `GooglePlacesAddressProvider` de aynı turda `URI` geçişine alındı; place id ve oturum belirteci bugün `%` üretmese de kalıp tekrar etmesin.

Testler: `GoogleGeocodingAddressProviderTest` (üç kademe, çoklu sonuçta en ayrıntılı seçim, REQUEST_DENIED ve taşıma hatası günlüğü, ikinci kademenin yalnız alan tiplerini istemesi), `AddressSearchManagerTest` (STREET/NEIGHBORHOOD adayı, ilçesiz aday → `REVERSE_NO_RESULT`, referans sorgusuna hiç gidilmez), `AddressReverseGeocodeIT` (gerçek HTTP ve seed Kadıköy referansıyla üç kademe + boş + REQUEST_DENIED 502; paylaşılan `RestTemplate`'e `MockRestServiceServer` bağlanır ve `@AfterEach` istek fabrikasını geri koyar — yeni context açılmaz), `LogMasksTest`.

---

## 2ba. Kullanıcı atama DTO'ları — zaman çizelgesi ve işveren kimliği (#282)

14.09.2026 cihaz turu: kullanıcı kabul aldığı işin detayında "ne zaman başvurdum, ne zaman kabul edildim, işveren
kim" göremiyordu. Veri entity'lerdeydi (`JobAssignment.application → JobApplication.applicationDateTime/
answeredDateTime`, `createdAt`, `Job.creator`, `Cancellation`, `Payout`, `Payment`), DTO'lara çıkmıyordu.

**`MyAssignmentDto` sekiz alan kazandı** (`/users/me/assignments`, `/users/me/work`.assignment, `/job-assignments/{id}`,
`/jobs/{jobId}/assignments`, respond/complete yanıtları): `application {id, applicationDateTime, answeredDateTime}`
(`AssignmentApplicationDto`; doğrudan atamada null — kabul anı `answeredDateTime`), `createdAt`, `confirmedAt`,
`expectedCompletionDateTime`, `paymentSecuredAt`, `employer: SafeUserDto` (ilan sahibi kişi — bireysel ilanda gösterilecek
ad), `company {id, name, logoUrl}` (`CompanySummaryDto`; bireysel ilanda null), `cancellation {cancelledByParty,
cancelledAt, feeRate, feeAmount, refundAmount}` (`AssignmentCancellationDto`; para alanları §2p'deki gibi exact decimal
string), `payout {id, status, completedAt}` (`AssignmentPayoutDto`). Olay yaşanmadıysa alan null; istemci satırı gizler.

**`confirmedAt` yeni sütun (`job_assignments.confirmed_at`, `14-03-assignment-timeline-stamps.xml`).** İşçinin
kabulü ile işverenin kabulü iki ayrı andır: işverenin kabulü başvurunun `answeredDateTime`'ı, işçininki
`POST /job-assignments/{id}/respond` `accepted=true` anında damgalanır; ret damgalamaz, geçmiş kayıtlarda null kalır
ve **geri doldurulmaz** — `updated_at` o satırın son değişikliği, kabul anı değildir. Aynı changelog `jobs.cancelled_at`
ekler; `JobManager.cancelJob` damgalar, `JobDetailDto.cancelledAt` okur (iptal edilmemişse ya da bu sütundan önce
iptal edildiyse null).

**`paymentSecuredAt` = ilk `SUCCEEDED` ödemenin `completedAt`'i.** `PaymentDao.findSecuredAtByJobAssignmentIds`
sayfa başına tek `min(completedAt) … group by` satırı (`CollectionQueryWithoutGraphTest` allowlist'inde, aggregate);
başarısız denemeler sayılmaz. `PaymentService.securedAtForAssignments` ile servis üzerinden.

**Bağlı kayıtlar sayfa başına dört toplu sorguyla, satır başına değil (§2af, §2o'daki `findLatestByJobsAndWorkers`
kalıbı).** `JobAssignmentManager.linkedRecordsOf` sayfanın id'leriyle `CancellationDao.findAllByJobAssignmentIdIn`,
`PayoutService.findForAssignments`, `PaymentService.securedAtForAssignments` ve (#293'ten beri)
`ReviewDao.findAllByJobAssignmentIdIn` çağırır, sonuçları id'ye anahtarlar
(şema atama başına tek iptal ve tek hakediş garanti ediyor, birleştirme yok; değerlendirme atama başına liste) ve
MapStruct'a beş kaynaklı `toMyAssignmentDto(assignment, cancellation, payout, paymentSecuredAt, viewerFacts)` ile
verir; iptal özeti `CancellationMapper.
toAssignmentCancellationDto` ile (`CancellationDto`'nun exact-decimal ifadeleriyle aynı yerde). Beş üretici
(`getMyAssignments`, `getAssignmentsForJob`, `getMyAssignment`, `answerToAssignment`, `completeAssignment`) iki
private yardımcıdan geçer — sayfalar `toPage`, tekiller üç parametreli `toMyAssignmentDto` — ve ikisi de adres
görünürlüğünü ve `galleryService.fillJobCovers`'ı uygular; respond yanıtı da `job.coverImageUrl` taşır. **`JobAssignmentManager → CancellationDao` yeni bir §2ae muafiyeti:**
`CancellationManager` zaten `JobAssignmentService`'e bağlı, `CancellationService` üzerinden gitmek döngü olurdu; salt
okuma, gerekçesi `AggregateBoundaryTest`'te. `PaymentService` ve `PayoutService` zaten enjekte edildiği için o ikisi
servis üzerinden.

**Grafikler büyüdü, muafiyetler düştü.** `findAllByWorkerId` / `findAllByJobCreatorId` / yeni `findAllByJobId` ve
`findWithDetailsForPaymentById` artık `job.creator(.profilePicture)`, `job.company(.logo)`, `application` ve
`job.address.city/district` çeker — son ikisi eskiden lazy'ydi ve `JobSummaryDto.cityName/districtName` her satırda
iki sorgu attırıyordu; `JobApplicationDao.findAllByUserId(AndStatus)` de aynı düzeltmeyi aldı.
`JobSummaryDto.companyLogoUrl` eklendiği için `JobDao.findAllByIdIn/findAllByCreatorId/findAllByCompanyId`
grafiklerine `company.logo` girdi ve `EagerFetchCoverageIT`'nin "JobSummaryDto yalnızca companyName taşıyor"
muafiyetleri silindi. `AssignmentReadIT.theJobAssignmentListIsBuiltWithoutAQueryPerRow` `size=1` ile `size=2`
sayfanın statement sayısını eşitliyor ve `EntityFetchCount`'u sıfırlıyor (AdminDetailReadIT deseni).
`GET /users/me/saved-jobs` bilerek dokunulmadı: `user.getSavedJobs()` koleksiyonundan geçiyor ve `companyName`
için zaten satır başına yükleme yapıyordu; ayrı iş.

**`POST /job-assignments/{id}/respond` artık `MyAssignmentDto` döndürüyor, `JobAssignmentDto` silindi.** İki istemci
de yanıttan yalnız `status` okuyordu (mobil `assignment_service.dart` bunu yorumda yazıyordu); `MyAssignmentDto` üst
düzeyde `JobAssignmentDto`'nun her alanını taşır, yalnız `company` alt şekli `OnlyCompanyDto` (vergi no, adres…)
yerine `CompanySummaryDto`'dur — işçiye açılan yanıttan işverenin vergi numarası ve adresi düşmüş oldu.

**`GET /jobs/{jobId}/assignments` yeni uç.** İlan sahibi ya da şirket ilanının güncel yetkilisi; `PageDto<MyAssignmentDto>`,
`/users/me/assignments` ile aynı sıralama alanları ve varsayılan (`assignmentDateTime,desc`). İşveren istemcileri
`/users/me/assignments?asEmployer=true` sayfalarını tarayıp ilana göre süzmeyi bırakır. Yetki `JobAssignmentManager`'da
`JobService.getById` + `CompanyContactService` ile; başkasının ilanı ve olmayan ilan aynı `JOB_NOT_FOUND` 404 zarfını
alır (`Concealment.notFound`, §2d2; `ExistenceConcealmentIT` ve `AssignmentReadIT` gövdeyi birebir karşılaştırıyor).
İlan herkese açık bir kaynak olduğu için burada gizlenen varlık değil, "kimin ilanı" bilgisidir. Kimlikli okuma,
hız sınırı kovası yok (§2j, `/jobs/{jobId}/applications` ile aynı). `JobAssignmentManager → JobService` döngü
üretmiyor (JobManager `JobAssignmentDao`'ya doğrudan uzanıyor, servise değil).

**Diğer DTO'lar.** `JobApplicantDto` (`GET /jobs/{jobId}/applications`): `answeredDateTime`, `assignmentId`
(`assignment.id`, grafik zaten çekiyordu), `rejectionReason` — web'in tüm atamaları tarayarak eşlemesi biter.
`PayoutDto` (`/users/me/payouts`): `jobTitle`, `companyName`; grafik `jobAssignment.job(.company)` kazandı,
`company.logo` muafiyette (DTO çizmiyor). `JobDetailDto`: `applicationCount` (`countByJobId`, durumdan bağımsız),
`updatedAt`, `cancelledAt`. `PublicProfileDto` (`GET /users/{id}`): `completedJobCount`
(`JobAssignmentService.countCompletedForWorker`), `averageRating`/`reviewCount` (`ReviewService.getSummaryFor`,
yoksa `0`/`0` — `EmployerStatsDto` ile aynı karar; `/users/{id}/rating-summary` ise özet yokken `null` döner),
`badges[]` (`BadgeService.getBadgesOf`, `/users/{id}/badges` ile aynı liste ve grafik). `PublicProfileManager`
orkestratör kaldı, DAO'ya uzanmaz.

**Fiyat alanları görüntüleyene göre kısılır — adres görünürlüğüyle aynı kalıp (#293).** Görüntüleyen atamanın
işçisiyse (`assignment.worker.id == viewerId`; ek sorgu yok) `totalCharged = null` ve **`pricing = null`** (nesne
hiç gelmez); işçi kendi kazancını üst düzey `workerPayout`'tan okur. İşveren ve şirket yetkilisi için değişiklik
yok; personel uçları (`AdminAssignmentDto`) etkilenmez. `respond` yanıtı işçiye gittiği için kısılmış, `complete`
yanıtı işverene gittiği için tam gelir. Ürün kararı: işçi yalnız kendi kazancını görür; komisyon ve işverenden
çekilen toplam yalnız işverene. Görüntüleyene bağlı dört alan (`totalCharged`, `pricing`, `myReview`,
`counterpartyReviewed`) mapper'a tek kaynaktan, `JobAssignmentMapper.ViewerFacts` ile girer;
`JobAssignmentManager.viewerFactsOf` üretir. **Neden alanları null'lanmış bir nesne değil de `null`:** yayındaki
mobil `PricingBreakdown.fromJson` fee alanlarını `requiredDecimalString` ile okuyor — alanı null bir nesne
`FormatException` fırlatır, `pricing: null` ise `Assignment.fromJson`'da tolere edilir ve `WorkDetailPage`
etiketi `workerPayout`'tan üretir; web `backend-contract.ts` de `pricing?.workerPayout ?? workerPayout` okur.
Yani sözleşme değişikliği yayındaki iki istemciyle geriye uyumlu. Mapper tarafında `totalCharged` ve `pricing`
`conditionExpression = "java(!viewer.workerView())"` ile koşullu eşlenir (`AdminDetailMapper`'ın `includePii`
ifadesiyle aynı kalıp); `PricingDtoMapper` yalnız mapper'ın `uses` listesinde, manager'a girmez. Karşı taraf kuralı
`JobAssignmentManager.isCounterpartyOf` (yazar işçi mi ≠ görüntüleyen işçi mi). `MyAssignmentDto.job.pricing` (`JobSummaryDto`)
bilerek dokunulmadı: ilan listelerinde herkese açık olan aynı nesne, ayrı issue.

Testler: `AssignmentReadIT` (null hâl, dolu hâl, başarısız ödeme sayılmaz, respond `confirmedAt`, iki işveren rolü
listeler / işçi ve yabancı 404 / bilinmeyen ilanla aynı gövde / bilinmeyen sıralama 400, N+1 — değerlendirme
tohumlu, işçi/işveren fiyat görünümü, respond işçi görünümü, `myReview`/`counterpartyReviewed` üç aktörle iki
yönde ve herkese açık listede `jobAssignmentId` null), `ExistenceConcealmentIT`,
`ApplicationDecisionIT` (aday listesi üç alan, ilan detayı sayım ve iptal damgası), `PayoutQueueIT`, `PublicProfileIT`,
`AssignmentOperationsAuthorizationTest` (kabul damgalar, ret damgalamaz), `MapperCharacterizationTest`,
`OpenApiDocumentIT`, `EagerFetchCoverageIT`, `EntityGraphPathsIT`, `CollectionQueryWithoutGraphTest`,
`AggregateBoundaryTest`. İstemci tarafı is-koala/koala#177, #178, is-koala/koala-web#165, #166.

---

## 3. Testler

`src/test/resources/application.properties` testlerin ayağa kalkması için gereken minimum değerleri sağlar. Buradan bir satır silmeden önce neye yaradığını bil:

- `jwt.secret` — `JwtService` açılışta Base64 çözülmüş anahtarı HS256 için en az 32 bayt bekliyor, kısa anahtarda context düşer.
- ~~`r2.*`~~ — **artık gerekmiyor.** `storage.type=local` varsayılanı sayesinde testler hiçbir bulut ayarı istemiyor (§2ab).
- `app.api-url` / `app.web-base-url` ve `payments.return-base-url` — doğrulanmış yapılandırmalardır, boş bırakılamaz.
- `spring.mail.*` — `EmailConsumer` constructor'ında `JavaMailSender` istiyor. Bu değerler yoksa Boot mail bean'ini hiç oluşturmaz ve context "No qualifying bean of type JavaMailSender" ile düşer. Testlerde gerçek SMTP'ye bağlanılmaz. Tanıtım sitesi iletişim formu (`POST /public/contact`) aynı kuyruğu kullanır; alıcı `SUPPORT_MAILBOX` (varsayılan `info@iskoala.com`). Next.js SMTP tutmaz.
- `spring.rabbitmq.listener.*.auto-startup=false` — testlerde RabbitMQ'ya bağlanılmıyor.
- `spring.datasource.hikari.maximum-pool-size=4`, `minimum-idle=0`, `idle-timeout=10000` — aşağıdaki bağlantı bütçesi.

Datasource'u `AbstractIntegrationTest` içindeki Testcontainers sağlar. Konteyner `static` ve tüm entegrasyon testleri aynı örneği paylaşır; sınıf başına yeni Postgres kaldırmak paketi dakikalarca uzatıyor.

**Yük altında zamanlama iddiası kanıt değildir.** Yarış IT'lerinde "ilerledi" bekleyen latch ve `Future.get` sabit 5 saniye değil `TestLatches.progressTimeout()` kullanır (`-Dkoala.test.latch-timeout`, yoksa `KOALA_TEST_LATCH_TIMEOUT`, yoksa 5 s). Pi işi 30 s verir. Tutma latch'leri (`releaseDelivery`, `allowMetadataCommit`) `awaitHold` ile süresiz bekler — aynı 30 s'yi paylaşırlarsa işçi, test `countDown` etmeden düşer. "Bitmedi" iddiaları (250 ms) değişmez — yavaş makinede yanlış yeşile dönmesinler (#240, is-koala/koala#97).

**Yük altında Hikari zaman aşımı bellek bitmesi değildir.** `Connection is not available … (total=1, active=1)` havuzun 2'lik tavanında ikinci JDBC bağlantısının Postgres'ten gelmediğini söyler; OOM killer JVM'i öldürür, 70 saniye beklemez. Broker IT'leri ana iki-fork paketle aynı anda koşmasın (§2).

**Bağlantı bütçesi: her önbelleğe alınan context tek Postgres kabından pay alır.** Spring TestContext her farklı yapılandırma için ayrı bir context açar ve kapatmaz — `@DynamicPropertySource` metot kümesi, `@MockitoBean` seti, `@TestPropertySource`, `@ActiveProfiles` ve `@AutoConfigureMockMvc` hepsi ayrı bir önbellek anahtarı üretir. Her context kendi Hikari havuzunu kurar; **Hikari varsayılanı `maximumPoolSize=10` ve `minimumIdle` ona eşit**, yani boşta duran bir context bile on bağlantıyı tutmaya çalışır. `postgres:17` kabı varsayılan `max_connections=100`, `superuser_reserved_connections=3` ile geliyor — sıradan kullanıcıya 97 kalıyor.

Bu sessiz bir tavandır ve **çarpma bir gün tutmaz**: #35 ile paket on context açıyordu (100 istek, 97 arz — sınırın hemen dibi, hâlâ yeşil), personel dilimi `AbstractStaffIntegrationTest` ile ikinci bir `@DynamicPropertySource` kümesi getirince on birinciyi açtı ve tavan aşıldı. Belirti yanıltıcıydı: `/admin/**` ile hiç ilgisi olmayan `PasswordResetIT` beş testinde "Failed to load ApplicationContext" verdi — o context sırada **sonuncu** yüklendiği için bağlantısız kalan oydu. Gerçek sebep yığının dibindeki `PSQLException: FATAL: sorry, too many clients already`; log'daki `ConnectException: Connection refused` satırları RabbitMQ'ya ait ve `main`'de de var, ilgisiz.

Çözüm havuzu küçültmek, kabı büyütmek değil: context sayısını kontrol edemiyoruz (36 `*IT` sınıfı, her yeni test tabanı bir tane daha açabilir), havuz boyutunu edebiliyoruz ve MockMvc testleri tek iş parçacıklı, dörtten fazlasını hiç kullanmıyor. `minimum-idle=0` boştaki context'in havuzunu doldurmasını engelliyor, `idle-timeout` da tuttuğunu geri veriyor. `IntegrationTestConnectionBudgetTest` aritmetiği sabitliyor: yirmi context bütçesi × havuz boyutu ≤ 97. Havuz kapağını kaldıran ilk commit'te kırmızıya döner.

**Yetkilendirme testleri saf birim testi.** `JobApplicationAuthorizationTest` ve `PaymentSessionAuthorizationTest` Spring context'i kaldırmadan, mock'lanmış bağımlılıklarla manager'ı doğrudan kurar. İkisi de yazıldığında kırmızıydı: yabancı bir kullanıcı hem başvuru kabul edebiliyor hem ödeme oturumu başlatabiliyordu. Yeni bir yetki dalı eklerken bu iki dosyaya karşılık gelen test yazılmalı.

**Faz 10/10B testleri de saf birim testi.** `MessagingAuthorizationTest`, `ReviewRulesTest`, `AssignmentOperationsAuthorizationTest`, `ReportRulesTest` ve `CancellationPolicyTest` Spring context'i kaldırmadan çalışır. Yeni bir yetki dalı ya da para kuralı eklerken karşılığı bu dosyalara yazılır.

**Mesajlaşma ödeme kapısı testleri (§2o, #257).** `MessagingAvailabilityTest` yüklemi tek başına sabitliyor (atama yok / ödenmemiş / başarısız → `PAYMENT_REQUIRED`; iptal, ret, iade ve pencere → `CLOSED`; emanet ve çıkış → `AVAILABLE`; iptal ödemeden önce gelir). `MessagingAuthorizationTest` kapıyı manager'da: ödenmemiş çift 403 `MESSAGING_PAYMENT_REQUIRED`, engel listesine hiç bakılmaz, `HELD_IN_ESCROW`/`RELEASED_TO_WORKER` açar, iptal/iade/ret 409 `MESSAGING_WINDOW_CLOSED`, ikili sohbette gönderim kapıyı yeniden sorar, kapı öncesinden kalma sohbet salt okunur, grupta iadesi yapılmış/ödenmemiş üye yazamaz ama işveren tarafı atamasız yazar, liste sayfası ikili sohbetlerin son atamalarını tek toplu sorguyla çözer. `LatestAssignmentLookupTest` "son atama"nın `createdAt`'e göre seçildiğini, damgasız satırın damgalıyı geçemediğini ve tekil/toplu tanımın aynı satırı verdiğini sabitliyor. `CrewConversationRulesTest` grubu: şirket ilanının grubu bütün yetkililerle kurulur, bireysel ilan yetkili sormaz, kapanan atama işçiyi çıkarır, olmayan gruptan çıkmak zararsız, işveren hiç çıkarılmaz, yeni yetkili yalnız penceresi açık gruplara katılır, çıkarılan yetkili hepsinden çıkar. `CrewConversationListenerTest` dört olayın yönlendirmesini, dinleyicinin `AFTER_COMMIT` + `fallbackExecution` olduğunu ve dört manager metodunun `REQUIRES_NEW` olduğunu; `CompanyContactCrewEventsTest` iki yetkili olayının kayıttan **sonra** yayımlandığını; `PaymentCallbackTest` ödenen olayın `paymentDao.save`'den sonra ve yalnız başarıda yayımlandığını; `AssignmentOperationsAuthorizationTest`, `UnpaidExpiryRulesTest` ve `PayoutReleaseTest` kapanış olayının üç yazma yolundan yayımlandığını (çıkışta yayımlanmadığını) sabitliyor. `MessagingPaymentGateIT` on yedi kuralı gerçek HTTP, gerçek Postgres ve sahte Iyzico ile koşuyor: ödeme öncesi üç tarafın da 403'ü ve grubun yokluğu, `POST /job-assignments/{id}/respond` ile kabulün grup kurmaması (ödeme gelince kurması) ve reddin `AssignmentClosedEvent` üzerinden o işçinin ikili sohbetini kapatması, atama yanıtındaki `messagingAvailable=false`/`PAYMENT_REQUIRED`, `POST /payments/callback` ile emanete geçince grubun ilan sahibi + yetkili + işçiyle kurulması ve ikili sohbetin 200 ile açılması, ikinci ödemenin aynı gruba katılması, ödenmemiş ikinci işçinin 403 alması, yetkilinin grubu okuyup yazması, sonradan eklenen yetkilinin katılıp çıkarılanın düşmesi ve 404 alması, 7 gün sonrası 409'un korunması, yabancının ödeme öncesi/sonrası **aynı** 404 zarfını alması, `POST /job-assignments/{id}/cancel` sonrası `REFUNDED` + gruptan çıkış + ikili sohbette 409 + geçmişin okunabilir kalması + `closed=true`, dinleyici düşmüş gibi gruba geri yazılan iadeli işçinin grupta 409 (ödenmemişin 403) alıp okumaya devam etmesi, aynı çiftin eski `REJECTED` + yeni ödenmiş atamasıyla sohbetin açık, eski ödenmiş + yeni `REJECTED` atamasıyla kapalı olması, ve kapı öncesinden kalma ikili sohbetin ödemeyle açılması.

**Faz 11C testleri.** `WorkerToolsRulesTest` on yedi kural sabitliyor: deneyim sahipliği ve tarih tutarlılığı, müsaitlik aralıklarının toptan değişmesi ve çakışma reddi, geçmiş tarihe istisna yasağı, talent pool'da kendini ekleme yasağı ve idempotanlık, şablonun başkasının ilanından çıkarılamaması, kriteri olmayan alarmın reddi ve alarm sayısı sınırı. `UserProfileContractTest` deneyimin artık yapılandırılmış döndüğünü doğruluyor.

**Kontenjan, ödenmemiş atama ve değerlendirme bildirimi testleri.** `JobHeadcountRulesTest` dokuz kural sabitliyor (§2ao2): kontenjansız ilan sınırsız işe alır ve sayım sorgusu hiç çalışmaz, son yer doldurulabilir, dolu ilan 409 verir ve başvuru `APPLIED` kalır, kontenjanın üstüne çıkmış ilan da reddeder, dolu ilan **reddetmeye** devam edebilir, kontenjan dolunca ilan kapanmaz, iş başladıktan sonra da işe alım yapılabilir, kabul yayınlanmış fiyatı atamaya kopyalar. `UnpaidExpiryRulesTest` dokuz kural: ödenmemiş atama düşer, başarısız ödeme denemesi de düşer, emanetteki para dokunulmaz kalır, kabul edilmemiş teklife dokunulmaz, kayıt işveren tarafına sıfır tutarla yazılır, iki tarafa da bildirim gider, ikinci koşu zararsız, bir atamadaki hata partiyi düşürmez, kapalıyken hiç çalışmaz. Değerlendirme bildirimi `ReviewRulesTest` içinde: puan verildiğinde gider, reddedilen değerlendirmede hiç gitmez.

**Duvar saati kuran testler yapılandırılmış zaman dilimini kullanmak zorunda.** `Job.startDateTime` ve `endDateTime` duvar saati (§2h); `CancellationPolicy`, otomatik tamamlama ve ödenmemiş atama zamanlayıcısı bunları **`Europe/Istanbul` varsayarak** `Instant`'a çeviriyor. Test `LocalDateTime.now().plusHours(2)` yazarsa sonuç makinenin saat dilimine bağlı olur: geliştirme makinesi Istanbul'da yeşil, **CI runner'ı UTC'de kırmızı** — üç saat geriye kayan değer geçmişe düşüp cezasız pencereyi geç iptal cezasına çevirir. `CancellationPreviewTest` tam olarak böyle kırıldı.

Kural: bu tür testlerde başlangıç zamanı `LocalDateTime.now(POLICY_ZONE)` üzerinden kurulur. Şüphedeysen `TZ=UTC ./mvnw verify` ile çalıştır — CI'ın gördüğü budur.

**Active Authentication testleri.** `ActiveAuthenticationTest` otuz kural (üçü parametreli, toplam 44 koşu) — sentetik RSA ve EC çip anahtarlarıyla. İlk yedisi: RSA çip sunucunun sayısını imzaladığını kanıtlar, EC çip de öyle, başka bir sayı için üretilmiş imza reddedilir, **yakalanmış imza yeni sayıya karşı geçmez**, bozulmuş imza reddedilir, anahtar taşımayan DG15 reddedilir, başka çipin anahtarı doğrulamaz. #253 ile gelenler: brainpoolP256r1 + SHA-256 düz, secp384r1 + SHA-384 düz ve DER, on EC özet×kodlama bileşiminin tamamı DG14'süz geçer, secp384 yanlış sayı reddedilir, **açık parametreli EC anahtarı** okunur ve tanıda `explicit(brainpoolP256r1)` yazar, DG14'ün bildirdiği algoritma yelpaze dışında olsa bile (RIPEMD160 düz) kullanılır ve DG14'süz aynı imza reddedilir, DG14 `ecdsa-with-SHA384` DER'i seçer, tanınmayan OID yelpazeye düşer ve tanıda `(unrecognised)` görünür, okunamayan DG14 doğrulamayı engellemez, DG15 zarfı kısa/`0x81`/`0x82`/`0x83` her biçimde okunur, üç baytlık uzunlukla RSA anahtarı okunur, bildirilen uzunluktan sonraki dolgu anahtarı gizlemez, dosyadan uzun bildirim `chip.key.unreadable`, EC ve RSA başarısızlık tanısı alanları taşır ve challenge/imza/DG15'in hex ya da base64'ünü **taşımaz**, kısmi kurtarmalı (M1 nonce + M2 challenge) RSA imzası doğrulanır ve başka sayıya karşı düşer. İnceleme sonrası eklenenler: açık trailer'lı SHA-224/256/384/512 RSA imzası ICAO uzunluğunda nonce ile geçer ve başka sayıya düşer (BC ile `implicit=false` üretilmiş; yalnız `implicit=true` doğrulayan kodda beşi de kırmızıydı), `0xBC` SHA-1 kapasite dolusu nonce ile ve açık trailer'lı SHA-1 geçer, 100 baytlık kısa nonce (bütün mesaj kurtarılabilir) geçer ve başka sayıya düşer, sıfır dolgulu 33+33 baytlık düz `r‖s` reddedilir. `IdentityChallengeRulesTest` yedi kural: sayı 8 rastgele bayt ve saklanır, iki sayı asla aynı olmaz, tanınmayan sayı reddedilir, süresi dolan reddedilir, imza tutmayınca sayı tüketilmiş sayılmaz, başka kullanıcının sayısı bulunamaz, başarısız imza `IdentityChallengeManager` logger'ında tek WARN satırı bırakır ve satırda challenge ile imza yoktur. `LdsFilesTest` on beş koşu: kısa form ve `0x81`/`0x82`/`0x83` açılır, bildirilen uzunluğun ardı atılır, dosyadan uzun bildirim (`0x82` ve `0x83`) reddedilir, belirsiz (`0x80`) ve üç oktetten uzun formlar (`0x84`, `0x85`, `0xFF`) reddedilir, `int` taşırmaya çalışan `0x84 7F FF FF FA` ve `0x83 FF FF FF` girdileri OOM değil `IllegalArgumentException` ile biter, etiketsiz ya da çok kısa dosya olduğu gibi döner.

**Paketlenmiş çıpa testleri.** `BundledTrustAnchorsTest` üç kural: imaj Türkiye çıpalarıyla geliyor, explicit EC parametreli sertifikalar da yükleniyor (varsayılan sağlayıcı bunları düşürüyordu), ve en az bir çıpa bugün hâlâ geçerli — hepsi süresi dolmuşsa liste yenilenmeli demektir ve test kırmızıya döner.

**Faz 27 testleri.** `PassiveAuthenticationTest` otuz kural (dokuz çekirdek + yedi MRZ + dört DG11 + beş DG15 + dört DG14 + bir SOD zarfı) — hepsi **sentetik ama gerçek** bir PKI'ya karşı: `TestChip` kendi CSCA'sını ve Document Signer'ını üretip gerçek bir CMS `SignedData` imzalıyor. Çekirdek dokuz: geçerli çip okuması imzalı kimliği döndürür, aynı SOD aynı parmak izini verir, kurcalanmış DG1 reddedilir, tanınmayan ülkenin imzası reddedilir, bozulmuş imza reddedilir, SOD olmayan çöp reddedilir, güven çıpası yokken hiçbir şey doğrulanmaz, MRZ etiketli DG1'den ayrıştırılır, TD1 olmayan alan reddedilir. Ayrıca DG11 için dört kural: ad ulusal karakterlerle DG11'den okunur, kurcalanmış DG11 reddedilir, DG11 yokken doğrulama yine geçer, adı olmayan DG11 ölümcül değil. DG15 için beş kural (#74): kartın kendi DG15'i SOD'a karşı doğrulanır, saldırganın kendi anahtar çiftinden DG15 reddedilir, SOD'un hiç imzalamadığı DG15 reddedilir, DG15 taşımayan ve taşıyan kart anahtar gönderilmeden geçer. DG14 için dört kural (#253): kartın kendi DG14'ü SOD'a karşı doğrulanır, kurcalanmış DG14 `data.group.tampered`, SOD'un hiç imzalamadığı DG14 `security.info.unsigned`, DG14 taşıyan kart DG14 gönderilmeden de geçer; ve `0x83` uzun formlu SOD zarfı ayrıştırılır. Ve yedi MRZ kuralı: kart sahibinin adı iddia edilenle eşleşmeli, Türkçe karakter katlanır, 30 karakterde kırpılan uzun ad kilitlemez, TCKN ayırıcıdan sonra okunur, ayırıcısız yerleşim de okunur, kırpılmış belge numarası TCKN sanılmaz, 11 haneden farklı optional data reddedilir. `IdentityVerificationRulesTest` yedi kural kazandı: bayrak kapalıyken kanıt istenmez, açıkken zorunlu olur, doğrulanan çip kanıtı hesaba damgalar, çiple uyuşmayan TCKN ve doğum tarihi reddedilir, kriptografik hata kendi kodunu taşır, base64 olmayan kanıt kriptografiye hiç gitmez. #253 üç kural daha ekledi: çözülen `dg14` hem doğrulayıcıya hem `IdentityChallengeService.consume`'a geçer, gönderilmezse ikisine de `null` gider, SOD'un imzalamadığı DG14 `IDENTITY_SECURITY_INFO_UNSIGNED` ile durur ve challenge'a hiç dokunulmaz.

**Bildirim akışı testleri.** `NotificationFeedRulesTest` dokuz kural: kayıt hem başlığı hem cümleyi taşır, hedefini taşır, teslimat beklemeden `SENT` olur, akış yalnızca `IN_APP` döndürür, rozet yalnızca onu sayar, toplu işaretleme yalnızca ona dokunur, tek bildirim okundu işaretlenir, zaten okunmuş olan değişmez, başkasının bildirimi işaretlenmez. `ActivityNotificationRulesTest` üç kural kazandı: her olay akışa bir kez düşer, hedefini taşır, akış kaydı patlasa da diğer kanallar gider.

**Faz 28 testleri.** `SmsConsumerTest` yedi kural: kuyruktaki mesaj teslim edilip `SENT` işaretlenir, ticari kategori İYS bayrağını taşır, işlemsel taşımaz, sağlayıcı hatası kaybolmaz `FAILED` olur, kaydı olmayan görev hiç gönderilmeden düşer, gövdesi üretilemeyen görev boş SMS atmaz, tanınmayan şablon tahmin edilmez. `MinimumAppVersionTest` on bir kural: güncel ve daha yeni istemci geçer, eski 426 alır, yanıt gerekli sürümü taşır, başlıksız ve ayrıştırılamayan sürüm engellenmez, sağlık ucu ve sağlayıcı callback'i hiç engellenmez, boş eşik kapıyı kapatır, sürümler sayısal karşılaştırılır, ayar satırı yoksa `application.yaml` değerine düşülür.

**Kimlik kapısı testleri.** `IdentityVerificationRulesTest` altı kural: doğrulama artık hiçbir dış servise bağlı değil, ad Türkçe locale ile büyütülüyor, belge numarası saklanıyor, başka hesapta kayıtlı TCKN 409 alıyor, ikinci doğrulama hâlâ reddediliyor, rozet/bildirim/Iyzico senkronizasyon event'i çalışmaya devam ediyor. `EmployerIdentityGateTest` üç kural: doğrulanmış bireysel işveren ilan açabilir, doğrulanmamış açamaz, kapı adres oluşturulmadan önce çalışır. `PaymentSessionAuthorizationTest` iki kural kazandı: doğrulanmamış bireysel işveren ödeme başlatamaz ve alt üye işyeri hiç açılmaz, şirket ilanı bu kapıdan etkilenmez.

**IBAN sahipliği testleri (§2k2).** `IbanVerificationRulesTest` on üç kural: başlatma tekil referans ve kuruş
ölçekli bir tutar üretir, bekleyen doğrulama yeniden sorulduğunda aynı talimatı döndürür ve deneme harcamaz,
**başka hesaptan gelen transfer doğrulamaz**, yanlış tutar doğrulamaz, kayıtlı hesaptan gelen transfer doğrular,
ekstre ad taşımıyorsa `accountHolderName` doğrulanmamış kalır, taşıyorsa yazılır ve doğrulanmış işaretlenir,
**süresi dolan doğrulama yeni talimatla yeniden başlatılabilir**, süresi geçtikten sonra gelen transfer
reddedilir, deneme bütçesi tükenir, doğrulanmış hesap bankaya geri gönderilmez, tanınmayan referans 404,
tahsilat hesabı tanımsızken doğrulama başlatılamaz. `BankAccountTest` üç kural kazandı: **doğrulanmış IBAN'ın
üstüne yeni IBAN yazılınca doğrulamanın her izi düşer** (durum, referans, tutar, süre, deneme sayısı, ad),
aynı IBAN yeniden gönderildiğinde doğrulama korunur, yalnız doğrulanmış birincil hesap doğrulanmış sayılır.
`PayoutReleaseTest` iki kural kazandı: doğrulanmamış IBAN'lı işçiye emanet çıkmaz ve kuyruğa hiçbir mutabakat
görevi girmez, çıkan `Payout` doğrulanmış hesabı gösterir. `UserProfileSyncRulesTest` altı kural (#109):
doğrulanmamış IBAN Iyzico senkronizasyon kuyruğuna girmez, doğrulanmış girer, doğrulanmış IBAN'ın üstüne
yazılan yenisi kendisi doğrulanana kadar girmez, hesabı olmayan kullanıcı IBAN'sız senkronize olur, ve
**olayın geri kalanı iki hâlde de gider** — son ikisi olmadan "senkronizasyonu tümden ertele" seçeneğinin
elenmesi kodda bağlı olmazdı. Test `BankAccountManager`'ı gerçek nesne olarak kuruyor, yani doğrulama
süzgeci taklit edilmiyor; `findVerifiedPrimaryAccount` yerine `findPrimaryIban` yazan mutasyon üç testi
birden kırmızıya döndürüyor. `UserProfileSyncOutboxAtomicityIT` / `UserProfileSyncDispatchIT` /
`UserProfileSyncOutboxRabbitIT` (#158) çağıran transaction ile intent'in atomikliğini, broker kaybında
retry/exhaustion ve Testcontainers redelivery idempotency'sini sabitliyor.

`IbanOwnershipGateIT` kapıyı **gerçek Postgres üzerinde** kanıtlıyor: emanetteki para doğrulanmamış IBAN'a
çıkmıyor ve `payment_status` `HELD_IN_ESCROW` kalıyor, doğrulandığında çıkıyor, doğrulanmış IBAN'ın üstüne
`savePrimaryAccount` ile yeni IBAN yazıldığında satırdaki bütün doğrulama izleri siliniyor ve kapı yeniden
kapanıyor. `IbanVerificationIT` akışın tamamını HTTP üzerinden koşuyor: talimat üretimi ham IBAN'ı yankılamıyor,
başka IBAN'dan gelen transfer `BANK_ACCOUNT_VERIFICATION_SENDER_MISMATCH` ile reddediliyor, kayıtlı IBAN'dan
geleni doğruluyor, düşen deneme yeniden başlatılıp başarıya ulaşabiliyor, doğrulanmış hesap 409 alıyor,
finans dalında olmayan personel onaylayamıyor, ve `PATCH /users/me` ile IBAN değiştirmek doğrulamayı düşürüyor.

**Faz 26 testleri.** `OutboundHttpClientTest` giden HTTP istemcisinin JDK olanı olmadığını ve Boot varsayılanının hâlâ o olduğunu sabitliyor. `ApplicationReadinessTest` altı kural: hazır işçi başvurabilir, kimliği doğrulanmamış başvuramaz, adressiz başvuramaz, IBAN'sız başvuramaz, boş IBAN eksik sayılır, kimlik IBAN'dan önce kontrol edilir. `CancellationPreviewTest` sekiz kural: işçi kesintisiz tam iade görür, işveren pencere içinde kesintisiz, pencere dışında geç iptal oranıyla, iş başladıysa iade yok, zaten iptal edilmiş ve tamamlanmış atama `cancellableNow: false` döner (409 değil), önizleme hiçbir iz bırakmaz, taraf olmayan 404 alır. `AnonymousReviewMappingTest` ve `ReviewRulesTest` anonim değerlendirmeyi sabitliyor: adlı değerlendirme yazarını taşır, anonim taşımaz, liste eşlemesi de gizler, anonim kayıt yazarını veritabanında tutar, puanı ortalamaya girer, ikinci değerlendirme anonim de olsa 409 alır.

**Iyzico protokol testleri.** `IyzicoProtocolTest` on kural sabitliyor — sahte bir HTTP sunucusuna karşı gerçek `RestTemplate` ile: uç yolları dokümandaki değerlerden sapmıyor, sorgulama doğru adrese gidiyor, her istekte `x-iyzi-rnd` var, başlıktaki rastgele anahtar imzalanan anahtarla aynı, imza `randomKey + yol + gövde` üzerinden üretiliyor, reddedilen ödeme exception değil sonuç döndürüyor, işlem kimliği ve tutar `itemTransactions[0]`'dan okunuyor, `status: failure` gövdesi 502'ye çevriliyor, 400 ile dönen hata gövdesi yine de ayrıştırılıyor, iade ondalık tutarla gidiyor. `PaymentCallbackTest` beş yeni kural kazandı: reddedilen ödeme emanet açmaz, dolandırıcılık reddi düşer, inceleme `IN_REVIEW`'da bekler, eksik tahsilat reddedilir, farklı para birimi reddedilir. `PayoutReleaseTest` iade yolunu sabitliyor: tam iade karta döner ve disapprove gerektirmez, kısmi iade her ikisini de kuyruğa alır.

**Ödeme hardening testleri.** `IyzicoProtocolTest` initialize/retrieve response signature sıralarını, trusted checkout URL'sini ve item snapshot alanlarını gerçek HTTP adapter'ında sabitler. `PaymentCallbackTest` belirsiz provider sonucunun aktif kaldığını, bütün snapshot eşleşmeden escrow açılmadığını, bounded retry tavanını ve sonradan başarılı mutabakatı kapsar. `ActivePaymentAttemptConstraintIT` gerçek Postgres'te iki eşzamanlı rezervasyon ve structured constraint→409 yolunu çalıştırır; ayrıca aktif durum/provider token indekslerini okur. `PaymentSessionContractTest` provider token'ın public DTO'ya dönmemesini korur.

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

**Gerçek zamanlı güvenlik testleri (§2ai).** `StompErrorFrameTest` iki kural: reddedilen abonelik soketi kapatan `ERROR` karesiyle değil, kendi aboneliğine adreslenmiş bir `MESSAGE` karesiyle yanıtlanır; başarısız `CONNECT` iç detay değil katalog metni döndürür ve token'ı hiçbir zaman yankılamaz. `StompConnectionRateLimitTest` üç kural: abonelik seli bütçe dolunca reddedilir, mesaj seli de öyle, ve bir bağlantı başka bir bağlantının bütçesini harcayamaz. `CorsContractTest` bir kural kazandı: **gerçek zamanlı handshake REST CORS allowlist'ine tabi değildir** — bu kural olmadan boş allowlist bütün tarayıcı bağlantılarını 403 ile kesiyordu.

`RealtimeHandshakeIT` bunları gerçek bir soket üzerinde `WebSocketStompClient` ile doğruluyor ve `webEnvironment = RANDOM_PORT` kullandığı için **`@Transactional` olamaz** — sunucu ayrı bir iş parçacığında kendi transaction'ında okuyor, geri alınan bir test transaction'ındaki kullanıcı/oturum satırlarını göremez. Satırlar her testte benzersiz e-posta ve cihaz kimliğiyle yazılıyor.

**Faz 18 testleri.** `AggregateBoundaryTest` (manager→DAO sınırı, gerekçeli muafiyetler), `EagerFetchCoverageIT` (liste sorgularında lazy eşlikçi zorunluluğu). `LogPrivacyTest` genişletildi: artık yalnızca getter'ları değil, **değişken/parametre üzerinden** geçen kişisel veriyi de yakalıyor — yazıldığı anda iki gerçek sızıntı buldu (`JavaMailSenderManager` ham e-posta, `PhoneVerificationManager` ham telefon).

**Denetim kaydı testleri (§2av).** `AuditScopeTest` on dört kural sabitliyor: her değişiklik yazılır, reddedilen istek yazılır, filtresiz liste yüklemesi yazılmaz, filtreli arama yazılır, tek kayıt okuması yazılır, hassas okuma filtresiz de yazılır, sayfalama parametresi filtre değildir, boş parametre filtre değildir, GET mutating izin altında bile değiştirmez, `sensitivity` izinlerin en yükseğidir, kaynak türü `/admin` altındaki koleksiyondur, kayıt id'si yolun ilk değişkenidir, hedefsiz istek null yerine boş string verir. `AdminAuditRetentionSchedulerTest` dört kural: her koşuda iki pencere de uygulanır, kapalıyken hiçbir şey silinmez, patlayan koşu zamanlayıcıdan kaçmaz, varsayılan süreler 24/120/6 aydır.

`AdminAuditTrailIT` on dokuz kural — gerçek HTTP üzerinden: izin verilen değişiklik aktör/izin/eylem/kayıt ile yazılır, detay gövde-IP-istemciyi taşır, `cancellation.late-fee-rate` artık iz bırakmadan değişmez, reddedilen istek `allowed = false` yazar, filtreli kullanıcı araması yazılır, filtresiz yükleme yazılmaz, sayfa çevirmek arama değildir, tek kayıt okuması yazılır, finansal satır 10 yıl normal satır 2 yıl saklanır, anonim istek tabloyu doldurmaz, köprü admini de denetlenir, denetim kaydını okumak yazılır ama kendi satırını döndürmez, ikinci okuma tek satır ekler, uç `audit.read` istiyor ve reddi de yazılıyor, çekirdek alanların hepsi dönüyor, detay silinince çekirdek dönmeye devam ediyor, beş süzgecin hepsi sunucuda daraltıyor, aktör süzgeci bir kişinin yaptıklarını buluyor, tanınmayan sıralama alanı 400 dönüyor.

`AdminAuditStorageIT` bölünmeyi gerçek Postgres'te doğruluyor (çekirdek detay silinince kalır, cascade ters yönde çalışır, iki yarı kendi tarihini taşır); `AdminAuditRetentionIT` saklama ve KVKK silmesini (10 yıl / 24 ay / 6 ay damgaları, süresi dolan detay çekirdeği bırakır, süresi dolan çekirdek detayı götürür, penceredeki satıra dokunulmaz, silme talebi yalnız o kişinin detayını siler, ikinci silme zararsız). KVKK tetiği `LegalConsentTest` içinde: kabul edilen talep silme çağırır, engellenen talep hiçbir şey silmez.

**Personel girişi testleri (§2au §Personel girişi).** `StaffLoginIT` on üç kural sabitliyor — gerçek HTTP ve gerçek Postgres üzerinden: personel e-posta+parola ile girip **kendi izinleriyle** `/admin/**` kullanıyor (izni olmayan uçtan 403 alıyor), **yanlış parola ile olmayan hesap ayırt edilemiyor** (zarf birebir aynı, `errorCode` `STAFF_LOGIN_FAILED`), devre dışı hesap da ayırt edilemiyor, beş yanlış deneme hesabı kilitliyor ve doğru parola da çalışmıyor, **kilit yanıtta da iz bırakmıyor**, başarılı giriş sayacı sıfırlıyor, çıkış kendi token'ını öldürüyor, yenileme token'ı yeni erişim token'ı veriyor ve çıkıştan sonra ölüyor, ilk girişte parola değiştirilmeden `/admin/**`'ın geri kalanı (ve `/admin/me`) açılmıyor ama değişimden sonra açılıyor, parola değişimi bütün token'ları öldürüyor, mevcut parola hâlâ soruluyor, kısa parola alan adıyla 400 alıyor, anonim çıkış 401.

`StaffAuthenticationIT` on üç zincir kuralı: izinli token geçer, izinsiz 403, izinler token'dan değil Koala'dan gelir, **üyelik silinince bir sonraki istek reddedilir**, süresi dolmuş token 401, yenileme token'ı erişim token'ı değildir, **marketplace kullanıcı token'ı hiçbir koşulda `StaffPrincipal` üretemez** (gerçek `JwtService` ile üretilmiş, `sub`'ı gerçek bir personel id'si olan token'la ölçülüyor), personel token'ı marketplace zincirinde işe yaramaz, ilk girişte parola değiştirmesi gereken personel hiçbir şeye ulaşamaz, anonim 401, **marketplace `ROLE_ADMIN` köprüsü kapalı**, sıradan kullanıcı 403, eşleşmeyen `/admin` yolu 403.

`StaffAdministrationIT` on üç yönetim kuralı: `staff.manage` ile personel açılıyor ve "ilk girişte değiştir" bayrağıyla doğuyor, aynı e-posta ikinci kez 409, liste ve detay rol üyeliklerini taşıyor, rol verme/geri alma denetim kaydına önce/sonra değerleriyle düşüyor, ikinci verme yeni satır açmıyor, tanınmayan rol kodu 404, **aktör taşımadığı bir rolü veremiyor**, devre dışı bırakma açık token'ları öldürüyor, yönetici parola atayınca hesap ilk girişe dönüyor, `staff.manage` taşımayan personel yedi ucun hepsinden 403 alıyor ve reddi denetim kaydına düşüyor, tanınmayan sıralama alanı 400, liste parola özetini taşımıyor.

`StaffMembershipRulesIT` altı üyelik kuralı — tabloyu boşaltıp ölçüyor: tek sahip kendi üyeliğini bırakamıyor ve reddedilen yazım geri alınıyor, ikinci bir sahip varken de bırakamıyor, başkasının üyeliğini alabiliyor, son etkin yönetici devre dışı bırakılamıyor, sondan bir önceki bırakılabiliyor ama sonuncusu bırakılamıyor, ve `role.manage`'in tek sahibini devre dışı bırakmaya çalışan `staff.manage` sahibi `STAFF_LAST_ADMINISTRATOR` alıyor.

`StaffBootstrapIT` beş bootstrap kuralı: boş tabloda ilk `OWNER` iki değişkenle doğuyor (BCrypt parola, `OWNER` üyeliği, "ilk girişte değiştir" bayrağı), denetim kaydına `staff.bootstrap` satırı düşüyor, **dolu tabloda hiçbir şey yazılmıyor, atlanan bootstrap kaç personel gördüğünü söylüyor ve var olan parola ezilmiyor** (denetim satırı da açılmıyor), ikinci bootstrap hiçbir şeyi değiştirmiyor, doğan hesap gerçekten giriş yapabiliyor. `StaffBootstrapRunnerTest` altı kural: iki değişken de yoksa servis hiç çağrılmıyor, biri eksikse de, boşluk "verilmemiş" sayılıyor, ikisi birlikte tam bir kez çağırıyor ve e-postayı `trim` ediyor, patlayan bootstrap açılışı düşürmüyor.

`RateLimitBucketTest` üç kural kazandı: `/admin/auth/login`, `/refresh` ve `/password` `AUTH` kovasını paylaşıyor, `/admin/staff` yazması ise `WRITE` kovasında kalıyor.

**Rol→izin yüzeyi testleri (§2au).** `StaffRoleAdministrationIT` on beş kural sabitliyor — gerçek HTTP ve gerçek Postgres üzerinden: liste her rolü kendi izinleri ve doğrudan kapsamalarıyla döndürür, detay `TRUST_LEAD → MODERATOR + DISPUTE_HANDLER → SUPPORT_AGENT` zincirini geçişli çözer, panelden verilen izin bir sonraki okumada görünür, geri alınan izin kaybolur, döngü yaratan kapsama **409** ile reddedilir, kaldırılan kapsama artık izin taşımaz, **aktör kendi ulaştığı rolden `role.manage`'i alamaz** (grafikte başka sahip kalsa bile) ama ulaşmadığı bir rolden alabilir, `role.manage` taşımayan personel **yedi ucun hepsinden 403** alır, sahip olmadığı izni role ekleyemez ve satır hiç açılmaz, izin kataloğu her kodu `mutating`/`sensitivity` ile verir. Son dördü denetim izini sabitliyor: verilen ve geri alınan iznin kodu, eklenen ve kaldırılan kenarın rol kodu denetim satırının önce/sonra değerlerinde görünür — ikisi de yalnız yolda geçtiği için başka hiçbir yerde yazmıyordu. `StaffAuthorizationIT` servis katmanındaki aynı kuralları `UUID` anahtarıyla korumaya devam ediyor.

**Görev ayrılığı testleri (§2au §Görev ayrılığı).** `StaffRoleGraphSeedTest` seed'i tutmaya devam ediyor ve dal
tanımını artık `StaffSeparationOfDuties`'ten okuyor; dokuzuncu testi **seed'deki her rolün** trust dalına, finans
dalına ya da bilinçli olarak hiçbirine atandığını iki yönlü doğruluyor — sınıflandırılmamış bir rol seed'e
eklendiğinde de, çalışma zamanı tanımı listeyle ayrıştığında da kırmızıya dönüyor.
`StaffSeparationOfDutiesIT` **çalışma zamanını** ölçüyor, gerçek HTTP ve gerçek Postgres üzerinden on üç kural:
trust dalını kapsayan bir role finans dalı eklenemez ve kenar yazılmaz, finans dalına bir ara rol üzerinden
geçişli ulaşan kapsama da reddedilir, **kenarın ebeveyni temiz olsa da atası iki dalı kapsayacaksa kenar
reddedilir**, **rol grafiği tek başına temiz kalsa da o kenar bir personeli iki dala ulaştıracaksa reddedilir**,
zaten var olan bir kenarın yeniden POST edilmesi **200** döner, **kuraldan önce yazılmış aykırı bir kenarın
yeniden POST edilmesi de 200 döner** (idempotanlık), aynı daldaki iki rol birleşmeye devam eder, **`OWNER` her iki
dalı kapsamaya devam eder**, trust rolü taşıyan personele finans rolü verilemez ve üyelik yazılmaz, ters sırada da
verilemez, tek dal içindeki ikinci rol verilebilir, **`OWNER` üyeliği verilebilir**. On üçüncü kural aynı role bir
finans kapsaması eklenirken o rolü trust üyeliği olan personele veren iki gerçek HTTP transaction'ını aynı anda
başlatır; ikisinin ortak advisory kilitte beklediğini, kilit bırakılınca yalnız birinin 200 diğerinin 409 aldığını
ve ne rol kapanışının ne personelin iki dala birden ulaşmadığını doğrular.

**Mutasyon kanıtı beş ayrı katmanda alındı** — saf yardımcıyı bozmak, onu kullanan bağlantının korunduğunu
kanıtlamaz. Her turda **yalnız** beklenen testler düştü: ata kümesi bugünkü hâline (`Set.of(parent.getId())`)
indirildiğinde yalnız ata testi (200 döndü, 409 bekleniyordu), `includeRole`'daki guard çağrısı silindiğinde dört
kapsama testi (üyelik testleri yeşil kaldı), üyelik yansıması çağrısı silindiğinde yalnız o test,
`grantRole`'daki guard çağrısı silindiğinde yalnız iki üyelik testi, `OWNER` istisnası silindiğinde yalnız iki
`OWNER` testi. Beş turda da `StaffRoleGraphSeedTest` **yeşil kaldı** — issue'nun teşhisinin kanıtı: seed testi
çalışma zamanı deliğini hiçbir zaman göremezdi. Ayrıca mesaj anahtarı yalnız `messages_tr`'den silindiğinde
`MessageCatalogTest`, seed'e sınıflandırılmamış bir rol (`FRAUD_REVIEWER`) eklendiğinde `StaffRoleGraphSeedTest`
kırmızıya döndü.

**Davet kanalı testleri (§2s3).** `CompanyInvitationDeliveryTest` beş kural: davet edilen kişinin akışına `COMPANY_CONTACT_INVITATION` hedefi ve davetin kimliğiyle bir kayıt düşer, akış kaydı yazılamazsa ne davet ne e-posta kaybolur, davet e-postasının konusu dolu, kabul ve ret makbuzlarının ikisi de kendi konusunu taşır. `CompanyInvitationDeliveryIT` iki kural — uçtan uca: davet edilen kişi `GET /users/me/notifications` yanıtındaki `targetId` ile `GET /contact-invitations/{id}` çağırıp davetine ulaşır, ve hem e-posta hem akış kaydı `notifications.subject` sütununu dolu bırakır.

**Yetkili daveti testleri (§2s2).** `CompanyContactInvitationRulesTest` yedi kural: şirket yöneticisi listeyi okur, üye olmayan okuyamaz, davet edilen/daveti gönderen/şirket yöneticisi tekil daveti okur, UUID'yi elinde tutan yabancı okuyamaz, yabancı davetin durumuna hiç bakılmadan reddedilir. `CompanyInvitationSendingRulesTest` beş kural: ilk davet gider, zaten yetkili olan kişi 409, bekleyen davet varken ikinci davet 409, cevaplanmış davet yeni daveti engellemez, yönetici olmayan davet edilecek kişinin durumuna hiç bakılmadan reddedilir. `CompanyContactInvitationAccessIT` sekiz kural — gerçek filtre zinciri üzerinden: yalnız `ROLE_USER` taşıyan şirket yöneticisi listeye 200 ile ulaşır, `STAFF` yetkilisi ve üye olmayan listeyi var olmayan bir şirketten ayırt edemez, üç taraf da daveti okur, yabancı ve `STAFF` daveti rastgele bir UUID'den ayırt edemez, başkasının davetini cevaplamak var olmayan daveti cevaplamakla aynı yanıtı verir, şirket yöneticisi daveti okuyabilir ama cevaplayamaz. Ayırt edilemezlik durum koduyla değil `errorCode` ile sabitleniyor.
**Var olma gizlemesi testleri (§2d2).** `ExistenceConcealmentIT` dokuz uçta tek bir kuralı sabitliyor: yabancının var olmayan bir kaynağa aldığı yanıt ile başkasına ait gerçek bir kaynağa aldığı yanıt, istemcinin gönderdiği id yer tutucuya çevrildikten ve `timeStamp`/`requestId` düşüldükten sonra **birebir aynı** — konuşma mesajları, iptal, iptal kaydı, iptal önizlemesi, puantaj, check-in, tamamlama, değerlendirme ve iş şablonu. `ExistenceConcealmentRulesTest` aynı kuralı kaynak kodda tarıyor, dört kural: hiçbir manager metodu iki farklı 404 anahtarı döndürmez, `Concealment.notFound`'a verilen her anahtar `NOT_FOUND` ile biter, gizlenen yanıt tek yerde ve log satırıyla birlikte üretilir, muafiyet listesi bayatlamaz. İkisinin de yakalayamadıkları §2d2'de yazılı.

**Şikayet inceleme testleri (§2p).** `ReportModerationIT` on sekiz kural sabitliyor — gerçek Postgres üzerinde: susuz kuyruk her durumu döndürür, durum verilince daralır, şikayet edileni olmayan satır susuz kuyruktan düşmez, gerekçe süzgeci daraltır, dört hedef tipi birbirinden ayrılır, aynı kişi hakkındaki şikayetler durumdan bağımsız gruplanır, satır şikayet edeni ve şikayet edileni adlandırır, kararı veren yalnız karardan sonra dolar, geçmiş sayısı kişinin tüm kayıtlarını sayar, **sayfa boyutu bire indiğinde sayı yine iki gelir** (sayfa kapsamlı olamaz), sonuçlanmış şikayet de geçmişe sayılır, ilan şikayetinin detayı başlığı, mesaj şikayetininki gövdeyi, görsel şikayetininki URL'i taşır, detay kuyruktaki kimlik ve sayıyı tekrarlar, bilinmeyen şikayetin detayı yok, ve **personel kimliğiyle gelen moderatör şikayeti karara bağlayabilir** (yanıt kuyruk satırının tamamı, `reviewedBy` boş). `ReportRulesTest` iki kural kazandı: personel kararı `reviewedBy` yazmaz, köprü kararı yazar — ikincisi olmadan düzeltme sessizce köprü yolunu da kapatabilirdi. `ReportModerationPrivacyTest` yüzeyin taşıdığı alan adlarını geçişli tarayıp e-posta/telefon/IBAN/TCKN taşımadığını sabitliyor ve kırmızıya dönebildiğini sentetik bir sızıntı DTO'suyla gösteriyor.

**`ApiConventionTest` yüzeyi yapısal olarak koruyor.** Kaynak kodu tarayıp §0b'deki altı kuralı doğruluyor: admin kuralı `/admin` dışında bir yol gösteremez, aynı fiil+yol iki handler'da olamaz, yol sondaki eğik çizgi taşıyamaz, DTO adı HTTP fiili içeremez, 32 karakteri aşamaz, `request`/`response` dışında duramaz. Yeni bir uç eklerken bedava koruma.

**Tarayıcının kendisi de test ediliyor (#72).** ADMIN kuralını bulan tarama `adminOnlyPaths(String)` olarak ayrıldı ve üç vaka onu sentetik yapılandırma metniyle besliyor: `hasRole("ADMIN")` görülüyor, tek rolü ADMIN olan `hasAnyRole("ADMIN")` görülüyor, çok rollü `hasAnyRole("USER", "ADMIN")` görülmüyor. Sebep: desen sessizce daralırsa `SecurityConfig` o an temiz olduğu için **hiçbir test kırmızıya dönmez** — koruma çalışmayı bırakır ve yeşil kalır. Kırmızıya dönebildiği gösterilmemiş bir koruma doğrulanmamıştır.

### Yapısal korumaların kör noktaları — #72 taraması

Bu depodaki yapısal korumaların hepsi kaynağı **düz metin** tarıyor, yani her biri yalnızca tanıdığı yazımı koruyor; tanımadığı yazım geriye korunduğu sanılan bir alan bırakır ve bu, hiç koruma olmamasından kötüdür. #72 kapsamında altısı aynı gözle tarandı. **Aşağıdakilerin hiçbiri bugün canlı bir güvenlik ya da veri hatası değil** — mekanizma olarak duruyorlar; düzeltmeleri ayrı issue'lara ait.

| Koruma | Görmediği yazım (taramada, #88 öncesi) | #88 sonrası |
|---|---|---|
| `MessageCatalogTest` | yalnız **kullanılan → var**; öksüz `device.not.found.by.user.and.user.agent` | **var → kullanılıyor** yönü `unusedKeysAreReportedAndOnlyTheDocumentedOnesRemain`; öksüz anahtar silindi; kalan dördü allowlist'te (silinmedi) |
| `MessageCatalogTest` | varsayılan `messages.properties` yarım ve denetlenmiyordu | **kaldırıldı** (seçenek b). `AcceptHeaderLocaleResolver` zaten `tr` varsayılan; üçüncü dil eklenince `messages_xx` yazılır, yarım ROOT katalog açılmaz |
| `MessageCatalogTest` | `KEY` deseni yalnız tam literal `"{anahtar}"` | aynı — bugün her anahtar literal; kapatılamadı çünkü birleştirmeli anahtar yok, sentetik vaka üretmek üretimi değiştirirdi |
| `LogPrivacyTest` | `LOG_CALL` yalnız `log.` alıcısı | `accessLog`/`logger`/`LOGGER` de taranıyor; tanınmayan `Logger` alan adı testi kırar; `"..." + email` birleştirmesi argüman |
| `PageableSortSafetyTest` | `)\s*\{` `throws`'u kaçırır; 900 karakterlik gövde sonraki metoda taşar | imza `throws` kabul eder; gövde eşleşen suslu parantez; iki meta-vaka |
| `SoftDeleteCoverageTest` | `theExclusionListStaysHonest` yalnız `HARD_DELETE_BY_DESIGN` | üç grup da denetleniyor; `CompanyContact` muafiyetten çıktı; bayat `NOT_AUDITED` sentetik vakası kırmızıya döner |
| `AggregateBoundaryTest` | yalnız `\w+Dao`; `EntityManager` persist görünmez | `\w+(Dao\|Repository)`; `flush`/`clear` hariç `entityManager.*(` sınır ihlali; iki meta-vaka |
| `EagerFetchCoverageIT` | yalnız grafı **olan** metotlar | `CollectionQueryWithoutGraphTest` grafı olmayan koleksiyon sorgularını allowlist'e kilitler; IT'nin lazy-companion kuralı aynı |

Öksüz mesaj anahtarları **raporlanıyor, silinmiyor** — allowlist `MessageCatalogTest.DOCUMENTED_UNUSED_KEYS`. `device.not.found.by.user.and.user.agent` bunun istisnası: çağıranı yoktu, yerini `{device.not.found}` almıştı, silindi.

**Varsayılan katalog kararı (b).** `messages.properties` ROOT düşümüydü ve tr/en'in yarısıydı. Senkron tutmak (a) her anahtarı üç dosyaya yazdırırdı; yarım bırakmak üçüncü dilde sessizce anahtara düşerdi. Dosya silindi, varsayılan locale zaten `tr` (`MultiLanguageSupportConfig`).

**Faz 11A/11B testleri.** `AutoCompletionRulesTest` (vadesi geleni tamamlar ve emaneti çıkarır, açık uyuşmazlıkta durur, emanette para yoksa dokunmaz, ikinci koşu zararsız, bir atamadaki hata partiyi düşürmez) ve `SettlementRetryTest` (kuyruğa alma idempotan, geri çekilme üstel ve tavanlı, deneme limitinde durur, başarıda `Payout` `PROCESSING`'e geçer).

**Faz 12 testleri.** `BadgeRulesTest` rozet monotonluğunu (kazanılan geri alınmaz), idempotansı, eşik konfigüre değilken hiçbir şey verilmediğini ve seed satırı eksikken çökmediğini sabitliyor. İş tamamlama yetkisi `AssignmentOperationsAuthorizationTest` içinde.

**Faz 8B/8D testleri.** `MyWorkRulesTest` (oturum sahipliği ve kendi oturumunu koruma), `CompanyAdministrationRulesTest` (şirket yetkisi, son admin, onay), `ProfileEndpointRulesTest` (eğitim sahipliği, kaydedilen ilan), `UserPatchRulesTest` (telefon doğrulama akışı), `UserProfileContractTest` (`/users/me` alanları), `JobRequestPrimitiveContractTest` (eksik primitive alanlar). `JobCategorySeedIT` kaldırıldı: seed gittikten sonra beş kategoriyi sabitlemek yanlış gerçeği koruyordu; yerine `LiquibaseMigrationIT.theJobCategorySeedIsGone` boşluğu, `JobCategoryAdministrationRulesTest` (on bir kural) ve `JobCategoryAdministrationIT` (üç kural) yönetim yüzeyini sabitliyor.

**`JobRequestPrimitiveContractTest` yazıldığında derlenmiyordu** — `getExperienceRequired()` yoktu çünkü alan primitive `boolean`'dı. Bu, `isEmployer` tuzağının aynısıydı: `@NotNull` primitive üzerinde hiçbir şey yapmaz, eksik alan sessizce `false` olur. `experienceRequired`, `insuranceRequired`, `minAge`, `maxAge` sarmalayıcı tipe çevrildi ve `salary` `@NotNull` kazandı; `maxAge < minAge` da artık 400. #179 kalan yazma primitive'lerini (`gpa`, galeri `cover`/`sortOrder`, rozet `sortOrder`) aynı şekilde sardı; `addressPublic` bilinçli `false` varsayılanıyla duruyor. Envanter `RequestDtoPrimitiveContractTest`.

**`MapperCharacterizationTest` eşleme davranışını sabitliyor.** Spring context'siz, üretilen `…MapperImpl` sınıflarını doğrudan kurar. MapStruct geçişinden önce yazıldı ve iki testi kırmızıydı; geçiş ikisini de yeşile çevirdi.

**`ErrorContractIT` HTTP sözleşmesini koruyor.** Yetkisiz erişimin 403, eksik Authorization başlığının 401 döndüğünü doğrular. İkisi de eskiden 500 dönüyordu — biri yanlış `AccessDeniedException` import'u yüzünden, diğeri null başlıkta `substring(7)` çağrıldığı için.

**Başvuru geri çekme testleri.** `ApplicationWithdrawalTest` on iki kural sabitliyor (§2ar2): sahibi `APPLIED` başvurusunu geri çeker, başkasınınki **404**, bilinmeyen kimlik 404, `ACCEPTED` ve `REJECTED` **409**, iki kez geri çekme 409, kayıt silinmez ve `delete` hiç çağrılmaz, işverene bildirim gider, geri çekilmiş başvuru kabul de red de **edilemez**, `WITHDRAWN` yeniden başvuruyu engellemez ama `APPLIED` engeller (mesajlaşma kapısını tutan on üçüncü kural #257 ile kapının kendisiyle birlikte düştü). `ApplicationWithdrawalIT` aynı davranışı gerçek Postgres'te dört kuralla doğruluyor: satır tabloda kalır ve `is_deleted` `false`'tur, `APPLIED` süzgecinden düşüp `WITHDRAWN` süzgecinde görünür, işverenin listesinde durur, mükerrer başvuru kapısı yalnızca canlı başvuruda kapanır.

**`EntityGraphPathsIT` neden var.** `@EntityGraph` içindeki `attributePaths` derleyicinin denetlemediği düz metin; yanlış bir yol ancak o sorgu çalıştırıldığında patlıyor. `JobAssignmentDao` içinde `job.creator.addresses` yazıyordu, oysa `User.address` tekil — `PATCH /job-assignments/{id}/status` her çağrıda çöküyordu ve kimse fark etmemişti. Test tüm DAO'ları tarayıp her yolu JPA metamodel'ine karşı doğruluyor. Yeni bir entity graph eklerken bu test bedava koruma sağlıyor.

**Bildirim tercihi testleri (§2at).** `NotificationPreferenceRulesTest` on altı kural: işlemsel kategori satır ne derse desin açık kalır, **yetkili daveti kapatılmış `COMPANY_UPDATES` satırına rağmen gider** (issue #52), alarm kullanıcı kapatana kadar açık, ticari kullanıcı açana kadar kapalı, bir kanalı kapatmak diğerlerine dokunmaz, alıcısı bilinmeyen gönderim susturulmaz, karar yazılır ve çözümlenmiş görünüme yansır, işlemsel satırlar `editable=false` gelir, **`COMPANY_UPDATES` görünümde anahtar sunmaz**, tercih taşımayan kanal görünümde hiç yok, aynı kanala ikinci karar yeni satır açmaz, ticari izin an/kanal/IP ile kaydedilir, izin çekilişi de kaydedilir, ticari olmayan kategori rıza kaydı bırakmaz, çıkış bağlantısı yalnız kendi kanalını kapatır, çıkış bağlantısı oturum istemez. Son iki kural PR #48'in getirdiği regresyonu koruyor: ikisi de yazıldığında kırmızıydı. `NotificationUnsubscribeRulesTest` altı kural: bağlantı token'ı taşır ve veritabanı yalnız özeti, satır hangi kanalı kapatacağını bilir, tanınmayan token reddedilir, süresi dolmuş bağlantı çalışmaz, canlı bağlantı hedefine çözümlenir, aynı bağlantıya ikinci tık zararsız. `NotificationSuppressionTest` on bir kural gönderim kapısını sabitliyor: kapatılan kanal ne kuyruğa girer ne kayıt açar, e-postayı kapatmak push'u susturmaz, ödeme bildirimi tercihe rağmen gider, hesabı olmayan adrese giden e-posta susturulmaz, ticari e-posta çıkış bağlantısı taşır, işlemsel e-posta taşımaz, yetkili daveti kuyruğa girer ve çıkış bağlantısı taşımaz, iş alarmı taşımaz, bağlantı bindiği e-postanın kategori ve kanalını adlandırır, bastırılan e-posta token bile istemez.

`WorkerToolsRulesTest` bir kural kazandı: eşleşen iş alarmı `NotificationService`'e `JOB_ALERTS` kategorisiyle push olarak veriliyor — yani üretimdeki alarm yolu da kapıdan geçiyor.

**`NotificationPreferenceIT` kabul kriterlerini gerçek Postgres üzerinde kanıtlıyor.** Sekiz kural: iş alarmı e-postasını kapatan kullanıcı gerçekten almıyor (kapatmayan alıyor), kapatma yalnız o kanalı etkiliyor, işlemsel kategoriyi kapatma denemesi 400 dönüyor ve o bildirim yine gidiyor, **e-postanın içindeki çıkış bağlantısı giriş yapmadan çalışıyor ve yalnız kendi kanalını kapatıyor**, uydurma token reddediliyor, ticari izin an/kanal/IP ile iz bırakıyor, ticari ileti kullanıcı istemeden gitmiyor, tercih yüzeyi anonime kapalı.

## 4. Bilinen tuzaklar

- **`ddl-auto=validate`.** Şema yalnızca Liquibase ile değişir. Entity'ye alan eklemek tek başına yetmez, changelog yazılmadan uygulama açılmaz. Bu bilinçli bir karar — bozmayın.
- **`Job.duties` alanı `location` kolonuna eşlenmiş.** Kod "görevler" derken veritabanı "konum" diyor. Rapor yazan ya da elle SQL çalıştıran herkes yanılır.
- **Uç yolları `/api` öneki taşımaz.** API kendi alt alanında (`api.iskoala.com`) servis ediliyor; `api.iskoala.com/api/jobs` kekeleme olur. Eski `/api` önekinden kalma tek referans `EmailConsumer`'daki takip pikseliydi, düşürüldü.
- **Uç yolları sondaki eğik çizgi taşımaz.** Spring Boot 3'ten beri `/companies/` ile `/companies` aynı şey değil. Bir mapping'e eğik çizgi eklersen istemciler 404 alır.
- **Eşleşmeyen yol 404, desteklenmeyen metot 405 döner.** İkisinin de handler'ı var; yoksa `Exception` yakalayıcısına düşüp 500 dönüyorlardı.
- **Eksik ya da tip uyumsuz query parametresi 400 döner.** `MissingServletRequestParameterException` handler'ı yoksa 500 dönüyordu.
- **Gövdesi okunamayan istek 400 döner.** `HttpMessageNotReadableException` handler'ı `GlobalExceptionHandler`'da; eskiden bozuk JSON 500 döndürüyor ve ayrıştırma hatasının metnini istemciye yansıtıyordu. Jackson ayrıştırma mesajı ham gövdedeki token'ı taşıyabildiği için exception/cause metni loglanmaz; yalnızca sabit hata tipi/kodu ve `requestId` yazılır.
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

**Normal test context'leri `.env`'den yalıtılmış.** `src/test/resources/application.properties` içindeki
`spring.config.import=` satırı bu bağlantıyı keser. `ConfigDataApplicationContextInitializer` ile gerçek
`application.yaml` zincirini bilinçli olarak açan dar yapılandırma testleri ise ölçtükleri dış girdileri kendi
`ApplicationContextRunner` fixture'ında ayrıca sabitler; geliştirici makinesindeki değerleri varsayılan saymaz.

**Bekleyen iş:** eski `application.properties` içindeki anahtarlar (Gmail parolası, JWT secret, R2 ve Iyzico anahtarları, SMS anahtarları) bir süre imajda taşındı. Hepsi rotate edilmeli.

## 6. Deployment ve sürümleme

Doğrulama ve GHCR imajı Yusuf'un Mac'inde. GitHub Actions `./mvnw verify`
koşmaz ve imaj basmaz. `v*` etikette (veya `workflow_dispatch`) tek iş org
self-hosted runner üzerinde Dokploy webhook'udur. Sıra
`docs/runbooks/self-hosted-runner.md`.

1. **verify** — `./mvnw verify -Dkoala.test.latch-timeout=30` (Docker, Temurin 25).
2. **image** — `scripts/publish-ghcr-image.sh --latest` → `ghcr.io/is-koala/koala:<pom>` ve `:latest`, tek `linux/amd64` manifest (`provenance`/`sbom` kapalı).
3. **deploy** — annotated `v*` etiket push'u GitHub'dan webhook atar. İmaj GHCR'de yokken etiket basılmaz.

Sürümleme: `v1.2.3` tag'i Dokploy'un çektiği `:latest` ile aynı digest'i varsayar. `main` push imaj basmaz. Maven `pom.xml` sürümü git tag'iyle aynı semver'dir (`0.9.26` ↔ `v0.9.26`); `build-info` ve `info.app.version` / ECS `SERVICE_VERSION` (boşsa) buradan okunur. Dokploy'da `SERVICE_VERSION` hâlâ `0.0.1` ise o satır imaj varsayılanını ezer — boşaltın veya tag'le eşleyin.

Uygulama root olmayan `koala` kullanıcısıyla, JRE tabanlı imajda çalışıyor. Sağlık kontrolü `/actuator/health/readiness` üzerinden; `/actuator/health/**` herkese açık, kalan actuator uçları ADMIN rolü istiyor.

## 7. Yol haritası

Denetim bulguları `K-01`…`K-42` kodlarıyla numaralandırılmış; commit mesajlarında ve bu dosyada o kodlarla anılıyorlar. Ayrıntılı plan ve bulgu listesi iki artefakt belgesinde tutuluyor.

**Tamamlananlar:** Faz 0 (test zemini), 1 (environment + CI/CD), 2 (güvenlik blokerleri), 3 (Spring Boot 4), 3B (MapStruct), 4 (mesaj katmanı), 5 (kod kalitesi), 5B (UserPrincipal), 6 (loglama + zaman + hız sınırı), 7 (ödeme), 7B (payout/refund/bank account), 8 (API yüzeyi — kısmi), 8C (istemci düzeltmeleri), 9 (yasal — kısmi), 9C (soft delete), 10 (mesajlaşma + değerlendirme + engelleme), 10B (iptal + uyuşmazlık + şikayet + timesheet), 8D (istemci profil yüzeyi + kategori seed), 8B (kalan API yüzeyi), 12 (rozetler + iş tamamlama), 11A (otomatik tamamlama), 11B (ödeme dayanıklılığı), 15 (API yüzeyi tutarlılığı), 12B (rozet yönetimi), 13 (WebSocket), 16 (çalışma zamanı ayarları), 17 (depolama → S3), 11C (olgunlaşma), 18 (mimari denetim), 19 (hata sözleşmesi + doğrulama + güvenlik), 19B (hata zarfı zenginleştirme), 21 (medya galerisi), 22 (birleşik işlerim ucu), 23 (şirket takibi + işveren istatistikleri), 24 (API dokümantasyonu + e-posta şablonları), 25 (prod kurulumu).

**Sıradakiler:**

| Faz | Kapsam | Neye bağlı |
|---|---|---|
| **20** | **Referans veri olgunlaştırma.** Ayrıntı aşağıda | ürün + veri |
| **7C** | **Iyzico kalanları.** Dolandırıcılık webhook tüketicisi olmadan `IN_REVIEW` artık bounded retrieve ile çözülüyor; kalan dış kabul işi gerçek sandbox'ta ödeme→emanet→çıkış ve kısmi iade + disapprove sıralamasının teyidi | sandbox erişimi |
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
| `pendingVerifications[]` artık `message` taşımıyor, yerine `expiresAt` (ISO-8601, `Z`) var — doğrulama kodu sayacı bu ana bağlanmalı | §2m |
| Başvuru ekranı: kimlik/adres/IBAN eksikse "Başvur" kapalı olmalı, uç 400 döndürüyor | §2ar |
| IBAN sahipliği doğrulaması kodda durur ama **şimdilik kapı değil** (#127): ödeme/emanet birincil IBAN'ın varlığına bakar. 1,01 TL akışı çok sonraya. | §2k2 / #127 |
| `BankAccountDto.verificationStatus` artık gerçek bir kapı: `UNVERIFIED`/`PENDING`/`VERIFIED`/`EXPIRED` | §2k2 |
| Bireysel ilan açma ekranı: kimlik doğrulanmamışsa kapalı olmalı (`JOB_INDIVIDUAL_EMPLOYER_IDENTITY_NOT_VERIFIED`) | §2aq |
| Kimlik doğrulamada `sod` + `dg1` (base64) gönderilmeli — bayrak açıldığında zorunlu olacak | §2aq2 |
| `dg11` gönderilirse ad ulusal karakterlerle çipten alınır, gönderilmezse istekteki ad korunur | §2aq2 |
| Çip doğrulaması: `POST /users/me/identity-verification/challenge` → çipe imzalat → `dg15` + `challenge` + `activeAuthenticationSignature` | §2aq2 |
| Gönderilen `dg15` artık SOD'a karşı doğrulanıyor: çipten okunan ham DG15 dosyası aynen gönderilmeli — SOD'un imzalamadığı bir anahtar `IDENTITY_CHIP_KEY_UNSIGNED`, tutmayan hash `IDENTITY_DATA_GROUP_TAMPERED` alır | §2aq2 |
| `dg14` (base64, isteğe bağlı, ham DG14 dosyası) gönderilirse Active Authentication algoritması oradaki `ActiveAuthenticationInfo`'dan seçilir ve DG14 SOD'a karşı doğrulanır: SOD listelemiyorsa `IDENTITY_SECURITY_INFO_UNSIGNED`, tutmayan hash `IDENTITY_DATA_GROUP_TAMPERED`. DG14 yoksa sunucu ECDSA SHA-1/224/256/384/512 × (düz, DER) ve RSA ISO 9796-2 yelpazesini dener; TCKK DG14 taşımıyor (is-koala/koala#158) | §2aq2 / #253 |
| `UserMeResponseDto.tcIdentityNumber` → `maskedTcIdentityNumber`, değer artık `***********` | §2aq |
| İlan listesinde `applicationCount` var, ilan başına istek atma | §2ap |
| İlan silme değil `POST /jobs/{id}/cancel` | kayıt korunur, başvuranlara bildirim gider (§2ap) |
| Aday kartında `JobApplicantDto.jobTitle` ve `cvUrl` | §2ap |
| `CompanyDto.approved` (`isApproved` değil) | `CompanyMembershipDto` ile aynı ad |
| Şirket logosu `PUT/DELETE /companies/{id}/logo` | §2ap |
| Davet gövdesinden `tcIdentityNumber` **kalktı**: `POST /companies/{id}/contact-invitations` artık yalnız `{email, role}` alıyor | §2s |
| Aday profili `GET /users/{userId}` | giriş ister, tam adres dönmez, `cvUrl` ilişkiye bağlı (§2ap) |
| Bireysel ilanda sigorta alanı **olmayacak** | ürün kuralı, §2ap |
| İlan formuna isteğe bağlı `headcount`, kartta `hiredCount`/`headcount` göstergesi; kontenjan dolu ilanda kabul artık 409 (`JOB_APPLICATION_JOB_HEADCOUNT_FULL`) | §2ao2 — sunucu kontenjanı zorluyor, gösterge yine istemcide |
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
- `sitemap_manifests` ve `sitemap_manifest_entries` — **bilerek hard delete.** Bunlar denetlenen domain kaydı
  değil, TTL'li türetilmiş cache'tir; manifestteki `created_at` yayın/version zamanıdır. Expiry temizliği
  manifesti fiziksel siler, entry'ler cascade olur. Soft delete bounded-storage garantisini bozardı (§2l).
- `LoginAttempt` — **yarı istisna, ve ayrımı karıştırma.** Entity `@SQLDelete` + `@SQLRestriction` taşımaya devam ediyor: `loginAttemptDao.deleteById(...)` hâlâ soft delete ve satır tabloda kalıyor. **Ama saklama işi fiziksel siliyor** — `LoginAttemptDao.deleteUnattributedBefore` / `deleteAttributedBefore` native SQL, çünkü Hibernate `@SQLRestriction`'ı bulk JPQL delete'e de uyguluyor ve soft delete edilmiş bir satır saklama süresini hiçbir zaman dolduramazdı; adresi süresiz taşımaya devam ederdi. Gerekçesi denetim tablolarınınkiyle aynı, farkı bu tablonun `BaseEntity`'yi bırakamaması. Tam anlatım §2aw'de. Bu yüzden `SoftDeleteBehaviourIT`'in soft delete örneği artık `login_attempts` değil `universities`: aynı tabloyu hem "fiziksel olarak silinmemeliydi" örneği hem "saklama işi fiziksel siler" örneği olarak kullanmak iki belgeyi çelişkiye sokuyordu.
- `EmailVerification`, `PhoneVerification`, `IdentityVerification` tek tabloyu (`user_verifications`) paylaşıyor; `@SQLDelete` kök sınıf `UserVerification` üzerinde, alt sınıflarda ayrıca yok. Aynı durum `Media` hiyerarşisi için de geçerli.

**Benzersizlik kısmi indekslerle çözüldü.** `uk_users_email_active` ve `uk_bank_accounts_iban_active` yalnızca `is_deleted = false` satırları kapsıyor — hesabını silen kullanıcı aynı e-postayla yeniden kaydolabiliyor.

**Mutabakat silinmiş kayda ulaşabilir.** `@SQLRestriction` yalnızca JPA sorgularını etkiliyor; native SQL tüm satırları görüyor. Admin ve mutabakat sorguları bu yolu kullanır.

**Depodaki nesneler artık silinmiyor.** `ImageManager.deleteImage` eskiden satırı ve R2 nesnesini birlikte siliyordu — geri dönüşsüz. Şimdi yalnızca satır soft delete ediliyor, nesne duruyor. **Kalıcı politika Faz 9B'de:** kimlik belgesi fotoğrafları özel nitelikli kişisel veri, profil fotoğrafı ve logo saf kişisel veri, işe/ödemeye bağlı belgeler saklama yükümlülüğü altında — kategori bazlı saklama süresi ve temizlik işi orada tanımlanacak.

**`SoftDeleteCoverageTest` bunu yapısal olarak koruyor.** Yeni bir entity eklenip `@SQLDelete` unutulursa test kırmızıya döner; bilinçli bir istisnaysa testteki listeye yazılır. `SoftDeleteBehaviourIT` ise davranışı gerçek Postgres'te doğrular: satır tabloda kalıyor, JPA sorgusundan düşüyor, native SQL hâlâ görüyor.

## 9. Prod kurulumu (Dokploy)

**Üç ayrı Dokploy servisi**, repodaki `docker-compose.yml` değil. Compose'daki `nginx-network` Dokploy'un ağı değil ve `koala-app` servisi imajı VPS'te build ediyor; CI zaten GHCR'ye basıyor. Compose yerel geliştirme için duruyor.

**Compose bellek sınırları yalnızca yerel geliştirmeyi korur.** `KOALA_APP_MEM` (varsayılan `1200m`), `KOALA_DB_MEM` (`768m`) ve `KOALA_RABBITMQ_MEM` (`384m`) yerel servislerin host belleğini tüketmesini sınırlar; `RABBITMQ_MEM_WATERMARK` (`0.6`) RabbitMQ akış kontrolü eşiğidir. Bu değişkenler `.env.example` içinde belgelenir. Production bu compose dosyasını kullanmadığı için aynı sınırlar Dokploy'daki üç servise ayrıca verilmeden canlı ortam korunmuş sayılmaz.

| Servis | Tip | Kaynak |
|---|---|---|
| `koala-db` | Database → PostgreSQL | `postgres:17` |
| `koala-rabbitmq` | Application → Docker | `rabbitmq:4.1.3-management` |
| `koala-app` | Application → Docker | `ghcr.io/is-koala/koala:latest` |

**Postgres tuzağı — yaşandı.** Resmi imaj `POSTGRES_DB`'yi yalnızca veri dizini boşken oluşturur. Servis bir kez farklı bir adla ayağa kalktıysa sonradan adı değiştirmek env değişkenini değiştirir ama **volume'un içinde o veritabanını yaratmaz**; Dokploy'un bağlantı kutusu doğru adı gösterir, uygulama `FATAL: database ... does not exist` alır. Çözüm ya eksik veritabanını elle yaratmak ya volume'u silip yeniden ilkletmek.

**RabbitMQ node adı sabitlenmeli.** Dokploy uygulamaları Swarm üzerinde çalışıyor ve konteyner hostname'i her deploy'da değişiyor; RabbitMQ verisini `/var/lib/rabbitmq/mnesia/rabbit@<hostname>` altında tuttuğu için boş node olarak açılır. `RABBITMQ_NODENAME=rabbit@localhost` bunu kapatır. Topoloji zaten `RabbitMQConfig` bean'lerinden yeniden kurulur; kaybolabilecek tek şey kuyrukta bekleyen teslim edilmemiş mesajlardır.

**Delayed-message eklentisi hiç gerekmiyordu.** `rabbitmq.Dockerfile` `rabbitmq_delayed_message_exchange` kuruyordu ama `RabbitMQConfig` yalnızca `TopicExchange` ve durable kuyruk tanımlıyor; kaynak kodun hiçbir yerinde `x-delayed-message` yok. Dosya silindi, compose resmi imaja bağlandı.

**Açılış 84 saniye sürüyor** — Yauaa user-agent kuralları tek başına ~22 saniye. `Dockerfile` healthcheck `start-period` bu yüzden 150 saniye; 60 saniyelik pencerede konteyner hazır olmadan `unhealthy` işaretlenip döngüye girebiliyordu.

**Portlar dışarı açılmaz.** Ne 5432 ne 5672 ne 15672. Yönetim arayüzüne SSH tüneliyle bakılır.

**Sürümleme etiketle.** Mac `ghcr.io/is-koala/koala:1.2.3` ve `:latest` basar; `v1.2.3` push'u Dokploy webhook'unu tetikler. `main` push imaj basmaz.

**İlk production geçişi deploy değildir.** `docs/runbooks/launch-cutover.md`, mevcut
pre-production kaynaklarını temiz production'a çeviren bir defalık insan yordamıdır;
`ops/launch/launch-cutover-wizard.sh` dokuz geri dönüş kapısını sıraya koyup yalnız
kaynak/rol takma adlarını, kararları, UTC zamanlarını ve kanıt kimliklerini git
dışındaki `.env.launch-cutover` dosyasına yazar. Script hiçbir platform, database,
kuyruk, bucket veya sağlayıcı
değişikliği çalıştırmaz ve secret istemez. Reset'in CI/deploy'a bağlanmaması,
gerçek para yazıldıktan sonra eski snapshot'a kör dönüşün yasak olması ve SMTP ile
iyzico geçişlerinin ayrı onay taşıması bu yüzden yapısal seçimdir. Dar smoke
sonrasında ilk çalışma HOLD ile biter; 24 saatlik gözlem yalnız
`--close-observation` yoluyla, reset/deploy adımları tekrar gösterilmeden kapanır.

**`/api` öneki yok ve olmayacak.** API kendi alt alanında servis ediliyor. **URL sürümlemesi (`/v1`) de bilinçli olarak eklenmedi:** tek first-party istemci için iki API sürümünü paralel bakımda tutmak gerçekçi değil. Bunun yerine **asgari istemci sürümü kapısı** var (Faz 28, §2as). API dışarı açılırsa (B2B entegrasyon) karar değişir ve `/v1` zorunlu hâle gelir.

## Agent skills

### Issue tracker

İşler ve spesifikasyonlar `is-koala/koala-backend` GitHub Issues üzerinde takip edilir. Ayrıntılar için `docs/agents/issue-tracker.md`.

### Triage labels

Beş varsayılan triage etiketi kullanılır. Ayrıntılar için `docs/agents/triage-labels.md`.

### Domain docs

Tek-bağlam düzeni kullanılır: kökte `CONTEXT.md`, kararlar `docs/adr/` altında tutulur. Ayrıntılar için `docs/agents/domain.md`.
