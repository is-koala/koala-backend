package com.exskylab.koala.core.realtime;

import com.exskylab.koala.core.security.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

public class RealtimeAuthentication extends UsernamePasswordAuthenticationToken {

    public RealtimeAuthentication(UserPrincipal principal) {
        super(principal, null, principal.getAuthorities());
    }

    @Override
    public String getName() {
        return ((UserPrincipal) getPrincipal()).id().toString();
    }
}
