package com.exskylab.koala.business.abstracts;

import java.util.List;
import java.util.UUID;

public interface NotificationDispatchService {

    List<UUID> findDueTaskIds();

    boolean runTask(UUID notificationId);
}
