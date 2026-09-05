package com.exskylab.koala.core.security;

import com.exskylab.koala.business.abstracts.StaffAuthorizationService;
import com.exskylab.koala.entities.StaffMember;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
public class StaffJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    private final StaffAuthorizationService staffAuthorizationService;

    public StaffJwtAuthenticationConverter(StaffAuthorizationService staffAuthorizationService) {
        this.staffAuthorizationService = staffAuthorizationService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<String> tokenRoles = rolesOf(jwt);
        Optional<StaffMember> member = staffAuthorizationService.findActiveMember(jwt.getSubject());

        if (member.isEmpty()) {
            log.warn("Geçerli bir personel token'ı geldi ama staff_members kaydı yok: {}", jwt.getSubject());
            return new StaffAuthenticationToken(jwt, StaffPrincipal.unregistered(jwt.getSubject(), tokenRoles));
        }

        StaffMember staff = member.get();
        StaffAuthorization authorization = staffAuthorizationService.resolve(tokenRoles);

        return new StaffAuthenticationToken(jwt, new StaffPrincipal(
                staff.getId(),
                jwt.getSubject(),
                staff.getEmail(),
                displayNameOf(staff, jwt),
                authorization.roles(),
                authorization.permissions()));
    }

    private static String displayNameOf(StaffMember staff, Jwt jwt) {
        if (staff.getDisplayName() != null && !staff.getDisplayName().isBlank()) {
            return staff.getDisplayName();
        }
        String name = jwt.getClaimAsString("name");
        return name != null && !name.isBlank() ? name : jwt.getClaimAsString("preferred_username");
    }

    @SuppressWarnings("unchecked")
    private static Set<String> rolesOf(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null || !(realmAccess.get(ROLES_CLAIM) instanceof List<?> roles)) {
            return Set.of();
        }
        Set<String> named = new LinkedHashSet<>();
        roles.stream().filter(String.class::isInstance).map(String.class::cast).forEach(named::add);
        return named;
    }
}
