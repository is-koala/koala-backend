package com.exskylab.koala.core.utilities.iys;

import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.properties.IysProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IysRecipientCipherTest {

    @Test
    void encryptsWithFreshAuthenticatedNoncesAndDecryptsTheRecipient() {
        IysProperties properties = new IysProperties();
        properties.setRecipientEncryptionKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        IysRecipientCipher cipher = new IysRecipientCipher(properties);

        String first = cipher.encrypt("withdrawal@koala.test");
        String second = cipher.encrypt("withdrawal@koala.test");

        assertNotEquals(first, second);
        assertEquals("withdrawal@koala.test", cipher.decrypt(first));
    }

    @Test
    void rejectsTamperedCiphertextWithoutExposingCryptoDetails() {
        IysProperties properties = new IysProperties();
        properties.setRecipientEncryptionKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        IysRecipientCipher cipher = new IysRecipientCipher(properties);
        String ciphertext = cipher.encrypt("withdrawal@koala.test");

        assertThrows(ExternalServiceException.class,
                () -> cipher.decrypt(ciphertext.substring(0, ciphertext.length() - 2) + "xx"));
    }
}
