package com.exskylab.koala.core.dtos.device.request;

import com.exskylab.koala.core.constants.DeviceMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RenameDeviceDto {

    @NotBlank(message = DeviceMessages.NAME_NOT_BLANK)
    @Size(max = 60, message = DeviceMessages.NAME_TOO_LONG)
    private String name;
}
