package com.exskylab.koala.core.dtos.gallery.request;

import com.exskylab.koala.core.constants.GalleryMessages;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GalleryImageEntryDto {

    @NotNull(message = GalleryMessages.IMAGE_ID_NOT_NULL)
    private UUID imageId;

    @Min(value = 0, message = GalleryMessages.SORT_ORDER_INVALID)
    private int sortOrder;

    private boolean cover;

    @Size(max = 255, message = GalleryMessages.CAPTION_TOO_LONG)
    private String caption;
}
