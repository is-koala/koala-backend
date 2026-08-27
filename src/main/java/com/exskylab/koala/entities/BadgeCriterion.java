package com.exskylab.koala.entities;

import java.util.Arrays;
import java.util.Optional;

public enum BadgeCriterion {

    GEZGIN_SILVER(BadgeFamily.GEZGIN, BadgeTier.SILVER, BadgeRuleType.COMPLETED_ASSIGNMENTS),
    GEZGIN_GOLD(BadgeFamily.GEZGIN, BadgeTier.GOLD, BadgeRuleType.COMPLETED_ASSIGNMENTS);

    private final BadgeFamily family;
    private final BadgeTier tier;
    private final BadgeRuleType ruleType;

    BadgeCriterion(BadgeFamily family, BadgeTier tier, BadgeRuleType ruleType) {
        this.family = family;
        this.tier = tier;
        this.ruleType = ruleType;
    }

    public BadgeFamily getFamily() {
        return family;
    }

    public BadgeTier getTier() {
        return tier;
    }

    public BadgeRuleType getRuleType() {
        return ruleType;
    }

    public static Optional<BadgeCriterion> byCode(String code) {
        return Arrays.stream(values()).filter(c -> c.name().equals(code)).findFirst();
    }
}
