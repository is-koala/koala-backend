package com.exskylab.koala.core.dtos.badge.request;

import com.exskylab.koala.core.constants.BadgeMessages;
import com.exskylab.koala.entities.BadgeFamily;
import com.exskylab.koala.entities.BadgeRuleType;
import com.exskylab.koala.entities.BadgeTier;
import jakarta.validation.constraints.*;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SaveBadgeDto {

    @NotBlank(message = BadgeMessages.CODE_REQUIRED)
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = BadgeMessages.CODE_FORMAT)
    private String code;

    @NotNull(message = BadgeMessages.FAMILY_REQUIRED)
    private BadgeFamily family;

    @NotNull(message = BadgeMessages.TIER_REQUIRED)
    private BadgeTier tier;

    @NotBlank(message = BadgeMessages.NAME_REQUIRED)
    @Size(max = 255)
    private String name;

    @Size(max = 512)
    private String description;

    @Min(value = 0, message = BadgeMessages.SORT_ORDER_RANGE)
    private int sortOrder;

    @NotNull(message = BadgeMessages.RULE_TYPE_REQUIRED)
    private BadgeRuleType ruleType;

    @Min(value = 1, message = BadgeMessages.MIN_COUNT_REQUIRED)
    private Integer minCount;

    @DecimalMin(value = "1.00", message = BadgeMessages.RATING_THRESHOLD_REQUIRED)
    @DecimalMax(value = "5.00", message = BadgeMessages.RATING_THRESHOLD_REQUIRED)
    private BigDecimal minAverageRating;

    @Min(value = 1, message = BadgeMessages.RATING_THRESHOLD_REQUIRED)
    private Integer minReviewCount;

    private Boolean active;

    @AssertTrue(message = BadgeMessages.MIN_COUNT_REQUIRED)
    public boolean isCountThresholdPresentWhenTheRuleNeedsIt() {
        return ruleType != BadgeRuleType.COMPLETED_ASSIGNMENTS || (minCount != null && minCount >= 1);
    }

    @AssertTrue(message = BadgeMessages.RATING_THRESHOLD_REQUIRED)
    public boolean isRatingThresholdCompleteWhenTheRuleNeedsIt() {
        return ruleType != BadgeRuleType.AVERAGE_RATING_WITH_MINIMUM_REVIEWS
                || (minAverageRating != null && minReviewCount != null && minReviewCount >= 1);
    }
}
