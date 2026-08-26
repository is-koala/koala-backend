package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.properties.R2Properties;
import com.exskylab.koala.entities.Image;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class MediaUrlResolver {

    private final R2Properties r2Properties;

    public MediaUrlResolver(R2Properties r2Properties) {
        this.r2Properties = r2Properties;
    }

    @Named("imageToPublicUrl")
    public String toPublicUrl(Image image) {
        if (image == null || image.getFileUrl() == null) {
            return null;
        }
        return r2Properties.getPublicUrl() + "/" + image.getFileUrl();
    }
}
