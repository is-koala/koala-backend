package com.exskylab.koala.core.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class StaffAuthenticationToken extends AbstractAuthenticationToken {

    private final transient String token;

    private final transient StaffPrincipal principal;

    public StaffAuthenticationToken(String token, StaffPrincipal principal) {
        super(principal.permissions().stream()
                .map(permission -> (GrantedAuthority)
                        new SimpleGrantedAuthority("PERM_" + permission.getCode()))
                .toList());
        this.token = token;
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public StaffPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.email();
    }
}
