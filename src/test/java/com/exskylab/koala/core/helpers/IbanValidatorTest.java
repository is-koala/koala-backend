package com.exskylab.koala.core.helpers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IbanValidatorTest {

    @Test
    void acceptsValidTurkishIbans() {
        for (String iban : new String[]{
                "TR180006200119000006672315",
                "TR960001000200300040005006",
                "TR430006701000000012345678"}) {
            assertTrue(IbanValidator.isValid(iban), iban);
        }
    }

    @Test
    void ignoresWhitespaceGrouping() {
        assertTrue(IbanValidator.isValid("TR18 0006 2001 1900 0006 6723 15"));
        assertTrue(IbanValidator.isValid("  TR180006200119000006672315  "));
    }

    @Test
    void rejectsWrongCheckDigits() {
        assertFalse(IbanValidator.isValid("TR000006200119000006672315"));
        assertFalse(IbanValidator.isValid("TR190006200119000006672315"));
    }

    @Test
    void rejectsWrongLength() {
        assertFalse(IbanValidator.isValid("TR18000620011900000667231"));
        assertFalse(IbanValidator.isValid("TR1800062001190000066723155"));
        assertFalse(IbanValidator.isValid(""));
    }

    @Test
    void rejectsNullInsteadOfThrowing() {
        assertFalse(IbanValidator.isValid(null));
    }

    @Test
    void rejectsNonAlphanumericCharacters() {
        assertFalse(IbanValidator.isValid("TR18-0006200119000006672315"));
        assertFalse(IbanValidator.isValid("TR18_006200119000006672315"));
        assertFalse(IbanValidator.isValid("TR18*0006200119000006672"));
    }

    @Test
    void rejectsLowercaseCountryCode() {
        assertFalse(IbanValidator.isValid("tr180006200119000006672315"));
    }

    @Test
    void normalizeStripsWhitespaceAndToleratesNull() {
        assertEquals("TR180006200119000006672315",
                IbanValidator.normalize("TR18 0006 2001 1900 0006 6723 15"));
        assertNull(IbanValidator.normalize(null));
    }
}
