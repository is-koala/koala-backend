# Koala: gerçek API ile iki taraflı manuel test

Durum: Q23–Q37 kararları ve uygulama/test sınırları kullanıcı tarafından onaylandı. GitHub spec ve alt işleri [iş dizininde](github-issues.md) takip edilir. Bu belge tamamlanmış özellik raporu değildir.

## Problem Statement

İşveren ve iş arayan aynı ilan üzerinde gerçek kullanıcı akışlarını güvenilir biçimde test edemiyor. Çalışma ağacında birçok akış API'ye bağlı olsa da otomatik demo girişleri, eksik ortam ayrımı, beyaz harita, parçalı adres girişi, ödeme öncesi eksik fiyat dökümü ve Safari'ye çıkan checkout deneyimi birlikte testin bütünlüğünü bozuyor.

## Solution

İki iPhone simülatörü mevcut Koala API'sine staging uygulamasıyla bağlanır. İstanbul içinde ilişkileri tutarlı, gerçek PostgreSQL kayıtları ekranları doldurur. Kontrollü işveren ve iş arayan hesapları gerçek oturumla giriş yapar; gerçek SMTP, Google servisleri ve Iyzico Sandbox ile ilan → başvuru → atama → ödeme → çalışma → tamamlama akışı izlenebilir.

## User Stories

1. İşveren olarak gerçek hesabımla giriş yapmak istiyorum; böylece işlemlerim doğru kullanıcıya yazılsın.
2. İş arayan olarak ayrı simülatörde giriş yapmak istiyorum; böylece iki tarafın gördüğü durumları karşılaştırabileyim.
3. Kullanıcı olarak kayıt ve şifre sıfırlama e-postalarını kontrollü gelen kutumda almak istiyorum; böylece token akışını gerçekten sınayabileyim.
4. Kullanıcı olarak süresi dolmuş veya kullanılmış doğrulama bağlantısından anlaşılır hata almak istiyorum.
5. Kullanıcı olarak uygulamayı yeniden açınca gerçek oturumumla devam etmek istiyorum.
6. Bireysel işveren olarak şirket kurmadan kendi adıma ilan yayımlamak istiyorum.
7. Şirket işvereni olarak yetkili olduğum şirket adına ilan yayımlamak istiyorum.
8. İş arayan olarak başvuru önkoşullarındaki eksikleri görmek ve ilgili profile dönerek tamamlamak istiyorum.
9. Test yapan kişi olarak İstanbul'da 100'den fazla ilişkili kayıt görmek istiyorum; böylece boş ekranlarla sınırlı kalmayayım.
10. Test yapan kişi olarak her şirketin sahibi/yetkilisi, ilanı ve ilgili kullanıcı ilişkileri bulunmasını istiyorum.
11. Kullanıcı olarak şirket ve ilan listeleri ile detaylarının aynı backend kayıtlarını göstermesini istiyorum.
12. Kullanıcı olarak Google harita karolarını ve ilanın izin verilen konumunu görmek istiyorum.
13. Kullanıcı olarak adres arayıp öneri seçmek, alanları ve pini onaylamak istiyorum.
14. Kullanıcı olarak konum izni vermeden de adres aramak ve pin seçmek istiyorum.
15. Kullanıcı olarak adres arama sonucu gelmediğinde manuel adres ve pin ile devam etmek istiyorum.
16. Kullanıcı olarak kullanıcı, şirket ve ilan adreslerinde aynı seçim deneyimini kullanmak istiyorum.
17. Bireysel işveren olarak tam iş adresimin herkese açık ilan yanıtlarında bulunmamasını istiyorum.
18. Atanmış iş arayan olarak yetki kazandığımda tam iş adresini görmek istiyorum.
19. Şirket işvereni olarak tam işyeri adresini her ilan için ayrı bir tercihle herkese açmak istiyorum.
20. İşveren olarak ilanı yayımlamadan önce çalışan ücretini, Koala hizmet bedelini ve ödeyeceğim toplamı görmek istiyorum.
21. İşveren olarak yayımladığım ilanın fiyatının sonraki komisyon değişiminden etkilenmemesini istiyorum.
22. İş arayan olarak başvurumun kabulünü veya reddini, işveren olarak atama yanıtını gerçek API durumundan görmek istiyorum.
23. İşveren olarak tek çekim kart ve 3DS ile Iyzico Sandbox ödemesini uygulama içinden yapmak istiyorum.
24. İşveren olarak çift dokunma veya bağlantı hatasında aynı ödeme için ikinci tahsilat başlatılmamasını istiyorum.
25. Kullanıcı olarak ödeme doğrulanırken bekleme durumunu görmek; sonucu sonradan tekrar sorgulayabilmek istiyorum.
26. İşveren olarak checkout'u kapatınca ödemenin gerçekleşip gerçekleşmediğini backend'den öğrenmek istiyorum.
27. İş arayan olarak ödeme güvenceye alındığında check-in/check-out yapıp çalışma kaydımı görmek istiyorum.
28. Taraf olarak mesaj, okunma ve durum değişikliklerini diğer simülatörde görmek istiyorum.
29. Taraf olarak iptal önizlemesini, iptal/iade/uyuşmazlık ve geçmiş durumlarını doğru sonuçlarla görmek istiyorum.
30. Taraf olarak tamamlanan işte değerlendirme bırakmak ve backend'den tekrar okumak istiyorum.
31. Test yapan kişi olarak hesap, bağlantı ve sağlayıcı hatalarında boş ekran veya uydurma başarı yerine anlaşılır durum görmek istiyorum.
32. Proje sahibi olarak işleri ayrı commit ve mobil özellik PR'ları halinde incelemek istiyorum.

