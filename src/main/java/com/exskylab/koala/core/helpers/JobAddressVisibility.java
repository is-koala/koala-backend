package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.dtos.address.response.AddressComponentsDto;
import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.address.response.AddressPositionDto;
import com.exskylab.koala.core.dtos.job.response.JobDetailDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.entities.AddressPrecision;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobEmployerType;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JobAddressVisibility {

    public void protect(Job job, JobDetailDto detail, UUID viewerId, boolean assignedWorker) {
        boolean participant = isOwner(job, viewerId) || assignedWorker;
        AddressPrecision precision = participant || publishesAddress(job)
                ? AddressPrecision.EXACT : AddressPrecision.APPROXIMATE;
        detail.setAddressPrecision(precision);
        if (precision == AddressPrecision.APPROXIMATE) {
            mask(detail.getAddress());
        } else if (!participant) {
            withholdUnit(detail.getAddress());
        }
    }

    public void protect(Job job, JobSummaryDto summary, UUID viewerId, boolean assignedWorker) {
        reveal(job, summary, isOwner(job, viewerId) || assignedWorker || publishesAddress(job));
    }

    public void protectListing(Job job, JobSummaryDto summary, UUID viewerId) {
        reveal(job, summary, isOwner(job, viewerId) || publishesAddress(job));
    }

    public void approximate(AddressDto address) {
        mask(address);
    }

    private static void reveal(Job job, JobSummaryDto summary, boolean exact) {
        summary.setAddressPrecision(exact ? AddressPrecision.EXACT : AddressPrecision.APPROXIMATE);
        if (exact && job.getAddress() != null) {
            summary.setLatitude(exactCoordinate(job.getAddress().getLatitude()));
            summary.setLongitude(exactCoordinate(job.getAddress().getLongitude()));
        }
    }

    private static boolean isOwner(Job job, UUID viewerId) {
        return viewerId != null && job.getCreator() != null && viewerId.equals(job.getCreator().getId());
    }

    private static boolean publishesAddress(Job job) {
        return job.getEmployerType() == JobEmployerType.COMPANY && job.isAddressPublic();
    }

    private static void withholdUnit(AddressDto address) {
        if (address == null || address.getComponents() == null) {
            return;
        }
        AddressComponentsDto components = address.getComponents();
        components.setFloor(null);
        components.setUnitNumber(null);
        components.setAdditionalDirections(null);
        address.setDisplayAddress(AddressFormatter.displayAddress(address));
    }

    private static void mask(AddressDto address) {
        if (address == null) {
            return;
        }
        AddressComponentsDto components = address.getComponents();
        if (components != null) {
            components.setThoroughfare(null);
            components.setSiteName(null);
            components.setBlock(null);
            components.setBuildingName(null);
            components.setBuildingNumber(null);
            components.setFloor(null);
            components.setUnitNumber(null);
            components.setPostalCode(null);
            components.setAdditionalDirections(null);
        }
        AddressPositionDto position = address.getPosition();
        if (position != null) {
            position.setLatitude(round(position.getLatitude()));
            position.setLongitude(round(position.getLongitude()));
            position.setSource(null);
        }
        String neighborhood = components == null ? null : components.getNeighborhood();
        address.setDisplayAddress(Stream.of(neighborhood, address.getDistrictName(), address.getCityName())
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(", ")));
    }

    private static BigDecimal round(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    @Named("approximateCoordinate")
    public static Double approximateCoordinate(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static Double exactCoordinate(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
