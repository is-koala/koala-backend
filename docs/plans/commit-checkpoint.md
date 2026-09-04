# İlk commit kontrol noktası

5 Eylül 2026 hazırlık kontrol noktası. Kullanıcı daha sonra bu paketlerin ayrı ayrı commit ve push edilmesine açıkça izin verdi. Aşağıdaki komutlar paket sınırlarını belgeler; uygulanmış commit'lerin tekrar çalıştırılması gerekmez. Tag ve deploy ayrı yayın kontrolüdür. Index'te başka dosya varsa önce kapsamı kontrol edin; `git add .` / `git add -A` kullanmayın.

## 1 — Backend karar ve iş planı

Branch `main` olarak kalır. Yalnız belgeler ve QA hazırlık dosyasını Git dışında tutan ignore satırı alınır. SMTP davranışı bu commit'e girmez.

```bash
cd /Users/yusssss/Projects/koala/koala-backend
git branch --show-current
git diff --cached --stat
git add -- .gitignore CONTEXT.md \
  docs/adr/0001-use-current-stack-until-launch.md \
  docs/adr/0002-freeze-pricing-at-job-publication.md \
  docs/adr/0003-hide-exact-job-address-by-default.md \
  docs/adr/0004-use-an-isolated-in-app-iyzico-checkout.md \
  docs/adr/0005-use-tiered-production-smoke-tests.md \
  docs/research/test-environment-strategy.md \
  docs/research/iyzico-mobile-checkout.md \
  docs/plans/real-api-test-flow.md \
  docs/plans/implementation-slices.md \
  docs/plans/provider-setup.md \
  docs/plans/github-issues.md \
  docs/plans/commit-checkpoint.md
git diff --cached --check
git diff --cached --stat
git commit -m "docs: define real-api QA decisions and delivery slices" -m "Refs #3"
```

## 2 — Mobil karar ve iş planı

Bu çalışma için `feat/staging-real-auth` branch'i açıldı. Setup PR #3 henüz birleşmedi; özellik PR'ı açılmadan önce taban/diff kontrolü yapılır. Branch değiştirme veya rebase bu komutların parçası değildir. Ortam doğrulama kodu henüz tamamlanmadığı için bu commit'e alınmaz.

```bash
cd /Users/yusssss/Projects/koala/koala-mobile
git branch --show-current
git diff --cached --stat
git add -- CONTEXT.md \
  docs/adr/0001-use-current-stack-until-launch.md \
  docs/adr/0002-freeze-pricing-at-job-publication.md \
  docs/adr/0003-hide-exact-job-address-by-default.md \
  docs/adr/0004-use-an-isolated-in-app-iyzico-checkout.md \
  docs/adr/0005-use-tiered-production-smoke-tests.md \
  docs/adr/0006-use-distinct-mobile-identities-per-environment.md \
  docs/research/location-and-map.md \
  docs/plans/real-api-test-flow.md \
  docs/plans/implementation-slices.md \
  docs/plans/provider-setup.md \
  docs/plans/github-issues.md \
  docs/plans/commit-checkpoint.md
git diff --cached --check
git diff --cached --stat
git commit -m "docs: define real-api mobile QA decisions and delivery slices" -m "Refs #6"
```

## 3 — Backend SMTP alt paketi

Kapsam ve test/inceleme sonucu [SMTP teslim notlarında](https://github.com/is-koala/koala-backend/issues/4) takip edilir; yerel ayrıntı backend `docs/plans/smtp-delivery.md` içindedir. 608 birim + 6 entegrasyon testi geçti, gerçek kart girdisi gerektiren 1 test atlandı. İki inceleme ekseninde açık bulgu yok.

Bu checkpoint için 11 dosyalık seçili patch hazırlandı. `.env.example` ve `application.yaml` içindeki SMTP hunk'ları alınır; demo seed hunk'ları dışarıda kalır. Patch mevcut HEAD index'ine karşı `--check` ile doğrulandı; henüz stage edilmedi. Geçici dosya silindiyse veya ilgili dosyalar değiştiyse zorlamayın, patch yeniden üretilmelidir.

```bash
cd /Users/yusssss/Projects/koala/koala-backend
git diff --cached --stat
git apply --cached --check /private/tmp/koala-qa-setup.WMEEoJ/smtp-commit.patch
git apply --cached /private/tmp/koala-qa-setup.WMEEoJ/smtp-commit.patch
git diff --cached --check
git diff --cached --stat
git diff --cached
git commit -m "feat: guard QA email delivery and recover SMTP failures" -m "Refs #4; preserve the deployed email queue and retry messages without AMQP IDs"
```

`Refs` kullanılır, `Closes` değil: gerçek hesap, bağlantı ve mobil ortam kabulü bitmeden paket 1 kapanmaz.

## Henüz commit kapsamına alınmayanlar

- Backend seed, security/static demo değişiklikleri; geçici output/tmp dosyaları ve diğer script'ler.
- Mobil mevcut auth/profile/job/payment değişiklikleri, demo bootstrap, bağımlılık/lock dosyaları ve tamamlanmamış ortam seçimi.
- Maps yapılandırması, DB veri üretimi, WebView ve fiyat paketleri kendi issue ve doğrulamasıyla ayrıca tamamlanır.

Sıradaki kod checkpoint'i mobil #7: native dev/staging/production kimlikleri + açık API hedefi + otomatik demo girişinin kaldırılması + gerçek giriş/bağlantı kabulü. Üç küçük API yapılandırma testi şu an yeşil olsa da helper henüz runtime'a bağlanmadı; mobil paket hazır sayılmaz.

## Yayına çıkış ayrı işlem

Bu komutlarda push veya tag yoktur. Backend'i çıkarmadan önce gerçek SMTP allowlist ayarları hedef ortamda hazır olmalıdır; boş liste bütün postaları bastırır. Sürüm/tag dağıtım kararıyla birlikte ele alınır, her küçük commit için ayrı dağıtım yapılmaz. QA hesapları henüz oluşturulmadı, gerçek SMTP teslimi ve iki simülatör kabulü henüz yapılmadı.
