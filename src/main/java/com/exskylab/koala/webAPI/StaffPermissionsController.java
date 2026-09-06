package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.dtos.staff.response.StaffPermissionDto;
import com.exskylab.koala.core.mappers.StaffPermissionMapper;
import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.entities.StaffPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Tag(name = "Personel izinleri", description = "Rollere verilebilecek izin kataloğu.")
@RestController
@RequestMapping("/admin/permissions")
public class StaffPermissionsController {

    private final StaffPermissionMapper staffPermissionMapper;

    public StaffPermissionsController(StaffPermissionMapper staffPermissionMapper) {
        this.staffPermissionMapper = staffPermissionMapper;
    }

    @Operation(summary = "İzin kataloğunu listele",
            description = "Rol grafiğine yazılabilecek izin kodlarının tamamı, denetim kaydının okuduğu "
                    + "metadata ile birlikte: izin yazma yapıyor mu ve hangi hassasiyet sınıfında.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İzinler listelendi")
    })
    @RequiresPermission(StaffPermission.ROLE_MANAGE)
    @GetMapping
    public ResponseEntity<SuccessDataResult<List<StaffPermissionDto>>> permissions() {
        List<StaffPermission> catalogue = Arrays.stream(StaffPermission.values())
                .sorted(Comparator.comparing(StaffPermission::getCode))
                .toList();

        return ResponseEntity.ok(new SuccessDataResult<>(
                staffPermissionMapper.toPermissionDtoList(catalogue),
                StaffMessages.PERMISSIONS_LISTED, HttpStatus.OK));
    }
}
