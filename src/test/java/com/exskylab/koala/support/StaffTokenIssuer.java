package com.exskylab.koala.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class StaffTokenIssuer {

    private static final RSAKey KEY;
    private static final int PORT;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("koala-staff-test").generate();

            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            PORT = server.getAddress().getPort();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private StaffTokenIssuer() {
    }

    public static String issuer() {
        return "http://127.0.0.1:" + PORT + "/realms/koala-staff";
    }

    public static String jwkSetUri() {
        return "http://127.0.0.1:" + PORT + "/jwks";
    }

    public static String audience() {
        return "koala-api";
    }

    public static String tokenFor(String subject, Collection<String> roles) {
        return token(issuer(), audience(), subject, roles, Duration.ofMinutes(5));
    }

    public static String token(String issuer, String audience, String subject,
                               Collection<String> roles, Duration validity) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(subject)
                    .audience(List.of(audience))
                    .issueTime(Date.from(now.minusSeconds(30)))
                    .expirationTime(Date.from(now.plus(validity)))
                    .claim("realm_access", Map.of("roles", List.copyOf(roles)))
                    .claim("name", "Deneme Personeli")
                    .claim("preferred_username", subject)
                    .build();

            SignedJWT signed = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
            signed.sign(new RSASSASigner(KEY));
            return signed.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
