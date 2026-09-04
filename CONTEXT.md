# Koala İş Pazaryeri

Koala, işverenlerin kısa veya dönemsel işler yayımladığı; iş arayanların başvurup işi üstlendiği iki taraflı iş pazaryeridir.

## Taraflar ve iş akışı

**İş Arayan**:
İlanları inceleyen ve işe başvuran kullanıcı.
_Avoid_: Employee, personel, aday kullanıcı

**İşveren**:
Kendi adına veya yetkili olduğu bir şirket adına ilan yayımlayan kullanıcı.
_Avoid_: Business user, müşteri

**Şirket İşvereni**:
Bir şirketin adına hareket etmeye yetkili işveren.
_Avoid_: Kurumsal hesap

**Bireysel İşveren**:
Bir şirkete bağlı olmadan kendi adına ilan yayımlayan işveren.
_Avoid_: Şirketsiz kullanıcı

**İlan**:
Belirli bir yerde, zamanda ve ücretle yapılması istenen iş teklifi.
_Avoid_: Post, görev kaydı

**Başvuru**:
Bir iş arayanın belirli bir ilan için değerlendirilme talebi.
_Avoid_: Request, teklif

**Atama**:
Bir başvurunun kabulüyle işveren, iş arayan ve ilan arasında kurulan çalışma ilişkisi.
_Avoid_: Eşleşme, kabul kaydı

## Fiyat

**Çalışan Ücreti**:
Atanan kişinin iş karşılığında hak edeceği tutar.
_Avoid_: Maaş, işveren toplamı

**Koala Hizmet Bedeli**:
Koala'nın pazaryeri hizmeti için işverenden aldığı tutar.
_Avoid_: Çalışan kesintisi, Iyzico işlem bedeli

**İşveren Toplamı**:
Çalışan ücreti ile Koala hizmet bedelinin toplamı.
_Avoid_: Çalışan ücreti, maaş

**Fiyat Snapshot'ı**:
İlan yayımlandığında kesinleşen çalışan ücreti, hizmet bedeli oranı/tutarı, işveren toplamı ve para birimi bütünü.
_Avoid_: Güncel komisyon hesabı

**Ödeme Denemesi**:
Bir işverenin belirli bir atamanın işveren toplamını ödemek için başlattığı tek girişim.
_Avoid_: Atama, ödeme oturumu

**Ödeme Sonucu**:
Ödeme sağlayıcısının doğrulanmış cevabıyla backend tarafından kesinleştirilen finansal durum.
_Avoid_: Dönüş URL'si durumu, WebView sonucu

## Konum

**Yaklaşık Konum**:
İlan herkese açıkken gösterilen ilçe veya hassasiyeti azaltılmış bölge bilgisi.
_Avoid_: Tam adres, kesin koordinat

**Tam İş Adresi**:
İşin yapılacağı kesin adres ve koordinat bilgisi; yalnız yetkili taraflara açıklanır.
_Avoid_: Yaklaşık konum

**Açık İşyeri Adresi**:
Bir şirket işvereninin belirli bir ilanda herkese göstermeyi açıkça seçtiği doğrulanmış tam iş adresi.
_Avoid_: Varsayılan şirket adresi
