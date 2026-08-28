package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    private int maxImageSizeMb = 10;

    private int maxFileSizeMb = 10;

    private int maxCompanyImages = 10;

    private int maxJobImages = 10;

    private int maxImageMegapixels = 50;

    private Set<String> imageExtensions = new LinkedHashSet<>(List.of("png", "jpg", "jpeg", "webp"));

    private Set<String> fileExtensions = new LinkedHashSet<>(List.of("pdf", "doc", "docx"));

    private Set<String> publicFileExtensions = new LinkedHashSet<>(List.of("pdf", "png", "jpg", "jpeg"));

    public int getMaxImageSizeMb() {
        return maxImageSizeMb;
    }

    public void setMaxImageSizeMb(int maxImageSizeMb) {
        this.maxImageSizeMb = maxImageSizeMb;
    }

    public int getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public void setMaxFileSizeMb(int maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public int getMaxCompanyImages() {
        return maxCompanyImages;
    }

    public void setMaxCompanyImages(int maxCompanyImages) {
        this.maxCompanyImages = maxCompanyImages;
    }

    public int getMaxJobImages() {
        return maxJobImages;
    }

    public void setMaxJobImages(int maxJobImages) {
        this.maxJobImages = maxJobImages;
    }

    public int getMaxImageMegapixels() {
        return maxImageMegapixels;
    }

    public void setMaxImageMegapixels(int maxImageMegapixels) {
        this.maxImageMegapixels = maxImageMegapixels;
    }

    public Set<String> getImageExtensions() {
        return imageExtensions;
    }

    public void setImageExtensions(Set<String> imageExtensions) {
        this.imageExtensions = imageExtensions;
    }

    public Set<String> getFileExtensions() {
        return fileExtensions;
    }

    public void setFileExtensions(Set<String> fileExtensions) {
        this.fileExtensions = fileExtensions;
    }

    public Set<String> getPublicFileExtensions() {
        return publicFileExtensions;
    }

    public void setPublicFileExtensions(Set<String> publicFileExtensions) {
        this.publicFileExtensions = publicFileExtensions;
    }
}
