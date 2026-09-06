# Çevrimdışı puantaj olayını uzlaştırmadan önce kalıcı kaydet

Durum: Kabul edildi — 6 Eylül 2026.

## Karar

Yeni mobil istemci check-in ve check-out isteklerine bir `clientEventId` UUID'si
ile cihazın olayı yakaladığı `capturedAt` anını ekler. İki alan birlikte bulunur
ya da ikisi de bulunmaz. Mevcut istemcilerin konum dışında alan taşımayan gövdesi
geriye uyumlu çalışmaya devam eder; yalnız yeni sözleşme güvenli yeniden deneme
garantisi alır.

Sunucu her yeni istemci olayında şu gerçekleri birbirinden ayırır:

- `capturedAt`: cihazın bildirdiği ham an; PostgreSQL'in mikrosaniye
  hassasiyetine normalize edilerek denetim kaydı olarak saklanır.
- `receivedAt`: isteğin sunucuya ilk ulaştığı an.
- `effectiveAt`: çalışma oturumuna ve dakika hesabına uygulanan an.
- plausibility bayrakları: cihaz zamanının neden güvenilmediğini açıklar.

Bir istemci olayının kimliği kullanıcı, atama ve işlem türü bağlamında
benzersizdir. Aynı kimlik ve aynı normalize payload tekrarlandığında ilk
`WorkSession` sonucu döner. Aynı kimlik farklı `capturedAt` veya koordinatlarla
tekrarlandığında `409` döner. Aynı UUID'yi başka kullanıcı ya da atamada görmek
ilk olayın varlığını açığa çıkarmaz.

Zaman damgası normalizasyonu hem ilk kayıtta hem yeniden deneme karşılaştırmasında
uygulanır. Böylece JSON'un taşıyabildiği nanosaniye hassasiyeti ile PostgreSQL
`timestamptz` alanının mikrosaniye hassasiyeti arasındaki fark, aynı olayın yanlış
biçimde farklı payload sayılmasına yol açmaz.

Atama başına işlemler PostgreSQL transaction advisory lock'u ile sıralanır.
Böylece iki eşzamanlı check-in, farklı anahtarlarla bile iki açık oturum
oluşturamaz; aynı check-out'un belirsiz ağ sonucundan sonraki tekrarı kapanmış
oturumu olay kimliğiyle bulur.

## Zaman uzlaştırması

Varsayılan makullük penceresi ilk alım anına göre 72 saat geçmiş ve 5 dakika
gelecektir. Bu değerler çalışma zamanı yapılandırmasıdır; manager içine dağılmış
sabitler değildir.

- Pencere içindeki `capturedAt`, `effectiveAt` adayıdır.
- Daha eski veya daha ilerideki değer ham haliyle saklanır,
  `DEVICE_TIME_IMPLAUSIBLE` ile işaretlenir ve `effectiveAt = receivedAt` olur.
- İlanın başlangıç/bitiş penceresi Europe/Istanbul iş saatiyle Instant'a
  çevrilir. Aday zaman pencerenin dışındaysa olay reddedilmez,
  `OUTSIDE_JOB_WINDOW` ile işaretlenir ve en yakın pencere sınırına çekilir.
- Check-out etkili zamanı açık oturumun check-in zamanından önce olamaz. Böyle
  bir değer `NON_MONOTONIC` ile işaretlenir ve negatif süre üretmeyecek sınıra
  çekilir.

Bayraklı olay kaybolmuş sayılmaz: ham zaman, alım zamanı, etkili zaman ve
koordinatlar denetlenebilir kalır ve API yanıtında inceleme gereksinimi görünür.
Para ve `workHours` hesabı yalnız etkili zamanlardan türetilir.

Cihazın monotonik saati UTC değildir. Mobil kuyruk bunu yalnız yerel FIFO ve
cihaz yeniden başlatma teşhisi için saklar; sunucu zamanıymış gibi göndermez.

## Neden ayrı olay tablosu değil

Bir çalışma oturumu tam olarak bir check-in ve en fazla bir check-out taşır.
İki olayın idempotency ve audit alanlarını `work_sessions` üzerinde tutmak,
sonucu bulmak için ikinci bir aggregate ve iki yönlü yaşam döngüsü yaratmaz.
İki işlem için ayrı benzersiz indeksler aynı UUID'nin işlem türüne göre
ayrılmasını sağlar. Olay sayısı veya ara durumlar gelecekte çoğalırsa ayrı event
aggregate'i yeniden değerlendirilecektir.

## Sonuçlar

- Eski istemci çağrıları eskisi gibi sunucu zamanını kullanır ve idempotent
  değildir; davranış sessizce değişmez.
- Yeni istemci belirsiz bir yanıtı aynı anahtarla güvenle tekrarlar.
- Cihaz zamanı ücret hesabına körlemesine girmez; şüpheli değer hem korunur hem
  güvenli bir etkili zamana dönüştürülür.
- Advisory lock PostgreSQL'e özgüdür. Proje üretimde ve integration testlerinde
  zaten PostgreSQL kullanır; banka hesabı tek-birincil invariant'ı da aynı
  mekanizmayı kullanır.
- Koordinat, ham zaman ve idempotency anahtarı uygulama loglarına, ölçümlemeye
  veya hata izleme etiketlerine yazılmaz.
