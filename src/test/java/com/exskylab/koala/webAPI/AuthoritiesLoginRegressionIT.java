package com.exskylab.koala.webAPI;

import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class AuthoritiesLoginRegressionIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "GecerliParola1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aPlainUserStillLogsInAndIsStillRefusedByTheAdminSurface() throws Exception {
        String email = "authorities-plain@koala.test";
        saveUser(email, Role.ROLE_USER);

        String token = login(email);

        mockMvc.perform(get("/admin/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminStillLogsInAndStillPassesTheRoleCheck() throws Exception {
        String email = "authorities-admin@koala.test";
        saveUser(email, Role.ROLE_USER, Role.ROLE_ADMIN);

        String token = login(email);

        mockMvc.perform(get("/admin/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void saveUser(String email, Role... roles) {
        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Yilmaz");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEmailVerified(true);
        user.setAuthorities(new HashSet<>(Set.copyOf(List.of(roles))));
        userDao.saveAndFlush(user);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .header("X-Device-Id", "authorities-regression-device")
                        .header("User-Agent", "Koala Authorities IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data")
                .get("token")
                .asText();
    }
}
