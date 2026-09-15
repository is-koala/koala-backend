package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.device.response.DeviceDto;
import com.exskylab.koala.entities.Device;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface DeviceMapper {

    @Mapping(target = "name", expression = "java(device.displayName())")
    DeviceDto toDeviceDto(Device device);
}
