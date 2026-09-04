# Google ve QA kurulum hazırlığı

Durum: Hazırlık aracı üretildi ve statik olarak kontrol edildi; sağlayıcı kurulumu, mail gönderimi veya simülatör doğrulaması yapılmış değildir.

## Bugünkü kurulum

Öncelik staging iOS anahtarıdır. Wizard her çalıştırmada tek ortam seçer; aynı akış dev, staging ve production için tekrarlandığında dokuz platform/ortam anahtarı ayrı tutulur.

| Sıra | İnsan tarafından tamamlanacak adım | Kaynak | Üretilen değer | Hedef |
| --- | --- | --- | --- | --- |
| 1 | Google projesini ve billing'i seç, gereken API'leri etkinleştir | Google Cloud Maps/API sayfaları | Ortam ve public Project ID | Backend'deki Git-dışı yerel QA hazırlık dosyası |
| 2 | iOS anahtarı oluştur ve bundle + SDK kısıtını kaydet | Google Cloud Credentials | İlgili ortamın iOS anahtarı; gizli giriş | Aynı hazırlık dosyası |
| 3 | Android anahtarı oluştur ve package + imza SHA-1 + SDK kısıtını kaydet | Google Cloud Credentials | İlgili ortamın Android anahtarı; gizli giriş | Aynı hazırlık dosyası |
| 4 | Backend anahtarı oluştur ve servis + doğrulanmış çıkış IP kısıtını kaydet | Google Cloud Credentials | İlgili ortamın backend anahtarı; gizli giriş | Aynı hazırlık dosyası |

Android imzası veya backend çıkış IP'si hazır değilse ilgili adım eksik olarak kaydedilip sonraki çalıştırmada tamamlanır. Mevcut iOS sorununun doğrulanması bunların hepsinin bitmesini beklemez.

## Yerel girdilerin kullanımı

QA adresleri kullanıcı tarafından verildi: ilk adres işveren, ikinci adres iş arayan içindir. Bunlar backend'de Git dışındaki `.env.qa.local` dosyasına kaydedildi; izinleri yalnız dosya sahibinin okuyup yazacağı biçimdedir. Bu dosya henüz çalışan backend veya mobil tarafından otomatik okunmaz. Ortam paketi, hazırlanmış girdileri gerçek SMTP alıcı kontrolüne, hesap oluşturmaya ve platform build ayarlarına bağlayacaktır.

Wizard gerçek anahtar değerlerini çıktılamaz; anahtarların var olması yetkili olduklarını kanıtlamaz. Doğru bundle ile build ve gerçek harita görüntüsü kabul testidir. Hiçbir adım veritabanını silmez, Dokploy deploy'u veya GitHub secret yazımı yapmaz; mevcut CI bu Maps değişkenlerini tüketmediği için gereksiz CI secret'ları oluşturulmaz.

Lansman reseti farklı bir prosedürdür. Bu hazırlık aracı o işlemi içermez.

## Doğrulama

- Bash sözdizimi kontrolü geçti.
- Wizard kütüphanesi skill şablonuyla birebir aynı bırakıldı.
- Yerel QA dosyasının Git tarafından dışlandığı ve tracked olmadığı doğrulandı.
- Etkileşimli kurulum uçtan uca çalıştırılmadı.
- ShellCheck bu makinede bulunmadığından çalıştırılmadı.

## Kaynaklar

- [Google Maps Flutter kurulumu](https://developers.google.com/maps/flutter-package/config)
- [Google API anahtar kısıtları](https://developers.google.com/maps/api-security-best-practices)
- [Places API (New) kurulumu](https://developers.google.com/maps/documentation/places/web-service/get-api-key)
