package com.exskylab.koala.core.utilities.iys;

import com.exskylab.koala.core.constants.IysMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.properties.IysProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class IysRecipientCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final IysProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public IysRecipientCipher(IysProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String recipient) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv);
            byte[] encrypted = cipher.doFinal(recipient.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return "v1:" + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new ExternalServiceException(IysMessages.RECIPIENT_CIPHER_UNAVAILABLE);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            if (ciphertext == null || !ciphertext.startsWith("v1:")) {
                throw new IllegalArgumentException();
            }
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring(3));
            if (payload.length <= IV_BYTES) {
                throw new IllegalArgumentException();
            }
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, IV_BYTES, payload.length);
            return new String(cipher(Cipher.DECRYPT_MODE, iv).doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new ExternalServiceException(IysMessages.RECIPIENT_CIPHER_UNAVAILABLE);
        }
    }

    private Cipher cipher(int mode, byte[] iv) throws GeneralSecurityException {
        byte[] key = Base64.getDecoder().decode(properties.getRecipientEncryptionKey());
        if (key.length != 32) {
            throw new IllegalArgumentException();
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        return cipher;
    }
}