## Implementation Decisions

- Güncel ortam kararı ADR-0001'dir. Mevcut domain ve Dokploy altyapısı bugün test/pre-production'dır; şimdi ayrı staging kurulmaz. Lansman sırasında aynı altyapı temiz veritabanıyla production olur.
- Mobil kimlikler production için `com.iskoala.koala`, staging için `com.iskoala.koala.staging`, dev için `com.iskoala.koala.dev` olur. Staging mevcut internet API'sine, dev açıkça seçilen yerel backend'e bağlanır. Ortam eksikliği sessiz internet API varsayımına dönüşmez.
- İşveren ve iş arayan QA adresleri kullanıcı tarafından sağlandı; ilk adres işveren, ikinci adres iş arayan içindir. Adresler ve şifreler sürüm kontrollü doküman, kaynak kod veya mobil build'e gömülmez.
- Gerçek SMTP yalnız iki kontrollü QA alıcısına teslimat yapar. Diğer sentetik alıcıların gönderimleri SMTP'ye ulaşmadan engellenir; gönderilmeyen ileti başarılı teslimat gibi işaretlenmez. Mailpit kullanılmaz.
- Mevcut seed'in 150 kullanıcı, 30 şirket, 180 ilan hedefi korunur; bütün şirketler kullanıcı hesabına bağlıdır. Normal API ve gerçek tablolar kullanılır. Şirket işvereni ve bireysel işveren ayrımı korunur.
- Seed sürümlü, idempotent ve açıkça çalıştırılan tek seferlik işlemdir. Tekrar çalıştırma manuel testte ilerletilmiş kayıtları yeniden başlangıca çekmez. Normal backend açılışı seed çalıştırmaz. Q36-C gereği kayıt başına `testData` veya `seedRunId` eklenmez; reset yalnız test veritabanı bütününde, ayrı ve doğrulanmış operasyondur.
- Sentetik finansal/provider kimlikleri gerçek Iyzico çağrısına gönderilmez. Ödeme başarısı ve para hareketi yalnız gerçek Sandbox yanıtıyla oluşur. Mevcut doğrudan seed edilen finansal kayıtların nasıl kaldırılacağı/yenileneceği veri paketinin kabul ölçütüdür; tüm durum matrisi sağlayıcı testlerinin kanıtından ayrı tutulur.
- Runtime demo girişleri, hardcoded hesaplar, fake servis veya test API'si bulunmaz. Test doubles sadece test katmanında kalır.
- Google anahtarları ortam × iOS/Android/backend bazında ayrılır. Backend servis anahtarı mobil uygulamaya verilmez. Eksik veya placeholder iOS anahtarı giderilmeden beyaz harita düzelmiş sayılmaz.
- Adres akışı backend autocomplete → place çözümleme → kullanıcı tarafından adres/pin onayı → kalıcı adres şeklindedir. İzin reddi aramayı kapatmaz. Manuel adres/pin alternatifi bulunur.
- Ülke/il/ilçe ilişkileri backend'deki sabit kimliklerle doğrulanır; serbest adres metni, mahalle ve adres detayı açık alanlarda tutulur. Profil adresi değişikliği ilanın yayın adresini sessizce değiştirmez.
- Herkese açık yanıtlar yaklaşık konumu döndürür; tam koordinat sadece UI'da gizlenmez. Tam adresin atamaya bağlı açılıp terminal durumda gizlenmesi ve şirketin ilan bazlı açık adres tercihi ADR-0003'e uyar; iç içe DTO'lar da aynı kurala tabidir.
- Fiyat yayın sırasında kilitlenir. Atama ve ödeme denemesi aynı snapshot'ı devralır. Backend para hesabının tek kaynağıdır; mobil para alanlarını yeniden hesaplayıp üzerine yazmaz.
- İlk sürümde işveren toplamı çalışan ücreti + Koala hizmet bedelidir; Iyzico işlem maliyeti Koala'ya aittir. Q37 henüz kesin vergi kararı değildir: test sunumu A, ayrı KDV hesaplaması ertelenmiştir.
- Ödeme başlatma eşzamanlı çağrı ve tekrarlar için idempotent olur. Belirsiz initialize/retrieve sonucu yeni tahsilatı açmaz. İmza ve ödeme kimliği/tutar eşleşmeleri doğrulanır; ödeme denemesi ve durum uçları nesne seviyesinde yetkilendirilir.
- İzole checkout WebView'i JWT veya native bridge taşımaz. İlk URL doğrulanır, 3DS için HTTPS yönlendirmeleri desteklenir, tam Koala dönüş adresi eşleşmesi navigasyonu bitirir. Dönüş parametresi finansal sonuç sayılmaz; kapatma da tek başına iptal sayılmaz.
- Mobil sonuç ekranı authenticated backend ödeme durumunu sınırlı aralıklarla sorgular. Callback, uygulamanın yeniden açılması ve manuel kontrol aynı doğrulanmış sonucu gösterir.
- Backend main üzerinde kalır. Mobil özellikleri ayrı branch/PR paketleridir. Mevcut commitlenmemiş değişiklikler korunup ait oldukları pakete dahil edilir. Commit, push, tag ve deploy sınırları kullanıcıya somut değişiklik/test özetiyle sunulur.

