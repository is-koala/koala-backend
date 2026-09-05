package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.AuthLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/auth-links")
@Tag(name = "Uygulama bağlantıları", description = "Kayıt ve parola e-postalarını Koala uygulamasına taşır.")
public class AuthLinkController {

    private static final int MAX_TOKEN_LENGTH = 2048;

    private final AuthLinkService authLinkService;

    public AuthLinkController(AuthLinkService authLinkService) {
        this.authLinkService = authLinkService;
    }

    @SecurityRequirements
    @Operation(summary = "Eski kayıt bağlantısını web'e yönlendir",
            description = "Tokenı tüketmeden Koala web kayıt doğrulama sayfasına kalıcı yönlendirme yapar.")
    @ApiResponses({
            @ApiResponse(responseCode = "301", description = "Web kayıt doğrulama sayfası"),
            @ApiResponse(responseCode = "400", description = "Token eksik, boş veya birden fazla")
    })
    @GetMapping("/complete-registration")
    public ResponseEntity<Void> completeRegistration(@RequestParam MultiValueMap<String, String> parameters,
                                                     HttpServletResponse response) {
        String token = validToken(parameters);
        if (token == null) {
            return invalid(response);
        }
        return redirect(authLinkService.registrationLandingLink(token), response);
    }

    @SecurityRequirements
    @Operation(summary = "Eski parola bağlantısını web'e yönlendir",
            description = "Tokenı tüketmeden Koala web parola sıfırlama sayfasına kalıcı yönlendirme yapar.")
    @ApiResponses({
            @ApiResponse(responseCode = "301", description = "Web parola sıfırlama sayfası"),
            @ApiResponse(responseCode = "400", description = "Token eksik, boş veya birden fazla")
    })
    @GetMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestParam MultiValueMap<String, String> parameters,
                                              HttpServletResponse response) {
        String token = validToken(parameters);
        if (token == null) {
            return invalid(response);
        }
        return redirect(authLinkService.passwordResetLandingLink(token), response);
    }

    private ResponseEntity<Void> redirect(String location, HttpServletResponse response) {
        secure(response);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    private ResponseEntity<Void> invalid(HttpServletResponse response) {
        secure(response);
        return ResponseEntity.badRequest().build();
    }

    private String validToken(MultiValueMap<String, String> parameters) {
        List<String> tokens = parameters.get("token");
        if (parameters.size() != 1 || tokens == null || tokens.size() != 1
                || !StringUtils.hasText(tokens.getFirst()) || tokens.getFirst().length() > MAX_TOKEN_LENGTH) {
            return null;
        }
        return tokens.getFirst();
    }

    private void secure(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
        response.setHeader("Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; "
                        + "frame-ancestors 'none'; form-action 'none'");
    }
}
