package com.exskylab.koala.core.dtos;

import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AddressWriteJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void legacyNameBasedAddressFieldsAreRejectedEvenWhenGlobalUnknownFieldsAreIgnored() {
        String legacyBody = """
                {
                  "countryCode": "TR",
                  "cityName": "İSTANBUL",
                  "districtName": "KADIKÖY",
                  "addressLine": "Moda Caddesi No: 12",
                  "latitude": 40.982512,
                  "longitude": 29.025411
                }
                """;

        assertThrows(Exception.class, () -> objectMapper.readValue(legacyBody, AddressWriteDto.class));
    }
}
