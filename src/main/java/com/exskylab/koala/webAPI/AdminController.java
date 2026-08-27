package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.constants.AdminMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.company.response.CompanyDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.core.mappers.CompanyMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final CompanyService companyService;
    private final UserService userService;
    private final CompanyMapper companyMapper;
    private final UserMapper userMapper;

    public AdminController(CompanyService companyService, UserService userService,
                           CompanyMapper companyMapper, UserMapper userMapper) {
        this.companyService = companyService;
        this.userService = userService;
        this.companyMapper = companyMapper;
        this.userMapper = userMapper;
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
