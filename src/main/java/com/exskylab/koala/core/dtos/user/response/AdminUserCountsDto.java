package com.exskylab.koala.core.dtos.user.response;

public record AdminUserCountsDto(long applications, long assignmentsAsWorker, long assignmentsAsEmployer,
                                 long payments, long payouts, long disputes, long reportsReceived,
                                 long reportsMade) {
}
