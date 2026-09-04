# Production kontrollerini iki katmana ayır

Her production deploy'unda yalnız health, oturum ve zararsız okuma/yetki kontrolleri otomatik çalışır. İlan, başvuru, mesaj ve diğer yazma akışları gizli canary verisiyle ayrı zamanlanmış veya insan tetiklemeli koşularda; gerçek ödeme ise işlem ve iade adımları kayıtlı, insan onaylı bir runbook ile test edilir. Böylece bütün ürün akışları production'da doğrulanırken her deploy'un gerçek veri ve para hareketi üretmesi engellenir.
