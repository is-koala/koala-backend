package com.exskylab.koala.core.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class StaffAuthenticationToken extends AbstractAuthenticationToken {

    private final transient Jwt token;

    private final transient StaffPrincipal principal;

    public StaffAuthenticationToken(Jwt token, StaffPrincipal principal) {
        super(principal.permissions().stream()
                .map(permission -> (org.springframework.security.core.GrantedAuthority)
                        new SimpleGrantedAuthority("PERM_" + permission.getCode()))
                .toList());
        this.token = token;
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token.getTokenValue();
    }

    @Override
    public StaffPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.subject();
    }
}
