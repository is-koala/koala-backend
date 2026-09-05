---
status: accepted
---

# Hesapları yasal kayıtların referans bütünlüğünü koruyarak anonimleştir

Hesap silme talebi 30 günlük bekleme süresinden sonra kişisel verileri geri
döndürülemez biçimde temizler; kullanıcı satırını fiziksel olarak silmez. Ödeme,
emanet bakiye, etkin iş veya açık uyuşmazlık varken talep bekler. Yönetici yalnız
gerekçeli olarak talebi reddedebilir. Zamanlayıcı aynı talebi veya tamamlanmış bir
adımı yeniden gördüğünde güvenle devam edebilmelidir.

## Değerlendirilen seçenekler

- **Bütün satırları fiziksel silmek:** Kişisel veriyi doğrudan kaldırır; ödeme,
  fatura, atama, rıza ve uyuşmazlık kayıtlarındaki yabancı anahtarları ve yasal
  ispat zincirini bozar.
- **Hesabı yalnız pasifleştirmek:** Referans bütünlüğünü korur; ad, iletişim,
  kimlik, kesin konum, dosya ve serbest metin gibi artık gerekmeyen kişisel
  verileri süresiz tutar.
- **Kimliksiz bir hesap kabuğu bırakmak:** İşlem ve rıza kayıtlarını bağlı tutar;
  kişisel alanları, kimlik bilgilerini, oturumları, dosyaları ve kullanıcı üretimi
  içeriği temizler. Uygulama bu seçeneği kullanır.

## Sonuçlar

- Yaşam döngüsü `REQUESTED` → `ANONYMIZED` veya gerekçeli `REJECTED` durumudur.
  Terminal durumların yeniden işlenmesi etkisizdir.
- Bekleyen ödeme, emanet bakiye, etkin iş ve açık uyuşmazlık anonimleştirmeyi
  erteler; blokeli talep 24 saat sonrasına ötelenir ve hiç denenmemiş taleplerden
  sonra seçilir. Böylece eski bir engel batch'i sürekli işgal etmez; engel kalkınca
  aynı talep yeniden değerlendirilebilir.
- Kullanıcı satırı sentetik, benzersiz değerlerle kimliksiz bir kabuğa dönüşür.
  Ödeme, fatura, atama ve rıza gibi saklanması gereken kayıtlar bu kabuğa bağlı
  kalır; IP, User-Agent ve yeniden kullanılabilir finansal bilgiler temizlenir.
- Veritabanı adımları tek işlem içindedir. Yalnız profil, CV ve doğrulama
  belgelerinin nesne deposu silmeleri başarılı commit'ten sonra başlar; paylaşılan
  şirket ve ilan medyası korunur. Silmeler ve adım denetim kayıtları
  idempotenttir; denetim kaydı kişisel veri kopyalamaz. Silme niyeti aynı
  transaction'da kalıcı outbox'a yazılır; commit sonrası S3 hatası talep terminal
  duruma geçse bile kaybolmaz ve başarılı olana kadar yeniden denenir.
- `subject_id` rolling deploy için expand aşamasında nullable kalır. Backfill mevcut
  satırları doldurur ve geçici veritabanı tetikleyicisi eski binary'nin yalnız
  `user_id` ile yaptığı insertleri tamamlar. `NOT NULL` contract adımı eski binary
  trafikten kaldırıldıktan sonraki migrasyona bırakılır.
- Yedekler, replikalar, sağlayıcı logları ve dış analiz sistemleri bu işlemin
  dışında kendi saklama ve silme kontrollerine ihtiyaç duyar. Ayrıntılı tablo
  eşlemesi `docs/runbooks/account-anonymization.md` içindedir.
