# İYS entegrasyonu — kapsam, zamanlama ve neden bu issue'da yapılmadığı

Tarih: 6 Eylül 2026
İlgili issue: is-koala/koala-backend#31 (Dalga 3)

## Dalga 7 teslim durumu

Takip issue'su #47 kapsamında sağlayıcı istemcisi, izin outbox'ı ve retry/backoff, periyodik
mutabakat, doğrulanmış izin önbelleği, gönderim kapısı ve SMS tercih yüzeyi uygulandı. Kod
varsayılan olarak kapalı ve kimlik bilgileri eksikken fail-closed çalışır.

Canlı sağlayıcı doğrulaması, ETBİS/hukuk onayı ve geçmiş kayıtların sağlayıcı tarafından
onaylanmış ilk aktarımı hâlâ operasyonel kapıdır. Ayrıntılar `docs/runbooks/iys.md` içindedir.

## Kısa karar

Issue #31 bildirim tercihi modelini, uçları, gönderim kapısını ve abonelikten çıkma bağlantısını
teslim ediyor. **İYS (İleti Yönetim Sistemi) entegrasyonu bu issue'da uygulanmadı** ve kendi
dilimine bırakıldı. Issue'nun son maddesi bu seçeneği açıkça veriyor; gerekçe ve kapsam aşağıda.

Bu dosya o maddenin karşılığıdır. Takip issue'su Dalga 7 altında açıldı.

## Neden ayrıldı

**İYS bir dış sağlayıcı entegrasyonu, bir veri modeli değil.** #31'in tamamı Koala'nın kendi
veritabanında biten bir iş: tercih satırı, rıza kaydı, imzalı token, gönderim kapısı. İYS ise
İleti Yönetim Sistemi A.Ş.'nin REST API'sine karşı çalışan, kimlik bilgisi, marka/hizmet sağlayıcı
kaydı ve kota yönetimi gerektiren ayrı bir bileşen. İkisini tek PR'a koymak, dış servise
erişilemediği sürece doğrulanamayan kodu doğrulanabilir kodla aynı pakete sokardı — Iyzico'da
yaşanan durumun aynısı (§2x, §2y: protokol dokümana karşı yazıldı, sandbox'ta uçtan uca hâlâ
doğrulanmadı).

**Ön koşulları henüz yok.** Entegrasyon için gereken üç şeyin üçü de bugün elimizde değil:

1. **İYS hesabı ve API kimlik bilgisi.** Marka/hizmet sağlayıcı kaydı hukuki bir başvuru süreci.
2. **ETBİS kaydı.** Yol haritasında Faz 9B'de, "hukuk" bağımlılığıyla işaretli.
3. **Hukuki metinlerin son hâli.** `AgreementType.COMMERCIAL_MESSAGE` sözleşmesinin metni
   avukattan gelmedi (§2n).

**Yükümlülüğü doğuran gönderim henüz yok.** Bugün `MARKETINGS` ve `NEWSLETTER` kategorilerinde
hiç gönderim yapılmıyor. #31 sonrasında bu kategoriler **varsayılan olarak kapalı** ve yalnızca
kullanıcı açıkça açtığında gönderilebiliyorlar. Yani İYS yükümlülüğünü tetikleyecek ilk ticari
ileti, İYS dilimi teslim edilmeden gönderilemez — sıralama doğru tarafta güvenli.

## #31'in bıraktığı zemin

İYS dilimi sıfırdan başlamayacak; #31 onun ihtiyaç duyduğu veriyi zaten üretiyor:

| İYS'nin istediği | #31'de nerede duruyor |
| --- | --- |
| İzin var mı / yok mu | `notification_preferences` (kullanıcı × kategori × kanal) |
| İznin alındığı an | `commercial_message_consents.recorded_at` |
| İzin kanalı | `commercial_message_consents.channel` |
| İzin kaynağı/IP | `commercial_message_consents.ip_address`, `user_agent`, `source` |
| İzin mi ret mi | `commercial_message_consents.granted` — çekilen izin de kaydediliyor |
| Ticari ileti ayrımı | `NotificationCategoryKind.COMMERCIAL` |
| Çıkış bağlantısı | yalnız `COMMERCIAL` kategorideki e-postalara biniyor |

