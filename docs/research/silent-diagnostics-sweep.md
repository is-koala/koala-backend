# Sessiz teşhis taraması — bulgular (05.09.2026)

**Durum: HİÇBİRİ UYGULANMADI.** Bu dosya yalnızca bir kayıttır; kod tabanında bu bulgular
nedeniyle tek satır değişmedi. Kullanıcı düzeltmeleri sonraki bir oturuma bıraktı.

## Tarama neydi

`AggregateBoundaryTest.aManagerOnlyReachesForItsOwnAggregatesRepository` içinde bir hata
düzeltildi: hata mesajı `"...%s..." + "...".formatted(manager, dao)` şeklinde kuruluyordu ve
Java'da metot çağrısı `+` operatöründen sıkı bağladığı için `.formatted` yalnızca ikinci
literale uygulanıyordu. Birinci literaldeki `%s`'ler hiç doldurulmuyordu, yani test kırıldığında
ihlal eden manager ve DAO'nun adı basılmıyordu. Düzeltme: birleşik string parantez içine alındı.

Bunun üzerine aynı **aileden** kusurlar arandı: *bir şeyler zaten bozulduğunda devreye giren,
ama kendisi de bozuk olan teşhis mekanizmaları*. Hiçbir geçen test bunları yakalamaz, çünkü
yalnızca bir şey patladığında render edilirler.

