package com.exskylab.koala.core.utilities.iys;

import com.exskylab.koala.entities.NotificationChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IysRecipientNormalizerTest {

    @Test
    void turkishMobileNumbersUseOneE164Representation() {
        assertEquals("+905551112233",
                IysRecipientNormalizer.normalize("0555 111 22 33", NotificationChannel.SMS));
        assertEquals("+905551112233",
                IysRecipientNormalizer.normalize("0090 (555) 111-22-33", NotificationChannel.SMS));
        assertEquals("+905551112233",
                IysRecipientNormalizer.normalize("905551112233", NotificationChannel.SMS));
    }

    @Test
    void aNonTurkishMobileNumberIsNotAnIysRecipient() {
        assertNull(IysRecipientNormalizer.normalize("555-12", NotificationChannel.SMS));
    }
}
