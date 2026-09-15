# Oturum ve cihaz temizliği (personel)

Kullanıcının kendi oturumlarını kapatması için önce ürün yolu denenir: **Oturumlar → ilgili satır → "Oturumu
kapat"** (`DELETE /users/me/sessions/{sessionId}`) ya da **"Diğer oturumları kapat"** (`DELETE /users/me/sessions`).
Kullanıcıya ulaşılamıyorsa ya da hesap güvenliği bekleyemiyorsa aşağıdaki SQL personel tarafından, salt-okunur
inceleme ve yazma adımları ayrı ayrı, **tek bir transaction içinde** çalıştırılır.

Bu belge is-koala/koala-backend#306'nın 5. maddesi. İleride bu işi yapacak admin ucu
`DELETE /admin/users/{id}/sessions/{sessionId}` (`user.session.revoke` izni, denetim kaydı) **henüz yok**; ayrı
issue'da eklenecek. O gelene kadar bu yordam tek yol.

## Kavramlar

| Tablo | Satır ne | Kapatınca ne olur |
|---|---|---|
| `devices` | kullanıcı + istemci kimliği (`client_device_id`, mobil/web `X-Device-Id`) ya da UA-imzası başına bir satır; `name` otomatik ad, `custom_name` kullanıcının verdiği ad | soft-delete edilince `SessionAuthFilter` cihazı bulamaz, o cihazın her oturumu ilk istekte 401 alır; aynı `X-Device-Id` ile bir sonraki giriş **yeni** satır açar |
| `sessions` | cihaz başına giriş; `refresh_token_hash` yenileme anahtarı, `is_active` erişim kapısı | `is_active=false` erişim token'ını, `refresh_token_hash=null` yenilemeyi keser |
| `login_attempts` | giriş denemeleri (`device_id` ile cihaza bağlı, `location` GeoIP) | dokunulmaz; saklama politikası zaten var (CLAUDE.md §2aw) |

`sessions.is_active=false` yeterlidir: `SessionAuthFilter` ve `/auth/refresh` yalnız aktif oturumu kabul eder.
`refresh_token_hash=null` ek savunmadır — satır yanlışlıkla tekrar aktif edilse bile eski refresh token çalışmaz.

## 1. İncele — kullanıcının cihazları ve oturumları

```sql
select d.id            as device_id,
       d.type,
       d.name,
       d.custom_name,
       d.client_device_id,
       d.last_seen_at,
       s.id            as session_id,
       s.is_active,
       s.ip_address,
       s.location,
       s.last_accessed_at,
       s.refresh_expires_at
  from devices d
  left join sessions s on s.device_id = d.id and s.is_deleted = false
 where d.user_id = :user_id
   and d.is_deleted = false
 order by d.last_seen_at desc nulls last, s.last_accessed_at desc nulls last;
```

Şüpheli satırı tanımak için: `name` `Bilinmeyen cihaz` ya da beklenmeyen tarayıcı/OS, `ip_address`/`location`
kullanıcının söylediğiyle uyuşmuyor, `client_device_id` boş (curl/UA yolu). Kullanıcıya hangi cihazın kendisine ait
olduğunu **sormadan** silme.

## 2. Tek bir oturumu kapat

Erişim ve yenileme aynı anda kesilir; cihaz satırı ve giriş geçmişi durur.

```sql
update sessions
   set is_active = false,
       refresh_token_hash = null,
       updated_at = now()
 where id = :session_id
   and is_deleted = false
   and device_id in (select id from devices where user_id = :user_id);
```

`user_id` şartı yanlış kullanıcıya ait bir oturum kimliğinin yapıştırılmasına karşı; etkilenen satır sayısı **1**
olmalı, 0 ise kimliği ya da kullanıcıyı yeniden kontrol et.

## 3. Bir cihazı bütün oturumlarıyla kapat

Cihaz soft-delete edilir, push token düşürülür, cihazın her oturumu kapatılıp soft-delete edilir. Sıra önemli
değil, ikisi aynı transaction'da.

```sql
update sessions
   set is_active = false,
       refresh_token_hash = null,
       is_deleted = true,
       deleted_at = now(),
       updated_at = now()
 where device_id = :device_id
   and device_id in (select id from devices where user_id = :user_id)
   and is_deleted = false;

update devices
   set push_token = null,
       is_deleted = true,
       deleted_at = now(),
       updated_at = now()
 where id = :device_id
   and user_id = :user_id
   and is_deleted = false;
```

Push token'ı düşürmek şart: aktif satırlardaki `push_token` kısmi benzersiz indeksi soft-delete edilen satırı
görmez, ama aynı token'ın kullanıcının bir sonraki cihaz satırına taşınması `DeviceManager.detachTokenFromOtherDevices`
ile olur ve soft-delete edilmiş satırı `@SQLRestriction` yüzünden görmez; token'ı burada temizlemek çift kaydı
baştan önler.

## 4. Doğrula

```sql
select count(*) as still_open
  from sessions
 where device_id in (select id from devices where user_id = :user_id and is_deleted = false)
   and is_active = true
   and is_deleted = false;
```

Kullanıcı tarafında `GET /users/me/sessions` kapatılan oturumu artık listelemez; kapatılan oturumun erişim token'ı
ile atılan ilk istek 404 `SESSION_NOT_FOUND`, yenileme denemesi 401 `AUTH_SESSION_NOT_FOUND_OR_INACTIVE` alır
(`SessionDetailIT` iki SQL bloğunu da gerçek Postgres'te koşar).

## Yapılmayanlar

- `login_attempts` satırları silinmez; giriş geçmişi güvenlik incelemesi içindir ve kendi saklama penceresi vardır.
- Kullanıcıya bildirim gitmez; ürün kararı verilene kadar personel kullanıcıyı destek kanalından bilgilendirir.
- Bu yordam denetim kaydı bırakmaz (`admin_audit_*` yok). Admin ucu geldiğinde bu belge o uca yönlendirilecek.
