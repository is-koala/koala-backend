# Backup automation

`postgres-backup.sh` her gün PostgreSQL custom-format mantıksal yedeği ve SHA-256 toplamını ayrı yedek kovasına KMS ile şifreli yükler. Pazar kopyaları `weekly/`, ayın ilk günü kopyaları `monthly/` önekine gider. `archive-wal.sh`, PostgreSQL `archive_command` için idempotent WAL yükleme giriş noktasıdır.

Gerekli değişkenler değerleri loglamadan okunur: `BACKUP_DATABASE_URL`, `BACKUP_S3_URI`, `BACKUP_KMS_KEY_ID`, `BACKUP_ENVIRONMENT`. Bu iş için kullanılan AWS kimliği medya depolama kimliğinden farklı olmalı ve yalnız yedek kovası/anahtarı için gerekli en az izinleri taşımalıdır.

Örnek zamanlama:

```cron
15 1 * * * /srv/koala/ops/backup/postgres-backup.sh
```

Kurulumdan önce `--dry-run` kullanılmalıdır. Kova sürümleme, Object Lock retention, çapraz bölge kopyalama ve yaşam döngüsü yapılandırması sağlayıcı tarafında uygulanır; `s3-lifecycle.json` sürüm kontrollü sözleşmedir. Bu repo sağlayıcı hesabına erişmez ve ayarları uygulanmış kabul etmez.
