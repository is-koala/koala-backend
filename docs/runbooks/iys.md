# İYS ticari ileti entegrasyonu

Bu entegrasyon, Koala'daki ticari ileti tercihlerini İletiMerkezi'nin İYS kayıt ve sorgu
uçlarına taşır. Varsayılanı kapalıdır. Kimlik bilgisi, marka kodu veya izin kaynağı eksikse
ticari e-posta ve SMS gönderimi kapalı kalır; yerel tercih güncellemesi ise başarısız olmaz ve
`iys_consent_tasks` outbox'ına kaydedilir.

## Üretim ön koşulları

`IYS_ENABLED=true` yapılmadan önce aşağıdakilerin tamamı insan tarafından doğrulanmalıdır:

1. Koala'nın ETBİS durumu ve ticari ileti hukuki metni hukuk danışmanı tarafından onaylanmış olmalı.
2. İletiMerkezi hesabında API erişimi açılmış, aktif İYS aboneliği ve Koala'ya ait `brandCode`
   tanımlanmış olmalı.
3. `IYS_SOURCE`, iznin gerçekten toplandığı kanalı anlatan ve sağlayıcının kabul ettiği kaynak
   kodu olmalı. Web tercih merkezi için `HS_WEB` ancak sağlayıcı/hukuk teyidiyle kullanılmalıdır.
4. API anahtarı ve hash'i secret store'a konmalı; repoya, loglara veya destek kayıtlarına
   yazılmamalıdır.
5. Kontrollü bir Koala test hesabıyla hem e-posta hem SMS için `ONAY` ve `RET` akışları gerçek
   sağlayıcı hesabında doğrulanmalıdır. Kayıt ucunun `200` cevabı yalnız kabul/kuyruk anlamına
   geldiği için sorgu ucunda aynı durum görülmeden test başarılı sayılmamalıdır.

Sağlayıcı dokümanında ayrı bir sandbox olmadığı belirtilir. Bu nedenle otomatik testler yalnız
dış HTTP sınırını taklit eder; canlı doğrulama yerine geçmez.

## Açma ve geri alma

Önce kimlik bilgilerini, marka kodunu ve kaynağı yükleyin; ardından `IYS_ENABLED=true` yapın.
Uygulama yeni tercih kararlarını asenkron işler ve kayıt sonrasında sorgu ucuyla sonucu doğrular.
İYS'de `RET` görüldüğünde aynı kanaldaki bütün yerel ticari kategoriler kapanır. İYS'deki `ONAY`,
kullanıcının Koala içindeki daha dar opt-out kararlarını açmaz.

Acil geri alma için `IYS_ENABLED=false` yapın. Bu ticari gönderimleri güvenli biçimde kapatır,
bekleyen outbox kayıtlarını silmez ve deneme sayılarını tüketmez. Sorun giderildikten sonra aynı
kayıtlar yeniden işlenebilir.

## İzleme

Bekleyen ve tükenen görevleri izleyin:

```sql
select status, count(*)
from iys_consent_tasks
where is_deleted = false
group by status;

select id, user_id, channel, attempts, next_attempt_at, last_error
from iys_consent_tasks
where is_deleted = false and status in ('PENDING', 'EXHAUSTED')
order by next_attempt_at;
```

`EXHAUSTED` artışı, en eski `PENDING` kaydın yaşı, 401/403/422 sağlayıcı hataları ve mutabakat
gecikmesi alarm üretmelidir. İYS kayıt API'si eski izin tarihlerini reddedebildiği için kuyruk
yaşı üç iş günü sınırına yaklaşmadan müdahale edilmelidir.

## Eski izin kayıtları

Geçmiş `ConsentRecord(COMMERCIAL_MESSAGE)` satırları normal kayıt API'sine otomatik verilmez.
Sağlayıcı kayıt ucu eski `consentDate` değerlerini reddeder ve mevcut kayıtlarda sağlayıcının
zorunlu tuttuğu özgün izin kaynağı güvenilir biçimde bulunmaz. İlk toplu aktarım ancak hukuk ile
İletiMerkezi'nin onayladığı ayrı bir import prosedürü, kaynak eşlemesi ve mutabakat raporu ile
yapılmalıdır. Bu onay olmadan tarih veya kaynak uydurulmamalıdır.

## Kaynaklar

- [İletiMerkezi iys-register](https://www.iletimerkezi.com/docs/api/iys-register):
  `POST /v1/consent/create/json`
- [İletiMerkezi iys-check](https://www.iletimerkezi.com/docs/api/iys-check):
  `POST /v1/consent/show/json`
- [İYS servis sağlayıcı entegrasyonu](https://iys.org.tr/iys/nasil-calisir): dışarıda alınan
  izinlerin süresi içinde kaydı ve İYS tarafındaki değişikliklerin pull/push yoluyla alınması
