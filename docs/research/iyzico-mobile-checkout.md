# Flutter + Spring Boot icin iyzico Checkout Form arastirmasi

> Karar güncellemesi (5 Eylül 2026): Yayında fiyat kilidi, Koala'nın üstlendiği Iyzico işlem maliyeti, izole uygulama içi checkout ve tek çekim/3DS seçildi. Q37 vergi kararı açık; test sürümünde ayrı KDV hesaplanmaz. Araştırmadaki alternatifler yerine güncel [uygulama sentezi](../plans/real-api-test-flow.md) ve ADR-0002/0004 esas alınır.

Tarih: 4 Eylul 2026

## Amac ve karar ozeti

Bu belge Koala'da isveren odemesinin Safari'ye cikmadan uygulama icinde tamamlanmasi, odeme sonucunun guvenilir bicimde backend'den okunmasi ve isverenin odemeden once tum fiyat kirilimini gormesi icin uretim mimarisini tarif eder.

Onerilen yon:

1. Kart formunu Koala'nin kendisi uretmemeli. Backend iyzico Checkout Form oturumunu acmali; mobil uygulama yalnizca iyzico'nun `paymentPageUrl` degerini sistem WebView'i icinde gostermeli.
2. Mobil uygulama yonlendirme URL'sindeki `status` degerine guvenmemeli. Yonlendirme yalnizca "form bitti, backend'e sor" sinyalidir; kesin sonuc backend'deki odeme denemesinden okunmalidir.
3. Backend callback'i yalnizca tetikleyici kabul etmeli, token ile iyzico Checkout Form Retrieve cagrisini yapmali, donen yanitin imzasini ve tum finansal kimlik/tutar alanlarini dogrulamalidir.
4. iyzico API'lerinin cogunlugu idempotent olmadigi icin Koala kendi idempotency katmanini kurmali; ayni mobil istegin tekrari ikinci bir iyzico oturumu olusturmamalidir.
5. Is ilani ucreti "calisana odenecek tutar" olarak tanimlanmali. Koala hizmet bedeli ve isveren toplam odemesi backend'de hesaplanip ilan/atama/odeme denemesi uzerinde surumlu finansal snapshot olarak saklanmalidir. Mobil bu tutarlari hesaplamamali.
6. Iyzico hosted sayfasinin dogrudan yonlendirme modu kullanilmali. `popup` modu ve `&iframe=true` secenegi native WebView icinde gereksiz ikinci pencere/cerez karmasasi yaratir.

