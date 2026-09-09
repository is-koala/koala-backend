# Deployment'ı terfi ettir, eski state'i değil

İlk lansmanda mevcut Dokploy uygulama kaydı ve production domainleri korunarak
production'a terfi ettirilir; fakat pre-production PostgreSQL verisi, RabbitMQ
state'i, media nesneleri ve secret seti taşınmaz. Uygulama temiz ve ayrı production
state'ine yeniden bağlanır, legacy state salt-okunur karantinada tutulur ve staging
lansmandan sonra kendi ayrılmış kaynaklarıyla çalışır; böylece mevcut kontrol
düzlemini yeniden kurmadan sentetik veri ve eski oturumların production'a sızması
önlenir.
