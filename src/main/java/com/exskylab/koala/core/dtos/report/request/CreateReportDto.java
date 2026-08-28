package com.exskylab.koala.core.dtos.report.request;

import com.exskylab.koala.core.constants.ReportMessages;
import com.exskylab.koala.entities.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateReportDto {

    private UUID reportedUserId;

    private UUID jobId;

    private UUID messageId;

    private UUID imageId;

    @NotNull(message = ReportMessages.TARGET_REQUIRED)
    private ReportReason reason;

    @Size(max = 2000)
    private String description;

    @AssertTrue(message = ReportMessages.TARGET_REQUIRED)
    public boolean isPointingAtSomething() {
        return reportedUserId != null || jobId != null || messageId != null || imageId != null;
    }
}
