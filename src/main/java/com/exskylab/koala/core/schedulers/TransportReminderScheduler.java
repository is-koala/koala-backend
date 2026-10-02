package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.TransportService;
import com.exskylab.koala.core.properties.TransportReminderProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;

@Component
@Slf4j
public class TransportReminderScheduler {

    private final TransportService transportService;
    private final TransportReminderProperties properties;
    private final Clock clock;

    public TransportReminderScheduler(TransportService transportService, TransportReminderProperties properties,
                                      Clock clock) {
        this.transportService = transportService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${transport.reminders.cron:0 */5 * * * *}")
    public void sendDueReminders() {
        if (!properties.isEnabled()) {
            return;
        }
        sendDueReminders(clock.instant());
    }

    public int sendDueReminders(Instant now) {
        int sent = 0;
        sent += send("evening", transportService.findEveningReminderDueIds(now), now,
                transportService::sendEveningReminder);
        sent += send("departure", transportService.findDepartureReminderDueIds(now), now,
                transportService::sendDepartureReminder);
        sent += send("boarding", transportService.findBoardingReminderDueIds(now), now,
                transportService::sendBoardingReminder);
        sent += send("not-boarded", transportService.findNotBoardedNoticeDueIds(now), now,
                transportService::sendNotBoardedNotice);
        return sent;
    }

    private int send(String kind, List<UUID> due, Instant now, BiPredicate<UUID, Instant> sender) {
        if (due.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (UUID seatId : due) {
            try {
                if (sender.test(seatId, now)) {
                    sent++;
                }
            } catch (Exception exception) {
                log.error("Transport {} reminder failed for seat {}", kind, seatId, exception);
            }
        }
        log.info("Transport {} reminders: {} of {} candidates sent.", kind, sent, due.size());
        return sent;
    }
}
