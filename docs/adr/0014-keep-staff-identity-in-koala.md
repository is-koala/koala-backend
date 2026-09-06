# Personel kimliğini de Koala'da tut — Keycloak kullanılmıyor

Durum: Kabul edildi — 6 Eylül 2026. [ADR-0010](0010-split-staff-identity-from-koala-authorization.md)'un yerine geçer.

## Karar

Personel kimliği Keycloak'ta değil, Koala'da tutulur. `koala-staff` realm'i kurulmaz; Keycloak projeye hiç girmez.

ADR-0010'un **iki yarısından biri aynen korunur**, diğeri değişir:

- **Yetkilendirme Koala'da** — rolün hangi izinleri içerdiği, roller arası kapsama grafiği, domain kuralları, denetim kaydı. **Değişmiyor.** ADR-0010'un bu yarısı için yazılan her şey yerinde: `StaffRole`, `StaffPermission`, rol→izin DAG'ı, `/admin/roles` uçları, görev ayrılığı kuralı, izinlerin token'a gömülmemesi.
- **Kimlik de Koala'da** — parola, oturum, rol üyeliği. ADR-0010 bunları Keycloak'a vermişti; artık `staff_members` tablosunda ve Koala'nın kendi JWT yığınında.

Personel marketplace kullanıcılarından **ayrı kalmaya devam eder**. İki yönde de yabancı anahtar kurulmaz; bir personel kaydı bir `users` satırına bağlanmaz. ADR-0010'un bu kararı da korunuyor — değişen tek şey kimliğin nerede doğrulandığı.

## Neden

Keycloak, iki kişilik bir ekibin tek bir back-office paneli için ayakta tutacağı bir servis değil. Getirdiği yük, çözdüğü problemden büyük:

- Ayrı bir servis, ayrı bir veritabanı, ayrı bir yedek, ayrı bir sürüm yükseltme hattı.
- **Panel ona bağımlı hâle geliyor**: Keycloak düşerse panele hiç girilemiyor. ADR-0010 bunu "sonuç" olarak kaydetmişti ve karşılığında mühürlü yedek hesap, kasada parola ve yazılı bir psql runbook'u gerektiriyordu — üçü de yalnız bu bağımlılık yüzünden var olan işler.
- Kurulumun kendisi bir gün yiyor ve sonrasında realm, composite roller, client, mapper ve MFA yapılandırması elle sürdürülüyor.

Karşılığında aldığımız iki şey vardı ve ikisi de bugün şart değil:

**MFA.** ADR-0010'un kimlik tarafında Keycloak'ı seçmesinin asıl gerekçesi buydu — elle yazmak 1-2 hafta sürer ve IdP'ye geçilirse atılır. Doğru bir gözlem, ama **MFA bugün yok ve Keycloak kurulsa da açılmamıştı**. Yani ödenen bedel gerçek, alınan fayda henüz değil. MFA gerektiğinde ayrıca değerlendirilir; o gün bir IdP'ye geçmek hâlâ mümkün, çünkü aşağıdaki dikiş bunu ucuz tutuyor.

**Rol üyeliğinin panelde yönetilmemesi.** ADR-0010 personel ekleme ve rol atama ekranlarını yazmamak için üyeliği Keycloak'a vermişti. Bu iş artık yazılacak — ama Keycloak konsolunda personel yönetmek de bedava değildi: ayrı bir arayüz, ayrı bir yetki modeli ve Koala'nın denetim kaydının **görmediği** bir yer. Üyelik Koala'ya gelince rol atama da denetim kaydına giriyor, ki ADR-0010 bunun kaybını kendi "Sonuçlar" bölümünde yazmıştı.

## Bunu ucuz kılan dikiş

`StaffAuthorizationManager.resolve` imzası şu:

```java
public StaffAuthorization resolve(Collection<String> roleCodes)
```

Rol **kodlarını** alıyor; kodların bir Keycloak token'ından mı yoksa bir veritabanı tablosundan mı geldiğini bilmiyor. Bütün yetkilendirme mantığı — kapanış hesabı, izin çözümü, kendini kilitleme koruması — bu imzanın arkasında duruyor ve **hiç değişmiyor**.

