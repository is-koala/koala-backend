# Koala uygulama paketleri

Durum: Kullanıcı tarafından onaylandı; GitHub alt işleri ve bağımlılıkları açıldı. Aşağıdaki sayılar paket numaralarıdır. Gerçek issue eşlemesi [GitHub iş dizininde](github-issues.md) bulunur. Tamamlanmış bir paket backend + mobil davranışıyla gösterilir; bu belge özelliklerin tamamlandığı anlamına gelmez.

| Paket | Teslim | Önkoşul |
| --- | --- | --- |
| 1 | Staging kimliğiyle gerçek hesap, oturum ve SMTP akışı | Yok |
| 2 | Gerçek DB'den dolu İstanbul listeleri ve iki QA hesabı | 1 |
| 3 | iOS/Android harita yapılandırması ve görünür karolar | 1, ilgili Maps anahtarı |
| 4 | Kullanıcı/şirket/ilan için arama + adres/pin onayı | 3, backend Places anahtarı |
| 5 | İlan tam adresini yetkiye göre açma/gizleme | 4 |
| 6 | Yayında kilitlenen fiyat ve işveren toplamı | 1 |
| 7 | Tekil ve doğrulanmış ödeme denemesi/durum akışı | 6, Iyzico Sandbox ayarı |
| 8 | Uygulama içi hosted checkout ve dönüş | 7 |
| 9 | İki simülatörde çalışma, iptal ve geçmiş doğrulaması | 2, 5, 8 |
| 10 | Lansman reseti ve sonraki production kontrolleri runbook'u | Taslak hemen; son prova 9 |

## 1 — Staging kimliğiyle gerçek hesap, oturum ve SMTP

**Teslim:** İki kullanıcı staging uygulamasıyla mevcut API'ye gerçek oturum açar; kayıt/şifre sıfırlama postası kontrollü gelen kutusuna gider.

**Önkoşul:** Yok. QA alıcıları sağlandı.

- [ ] Dev/staging/production kimlikleri ile API hedefi uyumludur; eksik seçim internet API'sine sessizce bağlanmaz.
- [ ] Mevcut demo bootstrap ve gömülü hesap bilgileri runtime'dan çıkarılır; gerçek login ve token yenileme korunur.
- [ ] Mail alıcı kontrolü SMTP'den önce uygulanır; iki kontrollü adres dışındaki gönderim teslim edilmiş sayılmaz.
- [ ] Şirket ve bireysel işveren akışı korunur; kullanılan doğrulama/reset bağlantısı doğru ortamdaki uygulamaya gider.
- [ ] Gerçek HTTP giriş/yenileme, bozuk token ve SMTP izinli/engelli alıcı davranışları sınanır.

**Önerilen mobil branch:** `feat/staging-real-auth`

## 2 — İstanbul verisi ve QA hesapları

**Teslim:** API'den okunan 150 kullanıcı, 30 şirket ve 180 ilan ile listeler doludur; şirketler gerçek yetkili hesaplarına bağlıdır.

**Önkoşul:** 1.

- [ ] Sürümlü one-shot seed iki QA adresini yerel/ortam ayarından alır; kaynak kodda ortak parola bulunmaz.
- [ ] Tekrar çalıştırma kayıt çoğaltmaz, manuel testte ilerleyen iş durumlarını geri almaz.
- [ ] Kullanıcı, şirket, ilan, başvuru ve mesaj ilişkileri PostgreSQL'de doğrulanır.
- [ ] Normal servis açılışı seed çalıştırmaz; selective testData/seedRunId modeli eklenmez.
- [ ] Uydurma provider kimlikleri dış servislere gönderilmez; finansal fixture'lar gerçek ödeme kanıtı sayılmaz.
- [ ] Aynı veri mobil listelerden, filtrelerden ve detaylardan gerçek API ile okunur.

**Önerilen mobil branch:** Mobil değişiklik gerekiyorsa `feat/istanbul-qa-flow`; veri işi tek başına mobil commit gerektirmez.

## 3 — Görünür harita

**Teslim:** Mevcut beyaz ekran kaydı gerçek karolarla doğrulanarak kapanabilir.

**Önkoşul:** 1 ve ilgili platform/ortam için yetkili Google Maps anahtarı.

