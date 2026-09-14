package com.exskylab.koala.core.dtos.jobAssignment.response;

import com.exskylab.koala.core.dtos.cancellation.response.AssignmentCancellationDto;
import com.exskylab.koala.core.dtos.company.response.CompanySummaryDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.dtos.jobApplication.response.AssignmentApplicationDto;
import com.exskylab.koala.core.dtos.payout.response.AssignmentPayoutDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.MessagingAvailabilityReason;
import com.exskylab.koala.entities.PaymentStatus;
import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MyAssignmentDto(UUID id, JobSummaryDto job, SafeUserDto worker,
                              AssignmentStatus status, PaymentStatus paymentStatus,
                              Instant assignmentDateTime, Instant actualCompletionDateTime,
                              int workHours, BigDecimal workerPayout, BigDecimal totalCharged,
                              PricingBreakdownDto pricing,
                              boolean messagingAvailable,
                              MessagingAvailabilityReason messagingAvailableReason,
                              @Schema(description = "Atamayı doğuran başvuru ve zamanları; doğrudan atamada null.")
                              AssignmentApplicationDto application,
                              @Schema(description = "Atama kaydının oluşturulduğu an.")
                              Instant createdAt,
                              @Schema(types = {"string", "null"}, format = "date-time",
                                      description = "İşçinin atamayı kabul ettiği an; henüz cevaplanmadıysa, reddedildiyse ya da kayıt bu alandan önceyse null.")
                              Instant confirmedAt,
                              @Schema(types = {"string", "null"}, format = "date-time",
                                      description = "Beklenen tamamlanma anı; belirlenmediyse null.")
                              Instant expectedCompletionDateTime,
                              @Schema(types = {"string", "null"}, format = "date-time",
                                      description = "Ödemenin emanete alındığı an: ilk başarılı ödeme denemesinin tamamlanma zamanı; ödeme yoksa null.")
                              Instant paymentSecuredAt,
                              @Schema(description = "İlan sahibi kişi. Bireysel ilanda işveren olarak gösterilecek addır; şirket ilanında ilanı açan yetkilidir.")
                              SafeUserDto employer,
                              @Schema(description = "Şirket ilanının işvereni; bireysel ilanda null.")
                              CompanySummaryDto company,
                              @Schema(description = "İptal edilmişse iptal özeti; değilse null.")
                              AssignmentCancellationDto cancellation,
                              @Schema(description = "Hakediş kaydı; emanet çıkışa verilmediyse null.")
                              AssignmentPayoutDto payout) {
}
