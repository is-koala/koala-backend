# SMTP alıcı güvenliği ve kuyruk dayanıklılığı

İlgili iş: [backend #4](https://github.com/is-koala/koala-backend/issues/4). Bu değişiklik paket 1'in SMTP alt adımıdır; gerçek hesap, uygulama ortamı ve bağlantı kabulü tamamlanmadan issue kapanmaz.

## Dağıtım öncesi ayarlar

- `MAIL_DELIVERY_MODE=ALLOWLIST` ve `MAIL_ALLOWED_RECIPIENTS` içinde virgülle ayrılmış iki kontrollü QA adresi kullanılır. Adresler Git'e konmaz; yerel hazırlık dosyasında bulunur.
- Mod belirtilmezse `ALLOWLIST`, liste belirtilmezse boş liste kullanılır: hiçbir e-posta SMTP'ye gitmez. Bu yüzden yeni kodu mevcut ortama çıkarmadan önce iki ayar Dokploy'da hazırlanmalıdır.
- `UNRESTRICTED` açıkça seçilen kısıtsız moddur. Mevcut test ortamında kullanılmaz; lansman geçişinde ayrıca değerlendirilir.
- Hatalı mod veya geçersiz alıcı yapılandırması açılışta reddedilir.
- `.env.qa.local` otomatik içe aktarılmaz. `MAIL_HOST`, port ve SMTP kimlik bilgileri mevcut gerçek sağlayıcı ayarlarıdır; bu commit bunları değiştirmez.

## Teslim davranışı

Kuyruk mesajının alıcısı kalıcı bildirim alıcısıyla eşleşmelidir. Eşleşmiyorsa SMTP çağrılmaz ve bildirim `FAILED / EMAIL_RECIPIENT_MISMATCH` olur. İzinli listede olmayan alıcı `SUPPRESSED / EMAIL_RECIPIENT_NOT_ALLOWED` olur; gönderilmemiş ileti `SENT` gösterilmez. Daha önce gönderilmiş veya bastırılmış mesaj yeniden teslim edilmez.

SMTP hatası DB'de `FAILED` olarak kalır ve retry'a iletilir. Üç deneme (ilk çağrı + iki retry, 1 ve 2 saniye bekleme) tükenince mesaj `email.queue.dlq` kuyruğuna yeniden yayımlanır. Ana `email.queue` tanımı değişmez; mevcut kuyruğu silmek veya yeniden oluşturmak gerekmez. DLQ otomatik tekrar gönderim değildir; nedeni giderildikten sonra kontrollü inceleme gerekir. Kuyruk içeriği doğrulama bağlantıları gibi hassas veri taşıyabilir; erişim kısıtlanır ve log/issue'ya kopyalanmaz.

SMTP ile DB arasında dağıtık transaction yoktur; bu değişiklik tam olarak bir kez e-posta teslimi garantisi vermez. SMTP kabulü ile `sentAt` kaydı arasındaki süreç çökmesi tekrar gönderime yol açabilir.

## Doğrulama — 5 Eylül 2026

Yalnız SMTP dosyaları `9e96979` tabanının geçici kopyasına uygulandı. Demo seed, security veya mobil değişiklikleri olmadan:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dit.test=EmailRetryIT,EmailDeliveryIT verify
```

- 83 birim test sınıfı: 608 başarılı, 1 atlanan, hata yok. Atlanan gerçek kart testi `KOALA_CHIP_DIR` / `KOALA_CSCA_DIR` girdileri gerektirir.
- 2 entegrasyon test sınıfı, 6 test: hepsi başarılı. Gerçek PostgreSQL 17 ve RabbitMQ 4.1.3 Testcontainers kullanıldı; yalnız dış SMTP taşıyıcısı test çiftidir.
- Push öncesi CI ile aynı tam `./mvnw -q verify` komutu da aynı izole kopyada geçti: 608 birim testi ve 16 sınıfta 68 entegrasyon testi başarılı, yalnız gerçek kart testi atlandı.
- Eski kuyruk tanımına uyumluluk, AMQP kimliği olmayan mesajın üç denemesi, DLQ'da korunması ve sonraki bildirimin teslimi doğrulandı.
- İzinli iki alıcı, engelli alıcı, alıcı uyuşmazlığı ve önceden gönderilmiş kaydın korunması doğrulandı.
- Dışarıya gerçek e-posta gönderimi, Dokploy ayarları ve canlı hesap kabulü henüz yapılmadı.

## Standards

İlk inceleme mevcut queue argümanlarının uyumsuzluğunu ve kimliksiz stateful retry sorununu buldu. İkisi de gerçek broker'da kırmızı testle üretildi ve giderildi. Yeni iki yorum satırı repo kuralına uygun olarak çıkarıldı. Açık bulgu: 0.

## Spec

İlk incelemedeki iki kuyruk bulgusuna ek olarak alıcı/bildirim uyuşmazlığı düzeltildi ve regresyon testi eklendi. SMTP alt kapsamı için açık bulgu: 0. Paket 1'in kalan mobil/gerçek hesap kabulü bu alt commit'in dışında takip edilir.

Özet: Standards 0 açık bulgu; Spec 0 açık bulgu. Yalnız kuyruk argümanlarını inceleyen eski test gerçek broker regresyonuyla değiştirildi.
