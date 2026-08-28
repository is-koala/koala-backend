package com.exskylab.koala.core.helpers;

import com.exskylab.koala.entities.Image;
import com.exskylab.koala.entities.Media;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class MediaUrlResolver {

    private final StorageUrlResolver storageUrlResolver;

    public MediaUrlResolver(StorageUrlResolver storageUrlResolver) {
        this.storageUrlResolver = storageUrlResolver;
    }

    @Named("imageToPublicUrl")
    public String toPublicUrl(Image image) {
        return image == null ? null : storageUrlResolver.urlFor(image.getFileUrl());
    }

    @Named("mediaToPublicUrl")
    public String toFileUrl(Media media) {
        return media == null ? null : storageUrlResolver.urlFor(media.getFileUrl());
    }
}
