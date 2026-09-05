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

**Adres Adayı**:
Adres sağlayıcısından veya manuel pin seçiminden gelen, kullanıcı henüz onaylamadığı için kalıcı iş verisi sayılmayan kısa ömürlü öneri.
_Avoid_: Kayıtlı adres, doğrulanmış adres

**Adres Arama Oturumu**:
Tek bir autocomplete seçimini ve sağlayıcı oturumunu backend içinde kısa süreyle ilişkilendiren, kullanıcıya bağlı opaque kimlik.
_Avoid_: Google session token, oturum açma belirteci

**Adres Bileşenleri**:
Ülke/il/ilçe referansları ile mahalle, cadde/sokak, dış/iç kapı ve posta kodu gibi ayrı tutulan kanonik alanlar.
_Avoid_: Provider formatted address, tek serbest adres satırı

**Onaylı Adres**:
Kullanıcının yapılandırılmış alanları ve harita konumunu birlikte görüp onayladığı kalıcı adres.
_Avoid_: Google sonucu, adres adayı

**Konum Kaynağı**:
Onaylanan pinin autocomplete, ters geocoding veya manuel seçim yolundan hangisiyle oluştuğunu belirten veri soyu.
_Avoid_: Adres doğruluk garantisi, provider kimliği

**İş Konumu**:
İlan yayımlanırken onaylı adresten alınan ve profil ya da şirket adresi sonradan değişse de ilanla birlikte sabit kalan konum snapshot'ı.
_Avoid_: Güncel profil adresi, şirketin varsayılan adresi

**Yaklaşık Konum**:
İlan herkese açıkken gösterilen ilçe veya hassasiyeti azaltılmış bölge bilgisi.
_Avoid_: Tam adres, kesin koordinat

**Tam İş Adresi**:
İşin yapılacağı kesin adres ve koordinat bilgisi; yalnız yetkili taraflara açıklanır.
_Avoid_: Yaklaşık konum

**Açık İşyeri Adresi**:
Bir şirket işvereninin belirli bir ilanda herkese göstermeyi açıkça seçtiği doğrulanmış tam iş adresi.
_Avoid_: Varsayılan şirket adresi
