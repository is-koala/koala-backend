package com.exskylab.koala.webAPI;

import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class LoginEnumerationIT extends AbstractIntegrationTest {

    private static final String REGISTERED = "sayim-kayitli@koala.test";
    private static final String UNREGISTERED = "sayim-kayitsiz@koala.test";
    private static final String PASSWORD = "KoalaGecerliParola1";
    private static final String WRONG_PASSWORD = "KoalaYanlisParola1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Yilmaz");
        user.setEmail(REGISTERED);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEmailVerified(true);
        user.setAuthorities(new HashSet<>(Set.of(Role.ROLE_USER)));
        userDao.saveAndFlush(user);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from login_attempts where email_attempted in (?, ?)", REGISTERED, UNREGISTERED);
        jdbc.update("delete from authorities where user_id in (select id from users where email = ?)", REGISTERED);
        jdbc.update("delete from users where email = ?", REGISTERED);
    }

    @Test
    void anUnregisteredEmailLooksExactlyLikeARegisteredOneWithAWrongPassword() throws Exception {
        assertEquals(envelope(attempt(REGISTERED, WRONG_PASSWORD)), envelope(attempt(UNREGISTERED, WRONG_PASSWORD)),
                "Kayıtsız e-posta ile yanlış parola ayırt edilebiliyor, giriş ucu kayıtlı adresleri sızdırıyor");
    }

    @Test
    void anUnregisteredEmailIsStillRecordedAsAFailedLoginAttempt() throws Exception {
        attempt(UNREGISTERED, WRONG_PASSWORD);

        assertEquals(1, jdbc.queryForObject(
                        "select count(*) from login_attempts where email_attempted = ? and is_success = false",
                        Integer.class, UNREGISTERED),
                "Kayıtsız e-postayla yapılan giriş denemesi hiç kaydedilmiyor");
    }

    private ResultActions attempt(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .header("X-Device-Id", "sayim-testi-cihazi")
                .header("User-Agent", "Koala Enumeration IT")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String envelope(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> parsed =
                new TreeMap<>(objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
                }));
        parsed.remove("timeStamp");
        parsed.remove("requestId");
        return parsed.toString();
    }
}
