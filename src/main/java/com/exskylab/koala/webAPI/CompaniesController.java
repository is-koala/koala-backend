package com.exskylab.koala.webAPI;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.CompanyContactInvitationService;
import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.core.constants.CompanyMessages;
import com.exskylab.koala.core.dtos.company.request.CreateCompanyRequestDto;
import com.exskylab.koala.core.dtos.company.request.UpdateCompanyRequestDto;
import com.exskylab.koala.core.dtos.company.response.CompanyDto;
import com.exskylab.koala.core.dtos.companyContact.request.InviteContactToCompanyDto;
import com.exskylab.koala.core.dtos.companyContactInvitation.response.CompanyContactInvitationDto;
import com.exskylab.koala.core.constants.JobMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.job.request.CreateCompanyJobDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.dtos.job.response.CompanyJobDto;
import com.exskylab.koala.core.dtos.companyContact.response.CompanyContactDto;
import com.exskylab.koala.core.constants.GalleryMessages;
import com.exskylab.koala.core.dtos.gallery.request.UpdateGalleryDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import com.exskylab.koala.core.mappers.CompanyContactInvitationMapper;
import com.exskylab.koala.core.mappers.CompanyContactMapper;
import com.exskylab.koala.core.mappers.CompanyMapper;
import com.exskylab.koala.core.mappers.JobMapper;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import com.exskylab.koala.entities.CompanyContactInvitation;
import com.exskylab.koala.entities.Job;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Şirketler", description = "Şirket oluşturma, düzenleme, yetkililer, galeri ve takip.")
@RestController
@RequestMapping("/companies")
public class CompaniesController {

    private final CompanyService companyService;
    private final CompanyContactMapper companyContactMapper;
    private final JobService jobService;
    private final CompanyContactInvitationService companyContactInvitationService;


    public CompaniesController(CompanyService companyService, JobService jobService,
                             CompanyContactInvitationService companyContactInvitationService,
                             CompanyContactMapper companyContactMapper) {
        this.companyService = companyService;
        this.jobService = jobService;
        this.companyContactInvitationService = companyContactInvitationService;
        this.companyContactMapper = companyContactMapper;
    }


    @Operation(summary = "Şirket oluştur",
            description = "Talebi oluşturan kullanıcı şirketin ADMIN yetkilisi olur. Şirket onaylanana kadar ilan yayınlayamaz. Kullanıcının kimliği doğrulanmış olmalıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Şirket oluşturuldu, onay bekliyor"),
            @ApiResponse(responseCode = "400", description = "Kimlik doğrulanmamış ya da alan doğrulaması başarısız",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessDataResult<CompanyDto>> addCompany(
            @RequestPart("company") @Valid CreateCompanyRequestDto createCompanyRequestDto,
            @RequestPart(value = "logo", required = false) MultipartFile logo){

        var response = companyService.addCompany(createCompanyRequestDto, logo);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SuccessDataResult<CompanyDto>(response, CompanyMessages.COMPANY_ADDED_SUCCESS, HttpStatus.CREATED)
        );

    }


    @Operation(summary = "Şirkete yetkili davet et",
            description = "Gövde yalnızca davet edilen kişinin e-postasını ve rolünü taşır; kimlik numarası istenmez. "
                    + "Davet edilen kişinin kimliği doğrulanmış olmalıdır. "
                    + "Davet edilen kullanıcıya e-posta ve uygulama içi bildirim gider.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Davet gönderildi"),
            @ApiResponse(responseCode = "400", description = "Rol geçersiz ya da davet edilen kişinin kimliği doğrulanmamış",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Şirket ya da kullanıcı bulunamadı; şirketin ADMIN yetkilisi değilseniz de bu kod döner",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Kullanıcı zaten yetkili ya da bekleyen davet var",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{companyId}/contact-invitations")
    public ResponseEntity<SuccessResult> sendCompanyContactInvitation(@PathVariable UUID companyId, @RequestBody @Valid InviteContactToCompanyDto contactToCompanyDto){

        companyService.inviteContactToCompany(companyId, contactToCompanyDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SuccessResult(CompanyMessages.COMPANY_CONTACT_INVITATION_SENT_SUCCESS,
                        HttpStatus.CREATED));

    }

    @Operation(summary = "Şirketin davetleri",
            description = "Yalnızca şirketin ADMIN yetkilisine açıktır; platform yöneticisi olmak gerekmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Davetler listelendi"),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı ya da şirketin ADMIN yetkilisi değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{companyId}/contact-invitations")
    public ResponseEntity<SuccessDataResult<List<CompanyContactInvitationDto>>> getCompanyContactInvitations(@PathVariable UUID companyId){
        List<CompanyContactInvitationDto> response = companyContactInvitationService.getCompanyContactInvitationsByCompanyIdDto(companyId);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessDataResult<List<CompanyContactInvitationDto>>(response,
                        CompanyMessages.COMPANY_CONTACT_INVITATIONS_LISTED_SUCCESS,
                        HttpStatus.OK

                ));


    }