Bu yüzden ADR-0010'un tersine çevrilen yarısı, korunan yarısına dokunmadan sökülebiliyor. Aynı dikiş, ileride bir IdP'ye geçilmek istenirse ters yönde de çalışır.

## Rol grafiği ve görev ayrılığı

Bu grafik daha önce `koala-admin/docs/keycloak-setup.md` §3'te yazılıydı; o dosya Keycloak'la birlikte kalktığı için buraya taşındı. İçerik değişmedi.

Yaprak roller: `CATALOG_EDITOR`, `SUPPORT_AGENT`, `FINANCE_ANALYST`, `AUDITOR`.

Kapsayan (composite) roller:

```
MODERATOR         ← SUPPORT_AGENT
DISPUTE_HANDLER   ← SUPPORT_AGENT
COMPANY_APPROVER  ← SUPPORT_AGENT
TRUST_LEAD        ← MODERATOR + DISPUTE_HANDLER
FINANCE_OFFICER   ← FINANCE_ANALYST
OWNER             ← hepsi
```

`TRUST_LEAD`'in iki rolü birden kapsaması, bunun neden ağaç değil **DAG** olduğunun sebebi.

**Görev ayrılığı kuralı:** hiçbir rol hem trust dalını (`MODERATOR`/`DISPUTE_HANDLER`) hem finans dalını (`FINANCE_*`) birden kapsamamalı — `OWNER` hariç.

Grafik `db/changelog/2026/09/06-20-staff-authorization.xml` ile seed'lenir ve `StaffRoleGraphSeedTest` bu kuralı testle sabitler. Rolün *ne yapabildiği* rol→izin tablosunda durur; grafik yalnız kapsamayı söyler.

## Sonuçlar

- **`staff_members.keycloak_subject` kalkar**, yerine parola özeti gelir. Personel e-posta ve parolayla giriş yapar; token'ı Koala üretir.
- **Rol üyeliği için yeni bir tablo gerekir** (`staff_members` ↔ `staff_roles`). Bugün Koala'da böyle bir tablo yok, çünkü üyelik Keycloak'taydı.
- **Panelde personel yönetimi ekranı gerekir**: personel oluşturma, rol atama/geri alma, devre dışı bırakma. Keycloak konsolunun yerini alır.
- **Rol iptali anında geçerli olur.** ADR-0010 bunu "token yenilenene kadar gecikir, ~5 dakikalık token ömrüyle yönetilir" diye kaydetmişti. Üyelik veritabanından okununca gecikme sıfırlanır — beklenmedik bir kazanç.
- **Rol atama denetim kaydına girer.** ADR-0010'un açıkça kayıp yazdığı şey geri gelir.
- **MFA yok.** Bu, ADR-0010'a göre gerçek bir gerileme ve öyle kaydedilmelidir. İkinci personelden önce yeniden değerlendirilmeli; kilitleme tarafı için mevcut hız sınırlayıcı (§2j `AUTH` kovası) bir taban sağlıyor ama MFA'nın yerini tutmuyor.
- **Kurtarma planı basitleşir.** Panel artık dış bir servise bağlı değil; Koala ayaktaysa panel de ayakta.
- ADR-0010'un "Geçiş" bölümündeki marketplace `ROLE_ADMIN` köprüsü, personel girişi indiğinde kapanır. Köprünün ömrü `koala.staff.bridge.open` anahtarına bağlı (is-koala/koala-backend#64) ve o anahtar bu iş bitince kapatılır.

## Alternatifler

**Personeli marketplace kullanıcısı yapmak** (rolle ayrılan bir `users` satırı) daha da az kod olurdu, ama ADR-0010'un "iki yönde de yabancı anahtar yok" kararını da tersine çevirirdi: personel ile müşteri aynı tabloya karışır, ve rol→izin DAG'ının üzerine oturduğu ayrım kaybolur. DAG'ın varlık sebebi görev ayrılığıydı — uyuşmazlık görevlisi ciroyu görmemeli — ve o ayrımı taşıyan şey personelin ayrı bir varlık olması. Reddedildi.

**Keycloak'ı kurup köprüde bırakmak** — yani kurmak ama kullanmamak — en kötüsü olurdu: bedeli öder, faydayı almazdın.
