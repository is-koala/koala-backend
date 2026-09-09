# koala

Production lansmanı için taslak yordam [lansman geçiş
runbook'unda](docs/runbooks/launch-cutover.md) ve eşlik eden
`ops/launch/launch-cutover-wizard.sh` içinde tutulur. Nihai kabul provası ve
gerçek hedef onayı tamamlanmadan çalıştırılmaz. Betik üretim kaynağı değiştirmez;
operatörü güvenli kontrol noktalarından geçirip git dışındaki yerel kanıt kaydını
oluşturur. Dar smoke sonrası 24 saatlik gözlem, aynı kanıt dosyasıyla yalnız
`--close-observation` modunda kapatılır; ana cutover akışı yeniden çalıştırılmaz.
