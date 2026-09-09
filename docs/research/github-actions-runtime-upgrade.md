# GitHub Actions Node.js 24 major sürüm geçişi

Tarih: 9 Eylül 2026

Kapsam: `koala-backend` iş akışındaki üçüncü taraf action referanslarının
is-koala/koala-backend#2 kabul ölçütleri açısından incelenmesi. Dalga 5'teki
is-koala/koala-backend#91 tetikleyici, maliyet ve runner mimarisi kararları bu
araştırmanın kapsamı dışındadır.

## Sonuç

Issue #2 güncel `main` üzerinde tamamlanmış değildir. İş akışı hâlâ Node.js 20
hedefleyen şu referansları kullanır:

| Mevcut referans | 9 Eylül 2026'da güncel major | Güncel release | Node.js 24 kanıtı |
| --- | --- | --- | --- |
| `actions/checkout@v4` | `actions/checkout@v7` | [`v7.0.1`](https://github.com/actions/checkout/releases/tag/v7.0.1) | [`v7/action.yml`](https://github.com/actions/checkout/blob/v7/action.yml#L115-L118) |
| `actions/setup-java@v4` | `actions/setup-java@v6` | [`v6.0.0`](https://github.com/actions/setup-java/releases/tag/v6.0.0) | [`v6/action.yml`](https://github.com/actions/setup-java/blob/v6/action.yml#L146-L149) |
| `actions/upload-artifact@v4` | `actions/upload-artifact@v7` | [`v7.0.1`](https://github.com/actions/upload-artifact/releases/tag/v7.0.1) | [`v7/action.yml`](https://github.com/actions/upload-artifact/blob/v7/action.yml#L73-L75) |
| `docker/setup-qemu-action@v3` | `docker/setup-qemu-action@v4` | [`v4.3.0`](https://github.com/docker/setup-qemu-action/releases/tag/v4.3.0) | [`v4/action.yml`](https://github.com/docker/setup-qemu-action/blob/v4/action.yml#L31-L34) |
| `docker/setup-buildx-action@v3` | `docker/setup-buildx-action@v4` | [`v4.3.0`](https://github.com/docker/setup-buildx-action/releases/tag/v4.3.0) | [`v4/action.yml`](https://github.com/docker/setup-buildx-action/blob/v4/action.yml#L74-L77) |
| `docker/login-action@v3` | `docker/login-action@v4` | [`v4.6.0`](https://github.com/docker/login-action/releases/tag/v4.6.0) | [`v4/action.yml`](https://github.com/docker/login-action/blob/v4/action.yml#L33-L36) |
| `docker/build-push-action@v6` | `docker/build-push-action@v7` | [`v7.3.0`](https://github.com/docker/build-push-action/releases/tag/v7.3.0) | [`v7/action.yml`](https://github.com/docker/build-push-action/blob/v7/action.yml#L123-L126) |

`setup-qemu-action` issue metnindeki ilk listede yoktur; daha sonra iş akışına
eklenmiştir. Mevcut çalışmanın özeti onu da Node.js 20 action'ı olarak raporladığı
için aynı atomik yükseltmeye dahil edilmelidir. [Başarılı `main` çalışması ve
uyarı özeti](https://github.com/is-koala/koala-backend/actions/runs/34275414127)

Docker action'larının yeni major sürüm notları Node.js 24 için en az Actions
Runner `v2.327.1` ister:

- [`setup-qemu-action@v4.0.0`](https://github.com/docker/setup-qemu-action/releases/tag/v4.0.0)
- [`setup-buildx-action@v4.0.0`](https://github.com/docker/setup-buildx-action/releases/tag/v4.0.0)
- [`login-action@v4.0.0`](https://github.com/docker/login-action/releases/tag/v4.0.0)
- [`build-push-action@v7.0.0`](https://github.com/docker/build-push-action/releases/tag/v7.0.0)

Koala'nın 9 Eylül'deki başarılı PR çalışması self-hosted runner sürümünü
`2.337.0` olarak kaydetmiştir; bu alt sınırın üzerindedir. Aynı çalışma Maven
cache'i için birincil anahtar isabetini, test raporunun yüklenmesini ve eski
action uyarılarının gerçekten üretildiğini gösterir. [PR çalışması
`34294575637`](https://github.com/is-koala/koala-backend/actions/runs/34294575637)

## Korunması gereken mevcut davranış

Action major'larından bağımsız iş akışı davranışı bugün canlı çalışmalarla
doğrulanmıştır:

- Pull request'te `test` geçer; `build and push` ile `deploy` atlanır. [PR
  çalışması](https://github.com/is-koala/koala-backend/actions/runs/34294575637)
- `main` pushunda test ve build çalışır, yalnız `main-bbe5c90` imaj etiketi
  basılır, deploy atlanır. [`main` push
  çalışması](https://github.com/is-koala/koala-backend/actions/runs/34275414127)
- `v0.9.2` tag'inde `0.9.2` ve `latest` aynı imaj digest'ine basılır; Dokploy
  deploy işi başarıyla çalışır. [Tag
  çalışması](https://github.com/is-koala/koala-backend/actions/runs/34275434826)

Bu nedenle #2 için en küçük güvenli değişiklik yalnızca yukarıdaki yedi `uses:`
referansını yükseltmektir. `on`, `runs-on`, job `if` koşulları, `needs`, sürüm
çözümleme kabuğu, `tags`, `platforms`, `provenance`, `sbom`, cache ayarları ve
Dokploy adımı değişmemelidir. Bu sınır #91'in tetikleyici ve runner kararlarına
karışmaz; iki issue aynı YAML dosyasına dokunduğu için eşzamanlı dallar yine de
birleştirme öncesi koordine edilmelidir.

## Doğrulama planı

1. Diff'in yalnız yedi `uses:` satırını değiştirdiğini doğrula.
2. Workflow YAML'ını parse et ve varsa `actionlint` çalıştır.
3. PR çalışmasında test, Maven cache hit'i ve test artifact'ini; build/deploy
   işlerinin atlandığını doğrula. Node.js 20 ve `setup-java v4` uyarıları
   bulunmamalı.
4. Birleştirme sonrası `main` çalışmasında yalnız `main-<sha>` imajının
   basıldığını ve deploy'un atlandığını doğrula.
5. Bir sonraki planlı gerçek `v*` sürümünde sürüm etiketi + `latest` ve yalnız
   tag'e bağlı Dokploy deploy işini doğrula. Sırf bu kontrol için deneme `v*`
   tag'i oluşturulmamalıdır; mevcut workflow böyle bir tag'i production
   deploy'a bağlar.

PR yalnız test job'undaki üç action'ı çalıştırır. Docker action yükseltmeleri ilk
kez `main` pushunda, tag'e özgü deploy zinciri ise sonraki planlı sürümde gerçek
runner üzerinde sınanır. Dolayısıyla workflow değişikliğinin kabulü bu üç aşamalı
kanıtı birlikte gerektirir.
