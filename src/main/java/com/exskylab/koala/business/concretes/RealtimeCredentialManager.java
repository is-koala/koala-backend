package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.RealtimeCredentialService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.core.security.RealtimeCredential;
import com.exskylab.koala.core.security.UserPrincipal;
import org.springframework.stereotype.Service;

@Service
public class RealtimeCredentialManager implements RealtimeCredentialService {

    private final SecurityService securityService;
    private final JwtService jwtService;

    public RealtimeCredentialManager(SecurityService securityService, JwtService jwtService) {
        this.securityService = securityService;
        this.jwtService = jwtService;
    }

    @Override
    public RealtimeCredential issue() {
        UserPrincipal principal = securityService.getAuthenticatedPrincipal();
        return jwtService.generateRealtimeCredential(principal.id(), principal.sessionId());
    }
}
