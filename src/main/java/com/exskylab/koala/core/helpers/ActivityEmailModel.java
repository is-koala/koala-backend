package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.constants.ActivityMessages;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.NotificationTargetType;
import com.exskylab.koala.entities.User;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ActivityEmailModel {

    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private static final Locale TR = Locale.of("tr", "TR");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", TR);

    private static final Set<String> KNOWN_BODIES = Set.of(
            ActivityMessages.APPLICATION_ACCEPTED,
            ActivityMessages.APPLICATION_REJECTED,
            ActivityMessages.APPLICATION_WITHDRAWN,
            ActivityMessages.ASSIGNMENT_OFFERED,
            ActivityMessages.ASSIGNMENT_ACCEPTED,
            ActivityMessages.ASSIGNMENT_REJECTED,
            ActivityMessages.WORK_COMPLETED,
            ActivityMessages.PAYOUT_RELEASED,
            ActivityMessages.DISPUTE_OPENED,
            ActivityMessages.DISPUTE_RESOLVED,
            ActivityMessages.ASSIGNMENT_CANCELLED,
            ActivityMessages.ASSIGNMENT_EXPIRED_WORKER,
            ActivityMessages.ASSIGNMENT_EXPIRED_EMPLOYER,
            ActivityMessages.REVIEW_RECEIVED,
            ActivityMessages.IDENTITY_VERIFIED,
            ActivityMessages.IDENTITY_REVEALED,
            ActivityMessages.COMPANY_INVITATION_RECEIVED,
            ActivityMessages.COMPANY_APPROVED,
            ActivityMessages.COMPANY_REJECTED,
            ActivityMessages.MESSAGE_RECEIVED);

    private ActivityEmailModel() {
    }

    public static String formatWhen(LocalDateTime wallClock, Instant instant) {
        if (wallClock != null) {
            return wallClock.format(WHEN);
        }
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZONE).format(WHEN);
    }

    public static String formatWhere(Address address) {
        if (address == null) {
            return null;
        }
        String district = address.getDistrict() == null ? null : blankToNull(address.getDistrict().getName());
        String city = address.getCity() == null ? null : blankToNull(address.getCity().getName());
        if (district == null) {
            return city;
        }
        if (city == null) {
            return district;
        }
        return district + ", " + city;
    }

    public static String formatWho(User user) {
        return user == null ? null : DisplayNames.of(user.getFirstName(), user.getLastName());
    }

    public static String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        NumberFormat format = NumberFormat.getNumberInstance(TR);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        String number = format.format(amount);
        if (currency == null || currency.isBlank()) {
            return number;
        }
        String code = currency.toUpperCase(Locale.ROOT);
        if ("TRY".equals(code) || "TL".equals(code)) {
            return number + " ₺";
        }
        return number + " " + code;
    }

    public static BigDecimal amountOf(JobAssignment assignment) {
        if (assignment == null) {
            return null;
        }
        if (assignment.getWorkerPayout() != null) {
            return assignment.getWorkerPayout();
        }
        Job job = assignment.getJob();
        if (job == null) {
            return null;
        }
        if (job.getWorkerPayout() != null) {
            return job.getWorkerPayout();
        }
        return job.getSalary();
    }

    public static String currencyOf(JobAssignment assignment) {
        if (assignment == null) {
            return null;
        }
        if (assignment.getCurrency() != null && !assignment.getCurrency().isBlank()) {
            return assignment.getCurrency();
        }
        Job job = assignment.getJob();
        return job == null ? null : job.getCurrency();
    }

    public static String actionUrl(String webBaseUrl, NotificationTargetType type, UUID targetId,
                                   UUID jobId, boolean workerView) {
        String base = trimSlash(webBaseUrl);
        String path = pathOf(type, targetId, jobId, workerView);
        if (base.isEmpty()) {
            return path;
        }
        return base + path;
    }

    public static String body(String bodyKey, String resolved, String fallback) {
        if (bodyKey != null && KNOWN_BODIES.contains(bodyKey)) {
            return resolved;
        }
        return fallback;
    }

    public static Map<String, Object> parameters(User recipient, String jobTitle, String message,
                                                 String actionUrl, String when, String where,
                                                 String who, String amount) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("firstName", recipient == null ? null : recipient.getFirstName());
        parameters.put("message", message);
        parameters.put("jobTitle", jobTitle);
        parameters.put("actionUrl", actionUrl);
        parameters.put("when", when);
        parameters.put("where", where);
        parameters.put("who", who);
        parameters.put("amount", amount);
        return parameters;
    }

    private static String pathOf(NotificationTargetType type, UUID targetId, UUID jobId, boolean workerView) {
        if (type == null) {
            return "/panel/bildirimler";
        }
        return switch (type) {
            case JOB_ASSIGNMENT -> {
                if (targetId == null) {
                    yield "/panel/bildirimler";
                }
                yield workerView ? "/panel/is/" + targetId : "/isveren/atama/" + targetId;
            }
            case JOB_APPLICATION -> {
                if (workerView) {
                    yield "/panel/basvurularim";
                }
                yield jobId == null ? "/panel/bildirimler" : "/isveren/ilan/" + jobId + "/basvurular";
            }
            case JOB -> targetId == null ? "/is-ilanlari" : "/is-ilanlari/" + targetId;
            case COMPANY -> targetId == null ? "/panel/bildirimler" : "/isveren/sirket/" + targetId;
            case USER -> "/panel/profil";
            case CONVERSATION, COMPANY_CONTACT_INVITATION -> "/panel/bildirimler";
        };
    }

    private static String trimSlash(String base) {
        if (base == null) {
            return "";
        }
        String trimmed = base.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
