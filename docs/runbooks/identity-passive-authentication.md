# Passive Authentication bayrağı — açma ve geri alma

Bu runbook **bayrağı açmaz**. `IDENTITY_PASSIVE_AUTH_ENABLED` üretimde tanımsızdır
ve yaml varsayılanı `false`'tur. Açmak is-koala/koala-backend#73'ün kabul
kriteridir ve is-koala/koala-backend#70 (gerçek kart dökümü) ile
is-koala/koala#59 (kanıt gönderen istemci sürümü kullanıcıların telefonunda)
kapanmadan yapılmaz. Bu dosya geri alma yolunu ve CSCA tazelemesini yazar ki
#70 geldiğinde açma bir dokümantasyon avı olmasın.

## Bugünkü durum

- `identity.passive-authentication.enabled=${IDENTITY_PASSIVE_AUTH_ENABLED:false}`
- `identity.passive-authentication.trust-anchor-directory=${IDENTITY_CSCA_DIRECTORY:}`
  — **boş bırakılır.** Paketlenmiş çıpa `src/main/resources/csca/*.der` içinden
  gelir. NVİ'den yeni bir kök gelirse doğru yol onu repoya commit etmektir,
  volume bağlamak değil.
- Test kaynakları bayrağı açmaz. Açılırsa çip kanıtı üretmeyen her kimlik testi
  kırılır.

## Geri alma

Sorun çıkarsa ortam değişkenini kapatmak yeterlidir; deploy gerekmez:

```
IDENTITY_PASSIVE_AUTH_ENABLED=false
```

Kapalıyken `UserManager.verifyChipEvidence` `null` döner ve istek gövdesindeki
ad / soyad / doğum tarihi / TCKN olduğu gibi yazılır. Bu, bayrak öncesi
davranıştır — doğrulama değil, benzersizlik.

## Açmadan önce (henüz yapılmadı)

1. #70 kapanmış ve TCKK Document Signer'ının paketlenmiş bir CSCA'ya
   zincirlendiği yazılı olsun.
2. Kanıt gönderen mobil sürüm yayında olsun; `app.minimum-version` eşiği o
   sürüme çekilsin. Aksi hâlde eski istemci `IDENTITY_EVIDENCE_REQUIRED` alır
   ve ne yapacağını bilmez — sürüm kapısı (426) bunu "güncelle"ye çevirir.
3. `SERVICE_ENVIRONMENT=staging` ortamında bayrak `true` iken gerçek bir kart
   ve gerçek bir cihazla uçtan uca geçilsin.
4. Mevcut `identityVerified = true` hesapları için ürün kararı CLAUDE.md
   §2aq2'ye yazılsın. Bu runbook o kararı vermez.

## CSCA listesini tazelemek

Liste üç ayda bir güncellenir:

```
./scripts/fetch-csca-anchors.sh src/main/resources/csca TR
```

Çıktıdaki `csca-tr-NN.der` dosyaları commit edilir. Betik BSI master
list'indendir; kimlik kartı kökleri (`csca-tr-12` ve sonrası) NVİ deposundan
gelir ve bu betik onları silmez, yalnız `C=TR` pasaport CSCA'larını yeniden
yazar. Seyahat belgesi köklerini ayrı tut.

Gerçek bir çip dökümü elinizdeyse:

```
./scripts/verify-chip.sh sod.bin dg1.bin src/main/resources/csca
```
