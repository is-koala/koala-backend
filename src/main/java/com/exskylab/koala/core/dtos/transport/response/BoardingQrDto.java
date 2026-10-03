package com.exskylab.koala.core.dtos.transport.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BoardingQrDto {

    @Schema(description = "HMAC biniş token'ı (servis: s1., işçi: w1.); okutma ucuna token alanında gönderilir.")
    private String token;

    @JsonProperty("static")
    @Schema(name = "static", description = "true ise süresi dolmayan, kâğıda basılabilen sabit servis QR'ı; "
            + "okutulduğunda olay STATIC_QR bayrağı taşır ve ancak servisin QR anahtarı yenilenince geçersizleşir.")
    private boolean staticQr;

    @Schema(description = "Token'ın üretildiği sunucu anı. İstemci refreshAt/expiresAt'i kendi saatine değil bu ana "
            + "göre (cihaz saat farkını düşerek) planlar.")
    private Instant issuedAt;

    @Schema(types = {"string", "null"}, format = "date-time",
            description = "Token'ın sunucu saatiyle kabul edildiği son anın sonu (sonrası çevrimiçi okutmada 400 "
                    + "TRANSPORT_TOKEN_EXPIRED); sabit QR'da null.")
    private Instant expiresAt;

    @Schema(types = {"string", "null"}, format = "date-time",
            description = "Ekrandaki QR'ın yenilenmesi gereken an: yeni pencere başlar, eski token expiresAt'e kadar "
                    + "kabul edilmeye devam eder; sabit QR'da null.")
    private Instant refreshAt;

    @Schema(description = "QR'a basılacak bağlantı: {şema}://board?t=<token>. Şema ortama göre koala-dev, "
            + "koala-staging ya da koala; token türünü ve kimliğini kendisi taşır.")
    private String uri;
}
