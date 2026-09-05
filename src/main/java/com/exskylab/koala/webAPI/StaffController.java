package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.dtos.staff.response.StaffIdentityDto;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.mappers.StaffIdentityMapper;
import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Personel kimliği", description = "Panelin oturum açan personeli tanıdığı uç.")
@RestController
@RequestMapping("/admin/me")
public class StaffController {

    private final StaffIdentityMapper staffIdentityMapper;

    public StaffController(StaffIdentityMapper staffIdentityMapper) {
        this.staffIdentityMapper = staffIdentityMapper;
    }

    @Operation(summary = "Oturum açan personel",
            description = "Personel kaydı, rolleri ve rol grafiğinden çözülmüş izinleri. "
                    + "İzinler token'dan değil Koala'nın veritabanından gelir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Personel kimliği döndü"),
            @ApiResponse(responseCode = "403", description = "Token geçerli ama personel kaydı yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission
    @GetMapping
    public ResponseEntity<SuccessDataResult<StaffIdentityDto>> me(
            @AuthenticationPrincipal StaffPrincipal principal) {

        if (principal == null || !principal.isRegistered()) {
            throw new ForbiddenException(StaffMessages.RECORD_NOT_FOUND);
        }

        return ResponseEntity.ok(new SuccessDataResult<>(
                staffIdentityMapper.toIdentityDto(principal), StaffMessages.IDENTITY_RESOLVED, HttpStatus.OK));
    }
}
