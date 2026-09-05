package com.exskylab.koala.core.helpers;

import com.exskylab.koala.entities.Address;
import org.mapstruct.Named;

import java.util.ArrayList;
import java.util.List;

public class AddressFormatter {

    @Named("displayAddress")
    public static String displayAddress(Address address) {
        if (address == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        add(parts, address.getNeighborhood());
        add(parts, address.getThoroughfare());
        add(parts, address.getSiteName());
        add(parts, address.getBuildingName());
        addPrefixed(parts, "Blok ", address.getBlock());
        addPrefixed(parts, "No: ", address.getBuildingNumber());
        addPrefixed(parts, "Kat: ", address.getFloor());
        addPrefixed(parts, "Daire: ", address.getUnitNumber());
        add(parts, districtAndCity(address));
        add(parts, address.getPostalCode());
        return String.join(", ", parts);
    }

    private static String districtAndCity(Address address) {
        String district = address.getDistrict() == null ? null : address.getDistrict().getName();
        String city = address.getCity() == null ? null : address.getCity().getName();
        if (district == null) {
            return city;
        }
        return city == null ? district : district + "/" + city;
    }

    private static void addPrefixed(List<String> parts, String prefix, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(prefix + value);
        }
    }

    private static void add(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }
}
