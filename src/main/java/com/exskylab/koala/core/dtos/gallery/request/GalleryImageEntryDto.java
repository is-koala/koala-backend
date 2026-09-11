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

    @NotNull(message = GalleryMessages.SORT_ORDER_NOT_NULL)
    @Min(value = 0, message = GalleryMessages.SORT_ORDER_INVALID)
    private Integer sortOrder;

    @NotNull(message = GalleryMessages.COVER_NOT_NULL)
    private Boolean cover;

    @Size(max = 255, message = GalleryMessages.CAPTION_TOO_LONG)
    private String caption;
}
