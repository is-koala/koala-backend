package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.companyContact.response.CompanyContactDto;
import com.exskylab.koala.entities.CompanyContact;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = UserMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CompanyContactMapper {

    CompanyContactDto toCompanyContactWithoutCompanyDto(CompanyContact contact);

    List<CompanyContactDto> toCompanyContactWithoutCompanyDtoList(List<CompanyContact> contacts);
}
