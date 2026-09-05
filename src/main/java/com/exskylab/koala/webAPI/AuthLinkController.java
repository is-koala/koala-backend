package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.AuthLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
    @Operation(summary = "Kayıt işlemini Koala uygulamasında aç",
            description = "Tokenı tüketmeden, yapılandırılmış Koala uygulama scheme'ine geçiş sayfası gösterir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uygulamaya geçiş sayfası",
                    content = @Content(mediaType = MediaType.TEXT_HTML_VALUE)),
            @ApiResponse(responseCode = "400", description = "Token eksik, boş veya birden fazla",
                    content = @Content(mediaType = MediaType.TEXT_HTML_VALUE))
    })
    @GetMapping("/complete-registration")
    public String completeRegistration(@RequestParam MultiValueMap<String, String> parameters,
                                       Model model,
                                       HttpServletResponse response) {
        String token = validToken(parameters);
        if (token == null) {
            return invalidLanding(model, response);
        }
        return landing(
                "Kaydını tamamla",
                "E-posta adresin doğrulandıktan sonra hesabını Koala uygulamasında tamamlayabilirsin.",
                "Koala Staging'de kaydı tamamla",
                authLinkService.registrationAppLink(token),
                model,
                response);
    }

    @SecurityRequirements
    @Operation(summary = "Parola sıfırlamayı Koala uygulamasında aç",
            description = "Tokenı tüketmeden, yapılandırılmış Koala uygulama scheme'ine geçiş sayfası gösterir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uygulamaya geçiş sayfası",
                    content = @Content(mediaType = MediaType.TEXT_HTML_VALUE)),
            @ApiResponse(responseCode = "400", description = "Token eksik, boş veya birden fazla",
                    content = @Content(mediaType = MediaType.TEXT_HTML_VALUE))
    })
    @GetMapping("/reset-password")
    public String resetPassword(@RequestParam MultiValueMap<String, String> parameters,
                                Model model,
                                HttpServletResponse response) {
        String token = validToken(parameters);
        if (token == null) {
            return invalidLanding(model, response);
        }
        return landing(
                "Yeni parola belirle",
                "Parolanı güvenli biçimde değiştirmek için Koala uygulamasına devam et.",
                "Koala Staging'de aç",
                authLinkService.passwordResetAppLink(token),
                model,
                response);
    }

    private String landing(String title, String message, String buttonLabel, String appLink,
                           Model model, HttpServletResponse response) {
        secure(response);
        model.addAttribute("title", title);
        model.addAttribute("message", message);
        model.addAttribute("buttonLabel", buttonLabel);
        model.addAttribute("appLink", appLink);
        return "auth-link-landing";
    }

    private String invalidLanding(Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return landing(
                "Bağlantı geçersiz",
                "Bu bağlantı eksik veya hatalı. E-postadaki bağlantıyı yeniden açmayı deneyebilirsin.",
                "",
                null,
                model,
                response);
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