Beş mercek koştu (SLF4J arity, format-string arity/öncelik, yapısal testlerin hata mesajları,
ateşlenemeyen guard'lar, hata yüzeyi). 19 aday çıktı, her biri iki karşıt mercekle doğrulandı
(biri dosyayı açıp iddianın harfiyen doğru olduğunu kontrol etti, diğeri "gerçek kusur mu, üslup
meselesi mi" diye eledi); ikisi de onaylamayan düştü. 12 onay, 7 eleme, 11 ayrı sorun.

## Doğrulama seviyesi — okurken buna dikkat et

- **[ELDE DOĞRULANDI]** = kaynak kod bu oturumda doğrudan okundu, iddia birebir teyit edildi.
- **[AJAN ONAYLI]** = iki bağımsız doğrulayıcı onayladı ama dosya elle açılmadı.
  Uygulamadan önce teyit et.

---

## 1. Şirket yetkilisi kendi davetlerini listeleyemiyor [ELDE DOĞRULANDI]

`src/main/java/com/exskylab/koala/core/configs/SecurityConfig.java:58`

```java
.requestMatchers(HttpMethod.POST, "/companies/{companyId}/contact-invitations").hasAnyRole("USER", "ADMIN")
.requestMatchers(HttpMethod.GET,  "/companies/{companyId}/contact-invitations").hasAnyRole("ADMIN")
```

GET kuralında `"USER"` düşmüş. Şirket yetkilisi davet **gönderebiliyor** ama **listeleyemiyor**;
platform ADMIN'i değilse Spring Security 403 veriyor.

Asıl yetki kontrolü zaten doğru yerde duruyor —
`CompanyContactInvitationManager.getCompanyContactInvitationsByCompanyIdDto` (satır 158-167)
`companyContactService.isUserAdminOfCompany` ile bakıp `ForbiddenException` atıyor. Yani
SecurityConfig kuralı hem yanlış hem gereksiz: doğru kontrole hiç sıra gelmiyor.

**Önerilen düzeltme:** `hasAnyRole("ADMIN")` → `hasAnyRole("USER", "ADMIN")`.
**Risk:** Güvenlik yüzeyi. Tek kelimelik değişiklik ama gözden geçirilmeli — manager'daki
şirket-admin kontrolünün gerçekten her yolu kapattığı teyit edilmeli.

## 2. ApiConventionTest'in kör noktası [ELDE DOĞRULANDI]

`src/test/java/com/exskylab/koala/webAPI/ApiConventionTest.java:24-25`

```java
private static final Pattern ADMIN_RULE =
        Pattern.compile("\\.requestMatchers\\(([^)]*)\\)\\s*\\.hasRole\\(\"ADMIN\"\\)");
```

`everyAdminOnlyRuleLivesUnderTheAdminPrefix` (satır 75-86) yalnızca `.hasRole("ADMIN")` formunu
tanıyor. SecurityConfig'te bu formda **tam iki kural** var: satır 74 `/admin/**` ve satır 216
`/actuator/**`. İkisi de `startsWith("/admin") || startsWith("/actuator")` koşulunu zaten
sağlıyor, yani döngü hiçbir zaman anlamlı bir şey doğrulamıyor.

Tek rolü ADMIN olan `hasAnyRole("ADMIN")` kuralları taramanın tümüyle dışında. Bugün bu formda
iki kural var ve ikisi de CLAUDE.md §0b'nin "yönetim işlemleri `/admin/**` altında, istisnasız"
kuralını çiğniyor:

- satır 58 — `GET /companies/{companyId}/contact-invitations` (yukarıdaki 1 numaralı hata)
- satır 77 — `POST /images`

Yani §0b'nin "test bunu koruyor" iddiası şu an fiilen geçersiz.

**Önerilen düzeltme:** Regex'i `\.has(?:Any)?Role\(` ve tek-ADMIN argüman listesi tanıyacak
şekilde genişlet.
**Risk:** Test düzeltilince **kırmızıya döner** — çünkü 1 ve `POST /images` gerçek ihlaller.
`POST /images` için karar gerekiyor: `/admin/images` altına mı taşınsın (istemci sözleşmesi
değişir), yoksa gerekçeli bir muafiyet listesi mi yazılsın. Bu yüzden 1 ile birlikte ele alınmalı.

## 3. Ham mesaj anahtarı istemciye sızıyor [ELDE DOĞRULANDI]

`src/main/java/com/exskylab/koala/core/dtos/userVerification/response/VerificationInfoDto.java:34,38`

```java
this.message = VerificationMessages.EMAIL_VERIFICATION_MESSAGE;  // "{email.verification.message}"
```

`PATCH /users/me` yanıtında `pendingVerifications[].message` alanı literal
`{email.verification.message}` / `{phone.verification.message}` döndürüyor.
`MessageResolvingAdvice` yalnızca zarfın üst seviye `Result.message` alanını çözüyor, iç içe DTO
alanlarına dokunmuyor.

İkinci ve daha önemli sorun: anahtarın katalogdaki karşılığı
`messages_tr.properties:180` → `E-posta doğrulama kodunuz: {0}`. Bu bir **e-posta/SMS gövde
şablonu**, API yanıt alanı değil. Çözülse bile `{0}` yerinde kalırdı ve yanıt "doğrulama
kodunuz: {0}" gibi anlamsız bir metin taşırdı. Yani alan iki kere yanlış.

**Önerilen düzeltme:** Ürün kararı gerekiyor — alan tümüyle kaldırılsın mı (istemci `type` ve
`sentTo`'dan zaten kendi metnini kurabilir), yoksa yanıt için ayrı ve doğru bir anahtar mı
tanımlansın?
**Risk:** `PATCH /users/me` yanıt sözleşmesi değişir. CLAUDE.md §2m `pendingVerifications`'ı
sözleşmenin parçası olarak belgeliyor.

## 4. Teşhis kalitesi — davranış değişmiyor

Bu grubun hiçbiri iş mantığına, imzaya veya HTTP koduna dokunmuyor; yalnızca log ve mesaj metni.

| Yer | Sorun | Seviye |
|---|---|---|
| `core/consumers/EmailConsumer.java:53` **[ELDE DOĞRULANDI]** | Tek `{}`, iki argüman. Sondaki `e.getMessage()` bir `String` (Throwable değil), bu yüzden SLF4J sessizce atıyor — hata sebebi hiç loglanmıyor. Aynı dosyada satır 97 doğru formu kullanıyor (`"... id: {}, Error: {}"`), yani niyet açık. Hafifletici: `e` satır 55'te `AmqpRejectAndDontRequeueException`'a cause olarak veriliyor | yüksek |
| `business/concretes/UserManager.java:509` **[ELDE DOĞRULANDI]** | `"...AddressId: {}, UserId: "` iki argüman alıyor ama `UserId:` etiketinin placeholder'ı yok; satır "UserId: " diye boş bitiyor ve `currentUser.getId()` düşüyor. Komşu satırlar (506, 514) zaten userId bastığı için etkisi küçük | düşük |
| `core/consumers/PushConsumer.java:37` [AJAN ONAYLI] | `catch (Exception e)` her hatayı yakalıyor, log ise "Notification not found" diye tek bir sebebi iddia ediyor; `e` bağlanıp hiç kullanılmıyor. DB kopsa da aynı yanlış satır basılıyor, stack trace yok, mesaj sessizce ack'leniyor | orta |
| `core/consumers/SmsConsumer.java:48` [AJAN ONAYLI] | Yukarıdakinin birebir aynısı, SMS kuyruğunda | orta |
| `core/utilities/location/GooglePlacesAddressProvider.java:131` [AJAN ONAYLI] | Yakalanan `RuntimeException` hiç kullanılmıyor: loglanmıyor, `ExternalServiceException`'a cause olarak da eklenemiyor (öyle bir constructor yok) ve sınıfta logger bile yok. Sağlayıcı hatasının sebebi tümüyle kayboluyor | orta |
| `core/utilities/location/GoogleGeocodingAddressProvider.java:59` [AJAN ONAYLI] | Ters geocoding adaptöründe aynı kusur | orta |
| `core/helpers/UploadValidator.java:74` [AJAN ONAYLI] | Görsel boyutu okunurken çıkan `IOException` kullanıcıya "Bu dosya türü desteklenmiyor" (400, `STORAGE_UNSUPPORTED_TYPE`) olarak dönüyor ve exception atılıyor. Gerçek bir I/O hatası kalıcı olarak kullanıcı girdi hatası kılığına giriyor | orta |
| `src/test/.../EagerFetchCoverageIT.java:179` [AJAN ONAYLI] | Guard eşiği `count() > 10` ama tek mesajı "liste döndüren entity graph taraması **boş**" diyor ve bulunan sayıyı basmıyor. 1..10 aralığında yanlış bir şey iddia ediyor ve okuyucuya sayı vermiyor. `LogPrivacyTest:66` bunu doğru yapıyor (`+ found`) | düşük |

## 5. Hata sözleşmesi tutarsızlıkları [AJAN ONAYLI]

`business/concretes/ImageManager.java`

- **satır 103** — Yetki reddi `ValidationException` ile atılıyor: `DELETE /images/{imageId}`
  sahibi olmayan biri için **400 + `VALIDATION_FAILED`** dönüyor, ama gövde "Bu görseli silme
  yetkiniz yok" diyor. Zarfın iki yarısı birbiriyle çelişiyor. CLAUDE.md §2ag'ye göre
  `ForbiddenException` (403) olmalı. Repoda `ForbiddenException` kullanmayan tek yetki reddi bu.
- **satır 97** — Olmayan görsel `ValidationException` → 400, ama **aynı sınıfta** `getImageById`
  (satır 51) aynı durumda `ResourceNotFoundException` → 404 dönüyor. Aynı eksik satır, çağrılan
  uca göre iki farklı statü.

**Risk:** İkisi de istemciye görünür. Mobil istemci bu uçlarda 400 bekliyorsa kırılır.

---

## Elenen adaylar

7 aday doğrulamada düştü. İkisi kayda değer:

- **`UserManager.java:509`** üç mercek tarafından bulundu, üç doğrulamada da elendi — ama
  **eleme yanlış**. Kod elde okundu, placeholder gerçekten eksik (yukarıda 4. bölümde duruyor).
  Doğrulayıcılar muhtemelen komşu satırlar userId bastığı için "etkisiz" saydı.
- **`ErrorCodeTest.java:58`** iki mercek bulup iki doğrulama eledi. Kod elde okundu:
  `assertEquals(List.of(...yedi ad...).size(), classes.size(), ...)` — yedi sınıf adı kurulup
  yalnızca `.size()`'ı, yani `7` sabiti karşılaştırmaya giriyor; adların kendisi hiç kullanılmıyor.
  Yani test "yedi **semantik** tip" değil, yalnızca "yedi **dosya**" garantisi veriyor:
  `ForbiddenException` silinip yerine `PaymentNotFoundException` eklense sayı 7 kalır ve test geçer.
  Hata mesajı `classes` listesini bastığı için teşhis edilebilir kalıyor; bu yüzden düşük öncelikli,
  ama testin adı (`theExceptionSurfaceStaysSmallAndSemantic`) verdiğinden fazlasını vaat ediyor.
  `assertEquals(List.of(...), classes.stream().sorted().toList())` daha güçlü olurdu.

## Taramanın kapsamadıkları

- `koala-mobile` hiç taranmadı, yalnızca `koala-backend`.
- Entegrasyon testleri (`*IT.java`) Docker gerektirdiği için çalıştırılmadı; `EagerFetchCoverageIT`
  bulgusu statik okumaya dayanıyor.
- Tarama teşhis/mesaj katmanına odaklandı. İş mantığı, eşzamanlılık, performans ve güvenlik
  açıkları kapsam dışıydı.
