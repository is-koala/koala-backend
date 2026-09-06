# Runbook: ödemeler

Durum: ilk müdahale belgesi. Ödeme yığını Iyzico Sandbox üzerinde çalışıyor;
canlı üye işyeri başvurusu henüz tamamlanmadı (#36). Buradaki adımlar bugünkü
kodun davranışını anlatır, gelecekteki bir kurulumu değil.

Bu belge **ne yapılacağını** yazar. **Neden öyle olduğu** CLAUDE.md'de:
ödeme akışı §2k, Iyzico protokol denetimi §2x, mutabakat outbox'ı §2y,
sandbox/canlı ayrımı §2y2. Aynı gerçeği iki yere yazmamak için burada
tekrarlanmıyor, referans veriliyor.

## Ortam ayrımı — hangi anahtar nerede

`SERVICE_ENVIRONMENT` uygulamanın hangi ortamda olduğunu söyleyen tek değerdir
(`docs/environments.md`). `iyzico.deployment` bu değere bağlanmıştır ve
`IyzicoProperties` açılışta `iyzico.base-url` ile eşleşmesini zorunlu kılar:

| `SERVICE_ENVIRONMENT` | Kabul edilen `IYZICO_BASE_URL` |
| --- | --- |
| `production` | yalnız `https://api.iyzipay.com` |
| `staging` | yalnız `https://sandbox-api.iyzipay.com` |
| `local` (ya da tanımsız) | sandbox veya loopback (`localhost`, `127.0.0.1`) |

Eşleşmeyen her kombinasyon **uygulamayı açılışta düşürür**. Listede olmayan bir
host da (`https://api.iyzipay.com.example.test` gibi) her ortamda reddedilir.
`SERVICE_ENVIRONMENT` bu üç değerden biri değilse uygulama yine açılmaz; yani
yazım hatası sessizce "production değil" anlamına gelmez.

Kural: `src/main/java/com/exskylab/koala/core/properties/IyzicoProperties.java`.
Testi: `src/test/java/com/exskylab/koala/core/properties/IyzicoEnvironmentSplitTest.java`.

**Açılışta `iyzico.base-url must be the iyzico environment that iyzico.deployment
allows` hatası görürsen** bu kapı kapanmıştır: ya ortam değişkeni yanlış ya da
o ortama ait olmayan anahtarlar yüklenmiştir. Kuralı gevşetme; yapılandırmayı
düzelt. Canlı anahtarların staging'e düşmesi tam olarak bu hatanın engellediği
şeydir.

## Ortak ilk adımlar

Her olayda önce şu üçü:

1. **Ataması hangisi.** Kullanıcıdan `assignmentId` al. Bütün sorgular buna asılı.
2. **Denemeleri oku.** `GET /job-assignments/{assignmentId}/payments` (taraf ya da
   ADMIN yetkisi ister). Her deneme ayrı `payments` satırıdır; sonuncusu son
   durumdur.
3. **Korelasyon.** `payments.conversation_id` Iyzico panelindeki kayıtla aynıdır
   (§2x). Panelde arama yaparken bu değeri kullan, `payment_id`'yi değil.

```sql
select id, status, failure_reason, total_charged, currency,
       provider_payment_id, provider_transaction_id, conversation_id,
       created_at, completed_at
from payments
where job_assignment_id = :assignmentId
order by created_at desc;
```

## 1. Başarısız ödeme

Belirti: kullanıcı `status=failed` ile uygulamaya dönüyor
(`PaymentReturnUrlBuilder.resultUrl`), `payments.status = FAILED`.

Karar `PaymentManager.completeFromCallback` içinde veriliyor ve gerekçe
`payments.failure_reason` alanına yazılıyor. Alanın dört biçimi var, her biri
farklı bir olay:

| `failure_reason` | Anlamı | Para hareketi | İlk adım |
| --- | --- | --- | --- |
| `paymentStatus=<değer>` | Sağlayıcı `SUCCESS` demedi: kart reddi, vazgeçme, 3DS'in tamamlanmaması | Yok | İşveren yeniden dener. Olay değil. |
| `{payment.fraud.review}` | `fraudStatus = -1`, sağlayıcı dolandırıcılık filtresinde reddetti | Yok | İşverene başka kart önerilir. |
| `{payment.3ds.verification.failed}` | `mdStatus ≠ 1` ya da yanıtta hiç yok: 3DS doğrulaması geçmedi (§2x2) | **Belirsiz — para çekilmiş olabilir** | Aşağıdaki "3DS doğrulanmadı" adımı. |
| `expected <a> but the provider collected <b>` veya `expected currency TRY but the provider used <c>` | Sağlayıcı bizim beklediğimizden farklı tahsil etti | **Belirsiz — para çekilmiş olabilir** | Aşağıdaki "tutar uyuşmazlığı" adımı. |
| Serbest metin (HTTP hatası, `ExternalServiceException` mesajı) | Sağlayıcıya ulaşılamadı ya da yanıt okunamadı | **Belirsiz** | Aşağıdaki "sonucu bilinmeyen deneme" adımı. |

**Tutar uyuşmazlığı.** Emanet bilerek açılmaz; bu bir güvenlik kararıdır, bir
hata değil. Ama Iyzico tarafında para çekilmiş olabilir. Panelde
`conversation_id` ile kaydı bul. Tahsilat gerçekleştiyse iade **panelden elle**
yapılır — bizde `SUCCEEDED` bir ödeme olmadığı için `POST /admin/job-assignments/{id}/refund`
çalışmaz (`PaymentManager.getSettledPaymentFor` kayıt bulamaz). Log satırı
`Payment {} rejected: expected ... but the provider collected ...` beklenen ve
gerçekleşen tutarı birlikte yazar.

**3DS doğrulanmadı.** `paymentStatus = SUCCESS` geldiği hâlde `mdStatus` `1` değil.
Emanet **bilerek** açılmaz (§2x2); bu bir hata değil, güvenlik kararıdır. Sayısal
değer yalnız logdadır:

```
Payment <id> rejected: 3-D Secure verification did not succeed, mdStatus=<değer>
```

`mdStatus=null` ise yanıtta alan hiç yoktu. iyzico "yalnız 3DS işlemlerinde
görünür" diyor, yani ya işlem 3DS'siz geçti ya da yanıt beklediğimiz gibi değil;
ikisi ayırt edilemediği için ödeme reddedilir. **Bu satırı canlıda görürsen
olaydır** — Checkout Form 3DS'siz ödeme geçiriyor demektir ve karar
(`forceThreeDS` mı, kapının gevşetilmesi mi) issue'ya yazılmalıdır. Kapıyı
kendiliğinden gevşetme.

Her iki hâlde de sağlayıcı tarafında para çekilmiş olabilir: panelde
`conversation_id` ile kaydı bul, tahsilat gerçekleştiyse **panelden elle** iade
et — tutar uyuşmazlığındaki yolun aynısı. İşveren yeniden deneyebilir; kayıt
`FAILED` olduğu için atama tekrar tahsilata açıktır.

**Sonucu bilinmeyen deneme.** Aynı yol: panelde `conversation_id` ile bak.
Sağlayıcı tarafında başarılı bir işlem varsa bu bir mutabakat farkıdır, bölüm
4'e geç.

**`IN_REVIEW` durumu.** `fraudStatus = 0`. Ödeme sağlayıcıda incelemede,
emanet açılmadı, işçi işe başlayamaz. **Bu durumdan otomatik çıkış yok** (§2x):
Iyzico incelemeyi bitirdiğinde webhook gönderiyor ama o tüketici yazılmadı.
Bugünkü müdahale: panelde sonucu gör, olumluysa işverene yeniden denemesini
söyle. Kayıt `provider_transaction_id` ile duruyor, elle de düzeltilebilir.

**Yükselt** (bu bir kod hatası): `payments.status = SUCCEEDED` ama
`job_assignments.payment_status <> HELD_IN_ESCROW`. `PaymentManager.settle`
ikisini aynı işlemde yazar; ayrışmışlarsa durumu bozan başka bir şey var.

## 2. Çift callback

Aynı token için ikinci callback **tasarım gereği zararsızdır**:
`completeFromCallback` zaten `SUCCEEDED` olan ödemede sağlayıcıya hiç gitmez ve
kaydı değiştirmez (§2k). Log: `Payment {} already settled, callback ignored.`
Bu satırı görmek olay değil.

Tanımadığı token ile gelen callback 404 döner (`{payment.not.found}`) ve hiçbir
şey yapmaz. `POST /payments/callback` `SecurityConfig` içinde bilerek
`permitAll` — doğrulama sunucudan sunucuya yapıldığı için imza kontrolü yok
(§2k). Callback yağmuru bir doğruluk sorunu değil, hız sınırı sorunudur.

Gerçek olay tek bir şeydir: **aynı atama için iki ayrı deneme de tahsil
edilmiş**. Kontrolü:

```sql
select job_assignment_id, count(*)
from payments
where status = 'SUCCEEDED'
group by job_assignment_id
having count(*) > 1;
```

Bu sorgu satır döndürmemeli. Döndürüyorsa işveren iki kez ödemiştir:

1. Hangisinin emanete yazıldığını bul (`job_assignments.total_charged` hangi
   denemeyle eşleşiyor).
2. Fazla olanı Iyzico panelinden `conversation_id` ile iade et. Bizim iade
   ucumuz atama başına çalışır ve emanetteki tek ödemeyi hedefler; ikinci
   tahsilatı o uçtan iade edemezsin.
3. Kaydı bırakma — `failure_reason` alanına elle bir not düşülmez, olay
   issue'ya yazılır.

## 3. İade

Uç: `POST /admin/job-assignments/{assignmentId}/refund` (ADMIN),
gövdede opsiyonel `amount` ve `reason`. Kod: `PayoutManager.refundEmployer`.

Ön koşullar — biri tutmazsa 409/400 döner ve hiçbir şey kuyruğa girmez:

- Atamanın ödeme durumu `HELD_IN_ESCROW` olmalı (`PaymentStateMachine.canRefund`).
  **İşçiye çıkmış para iade edilemez**; `RELEASED_TO_WORKER` durumunda uç reddeder.
- `amount` boşsa tam iade (`total_charged`). Verilmişse `0 < amount <= total_charged`.

Ne kuyruğa girer (§2x'teki tablo):

- Tam iade → yalnız `REFUND` görevi.
- Kısmi iade → `REFUND` **ve** `DISAPPROVE`. İkincisi kalan tutarın işçiye
  aktarılmasını engeller.

Sonra `refunds` satırı `PENDING` durumunda bekler. `SettlementRetryScheduler`
görevi başarıyla çalıştırınca `COMPLETED` olur; deneme limiti aşılırsa `FAILED`.

```sql
select st.id, st.operation, st.status, st.attempts, st.next_attempt_at,
       st.amount, st.provider_reference, st.last_error
from settlement_tasks st
join payments p on p.id = st.payment_id
where p.job_assignment_id = :assignmentId
order by st.created_at;
```

**Görev `FAILED` ise.** Zamanlayıcı sessizce durur ve bir daha denemez (§2y).
Yeniden kuyruğa alma ucu yoktur ve `(payment_id, operation)` kısmi benzersiz
indeksi ikinci bir görev açılmasını engeller. Bugünkü tek yol: iadeyi Iyzico
panelinden elle yap, `refunds` satırını `COMPLETED` ve `provider_refund_id`
ile düzelt. Bunu yapan kişi olayı issue'ya yazar.

**İade emanete geri dönmez.** `refundEmployer` atamayı `REFUNDED` yapar; bu
durumdan çıkış yoktur. Yanlış atamaya iade başlatıldıysa geri alınamaz.

İptal akışının ürettiği iade `cancellations.refund_id` üzerinden ilgili iptal
kaydına doğrudan bağlıdır. Bir iptalin yaşam döngüsünü incelerken atamanın en son
iadesini seçme; sonradan açılmış personel veya uyuşmazlık iadeleri aynı atamaya
ait olabilir. Migration öncesi iptallerde bu nedensellik kanıtlanamadığı için
bağlantı boş kalabilir.

## 4. Mutabakat farkı

Bizim tarafımızdaki üç kaynak: `payments` (ne tahsil ettik),
`settlement_tasks` (sağlayıcıdan ne istedik), `payouts` / `refunds` (sonucu
kullanıcıya nasıl gösteriyoruz).

**Önce şunu bil:** `PayoutStatus.COMPLETED` hiçbir yerde set edilmez (§2y).
`PROCESSING`, Iyzico'nun `approve` talimatını kabul ettiği anlamına gelir —
paranın işçinin banka hesabına geçtiği anlamına **gelmez**. O bilgi yalnızca
hesap ekstresinde vardır. Mutabakatın var olma sebebi budur.

### Yordam

1. **Kuyruğu dondur.** İnceleme sürerken `IYZICO_SETTLEMENT_RETRY_ENABLED=false`
   yap. Kuyruk işlenmez ama görevler `next_attempt_at` değerleriyle durur ve
   bayrak geri açıldığında kaldığı yerden devam eder. Hiç kuyruğa girilmesini
   istemiyorsan `IYZICO_SETTLEMENT_ENABLED=false` ayrı bir bayraktır.
2. **Gün için beklenen tahsilatlar.**

   ```sql
   select conversation_id, provider_payment_id, provider_transaction_id,
          total_charged, currency, completed_at
   from payments
   where status = 'SUCCEEDED'
     and completed_at >= :dayStart and completed_at < :dayEnd
   order by completed_at;
   ```

3. **Gün için sağlayıcıya gönderilmiş talimatlar.**

   ```sql
   select operation, status, provider_transaction_id, provider_reference,
          amount, attempts, completed_at, last_error
   from settlement_tasks
   where completed_at >= :dayStart and completed_at < :dayEnd
   order by completed_at;
   ```

4. **Iyzico ekstresini/raporunu indir** ve iki listeyi `conversation_id`
   üzerinden eşleştir.
5. **Farkları sınıfla.** Beş sınıf var, hepsinin anlamı farklı:

   | Fark | Anlamı | İlk adım |
   | --- | --- | --- |
   | Ekstrede var, bizde `SUCCEEDED` yok | Callback kaybolmuş ya da doğrulama hata vermiş | Bölüm 1'deki "sonucu bilinmeyen deneme". Ödeme `FAILED` ise para çekilmiş demektir; iade panelden. |
   | Bizde `SUCCEEDED`, ekstrede yok | Olmaması gerekir; kararı hep sunucudan sunucuya alıyoruz | Yükselt. Kod hatası varsayımıyla bak. |
   | `APPROVE` görevi `PENDING`, ekstrede transfer yok | Henüz sırası gelmemiş olabilir; geri çekilme tavanı 6 saat | `next_attempt_at` gelecekteyse fark değil. Bekle. |
   | `APPROVE` görevi `SUCCEEDED`, ekstrede transfer yok | Talimat kabul edildi, transfer görünmüyor | `provider_reference` ile Iyzico'ya kayıt aç. |
   | Görev `FAILED` | Zamanlayıcı 8 denemede pes etti ve durdu | `last_error` oku. `payouts`/`refunds` satırı da `FAILED` işaretlenmiştir (§2y). Elle telafi + issue. |

6. **Kuyruk sağlığı.**

   ```sql
   select status, operation, count(*), min(next_attempt_at)
   from settlement_tasks
   group by status, operation;

   select id, operation, attempts, last_error, next_attempt_at
   from settlement_tasks
   where status = 'FAILED'
   order by next_attempt_at;
   ```

   `FAILED` satırlar elle incelenmek üzere tabloda bırakılır, silinmez.

7. **Zamanlayıcı çalışıyor mu.** `SettlementRetryScheduler.runDueSettlements`
   varsayılan olarak beş dakikada bir koşar (`IYZICO_SETTLEMENT_CRON`) ve
   **yalnız işlenecek görev varsa** log yazar:
   `Settlement retry run finished: X of Y tasks succeeded.` Yani log sessizliği
   tek başına arıza değildir; `settlement_tasks` içinde vadesi geçmiş `PENDING`
   satır varken sessizlik arızadır.

   ```sql
   select count(*) from settlement_tasks
   where status = 'PENDING' and next_attempt_at < now();
   ```

8. **Kapanış.** Her mutabakat koşusu şunu kaydeder: incelenen gün, eşleşen
   kayıt sayısı, sınıflanan farklar, elle yapılan telafiler ve ilgili
   `conversation_id` değerleri. Kanıt olmadan kapatma.

## 5. Sırlar

Değerler hiçbir zaman repoya, issue'ya, log'a ya da bu belgeye yazılmaz. Yalnız
değişken adı anılır. Saklandıkları tek yer Dokploy secret store'dur (CLAUDE.md §5).

| Değişken adı | Ortam | Sahip | Rotasyon beklentisi |
| --- | --- | --- | --- |
| `IYZICO_API_KEY` | production | Ödeme sahibi (üye işyeri hesabının sahibi) | Yılda bir; sızıntı şüphesinde ya da ekipten ayrılmada hemen |
| `IYZICO_SECRET_KEY` | production | Ödeme sahibi | `IYZICO_API_KEY` ile birlikte, aynı anda |
| `IYZICO_API_KEY` | staging | Backend sahibi | Sandbox anahtarı; yılda bir yeterli |
| `IYZICO_SECRET_KEY` | staging | Backend sahibi | Sandbox anahtarı; yılda bir yeterli |

Notlar:

- Canlı ve sandbox anahtarları Iyzico tarafında ayrı çiftlerdir; aynı değer iki
  ortamda kullanılamaz. Yukarıdaki açılış kapısı bunu yapılandırma tarafında da
  imkânsızlaştırır.
- Rotasyon sırası: yeni çifti Dokploy'a yaz → production servisini yeniden
  başlat → açılış logunda hata olmadığını gör → eski çifti Iyzico panelinden
  iptal et. Ters sıra kesinti üretir.
- `docs/secrets-inventory.md` bu dalda henüz yok. Merkezî envanter geldiğinde bu
  tablo oraya taşınır ve buradan yalnız referans verilir; iki yerde birden
  yaşamaz.

## 6. 3DS: sandbox ile canlının ayrılabildiği yerler

Bu bölüm iyzico'nun **kendi** dokümantasyonuna (`docs.iyzico.com`) ve
`github.com/iyzico` altındaki resmî SDK kaynaklarına dayanır. Üçüncü taraf
yazılar kullanılmadı. **Belgelenmiş** ile **uyarı** ayrımı bilerek yapılıyor:
ikincisini canlıda doğrulamadan gerçek saymayın.

### 6.1 Belgelenmiş farklar

| Konu | Sandbox | Canlı | Kaynak |
| --- | --- | --- | --- |
| Anahtar çifti | `sandbox-` önekli | öneksiz | `/en/getting-started/preliminaries/live-vs-sandbox` |
| Yanlış eşleşme | — | — | Anahtar ve baseUrl uyuşmazsa **errorCode 1001**, "API details not found" (`/en/add-ons/error-codes`) |
| 3DS sayfası | iyzico'nun yayımladığı `threeDSHtmlContent` örneği `iyzico Mock 3D-Secure Processing Page` başlığını taşır ve `sandbox-api.iyzipay.com/payment/mock/init3ds` adresine POST eder | gerçek banka sayfası | `/en/payment-methods/api/3ds/3ds-implementation` |
| SMS/OTP | sabit `123456` | bankanın ürettiği tek kullanımlık şifre ya da banka uygulamasıyla onay | `/en/getting-started/preliminaries/sandbox` |
| Kartlar | gerçek kartlar **desteklenmez**; yalnız test kartları çalışır | test kartları çalışmaz | `/en/add-ons/test-cards`, `live-vs-sandbox` |
| `hostReference` | örnek yanıtlarda `mock…iyzihostrfn` | gerçek referans | `/en/payment-methods/api/3ds/3ds-implementation` |
| Rapor SFTP | `sandbox-report.iyzipay.com` | `report.iyzipay.com` | `/en/advanced/sftp` — aynı sayfa "Sandbox and Production SFTP ENVs might differ in certain terms" diyor; doküman setindeki **tek** açık ortam farkı uyarısı budur |
| Canlı hesap | sandbox kaydı kendi kendine yapılır | ayrı başvuru; "contact your account manager" | `/en/add-ons/integration-checklist` |

**`mdStatus` tablosu** (`/en/payment-methods/api/3ds/3ds-implementation` ve
Türkçe eşi `/odeme-metotlari/api/3ds/3ds-entegrasyonu`):
`1` başarılı, `0` 3DS imzası/doğrulaması geçersiz, `2` kart sahibi ya da banka
3DS'e kayıtlı değil, `3` banka kayıtlı değil, `4` doğrulama yapılamadı — kart
sahibi sonra kaydolmayı seçti, `5` doğrulama yapılamadı, `6` 3DS hatası,
`7` sistem hatası, `8` bilinmeyen kart. Aynı sayfa "`mdStatus` yalnızca 3DS
işlemlerinde döner" diyor.

**Türkçe tabloda İngilizcede olmayan bir satır var:** `-1`, "`mdStatus = 0` ile
aynıdır, QNB Finansbank'a özel". Aynı sayfaların `/payment/v2/3dsecure/auth`
şeması hem `enum`'da `-1`'i sayıyor hem de kuralı `1: Başarılı`, `0,2-8:
Başarısız` diye yazıyor — yani `-1` "başarısız" aralığı cümlesinin dışında
kalıyor. Kodumuz bu yüzden kara liste değil beyaz liste tutuyor (§2x2).

**CF sorgulama yanıtında `mdStatus` konusunda doküman kendi içinde çelişiyor.**
`/odeme-metotlari/odeme-formu/cf-entegrasyonu/cf-sorgulama` OpenAPI şeması alanı hiç saymıyor;
aynı doküman setindeki örnek CF yanıtı (`.../cf-ornek-entegrasyon`) ise
`"mdStatus": 1` içeriyor ve resmî `iyzipay-java` SDK'sında `CheckoutForm`,
`PaymentResource`'tan `getMdStatus()`'u miras alıyor. İkisi de resmî.

**Sandbox'ta üretilebilen 3DS sonuçları sınırlı.** Belgelenmiş üç 3DS kartı
(`/en/add-ons/test-cards`):

| Kart | Belgelenmiş davranış |
| --- | --- |
| `4131111111111117` | Ödeme başarılı ama **`mdStatus` 0** |
| `4141111111111115` | Ödeme başarılı ama **`mdStatus` 4** |
| `4151111111111112` | 3DS initialize başarısız |

`mdStatus` 2, 3, 5, 6, 7 ve 8 için belgelenmiş bir test kartı **yok**. Bu
dalların ilk gerçek örneği canlıda görülecek.

**Hız sınırları** (`/en/getting-started/preliminaries/limiters`): checkout form
(HPP) initialize ve retrieve dakikada 100; refund ve cancel dakikada 50. Aşımda
`errorCode 50000`, `"Request Limit Exceeded"`. Tablo ortamlara göre
ayrıştırılmamış.

**`callbackUrl` geçerli bir SSL sertifikası ister**
(`/en/payment-methods/checkoutform/cf-implementation/cf-initialize`). Bizde bu
değer `JobAssignmentManager` içinde `app.api-url + /payments/callback` olarak
üretiliyor; production'da `https://api.iskoala.com/...` olduğu için şart
sağlanıyor, yerelde `http://localhost:8080` olduğu için sağlanmıyor.

**`fraudStatus` için iyzico kendi içinde çelişiyor.** `cf-retrieve` şeması
`{1, 0, -1}` sayıyor ve "yalnız 1 iken ürünü gönderin, 0 iken bildirimi
bekleyin" diyor; `/en/advanced/fraud-notifications` ise ödeme yanıtında yalnız
`{0, 1}` olduğunu, `2` (incelemeden sonra onay) ve `-1` (incelemeden sonra ret,
"ödeme iade edilir ve dolandırıcılık olarak işaretlenir") değerlerinin
**bildirimle** geldiğini söylüyor. İki sayfa da resmî. Kodumuz `-1`'i ret,
`0`'ı inceleme sayıyor, kalanını (`1` ve `2`) emanete açıyor — iki okumaya göre
de güvenli tarafta.

### 6.2 Bu farkların bizim kodumuzda karşılığı olmayan yerleri

Canlıya geçmeden bilinmesi gereken üç boşluk:

1. **`mdStatus` artık okunuyor — kapatıldı.** `retrieveCheckoutFormResult`
   alanı ayrıştırıyor ve `completeFromCallback` yalnız `mdStatus = 1` iken
   emanet açıyor; başka her değer ve alanın yokluğu denemeyi `FAILED` yapıyor
   (§2x2). Yukarıdaki `4131111111111117` kartı artık emanet açmaz.
   **Kalan boşluk kuralın kendisi değil, doğrulaması:** sandbox'ta gerçek bir
   işlemle denenmedi ve Checkout Form'un 3DS'siz ödeme geçirip geçirmediği
   bilinmiyor — geçiriyorsa fail-closed kapı onu da reddeder. Canlıya geçişte
   ölçülecek ilk şey `mdStatus=null` loglarının olup olmadığıdır.
2. **Bildirim (IFN/webhook) tüketicisi yok.** `IN_REVIEW`'dan çıkış zaten
   otomatik değil (§2x). Aynı eksik, `fraudStatus` incelemesi sonradan `-1`
   dönerse — yani iyzico ödemeyi iade edip dolandırıcılık işaretlerse — bunu hiç
   öğrenmeyeceğimiz anlamına da gelir. Canlıda bu, bölüm 4'teki "ekstrede var,
   bizde yok" farkı olarak ortaya çıkar. Ayrıca iyzico webhook imzası
   `X-IYZ-SIGNATURE-V3` hesap bazında açılıyor (`/en/advanced/webhook`).
3. **Hız sınırı için özel işleme yok.** `errorCode 50000` bugün sıradan bir
   sağlayıcı hatası gibi ele alınır ve deneme `FAILED` olur. Canlıda eşzamanlı
   ödeme sayısı arttığında bunu `failure_reason` içindeki serbest metinden
   tanırsınız.

### 6.3 Uyarılar — belgelenmemiş, canlıda doğrulanmalı

Bunların hiçbiri iyzico dokümantasyonunda yazılı **değil**. Karar verirken
belgelenmiş gibi kullanmayın:

- **"Sandbox gerçek banka ACS'ine hiç gitmiyor."** Böyle bir cümle yok. Ama
  sayfanın başlığının `Mock 3D-Secure Processing Page` olması, `/payment/mock/…`
  adreslerine POST etmesi ve OTP'nin sabit olması bunu güçlü biçimde ima ediyor.
  Pratik sonucu: **3DS'in zaman aşımı, banka uygulamasıyla onay, kullanıcının
  yarıda bırakması gibi hâller sandbox'ta hiç görülmez.**
- **Checkout Form'un 3DS'i hangi kurala göre seçtiği.** Doküman CF'nin "3D
  Secure kontrolleri" gibi ek sorgulara gerek bırakmadığını söylüyor ama kararı
  neye göre verdiğini yazmıyor. BIN servisinin döndürdüğü `force3ds` bir kart
  özelliğidir, bizim gönderdiğimiz bir bayrak değil.
- **`forceThreeDS` istek alanı.** Dört resmî SDK'da var (Java'da `Integer`,
  yani `0`/`1`), REST referansının iki kopyasında da yok, semantiği hiçbir yerde
  yazılı değil. Bizim kodumuz bu alanı göndermiyor. 3DS'i her işlemde zorunlu
  kılmak istenirse önce iyzico'dan yazılı teyit alınmalı.
- **`callbackUrl`'in panelde allowlist'lenmesi gerektiği.** Belgelerde geçmiyor;
  tek yazılı şart geçerli SSL sertifikası. Canlı geçişte yine de sorulmalı.
- **Sandbox'ta paranın gerçekten akıp akmadığı**, blokajın çözülmesi, alt üye
  işyeri `approve`/`disapprove` takvimi. Hiçbiri ortam bazında belgelenmemiş.
  Mutabakat yordamının (bölüm 4) sandbox'ta anlamlı çalışıp çalışmadığı bu
  yüzden bilinmiyor.
- **Taksit, BKM ve kart saklama** için ortam farkı belgelenmemiş.
- **Sandbox hız sınırlarının canlıyla aynı olduğu.** Tablo ortam ayrımı
  yapmıyor; bu bir varsayım.

## 7. İnsan gerektiren adımlar

Aşağıdakiler kodla yapılamaz. Canlı üye işyeri hesabı olmadan hiçbiri
başlatılamaz ve mühendislik tarafı bunları bekler.

1. **Iyzico üye işyeri başvurusu.** Şirket belgeleri, banka hesabı ve KYC.
   Haftalar süren üçüncü taraf süreç. Iyzico entegrasyon kontrol listesi bunu
   ayrı bir adım olarak yazıyor: "Apply for the iyzico PROD account — please
   contact your account manager" (`/en/add-ons/integration-checklist`).
2. **Sözleşme ve komisyon/valör oranlarının yazılı teyidi.** Iyzico'nun kestiği
   komisyon bizim `COMMISSION_RATE` değerimizden ayrıdır ve mutabakatta ayrı
   satır olarak görünür. Beklenen oran bilinmeden bölüm 4'teki eşleştirme
   yapılamaz.
3. **Alt üye işyeri (marketplace) özelliğinin canlı hesapta açılması.** Bütün
   ödeme akışımız `subMerchantKey` ve `approve`/`disapprove` üzerine kurulu.
   Canlı hesapta bu ürün açık değilse ödeme alınabilir ama işçiye para
   çıkarılamaz.
4. **Canlı API anahtar çiftinin alınması ve yalnız production'a yazılması.**
   Panelde Ayarlar > Firma Ayarları > API Anahtarları. Değerler yalnız Dokploy
   secret store'a `IYZICO_API_KEY` ve `IYZICO_SECRET_KEY` olarak girilir.
   Staging'e girilemez — bölüm "Ortam ayrımı"ndaki kapı buna izin vermez.
5. **`SERVICE_ENVIRONMENT=production` ve `IYZICO_BASE_URL=https://api.iyzipay.com`
   birlikte ayarlanır.** İkisinden biri eksikse uygulama açılmaz; bu beklenen
   davranıştır.
6. **Rapor SFTP erişimi.** `report.iyzipay.com` kullanıcı bilgileri hesap
   yöneticisinden alınır (`/en/advanced/sftp`). Bölüm 4'teki mutabakat yordamı
   bu erişim olmadan yürütülemez.
7. **Webhook/bildirim URL'lerinin panelde tanımlanması** ve imza başlığının
   (`X-IYZ-SIGNATURE-V3`) hesap için açtırılması. Bunlar tanımlansa da bizim
   tarafta tüketici yok (§2x); yani bugünkü kazanç yalnızca panelde iz bırakmak.
   Tüketici yazılana kadar `IN_REVIEW` ve inceleme sonrası ret elle izlenir.
8. **Canlıda küçük tutarlı gerçek bir ödeme ve iadesi.** Issue'nun kabul
   kriteri. Gerçek bir kart gerektirir, otomatikleştirilemez. ADR-0005 bunu
   insan onaylı runbook'a bağlıyor. Koşu sırasında kaydedilecekler:
   `conversationId`, tutar, `mdStatus` (panelden — biz okumuyoruz),
   `fraudStatus`, callback sonucu, `settlement_tasks` satırının durumu ve
   iadenin ekstrede göründüğü tarih.
9. **Rotasyon sahibinin atanması.** Bölüm 5'teki tablodaki "Ödeme sahibi"
   bugün bir isim değil, bir rol. Canlı anahtarlar alınırken gerçek kişi
   yazılmalı.
