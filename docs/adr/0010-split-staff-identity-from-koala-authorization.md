# Personel kimliğini Keycloak'a, yetkilendirmeyi Koala'da tut

Durum: **[ADR-0014](0014-keep-staff-identity-in-koala.md) ile yerine geçildi — 6 Eylül 2026.** Keycloak kullanılmıyor; personel kimliği de Koala'da tutuluyor. Aşağıdaki *yetkilendirme* kararları (rol→izin tablosu, kapsama grafiği, izinlerin token'a gömülmemesi, personelin marketplace kullanıcılarından ayrı olması) hâlâ geçerli ve ADR-0014 tarafından aynen devralındı; değişen yalnız kimliğin ve rol üyeliğinin nerede durduğu. Bu dosya kayıt için bırakıldı, uygulanacak karar değil.

Kabul edildi — 5 Eylül 2026. Geçiş bölümü eklendi — 6 Eylül 2026: `/admin/**`'ın hangi token'ı kabul ettiği bu ADR'de, "Geçiş" başlığı altında yazılıdır.

## Karar

Back-office personeli marketplace kullanıcılarından tamamen ayrıdır. İki yönde de yabancı anahtar kurulmaz; bir personel kaydı bir `users` satırına bağlanmaz.

**Kimlik ve rol üyeliği Keycloak'ta** (`koala-staff` realm'i): parola, MFA, kilitleme, oturum, parola sıfırlama ve "bu kişi hangi role sahip" bilgisi. Personel Keycloak admin konsolundan yönetilir; panelde personel ekleme veya rol atama ekranı yoktur.

**Yetkilendirme Koala'da**: rolün hangi izinleri içerdiği, roller arası kapsama grafiği ve domain kuralları. İzinler access token'a **gömülmez**; her istekte Koala'nın veritabanından çözülür.

Marketplace kullanıcıları Keycloak'a **taşınmaz**.

## Bağlam

Panel ciro, kişisel veri, şirket onayı ve escrow'dan para çıkışı yönetecek. Bugün bunların hepsi tek bir düz `ROLE_ADMIN` kapısının arkasında: escrow'dan işçiye para salmakla rozet ikonu yüklemek aynı yetki seviyesinde. `Role` bir Java enum'u olduğu için yeni bir rol eklemek kod değişikliği ve deploy gerektiriyor, ve `authorities` tablosunun birincil anahtarı bile yok.

Dört bağımsız mimari üretilip her biri güvenlik, işletilebilirlik ve geri dönülebilirlik merceklerinden değerlendirildi. Dördü de bağımsız olarak aynı üç şeyde birleşti: personel kendi tablolarında yaşamalı, `/admin/**` kendi filtre zincirini ve kendi principal tipini taşımalı, ve izinler token'a konmamalı.

Kimlik tarafında Keycloak seçildi çünkü MFA — kararın gereği olarak ikinci personelden önce zorunlu — kendi elle yazılırsa 1-2 hafta sürer ve IdP'ye geçildiğinde tamamen atılır. Ekip Keycloak'ı zaten biliyor, dolayısıyla öğrenme maliyeti yok.

Rol üyeliğinin de Keycloak'ta tutulması, panelde personel yönetimi ekranlarını tamamen ortadan kaldırdığı için seçildi.

## Neden yetkilendirme Koala'da kalıyor

- **Token'daki izin bayatlar.** Bir yetki geri alındığında token süresi dolana kadar geçerli kalır. Para taşıyan izinlerde kabul edilemez; alternatifi her istekte Keycloak'a introspection çağrısıdır ve bu yerel bir okumadan yavaştır.
- **Kuralların yarısı Koala verisine bakar.** "Taraf olduğu uyuşmazlığı çözemez", "kendi şirketini onaylayamaz", "iade yalnız fiyat snapshot'ındaki tutar kadar" — Keycloak bu veriyi görmez. Domain farkındalıklı yetkilendirme zaten Koala'da olmak zorundadır; geri kalanı da orada tutmak yetkilendirmeyi tek yerde toplar.
- **Denetim kaydı.** Yetki kararını veren interceptor denetim satırını da yazar. Karar Keycloak'ta verilirse Koala'da loglanacak bir karar anı kalmaz.

