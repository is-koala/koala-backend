# Veritabanını örnek veri olmadan teslim et

Durum: Kabul edildi — 5 Eylül 2026. ADR-0001'in seed'e ilişkin maddesinin yerine geçer.
Referans verisi listesindeki "iş kategorileri" maddesi [ADR-0009](0009-manage-job-categories-from-the-admin-api.md) ile geçersizdir.

## Karar

Koala veritabanı yalnız referans verisiyle gelir: ülke, il, ilçe, ilçe alias'ları, iş kategorileri, üniversite/fakülte/bölüm, sözleşme metinleri, rozetler ve denetim için gereken `SYSTEM` kullanıcısı. Örnek kullanıcı, şirket, ilan, başvuru, atama, ödeme, mesaj ve değerlendirme üretilmez.

`istanbul-v1` QA marketplace seed'i, tek seferlik runner'ı, `qa_marketplace_seed_versions` tablosu, `/qa-assets/**` statik uç noktası ve buna bağlı demo görselleri kaldırılmıştır. Test hesapları artık yalnız gerçek kayıt akışıyla oluşturulur.

## Bağlam

Seed 150 kullanıcı, 30 şirket ve 180 ilanı doğrudan `JdbcTemplate` ile yazıyordu. Ekranları doldurma faydası vardı; karşılığında gerçek kayıt/oturum akışı manuel testte hiç yürütülmüyordu ve ekranda görülen her kayıt, üretim kodunun ürettiği değil seeder'ın ürettiği bir kayıttı. Kayıt akışındaki bir kusur bu kurulumda görünmez kalıyordu.

Değerlendirilen alternatifler:

- **Seed'i sürüm yükseltip korumak:** ekranlar dolu kalırdı ama gerçek kayıt akışı yine test edilmemiş olurdu.
- **Seed'i yalnız kapatmak:** ölü kod, ölü env değişkenleri ve 6,5 MB ölü demo görseli repoda kalırdı.
- **Tamamen kaldırmak (seçilen):** tek veri kaynağı üretim kod yolları olur.

## Sonuçlar

- Manuel test artık kayıt → e-posta doğrulama → şifre belirleme → oturum zincirini gerçekten yürütür; bu zincir daha önce seed hesapları yüzünden atlanıyordu.
- Keşif, liste ve harita ekranları başlangıçta boştur. Boş durum (empty state) davranışı bu yüzden gerçek bir test hedefidir, gizlenemez.
- `QaMarketplaceSeedIT`'in adres sütun invaryantları (mahalle/cadde/kapı no dolu, `position_source = USER_CONFIRMED_PIN`, `canonical_version = 1`) artık seed üzerinden doğrulanmıyor. İlçe sayısı ve Eminönü→Fatih eşlemesi `LiquibaseMigrationIT` kapsamındadır; kalan invaryantlar adres yazma yolunun kendi testlerine bırakılmıştır.
- Dokploy'da `QA_MARKETPLACE_SEED_VERSION`, `QA_WORKER_EMAIL`, `QA_WORKER_PASSWORD`, `QA_EMPLOYER_EMAIL`, `QA_EMPLOYER_PASSWORD` değişkenleri gereksizdir ve kaldırılmalıdır.
- SMTP allowlist (`MAIL_DELIVERY_MODE`, `MAIL_ALLOWED_RECIPIENTS`) korunur; gerçek kayıt akışı e-posta teslimatına dayandığı için artık daha kritiktir.
