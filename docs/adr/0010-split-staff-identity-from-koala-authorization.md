# Personel kimliğini Keycloak'a, yetkilendirmeyi Koala'da tut

Durum: Kabul edildi — 5 Eylül 2026

## Karar

Back-office personeli marketplace kullanıcılarından tamamen ayrıdır. İki yönde de yabancı anahtar kurulmaz; bir personel kaydı bir `users` satırına bağlanmaz.

**Kimlik ve rol üyeliği Keycloak'ta** (`koala-staff` realm'i): parola, MFA, kilitleme, oturum, parola sıfırlama ve "bu kişi hangi role sahip" bilgisi. Personel Keycloak admin konsolundan yönetilir; panelde personel ekleme veya rol atama ekranı yoktur.

**Yetkilendirme Koala'da**: rolün hangi izinleri içerdiği, roller arası kapsama grafiği ve domain kuralları. İzinler access token'a **gömülmez**; her istekte Koala'nın veritabanından çözülür.

Marketplace kullanıcıları Keycloak'a **taşınmaz**.

## Bağlam

Panel ciro, kişisel veri, şirket onayı ve escrow'dan para çıkışı yönetecek. Bugün bunların hepsi tek bir düz `ROLE_ADMIN` kapısının arkasında: escrow'dan işçiye para salmakla rozet ikonu yüklemek aynı yetki seviyesinde. `Role` bir Java enum'u olduğu için yeni bir rol eklemek kod değişikliği ve deploy gerektiriyor, ve `authorities` tablosunun birincil anahtarı bile yok.

Dört bağımsız mimari üretilip her biri güvenlik, işletilebilirlik ve geri dönülebilirlik merceklerinden değerlendirildi. Dördü de bağımsız olarak aynı üç şeyde birleşti: personel kendi tablolarında yaşamalı, `/admin/**` kendi filtre zincirini ve kendi principal tipini taşımalı, ve izinler token'a konmamalı.

Kimlik tarafında Keycloak seçildi çünkü MFA — kararın gereği olarak ikinci personelden önce zorunlu — kendi elle yazılırsa 1-2 hafta sürer ve IdP'ye geçildiğinde tamamen atılır. Ekip Keycloak'ı zaten biliyor, dolayısıyla öğrenme maliyeti yok.

Rol üyeliğinin de Keycloak'ta tutulması, panelde personel yönetimi ekranlarını tamamen ortadan kaldırdığı için seçildi.

## Neden yetkilendirme Koala'da kalıyor

- **Token'daki izin bayatlar.** Bir yetki geri alındığında token süresi dolana kadar geçerli kalır. Para taşıyan izinlerde kabul edilemez; alternatifi her istekte Keycloak'a introspection çağrısıdır ve bu yerel bir okumadan yavaştır.
- **Kuralların yarısı Koala verisine bakar.** "Taraf olduğu uyuşmazlığı çözemez", "kendi şirketini onaylayamaz", "iade yalnız fiyat snapshot'ındaki tutar kadar" — Keycloak bu veriyi görmez. Domain farkındalıklı yetkilendirme zaten Koala'da olmak zorundadır; geri kalanı da orada tutmak yetkilendirmeyi tek yerde toplar.
- **Denetim kaydı.** Yetki kararını veren interceptor denetim satırını da yazar. Karar Keycloak'ta verilirse Koala'da loglanacak bir karar anı kalmaz.

## Sonuçlar

- Rol atama Koala'nın denetim kaydına girmez; Keycloak'ın kendi log'unda kalır. Rol iptali token yenilenene kadar gecikir — access token ömrü kısa tutularak (~5 dk) yönetilir.
- Panel Keycloak'a bağımlıdır: Keycloak düşerse panele hiç girilemez. Kurtarma planı bu yüzden hem ikinci bir mühürlü hesabı hem Keycloak admin parolasını hem de yazılı bir psql runbook'unu kapsar.
- `staff.manage` ile `role.manage` ayrı izinlerdir. Aynı olsaydı, destek elemanı işe alan kişi kendi rolüne `finance.read` ekleyebilirdi. Ayrıca sahip olunmayan bir izin bir role konamaz ve bu her rol-grafiği yazımında yeniden denetlenir.
- `PayoutService` metotlarına yetki anotasyonu **konmaz**: bunları otomatik tamamlama zamanlayıcısı boş bir SecurityContext ile, kullanıcı iptalleri ve uyuşmazlık çözümü çağırıyor. Personel tarafı ince bir facade üzerinden geçer.
- Roller tek ebeveynli ağaç değil **DAG**'dır: bir rolün iki farklı rolü birden kapsaması gerekiyor.
- Denetim kaydı ikiye bölünür: değişmez çekirdek (24 ay, finansal olanlar 10 yıl) ve silinebilir detay (6 ay ya da KVKK talebi). Bu ayrım ilk satır yazılmadan yapılmalıdır; sonrası yasal süre baskısı altında veri migrasyonudur.
- İzin sabitleri `mutating` ve `sensitivity` metadata'sı taşır; yetki kararını veren nokta denetim satırını da yazdığı için loglamayı unutmak mümkün değildir.
- Personel marketplace hesabı tutabilir, ancak bağ beyan edilir ve panel çıkar çatışmasını engeller.
- Geçiş maliyeti: yetkilendirme Koala'da kaldığı için IdP değiştirmek authenticator'ı değiştirmekten ibarettir.
