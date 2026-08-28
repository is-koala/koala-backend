package com.exskylab.koala.webAPI;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.EducationService;
import com.exskylab.koala.business.abstracts.JobCategoryService;
import com.exskylab.koala.business.abstracts.LocationService;
import com.exskylab.koala.core.constants.LocationMessages;
import com.exskylab.koala.core.dtos.jobCategory.response.JobCategoryDto;
import com.exskylab.koala.core.mappers.JobCategoryMapper;
import com.exskylab.koala.core.mappers.ReferenceDataMapper;
import com.exskylab.koala.core.dtos.reference.response.CityDto;
import com.exskylab.koala.core.dtos.reference.response.CountryDto;
import com.exskylab.koala.core.dtos.reference.response.DepartmentDto;
import com.exskylab.koala.core.dtos.reference.response.DistrictDto;
import com.exskylab.koala.core.dtos.reference.response.FacultyDto;
import com.exskylab.koala.core.dtos.reference.response.UniversityDto;
import com.exskylab.koala.core.mappers.EducationMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Referans veri", description = "Ülke, il, ilçe, iş kategorisi ve eğitim referansları. Tamamı herkese açıktır.")
@RestController
@RequestMapping("/reference")
public class ReferenceDataController {

    private final LocationService locationService;
    private final JobCategoryService jobCategoryService;
    private final ReferenceDataMapper referenceDataMapper;
    private final JobCategoryMapper jobCategoryMapper;
    private final EducationService educationService;
    private final EducationMapper educationMapper;

    public ReferenceDataController(LocationService locationService, JobCategoryService jobCategoryService,
                                   ReferenceDataMapper referenceDataMapper, JobCategoryMapper jobCategoryMapper,
                                   EducationService educationService, EducationMapper educationMapper) {
        this.locationService = locationService;
        this.jobCategoryService = jobCategoryService;
        this.referenceDataMapper = referenceDataMapper;
        this.jobCategoryMapper = jobCategoryMapper;
        this.educationService = educationService;
        this.educationMapper = educationMapper;
    }

    @SecurityRequirements
    @Operation(summary = "Ülkeler")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ülkeler listelendi")
    })
    @GetMapping("/countries")
    public ResponseEntity<SuccessDataResult<List<CountryDto>>> countries() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                referenceDataMapper.toCountryDtoList(locationService.getAllCountries()),
                LocationMessages.LISTED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "Ülkenin illeri")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İller listelendi"),
            @ApiResponse(responseCode = "404", description = "Ülke bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/countries/{countryId}/cities")
    public ResponseEntity<SuccessDataResult<List<CityDto>>> cities(@PathVariable UUID countryId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                referenceDataMapper.toCityDtoList(locationService.getCitiesByCountryId(countryId)),
                LocationMessages.LISTED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "İlin ilçeleri")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlçeler listelendi"),
            @ApiResponse(responseCode = "404", description = "İl bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/cities/{cityId}/districts")
    public ResponseEntity<SuccessDataResult<List<DistrictDto>>> districts(@PathVariable UUID cityId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                referenceDataMapper.toDistrictDtoList(locationService.getDistrictsByCityId(cityId)),
                LocationMessages.LISTED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "İş kategorileri",
            description = "Her kategori sabit bir code taşır. İstemci ikon, pin ve renk eşlemesini UUID ya da görünen ada değil bu koda bağlamalıdır; kodlar addan bağımsızdır ve değişmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kategoriler listelendi")
    })
    @GetMapping("/job-categories")
    public ResponseEntity<SuccessDataResult<List<JobCategoryDto>>> jobCategories() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobCategoryService.getAllCategories().stream().map(jobCategoryMapper::toCategoryDto).toList(),
                LocationMessages.LISTED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "Üniversiteler")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Üniversiteler listelendi")
    })
    @GetMapping("/universities")
    public ResponseEntity<SuccessDataResult<List<UniversityDto>>> universities() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                educationMapper.toUniversityDtoList(educationService.getUniversities()),
                LocationMessages.LISTED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "Üniversitenin fakülteleri")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fakülteler listelendi"),
            @ApiResponse(responseCode = "404", description = "Üniversite bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/universities/{universityId}/faculties")
    public ResponseEntity<SuccessDataResult<List<FacultyDto>>> faculties(@PathVariable UUID universityId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                educationMapper.toFacultyDtoList(educationService.getFaculties(universityId)),
                LocationMessages.LISTED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "Fakültenin bölümleri")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bölümler listelendi"),
            @ApiResponse(responseCode = "404", description = "Fakülte bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/faculties/{facultyId}/departments")
    public ResponseEntity<SuccessDataResult<List<DepartmentDto>>> departments(@PathVariable UUID facultyId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                educationMapper.toDepartmentDtoList(educationService.getDepartments(facultyId)),
                LocationMessages.LISTED, HttpStatus.OK));
    }
}
