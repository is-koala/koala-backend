# Production lansman geçişi

**Durum: Taslak.** Önkoşul paketinin 9. adımı ve nihai kabul provası tamamlanıp
kanıt kimliği cutover kaydına eklenmeden bu yordam gerçek production hedefinde
çalıştırılmaz. Bu belgenin veya wizard'ın repoda bulunması prova yapıldığı ya da
sağlayıcı ayarlarının kabul edildiği anlamına gelmez.

Bu yordam, [ADR-0016](../adr/0016-promote-the-deployment-not-its-state.md) uyarınca
mevcut pre-production Dokploy uygulamasını ve production domainini koruyup bunları
temiz, ayrı production veri kaynaklarına bağlayarak bir kez production'a geçirmek
içindir. Eski stateful kaynaklar production olarak terfi ettirilmez. Normal deploy,
CI veya günlük smoke sırasında çalışmaz.
Operatör `ops/launch/launch-cutover-wizard.sh` ile dokuz kapıyı sırayla geçer;
betik Dokploy, PostgreSQL, RabbitMQ, S3 veya iyzico üzerinde değişiklik yapmaz.
Her değişiklik platformda insan tarafından yapılır ve geri döndürülemez adımdan
önce ikinci onay alınır.

## Değişmez kurallar

- Değişiklik kaydında hedef API servisi, PostgreSQL servis adı ve database adı,
  production RabbitMQ vhost'u, production media bucket/prefix'i, dağıtılacak
  image digest'i, iki onay rolünün/on-call takma adları ve rollback rolü yazılıdır. Parola,
  token, bağlantı dizesi, API anahtarı veya kişisel veri kanıta yazılmaz.
- Hedef database boş, production'a özel ve staging'den ayrı olmalıdır. Aynı
  database içinde schema değiştirerek ortam ayırmak kabul edilmez.
- Reset yalnız bu yordamın 7. kapısında, hedef kimliği iki kişi tarafından
  doğrulandıktan sonra platformun onaylı aracıyla yapılır. Reset komutu bu repoda,
  CI veya deploy işinde bulunmaz; otomatikleştirilemez.
- Eski pre-production PostgreSQL, RabbitMQ ve media kaynakları rollback penceresi
  kapanmadan silinmez, purge edilmez veya yeniden adlandırılmaz. Kapsam dışındaki
  proje, servis, database, vhost, bucket, domain ve secret setlerine dokunulmaz.
- Production'a secret değeri yalnız Dokploy secret store üzerinden girilir.
  Terminal çıktısı, `.env.launch-cutover`, issue veya ekran görüntüsü sır taşımaz.
- Her kapının çıkış koşulu sağlanmadan sonraki kapıya geçilmez. Şüphede değişiklik
  durur ve rollback sorumlusu çağrılır.

## 1. Kapsam, roller ve geri dönüş hedefi

Değişiklik penceresini açmadan önce tek bir cutover kaydı oluştur. İki onay rolü,
bir operatör rolü, bir gözlemci rolü ve ödeme/SMTP on-call rollerini ata. Kişi
adlarını wizard kanıt dosyasına kopyalama. Hedef kaynak adlarını
sağlayıcı konsollarındaki değişmez kimlikleriyle eşleştir. DNS TTL'i, maintenance
başlangıcı ve 24 saatlik gözlem penceresini kayda yaz.

Rollback hedefi geçişten önce seçilir. Eski pre-production verisi hiçbir rollback
kolunda production domaininden servis edilmez:

- Yeni production'a müşteri yazımı başlamadıysa trafik maintenance'a alınır;
  temiz production baseline snapshot'ı izole hedefe geri yüklenebilir. Eski
  pre-production yalnız offline inceleme/kanıt için açılabilir.
- Yeni production'a müşteri veya finansal yazım başladıysa bu yordam kapsamında
  database restore **yasaktır**. Doğrulanmış code/config aynı production database'i
  üzerinde geri alınır; veri sorunu `restore.md` içindeki PITR/forward-recovery
  sınırlarıyla çözülür. Bir database restore ancak kullanıcı, finans, provider
  callback, session, kuyruk ve media yazımlarını kapsayan ayrı bir reconciliation
  runbook'u önceden prova edilmiş ve onaylanmışsa yapılabilir; bugün böyle bir
  runbook yoktur. Legacy quiet-point snapshot hiçbir koşulda production kaynağı
  olamaz.

