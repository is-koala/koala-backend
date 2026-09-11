package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.finance.response.FinanceSummaryDto;

import java.time.Instant;

public interface FinanceService {

    FinanceSummaryDto summarize(Instant fromInclusive, Instant toExclusive, String currency);
}
