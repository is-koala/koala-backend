package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.company.request.CreateCompanyRequestDto;
import com.exskylab.koala.core.dtos.company.response.CompanyDto;
import com.exskylab.koala.core.dtos.companyContact.request.InviteContactToCompanyDto;
import com.exskylab.koala.core.dtos.companyContactInvitation.response.CompanyContactInvitationDto;
import com.exskylab.koala.core.dtos.company.request.UpdateCompanyRequestDto;
import com.exskylab.koala.entities.CompanyContact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyContactInvitation;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface CompanyService {


    CompanyDto addCompany(@Valid CreateCompanyRequestDto createCompanyRequestDto, MultipartFile logo);

    void inviteContactToCompany(UUID companyId, InviteContactToCompanyDto contactToCompanyDto);

    Company getCompanyById(UUID companyId);

    CompanyDto getCompanyDetail(UUID companyId);

    CompanyDto updateCompany(UUID companyId, UpdateCompanyRequestDto request);

    List<CompanyContact> getContacts(UUID companyId);

    void removeContact(UUID companyId, UUID userId);

    Page<Company> getCompaniesByApproval(boolean approved, Pageable pageable);

    Company approveCompany(UUID companyId);
}
