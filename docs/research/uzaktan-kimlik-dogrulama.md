# Uzaktan kimlik doğrulama — Koala hangi rejime tabi, çıta nerede, ne yapılmalı

Tarih: 6 Eylül 2026
İlgili bölümler: CLAUDE.md §2aq, §2aq2

## Kısa karar

**Koala, BDDK'nın uzaktan kimlik tespiti yönetmeliğine tabi değil. MASAK yükümlüsü de değil — bugün.**
Güven derecesi: **yüksek** (BDDK için ~%95, MASAK için ~%85). Gerekçe üç cümlede:

1. BDDK Yönetmeliği'nin dayanağı Bankacılık Kanunu m.76/2 ve m.93; muhatabı yalnızca **bankalar**.
2. MASAK yükümlüsü listesi Tedbirler Yönetmeliği m.4'te sayılı ve kapalı; pazar yerleri oraya
   25/12/2024'te girdi ama **yalnızca "orta, büyük veya çok büyük ölçekli"** olanlar — eşik bir
   takvim yılında **10 milyar TL net işlem hacmi** (yeniden değerlemeyle artıyor).
3. Alt üye işyerinin TCKN'sini IBAN'la eşleştirip doğrulama yükümlülüğü mevzuat metninde
   **ödeme hizmeti sağlayıcısında**, yani Iyzico'da.

**Bunun sonucu şu: Koala'nın ihtiyacı düzenleyici KYC değil, dolandırıcılık önleme.** Çıta
"denetime hazır ol" değil, "para yanlış IBAN'a gitmesin". İkisi çok farklı yerlere para harcatır.
Ama bir uyarıyla: **6563 (e-ticaret) tarafında Koala'nın zaten bir kimlik doğrulama yükümlülüğü
var** — sadece biyometrik değil, "tanıtıcı bilgileri resmî kaynaktan doğrula" düzeyinde (§1.4).

Aşağıdaki bölümler ayrık tutuluyor: **hukukun istediği** · **sektör pratiği** · **öneri**.
Son bölüm doğrudan "ne yapalım, hangi sırayla, kaça" sorusunu cevaplıyor.

---

## 1. Koala hangi rejime tabi?

### 1.1 BDDK Yönetmeliği Koala'yı bağlamıyor — açık

