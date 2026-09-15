# GeoIP — MaxMind GeoLite2-City

Oturum detayındaki `location` (ör. `Istanbul, TR`) ve `login_attempts.location`, giriş anındaki istemci IP'sinin
MaxMind **GeoLite2-City** veritabanında aranmasıyla dolar. Arama süreçte, `.mmdb` dosyasından yapılır; hiçbir IP
dış servise gönderilmez ve hiçbir IP loga yazılmaz (`LogPrivacyTest`).

**Dosya yoksa uygulama açılır, konum `null` kalır.** `GEOIP_DATABASE_PATH` boş ya da dosya okunamıyorsa
`GeoIpConfig` tek bir `WARN` yazar ve `GeoIpResolver.unavailable()` bağlanır. Yani GeoIP bir ön koşul değil,
bir zenginleştirmedir; yerelde ve CI'da dosya gerekmez (`GeoIpConfigTest`).

## Lisans ve atıf

GeoLite2, MaxMind'ın ücretsiz veritabanıdır ve **GeoLite2 End User License Agreement** ile dağıtılır
(https://www.maxmind.com/en/geolite2/eula). Kısaca:

- İndirmek için ücretsiz bir MaxMind hesabı ve lisans anahtarı gerekir; anahtar bir sırdır (`.env` / Dokploy
  secret), git'e girmez.
- Veritabanı **yeniden dağıtılamaz** — imaja gömülmez, volume'a indirilir; GHCR'deki imaj dosyayı içermez.
- Ürün içinde atıf: kullanıcıya görünen gizlilik metninde "Konum tahmini MaxMind'ın GeoLite2 verisiyle yapılır"
  ("This product includes GeoLite2 Data created by MaxMind, available from https://www.maxmind.com") ifadesi
  bulunmalı. KVKK aydınlatma metni güncellemesi ürün/hukuk adımıdır; backend yalnız veriyi tutar.
- Kullanıcı verisiyle birleşen konum bilgisi kişisel veridir: hesap anonimleştirmesi `sessions.location` ve
  `login_attempts.location` alanlarını temizler (`docs/runbooks/account-anonymization.md`).

## İlk kurulum

1. https://www.maxmind.com/en/accounts/current/license-key adresinden lisans anahtarı üret; Dokploy'da
   `koala-app` servisine **secret** olarak `MAXMIND_LICENSE_KEY` yaz (uygulama bu anahtarı okumaz, yalnız
   indirme adımı kullanır).
2. VPS'te kalıcı bir dizin aç ve Dokploy'da `koala-app` servisine volume olarak bağla:
   host `/var/lib/koala/geoip` → konteyner `/data/geoip` (salt-okunur yeterli).
3. Dosyayı indir (ilk kez elle):

   ```bash
   sudo mkdir -p /var/lib/koala/geoip
   curl -fsSL "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&license_key=${MAXMIND_LICENSE_KEY}&suffix=tar.gz" \
     -o /tmp/GeoLite2-City.tar.gz
   tar -xzf /tmp/GeoLite2-City.tar.gz -C /tmp
   sudo install -m 0644 /tmp/GeoLite2-City_*/GeoLite2-City.mmdb /var/lib/koala/geoip/GeoLite2-City.mmdb
   rm -rf /tmp/GeoLite2-City*
   ```

4. Uygulama ortamına `GEOIP_DATABASE_PATH=/data/geoip/GeoLite2-City.mmdb` ver ve servisi yeniden başlat.
   Açılış logunda `GeoIP database loaded from /data/geoip/GeoLite2-City.mmdb` görünmeli; `not found` ya da
   `not configured` uyarısı görünüyorsa yol ya da volume yanlış.

## Haftalık güncelleme

MaxMind GeoLite2'yi haftada iki kez (Salı/Cuma) yeniler; eski dosya çalışmaya devam eder, yalnız isabet düşer.
Aşağıdaki cron VPS'te root olarak haftada bir çalışır; dosya **atomik** değiştirilir (`mv` aynı dosya sistemi
içinde) ve uygulama **yeniden başlatılmaz**: `MaxMindGeoIpResolver` her aramadan önce dosyanın `mtime`'ına bakar,
değişmişse okuyucuyu bir kez yeniden açar ve eskisini kapatır (`GeoIP database reloaded from …` INFO satırı).
Yarım yazılmış/bozuk bir dosya açılamazsa eski okuyucu kalır, tek bir WARN yazılır ve dosya bir daha değişene
kadar yeniden denenmez — bu yüzden `mv` ile atomik değiştirmek şart, `cp` ile üstüne yazmak değil.

```cron
# /etc/cron.d/koala-geoip — Pazartesi 04:15
15 4 * * 1 root /usr/local/bin/koala-geoip-update.sh >> /var/log/koala-geoip.log 2>&1
```

```bash
#!/usr/bin/env bash
# /usr/local/bin/koala-geoip-update.sh
set -euo pipefail
: "${MAXMIND_LICENSE_KEY:?MAXMIND_LICENSE_KEY tanimli degil}"
target=/var/lib/koala/geoip/GeoLite2-City.mmdb
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
curl -fsSL "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&license_key=${MAXMIND_LICENSE_KEY}&suffix=tar.gz" \
  -o "$work/city.tar.gz"
tar -xzf "$work/city.tar.gz" -C "$work"
install -m 0644 "$work"/GeoLite2-City_*/GeoLite2-City.mmdb "$target.tmp"
mv -f "$target.tmp" "$target"
echo "$(date -u +%FT%TZ) GeoLite2-City guncellendi"
```

Alternatif: MaxMind'ın resmi `geoipupdate` aracı (`/etc/GeoIP.conf` içinde `AccountID`, `LicenseKey`,
`EditionIDs GeoLite2-City`, `DatabaseDirectory /var/lib/koala/geoip`) aynı işi yapar.

## Yerel geliştirme

Gerekli değil. Konumu görmek isteyen geliştirici dosyayı `~/geoip/GeoLite2-City.mmdb` gibi bir yere indirip
`.env` içinde `GEOIP_DATABASE_PATH` verir. Testler dosya istemez; `GeoIpResolver` Spring bean'i olduğu için
testte sahte çözümleyici bağlanabilir (`SessionDetailIT`).

## Sorun giderme

| Belirti | Neden | Çözüm |
|---|---|---|
| Açılışta `GeoIP database path is not configured` | env boş | `GEOIP_DATABASE_PATH` ver |
| `GeoIP database not found at …` | volume bağlı değil ya da yol yanlış | Dokploy volume eşlemesini ve dosya adını kontrol et |
| `could not be opened (InvalidDatabaseException)` | bozuk/yarım indirilmiş dosya | güncelleme scriptini yeniden koş |
| `changed but could not be reopened` | güncelleme sırasında yarım yazılmış dosya (cp ile üstüne yazma) | scripti `mv` ile atomik çalıştır; eski okuyucu o ana kadar çalışmaya devam eder |
| Konum hep `null` | istemci IP'si özel ağdan geliyor (`10.*`, `192.168.*`, `127.*`) ya da `APP_TRUSTED_PROXIES` eksik ve gerçek IP yerine proxy IP'si okunuyor | `ClientIpResolver` ayarı, CLAUDE.md §2j |
