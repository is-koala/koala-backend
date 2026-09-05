---
status: accepted
---

# Web istemcileri için Next.js BFF kullan

Koala web ve yönetim arayüzlerinde tarayıcı backend'e doğrudan HTTP isteği
göndermez; bütün HTTP trafiği aynı origin'deki Next.js BFF katmanından geçer.
Erişim ve yenileme tokenları `HttpOnly`, `Secure`, `SameSite=Lax` cookie'lerde
kalır; BFF `X-Device-Id`, `X-App-Version` ve `X-Client-Platform` başlıklarını
backend'e ekler.

## Değerlendirilen seçenekler

- **Doğrudan tarayıcı:** Daha az sunucu kodu gerektirir; tokenları JavaScript'in
  erişebildiği depoya taşır, CORS yüzeyini genişletir, CSRF riskini bearer
  başlığına karşı azaltırken XSS halinde tokenın doğrudan çalınmasına yol açar
  ve SSR için ayrı oturum yolu oluşturur.
- **Next.js BFF:** Ek ağ geçidi ve güvenilir proxy yapılandırması ister; ana
  tokenları tarayıcıdan saklar, SSR ile kullanıcı eylemlerini aynı cihaz
  kimliğinde birleştirir ve koala-admin ile aynı güven sınırını kurar. Cookie
  tabanlı oturum CSRF yüzeyi doğurur; `SameSite=Lax` ilk savunmadır, durum
  değiştiren BFF uçları ayrıca `Origin`/`Host` eşleşmesini doğrular ve yalnız
  güvenli HTTP yöntemleri çapraz origin gezinmeyle çalışabilir.

## Sonuçlar

- `api.iskoala.com` HTTP CORS allowlist'ine web veya admin origin'i eklenmez.
- Tarayıcı profili başına cihaz kimliğini BFF üretir ve HttpOnly cookie'de tutar.
- Web sürümü ve `X-Client-Platform: web` başlığını BFF ekler.
- BFF durum değiştiren isteklerde aynı-origin `Origin` doğrulaması yapar;
  eşleşmeyen veya eksik origin'i reddeder. `GET` uçları durum değiştirmez.
- STOMP tek doğrudan tarayıcı istisnasıdır. Ana access token JavaScript'e
  açılmaz; kısa ömürlü STOMP kimliği hazır olana kadar web mesajlaşması
  bağlanmaz ve handshake dar bir origin allowlist'i kullanır.
- Iyzico dönüş parametresi finansal gerçek sayılmaz; sonuç backend'den
  doğrulanır veya kullanıcı mobil uygulamaya aktarılır.
- `ClientIpResolver`, yalnız güvenilir proxy zincirini kabul edecek biçimde
  sertleştirilmeden BFF adresi son kullanıcı IP'si sayılmaz.
