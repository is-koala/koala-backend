# Parola sıfırlama token tasarımı

Tarih: 5 Eylül 2026

## Kısa karar

Koala için önerilen kalıcı tasarım, `User.passwordResetTokenId` alanı değil, ayrı bir `password_reset_tokens` tablosunda yalnız özeti tutulan yüksek entropili **opaque** token'dır. Yeni istek önceki etkin token'ı iptal etmeli; tüketim `token_digest + consumed_at IS NULL + expires_at > now()` koşuluyla tek bir atomik veritabanı işlemi olmalı ve parola değişimiyle aynı transaction içinde tamamlanmalıdır.

Mevcut `passwordResetTokenId` fikri başlı başına güvensiz değildir. Kullanıcıya verilen JWT'nin kendisini değil, JWT içindeki rastgele `jti`/nonce'u tutarak imzalı token'a sunucu taraflı tek-kullanım ve iptal kabiliyeti ekler. Fakat zaten veritabanına gidildiği için JWT'nin stateless avantajı kalmaz; geçici bir güvenlik akışını çekirdek `User` aggregate'ına taşır ve istek zamanı, bitiş zamanı, tüketilme/iptal nedeni ile temizlik politikasını modelleyemez. Bu nedenle savunulabilir bir ara çözüm, fakat Koala için gereksiz bir kısayoldur.

## Sektör tabanı

OWASP, parola sıfırlama tanımlayıcısının kriptografik olarak güvenli ve yeterince uzun üretilmesini, kullanıcıya bağlanmasını, güvenli saklanmasını, süreli ve tek kullanımlık olmasını ister. JWT kullanılabileceğini, fakat ek JWT riskleri getirdiğini de özellikle belirtir. İstek yanıtı kayıtlı/kayıtsız hesapta aynı olmalı, süre farkı hesap keşfine izin vermemeli, istekler sınırlandırılmalı; başarılı sıfırlamadan sonra kullanıcı bilgilendirilmeli ve mevcut oturumlar kapatılmalı veya kullanıcıya bu seçenek verilmelidir. URL güvenilen sabit/izinli hosttan ve HTTPS ile üretilmeli, reset sayfası referrer sızıntısını önlemelidir. [OWASP Forgot Password Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html)

