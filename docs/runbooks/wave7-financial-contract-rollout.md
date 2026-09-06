# Dalga 7 finansal JSON sözleşmesi geçişi

Dalga 7, ödeme denemesi, payout ve iptal tutarlarını JSON sayı yerine exact
decimal string olarak döndürür. Bu değişiklik eski `1.0.0` mobil istemcinin
`as num?` ayrıştırmasını kırar; backend sözleşmesi mobil dağıtımdan önce canlıya
alınamaz.

## Canlıya alma sırası

1. Mobil `1.1.0+2` sürümünü yayımla. Bu sürüm yeni string sözleşmesini okur;
   rolling deploy süresince eski backend'in JSON sayılarını da doğrulayarak
   kabul eder.
2. Mağaza yayılımını ve istemci sürüm telemetrisini doğrula.
3. Admin ayarlarından `app.minimum-version.ios` ve
   `app.minimum-version.android` değerlerini `1.1.0` yap. Eski sürümle korumalı
   bir uç çağrısının `426 Upgrade Required` ve `X-Minimum-App-Version: 1.1.0`
   döndürdüğünü doğrula.
4. Bundan sonra backend Dalga 7 exact-string sözleşmesini ve web istemcisini
   dağıt.
5. Ödeme denemesi, kazanç ve iptal önizleme ekranlarında kuruş/ölçek
   değerlerini staging verisiyle doğrula.

## Geri dönüş

Backend geri alınırsa mobil `1.1.0` JSON sayılarını geçici okuyabildiği için
çalışmaya devam eder. Mobil eşikleri ancak backend geri dönüşü tamamlandıktan
ve finansal uçlar doğrulandıktan sonra düşür. Backend exact-string sürümü
canlıdayken eşikleri `1.0.0` seviyesine indirme.
