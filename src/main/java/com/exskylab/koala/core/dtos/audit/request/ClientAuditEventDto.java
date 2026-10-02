package com.exskylab.koala.core.dtos.audit.request;

import com.exskylab.koala.core.constants.AuditMessages;
import com.exskylab.koala.core.helpers.PersonalDataPatterns;
import com.exskylab.koala.entities.AuditSubjectType;
import com.exskylab.koala.entities.ClientAuditAction;
import com.exskylab.koala.entities.StaffExportList;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientAuditEventDto {

    public static final int MAX_FILTERS_LENGTH = 1000;
    public static final int MAX_ROW_COUNT = 1000;

    @NotNull(message = AuditMessages.EVENT_ACTION_REQUIRED)
    @Schema(description = "İstemcinin bildirdiği eylem. Bugün yalnız EXPORT (CSV dışa aktarma) kabul edilir.")
    private ClientAuditAction action;

    @NotNull(message = AuditMessages.EVENT_LIST_REQUIRED)
    @Schema(description = "Dışa aktarılan personel listesi. Kayıt, çağıranın bu liste için taşıdığı izinle yazılır: "
            + "JOB_ASSIGNMENTS payment.read, PAYOUTS payout.reconcile, REPORTS content.moderate, "
            + "DISPUTES dispute.resolve.")
    private StaffExportList resourceType;

    @Size(max = MAX_FILTERS_LENGTH, message = AuditMessages.EVENT_FILTERS_TOO_LONG)
    @Schema(types = {"string", "null"},
            description = "Dışa aktarmada kullanılan süzgeçlerin özeti (ör. \"durum=CONFIRMED&from=2026-09-01\"), "
                    + "en çok 1000 karakter. Kişisel veri taşıyamaz: e-posta, telefon/TCKN gibi uzun sayı dizileri ve "
                    + "IBAN içeren değer 400 alır; arama metni gönderilecekse istemci onu çıkarmalıdır.",
            example = "durum=CONFIRMED&from=2026-09-01T00:00:00Z")
    private String filters;

    @NotNull(message = AuditMessages.EVENT_ROW_COUNT_REQUIRED)
    @Min(value = 0, message = AuditMessages.EVENT_ROW_COUNT_RANGE)
    @Max(value = MAX_ROW_COUNT, message = AuditMessages.EVENT_ROW_COUNT_RANGE)
    @Schema(description = "Dosyaya yazılan satır sayısı (0–1000; panel ilk 1000 satırı dışa aktarır).")
    private Integer rowCount;

    @NotNull(message = AuditMessages.EVENT_PII_FLAG_REQUIRED)
    @Schema(description = "Dosya kişisel veri sütunu (e-posta, telefon) taşıyorsa true. true ise kayıt en az "
            + "PERSONAL_DATA hassasiyetinde saklanır.")
    private Boolean piiIncluded;

    @Schema(types = {"string", "null"},
            description = "Dışa aktarma tek bir kullanıcının ya da şirketin listesiyse (ör. kullanıcı sayfasındaki "
                    + "atamalar) onun türü; subjectId ile birlikte verilir ve kayıt o konunun zaman çizelgesinde görünür.")
    private AuditSubjectType subjectType;

    @Schema(types = {"string", "null"}, format = "uuid",
            description = "Konu kullanıcının ya da şirketin kimliği; subjectType ile birlikte verilir.")
    private UUID subjectId;

    @JsonIgnore
    @AssertTrue(message = AuditMessages.EVENT_SUBJECT_INCOMPLETE)
    public boolean isSubjectComplete() {
        return (subjectType == null) == (subjectId == null);
    }

    @JsonIgnore
    @AssertTrue(message = AuditMessages.EVENT_FILTERS_PERSONAL_DATA)
    public boolean isFiltersFreeOfPersonalData() {
        return !PersonalDataPatterns.containsPersonalData(filters);
    }
}