## 2. Hedef ve eski kaynak envanteri

Aşağıdaki iki kümeyi ayrı ayrı kaydet ve her satırı sağlayıcı ekranında doğrula:

| Küme | Kaynaklar | Geçiş sonrası yaşam döngüsü |
| --- | --- | --- |
| Terfi ettirilen deployment | Mevcut Dokploy API/web uygulama kaydı ve production domainleri | Temiz production veri kaynaklarına yeniden bağlanan kontrol düzlemi; eski state'i taşımaz |
| Temiz production state | Boş PostgreSQL servis+database, ayrı RabbitMQ vhost+kullanıcı, ayrı media bucket/prefix ve production secret seti | Aktif hedef; staging veya legacy kimliği/verisiyle ortak olamaz |
| Eski pre-production state | PostgreSQL servis+database, RabbitMQ vhost+kuyruklar+DLQ'lar, media bucket/prefix | Yazmaya kapalı karantina; 24 saatlik gözlem ve rollback kapanışından önce silinmez |

İş verisi temiz production'a kopyalanmaz. Eski `sessions`, refresh token'lar ve
cihaz oturumları taşınmaz; ilk production açılışında herkes yeniden giriş yapar.
Eski RabbitMQ kuyrukları yeni vhost'a aktarılmaz veya replay edilmez; üretici ve
tüketiciler durdurulduktan sonra mesaj sayıları kanıt olarak kaydedilir. Eski
media nesneleri yeni production bucket'ına topluca kopyalanmaz. Gerçek bir
kullanıcıya ait olabilecek nesne bulunursa silme yerine karantinada tutulur ve
yasal/veri sahibi incelemesine açılır. İlgisiz altyapı kaynakları envanterde
yalnız “kapsam dışı” olarak işaretlenir.

## 3. Değişmez yedek

[`restore.md`](restore.md) önkoşullarını uygula. Son günlük mantıksal yedek,
fiziksel base backup, WAL zinciri ve `.sha256` nesnelerini doğrula. Object Lock,
versioning, lifecycle ve çapraz bölge replikasyonunun sağlayıcı ekranındaki
durumunu kaydet. RPO 15 dakikayı veya en son günlük yedeğin yaşı 26 saati aşarsa
geçiş başlamaz.

Cutover ön kontrol snapshot'ı için `ops/backup/postgres-backup.sh --dry-run`
çıktısını önce doğrula; asıl yedek onaylı production/pre-production backup
kimliğiyle platformda çalıştırılır. Kanıtta yalnız backup nesne kimliği, UTC
zamanı, checksum sonucu ve KMS key alias bulunur.

## 4. İzole restore provası

Boş, tek kullanımlık recovery database'i oluştur. Secret'ları onaylı store'dan
`RESTORE_DATABASE_URL`, `BACKUP_S3_URI` ve `BACKUP_ENVIRONMENT` olarak ver.
Önce:

```bash
ops/backup/restore-latest.sh --dry-run --tier daily
```

Seçilen nesne, hedef adı ve checksum iki kişi tarafından doğrulandıktan sonra
`RESTORE_CONFIRM_EMPTY_TARGET=I_HAVE_VERIFIED_THE_TARGET_IS_EMPTY` yalnız bu
çalıştırmaya verilir ve aynı komut `--dry-run` olmadan yürütülür. Liquibase
durumu, kritik anonim satır sayıları, foreign key'ler ve salt-okunur uygulama
smoke'u geçmelidir. Başlangıç/bitiş zamanı ve `restore.md` RPO/RTO sonucu kanıta
eklenir; recovery ortamı ancak kanıt kabul edildikten sonra kaldırılır.

## 5. Yazmayı durdur ve kuyruğu boşalt

