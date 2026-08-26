package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.company.response.CompanyDto;
import com.exskylab.koala.core.dtos.company.response.OnlyCompanyDto;
import com.exskylab.koala.entities.Company;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring",
        uses = {MediaUrlResolver.class, CompanyContactMapper.class, AddressMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CompanyMapper {

    @Mapping(target = "logoUrl", source = "logo", qualifiedByName = "imageToPublicUrl")
    @Mapping(target = "companyContacts", source = "contacts")
    CompanyDto toCompanyDto(Company company);

    @Mapping(target = "logoUrl", source = "logo", qualifiedByName = "imageToPublicUrl")
    OnlyCompanyDto toOnlyCompanyDto(Company company);
}
