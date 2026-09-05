package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.dtos.address.response.AddressComponentsDto;
import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.address.response.AddressPositionDto;
import com.exskylab.koala.core.dtos.job.response.JobDetailDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.entities.AddressPrecision;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobEmployerType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public final class JobAddressVisibility {

    public void protect(Job job, JobDetailDto detail, UUID viewerId, boolean assignedWorker) {
        AddressPrecision precision = precision(job, viewerId, assignedWorker);
        detail.setAddressPrecision(precision);
        if (precision == AddressPrecision.APPROXIMATE) {
            mask(detail.getAddress());
        }
    }

    public void protect(Job job, JobSummaryDto summary, UUID viewerId, boolean assignedWorker) {
        AddressPrecision precision = precision(job, viewerId, assignedWorker);
        summary.setAddressPrecision(precision);
        if (precision == AddressPrecision.APPROXIMATE) {
            summary.setLatitude(round(summary.getLatitude()));
            summary.setLongitude(round(summary.getLongitude()));
        }
    }

    private AddressPrecision precision(Job job, UUID viewerId, boolean assignedWorker) {
        boolean owner = viewerId != null && job.getCreator() != null
                && viewerId.equals(job.getCreator().getId());
        boolean publicCompanyAddress = job.getEmployerType() == JobEmployerType.COMPANY
                && job.isAddressPublic();
        return publicCompanyAddress || owner || assignedWorker
                ? AddressPrecision.EXACT : AddressPrecision.APPROXIMATE;
    }

    private void mask(AddressDto address) {
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
                .collect(java.util.stream.Collectors.joining(", ")));
    }

    private static BigDecimal round(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static Double round(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
