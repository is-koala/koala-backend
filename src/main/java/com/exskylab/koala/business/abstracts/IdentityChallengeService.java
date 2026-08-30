package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.User;

public interface IdentityChallengeService {

    String issue(User user);

    void consume(User user, String challenge, byte[] dataGroup15, byte[] signature);
}
