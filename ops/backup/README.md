# Backup automation

`postgres-backup.sh` her gün iki bağımsız kurtarma noktası üretir: hızlı nesne düzeyi geri yükleme için PostgreSQL custom-format mantıksal yedeği, zaman noktasına geri dönüş için `pg_basebackup --wal-method=stream` ile fiziksel taban yedeği. Her arşivin SHA-256 toplamı birlikte yüklenir. Pazar kopyaları `weekly/` ve `pitr/weekly/`, ayın ilk günü kopyaları `monthly/` ve `pitr/monthly/` öneklerine gider. `archive-wal.sh`, PostgreSQL `archive_command` için fiziksel taban yedeğini ileri taşıyan idempotent WAL yükleme giriş noktasıdır.

Gerekli değişkenler değerleri loglamadan okunur: `BACKUP_DATABASE_URL`, `BACKUP_S3_URI`, `BACKUP_KMS_KEY_ID`, `BACKUP_ENVIRONMENT`. Bu iş için kullanılan AWS kimliği medya depolama kimliğinden farklı olmalı ve yalnız yedek kovası/anahtarı için gerekli en az izinleri taşımalıdır.

Örnek zamanlama:

```cron
15 1 * * * /srv/koala/ops/backup/postgres-backup.sh
```

`pg_basebackup` çalıştıran rol `REPLICATION` yetkisine ve `pg_hba.conf` erişimine sahip olmalıdır. PostgreSQL `archive_mode=on`, `archive_timeout=15min` ve `archive_command='/srv/koala/ops/backup/archive-wal.sh %p %f'` ile yapılandırılır. `restore-latest.sh --tier daily|weekly|monthly` mantıksal yedeği seçer. `restore-pitr.sh --tier daily|weekly|monthly --target-time <UTC ISO-8601>` hedef zamandan daha yeni olmayan son fiziksel tabanı doğrular, `recovery.signal`, `restore_command` ve `recovery_target_time` üretir; PostgreSQL'i kendisi başlatmaz.

Kurulumdan önce `--dry-run` kullanılmalıdır. Kova sürümleme, Object Lock retention, çapraz bölge kopyalama ve yaşam döngüsü yapılandırması sağlayıcı tarafında uygulanır; `s3-lifecycle.json` sürüm kontrollü sözleşmedir. Bu repo sağlayıcı hesabına erişmez ve ayarları uygulanmış kabul etmez.
