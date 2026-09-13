package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.MessagingProperties;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.MessagingAvailabilityReason;
import com.exskylab.koala.entities.PaymentStatus;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Component
public class MessagingAvailability {

    private static final Set<PaymentStatus> PAID =
            EnumSet.of(PaymentStatus.HELD_IN_ESCROW, PaymentStatus.RELEASED_TO_WORKER);
    private static final Set<AssignmentStatus> ENDED =
            EnumSet.of(AssignmentStatus.CANCELLED, AssignmentStatus.REJECTED);

    private final MessagingProperties properties;

    public MessagingAvailability(MessagingProperties properties) {
        this.properties = properties;
    }

    @Named("messagingAvailableReason")
    public MessagingAvailabilityReason reasonOf(JobAssignment assignment) {
        if (assignment == null) {
            return MessagingAvailabilityReason.PAYMENT_REQUIRED;
        }
        if (ENDED.contains(assignment.getStatus())
                || assignment.getPaymentStatus() == PaymentStatus.REFUNDED) {
            return MessagingAvailabilityReason.CLOSED;
        }
        if (!PAID.contains(assignment.getPaymentStatus())) {
            return MessagingAvailabilityReason.PAYMENT_REQUIRED;
        }
        if (isWindowClosed(assignment.getJob())) {
            return MessagingAvailabilityReason.CLOSED;
        }
        return MessagingAvailabilityReason.AVAILABLE;
    }

    @Named("messagingAvailable")
    public boolean isAvailable(JobAssignment assignment) {
        return reasonOf(assignment) == MessagingAvailabilityReason.AVAILABLE;
    }

    public boolean isWindowClosed(Job job) {
        if (job == null || job.getEndDateTime() == null) {
            return false;
        }
        return LocalDateTime.now(properties.zone())
                .isAfter(job.getEndDateTime().plusDays(properties.getGraceDaysAfterJob()));
    }
}