## Sonuçlar

- Rol atama Koala'nın denetim kaydına girmez; Keycloak'ın kendi log'unda kalır. Rol iptali token yenilenene kadar gecikir — access token ömrü kısa tutularak (~5 dk) yönetilir.
- Panel Keycloak'a bağımlıdır: Keycloak düşerse panele hiç girilemez. Kurtarma planı bu yüzden hem ikinci bir mühürlü hesabı hem Keycloak admin parolasını hem de yazılı bir psql runbook'unu kapsar.
- `staff.manage` ile `role.manage` ayrı izinlerdir. Aynı olsaydı, destek elemanı işe alan kişi kendi rolüne `finance.read` ekleyebilirdi. Ayrıca sahip olunmayan bir izin bir role konamaz ve bu her rol-grafiği yazımında yeniden denetlenir.
- `PayoutService` metotlarına yetki anotasyonu **konmaz**: bunları otomatik tamamlama zamanlayıcısı boş bir SecurityContext ile, kullanıcı iptalleri ve uyuşmazlık çözümü çağırıyor. Personel tarafı ince bir facade üzerinden geçer.
- Roller tek ebeveynli ağaç değil **DAG**'dır: bir rolün iki farklı rolü birden kapsaması gerekiyor.
- Denetim kaydı ikiye bölünür: değişmez çekirdek (24 ay, finansal olanlar 10 yıl) ve silinebilir detay (6 ay ya da KVKK talebi). Bu ayrım ilk satır yazılmadan yapılmalıdır; sonrası yasal süre baskısı altında veri migrasyonudur.
- İzin sabitleri `mutating` ve `sensitivity` metadata'sı taşır; yetki kararını veren nokta denetim satırını da yazdığı için loglamayı unutmak mümkün değildir.
- Personel marketplace hesabı tutabilir, ancak bağ beyan edilir ve panel çıkar çatışmasını engeller.
- Geçiş maliyeti: yetkilendirme Koala'da kaldığı için IdP değiştirmek authenticator'ı değiştirmekten ibarettir.

## Geçiş: `/admin/**` hangi kimliği kabul eder

Bu bölüm `/admin/**`'ın hangi token'ı kabul ettiğinin **tek kaynağıdır**. `CLAUDE.md` §0b, koala-backend#14, koala-backend#35 ve koala-admin#4, #5 buraya bağlanır; cevabı kopyalamazlar.

**Cevap: geçiş süresince ikisi birden, ama süreli.** Tek seferde geçiş değil; kalıcı çift kapı da değil. Üç aşama, her birinin çıkış koşulu yazılı:

