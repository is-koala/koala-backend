package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.business.abstracts.GalleryService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.constants.AdminMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.company.response.CompanyDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.core.mappers.CompanyMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.core.constants.GalleryMessages;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Yönetim", description = "Yalnızca ADMIN rolüne açık işlemler.")
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final CompanyService companyService;
    private final UserService userService;
    private final CompanyMapper companyMapper;
    private final UserMapper userMapper;
    private final GalleryService galleryService;

    public AdminController(CompanyService companyService, UserService userService,
                           CompanyMapper companyMapper, UserMapper userMapper,
                           GalleryService galleryService) {
        this.companyService = companyService;
        this.userService = userService;
        this.companyMapper = companyMapper;
        this.userMapper = userMapper;
        this.galleryService = galleryService;
    }

    @Operation(summary = "Görseli her yerden kaldır",
            description = "Görseli bulunduğu tüm ilan ve şirket galerilerinden düşürür, medya kaydını soft delete eder. Depodaki nesne silinmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Görsel kaldırıldı"),
            @ApiResponse(responseCode = "404", description = "Görsel bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<Result> removeImage(@PathVariable UUID imageId) {
        galleryService.removeImageEverywhere(imageId);
        return ResponseEntity.ok(new SuccessResult(GalleryMessages.IMAGE_REMOVED, HttpStatus.OK));
    }

    @GetMapping("/companies")
    public ResponseEntity<SuccessDataResult<PageDto<CompanyDto>>> companies(
            @RequestParam(defaultValue = "false") boolean approved,
            @PageableDefault(size = 20) Pageable pageable) {

        var page = companyService.getCompaniesByApproval(approved, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, page.getContent().stream().map(companyMapper::toCompanyDto).toList()),
                AdminMessages.COMPANIES_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Şirketi onayla",
            description = "Onaylanana kadar şirket ilan yayınlayamaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şirket onaylandı"),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/companies/{companyId}/approve")
    public ResponseEntity<SuccessDataResult<CompanyDto>> approveCompany(@PathVariable UUID companyId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                companyMapper.toCompanyDto(companyService.approveCompany(companyId)),
                AdminMessages.COMPANY_APPROVED, HttpStatus.OK));
    }

    @GetMapping("/users")
    public ResponseEntity<SuccessDataResult<PageDto<SafeUserDto>>> users(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable) {

        var page = userService.search(query, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, page.getContent().stream().map(userMapper::toSafeUserDto).toList()),
                AdminMessages.USERS_LISTED, HttpStatus.OK));
    }
}
