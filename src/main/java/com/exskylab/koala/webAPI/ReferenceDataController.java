package com.exskylab.koala.webAPI;

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

    @GetMapping("/countries")
    public ResponseEntity<SuccessDataResult<List<CountryDto>>> countries() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                referenceDataMapper.toCountryDtoList(locationService.getAllCountries()),
                LocationMessages.LISTED, HttpStatus.OK));
    }

    @GetMapping("/countries/{countryId}/cities")
    public ResponseEntity<SuccessDataResult<List<CityDto>>> cities(@PathVariable UUID countryId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                referenceDataMapper.toCityDtoList(locationService.getCitiesByCountryId(countryId)),
                LocationMessages.LISTED, HttpStatus.OK));
    }

    @GetMapping("/cities/{cityId}/districts")
    public ResponseEntity<SuccessDataResult<List<DistrictDto>>> districts(@PathVariable UUID cityId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                referenceDataMapper.toDistrictDtoList(locationService.getDistrictsByCityId(cityId)),
                LocationMessages.LISTED, HttpStatus.OK));
    }

    @GetMapping("/job-categories")
    public ResponseEntity<SuccessDataResult<List<JobCategoryDto>>> jobCategories() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobCategoryService.getAllCategories().stream().map(jobCategoryMapper::toCategoryDto).toList(),
                LocationMessages.LISTED, HttpStatus.OK));
    }

    @GetMapping("/universities")
    public ResponseEntity<SuccessDataResult<List<UniversityDto>>> universities() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                educationMapper.toUniversityDtoList(educationService.getUniversities()),
                LocationMessages.LISTED, HttpStatus.OK));
    }

    @GetMapping("/universities/{universityId}/faculties")
    public ResponseEntity<SuccessDataResult<List<FacultyDto>>> faculties(@PathVariable UUID universityId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                educationMapper.toFacultyDtoList(educationService.getFaculties(universityId)),
                LocationMessages.LISTED, HttpStatus.OK));
    }

    @GetMapping("/faculties/{facultyId}/departments")
    public ResponseEntity<SuccessDataResult<List<DepartmentDto>>> departments(@PathVariable UUID facultyId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                educationMapper.toDepartmentDtoList(educationService.getDepartments(facultyId)),
                LocationMessages.LISTED, HttpStatus.OK));
    }
}
