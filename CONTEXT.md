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

**İptal İadesi**:
Belirli bir iptal kararının işveren için doğurduğu iade ve o iadenin yaşam döngüsü.
_Avoid_: Atamanın son iadesi, atamadaki herhangi bir iade

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

**Aday Hassasiyeti**:
Adres adayında sağlayıcının bulabildiği en ince düzey: bina (kapı numarası ya da site adı), cadde/sokak ya da yalnız mahalle/posta kodu; istemciye eksik kalan alanı kullanıcıdan istemesi için ipucu verir.
_Avoid_: Yaklaşık konum (ilan gizliliği), koordinat doğruluğu

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

**Adres Hassasiyeti**:
Bir ilan cevabındaki konumun yaklaşık mı yoksa tam mı olduğunu açıkça bildiren sözleşme değeri. Yetki değiştikçe aynı ilan `APPROXIMATE` veya `EXACT` dönebilir.
_Avoid_: Adres doğruluğu, konum kaynağı

## Oturum ve gizlilik

**Tarayıcı Cihazı**:
Web BFF'nin tarayıcı profili için ürettiği kalıcı cihaz kimliğiyle temsil edilen oturum sahibi. User-Agent kimliğin parçası değildir; cookie silinirse yeni tarayıcı cihazı oluşur.
_Avoid_: Tarayıcı türü, User-Agent oturumu

**Anonimleştirilmiş Hesap**:
Kimlik, iletişim, profil, konum, kimlik bilgisi ve kullanıcı üretimi serbest metni geri döndürülemez biçimde temizlenmiş; yalnız finansal ve yasal kayıtların referans bütünlüğü için kimliksiz bir kabuk olarak tutulan hesap.
_Avoid_: Devre dışı hesap, silme talebi

**Sahipsiz Giriş Denemesi**:
Hiçbir hesaba bağlanamayan giriş denemesi kaydı; `login_attempts` satırında `user_id` boştur ve satır yalnızca giriş formuna yazılan adresi taşır. Adresin sahibi hiç kaydolmamış olabilir. Kodda tek ad `unattributed` (`unattributedDays`, `deleteUnattributedBefore`, `purgeExpiredUnattributed`, `idx_login_attempts_unattributed_*`), Türkçe belgede tek ad "sahipsiz". Karşıtı **Hesaba Bağlı Giriş Denemesi** (`attributed`).
_Avoid_: Ownerless, sahipsiz kayıt, anonim giriş denemesi

## Personel notları ve işaretleri

**Personel Notu**:
Personelin bir kullanıcı, şirket, atama, uyuşmazlık, şikayet ya da ilan hakkında bıraktığı serbest metin (≤2000 karakter); yazarı ve düzenlenme anı taşır, yalnız yazarı ya da `staff.manage` sahibi düzenler/siler. Hedef kaynağın varlığı denetlenmez; kodda `StaffNote`.
_Avoid_: Yorum, admin notu, kullanıcı notu (kullanıcının kendi yazdığı `notes` alanlarıyla karışır)

**İşaret**:
Personelin bir kaynağa koyduğu `RISK` / `VIP` / `WATCH` / `OTHER` türünde etiket; aynı kaynakta her türden en fazla bir etkin işaret, kaldırılan işaret `clearedAt` ile kapanır ve listelerde görünmez. Liste rozetleri toplu okumayla (`resourceIds`, ≤200) çekilir; kodda `StaffFlag`.
_Avoid_: Bayrak, etiket, rozet (kullanıcı rozetleri `Badge` ile karışır)

## Ulaşım

**Ulaşım Planı**:
İşverenin bir ilan için yazdığı gidiş/dönüş ulaşım düzeni: yön başına yöntem (`SHUTTLE`, `CASH_ALLOWANCE`, `SELF`, `NONE`), yol parası tutarı ve servisler. İlan başına en çok bir plan; kodda `TransportPlan`.
_Avoid_: Servis planı, transfer

**Servis**:
Bir ulaşım planındaki tek bir araç: yön, ad, plaka, kapasite, şoför bilgisi ve sıralı duraklar. Plaka ve şoför bilgisi yalnız işverene ve o serviste koltuk seçmiş işçiye açıktır; kodda `Shuttle`.
_Avoid_: Araç, otobüs, shuttle (Türkçe metinde)

**Durak**:
Bir servisin belirli bir saatte uğradığı, mevcut adres sözleşmesiyle tanımlanmış nokta; kodda `ShuttleStop`. Herkese açık ilan görünümünde yalnız ilçe/semt adı ve saat görünür.
_Avoid_: Toplanma noktası, pickup

**Koltuk**:
Bir atamanın bir yön için seçtiği servis + durak çifti; atama başına yön başına en çok bir koltuk, kapasite dolunca seçilemez. İşçi kendisi seçer, işveren işçi adına seçebilir; kodda `SeatAssignment`.
_Avoid_: Rezervasyon, yer

**Yol Parası**:
`CASH_ALLOWANCE` yönteminde işverenin işçiye elden verdiği tutar; para yolunun (emanet/komisyon) dışındadır ve yalnız bilgi olarak kaydedilir.
_Avoid_: Ulaşım ücreti, harcırah
