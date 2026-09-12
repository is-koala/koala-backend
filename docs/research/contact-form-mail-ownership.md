# İletişim formu: SMTP kimliği nerede durur

Tarih: 12 Eylül 2026

## Kısa karar

Koala'nın iletişim formu tarayıcı → Next.js BFF → `POST /public/contact` → mevcut e-posta kuyruğu (`NotificationService` → Rabbit → `EmailConsumer` → `JavaMailSender`) yolunu kullanır. SMTP host, kullanıcı ve parola yalnız backend ortamında kalır. Next.js'e `SMTP_*` veya `NEXT_PUBLIC_*` posta sırrı konmaz.

Bu, koala-web#24'ün BFF içinde Nodemailer açmasının düzeltmesidir. Form zaten BFF'den geçiyordu; tarayıcı SMTP konuşmuyordu. Şikayet, Next sürecinin ikinci bir SMTP istemcisi olmasıydı — backend'de `spring.mail.*` zaten vardı.

## Sektör tabanı

Sır, onu kullanan süreçte durur:

- Next.js, `NEXT_PUBLIC_` önekli değişkenleri derlemede tarayıcı bundle'ına gömer. SMTP parolası bu öneki **asla** almaz. Öneksiz değişkenler yalnız Node tarafında vardır. [Next.js environment variables](https://nextjs.org/docs/app/guides/environment-variables)
- Aynı belgede sırların istemci bileşenine sızmasını önlemek için sunucu-yalnız kod önerilir. koala-web bunu ADR-0011 ile zaten zorunlu kılar: tarayıcı `api.iskoala.com` ile HTTP konuşmaz. [ADR-0011](../adr/0011-web-client-architecture.md)
- Spring'in e-posta desteği `JavaMailSender`'ı süreç içinde yapılandırır (`spring.mail.host` / `username` / `password`). Kimlik bilgisi istek gövdesine veya tarayıcıya çıkmaz. [Spring Framework Email](https://docs.spring.io/spring-framework/reference/integration/email.html)

Sık görülen “Next Route Handler + Nodemailer” yazıları (Mailtrap, şablon depolar) ayrı bir Java API'si olmayan siteler içindir. Koala'da mail altyapısı zaten `EmailConsumer` + allowlist + retry/DLQ'dur (`docs/plans/smtp-delivery.md`). İkinci bir SMTP istemcisi allowlist'i, kuyruk dayanıklılığını ve `MAIL_*` rotasyonunu ikiye böler.

Spam: OWASP, herkese açık yazma uçlarında hız sınırı ve gizli alan (honeypot) önerir; görünür CAPTCHA son çaredir. [OWASP Bot Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Bot_Management_and_Anti-Automation_Cheat_Sheet.html) Honeypot tetiklenince sessiz başarı dönmek, bota hangi alanın tuzak olduğunu öğretmez.

## Koala sözleşmesi

1. Ziyaretçi formu aynı origin'deki server action'a gönderir (ADR-0011).
2. BFF doğrular, honeypot'u yerelde yutar, sonra `POST /public/contact` çağırır. SMTP yok.
3. Backend alanları yeniden doğrular, honeypot'u yine yutar, `RateLimitFilter` AUTH kovasına koyar, kuyruğa yazar.
4. `EmailConsumer` `SUPPORT_MAILBOX`'a (varsayılan `info@iskoala.com`) gönderir; `Reply-To` ziyaretçinin e-postasıdır.
5. Teslimat `MAIL_DELIVERY_MODE` / `MAIL_ALLOWED_RECIPIENTS` kurallarına bağlıdır. Staging ALLOWLIST'te `info@iskoala.com` listede yoksa SMTP çağrılmaz (`docs/environments.md`).

## Yusuf'un `info@` kutusu

| Değişken | Nerede | Ne işe yarar |
| --- | --- | --- |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | backend Dokploy | Mevcut `JavaMailSender`. Next'te kopyası yok. |
| `SUPPORT_MAILBOX` | backend, varsayılan `info@iskoala.com` | Formun **alıcısı**. SMTP kullanıcısı olmak zorunda değil. |
| `MAIL_DELIVERY_MODE` | backend | Production cutover'da `UNRESTRICTED`; staging `ALLOWLIST`. |
| `MAIL_ALLOWED_RECIPIENTS` | backend | ALLOWLIST iken `info@iskoala.com` bu listede olmalı, yoksa kuyruk `SUPPRESSED` olur. |

Next `.env.local` içinden `SMTP_*` ve `SUPPORT_MAILBOX` kaldırılır.
