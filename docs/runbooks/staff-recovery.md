# Personel hesabı kurtarma (kilit / unutulan parola)

Personel girişi 5 yanlış parolada hesabı 15 dakika kilitler (`STAFF_MAX_FAILED_ATTEMPTS=5`,
`STAFF_LOCK_DURATION=15m`). Kilit kendiliğinden açılır. **Parola unutulmuşsa kilit süresi dolmasını
beklemek yetmez** — yeni bir parola yazılmadan giriş mümkün değildir.

`BOOTSTRAP_OWNER_PASSWORD` yalnız boş `staff_members` tablosunda ilk `OWNER`'ı doğurur; tablo doluyken
bootstrap hiçbir şey yazmaz. `POST /admin/auth/password` mevcut parolayı ister, yani unutulan parolada
işe yaramaz.

## Ürün yolu — başka bir OWNER varken

`POST /admin/staff/{id}/password` (`staff.manage`, denetim eylemi `staff.member.password.reset`):
kilidi açar, tek kullanımlık parola atar (`must_change_password=true`, `token_version+1`,
`failed_login_attempts=0`, `locked_until=NULL`). Görev ayrılığı: aktör **kendi** hesabına uygulayamaz
(`STAFF_MEMBER_PASSWORD_RESET_SELF`). Hedef personel atanan parola ile giriş yapar, ardından
`POST /admin/auth/password` ile kendi parolasını belirler.

## Son / tek OWNER — bu yordam

Başka `OWNER` yoksa ürün yolu kapalıdır. Aşağıdaki SQL production Postgres'te, tek bir transaction
içinde, **tek bir** `staff_members` satırına karşı çalıştırılır.

### 1. BCrypt özeti üret

Uygulama `BCryptPasswordEncoder` kullanır (`$2a$`, cost 10). `$2y$` (htpasswd) de kabul edilir.
Parola en az 12 karakter olmalıdır.

```bash
# $2a$ — Spring BCryptPasswordEncoder (uygulamanın bean'i)
CRYPTO_JAR=$(find ~/.m2/repository/org/springframework/security/spring-security-crypto \
  -name 'spring-security-crypto-*.jar' | sort | tail -1)
jshell --class-path "$CRYPTO_JAR" <<'EOF'
System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        .encode("BURAYA-TEK-KULLANIMLIK-PAROLA"));
/exit
EOF
```

```bash
# $2y$ — apache2-utils / httpd htpasswd; BCryptPasswordEncoder bunu da kabul eder
htpasswd -nbBC 10 unused 'BURAYA-TEK-KULLANIMLIK-PAROLA' | cut -d: -f2
```

Üretilen dize `$2a$` veya `$2y$` ile başlamalıdır. Komuta yazılan parolayı kabuk geçmişinden sil;
özeti runbook'a, ticket'a veya sohbete yapıştırma.

### 2. Satırı tanı

```sql
select id,
       email,
       is_active,
       must_change_password,
       failed_login_attempts,
       locked_until,
       token_version
  from staff_members
 where lower(email) = lower(:email);
```

Etkilenen satır sayısı **1** olmalı. `is_active=false` ise önce hesabın neden kapalı olduğunu
anlamadan parolayı sıfırlama.

### 3. Kilidi aç ve tek kullanımlık parola yaz

```sql
update staff_members
   set password_hash = :bcrypt_hash,
       must_change_password = true,
       failed_login_attempts = 0,
       locked_until = null,
       token_version = token_version + 1
 where id = :staff_id;
```

`token_version+1` açık erişim ve yenileme token'larını aynı anda öldürür. Etkilenen satır sayısı yine
**1** olmalı.

### 4. Kontrol

```sql
select id,
       email,
       is_active,
       must_change_password,
       failed_login_attempts,
       locked_until,
       token_version,
       password_hash
  from staff_members
 where id = :staff_id;
```

Beklenen: `must_change_password=true`, `failed_login_attempts=0`, `locked_until` boş, `token_version`
bir artmış, `password_hash` az önce üretilen özet.

### 5. İlk girişte parola değiştir

Atanan parola ile `POST /admin/auth/login`. Yanıt `mustChangePassword=true` taşır; `/admin/**`'ın
geri kalanı 403'tür. Hemen `POST /admin/auth/password` (`currentPassword` = atanan tek kullanımlık
parola, `newPassword` = personelin kendi seçtiği). Parola değişimi token sürümünü bir kez daha
artırır; yeni parolayla yeniden giriş gerekir.

Kilitliyken **doğru** parola da 401 döner (`STAFF_LOGIN_FAILED`); yanıt kilitli olduğunu sızdırmaz ve
kilit süresini uzatmaz. Bu SQL o kilidi `locked_until = null` ile düşürür.
