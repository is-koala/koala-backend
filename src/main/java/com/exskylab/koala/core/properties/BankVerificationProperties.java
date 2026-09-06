package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "bank.verification")
public class BankVerificationProperties {

    private String collectionIban = "";

    private int validityHours = 72;

    private int maxAttempts = 5;

    private BigDecimal minimumAmount = new BigDecimal("1.01");

    private BigDecimal maximumAmount = new BigDecimal("1.99");

    public String getCollectionIban() {
        return collectionIban;
    }

    public void setCollectionIban(String collectionIban) {
        this.collectionIban = collectionIban;
    }

    public int getValidityHours() {
        return validityHours;
    }

    public void setValidityHours(int validityHours) {
        this.validityHours = validityHours;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public BigDecimal getMinimumAmount() {
        return minimumAmount;
    }

    public void setMinimumAmount(BigDecimal minimumAmount) {
        this.minimumAmount = minimumAmount;
    }

    public BigDecimal getMaximumAmount() {
        return maximumAmount;
    }

    public void setMaximumAmount(BigDecimal maximumAmount) {
        this.maximumAmount = maximumAmount;
    }
}
