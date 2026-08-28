package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.File;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface CvService {

    File uploadCv(MultipartFile document);

    void deleteCv();

    String resolveCvUrl(UUID userId);

    void assertCanDownload(String storageKey);
}
