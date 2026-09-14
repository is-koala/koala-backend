package com.exskylab.koala.core.dtos.admin.request;

import com.exskylab.koala.core.helpers.CreatedAtRange;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreatedAtRangeDto {

    @Parameter(description = "Kaydın oluşturulma anı (createdAt) için alt sınır, dahil. Dilimli ISO-8601 an, örn. "
            + "2026-09-01T00:00:00Z veya 2026-09-01T00:00:00+03:00; dilimsiz değer 400 döner. Verilmezse sınır yok.",
            example = "2026-09-01T00:00:00Z")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant from;

    @Parameter(description = "Kaydın oluşturulma anı (createdAt) için üst sınır, dahil; from ile aynı biçim ve dilim "
            + "kuralı. Verilmezse sınır yok. Ters aralık doğrulanmaz, boş sayfa döner.",
            example = "2026-09-30T20:59:59.999Z")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant to;

    public CreatedAtRange toRange() {
        return CreatedAtRange.of(from, to);
    }
}
