package com.exskylab.koala.core.dtos.messaging.request;

import com.exskylab.koala.core.constants.MessagingMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SendMessageDto {

    @NotBlank(message = MessagingMessages.BODY_NOT_BLANK)
    @Size(max = 4000)
    private String body;
}
