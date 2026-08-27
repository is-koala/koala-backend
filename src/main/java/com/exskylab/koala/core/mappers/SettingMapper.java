package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.setting.response.SettingDto;
import com.exskylab.koala.entities.AppSetting;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface SettingMapper {

    SettingDto toSettingDto(AppSetting setting);

    List<SettingDto> toSettingDtoList(List<AppSetting> settings);
}