Maintenance sayfasını aç ve legacy uygulama servisini bütünüyle durdur. Böylece
HTTP yazımlarıyla beraber finansal ve finansal olmayan bütün scheduler'lar,
RabbitMQ producer/consumer'ları ve outbox worker'ları susar. Servisi durdurmadan
önce gerçek topolojideki `email.queue`, `sms.queue`, `push.queue`,
`user.profile.updated.for.sync.queue` ve `email.queue.dlq` sayılarını kaydet.
Finansal retry RabbitMQ kuyruğu değildir; `settlement_tasks` database outbox'ının
bekleyen/işlenen/başarısız satırları ayrıca kaydedilir. `iys_consent_tasks` ve
`account_media_deletion_outbox` için de durum/satır sayısı kanıta eklenir. Devam
eden ödeme veya iade varsa geçişi ertele; mesajları purge etme ve yeni vhost'a
replay etme. Servis durduktan sonra gözlem penceresi boyunca veritabanı audit/WAL
kanıtında sıfır yeni uygulama yazımı olduğunu doğrula, sonra ayrı bir **legacy
quiet-point backup** üretip kimliğini ve checksum'ını kaydet. Bu backup offline
inceleme içindir; temiz production rollback hedefi değildir.

## 6. iyzico Sandbox → Live yapılandırma kapısı

[`payments.md`](payments.md) bölüm 7 tamamlanmadan ödeme açılmaz. Production
üyeliği, marketplace alt üye işyeri özelliği, sözleşme oranları, SFTP ve callback
ayarları ödeme sahibi tarafından doğrulanır. Live anahtarlar yalnız production
secret store'a girilir. İlk production startup'ından **önce**
`SERVICE_ENVIRONMENT=production` ile
`IYZICO_BASE_URL=https://api.iyzipay.com` birlikte hazırlanır; staging Sandbox
anahtarları veya `https://sandbox-api.iyzipay.com` production'a taşınmaz.

Uygulamanın ortam guard'ı production+Sandbox eşleşmesinde açılışı durdurduğu için
bu kapı deploy/startup'tan önce geçilir. Bu doğrulama SMTP'nin UNRESTRICTED
kararından ayrı bir kanıt ve ayrı onay taşır; henüz public trafik açmaz.

## 7. Temiz database ve production yapılandırması

Hedef PostgreSQL servis/database kimliği iki kişi tarafından sesli okunup
cutover kaydıyla eşleştirilir. Platformun onaylı silme/yeniden oluşturma akışı
yalnız hedef boş database üzerinde insan tarafından çalıştırılır. Betik veya
deploy bunu tetiklemez.

Doğrulanmış `v*` image digest'ini production servisine bağla ve 6. kapıda
hazırlanan Live ödeme ayarları dahil
[`environments.md`](../environments.md) production matrisiyle secret adlarını
tek tek denetle. Açılışta Liquibase bütün changeset'leri uygular. Aşağıdaki
kanıtlar olmadan kapı geçmez:

- `qa_marketplace_seed_versions` tablosu ve eski QA dataset'i yoktur;
- `QA_MARKETPLACE_SEED_VERSION`, `QA_WORKER_EMAIL`, `QA_WORKER_PASSWORD`,
  `QA_EMPLOYER_EMAIL` ve `QA_EMPLOYER_PASSWORD` environment'ta yoktur;
- startup sonrasında `00000000-0000-0000-0000-000000000001` kimlikli zorunlu
  `SYSTEM` kullanıcısı vardır; bu satır dışındaki business-user, şirket ve ilan
  sayıları sıfırdır;
- seed veya reset ikinci startup'ta veri üretmez;
- ilk `OWNER` personel bootstrap secret'ı parola değişiminden sonra kaldırılır;
- ilk admin ve gerekli iş kategorileri yalnız admin API üzerinden kontrollü
  oluşturulur.

Bu kapıda uygulama dış trafiğe açılmaz. Production config drift logu, readiness,
DB bağlantısı ve migration sonucu önce içeriden doğrulanır.
Kontroller geçince, trafik öncesi rollback kaynağı olacak ayrı **clean production
baseline backup** alınır; legacy quiet-point backup ile aynı kimliği taşıyamaz.

## 8. SMTP alıcı kapısı

SMTP geçişi ödeme ortamından bağımsız bir onaydır. İlk production açılışı
`MAIL_DELIVERY_MODE=ALLOWLIST` ve yalnız kontrollü lansman alıcılarıyla yapılır.
Bir kayıt/doğrulama e-postasının doğru alıcıya ulaştığını, allowlist dışındaki
alıcının `SUPPRESSED / EMAIL_RECIPIENT_NOT_ALLOWED` kaldığını ve DLQ'nun boş
olduğunu doğrula. Sonra ayrı değişiklik kaydı ve ikinci onayla
`MAIL_DELIVERY_MODE=UNRESTRICTED` production secret store'da uygulanır, uygulama
yeniden başlatılır ve kontrollü bir gerçek alıcıya unrestricted teslimat ayrıca
kanıtlanır. Bu ikinci kanıt yoksa public trafik açılmaz. SMTP kimliği veya alıcı
listesi kanıta kopyalanmaz.

