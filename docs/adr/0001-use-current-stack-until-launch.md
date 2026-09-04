# Mevcut ortamı lansmana kadar test için kullan

Koala henüz gerçek kullanıcılara açılmadığı için mevcut Dokploy uygulaması, PostgreSQL, RabbitMQ ve depolama ile `api.iskoala.com` şimdilik pre-production test ortamıdır; bugün ikinci bir staging altyapısı kurulmayacaktır. Lansmanda mevcut veritabanı tamamen sıfırlanıp aynı altyapı temiz production olarak kullanılacaktır; sentetik verileri ayırmak amacıyla `testData` veya `seedRunId` alanları eklenmez. Bu karar, ilk ayrı staging önerisinin yerine Q25, Q33-B ve Q36-C ile alınmıştır.

## Sonuçlar

- Günlük testler gerçek Koala API'si ve PostgreSQL, gerçek SMTP ile yalnız iki kontrollü alıcı ve Iyzico Sandbox kullanır.
- Seed sürümlü ve idempotent tek seferlik bir işlemdir; normal uygulama açılışında çalışmaz. Seçici seed silme yerine açıkça hedeflenmiş test veritabanı reseti kullanılır.
- Lansman reseti bugün çalıştırılmaz. Çalıştırılacağı anda hedef veritabanı doğrulanır, geri alınabilir yedek hazırlanır; eski kuyruk mesajları, medya ve oturumların temiz production verisine taşınmaması geçiş planında ele alınır.
- Lansman sonrasında geniş sentetik veriyle yapılan testler tekrar production üzerinde yürütülmez; ayrı test altyapısı o dönemin işidir. Production kontrolleri ADR-0005 kapsamındadır.