NIST SP 800-63B, e-posta ile verilen recovery kodunun güvenli bağlantı içinde taşınabileceğini, e-posta kodunun en fazla 24 saat geçerli olmasını, denemelerin throttling'e tabi tutulmasını ve recovery olayının bağımsız bildirim üretmesini şart koşar. Koala'nın 15 dakikalık süresi bu üst sınırın içindedir. NIST'in kapsamı genel tüketici uygulamasından daha yüksek güven seviyelerini de içerdiği için yöntem sayısı gereksinimleri Koala'ya doğrudan kopyalanmamalı; tek kullanım, süre, throttling ve bildirim ilkeleri burada anlamlıdır. [NIST SP 800-63B, Account Recovery](https://pages.nist.gov/800-63-4/sp800-63b.html#account-recovery)

Yaygın framework'ler tek bir modeli zorunlu kılmaz:

- Laravel, özel parola-reset token deposu kullanır; yeni token üretirken önceki kaydı siler, token'ı hashleyerek saklar, oluşturulma zamanından süreyi denetler ve süresi dolan kayıtlar için temizlik işlemi sunar. Bu, önerdiğimiz ayrı credential-tablosu deseninin doğrudan örneğidir. [Laravel `DatabaseTokenRepository`](https://github.com/laravel/framework/blob/12.x/src/Illuminate/Auth/Passwords/DatabaseTokenRepository.php)
- Django, ayrı token satırı yerine imzalı/HMAC token'ı kullanıcının parola hash'i, son giriş zamanı, e-postası ve zaman damgasına bağlar. Parola değiştiğinde token kendiliğinden geçersizleşir. [Django `PasswordResetTokenGenerator`](https://github.com/django/django/blob/main/django/contrib/auth/tokens.py)
- Rails de süreli, amaç-özel imzalı token'ı parola salt'ına bağlayarak parola değişiminden sonra geçersiz kılan örnek sunar. [Rails 7.1 `generates_token_for`](https://guides.rubyonrails.org/v7.1/7_1_release_notes.html#add-activerecord-base-generates-token-for)

Dolayısıyla “sektör standardı JWT'yi User'a koymaktır” denemez. Standart olan davranış garantileridir; uygulamada hem ayrı hashli token deposu hem de parola durumuna bağlı imzalı token görülür.

## Seçeneklerin karşılaştırması

| Ölçüt | Opaque token + ayrı tablo | JWT + User üzerinde `jti` | Parola sürümü/hash'ine bağlı imzalı token |
| --- | --- | --- | --- |
| Tek kullanım | Atomik consume ile açık | Mevcut koşullu `UPDATE` ile iyi | Parola güncellemesi token'ları geçersiz kılar; yarış ayrıca kilitlenmelidir |
| Veritabanında sır | Yalnız SHA-256/HMAC özeti; DB sızıntısında bearer yok | JWT yok, yalnız rastgele nonce var; DB sızıntısında bearer yok | Token satırı yok |
| Süre/temizlik | Açık `expires_at`, kolay purge | Süre yalnız JWT'de; süresi dolmuş nonce User'da kalır | Token satırı olmadığı için temizlik yok |
| Yeni isteğin eskisini iptali | İş kuralı olarak açıkça yapılır | User alanının overwrite edilmesiyle olur | Ek nonce/sürüm yoksa tüm linkler ilk parola değişimine kadar geçerlidir |
| Eşzamanlı tüketim | Koşullu tek `UPDATE` ile tam kontrol | Koala'nın koşullu `UPDATE` yaklaşımı doğru | Sadece doğrulama yeterli değil; iki isteğin aynı anda geçmesini ayrıca engellemek gerekir |
| Audit/operasyon | İstek, tüketim, iptal ve süre metadata'sı modellenebilir | Yalnız güncel nonce vardır | Ayrı güvenlik olayı olmadan zayıf |
| Karmaşıklık | Küçük bir entity/repository/service | JWT doğrulaması + DB durumu; iki mekanizma | En az depolama, fakat güvenli yarış ve iptal semantiği daha örtük |

JWT seçilecekse RFC 8725 gereği issuer/subject/audience doğrulanmalı ve access, kayıt ve reset JWT'leri birbirinin yerine kullanılamayacak ayrık doğrulama kurallarına sahip olmalıdır. Koala'nın reset audience kontrolü doğru yönde; aynı issuer/key ile farklı JWT türleri kullandığı için issuer kontrolü ve daha kesin tür ayrımı da açıkça yapılmalıdır. [RFC 8725 §§3.8–3.12](https://www.rfc-editor.org/rfc/rfc8725.html#section-3.8)

## Koala'daki mevcut tasarım

- [`AuthManager`](../../src/main/java/com/exskylab/koala/business/concretes/AuthManager.java) yeni UUID üretip User'a yazıyor, bunu JWT `jti` olarak kullanıyor. Yeni istek eski nonce'u overwrite ettiği için yalnız son bağlantı geçerli kalıyor.
- [`UserDao`](../../src/main/java/com/exskylab/koala/dataAccess/UserDao.java) `userId + tokenId` eşleşmesini tek koşullu `UPDATE` ile `null` yapıyor. Bu, aynı token'la eşzamanlı iki resetten yalnız birinin ilerlemesini sağlayan doğru fikirdir; transaction daha sonra hata verirse tüketim de rollback olur.
- [`JwtService`](../../src/main/java/com/exskylab/koala/core/security/JwtService.java) reset JWT'sine `sub`, `jti`, `iss`, `aud`, `iat`, `exp` koyuyor ve reset audience'ını kontrol ediyor. Ancak issuer doğrulaması görünmüyor.
- [`AuthController`](../../src/main/java/com/exskylab/koala/webAPI/AuthController.java) kayıtlı/kayıtsız adres için aynı 200 sözleşmesini sunuyor ve resetten sonra token/session döndürmüyor; mobilin normal giriş ekranına dönmesi OWASP yaklaşımıyla uyumlu.
- Reset başarılı olduğunda tüm oturumlar kapatılıyor; bu iyi. Buna karşılık başarı bildirimi e-postası görünmüyor.
- [`RateLimitFilter`](../../src/main/java/com/exskylab/koala/core/configs/RateLimitFilter.java) endpoint'i IP bazlı auth bucket'ına alıyor. OWASP'ın hesap başına flood koruması ayrıca yok. Bilinmeyen kullanıcı yolu erken döndüğü için yanıt içeriği aynı olsa bile süre farkı ölçülebilir.
- `catch (RuntimeException)` hesap bulunamadı durumu dışındaki veri/servis hatalarını da “bilinmeyen adres” gibi yutar; yalnız beklenen not-found sonucu bastırılmalıdır.

`UserVerification` bu iş için uygun yeniden kullanım değildir: doğrulanmış aktör, belge ve soft-delete edilen kalıcı doğrulama kaydı modelidir. Parola-reset token'ı ise kimliği henüz doğrulanmamış isteğin kısa ömürlü credential'ıdır. `PendingRegistration` yaşam döngüsü bakımından daha yakın bir örnektir; Koala dokümanında tamamlanmamış kayıtların hard-delete edilmesi bilinçli kabul edilmiştir. Reset token'ı da ayrı, geçici ve hard-delete edilen bir güvenlik kaydı olmalı; gerekli denetim geçmişi token'dan bağımsız bir security-event kaydında tutulmalıdır.

## Önerilen Koala sözleşmesi

`password_reset_tokens`:

- `id UUID`, `user_id UUID FK`
- `token_digest BYTEA/CHAR(64)` ve unique index
- `expires_at`, `consumed_at`, `revoked_at`, `created_at`
- isteğe bağlı güvenli operasyon metadata'sı: maskelenmiş/özetlenmiş istemci bilgisi; ham token, e-posta veya gereksiz kişisel veri yok

Davranış:

1. CSPRNG ile en az 256 bit opaque token üret; yalnız SHA-256 veya anahtarlı HMAC özetini DB'ye yaz, ham değeri yalnız e-posta linkine koy.
2. En son bağlantı politikası kullan: yeni istek aynı kullanıcının etkin token'larını revoke etsin. Bu mevcut davranışı ve Laravel desenini korur.
3. Atomik consume sorgusunda digest, kullanıcı, `consumed_at/revoked_at IS NULL` ve `expires_at > now()` birlikte denetlensin. Consume, parola yazımı ve tüm session'ların kapatılması tek transaction olsun.
4. Her türlü parola değişiminde bütün etkin reset token'ları iptal edilsin. Böylece normal “parolamı değiştir” akışı da eski e-posta linkini öldürür.
5. Başarılı reset sonrası güvenlik bildirimi gönder; istemci otomatik login yapmasın.
6. Kayıtlı/kayıtsız cevap gövdesi ve HTTP kodu aynı kalırken zaman farkı azaltılsın; IP korumasına ek hesap anahtarlı throttling uygulansın. Loglarda ham token bulunmasın.
7. Süresi dolmuş/tüketilmiş credential kayıtları kısa bir operasyonel pencereden sonra hard-delete edilsin; kalıcı denetim gerekiyorsa secretsiz olay kaydı ayrı tutulmalı.

## Migration etkisi

`password_reset_token_id` migration'ı ve ilgili kod şu anda `main` HEAD (`v0.5.3`) üzerinde değil, çalışma ağacında commitlenmemiş görünüyor. Bu nedenle en temiz yol, deploy edilmemiş User kolonunu kalıcılaştırmadan mevcut değişikliği ayrı tablo tasarımına çevirmektir; production data migration gerekmez.

Eğer bu kolon herhangi bir ortamda uygulanmışsa migration dosyası geriye dönük düzenlenmemeli: önce yeni tablo eklenir, kısa TTL nedeniyle mevcut linklerin süresinin dolması beklenir veya hepsi açıkça geçersizleştirilir, uygulama yeni depoya geçirilir, sonraki migration'da User kolonu kaldırılır. Aktif token taşımaya gerek yoktur ve ham token zaten DB'de bulunmadığından güvenli biçimde dönüştürülemez.
