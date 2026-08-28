package com.exskylab.koala.core.dtos.gallery.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GalleryImageDto {

    private UUID id;

    private String url;

    private int sortOrder;

    private boolean cover;

    private String caption;
}
