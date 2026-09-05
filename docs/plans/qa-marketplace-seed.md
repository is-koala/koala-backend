# İstanbul QA marketplace veri paketi

Durum: Uygulama kodu ve PostgreSQL entegrasyon testleri hazırdır. Dokploy ortam ayarları girilip sürümlü backend etiketi dağıtılmadan mevcut internet API'sinde veri oluşmaz.

## Kapsam

`istanbul-v1` gerçek Koala tablolarına aşağıdaki ilişkili verileri bir kez yazar:

- 120 iş arayan ve 30 işveren hesabı;
- her biri işveren hesabına bağlı 30 şirket;
- İstanbul ilçelerine ve koordinatlarına dağılmış 180 ilan;
- başvurular, atamalar, ödeme görünüm durumları, sohbetler, mesajlar, takipler, kayıtlı ilanlar, bildirimler ve değerlendirmeler;
- iki kontrollü QA hesabı için ayrı işveren ve iş arayan oturumu.

Mobil uygulama bu kayıtları normal HTTP API üzerinden okur. Runtime'da fake repository, test API'si veya otomatik demo girişi kullanılmaz.

## Güvenlik sınırı

Seed normal açılışta çalışmaz. Yalnız `QA_MARKETPLACE_SEED_VERSION=istanbul-v1` olduğunda başlayan runner, sürümü `qa_marketplace_seed_versions` tablosunda atomik olarak sahiplenir. Aynı sürüm sonraki restart veya deploy'da kayıtları güncellemez; manuel test ilerlemesi korunur. Seed transaction'ı yarıda kalırsa sürüm kaydı da geri alınır.

Sentetik e-postalar `qa.iskoala.invalid` alanındadır ve teslim edilebilir değildir. Yalnız iki kontrollü hesap gerçek e-posta adresi alır. Parolalar kaynak kodda, Git geçmişinde, PR'da veya mobil binary'de bulunmaz.

Ödeme/atama durum matrisi ekran ve sorgu testi içindir. Bu satırlarda Iyzico token'ı, payment ID, transaction ID veya submerchant key yoktur. İki kontrollü QA hesabı sentetik finansal atamalara bağlanmaz. Gerçek ödeme başarısı yalnız Iyzico Sandbox initialize, hosted checkout, callback ve retrieve zinciriyle kanıtlanır.

## Dokploy'da tek seferlik uygulama

Dağıtımdan önce mevcut test ortamının veritabanı hedefini ve Iyzico base URL'sinin sandbox olduğunu doğrula. Ardından Dokploy environment ekranında secret değerlerini kayda geçmeden aşağıdaki alanları ayarla:

```text
QA_MARKETPLACE_SEED_VERSION=istanbul-v1
QA_WORKER_EMAIL=<kontrollü iş arayan adresi>
QA_WORKER_PASSWORD=<en az 12 karakterlik ayrı güçlü parola>
QA_EMPLOYER_EMAIL=<kontrollü işveren adresi>
QA_EMPLOYER_PASSWORD=<en az 12 karakterlik ayrı güçlü parola>
MAIL_DELIVERY_MODE=ALLOWLIST
MAIL_ALLOWED_RECIPIENTS=<iki kontrollü adres, virgülle ayrılmış>
```

`API_URL` public HTTPS origin olarak kalmalıdır; medya URL'leri bu değerden üretilir. İlk dağıtım logunda aşağıdaki semantik sonuç aranır:

```text
QA marketplace dataset istanbul-v1 is ready: 120 workers, 30 employers, 30 companies and 180 jobs.
```

Sonraki restart'ta “already applied” görülmesi beklenir. Başarılı uygulamadan sonra parola env değerleri ve `QA_MARKETPLACE_SEED_VERSION` Dokploy'dan kaldırılır; hesapların hash'lenmiş parolaları DB'de kalır. E-posta allowlist ayarları manuel SMTP testleri boyunca korunur.

## Kabul kontrolü

- Her iki kontrollü hesap `/auth/login` üzerinden token ve refresh token alır.
- Şirket sayısı 30, ilan sayısı 180 ve kontrollü hesaplarla birlikte kullanıcı sayısı 150'dir.
- İlan ve atama durum kümeleri ilgili tüm UI durumlarını kapsar.
- Public `/qa-assets/**` görselleri ve örnek CV dosyası `200` döner.
- Aynı seed sürümünü yeniden çağırmak değiştirilmiş bir ilanı başlangıç durumuna döndürmez.
- Kontrollü hesaplara bağlı sentetik ödeme/provider kanıtı bulunmaz.

Otomatik kabul testi `QaMarketplaceSeedIT`, medya erişim testi `QaAssetsIT` sınıfındadır.

