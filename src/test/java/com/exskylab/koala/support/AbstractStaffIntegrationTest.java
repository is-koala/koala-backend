package com.exskylab.koala.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractStaffIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void staffTokenProperties(DynamicPropertyRegistry registry) {
        registry.add("koala.staff.issuer-uri", StaffTokenIssuer::issuer);
        registry.add("koala.staff.jwk-set-uri", StaffTokenIssuer::jwkSetUri);
        registry.add("koala.staff.audience", StaffTokenIssuer::audience);
    }
}