Bu sonuc, iyzico'nun Checkout Form'u mobil uygulamalarda kullanilabilen hosted bir odeme sayfasi olarak tanimlamasina ve akisi Initialize + Retrieve olarak belgelemesine dayanir. `paymentPageUrl` dogrudan yonlendirme icin verilen URL'dir; `token` ise Retrieve icin zorunludur. Callback URL'si gecerli SSL sertifikali olmalidir. Kaynaklar: [iyzico Checkout Form API](https://docs.iyzico.com/en/getting-started/preliminaries/api-reference-beta/payment-methods/checkoutform), [iyzico CF implementation](https://docs.iyzico.com/en/payment-methods/checkoutform/cf-implementation), [iyzico Checkout Form urun sayfasi](https://docs.iyzico.com/en/payment-methods/direct-charge/checkoutform).

## Resmi kaynaklardan kesinlesen davranislar

### Checkout ve sonuc dogrulama

- Checkout Form iki finansal adimdan olusur: Initialize ile oturum acilir, Retrieve ile token kullanilarak sonuc alinir. `conversationId` istek/yanit korelasyonu, `basketId` is emri/atama korelasyonu icin kullanilabilir. [iyzico Checkout Form API](https://docs.iyzico.com/en/getting-started/preliminaries/api-reference-beta/payment-methods/checkoutform)
- `paymentPageUrl` hosted ortak odeme sayfasidir. iyzico dogrudan yonlendirmeyi en yaygin kullanim olarak, iframe kullanimini ise URL'ye `&iframe=true` eklenen ayri bir secenek olarak anlatir. Native WebView icin dogrudan URL yuklemek bu iki secenekten daha sade olanidir. [iyzico CF implementation](https://docs.iyzico.com/en/payment-methods/checkoutform/cf-implementation)
- Retrieve yanitinda `paymentStatus`, `fraudStatus`, `price`, `paidPrice`, `currency`, `basketId`, `conversationId`, `token`, `paymentId` ve kalem islemleri bulunur. `fraudStatus=1` onay, `0` inceleme, `-1` ret anlamina gelir; `0` iken hizmet baslatilmamali/teslim edilmemelidir. [iyzico Checkout Form API](https://docs.iyzico.com/en/getting-started/preliminaries/api-reference-beta/payment-methods/checkoutform), [iyzico fraud notifications](https://docs.iyzico.com/en/advanced/fraud-notifications)
- iyzico, finansal API yanitlarindaki `signature` alaninin HMAC-SHA256 ile dogrulanmasini ister. Checkout Form Initialize icin sirali alanlar `conversationId, token`; Retrieve icin `paymentStatus, paymentId, currency, basketId, conversationId, paidPrice, price, token` olarak belgelenmistir. [iyzico response signature validation](https://docs.iyzico.com/en/advanced/response-signature-validation)
- Hosted sayfa webhook'lari `X-IYZ-SIGNATURE-V3` ile dogrulanabilir. HPP/CF formatinda imza girdileri `secretKey, iyziEventType, iyziPaymentId, token, paymentConversationId, status` sirasindadir. Webhook ilk denemeden yaklasik 10-15 saniye sonra gelebilir ve 2xx almazsa iyzico tekrar dener. [iyzico webhook](https://docs.iyzico.com/en/advanced/webhook)

### Idempotency ve marketplace tutarlari

- iyzico, servislerinin cogunun bilerek non-idempotent oldugunu belirtir. `conversationId`/`basketId` korelasyon icindir; bunlarin varligi yinelenen initialize cagrisini iyzico tarafinda otomatik olarak tekilleştirme garantisi vermez. Hosted odeme token'i her istekte yeniden uretilir. [iyzico idempotency](https://docs.iyzico.com/en/getting-started/preliminaries/idempotency)
- Marketplace odemesinde her sepet kalemi `subMerchantKey` ve `subMerchantPrice` tasir. `subMerchantPrice` alt uye isyerine aktarilacak tutardir; sepet kalemlerinin toplami `price` ile uyumlu olmalidir. [iyzico marketplace online payment](https://docs.iyzico.com/en/products/marketplace/marketplace-implementation/online-payment)
- iyzico'nun Retrieve yanitindaki `merchantCommissionRate` ve `iyziCommission*` alanlari, Koala'nin isverene uyguladigi platform/hizmet bedeliyle ayni kavram degildir. API/UI isimlerinde Koala bedeli `platformFee` veya `serviceFee` diye ayrilmalidir. [iyzico Checkout Form API](https://docs.iyzico.com/en/getting-started/preliminaries/api-reference-beta/payment-methods/checkoutform)

### Flutter WebView ve platform guvenligi

- Flutter ekibinin resmi `webview_flutter` paketi iOS'ta `WKWebView`, Android'de sistem `WebView` kullanir. `WebViewController`, `WebViewWidget`, `JavaScriptMode.unrestricted`, `NavigationDelegate` ve `loadRequest` resmi API'lerdir. Checkout Form JavaScript ile calistigi icin JavaScript acik olmalidir. [webview_flutter](https://pub.dev/packages/webview_flutter)
- `NavigationDelegate.onNavigationRequest` navigasyonu kabul/engellemek, `onPageStarted/onPageFinished/onProgress` yukleme durumunu izlemek icindir. `onWebResourceError` alt frame hatalarini da bildirebildiginden yalniz ana frame hatalari kullaniciya terminal hata gibi gosterilmelidir. [webview_flutter](https://pub.dev/packages/webview_flutter), [NavigationDelegate API](https://pub.dev/documentation/webview_flutter/latest/webview_flutter/NavigationDelegate-class.html)
- Android guvenlik dokumani WebView'e girecek URI'nin parse edilip hem scheme hem host alaninin tam eslesmeyle kontrol edilmesini; `contains`, eksik `endsWith` veya yalniz host kontrolu yapilmamasini ister. `file:`, `content:`, `javascript:` ve HTTP gibi beklenmeyen semalar reddedilmelidir. [Android unsafe URI loading](https://developer.android.com/privacy-and-security/risks/unsafe-uri-loading)
- Native JavaScript bridge odeme icin gerekli degildir ve ek saldiri yuzeyi olusturur. Koala checkout WebView'i `addJavaScriptChannel` kullanmamali, uygulama JWT'sini header/cookie/JavaScript ile sayfaya vermemeli, dosya ve cihaz izinlerini reddetmelidir. [Android WebView native bridge security](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges), [Android unsafe file inclusion](https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion)
- iOS App Transport Security varsayilan olarak guvenli TLS ve gecerli sertifika ister. iyzico ve Koala callback'i HTTPS oldugu icin `NSAllowsArbitraryLoads` veya `NSAllowsArbitraryLoadsInWebContent` acilmamalidir. [Apple ATS](https://developer.apple.com/documentation/security/preventing-insecure-network-connections)
- Hosted odeme sayfasina URL redirect etmek, kart alanlarini Koala yerine odeme saglayicisinin urettigi sayfada tutar. Bu PCI kapsam riskini azaltir fakat PCI yukumlulugunu otomatik olarak sifirlamaz; kesin SAQ kapsami odeme saglayicisi/QSA ile teyit edilmelidir. [PCI SSC FAQ 1291](https://www.pcisecuritystandards.org/faqs/1291/)

## Mevcut Koala akisi: iyi kisimlar ve bosluklar

### Dogru kurulmus kisimlar

- `JobAssignmentManager.initiatePaymentSession` fiyatlari backend'de `CommissionCalculator` ile hesapliyor; mobil tutar gondermiyor.
- `Payment` kaydi `workerPayout`, `commissionAmount`, `commissionRate`, `totalCharged` ve `currency` snapshot'ini DB'de sakliyor.
- Her denemeye ayri rastgele `conversationId` veriliyor.
- Callback, mobil/browser `status` degerini finansal sonuc saymiyor. Yerel token kaydini bulup iyzico Retrieve cagrisi yapiyor.
- `PaymentResultPage` URL'deki status ipucunu gostermek yerine authenticated assignment listesini yeniden okuyor.
- Retrieve sonrasi `paidPrice`, `currency` ve `fraudStatus` kontrol ediliyor; `fraudStatus=0` emaneti acmiyor.

### P0: odeme oncesi duzeltilmesi gerekenler

1. **Initialize ve Retrieve yanit imzalari dogrulanmiyor.** `IyzicoPaymentService.call` yalniz JSON `status=success` kontrol ediyor. Resmi response-signature sirasi uygulanmali; imza tutmazsa finansal durum ilerletilmemeli.
2. **Ayni anda birden fazla aktif checkout acilabilir.** `payment-session` isteginde idempotency anahtari yok; assignment satiri kilitlenmiyor; iyzico initialize non-idempotent. Cift tik, timeout sonrasi retry veya iki cihaz iki ayri kullanilabilir token uretebilir.
3. **Belirsiz sonuc ikinci tahsilata donebilir.** Retrieve ag hatasi mevcut kodda `FAILED` yapiliyor. Iyzico karti cekmis ama Koala sonucu okuyamamis olabilir; UI yeniden odemeye izin verirse cift tahsilat riski dogar. Ag/5xx/parse/imza hatasi `VERIFY_PENDING` gibi ayri bir belirsiz durumda kalmali ve yeni checkout kapali olmali.
4. **Retrieve kimlik eslestirmesi eksik.** Basarili sonuc icin token, `conversationId`, `basketId`, `price`, `paidPrice`, currency, kalem id/price, `subMerchantKey` iliskisi ve `subMerchantPrice` yerel snapshot ile tam eslesmelidir. Bugun yalniz `paidPrice` ve currency karsilastiriliyor.
5. **Odeme denemeleri ucunda nesne-seviyesi yetki yok.** SecurityConfig rol kontrolu yapiyor fakat `PaymentManager.getAttemptsForAssignment` assignment'in mevcut kullaniciya ait olup olmadigini kontrol etmeden DAO listesini donuyor. Controller aciklamasi 404 vaat ediyor, uygulama bunu gerceklestirmiyor. Isveren/calisan/ADMIN katilimci kontrolu eklenmeden bu uc mobile baglanmamali.
6. **`provider_token` DB'de unique degil.** Callback lookup tek kayit bekliyor; kolon/index unique constraint ile korunmali.

### P1: urun ve entegrasyon bosluklari

1. Mobil `_startPayment`, `LaunchMode.externalApplication` kullaniyor; bu nedenle iOS'ta Safari'ye/harici tarayiciya cikiyor. `webview_flutter` henuz dependency degil.
2. `PaymentSessionDto` mobile hem URL hem token veriyor. Mobil Retrieve yapmayacaksa token'i ayri alan olarak vermek gereksizdir; yalniz `paymentAttemptId`, hosted URL, return URL bilgisi ve fiyat snapshot'i yeterlidir. Token backend'de kalmalidir.
3. `MyAssignmentDto` yalniz `workerPayout` ve `totalCharged` tasiyor; `commissionAmount`, `commissionRate` ve currency yok. Dahasi bu alanlar assignment'a ancak basarili callback'te yaziliyor. Isveren odemeden **once** kirilimi goremez.
4. Yeni ilan ekraninda yalniz "Ucret" alani var; bunun calisan ucreti mi isveren toplami mi oldugu soylenmiyor ve hizmet bedeli/toplam gostergesi yok.
5. Backend `https://iskoala.com/payment/result?...` yoluna yonlendiriyor; mobil `AppLinkDestination` ise `payment-result` ariyor. Yol birebir uyusmuyor.
6. Android manifest yalniz `www.iskoala.com` hostunu dinliyor; backend `iskoala.com` kullaniyor. iOS projesinde Associated Domains entitlement dosyasi yok. Dolayisiyla uygulama disinda kalan fallback linki bugun guvenilir degil.
7. `PaymentResultPage` tek kez sorguluyor ve sonra manuel "Tekrar kontrol et" istiyor. Callback/webhook birkac saniye gecikirse kisa, sinirli ve backoff'lu otomatik polling daha akici olur.

## Onerilen uretim akisi

```text
Mobil                 Koala API/DB                    iyzico
  |  GET payment quote     |                            |
  |----------------------->| server pricing snapshot    |
  |<-----------------------| tam fiyat kirilimi         |
  |                        |                            |
  | POST payment-session   |                            |
  | Idempotency-Key        | lock + get/create attempt  |
  |----------------------->| CF Initialize ------------>|
  |                        | verify response signature  |
  |<-----------------------| URL + attemptId + pricing  |
  |                        |                            |
  | WebView: paymentPageUrl --------------------------->|
  |                        |<--------- callback(token)  |
  |                        | CF Retrieve -------------->|
  |                        |<-- signed financial result |
  |                        | verify + reconcile + commit|
  |                        |                            |
  | intercept return URL / poll attempt status          |
  |----------------------->|                            |
  |<-----------------------| authoritative status       |
```

Callback, webhook ve manuel/scheduled reconciliation ayni tek `reconcileAttempt(...)` servis metoduna girmeli. Her yol ayni satir kilidi, imza kontrolleri, kimlik/tutar kontrolleri ve state machine'i kullanmalidir.

### 1. Fiyat kirilimi sozlesmesi

`Job.salary` bugunku hesapta fiilen calisan payout'udur. Bunu API ve UI'da acikca `workerPayout` diye adlandirmak gerekir. Onerilen yanit:

```json
{
  "pricingVersion": "2026-09-01-v1",
  "currency": "TRY",
  "workerPayout": "1000.00",
  "serviceFeeRate": "0.0750",
  "serviceFeeAmount": "75.00",
  "employerTotal": "1075.00"
}
```

Kurallar:

- Isveren ilan olustururken alan etiketi "Calisana odenecek ucret" olmali; altinda "Koala hizmet bedeli" ve "Odeyecegin toplam" canli gorunmeli.
- Canli preview backend'den alinabilir; mobilin hesapladigi gecici gorunum olsa bile yayinlama ve odeme icin baglayici degildir.
- Ilan yayinlanirken backend hesaplamayi tekrar yapip `pricingVersion`, rate, fee, payout, total ve currency degerlerini DB'de ilan fiyat snapshot'i olarak saklamalidir. Komisyon ayari sonradan degisse bile yayinlanmis ilanin isveren toplami sessizce degismemelidir. Alternatif urun karari dinamik fiyat ise, degisiklik odeme oncesi acikca yeniden onaylatilmalidir.
- Atama olusurken ilan snapshot'i atamaya, payment attempt olusurken atama snapshot'i payment kaydina kopyalanmalidir. Iyzico istegi yalniz payment snapshot'indan uretilmelidir.
- Bugunku `CommissionCalculator` para degerini 2 haneye, `HALF_UP` ile yuvarliyor ve fee'yi yuvarladiktan sonra topluyor; bu tek kalemli TRY akisi icin tutarli bir kuraldir. Kural testlerle sabitlenmeli ve UI'da daima backend degerleri gosterilmelidir.
- Finansal JSON alanlari Dart `double` ile tekrar hesaplanmamalidir. Wire contract'ta decimal string veya kurusun integer minor-unit degeri secilmeli; tum iki repo dokumani ayni formati kullanmalidir.
- Koala hizmet bedeli ile iyzico islem maliyeti ayri muhasebe alanlaridir. Isveren UI'i, hangi bedelin kendisine yansitildigini acikca gostermelidir.

Odeme oncesi okunacak uc icin en basit sozlesme `GET /job-assignments/{id}/payment-quote` olur: tutari mobil gondermez, backend assignment/ilan snapshot'ini doner. Ilan formundaki canli onizleme icin ayri `POST /payment-quotes` hesaplama ucu kullanilabilir; fakat yayinlama cevabindaki persisted snapshot baglayicidir.

### 2. Idempotent session acma

- Mobil her kullanici odeme aksiyonu icin UUID tabanli `Idempotency-Key` uretir; timeout/ag tekrarinda ayni anahtari yeniden kullanir. Yeni bir kullanici aksiyonu yeni anahtar alir.
- Backend `payment_attempts.idempotency_key` degerini unique saklar; `assignment_id + key` icin ayni session response'u dondurur.
- Assignment satiri pessimistic lock veya DB seviyesinde esdeger atomik kural ile kilitlenir. Bir assignment'ta yalniz bir `INITIALIZING/READY/VERIFY_PENDING/IN_REVIEW` denemesi olabilir.
- Provider cagrisindan once yerel deneme kalici hale getirilir. Initialize timeout'unda deneme `VERIFY_PENDING`/`INIT_UNKNOWN` kalir; otomatik olarak ikinci session acilmaz. Operasyon ekibi/reconciliation bu denemeyi cozer.
- Iyzico token ve payment URL'sinin gecerlik suresi resmi CF yanitinda kesin bir alan olarak alinabiliyorsa DB'de saklanir; aksi halde Koala kendi kendine token suresi uydurmamalidir.
- Initialize yaniti `status`, signature, conversationId, token varligi ve `paymentPageUrl` icin HTTPS + beklenen iyzico hostu ile dogrulanmadan mobile donulmez.

### 3. Callback, webhook ve status

- Public callback token'i loglamaz. Token yerel payment kaydini bulmak icin kullanilir; callback'in `status` gibi baska alanlari finansal gercek kabul edilmez.
- Retrieve yanit signature'i dogrulanir, ardindan tum korelasyon/tutar alanlari snapshot ile karsilastirilir. Ancak `paymentStatus=SUCCESS && fraudStatus=1` ise escrow acilir.
- `fraudStatus=0` `IN_REVIEW`, gercek ret `FAILED`, ag/timeout/5xx/imza sorunu `VERIFY_PENDING` olur. `VERIFY_PENDING` ve `IN_REVIEW` yeni tahsilata izin vermez.
- Callback yinelenirse terminal kayit tekrar degismez. Eszamanli callback/webhook ayni payment satir kilidinden gecmelidir.
- Ayrica imzali HPP webhook'u etkinlestirilmelidir. Webhook `iyziReferenceCode`/olay kimligiyle dedupe edilmeli ve 2xx yalniz olay kalici olarak kaydedildikten veya zaten islendigi goruldukten sonra donmelidir.
- `GET /job-assignments/{id}/payment-status` veya latest-attempt ucu authenticated katilimci kontrolu yapmali ve mobile normalize durumlar donmelidir: `READY`, `PROCESSING`, `IN_REVIEW`, `SUCCEEDED`, `FAILED`, `CANCELLED`. Ham provider hata metni istemciye verilmemelidir.
- Periyodik reconciliation, uzun sure `VERIFY_PENDING/IN_REVIEW` kalan kayitlari iyzico Retrieve/Reporting uzerinden yeniden kontrol etmelidir. iyzico Reporting API guncel odeme ve fraud durumlarini sorgulamak icin tasarlanmistir. [iyzico reporting service](https://docs.iyzico.com/en/advanced/reporting-service)

### 4. Uygulama ici WebView

Mobilde yeni tam ekran bir `PaymentCheckoutPage` acilmalidir:

- Giris parametreleri yalniz dogrulanmis `paymentPageUrl`, `paymentAttemptId`, exact Koala return URL scheme/host/path ve gosterilecek backend pricing snapshot'i olur.
- Sayfanin ustunde Koala app bar, kapat butonu ve yukleme gostergesi bulunur. Fiyat kirilimi WebView acilmadan once onay ekraninda bir kez daha gosterilir.
- `WebViewController` JavaScript'i acar, `NavigationDelegate` kurar ve URL'yi GET ile yukler. Koala JWT'si WebView'e eklenmez; JavaScript channel/native bridge kurulmaz.
- Ilk URL yalniz `https` ve konfigurasyondaki tam iyzico hostuyla kabul edilir. Koala return URL'si scheme + host + path tam eslesmeyle yakalanir; `contains('payment-result')` kullanilmaz.
- Return URL yakalaninca navigasyon engellenir, WebView kapanir ve authenticated status ucu sorgulanir. URL'deki `status` sadece telemetri/ipucu olabilir, kullaniciya sonuc olarak gosterilmez.
- Callback navigasyonu gorulmese bile WebView acikken dusuk frekansli, sinirli polling; uygulama foreground'a donunce bir sorgu; kullanici kapatinca bir son sorgu yapilir. "Kapat" odemeyi iptal etmis sayilmaz.
- HTTP, `file:`, `content:`, `javascript:` ve bilinmeyen uygulama semalari reddedilir. `tel:`/`mailto:` gibi yardimci linkler gerekiyorsa kart/oturum token'i tasimadigi dogrulanarak acik politika ile ele alinir.
- Checkout Form ve 3DS JavaScript/cerez/DOM storage kullanabilir; session sirasinda cookie veya local storage temizlenmez. Odeme bittikten sonra checkout'a ozel web verisinin temizlenmesi test edilmelidir; `webview_flutter` cache ve local storage'i ayri API'lerle temizler.
- Release build'de WebView debugging kapali kalir. Sertifika/SSL hatalari fail-closed davranir; kullaniciya tekrar deneme yerine once backend status kontrolu sunulur.
- Form icinde kamera, mikrofon, konum veya dosya secme izni istenirse varsayilan cevap ret olmalidir; mevcut Checkout Form akisi bunlara ihtiyac duymaz.

**3DS hostlari icin acik karar:** Android tam scheme/host dogrulamasi onerir, ancak iyzico dokumani bankalarin ACS domainleri icin eksiksiz ve sabit bir allowlist yayinlamiyor. Uretime cikmadan once iyzico'dan desteklenen domain/navigasyon politikasi alinmali. Bu liste edinilemiyorsa, yalniz bu izole, bridgesiz, JWT'siz checkout WebView'inde HTTPS top-level navigasyonlara izin verilmesi bilincli bir risk karari olarak ADR'a yazilmali; rastgele tahmin edilen banka listesiyle 3DS kirilmamalidir.

**Yeni pencere/popup karari:** iyzico'nun `popup` sunum modu secilmemeli; dogrudan hosted URL kullanilmalidir. 3DS/test senaryolarinda `target=_blank` veya yeni pencere talebi gozlenirse iOS `WKUIDelegate`/Android WebChromeClient davranisi ayrica ele alinmalidir. Apple, yeni pencere talebini `WKUIDelegate` ile uygulamanin yonetmesini belgeler. [Apple WKUIDelegate](https://developer.apple.com/documentation/webkit/wkuidelegate)

### 5. App Link / Universal Link fallback

Uygulama ici WebView'in normal tamamlama yolu `NavigationDelegate + backend status` olmalidir. App/Universal Link su durumlar icin fallback'tir: isletim sistemi WebView disina cikardi, kullanici harici tarayicida acti veya uygulama callback sirasinda kapaliydi.

- Tek kanonik URL belirlenmeli; ornegin `https://iskoala.com/payment/result/{attemptId}`. Backend, Android manifest, iOS entitlement, AASA/assetlinks ve Dart route parser ayni host/path'i kullanmalidir.
- iOS'ta `applinks:iskoala.com` Associated Domains capability ve `https://iskoala.com/.well-known/apple-app-site-association` gerekir. Apple farkli subdomainlerin ayri ayri tanimlanmasini ve AASA'nin HTTPS, gecerli sertifika ve redirect olmadan sunulmasini ister. [Apple associated domains](https://developer.apple.com/documentation/Xcode/supporting-associated-domains)
- Android'de manifest `android:autoVerify=true`, kanonik host/path ve `https://iskoala.com/.well-known/assetlinks.json` gerekir; dosyadaki package ve release imza SHA-256 eslesmelidir. [Android App Links](https://developer.android.com/training/app-links/about), [Flutter Android App Links rehberi](https://docs.flutter.dev/cookbook/navigation/set-up-app-links)
- Web fallback sayfasi uygulama yuklu degilse "Odeme sonucunu kontrol et" ekrani gostermeli; query status ile basari ilan etmemeli.

## Guvenlik ve loglama kontrol listesi

- Iyzico API key/secret yalniz backend secret store/environment icinde; mobile veya tracked config'e girmez.
- JWT, refresh token, iyzico provider token, `paymentPageUrl`, kart verisi, CVV, tam BIN, callback body ve response body loglanmaz.
- Loglarda yerel `paymentAttemptId`, assignmentId, conversationId, sonuc sinifi, sure ve correlationId yeterlidir. Provider hata mesaji allowlist edilmis hata koduna cevrilir.
- Mobile'a kart/banka detaylari veya ham iyzico cevabi donulmez. Gerekiyorsa yalniz maskeli kart son dordu ve provider paymentId yetkili operasyon ekraninda bulunur.
- Provider token DB'de erisim kontrollu tutulur ve unique constraint ile korunur. Veri saklama/temizleme suresi finansal mutabakat gereksinimiyle belgelenir.
- Callback/webhook endpointlerine boyut siniri, beklenen content type, token uzunluk/formati, rate limit ve olay tekrar korumasi uygulanir. Webhook signature karsilastirmasi constant-time yapilir.
- Mobil initial/return URL dogrulamasi exact scheme/host/path ile yapilir. Koala API hostu ile frontend hostu ortam bazli config'ten gelir; sandbox/prod birbirine karismaz.
- ATS veya Android cleartext icin genis istisna acilmaz. Sandbox dahil HTTPS kullanilir.
- Hosted sayfa kart alanlarini iyzico'ya birakir; yine de guncel PCI SAQ kapsami iyzico/QSA ile yazili teyit edilir.

## TDD ve asamali teslim plani

Her asama ayri review/commit siniri olabilir. Bu belge hicbir commit veya push islemi yapmaz.

### Asama 1 — Finansal terimler ve quote

Backend testleri:

- `1000.00 TRY` icin payout `1000.00`, rate `0.0750`, fee `75.00`, total `1075.00`.
- Yarim kurus sinirlari ve 2 hane `HALF_UP` davranisi.
- Eski ilanin fiyat snapshot'i komisyon konfigurasyonu degisince degismiyor.
- Isveren olmayan kullanici quote/attempt bilgisi okuyamiyor; kaynak varligini sizdirmamak icin 404.

Mobil testleri:

- Create job formu backend quote degerlerini etiketleriyle gosteriyor.
- Mobil yerel hesap ile backend rakamini override etmiyor.
- Quote ulasilamazsa ilan yayini backend onayi olmadan "tahmini toplam" diye kesinlestirilmiyor.

### Asama 2 — Idempotent session ve saglam reconciliation

Backend testleri:

- Ayni `Idempotency-Key` ile N tekrar yalniz bir iyzico Initialize cagrisi yapiyor ve ayni attempt'i donduruyor.
- Iki eszamanli farkli key yalniz bir aktif checkout uretiyor.
- Initialize/ Retrieve timeout'u `VERIFY_PENDING`; yeni tahsilat kapali.
- Initialize ve Retrieve signature hatasi state'i ilerletmiyor.
- Token/conversation/basket/price/paidPrice/currency/kalem/submerchant uyusmazliklarinin her biri escrow'u engelliyor.
- Callback ve webhook'un eszamanli/tekrarli gelisi tek state transition ve tek settlement outbox olayi uretiyor.
- `fraudStatus=0` incelemede kaliyor; `1` escrow aciyor; `-1` reddediyor.

### Asama 3 — Uygulama ici checkout

Mobil testleri:

- Session response icindeki HTTP, yanlis host ve bozuk URL reddediliyor.
- Exact return URL navigasyonu engelliyor, WebView'i kapatiyor ve backend status sorguluyor.
- `?status=success` ama backend pending/failed ise basari gosterilmiyor.
- Kullanici geri/kapat yapinca once status kontrol ediliyor; kapanis otomatik iptal/failed sayilmiyor.
- Ana-frame yukleme hatasi dogru hata durumunu, alt-frame hatasi ise terminal olmayan telemetriyi uretiyor.
- Safari/harici tarayici checkout normal akista hic cagrilmiyor.

### Asama 4 — Link fallback ve gercek sandbox E2E

Platform test matrisi:

- iOS simulator + gercek cihaz ve Android emulator + gercek cihaz.
- Sandbox basari, kart reddi, 3DS basari/ret, fraud review, kullanici formu kapatma.
- Callback once/sonra gelmesi, webhook tekrari, uygulamanin background/terminated olmasi.
- Initialize cevabi kaybolmasi, callback sirasinda ag kesilmesi, status polling sirasinda 401 refresh.
- `iskoala.com` ve gerekiyorsa `www.iskoala.com` icin verified link testi; uygulama kurulu degilken web fallback.
- Her senaryoda DB Payment/Assignment/Payout/Settlement kayitlari ve iyzico paneli birebir mutabik.

## Uygulama oncesi cevaplanmasi gereken urun kararları

1. Ilan yayinlandigi anda hizmet bedeli orani kilitlenecek mi? Oneri: evet; aksi kullaniciya odeme aninda fiyat degisikligi ve yeniden onay gerektirir.
2. Koala hizmet bedeline vergi dahil mi ve UI etiketi/fatura sahibi kim? Bu, hesap formulu ve hukuki metni etkiler.
3. Taksit aciksa `paidPrice` ilave vade farki tasiyabilecek mi, yoksa isveren ekranda gorulen total tam tahsilat mi olacak? Bugunku kod `price == paidPrice == totalCharged` gonderiyor ve birebir esitlik bekliyor.
4. EFT/havale/kredi gibi Checkout Form alternatifleri acik kalacak mi? Asenkron yontemler state machine ve polling suresini etkiler.
5. 3DS/banka domain politikasi icin iyzico'nun resmi destek matrisi alinabilecek mi?

Bu kararlar netlesmeden UI metni ve state enum'lari kalici hale getirilmemelidir; ancak hosted WebView, backend-authoritative status ve yerel idempotency kararlari bunlardan bagimsiz olarak uygulanabilir.
