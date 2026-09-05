# Sağlayıcıdan bağımsız onaylı adresler kullan

Durum: Kabul edildi — 5 Eylül 2026

## Karar

Koala kullanıcı, şirket ve ilan adreslerini tek `AddressWriteV2` sözleşmesiyle kaydeder. Kalıcı kimlikler Koala ülke/il/ilçe UUID'leri ile Türkiye adres standardına uygun yapılandırılmış bileşenlerdir; konum kullanıcı tarafından aynı ekranda görülüp onaylanır ve ilan oluşturulurken snapshot alınır. Eski `countryCode/cityName/districtName/addressLine` yazma sözleşmesi, isimle eşleme, adapter ve dual-write tutulmaz.

Autocomplete, place resolve ve reverse geocoding gerçek bir sağlayıcıya açılan ayrı bir portun adapter'larıdır. Provider session token'ı, ham cevap, formatted address ve koordinat yalnız kısa ömürlü aday sınırında kalır; mobil yalnız backend'in opaque arama oturumu ve aday belirteçlerini görür. Web-service anahtarı mobil binary'ye girmez. Google kaynaklı adres/koordinatın kalıcı ve çok kullanıcılı kullanımına, lisans ile KVKK yurt dışı aktarım kararı yazılı olarak kapanmadan izin verilmez; bu aşamaya kadar kalıcı kayıt kullanıcı tarafından bağımsız girilip onaylanan birinci taraf veridir.

## Bağlam

Mevcut üç mobil akış aynı adresi farklı biçimlerde kuruyor; şirket akışı İstanbul'u sabitliyor, diğer akışlar referans UUID'leri ellerinde olmasına rağmen isim gönderiyor ve pin ile metin birbirinden kopuk kalıyor. Google Address Validation Türkiye'yi desteklemiyor. Places ve Geocoding içeriğinin saklama/yayınlama şartları da Google sonucunu doğrudan Koala'nın kalıcı public ilan verisi yapmayı güvenli bir varsayım olmaktan çıkarıyor.

## Sonuçlar

- Kullanıcı adresi, şirket adresi ve şirket/bireysel ilan adresi aynı derin modülü ve sözleşmeyi kullanır.
- Provider değiştirmek adres tablosunu veya mobil formları değiştirmez; yalnız adapter etkilenir.
- Cihaz konum izni yalnız `Konumumu kullan` eyleminde istenir; yaklaşık/ret durumu manuel akışı engellemez.
- İstanbul referans verisi 39 aktif ilçedir; Eminönü kayıtları audit ile Fatih'e taşınır ve alias yalnız provider eşlemesinde kullanılır.
- Public ve exact iş konumu farklı read projection'larıdır; bireysel işverenin tam adresi public yanıta girmez.
- Eski mobil build yeni write API'sine erişemez; cutover minimum sürüm kapısıyla koordineli yapılır.
- Gerçek provider gateway'i ve production yayını, ayrı sunucu anahtarı, sağlayıcı lisansı, KVKK aktarım mekanizması, aydınlatma/izin ve retention kararlarını gerektirir.

