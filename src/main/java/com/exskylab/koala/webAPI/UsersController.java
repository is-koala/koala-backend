package com.exskylab.koala.webAPI;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.IdentityDocumentReviewService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.identity.response.IdentityReviewDto;
import com.exskylab.koala.core.constants.JobMessages;
import com.exskylab.koala.core.constants.UserMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.job.request.CreateIndividualJobDto;
import com.exskylab.koala.core.dtos.job.response.IndividualJobDto;
import com.exskylab.koala.core.dtos.user.request.ChangePasswordDto;
import com.exskylab.koala.core.dtos.user.request.UpdateProfileDto;
import com.exskylab.koala.core.dtos.user.request.VerifyIdentityDto;
import com.exskylab.koala.core.dtos.user.response.UserMeResponseDto;
import com.exskylab.koala.core.dtos.user.response.UserUpdateResponseDto;
import com.exskylab.koala.core.helpers.BearerTokenExtractor;
import com.exskylab.koala.business.abstracts.PublicProfileService;
import com.exskylab.koala.business.abstracts.StatisticsService;
import com.exskylab.koala.core.dtos.statistics.response.EmployerStatsDto;
import com.exskylab.koala.core.dtos.user.response.PublicProfileDto;
import com.exskylab.koala.core.mappers.JobMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.User;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Validated
@Tag(name = "Kullanıcı profili", description = "Oturum sahibinin profili, parolası, adresi, kimlik doğrulaması ve ilanları.")
@RestController
@RequestMapping("/users")
public class UsersController {

    private final UserService userService;
    private final JwtService jwtService;
    private final SecurityService securityService;
    private final JobService jobService;
    private final JobMapper jobMapper;
    private final StatisticsService statisticsService;
    private final PublicProfileService publicProfileService;
    private final IdentityDocumentReviewService identityDocumentReviewService;

    public UsersController(UserService userService, JwtService jwtService, SecurityService securityService, JobService jobService, JobMapper jobMapper,
                           StatisticsService statisticsService, PublicProfileService publicProfileService,
                           IdentityDocumentReviewService identityDocumentReviewService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.securityService = securityService;
        this.jobService = jobService;
        this.jobMapper = jobMapper;
        this.statisticsService = statisticsService;
        this.publicProfileService = publicProfileService;
        this.identityDocumentReviewService = identityDocumentReviewService;
    }

