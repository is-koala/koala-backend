# Iyzico checkout'u platformun tarayıcı görünümünde aç

Durum: Kabul edildi — 6 Eylül 2026. [ADR-0004](0004-use-an-isolated-in-app-iyzico-checkout.md)'ün yerine geçer.

## Karar

Iyzico hosted checkout, uygulamanın kendi WebView'ında değil, **platformun tarayıcı görünümünde** açılır: Android'de Custom Tabs, iOS'ta `SFSafariViewController` (`LaunchMode.inAppBrowserView`). Platform bunu desteklemiyorsa akış **başarısız döner**; harici tarayıcıya sessizce düşülmez.

Açılan ilk adres allowlist'ten geçer — yalnız `payment.iyzipay.com` ve `sandbox-payment.iyzipay.com`, yalnız HTTPS, kullanıcı bilgisi/port/fragment taşımadan. Bankanın 3DS yönlendirmeleri tarayıcı görünümünün içinde serbestçe akar; allowlist **sonraki** navigasyonlara uygulanmaz.

Dönüş, navigasyon yakalayarak değil, **`koala://payment-result` derin bağlantısıyla** olur. Finansal sonuç her zaman backend'in doğrulanmış ödeme durumundan okunur.

## Neden ADR-0004 değişti

ADR-0004 izole bir WebView öngörüyordu ve dönüşü *"yalnız tam Koala dönüş URL'si yakalanarak"* kapatmayı tarif ediyordu. Kod bunu yapmıyor ve **yapmaması daha doğru**:

**Custom Tabs / `SFSafariViewController` uygulamaya daha kapalı.** Bir WebView'ı barındıran uygulama JavaScript enjekte edebilir, çerezleri okuyabilir, DOM'a erişebilir. Custom Tabs edemez — süreç sınırının öte tarafındadır. Ödeme sayfası için bu, ADR-0004'ün "JWT veya native bridge taşımayan" amacını daha güçlü biçimde sağlar: taşımamakla kalmaz, **taşıyamaz**.

**Kullanıcı gerçek adresi görür.** Tarayıcı görünümü adres çubuğunu ve kilit simgesini gösterir; WebView göstermez. Ödeme sayfasında bunun kimlik avına karşı değeri var ve WebView'da karşılığı yok.

**3DS allowlist ikilemi ortadan kalkıyor.** ADR-0004 kendi gerekçesinde bu gerilimi yazmıştı: bilinmeyen banka domainlerini katı allowlist etmek akışı bozar, etmemek izolasyonu zayıflatır. Tarayıcı görünümünde soru düşüyor — navigasyon zaten uygulamanın erişemediği bir yerde oluyor, dolayısıyla kısıtlamaya gerek yok. Allowlist yalnız **ilk** adrese uygulanıyor ve orada asıl işi görüyor: uygulamanın rastgele bir adresi ödeme sayfası diye açmasını engellemek.

Bu, ADR-0004'ün amacını terk etmiyor; aracını değiştiriyor. Amaç "uygulamadan Safari'ye çıkmadan, JWT taşımayan izole bir yüzey"di ve platform görünümü bunu WebView'dan daha iyi veriyor.

## Sonuçlar

- **Terk edilen ödeme tespit edilemiyor.** WebView kapanışını uygulama görürdü; tarayıcı görünümü kapanışını görmüyor. Karşılığı üç parçalı ve zaten kurulu: dönüş derin bağlantısı, sonucun backend'den okunması, ve kullanıcının elle yenileyebildiği "TEKRAR KONTROL ET" düğmesi. Yani "ödeme ekranında ne oldu" sorusunun cevabı istemcinin gözlemine değil sunucunun durumuna bağlı — ki ADR-0004 de finansal sonucu zaten oraya bağlamıştı.
- **Derin bağlantı sözleşmesi ödeme akışının parçası oldu.** `payment-result` host'u `app_link_destination.dart`'ta tanımlı; o sözleşme bozulursa ödeme dönüşü de bozulur. İkisi birlikte düşünülmeli.
- **Platform desteği bir önkoşul.** `inAppBrowserView` desteklenmiyorsa akış başlamıyor. Harici tarayıcıya düşmek kolay olurdu ama uygulamadan çıkmayı ADR-0004 bilinçle reddetmişti; o kısım korunuyor.

## Alternatifler

**ADR-0004'ün WebView'ı** yukarıdaki üç sebeple daha zayıf; ayrıca 3DS akışlarında banka sayfalarının WebView'da bozulması yaygın bir sorun.

**Harici tarayıcı** (`LaunchMode.externalApplication`) izolasyonu en üst düzeye çıkarır ama kullanıcıyı uygulamadan atar ve dönüşü tamamen derin bağlantıya bağlar. ADR-0004 bunu değerlendirip reddetmişti, o değerlendirme geçerli.
