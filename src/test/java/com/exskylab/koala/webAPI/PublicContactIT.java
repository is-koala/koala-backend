package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PublicContactIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void anAnonymousVisitorCanSubmitTheSupportForm() throws Exception {
        mockMvc.perform(post("/public/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic":"destek","name":"Ada Yılmaz","email":"ada@example.test",\
                                "message":"Hesap silme talebim görünmüyor.","consent":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<SendEmailDto> captor = ArgumentCaptor.forClass(SendEmailDto.class);
        verify(notificationService).sendEmail(captor.capture(), any(), eq(true));
        assertEquals("info@iskoala.com", captor.getValue().getDestinationEmail());
        assertEquals("ada@example.test", captor.getValue().getTemplateParameters().get("replyTo"));
    }

    @Test
    void aFilledHoneypotLooksLikeSuccessAndDoesNotQueueMail() throws Exception {
        mockMvc.perform(post("/public/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic":"sikayet","name":"Bot","email":"bot@example.test",\
                                "message":"spam","consent":true,"website":"http://spam.test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService, never()).sendEmail(any(), any(), eq(true));
    }

    @Test
    void missingConsentIsAFieldErrorNotAServerError() throws Exception {
        mockMvc.perform(post("/public/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic":"diger","name":"Ada","email":"ada@example.test",\
                                "message":"Merhaba","consent":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("consent"));
    }
}
