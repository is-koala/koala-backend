# Iyzico checkout'u izole uygulama içi tarayıcıda çalıştır

Iyzico hosted checkout, uygulamadan Safari'ye çıkmak yerine JWT veya native bridge taşımayan izole bir WebView içinde açılacaktır. Banka 3DS yönlendirmelerini kırmamak için üst seviye HTTPS navigasyonlarına izin verilir; yalnız tam Koala dönüş URL'si yakalanarak WebView kapatılır ve finansal sonuç her zaman backend'in doğrulanmış ödeme durumundan alınır. Bu yaklaşım, harici tarayıcının kopuk deneyimi ile bilinmeyen banka domainlerini katı allowlist ederek ödeme akışını bozma riski arasındaki denge için seçilmiştir.