## 9. Trafik, smoke, finansal prova ve kapanış

Önce tersine çevirme kontrolünü kayda yaz: maintenance/ingress/DNS'in hangi
onaylı kontrolle önceki durumuna alınacağı, hedef API servis kimliği ve sorumlusu.
SMTP `UNRESTRICTED`, iyzico Live ve clean production baseline kanıtları hazırsa
maintenance/ingress yalnız doğrulanmış production API'ye çevrilir; UTC zamanı ve
sağlayıcı değişiklik kaydı yazılır. `KEEP_ALLOWLIST` kararıyla public trafik
açılamaz.

Trafik açıldıktan sonra ADR-0005 uyarınca dar production smoke çalıştır:

1. health ve readiness;
2. oturum açma/yenileme/çıkış;
3. zararsız salt-okunur listeleme;
4. yetkisiz isteğin reddedilmesi;
5. log/metric/alarm akışı ve dar smoke sırasında 5xx oluşmaması.

Bu günlük production smoke ilan, başvuru, mesaj, ödeme, iade veya payout üretmez.
Kapsamlı iki taraflı test staging'de kalır. Canlı küçük tutarlı ödeme+iade yalnız
[`payments.md`](payments.md) bölüm 7'deki ayrı finansal değişiklik kaydı, gerçek
kart sahibi ve işlem öncesi açık insan onayıyla yürütülür; deploy smoke'una veya
CI'a eklenmez. Conversation/işlem kimliği, tutar, `mdStatus`, `fraudStatus`,
callback, settlement ve iadenin ekstre tarihi kaydedilir; kart veya secret değeri
kaydedilmez.

Dar smoke başarısızsa yeni trafiği durdur, yeni production'ı yedekle, ödeme ve
bildirim işlerini dondur ve 1. bölümde seçilen Rollback yolunu uygula. Başarılıysa
başlangıç/bitiş UTC zamanları ve alarm gözlem bağlantısı kaydedilen 24 saatlik
gözlem başlatılır. İlk wizard çalışması burada güvenli HOLD ile biter; eski
aşamaları yeniden çalıştırma. En az 24 saat sonra aynı kanıt dosyasıyla yalnız
gözlem kapanışını aç:

```bash
ENV_FILE=.env.launch-cutover ops/launch/launch-cutover-wizard.sh --close-observation
```

Kapanış yolu önceki cutover adımlarını göstermez; başlangıç/bitiş zamanlarını UTC
ISO-8601 biçiminde ve aradaki en az 24 saati kanıtlayan değişmez dashboard/kayıt
kimliğiyle zorunlu tutar. Ancak ondan sonra rollback sorumlusu `PROCEED` kararı
verir. Eski pre-production kaynaklarının silinmesi bu issue'nun dışında ayrı,
hedefli ve onaylı bir değişiklik olur.

## Kanıt özeti

| Kapı | Zorunlu kanıt |
| --- | --- |
| Kapsam | Değişiklik kaydı, kaynak kimlikleri, iki onaylayan, rollback sahibi |
| Backup/restore | Pre-cutover backup ve restore kanıt kimlikleri, checksum, RPO/RTO, anonim sayımlar |
| Sessiz nokta | Maintenance zamanı, tam legacy servis duruşu, kuyruk/DLQ ile `settlement_tasks`/`iys_consent_tasks`/`account_media_deletion_outbox` sayıları, sıfır yeni DB yazımı ve legacy quiet-point backup kimliği |
| Temiz açılış | Image digest, Liquibase sonucu, yalnız SYSTEM kullanıcısı, seed yokluğu, clean production baseline backup kimliği |
| SMTP | Allowlist teslimi+bastırma, ayrı UNRESTRICTED onayı+uygulama+teslim kanıtı |
| iyzico | Live hesap/URL doğrulaması, ayrı ödeme sahibi onayı |
| Trafik/smoke | Tersine çevirme kontrolü, trafik değişiklik kaydı+UTC zamanı, dar smoke, 24 saat başlangıç/bitiş+gözlem ve süre kanıtı, `PROCEED` veya `ROLLBACK` |
