package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.SettingsService;
import com.exskylab.koala.core.constants.SettingMessages;
import com.exskylab.koala.core.dtos.setting.request.UpdateSettingDto;
import com.exskylab.koala.core.dtos.setting.response.SettingDto;
import com.exskylab.koala.core.mappers.SettingMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Çalışma zamanı ayarları", description = "Politika sayılarının deploy olmadan değiştirilmesi.")
@RestController
@RequestMapping("/admin/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final SettingMapper settingMapper;

    public SettingsController(SettingsService settingsService, SettingMapper settingMapper) {
        this.settingsService = settingsService;
        this.settingMapper = settingMapper;
    }

    @Operation(summary = "Tanımlı ayarları listele",
            description = "Tabloda satırı olmayan anahtarlar konfigürasyondaki varsayılana düşer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ayarlar listelendi")
    })
    @RequiresPermission(StaffPermission.SETTINGS_MANAGE)
    @GetMapping
    public ResponseEntity<SuccessDataResult<List<SettingDto>>> settings() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                settingMapper.toSettingDtoList(settingsService.getAll()),
                SettingMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Ayarı güncelle",
            description = "Yalnızca tanımlı anahtarlar yazılabilir; serbest anahtar deposu değildir. Değer sayısal olmak zorundadır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ayar güncellendi"),
            @ApiResponse(responseCode = "400", description = "Tanımsız anahtar ya da sayısal olmayan değer",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.SETTINGS_MANAGE, action = "setting.update")
    @PutMapping("/{key}")
    public ResponseEntity<SuccessDataResult<SettingDto>> update(
            @PathVariable String key, @RequestBody @Valid UpdateSettingDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                settingMapper.toSettingDto(settingsService.update(key, request.getValue())),
                SettingMessages.UPDATED, HttpStatus.OK));
    }
}
