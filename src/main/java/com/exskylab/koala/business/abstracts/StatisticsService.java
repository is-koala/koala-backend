package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.statistics.response.EmployerStatsDto;

import java.util.UUID;

public interface StatisticsService {

    EmployerStatsDto getEmployerStats(UUID userId);
}
