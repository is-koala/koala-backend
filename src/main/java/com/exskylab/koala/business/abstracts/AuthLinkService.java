package com.exskylab.koala.business.abstracts;

public interface AuthLinkService {

    String registrationLandingLink(String token);

    String passwordResetLandingLink(String token);

    String registrationAppLink(String token);

    String passwordResetAppLink(String token);
}
