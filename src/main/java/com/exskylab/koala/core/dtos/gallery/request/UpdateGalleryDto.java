package com.exskylab.koala.core.dtos.gallery.request;

import com.exskylab.koala.core.constants.GalleryMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateGalleryDto {

    @NotNull(message = GalleryMessages.IMAGES_NOT_NULL)
    @Valid
    private List<GalleryImageEntryDto> images;

    @AssertTrue(message = GalleryMessages.DUPLICATE_IMAGE)
    public boolean isEveryImageListedOnce() {
        if (images == null) {
            return true;
        }
        return images.stream().map(GalleryImageEntryDto::getImageId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet()).size() == images.size();
    }

    @AssertTrue(message = GalleryMessages.MULTIPLE_COVERS)
    public boolean isAtMostOneCoverSelected() {
        if (images == null) {
            return true;
        }
        return images.stream().filter(entry -> Boolean.TRUE.equals(entry.getCover())).count() <= 1;
    }

    public List<UUID> imageIds() {
        return images == null ? List.of()
                : images.stream().map(GalleryImageEntryDto::getImageId).toList();
    }
}