## Testing Decisions

Önerilen ana test sınırı kullanıcıya açık API davranışı ve mobil kullanıcının gözlediği akıştır. Dahili method çağrı sırası testin hedefi değildir.

- Backend: mevcut Spring Boot HTTP/servis entegrasyon düzeni ve gerçek PostgreSQL Testcontainers ile rol, sahiplik, kalıcılık, para, idempotency ve adres gizliliği sınanır.
- Mobil: mevcut servis/provider ve widget testlerinde gerçek DTO sözleşmesi, loading/error/empty ayrımı, oturum, adres seçimi ve backend fiyat sunumu sınanır.
- Saf URL/navigasyon kuralları ve fiyat yuvarlama gibi hesaplar küçük davranış testleriyle kapsanır; dış sağlayıcı arızaları deterministik test-only doubles ile üretilir.
- Gerçek SMTP teslimatı yalnız iki QA gelen kutusunda; Google haritası gerçek cihaz görünümünde; Iyzico Sandbox tahsilatı hosted form → callback → doğrulanmış backend sonucu üzerinden kabul edilir.
- Son kabul: iki iPhone simülatöründe aynı backend'e karşı işveren/iş arayan akışı. Uygulama yeniden açılarak kayıtların DB'de kaldığı doğrulanır.
- Tüm anlamlı başvuru/atama/ödeme/iptal/uyuşmazlık durumları matriste listelenir. Her satırda API/UI sonucu ve kanıt türü (gerçek Sandbox, DB kaydı veya otomatik hata testi) açıkça yazılır; sentetik satır gerçek sağlayıcı başarısı sayılmaz.
- Mobil native değişiklikler için ilgili platform build'i; mantık değişikliği için hedef testler ve son paket kontrolünde tam test/analiz çalışır.
- Production'a geçildikten sonra ADR-0005'teki kademeli smoke uygulanır. Lansman reseti gündelik test veya CI tarafından otomatik tetiklenmez.

## Out of Scope

- Bugün yeni staging domaini/altyapısı kurmak veya mevcut veritabanını sıfırlamak.
- Kesin KDV oranı, matrahı, fatura uygulaması veya ticari lansman mali kararı.
- NFC kimlik projesini öne çekmek; mevcut bilinçli ara kimlik akışını değiştirmek.
- Bu test aşamasında canlı para tahsilatı veya mağazaya production uygulama yayını.
- Kullanıcının ayrıca yönlendirmediği yeni ürün özellikleri; mevcut akışları çalıştırmanın dışındaki yeniden tasarımlar.

## Further Notes

Backend ana kayıt: https://github.com/is-koala/koala-backend/issues/1

Mobil ana kayıt: https://github.com/is-koala/koala/issues/4

Mevcut beyaz harita kaydı: https://github.com/is-koala/koala/issues/5 — yeni bir kopyası açılmayacak.

Önceki araştırmalardaki “hemen ayrı staging” ve Mailpit önerileri son kullanıcı kararlarıyla değiştirilmiştir. Güncel ADR'lar ve bu sentez önceliklidir. Sağlayıcı kurulum girdileri hazırlanması, onların runtime'a bağlandığı veya gerçek E2E testinin geçtiği anlamına gelmez.
