package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.company.request.CreateCompanyRequestDto;
import com.exskylab.koala.core.dtos.company.response.CompanyDto;
import com.exskylab.koala.core.dtos.companyContact.request.InviteContactToCompanyDto;
import com.exskylab.koala.core.dtos.companyContactInvitation.response.CompanyContactInvitationDto;
import com.exskylab.koala.core.dtos.company.request.UpdateCompanyRequestDto;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.companyFollow.response.FollowedCompanyDto;
import com.exskylab.koala.core.dtos.gallery.request.UpdateGalleryDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import com.exskylab.koala.entities.CompanyApprovalStatus;
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

    CompanyDto updateLogo(UUID companyId, MultipartFile logo);

    CompanyDto removeLogo(UUID companyId);

    void followCompany(UUID companyId);

    void unfollowCompany(UUID companyId);

    PageDto<FollowedCompanyDto> getFollowedCompanies(Pageable pageable);

    List<GalleryImageDto> getCompanyGallery(UUID companyId);

    List<GalleryImageDto> addCompanyImage(UUID companyId, MultipartFile file);

    List<GalleryImageDto> replaceCompanyGallery(UUID companyId, UpdateGalleryDto request);

    List<GalleryImageDto> removeCompanyImage(UUID companyId, UUID imageId);



    CompanyDto addCompany(@Valid CreateCompanyRequestDto createCompanyRequestDto, MultipartFile logo);

    void inviteContactToCompany(UUID companyId, InviteContactToCompanyDto contactToCompanyDto);

    Company getCompanyById(UUID companyId);

    CompanyDto getCompanyDetail(UUID companyId);

    CompanyDto updateCompany(UUID companyId, UpdateCompanyRequestDto request);

    List<CompanyContact> getContacts(UUID companyId);

    void removeContact(UUID companyId, UUID userId);

    Page<Company> searchCompanies(CompanyApprovalStatus status, String query, Pageable pageable);

    Company approveCompany(UUID companyId, String note);

    Company rejectCompany(UUID companyId, String reason);
}
