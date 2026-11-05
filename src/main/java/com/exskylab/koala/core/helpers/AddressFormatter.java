package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.dtos.address.response.AddressComponentsDto;
import com.exskylab.koala.core.dtos.address.response.AddressDto;
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
        return compose(new Parts(address.getNeighborhood(), address.getThoroughfare(), address.getSiteName(),
                address.getBuildingName(), address.getBlock(), address.getBuildingNumber(), address.getFloor(),
                address.getUnitNumber(),
                address.getDistrict() == null ? null : address.getDistrict().getName(),
                address.getCity() == null ? null : address.getCity().getName(),
                address.getPostalCode()));
    }

    @Named("displayAddressOfDto")
    public static String displayAddress(AddressDto address) {
        if (address == null) {
            return null;
        }
        AddressComponentsDto components = address.getComponents() == null
                ? new AddressComponentsDto() : address.getComponents();
        return compose(new Parts(components.getNeighborhood(), components.getThoroughfare(),
                components.getSiteName(), components.getBuildingName(), components.getBlock(),
                components.getBuildingNumber(), components.getFloor(), components.getUnitNumber(),
                address.getDistrictName(), address.getCityName(), components.getPostalCode()));
    }

    private static String compose(Parts address) {
        List<String> parts = new ArrayList<>();
        add(parts, address.neighborhood());
        add(parts, address.thoroughfare());
        add(parts, address.siteName());
        add(parts, address.buildingName());
        addPrefixed(parts, "Blok ", address.block());
        addPrefixed(parts, "No: ", address.buildingNumber());
        addPrefixed(parts, "Kat: ", address.floor());
        addPrefixed(parts, "Daire: ", address.unitNumber());
        add(parts, districtAndCity(address.district(), address.city()));
        add(parts, address.postalCode());
        return String.join(", ", parts);
    }

    private static String districtAndCity(String district, String city) {
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

    private record Parts(String neighborhood, String thoroughfare, String siteName, String buildingName,
                         String block, String buildingNumber, String floor, String unitNumber,
                         String district, String city, String postalCode) {
    }
}
