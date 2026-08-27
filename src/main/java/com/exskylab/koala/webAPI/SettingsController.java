package com.exskylab.koala.webAPI;

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

@RestController
@RequestMapping("/admin/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final SettingMapper settingMapper;

    public SettingsController(SettingsService settingsService, SettingMapper settingMapper) {
        this.settingsService = settingsService;
        this.settingMapper = settingMapper;
    }

    @GetMapping
    public ResponseEntity<SuccessDataResult<List<SettingDto>>> settings() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                settingMapper.toSettingDtoList(settingsService.getAll()),
                SettingMessages.LISTED, HttpStatus.OK));
    }

    @PutMapping("/{key}")
    public ResponseEntity<SuccessDataResult<SettingDto>> update(
            @PathVariable String key, @RequestBody @Valid UpdateSettingDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                settingMapper.toSettingDto(settingsService.update(key, request.getValue())),
                SettingMessages.UPDATED, HttpStatus.OK));
    }
}
