package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.companyContact.response.CompanyContactWithoutCompanyDto;
import com.exskylab.koala.entities.CompanyContact;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = UserMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CompanyContactMapper {

    CompanyContactWithoutCompanyDto toCompanyContactWithoutCompanyDto(CompanyContact contact);

    List<CompanyContactWithoutCompanyDto> toCompanyContactWithoutCompanyDtoList(List<CompanyContact> contacts);
}