| Aşama | `/admin/**` kabul eder | Başlangıç koşulu |
|---|---|---|
| **0 — bugün** | Yalnız marketplace `ROLE_ADMIN` (`SecurityConfig`'teki `.requestMatchers("/admin/**").hasRole("ADMIN")`) | Şu an geçerli |
| **1 — köprü** | Keycloak `koala-staff` token'ı **ve** marketplace `ROLE_ADMIN` | `koala-staff` realm'i yayında **ve** #14'ün ikinci zinciri indi |
| **2 — kesim** | Yalnız Keycloak `koala-staff` token'ı | Aşağıdaki üç koşul birlikte sağlandı |

Aşama 0 bugünkü gerçek: `auth.iskoala.com` **çözülmüyor**, `koala-staff` realm'i kurulmadı. Keycloak'ın bugün ayakta olduğunu varsayan her plan yanlıştır. Aşama 0 boyunca panelin tek çalışan kimliği marketplace oturumudur ve `ROLE_ADMIN`'i yönetilebilir kılan iş koala-backend#35'tir.

### Neden tek seferde geçilmiyor

- **Lansman `/admin/**`'a bağlı.** ADR-0009 gereği kategori seed'i yok; katalog yalnız `POST /admin/job-categories` ile kurulabiliyor ve kategori ilanın zorunlu alanı. Tek seferde geçiş, `/admin/**`'ı Keycloak kurulana kadar tamamen kapatmak demektir — yani lansmanı Keycloak kurulumuna, #14'e ve panelin iki dilimine **aynı anda** bağlamak.
- **Keycloak kapısı açıldığı anda arkası boştur.** Token geçerli olsa bile `staff_members` satırı yoksa panel 403 alır (koala-admin#5). O ilk satırı ve rol grafiğini yazacak ekran panelde **yok** — ADR-0010 gereği personel yönetimi Keycloak konsolunda, rol→izin grafiği backend'de. Keycloak kapısı **kimlik doğrular ama henüz hiçbir şeyi yetkilendirmez**. Eski kapı aynı anda kapanırsa `/admin/**`'a hiç kimse giremez ve sistem kendi kendini kurtaramaz.
- **Big-bang geri alınamaz.** Tek seferde geçiş, iki repoda dört issue'nun aynı anda deploy edilmesini şart koşar. Köprü aşaması her birinin ayrı ayrı inmesine ve ayrı ayrı geri alınmasına izin verir.

### Neden çift kapı kalıcı değil

- **İki kapının zayıfı sistemin seviyesini belirler.** Marketplace kapısında MFA yok, izin DAG'ı yok, denetim aktörü personel değil kullanıcı. `payout.release` bu kapının arkasında kalırsa ADR-0010'un tüm gerekçesi kâğıt üstünde kalır: escrow'dan para salmak yine düz bir `ROLE_ADMIN` ile mümkün olur.
- **Kurtarma planı zaten yazıldı ve içinde bu kapı yok.** Bu ADR kurtarmayı ikinci bir mühürlü Keycloak hesabı, Keycloak admin parolası ve yazılı bir psql runbook'u olarak tanımladı. Hiç kullanılmayan ikinci bir kimlik doğrulama yolu acil durumda da çalışmaz — tatbikat yapılmayan acil durum kapısı, acil durum kapısı değil sürekli açık bir kapıdır.

### Marketplace `ROLE_ADMIN` yolunun ömrü

**Geçici köprü.** Kalıcı acil durum kapısı değil. Üç koşul birlikte sağlandığında `SecurityConfig`'teki `/admin/**` → `hasRole("ADMIN")` satırı **tek commit'te silinir**:

1. `staff_members`'ta, Keycloak ile giriş yapabilen, `OWNER` rolünde en az bir kayıt var.
2. Panelin kategori ekranı bu kimlikle uçtan uca çalıştı (koala-admin#1 dilim 1 kabulü).
3. İkinci mühürlü Keycloak hesabı açıldı ve psql runbook'u yazıldı.

**Silinmeyen şey:** `Role.ROLE_ADMIN` enum sabiti ve marketplace kurallarındaki `hasAnyRole("USER", "ADMIN")` kalır — `ImageManager` ve `CvManager` bu rolü domain kararı için okuyor. Ölen şey `ROLE_ADMIN`'in **`/admin/**` anahtarı olması**.

koala-backend#35'in açtığı üç uç (`/admin/users/{userId}/roles`) kesimden sonra da kalır: marketplace rollerini yönetmek meşru bir personel işidir, yalnızca artık personel kimliğiyle ve personel izniyle korunur.

`/actuator/**` bu kararın **kapsamı dışındadır** — operasyon yüzeyi, personel yüzeyi değil. Kuralı olduğu yerde kalır; kesimden sonra `ROLE_ADMIN`'in `/admin` dışında kalan tek kapısı odur ve ayrıca ele alınması gerekir.

### Mekanizma: iki zincir, issuer resolver'ı değil

Köprü, `/admin/**` için #14'ün açtığı **ikinci `SecurityFilterChain`** üzerinde kurulur. O zincir geçiş penceresinde iki kimlik doğrulayıcıyı birden taşır — Keycloak resource server ve mevcut `SessionAuthFilter` — ve yetki kuralı "gerekli izne sahip `StaffPrincipal` **ya da** `ROLE_ADMIN`" olur. Kesim, o zincirden `SessionAuthFilter`'ı ve `ROLE_ADMIN` alternatifini silmekten ibarettir: tek dosya, tek commit, geri alınabilir.

**`AuthenticationManagerResolver` bu ayrımı yapamaz ve yapmamalı.** Marketplace token'ı bir OAuth2 bearer JWT değil: `iss` claim'i (`iskoala.com`) hiç doğrulanmıyor, geçerliliği imzaya değil `sessions` satırına ve cihaz parmak izine bağlı (`SessionAuthFilter`). Bunu bir `JwtDecoder`'a sığdırmak, oturum ve cihaz kontrolünü kimlik doğrulamadan koparmak olurdu. Resolver'ın gerçek işi **Keycloak issuer'ları arasındadır** — realm/ortam ayrımı ve ileride IdP değişimi; `JwtIssuerAuthenticationManagerResolver.fromTrustedIssuers(...)` bir izin listesidir, `iss`'e körü körüne güvenen bir çözüm token uydurmaya açıktır.

**Resolver bugün yazılmadı, #14'ün ikinci zinciriyle birlikte inecek.** Sebep: `spring-boot-starter-oauth2-resource-server` bağımlılığı projede yok, `auth.iskoala.com` çözülmediği için JWKS çekilemiyor, ve claim'lerin dönüştürüleceği `StaffPrincipal` tipi henüz yok. Bugün yazılsa hiçbir test onu çalıştıramazdı; çalıştırılmayan güvenlik kodu güvenlik değil dekordur.

### Bugün yapılan tek hazırlık

`SessionAuthFilter` bir `@Component` olduğu için Spring Boot onu servlet konteynerine de kaydediyordu, üstelik `@Order` taşımadığı için güvenlik zincirinden **sonra**. Bugün zararsız: `OncePerRequestFilter` aynı istekte ikinci kez gövdesini çalıştırmıyor. #14 `/admin/**` için `SessionAuthFilter` içermeyen ayrı bir zincir açtığı anda zararsızlık biter — konteyner kaydı yetkilendirmeden sonra çalışır, Keycloak token'ında `extractSessionId` patlar ve **staff zinciri isteği kabul etmiş olsa bile istek marketplace kimlik hatasıyla reddedilir**. Hata mesajı sebebi söylemez.

Bu yüzden konteyner kaydı kapatıldı: filtre yalnızca onu açıkça ekleyen `SecurityFilterChain`'in üyesidir. Davranış bugün değişmiyor; `SessionAuthFilterRegistrationIT` bunu sabitliyor.

### Personel aktörü servis katmanına nasıl ulaşır (#16)

- **Servis katmanı aktörü `SecurityContext`'ten okumaz; aktör parametre olarak açıkça geçirilir.** Aynı metotları otomatik tamamlama zamanlayıcısı boş SecurityContext ile, kullanıcı iptalleri kullanıcı kimliğiyle, personel ise `/admin/**` üzerinden çağırıyor — context'ten okuyan bir servisin üç farklı gerçekliği olur.
- `SecurityService` marketplace tarafında kalır ve personel principal'ini öğrenmez. `/admin/**` handler'ları `getAuthenticatedUser()` çağırmaz.
- `Dispute.resolvedBy` / `Report.reviewedBy` yanına nullable `resolved_by_staff_id` / `reviewed_by_staff_id` gelir (#16); ikisinden **tam olarak biri** dolu olur. İki sütunun bir arada bulunmasının sebebi köprü aşamasıdır: o pencerede marketplace `ROLE_ADMIN` ile çözülen bir uyuşmazlık `User` sütununu doldurmaya devam eder. Bu yüzden ikisi de NOT NULL olamaz.
- Para taşıyan metotlara yetki anotasyonu konmaz; personel `StaffPayoutFacade` üzerinden geçer (#14, #16). Facade yetkiyi kontrol eder, aktörü alır, sonra korumasız iç metodu çağırır.
