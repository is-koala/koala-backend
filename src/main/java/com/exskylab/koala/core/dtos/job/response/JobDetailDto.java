package com.exskylab.koala.core.dtos.job.response;

import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.company.response.JobCompanyDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import com.exskylab.koala.core.dtos.jobCategory.response.JobCategoryDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
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
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobDetailDto {

    private UUID id;
    private String title;
    private String description;
    private String duties;
    private String sector;
    private String position;
    private String dressCode;
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
    private Integer minAge;
    private Integer maxAge;
    private Boolean insuranceRequired;
    private Boolean experienceRequired;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private JobStatus status;
    @Schema(description = "İlanın durum ve bitiş zamanına göre şu anda başvuru kabul edip etmediğini belirtir.")
    private boolean acceptingApplications;
    private AddressDto address;
    @Schema(description = "Adres ve koordinatın yaklaşık mı kesin mi olduğunu belirtir.")
    private AddressPrecision addressPrecision;
    private SafeUserDto creator;
    private JobCompanyDto company;
    private List<GalleryImageDto> images;
    private Instant createdAt;
    @Schema(types = {"string", "null"}, format = "date-time",
            description = "İlanın veri tabanında son değiştiği an. Eski kayıtlarda null olabilir.")
    private Instant updatedAt;
    @Schema(types = {"string", "null"}, format = "date-time",
            description = "İlanın iptal edildiği an; iptal edilmemişse ya da bu alandan önce iptal edildiyse null.")
    private Instant cancelledAt;
    private Integer headcount;
    @Schema(description = "İlana yapılmış toplam başvuru sayısı, durumdan bağımsız.")
    private long applicationCount;
    private long hiredCount;
}
