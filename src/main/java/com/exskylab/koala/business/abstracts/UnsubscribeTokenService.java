package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.UnsubscribeToken;
import com.exskylab.koala.entities.User;

public interface UnsubscribeTokenService {

    String issueLink(User recipient, NotificationCategory category, NotificationChannel channel);

    UnsubscribeToken consume(String rawToken);
}
