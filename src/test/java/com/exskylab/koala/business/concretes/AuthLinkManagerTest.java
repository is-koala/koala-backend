package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.properties.AuthLinkProperties;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthLinkManagerTest {

    @Test
    void buildsEnvironmentControlledLandingAndAppLinks() {
        AuthLinkManager links = new AuthLinkManager(properties(
                "https://api.iskoala.com", "koala-staging"));

        assertEquals(
                "https://api.iskoala.com/auth-links/reset-password?token=reset-token",
                links.passwordResetLandingLink("reset-token"));
        assertEquals(
                "koala-staging://complete-registration?token=registration-token",
                links.registrationAppLink("registration-token"));
    }

    @Test
    void encodesTokenCharactersInsteadOfTreatingThemAsUriSyntax() {
        AuthLinkManager links = new AuthLinkManager(properties(
                "https://api.iskoala.com", "koala-staging"));

        String link = links.passwordResetAppLink("\"><script>alert(1)</script>");

        assertFalse(link.contains("<script>"));
        assertTrue(link.contains("%3Cscript%3E"));
    }

    @Test
    void publicBaseUrlRejectsNonHttpsRemoteOriginsAndEmbeddedUrlParts() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();

            assertFalse(validator.validate(properties("http://example.com", "koala-staging")).isEmpty());
            assertFalse(validator.validate(properties(
                    "https://api.iskoala.com/path?redirect=evil", "koala-staging")).isEmpty());
            assertTrue(validator.validate(properties("http://localhost:8080", "koala-dev")).isEmpty());
            assertTrue(validator.validate(properties("https://api.iskoala.com", "koala")).isEmpty());
        }
    }

    private AuthLinkProperties properties(String publicBaseUrl, String appScheme) {
        AuthLinkProperties properties = new AuthLinkProperties();
        properties.setPublicBaseUrl(publicBaseUrl);
        properties.setAppScheme(appScheme);
        return properties;
    }
}