- [ ] Mevcut mobil #5 kullanılır; ikinci beyaz harita issue'su açılmaz.
- [ ] Build doğru bundle/package ve platform anahtarını taşır; değer loga veya Git'e girmez.
- [ ] Eksik anahtar için yapılandırma hatası anlaşılırdır; boş harita başarı kabul edilmez.
- [ ] İki iOS simülatöründe gerçek karolar görünür; Android yapılandırması kendi build'iyle doğrulanır.
- [ ] Bu paket yeni adres API'sini beklemez; mevcut harita ekranında bağımsız gösterilebilir.

**Önerilen mobil branch:** `fix/maps-configuration`

## 4 — Ortak adres seçimi

**Teslim:** Kullanıcı, şirket ve ilan formları aynı adres arama ve pin onayı akışıyla tutarlı kayıt oluşturur.

**Önkoşul:** 3 ve backend Google Places anahtarı.

- [ ] Arama/resolve normal Koala API'sinden geçer; mobil backend anahtarını bilmez.
- [ ] Ülke/il/ilçe ilişkileri doğrulanır; kullanıcı onayladığı adresi yeniden açtığında aynı alanları görür.
- [ ] Konum izni reddedildiğinde arama/manual pin yolu çalışır.
- [ ] Boş sonuç, iptal edilen arama, eski sorgunun geç yanıtı ve provider hatası kapsanır.
- [ ] İlan adresi profil adresinden bağımsız bir yayın kaydıdır.

**Önerilen mobil branch:** `feat/address-selection`

## 5 — İlan adresi görünürlüğü

**Teslim:** Aynı ilanı farklı hesaplar açtığında yalnız yetkileri ölçüsünde konum görür.

**Önkoşul:** 4.

- [ ] Anonim/yetkisiz listelerde ve iç içe detay yanıtlarında tam adres/koordinat sızıntısı yoktur.
- [ ] Atamaya bağlı adres erişimi ve terminal durumda gizlenme ADR-0003 ile aynıdır.
- [ ] Şirketin açık işyeri tercihi ilan bazındadır; bireysel işveren bunu etkinleştiremez.
- [ ] Önbellekte kalmış tam adres yetki sona erdikten sonra gösterilmez.
- [ ] İşveren, atanmış çalışan, diğer kullanıcı ve anonim erişim HTTP + mobil davranış testleriyle karşılaştırılır.

**Önerilen mobil branch:** `feat/job-location-privacy`

## 6 — Fiyat dökümü ve yayın snapshot'ı

**Teslim:** İşveren oluşturma, ilan detayı, atama ve ödeme öncesinde aynı çalışan ücreti/hizmet bedeli/toplamı görür.

**Önkoşul:** 1; yeni adres akışının bitmesi gerekmez.

- [ ] Fiyat backend'den gelir; yayın anında kalıcı olur ve atama/ödeme denemesine aktarılır.
- [ ] Sonraki komisyon değişimi önceki ilanın toplamını değiştirmez.
- [ ] Yuvarlama ve para birimi backend ile mobilden tutarlı okunur.
- [ ] Fiyat sorgusu başarısızsa uydurma toplam gösterilmez.
- [ ] Test sunumunda ayrı KDV hesabı/ifadesi yoktur; mali karar açık kalır.

**Önerilen mobil branch:** `feat/employer-pricing`

## 7 — Güvenilir ödeme denemesi

**Teslim:** İşveren aynı atama için tekrar ödeme başlattığında tek güvenilir denemeye ulaşır; iki taraf yetkili oldukları sonucu görür.

**Önkoşul:** 6 ve mevcut Iyzico Sandbox bağlantısı.

- [ ] Çift dokunma, eşzamanlı cihaz ve ağ tekrarı ikinci aktif checkout/tahsilat oluşturmaz.
- [ ] Initialize/Retrieve imzaları ve ödeme/snapshot eşleşmeleri doğrulanır.
- [ ] Belirsiz sonuç kesin ret sayılmaz; yeni tahsilat açılmadan doğrulanır.
- [ ] Katılımcı olmayan kullanıcı ödeme denemesi/durumuna erişemez.
- [ ] Yinelenen callback/reconciliation tek finansal sonuç üretir; sağlayıcı token'ı gereksiz yere DTO'ya çıkmaz.
- [ ] Mobil durum sorgusu başarı/bekleme/ret/tekrar kontrol ayrımını gösterir.

