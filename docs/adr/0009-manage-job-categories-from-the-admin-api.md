# İş kategorilerini admin API'sinden yönet

Durum: Kabul edildi — 5 Eylül 2026. [ADR-0008](0008-ship-an-empty-database-without-sample-data.md)'in referans verisi listesindeki "iş kategorileri" maddesinin yerine geçer.

## Karar

İş kategorileri artık veritabanıyla birlikte gelmez. `istanbul`/`PHOTOGRAPHY`/`WAITER`/`PET_CARE`/`DESIGN`/`BARISTA` seed'i kaldırılmıştır; katalog `/admin/job-categories` uçlarından ADMIN yetkisiyle yönetilir.

ADR-0008 kategorileri ülke/il/ilçe ile aynı kefeye koyup "referans verisi" saymıştı. Bu yanlıştı: ülke ve ilçe listesi dış dünyanın verisidir, değişmez ve ürün kararı içermez. Kategori ise bir **ürün kararıdır** — hangi işlerin platformda açılabileceğini ve hangilerinin sigorta zorunlu olduğunu belirler. Bu karar kodda sabit durmamalı.

## Bağlam

Kategori sabit bir Liquibase seed'iydi. Hangi kategorinin sigorta zorunlu kılacağını değiştirmek migration yazmayı ve deploy etmeyi gerektiriyordu; yeni kategori eklemenin başka yolu yoktu. Beş kategorinin hepsi `requires_insurance = false` ile geliyordu, Barista dahil — yani kural altyapısı vardı ama hiç kullanılmıyordu.

Değerlendirilen alternatifler:

- **Seed'i güncel tutmak:** her ürün kararı bir migration ve deploy demek. Kategori listesi ürün ekibinin değil, geliştiricinin kontrolünde kalırdı.
- **`app_settings`'e taşımak:** o tablo sayısal tekil değerler için tasarlanmış bir beyaz liste; kod, ad ve sigorta bayrağı taşıyan bir katalog oraya sığmaz.
- **Admin API'si (seçilen):** katalog veriye dönüşür, ürün kararı deploy gerektirmez.

## Sonuçlar

- **Boş bir veritabanında hiç kategori yoktur ve kategori olmadan ilan açılamaz.** Kurulumun ilk adımı bir adminin kategorileri oluşturmasıdır. Bu, admin panelinin ilk sürümünün neden kategori yönetimiyle başladığının sebebidir.
- Kategori silme, o kategoriye bağlı ilan/iş alarmı/ilan şablonu varsa reddedilir. Kontrol **native sorgu** ile yapılır: `Job`, `JobAlert` ve `JobTemplate` üzerindeki `@SQLRestriction("is_deleted = false")` yüzünden türetilmiş sorgu soft-delete edilmiş satırı göremez, ama yabancı anahtar onu hâlâ tutar. `JobCategoryAdministrationIT` bu davranışı sabitler.
- Seed'i silen migration yalnız **referanssız** satırları siler. Bu, bu ortamda gereğinden temkinlidir (veritabanı boş), ama veri barındıran herhangi bir ortamda migration'ın yabancı anahtar ihlaliyle patlayıp uygulamayı hiç açılamaz hâle getirmesini önler.
- Kategorilerin sabit UUID'leri kaybolur. Mobil uygulama ikon, pin ve renk eşlemesini `code` alanına göre yaptığı için yeniden oluşturulan kategorilerin **kodları eskisiyle aynı yazılmalıdır**; aksi halde bilinmeyen kategori varsayılan pine düşer.
- `2026/08/27-10-job-category-seed.xml` **değiştirilmeden bırakılmıştır**. Uygulanmış bir changeSet'i düzenlemek mevcut veritabanlarında checksum'ı bozar; temiz veritabanında beş satır oluşur ve hemen ardından silinir, net sonuç aynıdır.
