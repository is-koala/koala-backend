package com.exskylab.koala.core.dtos.report.request;

import com.exskylab.koala.core.constants.ReportMessages;
import com.exskylab.koala.entities.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReviewReportDto {

    @NotNull(message = ReportMessages.TARGET_REQUIRED)
    private ReportStatus status;

    @Size(max = 2000)
    private String note;
}
