package com.exskylab.koala.core.producers.notification;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PushTaskDtoTest {

    @Test
    void legacyQueuePayloadStillDeserializesWhileSensitiveSnapshotsAreIgnored() throws Exception {
        UUID id = UUID.randomUUID();
        String legacyJson = """
                {"id":"%s","pushToken":"secret-token","title":"old","body":"old","dataPayload":{"x":"y"}}
                """.formatted(id);

        PushTaskDto task = new ObjectMapper().readValue(legacyJson, PushTaskDto.class);

        assertEquals(id, task.id());
    }
}
