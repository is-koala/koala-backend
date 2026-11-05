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
                    + "en çok 1000 karakter. Kişisel veri taşıyamaz: e-posta adresi, 10 ya da daha fazla rakamlık dizi "
                    + "(telefon, TCKN; rakamlar arasında en çok iki boşluk/nokta/parantez/tire olabilir) ve TR IBAN "
                    + "içeren değer 400 VALIDATION_FAILED alır (details[].field = filtersFreeOfPersonalData). Değer "
                    + "eşleştirmeden önce yüzde kodlamasından ve '+' boşluğundan gevşekçe (en çok üç tur) çözülür, "
                    + "yani ali%40example.com ya da 0532+123+45+67 de reddedilir. UUID ve ISO tarih/zaman değerleri "
                    + "sayılmaz. Bilinen yanlış pozitif: kimlik/tarih olmayan 10+ rakamlık her sayı (ör. epoch "
                    + "milisaniye) reddedilir — tarihleri ISO biçiminde gönderin. Arama metni gönderilecekse istemci "
                    + "onu çıkarmalıdır.",
            example = "durum=CONFIRMED&from=2026-09-01T00:00:00Z")
    private String filters;

    @NotNull(message = AuditMessages.EVENT_ROW_COUNT_REQUIRED)
    @Min(value = 0, message = AuditMessages.EVENT_ROW_COUNT_RANGE)
    @Max(value = MAX_ROW_COUNT, message = AuditMessages.EVENT_ROW_COUNT_RANGE)
    @Schema(description = "Dosyaya yazılan satır sayısı (0–1000; panel ilk 1000 satırı dışa aktarır). Denetim "
            + "satırının çekirdeğinde (rowCount) tutulur.")
    private Integer rowCount;

    @NotNull(message = AuditMessages.EVENT_PII_FLAG_REQUIRED)
    @Schema(description = "Dosyanın içeriğiyle ilgili beyan, çağıranın yetkisiyle değil: dosya kişiler hakkında "
            + "kişisel veri sütunu (ad soyad, e-posta, telefon, TCKN, IBAN, adres, doğum tarihi gibi) ya da kişiler "
            + "hakkında serbest metin (şikayet/uyuşmazlık açıklaması, personel notu gibi) taşıyorsa true; yalnız "
            + "kimlik (UUID), durum, tutar, tarih ve sayı sütunları taşıyorsa false. Personelin user.read_pii "
            + "taşıyıp taşımaması bu alanı belirlemez. true ise kayıt en az PERSONAL_DATA hassasiyetinde saklanır; "
            + "değer denetim satırının çekirdeğinde (piiIncluded) tutulur.")
    private Boolean piiIncluded;

    @Schema(types = {"string", "null"},
            description = "Dışa aktarılan liste bir taraf süzgeciyle daraltıldıysa o tarafın türü; kayıt o konunun "
                    + "zaman çizelgesinde görünür. Kural: tek bir taraf süzgeci (ör. workerId, reportedUserId, "
                    + "userId) → USER konu; işçi ve işveren süzgeci birlikte → işçi konu, işveren related*. Süzgeçsiz "
                    + "liste konusuz gönderilir. COMPANY kabul edilir ama bugünkü dört liste sayfası onu kullanmaz. "
                    + "subjectId ile birlikte verilir.")
    private AuditSubjectType subjectType;

    @Schema(types = {"string", "null"}, format = "uuid",
            description = "Konu kullanıcının ya da şirketin kimliği; subjectType ile birlikte verilir. Kayıt "
                    + "(soft-delete edilmiş dahil) var olmalı, yoksa 400 AUDIT_EVENT_SUBJECT_NOT_FOUND.")
    private UUID subjectId;

    @Schema(types = {"string", "null"},
            description = "Karşı tarafın türü: işçi ve işveren süzgeci birlikte kullanıldıysa işveren (USER; "
                    + "şirket işvereni ise COMPANY). relatedSubjectId ile birlikte ve yalnız bir konuyla verilir.")
    private AuditSubjectType relatedSubjectType;

    @Schema(types = {"string", "null"}, format = "uuid",
            description = "Karşı tarafın kimliği; relatedSubjectType ile birlikte verilir. Kayıt var olmalı, yoksa "
                    + "400 AUDIT_EVENT_SUBJECT_NOT_FOUND.")
    private UUID relatedSubjectId;

    @JsonIgnore
    @AssertTrue(message = AuditMessages.EVENT_SUBJECT_INCOMPLETE)
    public boolean isSubjectComplete() {
        return (subjectType == null) == (subjectId == null);
    }

    @JsonIgnore
    @AssertTrue(message = AuditMessages.EVENT_RELATED_SUBJECT_INCOMPLETE)
    public boolean isRelatedSubjectComplete() {
        return (relatedSubjectType == null) == (relatedSubjectId == null);
    }

    @JsonIgnore
    @AssertTrue(message = AuditMessages.EVENT_RELATED_SUBJECT_WITHOUT_SUBJECT)
    public boolean isRelatedSubjectBesideASubject() {
        return relatedSubjectType == null && relatedSubjectId == null || subjectType != null && subjectId != null;
    }

    @JsonIgnore
    @AssertTrue(message = AuditMessages.EVENT_FILTERS_PERSONAL_DATA)
    public boolean isFiltersFreeOfPersonalData() {
        if (filters != null && filters.length() > MAX_FILTERS_LENGTH) {
            return true;
        }
        return !PersonalDataPatterns.containsPersonalData(filters);
    }
}
