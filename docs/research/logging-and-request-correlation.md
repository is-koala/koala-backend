# Yapılandırılmış loglama ve istek korelasyonu

Tarih: 5 Eylül 2026

Kapsam: Spring Boot 4.1.1 / Java 25 üzerinde çalışan Koala REST API'si için log alanı adlandırması, istek korelasyonu ve JSON log formatı. Bütün iddialar birincil kaynaklara (W3C, IETF, OASIS, OpenTelemetry, Spring, Logback, OWASP, EUR-Lex, KVKK) bağlanmıştır; ikincil blog yazıları kullanılmamıştır.

## Kısa karar

Tek bir HTTP isteği için doğru terim `requestId`'dir. Koala'nın bugün `correlationId` diye ürettiği değer aslında bir request id'dir ve isim yanlış kullanılmaktadır.

Gerekçe üç maddede:

1. `traceId`/`spanId` W3C Trace Context'in normatif terimleridir ve bir isteği **süreçler arasında** izlemek içindir. Koala bugün bunları hiç üretmiyor. [W3C Trace Context, REC 23 Kasım 2021](https://www.w3.org/TR/trace-context/)
2. `requestId` hiçbir standartta tanımlı değildir; tek bir HTTP istek/yanıt çiftini kenar (edge) katmanında tekilleştiren de-facto bir alandır. IANA HTTP alan adı kayıt defterinde `X-Request-Id` de `X-Correlation-Id` de yoktur; buna karşılık `traceparent` ve `tracestate` kalıcı (permanent) olarak kayıtlıdır. [IANA HTTP Field Name Registry](https://www.iana.org/assignments/http-fields/http-fields.xhtml)
3. `correlationId` mesajlaşma kökenli bir terimdir ve **birden fazla mesajı birbirine bağlamak** için vardır. EIP kalıbı, JMS, AMQP ve Spring Integration bu anlamı açıkça yazar. Her istekte sıfırdan üretilip hiçbir aşağı akış çağrısına taşınmayan bir değer bu tanıma uymaz. [EIP Correlation Identifier](https://www.enterpriseintegrationpatterns.com/patterns/messaging/CorrelationIdentifier.html)

Somut yargı: Koala'nın `CorrelationIdFilter` sınıfı üretilen değeri hiçbir yere yaymıyor (RabbitMQ mesajlarına, iyzico çağrılarına, S3 isteklerine geçmiyor) ve mobil istemci `X-Correlation-Id` başlığını hiç göndermiyor. Yani pratikte istek başına taze üretilip yalnızca yanıtta geri yansıtılan bir değer var. Bu bir **request id**'dir. `correlationId` adı burada kavramsal olarak yanlıştır — ancak yanlışlık bir güvenlik açığı değil, terminoloji borcudur; düzeltme yayın sonrası ve geriye dönük uyumlu biçimde yapılabilir (bkz. [Koala için öneri](#7-koala-için-somut-öneri)).

---

## 1. Terminoloji: hangi ad neyi tanımlar

### 1.1 `traceId` ve `spanId` — W3C Trace Context

W3C Trace Context 1.0, 23 Kasım 2021 tarihli bir W3C Recommendation'dır (`REC-trace-context-1-20211123`). İki HTTP başlığı tanımlar: `traceparent` ve `tracestate`. [W3C Trace Context](https://www.w3.org/TR/trace-context/)

`traceparent` biçimi tire ile ayrılmış dört alandır:

```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
             │  │                                │                │
             │  │                                │                └─ trace-flags (2 hex)
             │  │                                └────────────────── parent-id   (16 hex / 8 bayt)
             │  └─────────────────────────────────────────────────── trace-id    (32 hex / 16 bayt)
             └────────────────────────────────────────────────────── version     (2 hex)
```

Alan boyutları ve kuralları:

| Alan | Boyut | Kural |
| --- | --- | --- |
| `version` | 2 hex karakter | Şu anki sürüm `00`; `ff` yasaktır |
| `trace-id` | 32 hex karakter (16 bayt) | Tamamı sıfır olan değer geçersizdir |
| `parent-id` | 16 hex karakter (8 bayt) | Tamamı sıfır olan değer geçersizdir |
| `trace-flags` | 2 hex karakter (8 bit) | Yalnız `sampled` biti tanımlı: `01` örneklenmiş, `00` değil |

Spec, geçersiz `trace-id` (izinsiz karakter veya tamamı sıfır) durumunda satıcıların `traceparent` başlığını **yok saymasını** zorunlu kılar (MUST). [W3C Trace Context](https://www.w3.org/TR/trace-context/)

Terminoloji ayrıntısı önemlidir: spec bu alanı **`parent-id`** diye adlandırır, `span-id` demez. Yalnızca açıklayıcı bir notta "bazı izleme sistemlerinde bu `span-id` olarak bilinir; span, bir istemci isteğinin yürütülmesidir" der. Yani `spanId` adı W3C'nin değil, OpenTelemetry/Zipkin geleneğinin terimidir; W3C başlığındaki karşılığı `parent-id` alanıdır. [W3C Trace Context](https://www.w3.org/TR/trace-context/)

`tracestate` ise satıcıya özgü (vendor-specific) ek bağlamı taşımak içindir. Virgülle ayrılmış `key=value` listesidir (`rojo=00f067aa0ba902b7,congo=t61rcWkgMzE`). Sınırlar: satıcılar birleşik başlığın en az 512 karakterini yaymalıdır (SHOULD), en fazla 32 liste üyesi bulunur, değerler 256 yazdırılabilir ASCII karakter (0x20–0x7E, virgül ve eşittir hariç) ile sınırlıdır. Anahtarlar küçük harf/rakam ile başlar; çok kiracılı biçim `tenant-id@system-id` şeklindedir. [W3C Trace Context](https://www.w3.org/TR/trace-context/)

`tracestate` bir korelasyon kimliği taşıma yeri **değildir**; satıcıların kendi izleme sistemlerine ait konum bilgisini `traceparent` yanında taşımaları içindir. Uygulama verisi için W3C ayrı bir Baggage spesifikasyonu tanımlar ve `baggage` başlığı IANA kayıt defterinde yer almaz.

Her iki başlık da IANA HTTP Field Name Registry'de **permanent** durumda, referansı "Trace Context" olarak kayıtlıdır. [IANA HTTP Field Name Registry](https://www.iana.org/assignments/http-fields/http-fields.xhtml)

### 1.2 `requestId` — hiçbir yerde standart değil

`Request-Id`, `X-Request-Id`, `Correlation-Id`, `X-Correlation-Id` başlıklarının hiçbiri IANA HTTP Field Name Registry'de kayıtlı değildir. [IANA HTTP Field Name Registry](https://www.iana.org/assignments/http-fields/http-fields.xhtml)

IETF httpapi çalışma grubunun ("Building Blocks for HTTP APIs") yayımladığı taslaklar arasında bir request-id başlığı yoktur; gruptaki ilgili taslak `Idempotency-Key`'dir ve bambaşka bir amaca (POST/PATCH'i hataya dayanıklı kılmak) hizmet eder. [IETF httpapi WG](https://datatracker.ietf.org/wg/httpapi/), [draft-ietf-httpapi-idempotency-key-header](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/)

Buna karşılık güçlü bir **de-facto** uygulama vardır. Envoy'un birinci taraf dokümanı `x-request-id` başlığını şöyle tanımlar: Envoy tarafından "bir isteği tekil olarak tanımlamak ve ayrıca kararlı erişim loglaması ve izleme yapmak için" kullanılır. Başlık yoksa Envoy UUID biçiminde yeni bir değer üretir. Kenar davranışı yapılandırmaya bağlıdır: `use_remote_address` true ve `preserve_external_request_id` false ise Envoy dışarıdan gelen değeri yeni üretilen UUID ile **değiştirir**; `preserve_external_request_id` true ise kenar isteğinde mevcut değeri korur; kenar olmayan isteklerde mevcut değer her zaman korunur. Doküman ayrıca `x-request-id` tüm host'lar boyunca yayılırsa kararlı erişim loglaması ve izleme için rastgele örnekleme özelliklerinin kullanılabilir olduğunu belirtir. [Envoy HTTP connection manager headers](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_conn_man/headers)

Yani `requestId` **tek bir HTTP istek/yanıt alışverişini** tanımlar. Kapsamı bir isteğin ömrüdür; bir kullanıcı oturumu, bir iş akışı veya bir dağıtık çağrı zinciri değildir.

Başlık adı seçiminde `X-` öneki hakkında IETF'in kararı nettir: RFC 6648 (BCP 178, Haziran 2012) yeni parametre adları oluşturanların "adlarını `X-` veya benzeri yapılarla öneklememesi GEREKTİĞİNİ (SHOULD NOT)" söyler ve uygulamaların bir parametrenin durumu hakkında yalnızca `X-` varlığına/yokluğuna bakarak varsayımda bulunmasını veya otomatik işlem yapmasını YASAKLAR (MUST NOT). Dolayısıyla yeni bir alan tanımlanacaksa `Request-Id` tercih edilmelidir; ancak `X-Request-Id` yaygın olarak konuşlanmış olduğu için geriye dönük uyumluluk adına desteklenmeye devam edilir. [RFC 6648](https://www.rfc-editor.org/rfc/rfc6648.html)

### 1.3 `correlationId` — mesajlaşma mirası

Terim, Hohpe & Woolf'un Enterprise Integration Patterns kitabındaki **Correlation Identifier** kalıbından gelir. Kalıbın çözdüğü problem birebir şudur: "Bir yanıt almış olan istekçi, bunun hangi isteğe ait yanıt olduğunu nasıl bilir?" Çözüm cümlesi de nettir: "Her yanıt mesajı, bu yanıtın hangi istek mesajına ait olduğunu gösteren tekil bir tanımlayıcı — bir *Correlation Identifier* — içermelidir." Mekanizma: istekçi bir istek kimliği gönderir, yanıtlayıcı bunu gelen mesajdan okur ve **aynı değeri** giden yanıtta korelasyon kimliği olarak kullanır. [EIP Correlation Identifier](https://www.enterpriseintegrationpatterns.com/patterns/messaging/CorrelationIdentifier.html)

Bu tanım standartlaşmış mesajlaşma API'lerinde birebir görünür:

- **Jakarta Messaging (JMS).** `JMSCorrelationID` için javadoc: "Bir istemci bir mesajı bir başkasıyla ilişkilendirmek için `JMSCorrelationID` başlık alanını kullanabilir. Tipik kullanım bir yanıt mesajını istek mesajıyla ilişkilendirmektir." Değer üç tipte olabilir: sağlayıcıya özgü mesaj kimliği (`'ID:'` öneki zorunlu), uygulamaya özgü `String` (`'ID:'` ile başlamamalı) veya sağlayıcıya özgü `byte[]` (taşınabilir değil). [Jakarta Messaging 3.1 `Message` javadoc](https://jakarta.ee/specifications/messaging/3.1/apidocs/jakarta.messaging/jakarta/jms/message)
- **AMQP 1.0 (OASIS Standard, 29 Ekim 2012).** Mesaj `properties` bölümünde `message-id` "application message identifier", `correlation-id` ise "application correlation identifier" olarak tanımlanır. `correlation-id` açıklaması: "Bu, istemciler arasındaki mesajları işaretlemek veya tanımlamak için kullanılabilen istemciye özgü bir kimliktir." Her ikisi de isteğe bağlıdır ve `ulong`, `uuid`, `binary`, `string` tiplerini alabilir. [OASIS AMQP 1.0 Messaging](https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html)
- **AMQP 0-9-1 / RabbitMQ.** RabbitMQ'nun resmi RPC eğitimi `correlation_id` özelliğini "RPC yanıtlarını isteklerle ilişkilendirmek için kullanışlıdır" diye tanımlar ve tek bir geri-çağrı kuyruğu üzerinde her isteğe tekil bir `correlation_id` verip gelen yanıtları bu değere göre eşleştirmeyi öğretir; eşleşmeyen yanıtlar güvenle atılır. `reply_to` ise geri-çağrı kuyruğunun adını taşır. [RabbitMQ RPC tutorial](https://www.rabbitmq.com/tutorials/tutorial-six-python)
- **Spring Integration.** Önceden tanımlı mesaj başlıkları tablosunda `IntegrationMessageHeaderAccessor.CORRELATION_ID` (tip `java.lang.Object`) kullanımı tek cümleyle verilir: "İki veya daha fazla mesajı ilişkilendirmek için kullanılır." Yanında `SEQUENCE_NUMBER` ve `SEQUENCE_SIZE` bulunur; birlikte splitter/aggregator senaryosunu modellerler. [Spring Integration — Message](https://docs.spring.io/spring-integration/reference/message.html)

Ortak payda: `correlationId` **birden çok mesajı/işlemi bir gruba bağlayan** kimliktir. Bir mesajın kendi kimliği değildir — o `message-id`/`JMSMessageID`'dir.

### 1.4 Blunt verdict

| Ad | Sahip spec | Neyi tanımlar | Kapsam | Yayılır mı |
| --- | --- | --- | --- | --- |
| `traceId` | [W3C Trace Context](https://www.w3.org/TR/trace-context/) (`trace-id`) | Tüm dağıtık çağrı ağacı | Servisler arası, uçtan uca | Evet, `traceparent` ile zorunlu |
| `spanId` | W3C'de `parent-id`; `spanId` adı OTel/Zipkin geleneği | Tek bir iş birimi (bir servisin işlemesi) | Tek süreç içi bir işlem | Evet, her atlamada yenilenir |
| `requestId` | Yok (de-facto: [Envoy `x-request-id`](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_conn_man/headers)) | Tek bir HTTP istek/yanıt çifti | Kenardan başlayıp yanıtla biter | Genelde kenarda üretilip aşağı yayılır |
| `correlationId` | Mesajlaşma: [EIP](https://www.enterpriseintegrationpatterns.com/patterns/messaging/CorrelationIdentifier.html), [JMS](https://jakarta.ee/specifications/messaging/3.1/apidocs/jakarta.messaging/jakarta/jms/message), [AMQP 1.0](https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html) | Birbirine ait iki veya daha fazla mesaj | İş akışı/konuşma | Tanımı gereği taşınır; taşınmazsa anlamı yoktur |

**Tek bir HTTP isteği için doğru ad `requestId`'dir.** Dağıtık izleme isteniyorsa doğru ad çifti `traceId` + `spanId`'dir ve bunlar `traceparent` ile taşınmalıdır.

**`correlationId` her istekte taze üretilip hiçbir yere yayılmıyorsa kötüye kullanılıyordur.** Terimin tüm sahip spesifikasyonlarında ortak olan tek şey, aynı değerin **birden fazla mesajda tekrar görünmesidir**; yayılmayan bir korelasyon kimliği tanım gereği hiçbir şeyi korele etmez. Bu durumda alan aslında bir request id'dir ve öyle adlandırılmalıdır.

`correlationId` adının Koala'da meşru kalacağı tek senaryo şudur: değer istemciden (mobil) gelip HTTP isteği + RabbitMQ mesajı + e-posta gönderimi + iyzico çağrısı gibi **birden fazla işlemi tek bir kullanıcı niyetine** bağlıyorsa. O zaman da her istekte yeniden üretilmemeli, uçtan uca taşınmalıdır.

---

## 2. OpenTelemetry semantic conventions — HTTP server span'leri

Okunan sürüm: **OpenTelemetry Semantic Conventions 1.44.0**. [OTel Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/) — HTTP bölümü "Status: Stable" olarak işaretlidir. [HTTP spans](https://opentelemetry.io/docs/specs/semconv/http/http-spans/)

Span adı kuralı: `{method} {target}`; server span'lerinde `{target}` = `http.route`. `http.route` düşük kardinaliteli olmak ZORUNDADIR (MUST) ve tüm statik yol parçalarını içerip dinamik parçaları yer tutucuyla göstermelidir. [HTTP spans](https://opentelemetry.io/docs/specs/semconv/http/http-spans/), [HTTP attribute registry](https://opentelemetry.io/docs/specs/semconv/registry/attributes/http/)

### 2.1 Zorunluluk seviyeleri

| Öznitelik | Seviye | Not |
| --- | --- | --- |
| `http.request.method` | **Required** | Bilinmeyen metot `_OTHER` olmalıdır (MUST). Metot adları büyük/küçük harf duyarlıdır ve bilinen bir metot adıyla tam eşleşmelidir |
| `url.path` | **Required** | URI yol bileşeni |
| `url.scheme` | **Required** | `http` / `https` |
| `error.type` | Conditionally Required | İstek hatayla bittiyse |
| `http.request.method_original` | Conditionally Required | `http.request.method`'dan farklıysa (istemcinin gönderdiği ham metot) |
| `http.response.status_code` | Conditionally Required | Bir yanıt kodu alındı/gönderildiyse |
| `http.route` | Conditionally Required | "Yalnızca ve yalnızca mevcutsa"; framework desteklemiyorsa doldurulMAmalıdır |
| `network.protocol.name` | Conditionally Required | `http` değilse ve sürüm ayarlıysa |
| `server.port` | Conditionally Required | Mevcutsa ve `server.address` ayarlıysa |
| `url.query` | Conditionally Required | Bir sorgu dizesi alındı/gönderildiyse |
| `client.address` | Recommended | |
| `server.address` | Recommended | |
| `user_agent.original` | Recommended | |
| `network.protocol.version` | Recommended | |
| `network.peer.address` / `network.peer.port` | Recommended | |
| `client.port` | Opt-In | |
| `http.request.header.<key>` / `http.response.header.<key>` | Opt-In | |
| `http.request.body.size`, `http.request.size`, `http.response.body.size`, `http.response.size` | Opt-In | |
| `network.local.address`, `network.local.port`, `network.transport` | Opt-In | |
| `user_agent.synthetic.type` | Opt-In | |

Kaynak: [HTTP spans](https://opentelemetry.io/docs/specs/semconv/http/http-spans/)

### 2.2 Öznitelik tanımları ve gizlilik notları

- `client.address` (Stable): "İstemci adresi — ters DNS araması olmadan elde edilebiliyorsa alan adı; aksi hâlde IP adresi veya UNIX domain socket adı." Spec, proxy gibi ara katmanların arkasındaki **asıl istemcinin** adresini temsil etmesi gerektiğini söyler; yani doğrudan eş (peer) değil. HTTP bölümü bunu açıkça `Forwarded#for`, `X-Forwarded-For` veya benzeri bir başlıktan okunacak "tüm proxy'lerin arkasındaki orijinal istemcinin IP adresi" olarak tarif eder. Ara katmanın kendi adresi `network.peer.address`'e gider. [client attributes](https://opentelemetry.io/docs/specs/semconv/registry/attributes/client/), [HTTP spans](https://opentelemetry.io/docs/specs/semconv/http/http-spans/)
- `user_agent.original` (Stable): "İstemcinin gönderdiği HTTP User-Agent başlığının değeri." Türetilmiş `user_agent.name`, `user_agent.version`, `user_agent.os.*`, `user_agent.synthetic.type` alanları hâlâ *Development* seviyesindedir. [user-agent attributes](https://opentelemetry.io/docs/specs/semconv/registry/attributes/user-agent/)
- `url.full` (Stable): kimlik bilgileri İÇERMEMELİDİR (MUST NOT). `https://user:pass@www.example.com/` biçimindeki değerler `https://REDACTED:REDACTED@www.example.com/` olarak maskelenMELİDİR (SHOULD). Ayrıca `X-Amz-Signature`, `X-Amz-Credential`, `X-Amz-Security-Token`, `sig`, `X-Goog-Signature` gibi sorgu parametrelerinin değerleri varsayılan olarak maskelenmelidir; maskelendiğinde **anahtar korunmalıdır**. [url attributes](https://opentelemetry.io/docs/specs/semconv/registry/attributes/url/)
- `url.path` (Stable): "Enstrümantasyon tanıyabildiğinde `url.path` içindeki hassas içerik temizlenMELİDİR (SHOULD)." Aynı maskeleme kuralları `url.query` için de geçerlidir. [url attributes](https://opentelemetry.io/docs/specs/semconv/registry/attributes/url/)
- `http.request.header.<key>` / `http.response.header.<key>` (Stable): "Enstrümantasyonlar hangi başlıkların yakalanacağının açıkça yapılandırılmasını İSTEMELİDİR (SHOULD). Tüm istek başlıklarını dâhil etmek bir güvenlik riski olabilir." Hassas başlıkların yakalanması önerilmez. [HTTP attribute registry](https://opentelemetry.io/docs/specs/semconv/registry/attributes/http/)

Bu üç madde, aşağıdaki PII bölümünün OTel tarafındaki normatif karşılığıdır: Koala erişim logunda `url.query` ham hâlde yazılmamalı, `Authorization`/`Cookie` başlıkları hiç yakalanmamalıdır.

---

## 3. Log kayıtlarında iz bağlamı (log ↔ trace korelasyonu)

### 3.1 OpenTelemetry log veri modeli

OTel Logs Data Model'de bir `LogRecord` üst düzey alanları arasında `TraceId`, `SpanId` ve `TraceFlags` vardır:

- `TraceId`: bayt dizisi; "W3C Trace Context'te tanımlandığı biçimde istek iz kimliği". İsteğe bağlıdır.
- `SpanId`: bayt dizisi; belirli bir işleme span'ini tanımlar. "`SpanId` mevcutsa `TraceId` de mevcut OLMALIDIR (SHOULD)."
- `TraceFlags`: tek bayt; W3C Trace Context'te tanımlı iz bayrağı (şu an yalnız SAMPLED).

Diğer üst düzey alanlar: `Timestamp`, `ObservedTimestamp`, `SeverityText`, `SeverityNumber` (1–24), `Body`, `Resource`, `InstrumentationScope`, `Attributes`, `EventName`. [OTel Logs Data Model](https://opentelemetry.io/docs/specs/otel/logs/data-model/)

### 3.2 Mevcut (legacy) log formatlarında iz bağlamı

OTel'in "Trace Context in Legacy Formats" spesifikasyonu, kendi veri modelini kullanmayan log formatları için **alan adlarını** normatif olarak verir:

- `trace_id`
- `span_id`
- `trace_flags`

`trace_id` ve `span_id` küçük harfli ve hex kodlanmış olmalıdır; `trace_flags` W3C traceflags biçiminde yazılmalıdır. JSON formatları için bu alanlar "JSON yapısında üst düzey alanlar" olarak yazılmalıdır. Üçü de isteğe bağlıdır. Spec MDC veya loglama kütüphanesi bağlamına değinmez. [OTel logging trace context compatibility](https://opentelemetry.io/docs/specs/otel/compatibility/logging_trace_context/)

Not: Bu adlar (`trace_id`, `span_id`) OTel'in JSON logları için normatif tercihidir; Elastic Common Schema ise aynı kavramlar için **nokta gösterimi** kullanır ve OTLP ile uyumlu olduğunu belirtir:

- `trace.id` (keyword): "İzin tekil tanımlayıcısı. Bir iz, birbirine ait olan işlemler gibi birden fazla olayı gruplar."
- `span.id` (keyword): "İzinin kapsamı içinde span'in tekil tanımlayıcısı. Bir span, bir işlem içindeki bir operasyonu temsil eder."
- `transaction.id` (keyword): iz kapsamındaki en üst düzey iş birimi; OTLP eşlemesi için "not applicable" işaretlidir.

ECS bu alanların alan-kümesi adı altında **iç içe geçmediğini** (yani `tracing.trace.id` değil `trace.id` olduğunu) vurgular. [ECS Tracing fields](https://www.elastic.co/docs/reference/ecs/ecs-tracing)

Yani hedef sink'e göre karar verilir: saf OTLP/OTel Collector hedefliyorsanız `trace_id`/`span_id`, Elasticsearch/ECS hedefliyorsanız `trace.id`/`span.id`. Koala bugün ECS formatını seçmiş durumdadır.

### 3.3 MDC anahtarları: Spring / Micrometer Tracing

Micrometer Tracing'in OTel köprüsündeki `Slf4JEventListener`, span kapsamı (scope) açıldığında MDC'yi doğrudan doldurur. Varsayılan anahtarlar kaynak kodda sabittir:

```java
private static final String DEFAULT_TRACE_ID_KEY = "traceId";
private static final String DEFAULT_SPAN_ID_KEY  = "spanId";
...
MDC.put(traceIdKey, span.getSpanContext().getTraceId());
MDC.put(spanIdKey,  span.getSpanContext().getSpanId());
```

Kapsam kapandığında her iki anahtar da MDC'den silinir. 1.1.0'dan beri özel anahtar adları veren bir kurucu da vardır (`Slf4JEventListener(String traceIdKey, String spanIdKey)`). [Micrometer Tracing `Slf4JEventListener`](https://github.com/micrometer-metrics/tracing/blob/main/micrometer-tracing-bridges/micrometer-tracing-bridge-otel/src/main/java/io/micrometer/tracing/otel/bridge/Slf4JEventListener.java)

Spring Boot bu MDC anahtarlarını doküman düzeyinde de sabitler: "Varsayılan korelasyon kimliği `traceId` ve `spanId` MDC değerlerinden oluşturulur. Örneğin Micrometer Tracing MDC'ye `803B448A0489F84084905D3093480352` `traceId`'si ve `3425F23BB2432450` `spanId`'si eklemişse log çıktısı `[803B448A0489F84084905D3093480352-3425F23BB2432450]` korelasyon kimliğini içerir." [Spring Boot — Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)

Spring Boot ayrıca OpenTelemetry (OTLP ile) ve OpenZipkin Brave (Zipkin ile) olmak üzere iki tracer için otomatik yapılandırma sağlar. [Spring Boot — Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)

### 3.4 MDC → Logback yolu

Üç mekanizma vardır ve Koala'yı ilgilendiren üçü de aynı `ILoggingEvent.getMDCPropertyMap()` kaynağından beslenir:

1. **Desen (pattern) dönüştürücüsü.** Spring Boot'un `defaults.xml` dosyası `correlationId` dönüştürme sözcüğünü `org.springframework.boot.logging.logback.CorrelationIdConverter` sınıfına bağlar ve varsayılan konsol/dosya desenlerine `${LOG_CORRELATION_PATTERN:-}` yer tutucusunu koyar. [Spring Boot `defaults.xml`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/resources/org/springframework/boot/logging/logback/defaults.xml)
2. **Formatlayıcı ve varsayılan spec.** `CorrelationIdFormatter.DEFAULT` tam olarak `"traceId(32),spanId(16)"` olarak tanımlıdır; yani MDC anahtar adı + beklenen uzunluk çiftleri. Değerler yoksa aynı genişlikte boşluk basılır (log hizası bozulmaz). [Spring Boot `CorrelationIdFormatter`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/java/org/springframework/boot/logging/CorrelationIdFormatter.java), [`CorrelationIdConverter`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/java/org/springframework/boot/logging/logback/CorrelationIdConverter.java)
3. **Özellik adı.** Desen `logging.pattern.correlation` (sistem özelliği karşılığı `LOG_CORRELATION_PATTERN`) ile değiştirilir. [Spring Boot `LoggingSystemProperty`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/java/org/springframework/boot/logging/LoggingSystemProperty.java), [Spring Boot — Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
4. **Yapılandırılmış encoder.** Aşağıda anlatılan `logging.structured.format.*` yolunda MDC'nin **tüm** anahtar/değer çiftleri JSON nesnesine eklenir; ayrıca SLF4J fluent API'nin `addKeyValue()` çağrıları da dâhil edilir. [Spring Boot — Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)

Önemli sınır: MDC bir `ThreadLocal`'dır. `@Async`, `CompletableFuture`, RabbitMQ dinleyicileri veya elle açılan sanal iş parçacıklarında bağlam otomatik taşınmaz. Micrometer Tracing kullanılırsa taşıma `ObservationHandler`/context-propagation üzerinden yapılır; yapılmazsa bu yollarda log satırları korelasyon alanı olmadan çıkar.

---

## 4. Logback'te JSON loglama

### 4.1 Logback'in kendi `JsonEncoder`'ı

Logback 1.3.8/1.4.8 ile `ch.qos.logback.classic.encoder.JsonEncoder` eklendi. Üst düzey JSON üyeleri: `sequenceNumber`, `timestamp`, `nanoseconds`, `level`, `threadName`, `loggerName`, `context`, `mdc`, `kvpList`, `message`, `throwable` ve 1.5.0'dan itibaren `formattedMessage`. Çıktı JSON Lines standardına uyar ve RFC 8259 §7'ye göre kaçış yapar; her olay tek satırdır.

1.5.0'dan itibaren her üst düzey üye `<with...>` XML elemanıyla açılıp kapatılabilir (örneğin `pellam` adlı bir üye için `<withPellam>`). Dokümante edilen seçenekler arasında `withFormattedMessage`, `withMessage`, `withArguments` vardır; varsayılanda `formattedMessage` dışındaki tüm üyeler açıktır. [Logback — Encoders](https://logback.qos.ch/manual/encoders.html)

Değerlendirme: `JsonEncoder` şema açısından **Logback'e özgüdür**; ne ECS ne GELF ne de OTel alan adlarını üretir. `mdc` içeriği ayrı bir alt nesnede toplanır, üst düzeye düzleştirilmez. Bu yüzden Koala'nın Elasticsearch/ECS hedefi için doğrudan uygun değildir.

### 4.2 `logstash-logback-encoder`

Bu kütüphane **Logback projesinin resmî parçası değildir**; `logfellow` organizasyonu tarafından Apache-2.0 lisansıyla bakılan üçüncü taraf bir projedir. "Jackson tarafından desteklenen JSON ve diğer formatlarda loglamak için logback encoder'ları, layout'ları ve appender'ları" sağlar. [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder)

Sağladıkları: `LogstashEncoder`/`LogstashLayout` (Logstash v1 JSON), `LoggingEventCompositeJsonEncoder`/`Layout` (provider tabanlı, tamamen yapılandırılabilir), `AccessEventCompositeJsonEncoder` (HTTP erişim olayları), TCP/UDP socket appender'ları ve `AsyncDisruptorAppender`.

MDC davranışı: MDC alanları **varsayılan olarak dâhildir**. `<includeMdc>false</includeMdc>` ile tamamen kapatılır; `includeMdcKeyName`/`excludeMdcKeyName` ile anahtar bazlı seçim, `<mdcKeyFieldName>` ile yeniden adlandırma, özel `MdcEntryWriter` ile tip dönüşümü yapılabilir. Özel alanlar global olarak `<customFields>` ile, olay bazında SLF4J fluent API, `StructuredArguments` veya `Markers` ile eklenir.

Sürüm gereksinimleri: 9.x için en az Java 17 ve Logback 1.5.0. [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder)

### 4.3 Spring Boot 4'ün yerleşik yapılandırılmış loglaması (Koala'nın kullandığı yol)

Spring Boot 4.1.1 referans dokümanı üç JSON formatını **kutudan çıktığı gibi** destekler: Elastic Common Schema (ECS), Graylog Extended Log Format (GELF) ve Logstash JSON. [Spring Boot — Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)

Özellik adları:

```properties
logging.structured.format.console=ecs|gelf|logstash|<FQCN>
logging.structured.format.file=ecs|gelf|logstash|<FQCN>

logging.structured.ecs.service.name=...
logging.structured.ecs.service.version=...
logging.structured.ecs.service.environment=...
logging.structured.ecs.service.node-name=...

logging.structured.gelf.host=...
logging.structured.gelf.service.version=...

logging.structured.json.include=...
logging.structured.json.exclude=log.level
logging.structured.json.rename.process.id=procid
logging.structured.json.add.corpname=mycorp
logging.structured.json.stacktrace.root=first
logging.structured.json.stacktrace.max-length=1024
logging.structured.json.stacktrace.max-throwable-depth=10
logging.structured.json.stacktrace.include-common-frames=true
logging.structured.json.stacktrace.include-hashes=true
logging.structured.json.stacktrace.printer=logging-system
logging.structured.json.customizer=<FQCN>
```

`logging.structured.ecs.service.name` varsayılanı `spring.application.name`, `service.version` varsayılanı `spring.application.version`'dır. Üç formatta da MDC'deki her anahtar/değer çifti JSON nesnesine eklenir ve SLF4J fluent `addKeyValue()` çağrıları desteklenir. Özel format için `StructuredLogFormatter<ILoggingEvent>` (Logback) veya `StructuredLogFormatter<LogEvent>` (Log4j2) uygulanır; daha ince kontrol için `StructuredLoggingJsonMembersCustomizer` yazılıp `logging.structured.json.customizer` ile veya `META-INF/spring.factories` ile kaydedilir. [Spring Boot — Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)

Referans dokümanda görünmeyen ama kaynak kodda bulunan iki ek özellik vardır — MDC/bağlam alanlarını topluca kapatmak veya öneklemek için:

```properties
logging.structured.json.context.include=true|false   # varsayılan true
logging.structured.json.context.prefix=<önek>
```

Kaynak: `StructuredLoggingJsonProperties.Context(boolean include, String prefix)` — "bağlam değerlerini etkileyen özellikler (genellikle MDC'den yayılan öğeler)". [Spring Boot `StructuredLoggingJsonProperties`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/java/org/springframework/boot/logging/structured/StructuredLoggingJsonProperties.java)

ECS formatlayıcısının ürettiği tam üye listesi (Logback varyantı):

| JSON üyesi | Kaynak |
| --- | --- |
| `@timestamp` | `ILoggingEvent::getInstant` |
| `log.level`, `log.logger` | seviye ve logger adı |
| `process.pid` | `spring.application.pid` (null değilse) |
| `process.thread.name` | thread adı |
| `service.name`, `service.version`, `service.environment`, `service.node.name` | `logging.structured.ecs.*` |
| `message` | `getFormattedMessage()` |
| *(MDC + KeyValuePair çiftleri)* | `getMDCPropertyMap()` ve `getKeyValuePairs()` |
| `error.type`, `error.message`, `error.stack_trace` | throwable varsa |
| `tags` | marker adları (boş değilse) |
| `ecs.version` | sabit `"8.11"` |

Kaynak: [Spring Boot `ElasticCommonSchemaStructuredLogFormatter` (Logback)](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/java/org/springframework/boot/logging/logback/ElasticCommonSchemaStructuredLogFormatter.java)

Kritik davranış: ECS formatlayıcısı MDC çiftlerini `contextPairs.nested(...)` ile ekler. `ContextPairs.nested(...)` javadoc'u bunu "iç içe adlandırma kullanarak çiftleri ekle (örneğin ECS'de kullanıldığı gibi)" diye tanımlar ve birleştiriciyi `"."` ile kurar. Pratik sonucu şudur: **MDC anahtarında nokta varsa JSON'da iç içe nesne üretilir.** Yani MDC anahtarı `trace.id` ise çıktı `{"trace":{"id":"..."}}` olur — ECS'in beklediği yapı. MDC anahtarı `traceId` ise çıktı düz `"traceId":"..."` olur; bu ECS alanı **değildir**. [Spring Boot `ContextPairs`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/java/org/springframework/boot/logging/structured/ContextPairs.java)

Yeniden adlandırma (`logging.structured.json.rename.*`) tüm üye yollarına uygulanan bir ad işlemcisi olarak çalışır (`members.applyingNameProcessor(...)`), dolayısıyla MDC kökenli üyelere de uygulanabilir. `exclude`/`include` da aynı şekilde yol filtresidir. [Spring Boot `StructuredLoggingJsonPropertiesJsonMembersCustomizer`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/java/org/springframework/boot/logging/structured/StructuredLoggingJsonPropertiesJsonMembersCustomizer.java)

**Uyarı:** `rename` ile üretilen `trace.id` adının iç içe nesne mi yoksa düz noktalı anahtar mı ürettiği, `ContextPairs.nested` ile ad işlemcisinin uygulanma sırasına bağlıdır ve Koala'da bir birim testiyle doğrulanmalıdır. Elasticsearch her iki gösterimi de kabul ettiği için işlevsel risk düşüktür; ancak dashboard alan adları için netleştirilmelidir.

### 4.4 Koala'nın mevcut durumu

- `src/main/resources/logback-spring.xml` iki appender tanımlıyor: `json` profilinde `org.springframework.boot.logging.logback.StructuredLogEncoder` + `<format>ecs</format>`; diğer profillerde elle yazılmış bir desen.
- Elle yazılmış konsol deseni: `%d{HH:mm:ss.SSS} [%thread] [%X{correlationId:-}] %-5level %logger{36} - %msg%n`. Bu desen Spring Boot'un `CONSOLE_LOG_PATTERN` varsayılanını tamamen değiştirdiği için `${LOG_CORRELATION_PATTERN:-}` yer tutucusu devre dışı kalıyor. Micrometer Tracing eklendiğinde `traceId`/`spanId` bu konsol çıktısında **görünmez**; desen elle güncellenmelidir.
- `application.yaml` `logging.structured.ecs.service.name|version|environment` değerlerini ortam değişkenlerinden alıyor. Bu, Spring Boot 4 dokümanındaki özellik adlarıyla birebir uyumludur.
- `pom.xml` içinde `micrometer-tracing-*` veya OTLP exporter bağımlılığı yok; yalnızca `spring-boot-starter-actuator` var. Yani bugün `traceId`/`spanId` MDC'ye hiç yazılmıyor.

---

## 5. Loglarda kişisel veri ve sırlar

### 5.1 OWASP: asla loglanmayacaklar

OWASP Logging Cheat Sheet'in açıkça yasakladığı öğeler:

- Uygulama kaynak kodu
- Oturum tanımlama değerleri (session identification values)
- Erişim token'ları (access tokens)
- Kimlik doğrulama parolaları
- Veritabanı bağlantı dizeleri
- Şifreleme anahtarları ve diğer birincil sırlar
- Banka hesabı veya ödeme kartı sahibi verisi
- Hassas kişisel veriler ve bazı kişisel olarak tanımlanabilir bilgi (PII) biçimleri

Ek olarak ticari bilgi, hukuken kısıtlı veri ve kullanıcının toplanmasına rıza göstermediği bilgiler kaldırılmalı veya maskelenmelidir. [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)

Loglanması gerekenler "ne zaman, nerede, kim, ne" çerçevesindedir: zaman damgaları ve etkileşim tanımlayıcıları; uygulama adı/sürümü, host adı, IP, URL, kod konumu; kullanıcı kimliği, kaynak cihaz/IP, kullanıcı sınıfı; olay tipi, önem derecesi, açıklama, eylem, etkilenen nesne ve sonuç durumu. Loglanması gereken güvenlik olayları arasında kimlik doğrulama hataları, yetkilendirme hataları, girdi/çıktı doğrulama hataları, yönetici eylemleri, hassas veri erişimi ve şüpheli iş mantığı etkinlikleri sayılır. [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)

Koruma tarafında cheat sheet şunları zorunlu tutar: aktarımda güvenli protokol, dinlenme hâlinde kurcalama tespiti ve salt-okunur depolama, denetim izli kısıtlı erişim, saklama süreleri için "yasal, düzenleyici ve sözleşmesel yükümlülüklere" uyum ve logların gerekli saklama süresi dolmadan yok edilmemesi. Log verisi uygulama sırlarıyla aynı korumayı gerektirir. [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)

### 5.2 GDPR'ın loglamaya dokunan hükümleri

- Madde 4(1): "kişisel veri", "kimliği belirli veya belirlenebilir bir gerçek kişiyle ilgili her türlü bilgi" anlamına gelir. Tanım çevrimiçi tanımlayıcıları (online identifier) açıkça kapsar — dolayısıyla IP adresi, cihaz kimliği ve kullanıcı UUID'si log içinde kişisel veridir. [GDPR, EUR-Lex CELEX 32016R0679](https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=CELEX:32016R0679)
- Madde 5(1)(b) amaç sınırlaması: veriler "belirli, açık ve meşru amaçlar için toplanır ve bu amaçlarla bağdaşmayacak biçimde işlenmez". Loglar hata ayıklama/güvenlik amacıyla toplanıyorsa pazarlama analitiğine dönüştürülemez.
- Madde 5(1)(c) veri minimizasyonu: veriler "işlendikleri amaçlarla ilgili olarak yeterli, ilgili ve gerekli olanla sınırlı" olmalıdır.
- Madde 5(1)(e) saklama sınırlaması: veriler "işlendikleri amaçlar için gerekli olandan daha uzun süre veri sahiplerinin kimliğinin belirlenmesine izin veren bir biçimde tutulmaz". Bu, log saklama süresinin (retention) belgelenmesini zorunlu kılar.
- Madde 25: tasarımdan ve varsayılan olarak veri koruma — "uygun teknik ve organizasyonel tedbirler" uygulanır.
- Madde 32(1): işlemenin güvenliği — riske uygun güvenlik seviyesi için tedbirler, "kişisel verilerin takma adlaştırılması (pseudonymisation) ve şifrelenmesi" dâhil.

Kaynak: [GDPR tam metni, EUR-Lex](https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=CELEX:32016R0679)

### 5.3 KVKK (6698 sayılı Kanun)

6698 sayılı Kişisel Verilerin Korunması Kanunu 24 Mart 2016'da kabul edilmiş, 7 Nisan 2016'da Resmî Gazete'de yayımlanmıştır.

- Madde 4 — genel ilkeler: hukuka ve dürüstlük kurallarına uygunluk; doğru ve gerektiğinde güncel olma; belirli, açık ve meşru amaçlar için işlenme; işlendikleri amaçla bağlantılı, sınırlı ve ölçülü olma; ilgili mevzuatta öngörülen veya işlendikleri amaç için gerekli olan süre kadar muhafaza edilme.
- Madde 7 — silme, yok etme veya anonim hâle getirme: hukuka uygun işlenmiş olsa dahi, işlenmesini gerektiren sebepler ortadan kalktığında veriler resen veya ilgili kişinin talebiyle silinir, yok edilir veya anonim hâle getirilir.
- Madde 12 — veri güvenliğine ilişkin yükümlülükler: veri sorumlusu hukuka aykırı işlemeyi ve erişimi önlemek ve verilerin muhafazasını sağlamak için "gerekli her türlü teknik ve idari tedbirleri" almakla yükümlüdür; veri işleyenle müştereken sorumludur ve düzenli denetim yaptırmak zorundadır. Öğrenilen veriler görevden ayrılmadan sonra da başkasına açıklanamaz ve amaç dışı kullanılamaz.

Kaynak: [KVKK — Kişisel Verilerin Korunması Kanunu (6698)](https://www.kvkk.gov.tr/Icerik/6649/Personal-Data-Protection-Law)

### 5.4 Koala için pratik sonuçlar

1. Log gövdesine e-posta, telefon, TC kimlik numarası, adres, kart verisi veya ham istek gövdesi yazılmaz. Kullanıcı kimliği olarak yalnızca opak UUID kullanılır (Koala zaten UUID kullanıyor) — bu GDPR Madde 32(1) anlamında takma adlaştırmadır, anonimleştirme değildir.
2. `Authorization`, `Cookie`, `Set-Cookie` başlıkları ve parola sıfırlama token'ları hiçbir log seviyesinde yazılmaz. Bu, OWASP'ın "access tokens" ve "authentication passwords" maddelerinin doğrudan karşılığıdır ve OTel'in "hangi başlıkların yakalanacağı açıkça yapılandırılmalıdır" kuralıyla örtüşür.
3. `url.query` ham hâlde loglanmaz; en azından token benzeri parametre değerleri maskelenir, anahtar korunur (OTel kuralı). Koala'nın parola sıfırlama akışı token'ı URL'de taşıdığı için bu madde teoriden ibaret değildir.
4. `RequestLoggingFilter` bugün istemci IP'sini logluyor. IP, GDPR Madde 4(1) kapsamında çevrimiçi tanımlayıcıdır; loglanması meşru güvenlik amacıyla savunulabilir ama saklama süresi belirlenmeli ve belgelenmelidir (Madde 5(1)(e), KVKK Madde 4 ve 7).
5. `CorrelationIdFilter` içindeki `[A-Za-z0-9._-]{1,64}` doğrulaması yerinde bir karardır: istemciden gelen değeri log satırına yazmadan önce kısıtlar, CRLF enjeksiyonunu ve log forging'i engeller. Aynı disiplin `User-Agent` gibi diğer istemci kontrollü alanlar için de uygulanmalıdır (yapılandırılmış JSON çıktısında kaçış zaten encoder tarafından yapılır; asıl risk düz metin desen çıktısındadır).
6. Log saklama süresi ve erişim kontrolü ADR veya operasyon dokümanında yazılı olmalıdır; OWASP bunu doğrudan istiyor, KVKK Madde 12 idari tedbir olarak zorunlu kılıyor.

---

## 6. Hata gövdesinde istemciye ne gösterilmeli — RFC 9457

RFC 9457 (Problem Details for HTTP APIs) `application/problem+json` ve `application/problem+xml` medya tiplerini tanımlar ve şu üyeleri belirler:

| Üye | Tip | Anlam |
| --- | --- | --- |
| `type` | string (URI referansı) | Problem tipini tanımlar. Yoksa varsayılan `about:blank`. Tüketiciler problem tipinin birincil tanımlayıcısı olarak `type` URI'sini kullanMALIDIR (MUST) |
| `status` | number | HTTP durum kodu. "Mevcutsa yalnızca tavsiye niteliğindedir; tüketicinin kolaylığı için kullanılan HTTP durum kodunu iletir." Üretici gerçek yanıt koduyla eşleştirmelidir |
| `title` | string | Kısa, insan tarafından okunabilir özet. "Yerelleştirme dışında, problemin bir oluşumundan diğerine değişMEMELİDİR (SHOULD NOT)" |
| `detail` | string | Bu belirli oluşuma özgü açıklama. "Mevcutsa, hata ayıklama bilgisi vermek yerine istemcinin problemi düzeltmesine yardım etmeye odaklanmalıdır" |
| `instance` | string (URI referansı) | "`instance` üyesi, problemin belirli oluşumunu tanımlayan bir URI referansı içeren bir JSON dizesidir." Dereference edilebilir olması gerekmez; edilemiyorsa sunucu için anlamlı opak bir tanımlayıcı işlevi görür. Mutlak URI kullanılması önerilir |

Kaynak: [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html)

Uzantı üyeleri: "Problem tipi tanımları, problem detayları nesnesini o tipe özgü ek üyelerle genişletEBİLİR (MAY)." İstemciler tanımadıkları uzantıları yok saymak ZORUNDADIR (MUST) — bu, ileriye dönük evrimi mümkün kılar. Uzantı adları XML adlandırma kurallarına uymalı, ALPHA/DIGIT/`_` karakterlerinden oluşmalı ve tercihen en az üç karakter olmalıdır. [RFC 9457 §3.2](https://www.rfc-editor.org/rfc/rfc9457.html)

Güvenlik: "Yeni bir problem tipi tanımlarken dâhil edilen bilgi dikkatle gözden geçirilmelidir. Aynı şekilde, bir problem üretirken — nasıl serileştirilirse serileştirilsin — verilen ayrıntılar da incelenmelidir." Ayrıca "oluşum bilgisine bağlantı sağlayan üreticilerin, sunucu uygulamasının, verisinin vb. hassas ayrıntılarını açığa çıkarabileceği için yığın dökümü gibi uygulama ayrıntılarını HTTP arayüzü üzerinden erişilebilir kılmaktan kaçınmaları teşvik edilir." [RFC 9457 §5](https://www.rfc-editor.org/rfc/rfc9457.html)

**Korelasyon/iz kimliği için standart bir alan adı RFC 9457'de yoktur.** İki meşru yol vardır:

1. `instance` üyesini bu amaçla kullanmak. Spec'in kendi ifadesi bunu doğrudan destekler: `instance` "problemin belirli oluşumunu" tanımlar ve dereference edilemiyorsa opak bir tanımlayıcı olarak iş görür. Örneğin `"instance": "/errors/018f2a1c-6d3e-7c4b-9a11-0f2b5c7d8e90"`.
2. Bir uzantı üyesi eklemek (`"requestId": "..."` veya `"traceId": "..."`). Adlandırma kuralına uyar (≥3 karakter, ALPHA/DIGIT/`_`) ve tanımayan istemciler yok saymak zorundadır, dolayısıyla geriye dönük uyumludur.

İyi uygulama mıdır? Evet. İstemciye bir istek/iz kimliği göstermek RFC 9457'nin uyardığı sızıntı sınıfına girmez: değer opak, düşük entropili anlam taşımayan bir tanımlayıcıdır; yığın izi, SQL, sınıf adı veya iç yol açığa çıkarmaz. Aksine destek akışını mümkün kılar — kullanıcı ekrandaki kimliği bildirir, ekip logu tek sorguda bulur. `traceId` özelinde ek bir avantaj vardır: değer zaten `traceparent` başlığıyla ağ üzerinde açıkça dolaşmaktadır, dolayısıyla gizli sayılmaz.

Tek dikkat noktası: gösterilen kimlik **tahmin edilebilir bir sayaç olmamalıdır** (istek hacmi sızdırır) ve istemciden gelen ham değer doğrulanmadan yansıtılmamalıdır — Koala'nın mevcut regex doğrulaması bunu zaten karşılıyor.

---

## 7. Koala için somut öneri

### 7.1 Doldurulacak MDC alanları

| MDC anahtarı | ECS/JSON karşılığı | Nereden gelir | Zorunluluk | Not |
| --- | --- | --- | --- | --- |
| `traceId` | `trace.id` (rename ile) | Micrometer Tracing `Slf4JEventListener`; `traceparent` başlığından ya da yeni üretilir | Önerilen (yeni) | 32 hex; [W3C](https://www.w3.org/TR/trace-context/), [Micrometer](https://github.com/micrometer-metrics/tracing/blob/main/micrometer-tracing-bridges/micrometer-tracing-bridge-otel/src/main/java/io/micrometer/tracing/otel/bridge/Slf4JEventListener.java) |
| `spanId` | `span.id` (rename ile) | Aynı kaynak | Önerilen (yeni) | 16 hex |
| `requestId` | `requestId` | Kenar filtresi: `X-Request-Id` başlığı doğrulanır, yoksa UUIDv7/v4 üretilir | Zorunlu | Bugünkü `correlationId` alanının doğru adı |
| `enduser.id` veya `user.id` | `user.id` | Kimliği doğrulanmış principal'ın UUID'si | Önerilen | Asla e-posta/telefon değil ([OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)) |
| `http.request.method` | `http.request.method` | `RequestLoggingFilter` | Erişim logu | [OTel Required](https://opentelemetry.io/docs/specs/semconv/http/http-spans/) |
| `url.path` | `url.path` | `RequestLoggingFilter` | Erişim logu | OTel Required |
| `http.route` | `http.route` | `HandlerMapping.BEST_MATCHING_PATTERN` request attribute | Erişim logu | Düşük kardinalite; dashboard gruplaması için `url.path`'ten daha değerli |
| `http.response.status_code` | `http.response.status_code` | `RequestLoggingFilter` | Erişim logu | OTel Conditionally Required |
| `client.address` | `client.address` | `ClientIpResolver` (X-Forwarded-For arkasındaki asıl istemci) | Erişim logu | Kişisel veri — saklama süresi belirlenmeli |
| `user_agent.original` | `user_agent.original` | `User-Agent` başlığı | Erişim logu (opsiyonel) | OTel Recommended |
| `server.address` | `server.address` | Host başlığı / yapılandırma | Opsiyonel | OTel Recommended |

Uygulama notu: son yedi satır **her log satırında** MDC'de durmamalıdır. Bunlar tek bir erişim-logu olayına aittir; `RequestLoggingFilter` içinde SLF4J fluent API ile olay bazında eklenmelidir (`log.atInfo().addKeyValue("http.request.method", ...)...log(...)`). Spring Boot'un yapılandırılmış formatlayıcıları hem MDC hem `KeyValuePair` çiftlerini JSON'a yazar, dolayısıyla iki yol da aynı çıktıya varır ama MDC'yi şişirmemek doğru olandır. [Spring Boot — Logging](https://docs.spring.io/spring-boot/reference/features/logging.html), [`ElasticCommonSchemaStructuredLogFormatter`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/java/org/springframework/boot/logging/logback/ElasticCommonSchemaStructuredLogFormatter.java)

Yalnızca `requestId`, `traceId`, `spanId` ve `user.id` isteğin tüm ömrü boyunca MDC'de kalmalıdır.

### 7.2 Yapılacaklar sırası

1. `micrometer-tracing-bridge-otel` (ve dışa aktarım isteniyorsa OTLP exporter) bağımlılığını ekle. Spring Boot bu ikisi için otomatik yapılandırma sağlar ve `traceId`/`spanId` MDC'ye kendiliğinden yazılır. [Spring Boot — Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
2. `CorrelationIdFilter`'ı `RequestIdFilter`'a dönüştür: `X-Request-Id`'yi oku (geriye dönük uyum için `X-Correlation-Id`'yi de kabul et), aynı regex ile doğrula, MDC anahtarını `requestId` yap, yanıtta hem `X-Request-Id` hem — bir sürüm boyunca — `X-Correlation-Id` döndür.
3. `logback-spring.xml` içindeki elle yazılmış konsol desenini Spring Boot varsayılanına yaklaştır veya `%X{requestId:-}` yanına `${LOG_CORRELATION_PATTERN:-}` ekle; aksi hâlde `traceId`/`spanId` geliştirici konsolunda görünmez. [Spring Boot `defaults.xml`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/resources/org/springframework/boot/logging/logback/defaults.xml)
4. ECS alan adları isteniyorsa `logging.structured.json.rename.traceId=trace.id` ve `logging.structured.json.rename.spanId=span.id` ekle; çıktıyı bir testle doğrula (§4.3'teki uyarı).
5. `RequestLoggingFilter`'daki tek parça mesajı yapılandırılmış anahtar/değer çiftlerine çevir; `http.route`'u ekle; `url.query`'yi ya hiç yazma ya da maskeleyerek yaz. [OTel url attributes](https://opentelemetry.io/docs/specs/semconv/registry/attributes/url/)
6. RabbitMQ üreticilerinde AMQP `correlation-id`/`message-id` özelliklerini gerçekten korelasyon için kullan ve `traceparent`'ı mesaj başlığı olarak taşı. `correlationId` adı **burada** doğrudur. [OASIS AMQP 1.0](https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html)
7. Log saklama süresi, erişim kontrolü ve maskeleme kurallarını yazılı hâle getir (OWASP + KVKK Madde 12).

### 7.3 Mobil istemciler için kırıcı değişiklik uyarıları

| Değişiklik | Kırıcı mı | Gerekçe ve azaltma |
| --- | --- | --- |
| Yanıt zarfındaki `correlationId` alanının adının `requestId` olması | **Evet, sözleşme değişikliği** | `Result` sınıfı bu alanı her yanıtta döndürüyor ve `koala-mobile` tarafındaki `lib/product/network/api_response.dart` dosyası zarfı dokümante ederken alanı açıkça sayıyor. Alan bugün mobilde **ayrıştırılmıyor** (yalnız doc yorumunda geçiyor), dolayısıyla gerçek çalışma zamanı riski düşük; ama yayımlanmış bir sözleşmedir. Azaltma: önce **eklemeli** git — `requestId`'yi ekle, `correlationId`'yi bir sürüm boyunca aynı değerle döndürmeye devam et, mobil güncellendikten sonra kaldır |
| Yanıt başlığı `X-Correlation-Id` → `X-Request-Id` | Düşük risk | `koala-mobile` içinde bu başlığı okuyan veya gönderen kod yok. Yine de bir sürüm boyunca iki başlığı birden döndür |
| İstek başlığı olarak `X-Correlation-Id` kabulünün kaldırılması | Düşük risk | Mobil bu başlığı hiç göndermiyor; yine de kabul etmeye devam etmek maliyetsizdir |
| MDC anahtarının `correlationId` → `requestId` olması | Dahili, ama log tabanlı alarm/dashboard'ları kırar | `logback-spring.xml` deseni `%X{correlationId:-}` kullanıyor, `GlobalExceptionHandler` `MDC.get("correlationId")` okuyor. Üçü birlikte değişmeli; ECS JSON çıktısındaki alan adı da değiştiği için Kibana sorguları güncellenmeli |
| Hata gövdesine `traceId` eklenmesi | Kırıcı değil | RFC 9457 uzantı üyeleri için istemcilerin tanımadığı alanları yok sayması ZORUNLUDUR; Koala'nın zarfı da eklemeli alanlara toleranslıdır |
| `logging.structured.ecs.*` özellik adları | Değişmiyor | Spring Boot 4.1.1 aynı adları kullanıyor; Boot 3.4'ten gelen yapılandırma olduğu gibi geçerli |

### 7.4 Özet karar cümlesi

Koala tek bir HTTP isteği için `requestId` üretmeli, dağıtık izleme için `traceparent` kabul edip `traceId`/`spanId`'yi Micrometer Tracing'e ürettirmeli, `correlationId` adını yalnızca RabbitMQ tabanlı çok mesajlı akışlara saklamalı ve istemciye hata gövdesinde `requestId`'yi (isteğe bağlı olarak `traceId`'yi) opak bir destek anahtarı olarak göstermelidir. Alan adı değişimi eklemeli yapılmalı, `correlationId` en az bir mobil sürüm boyunca kaldırılmamalıdır.

---

## Kaynaklar

**Standartlar ve spesifikasyonlar**
- [W3C Trace Context, REC 23 Kasım 2021](https://www.w3.org/TR/trace-context/)
- [IANA HTTP Field Name Registry](https://www.iana.org/assignments/http-fields/http-fields.xhtml)
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html)
- [RFC 6648 (BCP 178) — Deprecating the "X-" Prefix](https://www.rfc-editor.org/rfc/rfc6648.html)
- [OASIS AMQP 1.0 Messaging, OASIS Standard 29 Ekim 2012](https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html)
- [Jakarta Messaging 3.1 — `Message` javadoc](https://jakarta.ee/specifications/messaging/3.1/apidocs/jakarta.messaging/jakarta/jms/message)
- [IETF httpapi WG](https://datatracker.ietf.org/wg/httpapi/)

**OpenTelemetry (semconv 1.44.0)**
- [Semantic Conventions ana sayfa](https://opentelemetry.io/docs/specs/semconv/)
- [HTTP spans](https://opentelemetry.io/docs/specs/semconv/http/http-spans/)
- [HTTP attribute registry](https://opentelemetry.io/docs/specs/semconv/registry/attributes/http/)
- [URL attribute registry](https://opentelemetry.io/docs/specs/semconv/registry/attributes/url/)
- [Client attribute registry](https://opentelemetry.io/docs/specs/semconv/registry/attributes/client/)
- [User-agent attribute registry](https://opentelemetry.io/docs/specs/semconv/registry/attributes/user-agent/)
- [Logs Data Model](https://opentelemetry.io/docs/specs/otel/logs/data-model/)
- [Trace Context in Legacy Formats](https://opentelemetry.io/docs/specs/otel/compatibility/logging_trace_context/)

**Spring / Micrometer / Logback / Elastic**
- [Spring Boot 4.1.1 — Logging (Structured Logging)](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [Spring Boot — Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [Spring Boot `ElasticCommonSchemaStructuredLogFormatter`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/java/org/springframework/boot/logging/logback/ElasticCommonSchemaStructuredLogFormatter.java)
- [Spring Boot `ContextPairs`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/java/org/springframework/boot/logging/structured/ContextPairs.java)
- [Spring Boot `StructuredLoggingJsonProperties`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/java/org/springframework/boot/logging/structured/StructuredLoggingJsonProperties.java)
- [Spring Boot `CorrelationIdFormatter`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/java/org/springframework/boot/logging/CorrelationIdFormatter.java)
- [Spring Boot Logback `defaults.xml`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot/src/main/resources/org/springframework/boot/logging/logback/defaults.xml)
- [Micrometer Tracing `Slf4JEventListener`](https://github.com/micrometer-metrics/tracing/blob/main/micrometer-tracing-bridges/micrometer-tracing-bridge-otel/src/main/java/io/micrometer/tracing/otel/bridge/Slf4JEventListener.java)
- [Spring Integration — Message headers](https://docs.spring.io/spring-integration/reference/message.html)
- [Logback — Encoders (`JsonEncoder`)](https://logback.qos.ch/manual/encoders.html)
- [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder)
- [Elastic Common Schema — Tracing fields](https://www.elastic.co/docs/reference/ecs/ecs-tracing)

**Güvenlik, gizlilik ve kalıplar**
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- [GDPR tam metni (EUR-Lex CELEX 32016R0679)](https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=CELEX:32016R0679)
- [KVKK — 6698 sayılı Kanun](https://www.kvkk.gov.tr/Icerik/6649/Personal-Data-Protection-Law)
- [EIP — Correlation Identifier](https://www.enterpriseintegrationpatterns.com/patterns/messaging/CorrelationIdentifier.html)
- [Envoy — HTTP connection manager headers (`x-request-id`)](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_conn_man/headers)
- [RabbitMQ — RPC tutorial (`correlation_id`)](https://www.rabbitmq.com/tutorials/tutorial-six-python)