    @Operation(summary = "Kullanıcının profili",
            description = "Ad, soyad, fotoğraf, meslek, hakkında ve il/ilçe. Tam adres hiçbir zaman dönmez. **cvUrl yalnızca** profil sahibinin kendisine ya da o kişinin başvurduğu bir ilanın işverenine döner, diğerlerinde null kalır. Uç giriş yapmış kullanıcılara açıktır, anonime değil.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{userId}")
    public ResponseEntity<SuccessDataResult<PublicProfileDto>> publicProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                publicProfileService.getPublicProfile(userId),
                UserMessages.USER_RETRIEVED_SUCCESSFULLY, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "İşveren istatistikleri",
            description = "Açılan ilan, tamamlanan iş, ortalama puan, yorum sayısı ve üyelik tarihi. Değerler saklanmaz, her istekte türetilir; değerlendirme yoksa ortalama 0 döner. Herkese açıktır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İstatistikler döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{userId}/employer-stats")
    public ResponseEntity<SuccessDataResult<EmployerStatsDto>> employerStats(@PathVariable UUID userId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                statisticsService.getEmployerStats(userId),
                UserMessages.EMPLOYER_STATS_RETRIEVED, HttpStatus.OK));
    }

    @Operation(summary = "Oturum sahibinin profili",
            description = "İstemcinin yönlendirme kararı için gerekenleri taşır: employer, emailVerified, phoneVerified, identityVerified, roles, companies, educations, experiences, badges, maskedIban ve cvUrl. IBAN tam hâliyle dönmez. averageRating ve reviewCount kullanıcı hakkındaki değerlendirmelerin ortalaması ve sayısıdır; GET /users/{id} ve GET /users/{id}/employer-stats ile aynı kaynaktan okunur, değerlendirme yoksa 0/0 döner ve istemci reviewCount 0 iken puan çizmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil döndürüldü")
    })
   @GetMapping("/me")
    public ResponseEntity<SuccessDataResult<UserMeResponseDto>> getCurrentUser(){

       UserMeResponseDto currentUserDto = userService.getCurrentUserFromDatabaseDto();

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessDataResult<UserMeResponseDto>(
                        currentUserDto,
                        UserMessages.USER_RETRIEVED_SUCCESSFULLY,
                        HttpStatus.OK

                ));
    }


    @Operation(summary = "Profili güncelle",
            description = "Yalnızca gönderilen alanlar değişir. E-posta ve telefon değişikliği doğrulama gerektirir; doğrulama tamamlanana kadar yeni değer uygulanmaz ve yanıttaki pendingVerifications alanına düşer. Her giriş kodun son kullanma anını expiresAt ile taşır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil güncellendi"),
            @ApiResponse(responseCode = "400", description = "Alan doğrulaması başarısız",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "E-posta ya da telefon başka bir hesapta kayıtlı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PatchMapping("/me")
    public ResponseEntity<SuccessDataResult<UserUpdateResponseDto>> patchCurrentUser(@RequestBody @Valid UpdateProfileDto userMePatchRequestDto){

        UserUpdateResponseDto patchedUser = userService.patchCurrentUser(userMePatchRequestDto);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessDataResult<UserUpdateResponseDto>(
                        patchedUser,
                        UserMessages.USER_UPDATED_SUCCESSFULLY,
                        HttpStatus.OK));
    }


    @Operation(summary = "Parolayı değiştir",
            description = "Mevcut parolanın doğrulanmasını ister.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Parola güncellendi"),
            @ApiResponse(responseCode = "400", description = "Yeni parola kuralları karşılamıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "401", description = "Mevcut parola hatalı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/me/password")
    public ResponseEntity<SuccessResult> updateCurrentUserPassword(@RequestBody @Valid ChangePasswordDto userMeChangePasswordPutRequestDto,
                                                                   HttpServletRequest request){
        UUID currentSessionId = jwtService.extractSessionId(BearerTokenExtractor.extract(request));

        userService.updateCurrentUserPassword(userMeChangePasswordPutRequestDto, currentSessionId);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessResult(
                        UserMessages.USER_PASSWORD_UPDATED_SUCCESSFULLY,
                        HttpStatus.OK));
    }

    @Operation(summary = "Profil fotoğrafı yükle",
            description = "Görsel metadata temizlenerek depoya yazılır. İzin verilen uzantılar ve boyut sınırı upload ayarlarından gelir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fotoğraf güncellendi"),
            @ApiResponse(responseCode = "400", description = "Dosya boş ya da desteklenmeyen türde",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "413", description = "Tek dosya 10 MiB ya da toplam multipart gövdesi 42 MiB sınırını aşıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping(value = "/me/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResult> uploadProfilePicture(@Parameter(
            description = "Profile picture",
            required = true,
            content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
                                                                  @RequestPart("image") MultipartFile image){

        userService.updateProfilePicture(image);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessResult(
                        UserMessages.USER_PROFILE_PICTURE_UPLOADED_SUCCESSFULLY,
                        HttpStatus.OK));
    }

    @Operation(summary = "Çip doğrulaması için tek kullanımlık sayı al",
            description = "Kimlik kartının çipine imzalatılacak rastgele sayıyı döndürür. Aynı sayı yalnızca bir kez kullanılabilir ve süresi dolar; böylece daha önce kaydedilmiş bir çip okuması tekrar gönderilemez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sayı üretildi")
    })
    @PostMapping("/me/identity-verification/challenge")
    public ResponseEntity<SuccessDataResult<String>> startIdentityChallenge() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                userService.startIdentityChallenge(),
                UserMessages.IDENTITY_CHALLENGE_CREATED, HttpStatus.OK));
    }

    @Operation(summary = "Oturum sahibinin son TCKK incelemesi",
            description = "Bekleyen, onaylanan veya reddedilmiş son inceleme. Hiç belge yoksa 404. "
                    + "Personel onayı olmadan identityVerified damgalanmaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Son inceleme döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Henüz belge gönderilmedi",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/me/identity-documents")
    public ResponseEntity<SuccessDataResult<IdentityReviewDto>> currentIdentityDocuments() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                IdentityReviewDto.from(identityDocumentReviewService.latestForCurrentUser()),
                UserMessages.USER_IDENTITY_DOCUMENTS_RETRIEVED,
                HttpStatus.OK));
    }

    @Operation(summary = "TCKK fotoğrafı ile kimlik incelemesi başlat",
            description = "Yüz, TCKK ön, TCKK arka ve TCKK yüzün altında (aynı kare) fotoğrafları personel "
                    + "kuyruğuna düşer. Çip kanıtı bu uçta yoktur; kimlik personel onayından sonra damgalanır.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "İnceleme kuyruğa alındı"),
            @ApiResponse(responseCode = "400", description = "Dosya eksik, zaten doğrulanmış ya da bekleyen inceleme var",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "413", description = "Tek dosya 10 MiB ya da toplam multipart gövdesi 42 MiB sınırını aşıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping(value = "/me/identity-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessDataResult<IdentityReviewDto>> submitIdentityDocuments(
            @RequestPart("identity") @Valid VerifyIdentityDto identity,
            @RequestPart("selfie") MultipartFile selfie,
            @RequestPart("front") MultipartFile front,
            @RequestPart("back") MultipartFile back,
            @RequestPart("together") MultipartFile together) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                IdentityReviewDto.from(identityDocumentReviewService.submit(identity, selfie, front, back, together)),
                UserMessages.USER_IDENTITY_DOCUMENTS_SUBMITTED,
                HttpStatus.CREATED));
    }

    @Operation(summary = "Kimlik doğrulama başlat",
            description = "Kimlik kartının çipinden okunan kanıtla kimlik doğrulaması yapar: `sod` ve `dg1` (base64) "
                    + "zorunlu, `dg11` (ulusal karakterli ad), `dg14` (Active Authentication imza algoritması, "
                    + "ActiveAuthenticationInfo) ve `dg15` + `challenge` + `activeAuthenticationSignature` (çip "
                    + "varlığı) isteğe bağlı. Gönderilen her veri grubu SOD özetleriyle doğrulanır; `dg14` yoksa "
                    + "sunucu ECDSA (SHA-1/224/256/384/512, düz ve DER) ve RSA ISO 9796-2 yelpazesini dener. "
                    + "Başarılı olursa kullanıcının rozetleri yeniden değerlendirilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kimlik doğrulandı"),
            @ApiResponse(responseCode = "400", description = "Bilgiler doğrulanamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "502", description = "Doğrulama servisine ulaşılamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/me/identity-verification")
    public ResponseEntity<SuccessResult> verifyIdentity(@RequestBody @Valid VerifyIdentityDto usersMeIdentityVerificationRequestDto){

        userService.verificateIdentityofAuthenticatedUser(usersMeIdentityVerificationRequestDto);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessResult(
                        UserMessages.USER_IDENTITY_VERIFICATED_SUCCESSFULLY,
                        HttpStatus.OK));
    }

    @Operation(summary = "Adresi güncelle",
            description = "Kullanıcının adresini toptan değiştirir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adres güncellendi"),
            @ApiResponse(responseCode = "400", description = "Adres alanları geçersiz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "İl ya da ilçe bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/me/address")
    public ResponseEntity<SuccessDataResult<AddressDto>> updateAddressForCurrentUser(@RequestBody @Valid AddressWriteDto addressDto){

        AddressDto updatedAddress = userService.updateAddressForCurrentUser(addressDto);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessDataResult<AddressDto>(
                        updatedAddress,
                        UserMessages.USER_ADDRESS_UPDATED_SUCCESSFULLY,
                        HttpStatus.OK));
    }


    @Operation(summary = "Açtığım ilanlar",
            description = "Yalnızca oturum sahibinin kendi açtığı ilanları döner. Şirket adına açılmış bir ilan "
                    + "yalnızca onu açan yetkilinin listesinde görünür; şirketin bütün ilanları için "
                    + "GET /companies/{companyId}/jobs kullanılır. İki uç iki ayrı soruya cevap verir. "
                    + "Sayfalıdır. Sıralama yalnızca createdAt, salary, startDateTime ve title alanlarına izin "
                    + "verir; listede olmayan bir alan 400 döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlanlar listelendi"),
            @ApiResponse(responseCode = "400", description = "Sıralama alanı desteklenmiyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/me/jobs")
    public ResponseEntity<SuccessDataResult<PageDto<JobSummaryDto>>> myJobs(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                jobService.getJobsOfCurrentUser(pageable), JobMessages.JOBS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Bireysel ilan aç",
            description = "Şirkete bağlı olmayan ilan oluşturur. Bireysel ilan sigorta zorunluluğu taşıyamaz. İlan kaydedildikten sonra eşleşen iş alarmları tetiklenir.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "İlan oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Alan doğrulaması başarısız ya da bireysel ilan sigorta isteyemez",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Kategori bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/me/jobs")
    public ResponseEntity<SuccessDataResult<IndividualJobDto>> createJobForCurrentUser(@RequestBody @Valid CreateIndividualJobDto usersMeJobsPostRequestDto){

        IndividualJobDto createdJob = jobService.createIndividualJob(usersMeJobsPostRequestDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SuccessDataResult<IndividualJobDto>(
                        createdJob,
                        UserMessages.JOB_CREATED_SUCCESSFULLY,
                        HttpStatus.CREATED
                ));


    }


}