    @Operation(summary = "Şirket adına ilan aç",
            description = "Yalnızca şirketin yetkilisi açabilir. İlan kaydedildikten sonra eşleşen iş alarmları ve şirketi takip edenler bildirilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "İlan oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Alan doğrulaması başarısız",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{companyId}/jobs")
    public ResponseEntity<SuccessDataResult<CompanyJobDto>> createJobForCompany(@PathVariable UUID companyId,
                                                                                                        @RequestBody @Valid CreateCompanyJobDto companiesCompanyIdJobsPostRequestDto){
        CompanyJobDto response = jobService.createCorporateJob(companyId, companiesCompanyIdJobsPostRequestDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SuccessDataResult<CompanyJobDto>(response,
                        CompanyMessages.JOB_CREATED_SUCCESS,
                        HttpStatus.CREATED
                ));
    }

    @Operation(summary = "Şirketin ilanları",
            description = "Şirketin bütün ilanlarını, hangi yetkilinin açtığından bağımsız olarak listeler; "
                    + "yalnızca kendi açtıklarınız için GET /users/me/jobs kullanılır. "
                    + "Yalnızca şirketin yetkilisi listeleyebilir. Sayfalıdır; sıralama yalnızca createdAt, "
                    + "salary, startDateTime ve title alanlarına izin verir. Tam adres yalnız ilanı açan "
                    + "yetkiliye ve adresi herkese açılmış ilanlarda döner, diğer yetkililere yaklaşık konum gider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlanlar listelendi"),
            @ApiResponse(responseCode = "400", description = "Sıralama alanı desteklenmiyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "403", description = "Şirketin yetkilisi değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{companyId}/jobs")
    public ResponseEntity<SuccessDataResult<PageDto<JobSummaryDto>>> companyJobs(
            @PathVariable UUID companyId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                jobService.getJobsOfCompany(companyId, pageable), JobMessages.JOBS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Şirket detayı",
            description = "Yanıt takipçi sayısını ve oturum sahibinin takip edip etmediğini de taşır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şirket döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{companyId}")
    public ResponseEntity<SuccessDataResult<CompanyDto>> getCompany(@PathVariable UUID companyId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.getCompanyDetail(companyId),
                CompanyMessages.COMPANY_FETCHED, HttpStatus.OK));
    }

    @Operation(summary = "Şirketi güncelle",
            description = "Yalnızca şirketin ADMIN yetkilisi düzenleyebilir; üye olmayan 404 alır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şirket güncellendi"),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PatchMapping("/{companyId}")
    public ResponseEntity<SuccessDataResult<CompanyDto>> updateCompany(
            @PathVariable UUID companyId, @RequestBody @Valid UpdateCompanyRequestDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.updateCompany(companyId, request),
                CompanyMessages.COMPANY_UPDATED, HttpStatus.OK));
    }

    @Operation(summary = "Şirketin yetkilileri")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Yetkililer listelendi"),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{companyId}/contacts")
    public ResponseEntity<SuccessDataResult<List<CompanyContactDto>>> contacts(
            @PathVariable UUID companyId) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                companyContactMapper.toCompanyContactWithoutCompanyDtoList(companyService.getContacts(companyId)),
                CompanyMessages.CONTACTS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Şirket logosunu değiştir",
            description = "Yalnızca şirketin ADMIN yetkilisi değiştirebilir. Görsel metadata temizlenerek depoya yazılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logo güncellendi"),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "400", description = "Dosya boş, çok büyük ya da desteklenmeyen türde",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping(value = "/{companyId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessDataResult<CompanyDto>> updateCompanyLogo(
            @PathVariable UUID companyId, @RequestPart("logo") MultipartFile logo) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.updateLogo(companyId, logo), CompanyMessages.LOGO_UPDATED, HttpStatus.OK));
    }

    @Operation(summary = "Şirket logosunu kaldır",
            description = "Logo bağlantısı düşürülür; depodaki nesne silinmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logo kaldırıldı"),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/{companyId}/logo")
    public ResponseEntity<SuccessDataResult<CompanyDto>> removeCompanyLogo(@PathVariable UUID companyId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.removeLogo(companyId), CompanyMessages.LOGO_REMOVED, HttpStatus.OK));
    }

    @Operation(summary = "Şirketi takip et",
            description = "İdempotanttır, ikinci istek yeni kayıt açmaz. Takip edilen şirket ilan açtığında bildirim gider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Takip ediliyor"),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{companyId}/follow")
    public ResponseEntity<Result> followCompany(@PathVariable UUID companyId) {
        companyService.followCompany(companyId);
        return ResponseEntity.ok(new SuccessResult(CompanyMessages.COMPANY_FOLLOWED, HttpStatus.OK));
    }

    @Operation(summary = "Şirket takibini bırak",
            description = "Takip edilmeyen bir şirketi bırakmak sessizce geçer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Takip bırakıldı"),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/{companyId}/follow")
    public ResponseEntity<Result> unfollowCompany(@PathVariable UUID companyId) {
        companyService.unfollowCompany(companyId);
        return ResponseEntity.ok(new SuccessResult(CompanyMessages.COMPANY_UNFOLLOWED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "Şirket galerisi",
            description = "Sıra numarasına göre döner. Herkese açıktır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Galeri listelendi"),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{companyId}/images")
    public ResponseEntity<SuccessDataResult<List<GalleryImageDto>>> companyImages(@PathVariable UUID companyId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.getCompanyGallery(companyId), GalleryMessages.IMAGE_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Şirket galerisine görsel yükle",
            description = "Yalnızca şirketin ADMIN yetkilisi ekleyebilir. İlk görsel kendiliğinden kapak olur.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Görsel eklendi"),
            @ApiResponse(responseCode = "400", description = "Dosya boş, çok büyük ya da desteklenmeyen türde",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Galeri sınırı aşıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping(value = "/{companyId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessDataResult<List<GalleryImageDto>>> addCompanyImage(
            @PathVariable UUID companyId, @RequestPart("image") MultipartFile image) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                companyService.addCompanyImage(companyId, image),
                GalleryMessages.IMAGE_ADDED, HttpStatus.CREATED));
    }

    @Operation(summary = "Şirket galerisini toptan değiştir",
            description = "Listede olmayan kayıtlar galeriden düşer. Aynı görsel iki kez listelenemez ve en fazla bir kapak seçilebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Galeri güncellendi"),
            @ApiResponse(responseCode = "400", description = "Aynı görsel iki kez listelendi ya da birden fazla kapak seçildi",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Görsel bu galeride yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/{companyId}/images")
    public ResponseEntity<SuccessDataResult<List<GalleryImageDto>>> updateCompanyGallery(
            @PathVariable UUID companyId, @RequestBody @Valid UpdateGalleryDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.replaceCompanyGallery(companyId, request),
                GalleryMessages.IMAGE_UPDATED, HttpStatus.OK));
    }

    @Operation(summary = "Görseli şirket galerisinden çıkar",
            description = "Kapak silinirse sıradaki görsel kapak olur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Görsel kaldırıldı"),
            @ApiResponse(responseCode = "404", description = "Görsel bu galeride yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/{companyId}/images/{imageId}")
    public ResponseEntity<SuccessDataResult<List<GalleryImageDto>>> removeCompanyImage(
            @PathVariable UUID companyId, @PathVariable UUID imageId) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.removeCompanyImage(companyId, imageId),
                GalleryMessages.IMAGE_REMOVED, HttpStatus.OK));
    }

    @Operation(summary = "Yetkiliyi şirketten çıkar",
            description = "Yalnızca ADMIN çıkarabilir. Son ADMIN çıkarılamaz, yoksa şirket yönetilemez hâle gelir. Üyelik kaydı silinmez, soft delete edilir; aynı kişi sonradan yeniden eklenebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Yetkili çıkarıldı"),
            @ApiResponse(responseCode = "404", description = "Şirket ya da yetkili bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Son yönetici çıkarılamaz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/{companyId}/contacts/{userId}")
    public ResponseEntity<Result> removeContact(@PathVariable UUID companyId, @PathVariable UUID userId) {
        companyService.removeContact(companyId, userId);
        return ResponseEntity.ok(new SuccessResult(CompanyMessages.CONTACT_REMOVED, HttpStatus.OK));
    }
}
