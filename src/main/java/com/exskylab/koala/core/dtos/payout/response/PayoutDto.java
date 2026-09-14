package com.exskylab.koala.core.dtos.payout.response;

import com.exskylab.koala.entities.PayoutStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "İşçiye çıkan bir hakediş. Durum sözlüğü: PENDING = çıkışa alındı, sağlayıcı onayı bekleniyor; "
        + "PROCESSING = sağlayıcı onayladı, banka transferi yolda; COMPLETED = banka ekstresiyle doğrulandı; "
        + "FAILED = çıkış başarısız, failureReason açıklar.")
public class PayoutDto {
    private UUID id;
    private UUID jobAssignmentId;
    @Schema(description = "Hakedişin ait olduğu ilanın başlığı.")
    private String jobTitle;
    @Schema(types = {"string", "null"},
            description = "İlanı açan şirketin adı; bireysel ilanda null.")
    private String companyName;
    @Schema(types = {"string", "null"},
            description = "Hakedişin gittiği banka hesabının maskeli IBAN'ı (ilk 4 + *** + son 4, örn. TR18***2315); "
                    + "çıkış anındaki birincil hesaptır, sonradan değişmez. Tam IBAN hiçbir yanıtta dönmez. "
                    + "Hesap kaydı yok ya da hesap sonradan silinmişse null.")
    private String maskedIban;
    @Schema(types = {"string", "null"}, format = "uuid",
            description = "Hakedişin gittiği banka hesabının kimliği (GET /users/me/bank-accounts ile eşleşir); "
                    + "hesap kaydı yok ya da hesap sonradan silinmişse null.")
    private UUID bankAccountId;
    @Schema(types = {"string", "null"},
            description = "Sağlayıcının (Iyzico) çıkış talimatını onaylarken verdiği işlem referansı; "
                    + "PENDING durumunda ya da sağlayıcı referans dönmediyse null.")
    private String providerReference;
    private String amount;
    private String currency;
    @Schema(description = "PENDING = çıkışa alındı, sağlayıcı onayı bekleniyor; PROCESSING = sağlayıcı onayladı, "
            + "banka transferi yolda; COMPLETED = banka ekstresiyle doğrulandı; FAILED = çıkış başarısız.")
    private PayoutStatus status;
    private String failureReason;
    @Schema(description = "Hakedişin emanetten çıkışa alındığı an (createdAt ile aynı değer).")
    private Instant releasedAt;
    @Schema(types = {"string", "null"}, format = "date-time",
            description = "Banka ekstresiyle doğrulanan gerçekleşme anı (completedAt ile aynı değer); "
                    + "yalnız COMPLETED durumunda dolu, öncesinde null.")
    private Instant settledAt;
    private Instant completedAt;
    private Instant createdAt;
}
