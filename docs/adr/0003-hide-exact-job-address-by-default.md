# Tam iş adresini varsayılan olarak gizle

Durum: **[ADR-0017](0017-publish-company-job-locations-by-default.md) ile yerine geçildi — 4 Kasım 2026.** Şirket ilanında tam konum artık varsayılan olarak açık, birim düzeyi alanlar (kat, daire, tarif) yalnız ilan sahibine ve etkin atamalı işçiye döner, harita listesinde atama kesinliği yükseltmez. Bireysel ilan için aşağıdaki kural aynen geçerli. Bu dosya kayıt için bırakıldı.

Herkese açık ilanlar varsayılan olarak yalnız yaklaşık konum gösterir; tam iş adresi başvuru kabul edilip atama kurulduğunda çalışana açıklanır ve atama tamamlandığında veya iptal edildiğinde normal kullanıcı görünümünde yeniden gizlenir. Bireysel işverenin tam adresi hiçbir zaman herkese açık olmaz; şirket işvereni doğrulanmış işyeri adresini her ilan için ayrı bir tercihle yayımlayabilir. Bu karar, işe ulaşım bilgisini korurken özellikle ev adresi niteliğindeki konumların gereksiz ifşasını engellemek için tam koordinatı herkese açmaya tercih edilmiştir.
