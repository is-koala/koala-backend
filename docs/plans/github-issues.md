# GitHub iş dizini

Onaylı spec: [backend #3](https://github.com/is-koala/koala-backend/issues/3) · [mobil #6](https://github.com/is-koala/koala/issues/6).

Paketlerin teslim ve kabul koşulları [uygulama paketleri](implementation-slices.md) belgesindedir. GitHub üzerinde alt işler ve paketler arası engelleme ilişkileri eklendi; iki repoya yayılan işler karşılıklı bağlantılıdır.

| Paket | Backend | Mobil |
| --- | --- | --- |
| 1 | [#4](https://github.com/is-koala/koala-backend/issues/4) | [#7](https://github.com/is-koala/koala/issues/7) |
| 2 | [#5](https://github.com/is-koala/koala-backend/issues/5) | [#8](https://github.com/is-koala/koala/issues/8) |
| 3 | — | [#5](https://github.com/is-koala/koala/issues/5) |
| 4 | [#6](https://github.com/is-koala/koala-backend/issues/6) | [#9](https://github.com/is-koala/koala/issues/9) |
| 5 | [#7](https://github.com/is-koala/koala-backend/issues/7) | [#10](https://github.com/is-koala/koala/issues/10) |
| 6 | [#8](https://github.com/is-koala/koala-backend/issues/8) | [#11](https://github.com/is-koala/koala/issues/11) |
| 7 | [#9](https://github.com/is-koala/koala-backend/issues/9) | [#12](https://github.com/is-koala/koala/issues/12) |
| 8 | — | [#13](https://github.com/is-koala/koala/issues/13) |
| 9 | [#10](https://github.com/is-koala/koala-backend/issues/10) | [#14](https://github.com/is-koala/koala/issues/14) |
| 10 | [#11](https://github.com/is-koala/koala-backend/issues/11) | — |

Mobil #5 mevcut beyaz harita kaydıdır; yeniden açılmadı ve mevcut üst ilişkisi değiştirilmedi. İlk girişim kayıtları backend #1 ve mobil #4 geçmiş bağlamı korur; yeni spec'ler bunların yerine silinmez veya kapatılmaz.

## Küçük commit sırası

1. Her repoda ayrı belge commit'i: CONTEXT, ADR, araştırma, onaylı spec ve iş dizini. Davranış değişmez.
2. Backend #4 altında SMTP alıcı güvenliği + uyumlu kuyruk retry/DLQ + testler. Bu, paket 1'in backend alt adımıdır; tek başına paket 1'i kapatmaz.
3. Mobil #7 altında açık ortam seçimi, uygulama kimlikleri ve gerçek giriş. Mobil branch: `feat/staging-real-auth`.
4. Sonraki her paket kendi kabul kanıtı ve commit/PR sınırıyla ilerler; paket içi bağımsız düzeltmeler de ayrılır.

Backend main üzerinde kalır. Mobil setup PR #3 henüz birleşmediğinden özellik branch'inin tabanı PR birleştirme şekline göre kontrol edilmelidir; bu işlem otomatik rebase/merge yetkisi vermez. Kullanıcıya commit kapsamı ve test sonucu sunulmadan commit, tag veya push yapılmaz. Backend push'u Dokploy dağıtımını tetikleyebildiği için ayrıca ele alınır.
