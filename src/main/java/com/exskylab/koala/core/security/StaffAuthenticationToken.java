package com.exskylab.koala.core.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.stream.Stream;

public class StaffAuthenticationToken extends AbstractAuthenticationToken {

    public static final String STAFF_AUTHORITY = "STAFF";

    private final transient String token;

    private final transient StaffPrincipal principal;

    public StaffAuthenticationToken(String token, StaffPrincipal principal) {
        super(Stream.concat(
                        Stream.of((GrantedAuthority) new SimpleGrantedAuthority(STAFF_AUTHORITY)),
                        principal.permissions().stream()
                                .map(permission -> (GrantedAuthority)
                                        new SimpleGrantedAuthority("PERM_" + permission.getCode())))
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
