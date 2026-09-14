package com.exskylab.koala.core.dtos.job.request;

import com.exskylab.koala.core.constants.AddressMessages;
import com.exskylab.koala.core.constants.JobMessages;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.JobType;
import com.exskylab.koala.entities.SalaryType;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobSearchCriteriaDto {

    private String query;
    private UUID companyId;
    private UUID categoryId;
    private UUID cityId;
    private UUID districtId;
    private JobType type;
    private SalaryType salaryType;
    private JobEmployerType employerType;
    private BigDecimal minSalary;
    private BigDecimal maxSalary;
    private Boolean insuranceRequired;
    private Boolean experienceRequired;

    private Double minLatitude;

    private Double maxLatitude;

    private Double minLongitude;

    private Double maxLongitude;

    @Parameter(description = "Başlangıç aralığının alt sınırı (dahil). Dilimli ISO-8601 an, örn. 2026-09-20T21:00:00Z "
            + "veya 2026-09-21T00:00:00+03:00; job-search.zone-id (Europe/Istanbul) duvar saatine çevrilip ilanın "
            + "startDateTime alanıyla karşılaştırılır. Dilimsiz değer 400 döner.",
            example = "2026-09-20T21:00:00Z")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant startFrom;

    @Parameter(description = "Başlangıç aralığının üst sınırı (dahil). startFrom ile aynı biçim ve dilim kuralı. "
            + "startFrom'dan önceyse 400 döner.",
            example = "2026-09-22T20:59:59Z")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant startTo;

    @Parameter(description = "Arayanın enlemi; lng ile birlikte verilir. Verildiğinde yanıtta distanceKm dolar ve "
            + "sort=distance,asc kullanılabilir. Mesafe ilanın herkese açık (yaklaşık) konumundan hesaplanır.",
            example = "41.015137")
    @DecimalMin(value = "-90.000000", message = AddressMessages.LATITUDE_RANGE)
    @DecimalMax(value = "90.000000", message = AddressMessages.LATITUDE_RANGE)
    @Digits(integer = 2, fraction = 6, message = AddressMessages.LATITUDE_PRECISION)
    private BigDecimal lat;

    @Parameter(description = "Arayanın boylamı; lat ile birlikte verilir.", example = "28.979530")
    @DecimalMin(value = "-180.000000", message = AddressMessages.LONGITUDE_RANGE)
    @DecimalMax(value = "180.000000", inclusive = false, message = AddressMessages.LONGITUDE_RANGE)
    @Digits(integer = 3, fraction = 6, message = AddressMessages.LONGITUDE_PRECISION)
    private BigDecimal lng;

    @AssertTrue(message = JobMessages.SEARCH_START_RANGE_INVALID)
    public boolean isStartRangeOrdered() {
        return startFrom == null || startTo == null || !startFrom.isAfter(startTo);
    }

    @AssertTrue(message = JobMessages.SEARCH_LOCATION_INCOMPLETE)
    public boolean isLocationComplete() {
        return (lat == null) == (lng == null);
    }

    public boolean hasLocation() {
        return lat != null && lng != null;
    }
}