`SmsConsumer` zaten `MARKETINGS`/`NEWSLETTER` kategorilerinde `isIys=true` bayrağını taşıyor
(§2s); bayrak kategoriden türediği için yeni bir pazarlama gönderimi eklendiğinde kendiliğinden
doğru gidiyor.

## İYS diliminin kapsamı

Yapılacaklar, teslim edilecek dilimin sınırı:

1. **`IysService` + sağlayıcı istemcisi.** İYS REST API'sine karşı; `PushService`/`SmsService`
   desenindeki gibi arayüz + uygulama, kimlik bilgisi yokken hata veren varsayılan uygulama.
2. **İzin gönderimi (ekleme/çıkarma).** `commercial_message_consents` satırı yazıldığında İYS'ye
   iletilmesi. Senkron değil: `settlement_tasks` (§2y) desenindeki gibi bir outbox + yeniden
   deneme, çünkü dış servis düştüğünde kullanıcının tercih değişikliği reddedilmemeli.
3. **Toplu izin yükleme (ilk mutabakat).** Bugüne kadar toplanmış `ConsentRecord`
   (`AgreementType.COMMERCIAL_MESSAGE`) satırlarının İYS'ye tek seferlik aktarımı.
4. **İzin sorgulama ve çift yönlü mutabakat.** Kullanıcı izni İYS tarafından (ör. 0850 hattı ya
   da İYS portalı üzerinden) çekebilir; o değişikliğin bizim `notification_preferences`
   satırımıza yansıması gerekiyor. Zamanlanmış mutabakat işi.
5. **Gönderim öncesi kapı.** `NotificationManager` bugün yalnızca yerel tercihe bakıyor; ticari
   iletide İYS'nin cevabı da kapı olmalı. Kapı noktası hazır — `suppressed(...)` tek yerde.
6. **SMS kanalının tercih yüzeyine alınması.** #31 tercihleri `EMAIL`/`PUSH`/`IN_APP` ile sınırlı
   tutuyor (issue metnindeki kanal listesi bu üçü). `NotificationChannel.SMS` bilerek
   `preferable = false`; ticari SMS İYS'nin asıl konusu olduğu için bu bayrağın açılması İYS
   dilimine ait.
7. **ETBİS kaydı ve marka tanımı.** Kod değil, operasyon; entegrasyonun ön koşulu.

## Zamanlama

**Dalga 7.** Gerekçesi:

- Yol haritasında İYS zaten Faz 9B'de ("yasal kalanlar", bağımlılık: **hukuk**) duruyor.
- `[16] Push bildirim token'ı` ve `[20] İş alarmları` mesaj hacmini artıran işler ve ikisi de
  ticari ileti değil; #31'in getirdiği çıkış kapısı onlar için yeterli.
- İlk ticari ileti gönderilmeden önce teslim edilmesi şart. Bugün o gönderim yok ve #31'den sonra
  varsayılan kapalı olduğu için kazara da yapılamaz.

**Kapı:** `MARKETINGS` ya da `NEWSLETTER` kategorisinde ilk gerçek gönderim planlandığı anda bu
dilim bloker hâline gelir. O ana kadar erteleme maliyetsiz.

## Risk kaydı

Erteleme bir riski açık bırakıyor: **bugün toplanan ticari ileti izinleri İYS'ye bildirilmiyor.**
Mevzuat izni alan tarafın İYS'ye kaydetmesini istiyor. Bunun bugünkü karşılığı sınırlı, çünkü
izne dayanan bir gönderim yok; ama kayıt yükümlülüğü gönderimden bağımsız olabilir. **Bu maddenin
hukuk tarafından teyidi gerekiyor** — teyit "evet, gönderim olmasa da kaydetmek gerekiyor" derse
İYS dilimi Dalga 7'den öne çekilmelidir.
