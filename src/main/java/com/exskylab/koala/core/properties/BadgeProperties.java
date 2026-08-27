package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "badge")
public class BadgeProperties {

    private Map<String, Threshold> thresholds = new HashMap<>();

    public Map<String, Threshold> getThresholds() {
        return thresholds;
    }

    public void setThresholds(Map<String, Threshold> thresholds) {
        this.thresholds = thresholds;
    }

    public Threshold forCode(String code) {
        return thresholds.getOrDefault(code, new Threshold());
    }

    public static class Threshold {

        private Integer minCount;

        private BigDecimal minAverageRating;

        private Integer minReviewCount;

        public Integer getMinCount() {
            return minCount;
        }

        public void setMinCount(Integer minCount) {
            this.minCount = minCount;
        }

        public BigDecimal getMinAverageRating() {
            return minAverageRating;
        }

        public void setMinAverageRating(BigDecimal minAverageRating) {
            this.minAverageRating = minAverageRating;
        }

        public Integer getMinReviewCount() {
            return minReviewCount;
        }

        public void setMinReviewCount(Integer minReviewCount) {
            this.minReviewCount = minReviewCount;
        }
    }
}
