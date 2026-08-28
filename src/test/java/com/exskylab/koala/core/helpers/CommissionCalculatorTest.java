package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.properties.CommissionProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommissionCalculatorTest {

    private CommissionCalculator calculatorWithRate(String rate) {
        CommissionProperties properties = new CommissionProperties();
        properties.setRate(new BigDecimal(rate));
        return new CommissionCalculator(properties);
    }

    private final CommissionCalculator calculator = calculatorWithRate("0.075");

    @Test
    void theBreakdownAddsUpToTheChargedTotal() {
        var breakdown = calculator.calculate(new BigDecimal("1000.00"));

        assertEquals(new BigDecimal("1000.00"), breakdown.workerPayout());
        assertEquals(new BigDecimal("75.00"), breakdown.commission());
        assertEquals(new BigDecimal("1075.00"), breakdown.totalCharged());
        assertEquals(breakdown.workerPayout().add(breakdown.commission()), breakdown.totalCharged());
    }

    @Test
    void roundingNeverLosesOrInventsKurus() {
        for (String amount : new String[]{"0.01", "3.33", "99.99", "1234.56", "7777.77", "100000.05"}) {
            var breakdown = calculator.calculate(new BigDecimal(amount));

            assertEquals(2, breakdown.workerPayout().scale(), amount);
            assertEquals(2, breakdown.commission().scale(), amount);
            assertEquals(2, breakdown.totalCharged().scale(), amount);
            assertEquals(breakdown.workerPayout().add(breakdown.commission()), breakdown.totalCharged(),
                    "Toplam bilesenlerin toplamina esit degil: " + amount);
        }
    }

    @Test
    void halfKurusRoundsUpNotDown() {
        var breakdown = calculatorWithRate("0.10").calculate(new BigDecimal("0.05"));

        assertEquals(new BigDecimal("0.01"), breakdown.commission());
        assertEquals(new BigDecimal("0.06"), breakdown.totalCharged());
    }

    @Test
    void theAppliedRateIsReportedSoItCanBeStored() {
        assertEquals(new BigDecimal("0.075"), calculator.calculate(new BigDecimal("500")).rate());
        assertEquals(new BigDecimal("0.12"), calculatorWithRate("0.12").calculate(new BigDecimal("500")).rate());
    }

    @Test
    void aDifferentRateProducesADifferentCommission() {
        assertEquals(new BigDecimal("60.00"), calculatorWithRate("0.12").calculate(new BigDecimal("500")).commission());
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        assertThrows(ValidationException.class, () -> calculator.calculate(null));
        assertThrows(ValidationException.class, () -> calculator.calculate(BigDecimal.ZERO));
        assertThrows(ValidationException.class, () -> calculator.calculate(new BigDecimal("-1")));
    }

    @Test
    void floatingPointDriftIsGone() {
        var breakdown = calculator.calculate(new BigDecimal("0.10").add(new BigDecimal("0.20")));

        assertEquals(new BigDecimal("0.30"), breakdown.workerPayout());
        assertEquals(0, breakdown.workerPayout().compareTo(new BigDecimal("0.3")));
    }
}
