package com.exskylab.koala.core.dtos.job.response;

import com.exskylab.koala.core.dtos.jobCategory.response.JobCategoryDto;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.JobStatus;
import com.exskylab.koala.entities.JobType;
import com.exskylab.koala.entities.SalaryType;
import com.exskylab.koala.entities.AddressPrecision;
import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobSummaryDto {

    private UUID id;
    private String title;
    private String position;
    private String sector;
    private JobCategoryDto category;
    private JobEmployerType employerType;
    private JobType type;
    private SalaryType salaryType;
    private BigDecimal salary;
    @Schema(types = {"object", "null"},
            description = "Fiyat anlık görüntüsü. Görüntüleyen ilan sahibi ya da şirket yetkilisiyse tam döküm; "
                    + "herkese açık ve işçi görünümünde yalnız pricingVersion, currency ve workerPayout dolu, "
                    + "hizmet bedeli alanları ve employerTotal null. Snapshot'ı olmayan ilanda null.")
    private PricingBreakdownDto pricing;
    private Boolean insuranceRequired;
    private Boolean experienceRequired;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private JobStatus status;

    @Schema(types = {"string", "null"}, format = "date-time",
            description = "İlanın veri tabanında son değiştiği an; sitemap ve önbellek tazeliğinin kaynağıdır. Eski kayıtlarda null olabilir.",
            example = "2026-09-09T10:11:12Z")
    private Instant updatedAt;

    private String cityName;
    private String districtName;
    private String companyName;

    @Schema(types = {"string", "null"}, format = "uuid",
            description = "İlanı açan şirket. Bireysel ilanlarda null.")
    private UUID companyId;

    @Schema(types = {"string", "null"},
            description = "İlanı açan şirketin logosu. Bireysel ilanda ya da logo yüklenmemişse null.")
    private String companyLogoUrl;

    private String coverImageUrl;

    private long applicationCount;

    private Integer headcount;

    private long hiredCount;

    private Double latitude;

    private Double longitude;

    @Schema(description = "Harita koordinatının yaklaşık mı kesin mi olduğunu belirtir.")
    private AddressPrecision addressPrecision;

    @Schema(types = {"number", "null"}, format = "double",
            description = "Aramada lat/lng verildiğinde arayan ile ilanın herkese açık (yaklaşık) konumu arasındaki "
                    + "büyük daire mesafesi, km, 1 ondalık. lat/lng verilmediğinde ya da ilanın koordinatı yoksa null.",
            example = "3.4")
    private Double distanceKm;
}