[Bankalarca Kullanılacak Uzaktan Kimlik Tespiti Yöntemlerine ve Elektronik Ortamda Sözleşme
İlişkisinin Kurulmasına İlişkin Yönetmelik](https://www.mevzuat.gov.tr/mevzuat?MevzuatNo=37680&MevzuatTur=7&MevzuatTertip=5)
(RG 1/4/2021-31441, yürürlük 1/5/2021) m.1/1: amaç "**bankalar** tarafından yeni müşteri
kazanımında kullanılabilecek uzaktan kimlik tespiti yöntemlerine … ilişkin usul ve esasları
düzenlemek". m.2/1: dayanak **5411 sayılı Bankacılık Kanunu m.76/2 ve m.93**.

Yetki normu bankacılık mevzuatından geliyor; BDDK'nın banka olmayan bir pazar yerine bu
yönetmelikle yükümlülük getirme yetkisi yok. Aynı model diğer sektörlerde **ayrı** düzenlemelerle
kuruldu — aracı kurumlar için SPK Tebliği (III-42.1), finansal kiralama/faktoring için ayrı
yönetmelik, ödeme kuruluşları için TCMB Tebliği. Pazar yerleri için böyle bir düzenleme **yok**.

> **Bu bir muafiyet değil, kapsam dışılık.** Yönetmelik Koala'ya uygulanmıyor; Koala isterse
> gönüllü olarak aynı çıtayı hedefleyebilir, kimse engellemiyor. Ama uymamak bir ihlal değil.

Metnin tamamı, düzenlemeyi yapan kurumun sitesinden erişilebilir olmadığı için burada
[Akbank'ın yayımladığı konsolide PDF nüshası](https://www.akbankinvestorrelations.com/tr/images/pdf/bankalarca-kullanilacak-uzaktan-kimlik-tespit-yontemlerine-ve-elektronik-ortamda-sozlesme-iliskisinin-kurulmasina-iliskin-yonetmelik.pdf)
üzerinden alıntılandı (RG metninin birebir çoğaltımı; 25/5/2023-32201 değişikliği işlenmiş).

### 1.2 MASAK / 5549: yükümlü listesi kapalı, pazar yerleri sadece ölçek üstündeyken içeride

5549 sayılı Kanun'un kimlik tespiti yükümlülüğü **"yükümlü"** sıfatına bağlı ve bu sıfat
[Suç Gelirlerinin Aklanmasının ve Terörün Finansmanının Önlenmesine Dair Tedbirler Hakkında
Yönetmelik](https://www.lexpera.com.tr/mevzuat/yonetmelikler/suc-gelirlerinin-aklanmasinin-ve-terorun-finansmaninin-onlenmesine-dair-tedbirler-hakkinda)
(2007/13012) **m.4**'te sayılan kapalı listeyle tanımlı. MASAK Tebliği Sıra No 19 m.3/1(f) de
yükümlüyü aynen "Yönetmeliğin 4 üncü maddesinde sayılanlar" diye tanımlıyor.

25/12/2024 tarihli ve 32763 sayılı RG'de yayımlanan değişiklikle bu listeye şu bent eklendi:

> m.4/1(y): "Elektronik ticaret hizmet sağlayıcılar ile gerçekleştirilen işlemlerle sınırlı olarak
> **orta, büyük veya çok büyük ölçekli** elektronik ticaret aracı hizmet sağlayıcılar."

Yani pazar yerleri MASAK yükümlüsü **oldu**, ama ölçek şartıyla. Uyum takvimi: uyum görevlisi
25/3/2025, ETHS kimlik tespiti 25/4/2025
([Aksan Hukuk bilgi notu](https://aksan.av.tr/elektronik-ticaret-araci-hizmet-saglayicilari-masak-yukumlusu-kilindi/) —
ikincil kaynak, değişikliği yorumluyor).

**Ölçek eşiği Koala'yı dışarıda bırakıyor.** 6563 sayılı Kanun Ek Madde 2, ek yükümlülükleri bir
takvim yılındaki **net işlem hacmi 10 milyar TL** üstünden başlatıyor; üst basamaklar 30 ve 60
milyar TL + işlem sayısı şartı. Eşikler her yıl e-ticaret hacmindeki değişim oranıyla yeniden
değerleniyor (2025 için %62,29, 2026 için %48,07 —
[Ticaret Bakanlığı duyurusu](https://ticaret.gov.tr/duyurular/6563-sayili-elektronik-ticaretin-duzenlemesi-hakkinda-kanundaki-parasal-esiklerin-guncellenmesine-iliskin-aciklama)),
yani eşik yalnızca yukarı gidiyor. **2026 için kesin rakamlar belirsiz** — Bakanlık tabloyu
görsel olarak yayımlıyor, metinden çıkarılamıyor. Ama kesin rakama ihtiyaç yok: alt sınır her
hâlükârda ≥10 milyar TL ve Koala oradan mertebelerce uzakta.

**Bu bir "asla" değil, bir "henüz".** Koala 10 milyar TL net işlem hacmine ulaştığı takvim yılını
takip eden dönemde MASAK yükümlüsü olur ve o gün kimlik tespiti, uyum görevlisi, şüpheli işlem
bildirimi ve 8 yıllık saklama yükümlülükleri **birlikte** doğar. Bugünün kararları o günü
mümkün olduğunca ucuz kılacak şekilde alınmalı; ama bugünden o maliyeti ödemek erken.

**Yükümlü olsaydı bile uzaktan kimlik tespiti otomatik değil.** Tebliğ 19 m.4/1 şart koşuyor:
yükümlüler uzaktan kimlik tespitini ancak "asli faaliyet alanları ile ilgili mevzuatta müşterileri
ile yüz yüze gelinmeksizin kimliğinin doğrulanmasına imkân verecek yöntemlerle sözleşme
kurulmasına cevaz verilmiş olması halinde" kullanabiliyor. Pazar yerleri için böyle bir sektörel
düzenleme henüz yok — kripto varlık hizmet sağlayıcılarına bu boşluk **Geçici Madde 1** ile ayrıca
çözüldü, pazar yerlerine çözülmedi. **Bu nokta belirsiz** ve netleşmesi MASAK'ın pazar yerlerine
özgü bir düzenleme yapmasına bağlı.

### 1.3 Alt üye işyeri: yükümlülük mevzuat metninde Iyzico'da

Koala işçi için Iyzico'da **alt üye işyeri** açıyor ve TCKN'yi IBAN'a bağlıyor
([`IyzicoPaymentService`](../../src/main/java/com/exskylab/koala/core/utilities/payment/iyzico/IyzicoPaymentService.java),
`subMerchantType=PERSONAL` + `identityNumber` + `iban`). Iyzico'nun kendi dokümanı IBAN için
*"Must be consistent with contactName/contactSurname"* diyor
([Submerchant dokümanı](https://docs.iyzico.com/en/products/marketplace/marketplace-implementation/submerchant.md)).

Iyzico bir **ödeme kuruluşu**, yani:

- Tedbirler Yönetmeliği m.4/1'de sayılı bir **MASAK yükümlüsü** (ödeme ve elektronik para
  kuruluşları bendinden).
- [Ödeme Hizmetleri ve Elektronik Para İhracı … Yönetmeliği](https://www.lexpera.com.tr/mevzuat/yonetmelikler/odeme-hizmetleri-ve-elektronik-para-ihraci-ile-odeme-hizmeti-saglayicilari-hakkinda-yonetmelik)
  m.9/3 uyarınca işyeri koduna bağlanan bilgiler **asgari olarak adres + TCKN/VKN** eşleştirmesini
  içeriyor ve "**Ödeme hizmeti sağlayıcısı bu fıkra uyarınca alınacak bilgilerin doğruluğunu teyit
  etmek için gerekli önlemleri almakla yükümlüdür.**" m.10/1 ayrıca hizmet sunumundan önce işyerinin
  sistemde kayıtlı olduğunun kontrolünü, m.10/4 işyeri özelinde izlemeyi istiyor.
- **KPS'ye erişim hakkı olan** taraf. 5490 sayılı Nüfus Hizmetleri Kanunu m.45, Kimlik Paylaşımı
  Sistemi'ne erişebilecekleri sayıyor: Bakanlık, kamu hizmeti sunan tüzel kişiler, sigorta ve
  emeklilik şirketleri, bankalar, Risk Merkezi, bilgi paylaşımı amaçlı kurulmuş şirketler, 6361
  ve 6362 kapsamındaki şirketler ve **6493 sayılı Kanun kapsamındaki ödeme hizmeti sağlayıcıları**.
  Pazar yeri listede yok. **§2aq'da "KPS kapandı" diye kaydedilen durumun hukuki sebebi bu** —
  servis bozulmadı, Koala hiçbir zaman erişim hakkı olan taraf değildi.

**Buradan çıkan tek en önemli mimari sonuç:** TCKN ↔ IBAN ↔ ad-soyad üçlüsünü resmî kaynaktan
doğrulayabilecek taraf Koala değil, Iyzico. §2aq'ın "asıl açığı yüz eşleştirmesi değil IBAN
sahipliği kapatır" tespiti doğru; ve o kontrolü yapabilecek yetkiye sahip taraf zaten zincirin
içinde.

**Iyzico'nun alt üye işyeri için fiilen ne kadar doğrulama yaptığı belirsiz.** Iyzico'nun genel
dokümanında alt üye işyeri için bir KYC/onay adımı tarif edilmiyor; marketplace akışındaki
"Approval" adımı kimlik onayı değil, **ödemenin serbest bırakılması**
([Approval dokümanı](https://docs.iyzico.com/en/products/marketplace/marketplace-implementation/approval.md)).
Bunu **Iyzico'ya yazılı olarak sormak gerekiyor**: "PERSONAL alt üye işyeri oluştururken
`identityNumber` ve `iban` alanlarını KPS/banka nezdinde doğruluyor musunuz; doğrulamıyorsanız
bu doğrulamanın yükümlüsü kim?" Cevap Koala'nın ne inşa etmesi gerektiğini doğrudan değiştirir.

### 1.4 6563 ve ETBİS: burada bir kimlik doğrulama yükümlülüğü **var**

Bu, tartışmanın atlanan tarafı. [6563 sayılı Kanun](https://mevzuat.gov.tr/MevzuatMetin/1.5.6563.pdf)
m.2 tanımları:

- **Elektronik ticaret:** "Fiziki olarak karşı karşıya gelmeksizin, elektronik ortamda
  gerçekleştirilen çevrim içi iktisadi ve ticari her türlü faaliyet."
- **ETAHS:** "Elektronik ticaret pazar yerinde elektronik ticaret hizmet sağlayıcıların mal veya
  **hizmetlerinin** teminine yönelik sözleşme yapılmasına ya da sipariş verilmesine imkân sağlayan
  aracı hizmet sağlayıcı."
- **ETHS:** "Elektronik ticaret pazar yerinde … mal veya hizmetlerinin teminine yönelik sözleşme
  yapan ya da sipariş alan hizmet sağlayıcı."

Tanım "hizmet"i açıkça kapsıyor, yani günlük iş pazarı bu tanımın dışında değil. Ve m.9:

> "Elektronik ticaret aracı hizmet sağlayıcı, elektronik ticaret hizmet sağlayıcının tanıtıcı
> bilgilerini, kendisinden temin ettiği belgeler üzerinden veya **ilgili kurumların erişime açık
> elektronik sistemlerinden doğrular**."

[ETAHS/ETHS Yönetmeliği](https://www.lexpera.com.tr/mevzuat/yonetmelikler/elektronik-ticaret-araci-hizmet-saglayici-ve-elektronik-ticaret-hizmet-saglayicilar-hakkinda-1)
m.6/3 bunu somutlaştırıyor: ETAHS, ETHS'nin tanıtıcı bilgilerini "ilgili kurumların erişime açık
elektronik sistemlerinden, bunun mümkün olmaması halinde ise ETHS'den temin ettiği belgeler
üzerinden doğrular" ve doğruluğu "**her takvim yılında en az bir defa**" kontrol eder. Tacir
olmayan gerçek kişi ETHS için istenen tanıtıcı bilgiler ad, soyad, **TCKN**, e-posta ve telefon.

Buna ek olarak **ETBİS kaydı** ayrı bir yükümlülük: hizmet sağlayıcı ve aracı hizmet sağlayıcılar
faaliyete başlamadan önce e-Devlet üzerinden ETBİS'e kaydolmak zorunda
([Ticaret Bakanlığı ETBİS sayfası](https://ticaret.gov.tr/ic-ticaret/elektronik-ticaret/elektronik-ticaret-bilgi-sistemi-etbis)).
ETBİS kaydı **kimlik doğrulama getirmiyor** — Koala'nın kendisini kaydettirmesiyle ilgili; ama
İYS gibi başka yükümlülüklerin ön koşulu (bkz. `iys-commercial-message-integration.md`).

**Belirsiz olan iki nokta:**

1. **Koala'nın işçisi hukuken "ETHS" mi?** ETHS tanımı "hizmet sağlayıcı" diyor ve 6563 m.2'de
   hizmet sağlayıcı "elektronik ticaret faaliyetinde bulunan" kişi. Arızi olarak gündelik iş yapan
   bir gerçek kişinin "iktisadi ve ticari faaliyet"te bulunup bulunmadığı yorum gerektiriyor.
   Bunu **Ticaret Bakanlığı'ndan görüş** ya da bir hukuk mütalaası netleştirir.
2. **Koala'nın kendisi ETAHS mı, yoksa yalnızca "aracı hizmet sağlayıcı" mı?** İş ilanını işveren
   açıyor, işçi başvuruyor — satıcının hangi taraf olduğu klasik pazar yeri kalıbına birebir
   oturmuyor.

Ama sonuç her iki yorumda da aynı yöne bakıyor: **6563 tarafında istenen şey "TCKN'yi resmî
kaynaktan doğrula", biyometrik uzaktan kimlik tespiti değil.** Ve Koala'nın KPS erişimi olmadığı
için "ilgili kurumların erişime açık elektronik sistemleri" yolu kapalı; geriye "ETHS'den temin
edilen belgeler üzerinden doğrulama" kalıyor — çip okuması tam olarak budur ve **fazlasıdır**.

### 1.5 Özet tablo

| Rejim | Koala'yı bağlıyor mu | Neden | Güven |
|---|---|---|---|
| BDDK Uzaktan Kimlik Tespiti Yönetmeliği | **Hayır** | Dayanak 5411 m.76/2, m.93; muhatap yalnız bankalar | ~%95 |
| MASAK / 5549 (Tedbirler Yön. m.4) | **Hayır, bugün** | m.4/1(y) yalnız orta+ ölçekli ETAHS; eşik ≥10 milyar TL | ~%85 |
| MASAK Tebliğ 19 (uzaktan kimlik tespiti) | **Hayır** | Yükümlü değilken uygulanmıyor; ayrıca sektörel cevaz şartı yok | ~%90 |
| TCMB Ödeme Tebliği (uzaktan kimlik tespiti) | **Hayır** — ama **Iyzico'yu bağlıyor** | Muhatap ödeme/e-para kuruluşları | ~%95 |
| 6563 + ETAHS Yönetmeliği m.6/3 | **Evet, muhtemelen** | Tanıtıcı bilgileri doğrulama + yıllık kontrol | ~%70 |
| ETBİS kayıt | **Evet** | Faaliyet öncesi kayıt zorunlu | ~%90 |
| KVKK | **Evet, koşulsuz** | Veri sorumlusu sıfatı ölçekten bağımsız | %99 |

---

## 2. Referans çıta: düzenleme teknik olarak ne istiyor

Koala bağlı olmasa da bu, "gerçek bankaların yaptığı gibi"nin somut karşılığı. Sorulan beş sorunun
cevabı, iki ayrı rejimde farklı:

| Gereklilik | BDDK Yön. (bankalar) | MASAK Tebliğ 19 (diğer yükümlüler) |
|---|---|---|
| Canlı görüntülü görüşme | **Zorunlu** (m.4/1) | **Zorunlu** ama yapay zekâ ile olabilir (m.4/7, m.4/9) |
| İnsan müşteri temsilcisi | **Zorunlu** (m.4/1, m.5) | **Zorunlu değil** — tamamen YZ olabilir (m.4/9-a) |
| Canlılık testi | **Zorunlu** (m.8/1) | **Zorunlu** (m.4/A-2) |
| Deepfake'e karşı ilave tedbir | **Zorunlu** (m.8/1) | **Zorunlu** (m.4/A-3-b) |
| Çip (NFC) okuma | **Zorunlu değil, ama alternatifi pahalı** (m.7/2-3) | Aynı (m.4/A-3-a) |
| Yüz ↔ çip fotoğrafı eşleştirme | **Zorunlu** (m.8/2) | **Zorunlu** (m.4/A-3-b) |
| SMS OTP | **Zorunlu** (m.6/7) | **Zorunlu** (m.4/A-3-c) |
| Sürecin tamamının kaydı | **Zorunlu** (m.10/1) | **Zorunlu** (m.4/7) |
| Saklama süresi | Yönetmelikte **yok**, atıf var | 5549 m.8: **8 yıl** |

**Canlı görüntülü görüşme zorunlu mu? Evet.** BDDK m.4/1: "Uzaktan kimlik tespiti, **müşteri
temsilcisi ile kişinin**; fiziksel olarak aynı ortamda bulunmasına gerek olmadan, **çevrim içi
olarak görüntülü görüşmesi** ve birbiriyle iletişim kurması ile yapılır." m.6/5 bunun "gerçek
zamanlı ve kesintisiz" ve "uçtan uca güvenli iletişim" ile yapılmasını istiyor. Tebliğ 19 m.4/7
aynı şeyi tüm yükümlüler için söylüyor: "süreç; çevrimiçi, kesintisiz, görüntülü ve gerçek zamanlı
olarak yürütülür."

Tebliğ 19 m.4/9 (11/8/2023 değişikliği) insan temsilciyi opsiyonel yaptı, **ama bedeli var**: YZ
ile yüz karşılaştırma veya canlılık testi yapılıyorsa, algoritmanın **yanlış onay vakasının on
milyonda birin altında** olduğunu gösteren bir **TSE raporu** gerekiyor. İstisna: yurt dışı menşeli
sağlayıcı "uluslararası geçerliliği bulunan bir sertifika" sahibiyse TSE raporu aranmıyor. TSE bu
belgelendirmeyi TÜBİTAK BİLGEM iş birliğiyle yürüteceğini duyurdu
([TSE sayfası](https://www.tse.org.tr/uzaktan-kimlik-tespiti-yapay-zeka-algoritmalari-ve-tse-belgelendirme-sureci/)).

**Canlılık testi zorunlu mu? Evet, iki yerde de.** BDDK m.8/1: "kişinin canlılığını tespit edici
yöntemler kullanılır. Banka **sahte yüz teknolojisine dair riskleri önlemeye yönelik ilave
tedbirler** alır." Yöntem, eşik veya sertifika seviyesi **belirtilmiyor** — mevzuat "canlılık" diyor,
ISO/IEC 30107-3 seviyesi demiyor. Seviye beklentisi sektör pratiğinden geliyor (§4.4).

**Çip okuma zorunlu mu? Hayır — ama okunmazsa iki ek yük geliyor.** BDDK m.7/2 NFC ile doğrulamanın
neyi kanıtladığını tanımlıyor: belgenin yetkili makamca verildiği, yonga üzerindeki bilgilerin
değiştirilmediği ve yonga anahtarlarının kopyalanarak oluşturulmadığı. m.7/3: NFC ile
doğrulanamazsa (a) **en az dört görsel güvenlik unsurunun** şekil ve içerik bakımından doğrulanması
ve (b) ilk finansal hareketin **KYC uygulanmış başka bir bankadaki kendi hesabından** yapılması
zorunlu. Tebliğ 19 m.4/A-3(a) ve m.6/4 aynı yapıyı kuruyor. Yani NFC "zorunlu değil" ama
alternatifi, insan gözüyle hologram/giyoş incelemesi + banka hesabı köprüsü.

**Yüz eşleştirmenin kaynağı önce çip.** BDDK m.8/2: "kişinin yüzü ile kimlik belgesinden yakın alan
iletişimi kullanılarak **alınabilmesi halinde temassız yongadaki fotoğrafın**, alınamaması halinde
ise kimlik belgesi üzerinde yer alan fotoğrafın biyometrik karşılaştırması yapılır." Yani DG2
tercih ediliyor, karttaki basılı fotoğraf yedek.

**Özel nitelikli veri kullanımı sınırlanmış.** BDDK m.6/2: "özel nitelikli kişisel verilerden
**sadece biyometrik verisi** kullanılabilir ve kişinin buna dair **açık rızası elektronik ortamda
kayıt altına alınır**."

**Kayıt ve saklama.** BDDK m.10/1 sürecin tamamının "tüm adımlarını içerecek ve denetlenebilir
olmasını sağlayacak şekilde" kaydedilip saklanmasını istiyor ama **süre vermiyor** — "Bilgi ve
belge saklama gerekliliklerine ilişkin ilgili mevzuat hükümleri saklıdır" diyerek atıf yapıyor.
Süreyi veren norm **5549 sayılı Kanun m.8**: belgeler düzenleme tarihinden, defterler son kayıt
tarihinden, **kimlik tespitine ilişkin belgeler son işlem tarihinden itibaren sekiz yıl**.

**"Yüksek riskli işlem" nasıl tanımlanıyor?** Tek bir tanım yok; üç ayrı katman var.

- **Tutar eşiği (kimlik tespitini tetikleyen):** Tedbirler Yönetmeliği m.5, 25/12/2024-32763
  değişikliğinden sonra — elektronik transferlerde **15.000 TL**, diğer işlemlerde **185.000 TL**
  (birbiriyle bağlantılı işlemlerin toplamı dâhil).
- **Uzaktan kimlik tespitiyle kurulan ilişkinin kendisi risklidir:** Tebliğ 19 m.4/5 bu ilişkiye
  "risk temelli yaklaşımla **sıkılaştırılmış tedbirler**" uygulanmasını, BDDK m.11/1 bu kişilerin
  "**farklı bir risk profilinde**" izlenmesini emrediyor.
- **Sıkılaştırılmış tedbirlerin içeriği:** Tebliğ 19 m.6/3 — ilave bilgi, üst düzey onay, kontrol
  sıklığını artırma, ilk finansal hareketi başka bir finansal kuruluştan zorunlu tutma, tutar ve
  işlem sayısı limiti koyma.

**İspat yükü kimde:** BDDK m.11/1 son cümlesi — "Müşteriye ya da üçüncü bir tarafa yükümlülük
doğuran işlemlerde **itiraz halinde ispat yükümlülüğü bankadadır**." Koala için bağlayıcı değil,
ama Koala'nın kanıt saklama tasarımının niçin ciddiye alınması gerektiğini gösteriyor: ihtilafta
"doğruladık" demek yetmez, gösterebilmek gerekir. §2aq2'nin `identity_evidence_digest` +
`identity_document_signer` kararı tam olarak bu ihtiyacı karşılıyor.

---

## 3. KVKK ve biyometrik veri

### 3.1 Yüz eşleştirme ve canlılık verisi özel nitelikli mi? Evet.

KVKK m.6/1 biyometrik veriyi özel nitelikli kişisel veriler arasında sayıyor. Kurum'un
[Biyometrik Verilerin İşlenmesinde Dikkat Edilmesi Gereken Hususlara İlişkin Rehber](https://www.kvkk.gov.tr/SharedFolderServer/CMSFiles/bd06f5f4-e8cc-487e-abe1-d32dc18e2d7e.pdf)'i
yüz tanımayı açıkça "fizyolojik nitelikli biyometrik veri" olarak sayıyor ve Danıştay 15. Dairesi'nin
2014/4562 E. sayılı kararındaki tanıma dayanıyor.

**İnce ama önemli ayrım:** Bir fotoğraf tek başına "kişisel veri"; **kimliği tekil olarak teyit
etmek amacıyla spesifik teknik işlemeye tabi tutulduğunda** biyometrik veri hâline gelir. Yani:

- Kullanıcının profil fotoğrafı → kişisel veri.
- Çipten okunan DG2 → kişisel veri (fotoğraf), **eşleştirmede kullanıldığı anda** biyometrik veri.
- Selfie + DG2'nin bir yüz eşleştirme motoruna verilmesi → **özel nitelikli kişisel veri işleme**.
- Canlılık testi → yüz görüntüsü üzerinde spesifik teknik işleme; **aynı kategoride** sayılmalı.

### 3.2 Açık rıza dışında hukuki sebep var mı? Pratikte hayır.

KVKK m.6/3'ün (7499 sayılı Kanun ile değişik) sebepleri Rehber'de aynen sayılıyor: (a) açık rıza,
(b) **kanunlarda açıkça öngörülmesi**, (c) fiili imkânsızlık, (ç) alenileştirme, (d) bir hakkın
tesisi/kullanılması/korunması için zorunluluk, (e) sağlık/sır saklama yükümlülüğü, (f) istihdam,
iş sağlığı ve güvenliği, sosyal güvenlik alanındaki **hukuki yükümlülükler**, (g) vakıf/dernek/
sendika istisnası.

Koala için tek tek:

- **(b) kanunlarda açıkça öngörülmesi** — bankalar ve MASAK yükümlüleri bu bende dayanabilir;
  onlar için biyometrik karşılaştırma mevzuatta yazılı. **Koala dayanamaz**, çünkü §1'de kurulduğu
  gibi o mevzuat Koala'yı bağlamıyor. Rehber ayrıca "söz konusu hükmün **şüpheye yer bırakmayacak
  kadar açık** olması gerektiği" değerlendirmesini yapıyor — dolaylı çıkarım yetmez.
- **(f) istihdam alanındaki hukuki yükümlülükler** — Koala işveren değil, aracı. Bu bent Koala'nın
  kendi çalışanları için olabilir, platform kullanıcıları için değil.
- **(d) bir hakkın tesisi/korunması** — dolandırıcılık savunması bu bende sokulmaya çalışılabilir,
  ama Kurul'un bugüne kadarki yorumu dar; kitlesel, önleyici, herkese uygulanan bir işlemeyi
  "zorunluluk" saymaz. **Zayıf ve riskli.**

**Geriye açık rıza kalıyor — ve açık rıza tek başına yetmiyor.** Rehber, m.6/3 şartının yanında
**m.4 genel ilkelerine** de uyulmasını istiyor ve ölçülülük testini şöyle kuruyor: "biyometrik veri
işlemenin yerine **herhangi bir alternatifin mevcut olması durumunda** biyometrik verinin işlenmesi
gerekli olmayacağından söz konusu veriler işlenemeyecektir."

Bu soyut bir uyarı değil. Kurul, **29/4/2026 tarihli ve 2026/921 sayılı ilke kararında** (RG
2/6/2026) mesai takibi amacıyla biyometrik veri işlemenin m.6/3'teki hiçbir şarta dayanmadığını ve
**geçerli açık rıza olsa dahi m.4'teki ölçülülük ölçütünü karşılamayacağını** değerlendirdi;
alternatif olarak şifreli kart, PIN, RFID/NFC kart gibi yöntemleri gösterdi
([Kurum duyurusu](https://www.kvkk.gov.tr/Icerik/8762/mesai-takibi-amaciyla-biyometrik-veri-islenmesi-hakkinda-kisisel-verileri-koruma-kurulunun-29-04-2026-tarihli-ve-2026-921-sayili-ilke-karari-na-iliskin-kamuoyu-duyurusu)).
İşçi–işveren ilişkisindeki güç dengesizliği ayrı bir gerekçeydi; **platform–kullanıcı ilişkisinde
aynı dengesizlik argümanı doğrudan kurulamaz**, ama "alternatif varsa biyometri ölçüsüzdür" ilkesi
sektörden bağımsız.

> **Koala için pratik sonuç:** Yüz eşleştirmeyi savunulabilir kılan şey rıza metni değil,
> **alternatifin gerçekten olmadığını gösterebilmek.** Ve Koala'nın çözmek istediği problemin —
> "para saldırganın IBAN'ına gitmesin" — daha az müdahaleci bir alternatifi **var**: IBAN sahipliği
> doğrulaması (§7). Bu, yüz eşleştirmesinin ölçülülük savunmasını zayıflatan en ciddi olgu.
> Rehber'in idari tedbirler bölümü ayrıca biyometrik çözümü kullanamayan veya kullanmaya rıza
> vermeyenler için "**herhangi bir kısıtlama veya ek maliyet olmaksızın alternatif bir sistem**"
> sağlanmasını istiyor — yani rızaya dayalı bir yüz eşleştirmesi kurulacaksa, rıza vermeyene aynı
> hizmeti veren ikinci bir yol zaten inşa edilmek zorunda.

Rehber'in **teknik tedbirler** listesi de aynen uygulanır: bulut ortamında yalnızca kriptografik
yöntemlerle saklama, türetilmiş verinin orijinali geri vermeyecek biçimde tutulması, şablonların
şifrelenmesi ve açık bir anahtar yönetimi politikası, test ortamlarında **sentetik veri**, yetkisiz
erişimde uyarı/silme, biyometrik veriyi işleyen yazılım üzerindeki kullanıcı işlemlerinin
izlenebilmesi.

### 3.3 Cihazda tutmak ile sunucuya göndermek arasındaki fark

**KVKK mevzuatında bu ayrımı yapan açık bir hüküm yok** — Rehber cihaz/merkez ayrımına doğrudan
girmiyor. Ayrım fiilî ve risk temelli, ama sonuçları çok somut:

| Boyut | Cihazda kalırsa | Sunucuya giderse |
|---|---|---|
| Koala veri sorumlusu mu | **Evet** — amacı ve vasıtayı Koala belirliyor | Evet |
| Açık rıza gerekiyor mu | **Evet** (m.6/3-a) | Evet |
| Aydınlatma | Gerekiyor | Gerekiyor |
| VERBİS/işleme envanteri | Gerekiyor | Gerekiyor |
| Kurum'a bildirilecek veri ihlali riski | **Neredeyse yok** — merkezde veri yok | Ciddi; özel nitelikli veri ihlali |
| Saklama/imha politikası | Cihaz ömrüyle sınırlı, basit | Şifreleme + anahtar yönetimi + imha kanıtı |
| İşleyen (yurt içi/dışı) sözleşmeleri | Yok | Gerekiyor |
| **Yurt dışına aktarım rejimi** | **Tetiklenmiyor** | **Tetikleniyor** (§3.4) |
| 2018/10 sayılı Kurul kararındaki yeterli önlemler | Kapsam dar | Tam kapsam |

**Yanlış anlaşılmaması gereken nokta: cihazda tutmak Koala'yı veri sorumlusu olmaktan
çıkarmıyor.** Uygulamayı Koala yazıyor, işlemenin amacını ve vasıtasını Koala belirliyor. Cihazda
tutmak **yükümlülüğü kaldırmıyor, riski ve maliyeti düşürüyor** — ki bu zaten yeterince değerli.

**Karşılaştırmalı destek (bağlayıcı değil, ikna edici):** EDPB'nin havalimanlarında yüz tanıma
kullanımına ilişkin [Opinion 11/2024](https://www.edpb.europa.eu/system/files/documents/2024-05/edpb_opinion_202411_facialrecognitionairports_en.pdf)'ü,
dört saklama modelini karşılaştırıp yalnızca **biyometrik verinin bireyin elinde tutulduğu** ya da
merkezde şifreli tutulup **anahtarın yalnızca bireyde olduğu** modellerin bütünlük/gizlilik,
tasarımdan itibaren koruma ve işleme güvenliği ilkeleriyle bağdaşabileceği sonucuna vardı. Bu bir
AB görüşü, Türk hukukunda bağlayıcı değil; ama Koala'nın kararının uluslararası en iyi uygulamayla
aynı hizada olduğunu gösteriyor.

### 3.4 Yurt dışına aktarım: burada rıza yolu kapalı

7499 sayılı Kanun'la değişen KVKK m.9 üç kademeli. Ve kritik olgu:
**Kurul bugüne kadar hiçbir ülke için yeterlilik kararı vermedi** —
[Kurum'un kendi sayfası](https://www.kvkk.gov.tr/Icerik/2053/Yurtdisina-Aktarim): "Bu konuda Kurul
tarafından **henüz bir belirleme yapılmamıştır**."

Dolayısıyla selfie/DG2'yi yurt dışındaki bir yüz tanıma servisine göndermek şu yollardan birini
zorunlu kılıyor:

1. **Standart sözleşme** (m.9/4) — Kurul'un yayımladığı metinlerden aktarım tipine uygun olanı
   imzalanır; imzadan itibaren **beş iş günü** içinde Kurum'a bildirilir (m.9/5). Bildirmemek
   m.18/1(d) uyarınca idari para cezası sebebi. Bildirim; imzalı nihai metin, imza yetkisi
   belgeleri ve yabancı dildeki belgelerin **noter onaylı çevirisi** ile yapılır.
2. **Bağlayıcı şirket kuralları** — grup içi aktarım için; Kurul onayı gerekiyor.
3. **Taahhütname** — Kurul'un ayrıca izni gerekiyor.

**Açık rıza (m.9/6-a) bu iş için kullanılamaz.** Kurum'un
[Kişisel Verilerin Yurt Dışına Aktarılması Rehberi](https://www.kvkk.gov.tr/Icerik/8142/Kisisel-Verilerin-Yurt-Disina-Aktarilmasi-Rehberi)
açık: istisnai aktarım halleri "ancak **arızi** nitelik arz ettiğinde yani tek veya birkaç sefer
gerçekleşen, süreklilik arz etmeyen ve **olağan faaliyet akışı içinde bulunmayan** durumlarda"
kullanılabilir; "veri aktaran ile veri alıcısı arasında süregelen bir ilişkinin sonucu olarak
düzenli bir şekilde gerçekleşen kişisel veri aktarımları kural olarak sistematik ve sürekli
tekrarlanan bir veri aktarımı olarak kabul edilir."

Her kayıt olan işçinin selfiesinin bir SaaS'a gitmesi, tanımı gereği **olağan faaliyet akışı**.
Yani "kullanıcıdan rıza aldık" cümlesi bu aktarımı meşrulaştırmaz. **Yurt dışı bir yüz tanıma
servisi kullanılacaksa standart sözleşme + Kurum bildirimi zorunludur.**

### 3.5 Koala'nın "biyometri cihazda kalsın" kararı hukuken sağlam mı?

**Evet — ve sunucu doğrulaması gerekse bile büyük ölçüde ayakta kalır.** Üç ayrı gerekçe:

1. **En az müdahaleci vasıta.** Rehber'in ölçülülük testi ve 2026/921 sayılı ilke kararının
   mantığı bunu doğrudan destekliyor: aynı sonucu veren daha az müdahaleci yol varsa o seçilmeli.
   Cihazda eşleştirme, merkezde eşleştirmeyle **aynı doğrulama sonucunu** üretir ve merkezde hiç
   özel nitelikli veri biriktirmez.
2. **Yurt dışı aktarım rejiminden tümüyle kaçınıyor.** §3.4'teki standart sözleşme + bildirim +
   noter çevirisi yükü hiç doğmuyor.
3. **İhlal yüzeyi yok.** Merkezde biyometrik veri yoksa, biyometrik veri ihlali de yok.

**Sunucu doğrulaması gerekirse karar tamamen çökmez, kademelenir.** Dört seçenek, artan
müdahalelilik sırasıyla:

| Seçenek | Sunucuya giden | KVKK yükü |
|---|---|---|
| A. Cihazda eşleştir, sonucu bildir (bugünkü tasarım) | Yalnız `true/false` | En düşük; sunucuda biyometri yok |
| B. Cihazda eşleştir + cihaz bütünlüğü kanıtı | Boolean + attestation token | A ile aynı; attestation biyometrik veri değil |
| C. Sunucuda eşleştir, **yurt içinde** | DG2 + selfie | Özel nitelikli veri işleme; sınır ötesi yok |
| D. Sunucuda eşleştir, **yurt dışında** | DG2 + selfie | + standart sözleşme + 5 iş günü bildirim |

**C ile D arasındaki fark, C ile A arasındaki farktan daha keskin.** Sunucuda eşleştirme
gerekiyorsa, **yurt içinde işleyen bir sağlayıcı seçmek** hukuki yükün en büyük parçasını
tek başına ortadan kaldırır.

---

## 4. Sunucuda doğrulanabilir yüz eşleştirme

### 4.1 En önemli bulgu: cihazda yapılan eşleştirme sunucuya **kanıtlanamaz**

Platform ilkelleri (Android Play Integrity + Key Attestation, iOS App Attest / DeviceCheck,
`BiometricPrompt` + `CryptoObject`) **kimin çalıştığını** ve **cihazda kayıtlı bir biyometriğin bir
anahtarı açtığını** kanıtlar. Hiçbiri **belirli bir karşılaştırmanın yapıldığını**, hangi görüntüler
üzerinde yapıldığını ya da sonucunun ne olduğunu kanıtlamaz.

`setUserAuthenticationRequired(true)` + `AUTH_BIOMETRIC_STRONG` ile üretilmiş bir anahtarın imzası
şunu kanıtlar: *bu cihazda, bu imzadan hemen önce, cihaza kayıtlı bir Class 3 biyometrik doğrulama
yaptı ve bunu güvenli donanım zorladı.* Kanıtlamadığı şeyler:

- **Kimin yüzü.** Kayıtlı biyometrik telefonu kuran kişiye ait; **kimlik belgesinin sahibiyle
  hiçbir bağı yok.** Kendi yüzünü kaydeden bir saldırgan birebir aynı geçerli imzayı üretir.
- **Hangi modalite.** `getAuthenticationType()` yalnız `BIOMETRIC` / `DEVICE_CREDENTIAL` / `UNKNOWN`
  döndürüyor; uygulama parmak izi mi yüz mü kullanıldığını **kendisi bile öğrenemiyor**.
- **Çip fotoğrafıyla bir karşılaştırma yapıldığını.** Çip fotoğrafı biyometrik alt sisteme hiç
  girmiyor. Apple sınırı açıkça yazıyor: Secure Enclave *"geri yalnızca bir geçti/kaldı sonucu
  döndürür"* ve altındaki doğrulama verisine hiçbir yazılım erişemez. İki platformda da donanım
  eşleştiricisine rastgele bir görüntü verip imzalı bir skor almanın yolu yok.
- **Herhangi bir canlılık özelliği.** Class 3'ün SAR ≤ %7 eşiği telefon kilidi için bir uyumluluk
  tabanı; ISO/IEC 30107-3 anlamında KYC seviyesi bir PAD sonucu değil.

Apple'ın kendi belgesi durumu tek cümlede söylüyor: *"uygulamanızın mantığına kendi üzerinde
güvenlik denetimi yaptırmaya güvenemezsiniz, çünkü ele geçirilmiş bir uygulama sonuçları
uydurabilir."*

En yakın istisna **Android Protected Confirmation** — TEE'nin çizdiği, metni imzalanan bir onay
kutusu. Ama *bir insanın bir cümleyi okuyup onayladığını* kanıtlıyor, bir biyometrik sonucu değil;
üstelik yalnız desteklenen cihazlarda ve hiç yaygınlaşmadı.

**Sonuç: sunucuya doğrulanabilir tek mimari, karşılaştırmanın ve PAD kararının sunucuda yapılmasıdır.**
Cihaz bütünlüğü kanıtı (Play Integrity / App Attest) gerekli bir tamamlayıcıdır, ikame değil.

### 4.2 ISO/IEC 30107-3 — ve satıcı sunumlarındaki üç yanlış

**Standart ürünü değil ölçümü standartlaştırıyor.** "ISO 30107-3 sertifikalı ürün" diye bir şey
yok; yalnız standardın raporlama şartlarına uyan bir laboratuvar raporu var. Standardın kendisi
uyarıyor: *"PAD mekanizmalarının hata oranları benzer testler arasında zorunlu olarak
karşılaştırılabilir değildir ve farklı laboratuvarlarca zorunlu olarak yeniden üretilebilir
değildir."*

Tanımlı metrikler (Madde 3.2): **APCER** (saldırı sunumunun gerçek sanılma oranı — **PAI türü
başına**, tek bir küresel sayı değil), **BPCER** (gerçek sunumun saldırı sanılma oranı), **IAPAR**
(2017'deki adı IAPMR), ve yanıtsızlık oranları.

Üç yaygın yanlış:

1. **ACER bir ISO/IEC 30107-3 metriği değil.** (APCER+BPCER)/2 formülü Madde 3.2'de geçmiyor,
   NIST'in kendi standart anlatımında geçmiyor, iBeta'nın zorunlu metrik listesinde geçmiyor.
   Üstelik ortalamak standardın mantığına aykırı: standart PAI türlerinin **ayrı ayrı** analiz
   edilmesini tam da tek bir ortalamanın içeri sızan türü gizlemesi yüzünden istiyor.
   **Satıcı sunumunda "ISO 30107-3'e göre ACER" görüyorsan bu bir kırmızı bayraktır.**
2. **"Seviye 1 / 2 / 3" ISO'nun değil, iBeta'nın kurgusu.** Standardın 7. maddesindeki "seviyeler"
   sistemin **hangi parçasının** test edildiğini anlatıyor (alt sistem / yakalama / tam sistem),
   saldırı zorluğunu değil. iBeta bunu kendisi söylüyor. Seviyeler: **1** = ≤30 $ malzeme, %0
   geçiş; **2** = ≤300 $ malzeme, %1 geçiş; **3** = hiper-gerçekçi maske, %5 geçiş. BPCER tavanı
   Seviye 1–2 için %15.
3. **Seviye 2 ayırt edici değil.** iBeta'nın yayımladığı 277 mektubun 166'sı Seviye 1, 99'u
   Seviye 2, **yalnız 3'ü Seviye 3**. Seviye 2 masaya oturma bedeli.

Ve kapsam sınırı: FIDO'nun kendi belgesi *"enjeksiyon saldırıları PAD testinin kapsamı dışındadır"*
diyor. Bir iBeta Seviye 2, sanal kameraya sentetik video besleyen saldırgan hakkında **hiçbir şey**
söylemiyor — ki uzaktan işe alımda bugün baskın saldırı budur.

### 4.3 MASAK'ın "on milyonda bir" şartı metrolojik olarak sorunlu

MASAK Genel Tebliği 19, Md. 4(9)(b) yapay zekâ algoritmasının *"yanlış onay vakasının on milyonda
birin altında"* olduğunu gösteren TSE raporu istiyor (yurtdışı menşeli kuruluş uluslararası geçerli
bir sertifika taşıyorsa TSE raporu aranmıyor).

İki sorun var:

- **NIST bu sayıyı hiç raporlamıyor.** FRTE 1:1 raporunun tamamında FMR = 1e-7 sonucu **yok**; en
  düşük eşik **1e-6**. Sebebi istatistiksel: sahtekâr karşılaştırma sayısı ~10⁸ mertebesinde ve
  "üçler kuralı" tabanı ≈3×10⁻⁸ — yani 1e-7 böyle bir korpusun çözebileceğinin tam sınırında.
- **Şart canlılık testine de uygulanıyor ama FMR bir *eşleştirme* metriği.** Canlılık algoritmasının
  FMR'si yoktur; güvenlik metriği APCER/IAPAR'dır ve iBeta bunları %0/%1/%5 ile sınırlıyor — yani
  altı yedi kat büyüklük daha gevşek.

**Hiçbir satıcının — ne Türk ne yabancı — 1e-7'nin altını belgeleyen bir TSE ya da akredite
laboratuvar raporu yayımladığı bulunamadı.** Bu şart metrolojik değil idari olarak karşılanıyor
görünüyor. Koala MASAK yükümlüsü olmadığı için (§1) bugün bağlayıcı değil, ama eşiği aşıldığı gün
bu sorunun cevabı doğrudan TSE'ye sorulmalı.

Not: Md. 4(8) dış hizmet alınan kuruluş için **TS EN ISO/IEC 27001**'i zorunlu tutuyor — bu bir
ayırt edici değil, taban.

### 4.4 Türkiye pazarı — üç sert bulgu

**① iBeta'nın ISO 30107-3 mektup listesinde tek bir Türk şirketi yok.** 277 satırın tamamı
Türkiye / Türkiye / İstanbul / Ankara ve bu araştırmada bulunan her Türk satıcı adı için tarandı.
**Sıfır sonuç.**

**② İHS Teknoloji'nin "iBeta Seviye 2" belgesi bir İngiliz şirketine ait.** Bağlantı verilen
mektuplar **Fraud.com International LTD** (Londra, şirket no. 11269440) adına düzenlenmiş; İHS
Türkiye ortağı/hizmet sağlayıcısı. Mektup gerçek (12 Nisan 2022, Udentify 1.0, 6 denek, 6 PAI türü,
%0 geçiş) — ama İHS'nin değil.

**③ NIST kayıtlarında üç Türk geliştirici var, ve biri satıcı izleniminin tersini söylüyor.**
FRTE 1:1 katılımcı tablosunda, ülke kodu TR: **Papilon Savunma** (`papsav1923-003`, FNMR 0,0034 @
FMR 1e-6), **Turkcell Technology** (`turkcell-001`, 0,0043), **Techsign** (`techsign-001`, 0,0087).
Sınıfın en iyisi ≈0,0015–0,0019 olduğuna göre Papilon ve Turkcell saygın orta-üst alan.

Ama **Techsign NIST FATE PAD'deki tek Türk katılımcı ve canlılık sonuçları kötü**: en kolay
kategoride (baskı fotoğraf / replay) `techsign-001` **APCER 0,53**, `techsign-000` **0,72** —
liderler 0,000'da. Bu, bağımsız kanıtın pazarlama izlenimiyle doğrudan çeliştiği nadir bir durum.

**Ve brief'te sayılan on Türk şirketin altısı kimlik işinde bile değil:** Vispera (raf görüntü
tanıma), Sestek/Knovvu (**yalnız ses** biyometrisi), ARDIC (IoT), Netaş, Kuika, Hipotenüs. Üçü
(Fintechhub, Toohoo, Vidyodijital) Türk şirketi olarak doğrulanamadı. Yalnız **Identify**
(identify.com.tr) gerçek bir IDV satıcısı — NFC var, canlılık sertifikası **yok**.

### 4.5 Veri yerleşimi — asıl süzgeç bu

MASAK Md. 4/A(3) NFC okumayı zaten masaya oturma bedeli yapıyor; ciddi satıcıların hepsinde var.
Gerçek süzgeçler ikisi:

- **Veri Türkiye'de kalabiliyor mu?** Yalnız **Regula'nın on-premise dağıtımı** ve **İHS'nin yurt
  içi barındırması** bunu karşılıyor; Sumsub sözleşmeye bağlı bir "belki". Veriff, Onfido/Entrust,
  Jumio, iDenfy, Signicat'in hiçbiri Türkiye bölgesi sunmuyor.
- **Canlılık sertifikası satıcının kendisinin mi?** Bu ölçüt iDenfy'ı (canlılığı FaceTec'ten alıyor)
  ve Signicat'i (iProov'dan alıyor) doğrudan sertifikalı taraf olmaktan çıkarıyor,
  Onfido/Entrust'ın Seviye 2 iddiasına soru işareti koyuyor (en yeni mektup **Seviye 1**), ve her
  Türk satıcının ya sertifikasız olduğunu ya da yabancı ortağının mektubunu ödünç aldığını gösteriyor.

En güçlü PAD kâğıt izi **Yoti**'de: yayımlanmış üç Seviye 3 mektubundan biri onun — ama
eşleştirmede 444 geliştirici içinde **251.** sırada ve FATE PAD'de sondan ortak birinci. Sertifika
ile doğruluk aynı şey değil. **IDEMIA** FATE PAD'de baskı/replay kategorisinde ortak birinci
(APCER 0,000).

### 4.5b Doğruluk sıralaması — canlı liderlik tablosundan

NIST'in "Accuracy By Developer" tablosu (444 geliştirici, 2026-08-31) satıcı sayfalarının verdiği
izlenimle çelişiyor. VISABORDER, FMR = 1e-6, FNMR (düşük daha iyi):

| Geliştirici | FNMR | Sıra |
|---|---|---|
| Paravision | 0,0015 | 6. |
| Innovatrics | 0,0017 | 10. |
| IDEMIA | 0,0020 | 17. |
| HyperVerge | 0,0027 | 68. |
| **Papilon Savunma (TR)** | 0,0034 | — |
| **Turkcell (TR)** | 0,0043 | — |
| Trust Stamp | 0,0073 | 210. |
| **Techsign (TR)** | 0,0087 | — |
| Yoti | 0,0106 | 251. |
| IDVerse / OCR Labs | listede yok | — |

**Papilon ve Turkcell, NIST 1:1 eşleştirmede Yoti ve Trust Stamp'i geçiyor.** Türk satıcıları
peşinen elemek için bir sebep yok; elenmeleri gerekiyorsa sebep canlılık sertifikasının olmaması.

### 4.5c İncelenen satıcıların altısı konumunu abartıyor

Bu, araştırmanın en aktarılabilir bulgusu — bir satıcı sunumuna bakarken doğrudan işe yarıyor:

| Satıcı | İddia | Gerçek |
|---|---|---|
| **HyperVerge** | "NIST FRVT 1:1'de dünyada 7." | **68./444**. Ayrıca iBeta'da yalnız **Seviye 1** var, Seviye 2 iddiasının mektubu listede yok |
| **Yoti** | "NIST'te ilk on" | **251./444**. İddia muhtemelen FATE *yaş tahmini* sıralamasından geliyor, farklı bir yarış |
| **IDEMIA** | "test edilen çoğu senaryoda 1 numara" | FRTE **1:N** tanımlama için; 1:1 doğrulama ayrı bir yarış |
| **Onfido / Entrust** | Pazarlama "Seviye 2" | En yeni mektup (27 Tem 2026) açıkça **Seviye 1** diyor |
| **İHS Teknoloji** | "iBeta Seviye 2 sertifikalıdır" | Mektup **Fraud.com International LTD**'ye (Londra) ait |
| **IDEMIA + Paravision** | "DHS RIVR kazananı" | DHS sonuçları **tümüyle anonim** yayımlanıyor; iddia yapısal olarak doğrulanamaz |

### 4.6 Mevzuat metni — birincil kaynak, ve iki gün önceki değişiklik

**BDDK Yönetmeliği** (Resmî Gazete 1 Nisan 2021, No. 31441):
- **Md. 7(2)** — belgenin temassız yongasının NFC ile okunması **birincil** doğrulama; ihraç makamı
  imzası ve yonga klonlaması da denetleniyor.
- **Md. 7(3)** — NFC başarısızsa **en az dört** görsel güvenlik unsuru doğrulanacak.
- **Md. 8(1)** — görüntülü görüşme sırasında **canlılık tespiti zorunlu**, derin sahtecilik karşıtı
  tedbirler açıkça isteniyor.
- **Md. 8(3)–(4)** — süreçte bir **müşteri temsilcisi** bulunmak zorunda.

**Sonuç:** Türkiye'de tamamen otomatik, sunucu tarafı selfie–belge eşleştirmesi tek başına
**uyumlu bir banka açılış akışı değil.** NFC + insanlı görüntülü görüşme mevzuat şartı. Satıcı
listesini sertifikadan daha sert süzen şey bu.

⚠️ **TCMB Tebliğ değişikliği, Resmî Gazete 4 Eylül 2026, No. 33360** — bu araştırma yapılırken iki
günlük. Uzaktan kimlik tespitini **ödeme ve elektronik para kuruluşlarına** genişletiyor: yine NFC
birincil, OCR/kart okuyucu yedeği MASAK görüşüne bağlı, ve yabancı uyruklular ICAO 9303 NFC
pasaportuyla uzaktan tanımlanabiliyor.

**Bu Koala'yı dolaylı ama gerçek biçimde ilgilendiriyor.** §1'in sonucu — alt üye işyeri kimlik
tespiti yükümlülüğünün Iyzico'da olduğu — bu değişiklikle güçleniyor; ama aynı değişiklik Iyzico'nun
kendi yükümlülüğünü sıkılaştırdığı için **Iyzico'nun Koala'dan alt üye işyeri açılışında daha fazla
kanıt istemeye başlaması beklenmelidir.** Bu, Koala'nın çip kanıtı toplamasını hukuki bir zorunluluk
değil ama ticari bir gereklilik hâline getirebilir. İzlenmeli.

### 4.7 Fiyat

Yalnız üç satıcı doğrulama başına fiyat yayımlıyor. Yayımlanan bant **1,35–1,89 $/doğrulama**,
aylık taban **135–299 $**. Veriff'in tam otomatik "Essential" katmanı **0,80 $** ile en ucuz
başlık — ama yalnız yapay zekâ ve 3 ay saklama. Sumsub 1,35/1,85 $, iDenfy 1,35 $ + katman
ücretleri (3D canlılık +0,20 $). Regula, Jumio, Onfido/Entrust, Signicat, IDEMIA ve **bütün Türk
satıcılar** fiyat yayımlamıyor.

---

## 5. Flutter'da NFC ile TCKK okuma

### 5.1 Paketler — ve en önemli bulgu: `dmrtd` pub.dev'de yok

Bu, planlamayı doğrudan etkileyen bir gerçek. `https://pub.dev/packages/dmrtd` **404 döndürüyor**
(doğrulandı, 6 Eylül 2026). ICAO 9303'ü Dart'ta uygulayan tek ciddi açık kaynak kütüphane
[ZeroPass/dmrtd](https://github.com/ZeroPass/dmrtd) yalnızca **GitHub'da** yaşıyor; `pubspec.yaml`
ile `git:` ya da `path:` bağımlılığı olarak alınır. Sürüm 2.0.0, `sdk: ^3.6.0`, bağımlılığı
`flutter_nfc_kit: ^3.6.0-rc.6`
([pubspec.yaml](https://raw.githubusercontent.com/ZeroPass/dmrtd/master/pubspec.yaml)).

Lisansı **LGPL-3.0 veya ticari** ikili lisans. LGPL-3.0'ın mobil uygulamada kullanımı, dinamik
bağlama garantisi zor olduğu için hukuk tarafına sorulması gereken bir konu — Koala'nın kapalı
kaynak uygulamasına statik olarak giren LGPL bir kütüphane, kullanıcıya yeniden bağlama hakkı
verme yükümlülüğü doğurabilir. **Bu, teknik değil lisans riski ve karar öncesi netleşmeli.**

| Paket | Sürüm | Yayın | pub.dev'de mi | Ne yapar | Durum |
|---|---|---|---|---|---|
| [`dmrtd`](https://github.com/ZeroPass/dmrtd) | 2.0.0 | — | **Hayır** | BAC + PACE + AA + secure messaging | Yarı-uykuda; son push 22/7/2025, 8 açık issue |
| [`flutter_nfc_kit`](https://pub.dev/packages/flutter_nfc_kit) | **3.6.2** | 20/1/2026 | Evet | Ham APDU taşıması (ISO 7816-4) | **Bakımlı**, MIT, doğrulanmış yayıncı |
| [`nfc_manager`](https://pub.dev/packages/nfc_manager) | **4.2.1** | 3/4/2026 | Evet | Ham APDU taşıması | Bakımlı ama ~135 açık issue |
| [`google_mlkit_text_recognition`](https://pub.dev/packages/google_mlkit_text_recognition) | 0.17.1 | ~19 gün önce | Evet | Genel OCR (MRZ parser değil) | Bakımlı, MIT |
| [`mrz_scanner`](https://pub.dev/packages/mrz_scanner) | 0.0.1+3 | ~3 yıl önce | Evet | MRZ OCR | **Terk edilmiş** (6 indirme) |
| [`flutter_nfc_reader`](https://pub.dev/packages/flutter_nfc_reader) | 0.1.0 | ~6 yıl önce | Evet | — | **Ölü**; pub.dev "Dart 3 incompatible" işaretli |
| [`passport_reader`](https://pub.dev/packages/passport_reader) | 0.0.11 | ~2 yıl önce | Evet | Kamera OCR + GPT-4o | **Adı yanıltıcı**, NFC yok |

Ticari alternatifler (hepsi BAC+PACE kapsıyor, hepsi lisans anahtarı istiyor):
[`flutter_document_reader_api`](https://pub.dev/packages/flutter_document_reader_api) +
[`flutter_document_reader_core_rfid`](https://pub.dev/packages/flutter_document_reader_core_rfid)
(Regula), [`readid_flutter`](https://pub.dev/packages/readid_flutter) (Signicat/ReadID),
[`dot_nfc`](https://pub.dev/packages/dot_nfc) (Innovatrics).
[`emrtd`](https://pub.dev/packages/emrtd) (Kinegram) bağımsız değil — kendi DocVal sunucusunu
gerektiren ince bir istemci.

**Gerçekçi yığın:** `dmrtd` (git bağımlılığı) → `flutter_nfc_kit` → `google_mlkit_text_recognition`
+ TD1 regex + kontrol hanesi doğrulaması, elle girişe düşüş yolu ile.

### 5.2 BAC mı PACE mi — TCKK ikisini de destekliyor

Bu, birincil kaynaktan cevaplanabiliyor. TCKK'nın çip işletim sistemi **AKİS**, TÜBİTAK BİLGEM
ürünü. BİLGEM'in [ulusal kimlik kartı ürün sayfası](https://akiskart.bilgem.tubitak.gov.tr/en/national-identity-card-tr-trnc-identity-card/)
temassız seyahat belgesi uygulamasının ICAO 9303, ISO/IEC 14443-3/4, ISO/IEC 7816-4/8/9, ICAO SAC
TR ve BSI TR-03110/03111 ile uyumlu olduğunu ve erişim mekanizmalarının **BAC**, **SAC: "PACE v2",
"MRZ and CAN support"**, **EAC v1** ve **Active Authentication** olduğunu belirtiyor.

Bu, §2aq2'deki iki açık soruyla ilişkili:

- **Active Authentication kartta var** — BİLGEM listesi doğruluyor. Backend'in
  [`ActiveAuthentication`](../../src/main/java/com/exskylab/koala/core/utilities/identity/ActiveAuthentication.java)
  sınıfının karşılığı kartta mevcut. DG15'in **her kartta** bulunup bulunmadığı yine de belirsiz.
- **CAN desteği çipte var, ama kartın üzerine basılıp basılmadığı belirsiz.** NVİ'nin
  [kimlik kartı sayfası](https://www.nvi.gov.tr/tc-kimlik-karti) ve SSS'inde CAN'dan söz edilmiyor.
  **CAN yoluna göre tasarım yapılmamalı**; MRZ yolu güvenli olan.

ICAO Doc 9303 Part 11 (8. baskı, 2021) §4.1'e göre uyumlu üç yapılandırma var: yalnız BAC, PACE+BAC,
yalnız PACE. Ve geçiş takvimi net: "Issuing States and organizations issuing eMRTDs **SHALL
implement PACE as of 1 January 2027**". Yani PACE yolunu yazmamak kısa vadeli bir borç.

**Belirsiz:** Dolaşımdaki **her** TCKK kuşağının hâlâ BAC kabul edip etmediği. Bunu çözecek deney
küçük: `EF.CardAccess` güvenli oturum öncesi okunabiliyor; farklı basım yıllarına ait birkaç
karttan okunup PACE OID'leri ile BAC akışının çalışıp çalışmadığı bakılır. `dmrtd` zaten
`mrtdData.cardAccess` alanını getiriyor. **Bu, gerçek kartla yapılacak ilk deneyin ikinci
maddesidir** (birincisi §2aq2'deki CSCA zinciri sorusu).

### 5.3 iOS gereksinimleri

| Öğe | Değer | Kaynak |
|---|---|---|
| Entitlement | `com.apple.developer.nfc.readersession.formats`, değer **`TAG`** | [Apple](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.nfc.readersession.formats) |
| Xcode capability | "Near Field Communication Tag Reading" + App ID'de aynı yetenek | aynı |
| AID listesi | `com.apple.developer.nfc.readersession.iso7816.select-identifiers` | [Apple](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.nfc.readersession.iso7816.select-identifiers) |
| eMRTD AID | **`A0000002471001`** | [Apple Developer Forums 817267](https://developer.apple.com/forums/thread/817267) |
| Kullanım açıklaması | `NFCReaderUsageDescription` (boş olamaz) | [NFCTagReaderSession](https://developer.apple.com/documentation/corenfc/nfctagreadersession) |
| Minimum iOS | `NFCTagReaderSession` **iOS 13+**; `flutter_nfc_kit` iOS 13+ | aynı |
| Apple onayı | **Özel onay yok**, ama ücretli Developer Program üyeliği şart (capability provisioning profile'a giriyor) | — |

**En sık yapılan hata ve buraya not edilmesinin sebebi:** `select-identifiers` anahtarı adı
"entitlement" gibi görünse de **Info.plist'e** yazılmalı, `.entitlements` dosyasına değil. Apple
mühendisinin [forum 817267](https://developer.apple.com/forums/thread/817267)'deki ifadesi:
*"As this is not an 'entitlement' in the literal sense, it belongs to the Info.plist."*
`.entitlements`'a konursa build "Provisioning profile … doesn't include the … entitlement"
hatasıyla düşüyor. `flutter_nfc_kit` README'si de Info.plist diyor. **Bir günlük bir tuzak,
bilinerek geçilirse yarım saatlik iş.**

İkinci tuzak: `flutter_nfc_kit` README'sine göre **iOS ≤14.5'te** bu Info.plist anahtarları ilk
`poll()` çağrısından önce yerinde olmalı; değilse NFC cihaz yeniden başlatılana kadar kullanılamaz
hâle geliyor.

**Belirsiz:** ISO 7816 etiket okumasını hangi iPhone modellerinin desteklediği. Apple kesin bir
model listesi yayımlamıyor; üçüncü taraf kaynaklar "iPhone 7+" ile "iPhone XS+" arasında ayrışıyor.
**Cihaz testiyle çözülecek bir soru.**

### 5.4 Android gereksinimleri

```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="false" />
```

`required="false"` — aksi hâlde Play Store uygulamayı NFC'siz cihazlarda gizler ve Koala'nın
kullanıcı tabanının bir kısmı uygulamayı hiç göremez.

- [`IsoDep`](https://developer.android.com/reference/android/nfc/tech/IsoDep): ISO-DEP
  (ISO/IEC 14443-4) katmanı; `connect()` / `transceive()` / `close()`. eMRTD APDU'ları buradan geçer.
- **`enableReaderMode()` tercih edilmeli**, foreground dispatch değil. Reader mode teknoloji
  seçimine, `FLAG_READER_SKIP_NDEF_CHECK` ile NDEF yoklamasını atlamaya (eMRTD NDEF değil, yoklama
  boşa gidiyor ve kartı rahatsız edebiliyor) ve `EXTRA_READER_PRESENCE_CHECK_DELAY` ile uzun DG2
  okumasını agresif varlık yoklamasının kesmesini engellemeye izin veriyor
  ([NfcAdapter](https://developer.android.com/reference/android/nfc/NfcAdapter),
  [Advanced NFC](https://developer.android.com/develop/connectivity/nfc/advanced-nfc)).
- **Extended-length APDU, DG2 için kritik.**
  [`IsoDep.isExtendedLengthApduSupported()`](https://developer.android.com/reference/android/nfc/tech/IsoDep)
  NFC denetleyicisinin desteğini söylüyor. Kısa APDU'da uzunluk alanı 1 bayt → en fazla 255 bayt
  veri; extended'da 3 bayt → 65535. DG2 onlarca KB. Destek yoksa **yüzlerce `READ BINARY` turu**
  gerekir ve her tur secure messaging şifreleme/çözme maliyeti taşır. **Her zaman bu bayrağa göre
  dallanılmalı**, varsayılmamalı.
- `flutter_nfc_kit` 3.6.x: Java 17, Gradle 8.9, AGP 8.7, `minSdk 24`.

### 5.5 Çipi açan anahtar nereden geliyor?

ICAO Doc 9303 Part 11 §4.3.2: denetim sistemi "MRZ_information"ı okur — **belge numarası, doğum
tarihi ve son geçerlilik tarihi, her biri kendi kontrol hanesiyle** birleştirilmiş. §4.4 PACE için
aynı üçlüyü kullanıyor: "Kπ = KDFπ(MRZ) is REQUIRED. It is derived from the Document Number, the
Date of Birth and the Date of Expiry." CAN yolu **OPTIONAL** ve ICAO'nun kendi notu şöyle: "In
contrast to the MRZ … the CAN has the advantage that it can easily be typed in manually."

Bu üçlünün kaynağı üç seçenek:

**(a) Kamera ile MRZ OCR — üretimde yapılan bu.** TCKK'nın MRZ'si kartın **arka yüzünde** basılı:
NVİ, kartın "temassız çipindeki veriler ve arka yüzündeki MRZ alanındaki bilgiler sayesinde"
seyahat belgesi olarak kullanılabildiğini belirtiyor
([nvi.gov.tr](https://www.nvi.gov.tr/tc-kimlik-karti)). Format **TD1** (3 satır × 30 karakter),
ki §2aq2'deki ayrıştırıcı zaten TD1 varsayıyor — **tutarlı**. Türk kimlik kartı seri numarası bir
harf, iki rakam, bir harf, beş rakam (9 karakter), TD1'in belge numarası alanına temiz oturuyor;
§2aq2'deki "Not (j) tuzağı"nın TCKK'da oluşmama gerekçesi bu.

`google_mlkit_text_recognition` genel OCR, MRZ ayrıştırıcı değil; standart yaklaşım Latin metin
tanıma + TD1 regex + **ICAO kontrol hanesi doğrulaması**. Kontrol haneleri güvenlik ağı: doğrulamayı
geçemeyen tarama reddedilip yeniden denenir, yani OCR hatası başarısız çip oturumuna değil
tekrara dönüşür. **TD1 MRZ üzerinde yayımlanmış doğruluk ölçümü bulunamadı** — tekrar döngüsü ve
elle giriş yedeği bütçelenmeli, ilk denemede tutma oranı varsayılmamalı.

**(b) Elle giriş — her hâlükârda gönderilmeli.** Üç kısa alan; yıpranmış veya kötü ışıkta okunmayan
kartı kurtarır. Not: BAC anahtar tohumu düşük entropili — bu şemanın bilinen bir özelliği, Koala'nın
uygulamasının kusuru değil.

**(c) CAN** — çip destekliyor (§5.2) ama kartın üzerinde basılı olduğu **doğrulanamadı**. Kullanma.

### 5.6 DG2 ve JPEG 2000 tuzağı

ICAO Doc 9303 Part 10 (8. baskı, 2021) §4.7.2: "**DATA GROUP 2 — Encoded Identification Features —
Face (MANDATORY)** … which SHALL be an image of the face of the holder as an input to a face
recognition system." §4.7.2.1: DG2 ISO/IEC 7816-11'deki BIT grup şablonunu kullanır ve CBEFF ile
uyumludur; "the first biometric recorded in each Data Group SHALL be encoded as per
[ISO/IEC19794-5]". §4.5.6 geçişi veriyor: ISO/IEC 19794 verisi `5F2E` etiketinde, ISO/IEC 39794
verisi `7F2E` etiketinde; **denetim sistemleri 1/1/2026'dan itibaren 39794'ü işleyebilmek zorunda**,
belge verenler 1/1/2030'dan itibaren 39794 kullanmak zorunda.

Görüntü formatı ICAO'da değil, ISO/IEC 19794-5'te tanımlı: **JPEG veya JPEG 2000**. Pratikte
çoğu ülke sıkıştırma verimi için JPEG 2000 kullanıyor — ve **ne iOS ne Android bunu bu iş için
yerel olarak çözüyor.** `AndyQ/NFCPassportReader` README'si açıkça "JPEG2000 images are currently
unsupported" diyor ve ham baytları çağırana bırakıyor;
[issue #218](https://github.com/AndyQ/NFCPassportReader/issues/218) Portekiz ve İspanya
pasaportlarında görüntünün hiç çıkmamasını tam olarak bu sebebe bağlıyor, çözüm olarak libopenjp2
bağlamayı öneriyor. Seçenekler: Android'de
[ThalesGroup/JP2ForAndroid](https://github.com/ThalesGroup/JP2ForAndroid), iOS'ta libopenjp2'yi
statik derlemek, ya da **çözmeyi sunucuya bırakmak**.

> **Koala için bu tuzak bugün maliyetsiz atlatılabiliyor**, çünkü mevcut tasarım DG2'yi hiç
> okumuyor: Passive Authentication yalnızca `sod` + `dg1` (+ opsiyonel `dg11`, `dg15`) istiyor.
> DG2 ancak yüz eşleştirmesi eklendiğinde devreye girer — ve o gün JPEG 2000 çözücü de gelir.

### 5.7 Referans uygulamalar

| Proje | Platform | Not |
|---|---|---|
| [ZeroPass/dmrtd](https://github.com/ZeroPass/dmrtd) | Dart/Flutter | Tek gerçek açık kaynak Flutter seçeneği. BAC + PACE (DH/ECDH), AA |
| [AndyQ/NFCPassportReader](https://github.com/AndyQ/NFCPassportReader) | iOS/Swift | Alandaki en aktif proje. **Türk kimlik kartlarıyla üretimde çalıştığı bildirilmiş** ([#228](https://github.com/AndyQ/NFCPassportReader/issues/228)) |
| [JMRTD](https://jmrtd.org/about.shtml) | Java/Android | Kanonik referans uygulama; diğerlerinin çoğu buradan türüyor |
| [tananaev/passport-reader](https://github.com/tananaev/passport-reader) | Android/Kotlin | JMRTD + JP2 for Android; **JPEG 2000 parçası dâhil uçtan uca örnek** |
| [phanvankhaicd/cccd_vietnam](https://github.com/phanvankhaicd/cccd_vietnam) | Flutter | `dmrtd`'yi paketleyip ulusal kimlik kartı için yayımlanmış örnek — TCKK durumuna en yakın kalıp |

### 5.8 EKDS/KEC yanılgısı — buna gerek yok

Bazı Türk entegratör yazıları TCKK okumak için **rol sertifikası** ve **KEC (Kimlik Erişim Cihazı)**
gerektiğini yazıyor. Bu iki farklı uygulamayı karıştırıyor:

- **ICAO eMRTD uygulaması** (DG1, DG2, SOD, BAC/PACE) — MRZ anahtarıyla, standart bir okuyucuyla
  açılır. Koala'nın ihtiyacı bu.
- **EKDS uygulaması** — [TCKK Elektronik Kimlik Doğrulama Sistemi Yönetmeliği](https://www.lexpera.com.tr/mevzuat/yonetmelikler/turkiye-cumhuriyeti-kimlik-karti-elektronik-kimlik-dogrulama-sistemi-yonetmeligi)
  kapsamındaki kimlik doğrulama uygulaması. **KEC ve rol sertifikası bunun içindir.**

DG1 + DG2 + SOD ile yetiniliyorsa EKDS bölgesine hiç girilmiyor. **Bu ayrım, projenin "yapılamaz"
sanılmasının önündeki en yaygın engel.**

---

## 6. Bugün elimizde ne var — kod doğrulaması

Aşağıdakilerin hepsi bu araştırma sırasında koda karşı doğrulandı.

**Sunucu tarafı hazır ve iyi durumda.**
[`PassiveAuthenticationVerifier`](../../src/main/java/com/exskylab/koala/core/utilities/identity/PassiveAuthenticationVerifier.java)
ve [`CscaTrustStore`](../../src/main/java/com/exskylab/koala/core/utilities/identity/CscaTrustStore.java)
mevcut; `src/main/resources/csca/` altında **12 adet Türkiye CSCA sertifikası** duruyor ve **hepsi
git'te izleniyor**. BouncyCastle sağlayıcısı açıkça kullanılıyor (`PROVIDER_NAME` beş ayrı yerde),
`PKIXParameters.setRevocationEnabled(false)` — yani **CRL kontrolü kapalı**, §2aq2'de bilinerek
kaydedildiği gibi. `scripts/fetch-csca-anchors.sh` ve `RealChipVerificationTest` de yerinde.

*Küçük bir tutarsızlık:* `scripts/` dizini **git'te izlenmiyor** (`git status` çıktısında `??`).
CSCA listesinin üç ayda bir güncellenmesi bu betiğe bağlıysa, betiğin commit edilmemiş olması
sessiz bir tek-kişi bağımlılığı. Bu araştırmanın konusu değil ama not düşülmeli.

**Varsayılan kapalı, doğrulandı.**
[`IdentityVerificationProperties`](../../src/main/java/com/exskylab/koala/core/properties/IdentityVerificationProperties.java):
`enabled = false`, `activeAuthenticationRequired = false`, `challengeValidityMinutes = 10`.
`application.yaml` bunları `IDENTITY_PASSIVE_AUTH_ENABLED:false` ve
`IDENTITY_ACTIVE_AUTH_REQUIRED:false` ile bağlıyor.

**İstemci hiçbir çip kanıtı göndermiyor — doğrulandı ve tahmin edilenden de dar.**
[`VerifyIdentityDto`](../../src/main/java/com/exskylab/koala/core/dtos/user/request/VerifyIdentityDto.java)'da
`sod`, `dg1`, `dg11`, `dg15`, `challenge`, `activeAuthenticationSignature` alanlarının **hiçbirinde
doğrulama anotasyonu yok** — yalnızca `firstName`, `lastName`, `birthDate`, `tcIdentityNumber`,
`tcDocumentNumber` `@NotBlank`/`@NotNull`. Flutter tarafında:

- `pubspec.yaml`'da NFC, kamera-tabanlı kimlik okuma veya yüz kütüphanesi **yok**
  (`image_picker` var, o da şirket logosu için).
- `lib/` altında `nfc`, `mrz`, `liveness`, `biometric` kelime sınırıyla arandığında **sıfır** eşleşme.
- `lib/employee/features/settings/presentation/pages/identity_verification_page.dart` ekranı
  **dört metin alanı ve bir tarih seçiciden ibaret**: `_firstName`, `_lastName`, `_tcIdentity`,
  `_tcDocument`, `_birthDate`.
- `ImageSource.camera` kullanımı **hiç yok**.

Yani "bugünkü doğrulama self-declaration" ifadesi doğru ve iyimser bile: ekranda kimlik kartına
ait tek bir görsel doğrulama adımı bile yok. Ekran `/identity-verification` rotasında ve iş
başvurusu, bireysel ilan açma ve ödeme oturumu bu bayrağa bağlı.

**Doğrulanan bağlam maddelerinden düzeltilmesi gerekenler:**

| İddia | Sonuç |
|---|---|
| PA ve AA backend'de uygulanmış | **Doğru** |
| CSCA çıpaları `resources/csca/`'da, BSI master list'inden | **Doğru**, 12 dosya, git'te izleniyor |
| `VerifyIdentityDto`'da çip alanları opsiyonel | **Doğru** |
| Flutter'da NFC/kamera/yüz kodu yok | **Doğru** — ve kimlik ekranında kamera hiç kullanılmıyor |
| Gerçek kart dökümü alınmadı, CSCA zinciri bilinmiyor | **Doğru** — ve buna §5.2'deki "BAC hâlâ açık mı" sorusu ekleniyor |
| — | **Yeni:** TCKK'nın AA desteklediği BİLGEM ürün sayfasından doğrulanabiliyor (§5.2) |

---

## 7. Ne yapılmalı, hangi sırayla, maliyeti ne

### 7.1 Önce doğru soruyu sormak

§1'in cevabı verildikten sonra soru "bankaların yaptığını nasıl yaparız" olmaktan çıkıyor. Koala'nın
kapatmak istediği somut zarar tektir ve §2aq'da zaten doğru tarif edilmiş:

> Elinde **başkasının gerçek kartı** olan biri, uygulamayı değiştirerek o kimlikle kayıt olur; alt
> üye işyeri **kurbanın TCKN'siyle** ama **saldırganın IBAN'ıyla** açılır ve para saldırgana gider.

Bu zincirin üç halkası var: (1) kimlik iddiası, (2) kartın gerçekliği, (3) **paranın gideceği
hesabın sahipliği**. Passive Authentication (2)'yi kapatıyor. Yüz eşleştirme (1)'i kapatmaya
çalışıyor. Ama zararı fiilen doğuran halka (3) — ve (3) bugün **hiç** kontrol edilmiyor:
[`BankAccountManager.savePrimaryAccount`](../../src/main/java/com/exskylab/koala/business/concretes/BankAccountManager.java)
yalnızca `IbanValidator.isValid` (mod-97 biçim kontrolü) yapıyor ve `accountHolderName`'i
**kullanıcının kendi yazdığı addan** türetiyor — doğrulanmış bir kaynaktan değil.

**Yüz eşleştirmesi olmadan da (3) kapatılırsa zarar kapanır. (3) kapatılmadan yüz eşleştirmesi
eklenirse zarar kapanmaz** — saldırgan kurbanın kartını ve kurbanın yüzünü değil, kurbanın kartını
ve **kendi** IBAN'ını kullanıyor; yüz eşleştirmesi onu ancak kart sahibi olmadığında durdurur.
Yani yüz eşleştirmesi bu senaryoda gerçekten yardımcı, ama tek başına yeterli değil ve **en pahalı
halka**.

### 7.2 Sıra

**Sıfırıncı adım — gerçek kartla tek oturumluk deney (0,5 gün).** Her şeyin önünde bu var, çünkü
üç açık soruyu aynı anda kapatıyor ve hepsi sonraki kararları değiştiriyor:

1. TCKK'nın Document Signer'ı `resources/csca/` altındaki 12 çıpadan birine zincirleniyor mu?
   (§2aq2'nin açık sorusu — bağlanmıyorsa NVİ'den kimlik kartına özel CSCA istenmesi gerekir ve
   bu bir yazışma süreci, kod işi değil.)
2. Kart BAC kabul ediyor mu, `EF.CardAccess` ne diyor? (§5.2)
3. DG11 ve DG15 kartta var mı? (§2aq2 ikisini de opsiyonel bırakmış; gerçek cevap bilinmiyor.)

Araç hazır: `KOALA_CHIP_DIR=... ./mvnw test -Dtest=RealChipVerificationTest`. Gereken tek şey bir
çip dökümü — herhangi bir açık kaynak okuyucuyla (§5.7) bir kez alınır.

**Adım 1 — IBAN sahipliği (en yüksek fayda/maliyet).** §2aq zaten KKB IBAN Doğrulama Hizmeti'ni
işaret ediyor ve erişimin KKB üyeliğine bağlı olduğunu, üyeliğin esas olarak finansal kuruluşlara
açık olduğunu kaydediyor. Bu araştırma bunu doğruluyor:
[KKB'nin kendi ürün sayfası](https://www.kkb.com.tr/urunler/kkb-iban-dogrulama-hizmeti) hizmetin
"IBAN numarası ile IBAN sahibinin TCKN/VKN'si bilinen ancak kimlik teyidi gerçekleştirilemeyen
işlemlerde" tam olarak bu eşleştirmeyi yaptığını anlatıyor; üyelik ise "finans sektörü
çalışanlarının kurumsal mailleri" ile sınırlı görünüyor. **Banka dışı bir şirketin erişip
erişemeyeceği belirsiz ve bunu ancak KKB'ye yapılacak yazılı bir başvuru netleştirir.**

Erişim alınamazsa mevzuatın kendi çözümü kopyalanabilir ve **hiçbir üçüncü taraf gerektirmez**:
BDDK m.7/3 ve Tebliğ 19 m.6/4, çip doğrulanamadığında **ilk finansal hareketin kişinin kendi banka
hesabından** yapılmasını zorunlu tutuyor. Koala'nın karşılığı: işçinin ilk ödemesi serbest
bırakılmadan önce, kayıtlı IBAN'dan Koala'ya **küçük bir doğrulama transferi** (ya da tersine, ad
bilgisi dönen bir iade akışı) istemek. Ucuz, biyometrik veri taşımıyor, KVKK yükü ~sıfır ve
zincirin (3) halkasını fiilen kapatıyor.

**Adım 2 — Passive Authentication'ı gerçekten açmak.** Backend hazır ve varsayılan kapalı. Açmanın
maliyeti sunucuda değil, **istemcide**: Flutter tarafında NFC + MRZ akışı yazılmalı (§5). Sıfırıncı
adım tamamlanmadan açılmamalı, çünkü CSCA zinciri tutmuyorsa `UNTRUSTED_DOCUMENT_SIGNER` ile
herkesi reddeder.

**Adım 3 — Active Authentication'ı zorunlu kılmak.** `activeAuthenticationRequired=true`. Kod ve
challenge uç noktası hazır; DG15'in kartta bulunduğu sıfırıncı adımda teyit edildiyse maliyeti
neredeyse sıfır. Kazancı büyük: SOD tekrar saldırısını (bir kez alınmış çip dökümünün yeniden
gönderilmesi) kapatıyor. **PA'nın tek başına kapatamadığı boşluk budur ve en ucuz kapanan boşluktur.**

**Adım 4 — canlılık + yüz eşleştirme, cihazda.** Buraya ancak 1–3 bittikten sonra gelinmeli.

**Adım 5 — sunucuda yüz eşleştirme.** §7.4'te ayrıca tartışılıyor; **varsayılan öneri: yapmamak.**

### 7.3 Maliyet

Geliştirme tarafı (tek geliştirici, gün):

| İş | Gün | Not |
|---|---|---|
| Gerçek kart dökümü + üç sorunun cevabı | 0,5 | Araç hazır |
| Flutter NFC + MRZ OCR + elle giriş yedeği | **8–12** | iOS Info.plist tuzağı, Android extended-APDU dallanması, `dmrtd` git bağımlılığı |
| PA'yı açmak, uçtan uca test, hata mesajları | 2–3 | Sunucu kodu hazır |
| Active Authentication'ı zorunlu kılmak | 0,5–1 | challenge akışı hazır |
| IBAN doğrulama transferi akışı (üçüncü taraf yoksa) | 3–5 | Mutabakat ve zamanaşımı mantığı dâhil |
| KKB entegrasyonu (erişim verilirse) | 2–3 | + başvuru süreci (kod dışı, haftalar) |
| Cihazda canlılık + yüz eşleştirme | **10–15** | JPEG 2000 çözücü dâhil (§5.6) |
| KVKK dokümantasyonu (aydınlatma, rıza, envanter, imha) | 2–3 | Hukuk desteği ayrı |

Yinelenen maliyet:

- **Adım 1–3 için sıfır.** Çip doğrulaması tamamen kendi altyapımızda; CSCA çıpaları ücretsiz
  (BSI master list). Doğrulama başına dış servis ücreti yok.
- **IBAN doğrulama transferi:** işlem başına banka masrafı; kullanıcı başına bir kez.
- **KKB:** fiyat kamuya açık değil, üyelik şartına bağlı.
- **Yüz eşleştirme:** sağlayıcı seçilirse doğrulama başına ücret — bkz. §4.

### 7.4 Sunucu tarafı yüz kontrolünü **yapmama** seçeneği

Bu seçenek dürüstçe ortaya konmalı, çünkü büyük ihtimalle doğru seçenek.

**Yapmazsak ne kaybediyoruz.** Sunucu, cihazın "yüz eşleşti" iddiasını doğrulayamaz. Uygulamayı
değiştiren ve **elinde başkasının fiziksel kartı olan** bir saldırgan, yüz kontrolünü atlayarak o
kimlikle kayıt olabilir. Kalan risk tam olarak budur — daha fazlası değil: çipe hiç dokunmayan
saldırgan SOD imzasını zaten üretemiyor (§2aq2), yani "uydurma TCKN" senaryosu kapalı.

**Yapmazsak ne kazanıyoruz.**

- Sunucuda **hiç özel nitelikli kişisel veri yok** — ihlal bildirimi riski, şifreleme/anahtar
  yönetimi politikası, imha kanıtı, 2018/10 sayılı Kurul kararındaki yeterli önlemler yükü doğmuyor.
- **Yurt dışına aktarım rejimi hiç tetiklenmiyor** — standart sözleşme, beş iş günü bildirimi,
  noter onaylı çeviri yok (§3.4).
- **Ölçülülük savunması güçlü kalıyor.** 2026/921 sayılı ilke kararının mantığı karşısında
  "biyometrik veriyi merkezde hiç tutmuyoruz" savunulabilir bir konum; "merkezde tutuyoruz ama
  gerekliydi" savunulması çok daha zor bir konum.
- Doğrulama başına **yinelenen ücret yok**.

**Kalan risk nasıl azaltılır — biyometri sunucuya taşınmadan.**

1. **IBAN sahipliği (Adım 1).** Kartı çalan saldırgan kendi IBAN'ını bağlayamazsa parayı alamaz.
   Bu, kalan riskin **para tarafını** tamamen kapatıyor.
2. **Active Authentication (Adım 3).** Kartın fiziksel olarak elde bulunmasını zorunlu kılıyor.
3. **TCKN benzersizliği** — `uk_users_tc_identity_number_active` zaten var; yeniden kullanımı
   engelliyor.
4. **Cihaz bütünlüğü kanıtı** — cihazın kurcalanmamış bir uygulama çalıştırdığını sunucuya
   kanıtlamak, "yüz eşleşti" iddiasını doğrulamaz ama **iddiayı uydurmayı ucuz olmaktan çıkarır**
   (§4).
5. **Ödemeyi geciktirme.** İlk ödeme için kısa bir bekleme penceresi, kimliği çalınan kişinin fark
   edip itiraz etmesine zaman tanır. Mevzuatın "sıkılaştırılmış tedbirler" listesindeki (Tebliğ 19
   m.6/3-e) "tutar ve işlem sayısı limiti belirlemek" ile aynı fikir.

**Ne zaman yeniden düşünülmeli.** Koala MASAK yükümlüsü olduğunda (10 milyar TL eşiği), ya da
kimlik hırsızlığına dayalı gerçek bir vaka yaşandığında. O gün gelirse §3.5'teki **C seçeneği**
(yurt içinde işleyen sağlayıcı) doğrudan hedeflenmeli; D seçeneği (yurt dışı) yalnızca yurt içi
seçenek gerçekten yoksa ve standart sözleşme yükü göze alınarak.