**Önerilen mobil branch:** `feat/payment-attempt-status`

## 8 — Uygulama içi Iyzico

**Teslim:** İşveren fiyatı onayladıktan sonra uygulamadan ayrılmadan kart/3DS akışını tamamlar.

**Önkoşul:** 7.

- [ ] JWT veya native bridge taşımayan hosted WebView kullanılır.
- [ ] İlk URL doğrulanır, HTTPS 3DS yönlendirmeleri çalışır; dönüş tam URL eşleşmesidir.
- [ ] Kapatma, geri dönüş ve foreground sonrası authenticated backend durumu okunur.
- [ ] Callback gecikmesi, ağ kaybı, işlem reddi ve uygulamanın kapanıp açılması kapsanır.
- [ ] Başarı, gerçek Iyzico Sandbox callback/retrieve ve PostgreSQL kaydıyla kanıtlanır.

**Önerilen mobil branch:** `feat/in-app-checkout`

## 9 — İki taraflı yaşam döngüsü kabulü

**Teslim:** İşveren ve iş arayan aynı ilanı iki simülatörde baştan sona test eder; farklı durumlar okunabilir bir matriste sunulur.

**Önkoşul:** 2, 5, 8.

- [ ] Başvuru → işveren yanıtı → çalışan yanıtı → ödeme → check-in/out → tamamlama → değerlendirme zinciri tamamlanır.
- [ ] Ret, iptal, ödeme bekleme/ret, iade ve uyuşmazlık durumları desteklenen gerçek geçişlerle sınanır.
- [ ] Mesajlaşma/okunma, bildirim ve ekran yenileme diğer cihazdaki gerçek sonucu gösterir.
- [ ] Restart sonrası oturum ve durumlar backend'den yeniden okunur.
- [ ] Her senaryoda hesap, işlem zamanı, DB/API sonucu ve kanıt türü bulunur; gizli değerler rapora yazılmaz.
- [ ] Bu aşamada bulunan düzeltmeler ayrı küçük issue/commit'e ayrılır; bütün hata düzeltmeleri tek dev test commit'ine yığılmaz.

**Önerilen mobil branch:** `test/two-device-marketplace`

## 10 — Lansman geçiş planı

**Teslim:** Mevcut altyapıyı temiz production'a çevirecek, hedefi ve geri dönüşü belli bir insan tarafından çalıştırılan prosedür.

**Önkoşul:** Taslak hemen hazırlanabilir; nihai kabul provası 9 sonrasıdır.

- [ ] Hedef DB adı/servisi ve yedekten dönüş açıkça doğrulanır.
- [ ] İş verisiyle beraber eski oturum, kuyruk ve medyanın yaşam döngüsü tanımlanır; ilgisiz kaynaklara dokunulmaz.
- [ ] Yeni ayarlar seed'i kapatır; eski veri tekrar oluşmaz.
- [ ] SMTP alıcı kısıtı ve Sandbox'tan live'a geçiş ayrı kontrol edilir.
- [ ] Reset rutin CI/deploy'a bağlanmaz; çalıştırma anında açık onay gerekir.
- [ ] Günlük production smoke ile kapsamlı manuel finansal test ayrımı korunur.

**Önerilen mobil branch:** Sadece mobil belge/config değişirse `chore/launch-runbook`.

## Commit ve PR sınırı

Mevcut setup PR'ı altyapı becerileriyle sınırlıdır. Çalışma ağacındaki eski ve yeni uygulama değişiklikleri önce yukarıdaki paketlere eşlenir; yalnız ait oldukları dosya/hunk'larla commit hazırlanır. Backend main üzerinde, mobil her özellik için ayrı branch üzerinde ilerler. Backend sözleşme değişikliği mobil tüketiminden önce doğrulanır; uyumsuz eski mobil sürüm için açık geçiş kararı olmadan yayına gidilmez.

İlk belge paketi glossary + ADR + araştırma + bu spec/paket taslağıdır. Kodu uygulama, commit veya push olarak raporlamak için ilgili işlem ve doğrulama gerçekten tamamlanmış olmalıdır.
