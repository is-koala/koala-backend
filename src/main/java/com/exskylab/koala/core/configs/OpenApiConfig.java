package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springdoc.core.customizers.OperationCustomizer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    private final AppProperties appProperties;

    public OpenApiConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public OpenAPI koalaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Koala API")
                        .version("v1")
                        .description("""
                                İşgücü pazaryeri API'si.

                                **Kimlik doğrulama.** `POST /auth/login` bir erişim token'ı ve yenileme token'ı döndürür.
                                Erişim token'ı `Authorization: Bearer <token>` başlığıyla gönderilir. Süresi dolduğunda
                                `POST /auth/refresh-token` çağrılır; bu uç süresi dolmuş bir erişim token'ıyla da çalışır.

                                **Cihaz kimliği.** Web BFF her istekte `X-Client-Platform: web` ve tarayıcı profiline
                                ait `[A-Za-z0-9._:-]{8,128}` biçimindeki sabit `X-Device-Id` değerini gönderir. Web
                                login ve refresh çağrılarında bu kimlik zorunludur; User-Agent yalnız okunabilir cihaz
                                etiketidir. Başlıksız eski mobil istemciler geçiş süresince User-Agent eşleşmesini kullanır.

                                **İstemci sürümü.** `X-App-Version`, `X-Client-Platform` için tanımlanan iOS, Android
                                veya web eşiğiyle karşılaştırılır. Web eşiği varsayılan olarak kapalıdır; başlıksız eski
                                mobil çağrılar `app.minimum-version` eşiğini kullanır. Eski istemci 426 ve
                                `X-Minimum-App-Version` alır.

                                **Web ve istemci IP'si.** Tarayıcı REST API'ye doğrudan bağlanmaz. BFF, gelen istemci
                                başlıklarını güven sınırı saymadan bağlantı IP'sini `X-Forwarded-For` olarak yeniden
                                yazar. Backend bu başlığı yalnız `app.trusted-proxies` içindeki doğrudan vekilden kabul eder.

                                **Dil.** `Accept-Language: tr` ya da `en`. Başlık yoksa Türkçe varsayılır; hem başarı
                                hem hata mesajları bu başlığa göre çözümlenir.

                                **Hata biçimi.** Tüm hatalar aynı zarfla döner. `errorCategory` kapalı bir kümedir ve
                                istemci mantığı buna bağlanmalıdır; `errorCode` daha spesifiktir ve log/hata ayıklama
                                içindir. Doğrulama hatalarında `details` alanı alan bazında mesaj taşır.

                                **Sayfalama.** Sayfalı uçlar `page`, `size` ve `sort` parametreleri alır. `size` en fazla
                                100'dür. `sort` yalnızca beyaz listedeki alanları kabul eder; listede olmayan bir alan
                                sessizce yok sayılmaz, 400 döner.
                                """)
                        .contact(new Contact().name("Koala").email("info@iskoala.com")))
                .servers(List.of(new Server().url(appProperties.apiUrl())))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("`POST /auth/login` yanıtındaki erişim token'ı.")));
    }

    @Bean
    public OperationCustomizer commonErrorResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();

            if (handlerMethod.hasMethodAnnotation(SecurityRequirements.class)) {
                operation.setSecurity(null);
            } else {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
                addIfAbsent(responses, "401", "Kimlik doğrulanmadı, token geçersiz ya da oturum kapatılmış");
                addIfAbsent(responses, "403", "Bu işlem için yetki yok");
            }

            addIfAbsent(responses, "500", "Beklenmeyen sunucu hatası; yanıtta yalnızca requestId taşınır");
            return operation;
        };
    }

    private static void addIfAbsent(ApiResponses responses, String code, String description) {
        if (responses.get(code) != null) {
            return;
        }

        responses.addApiResponse(code, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<ErrorResult>().$ref("#/components/schemas/ErrorResult")))));
    }
}
