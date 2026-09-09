package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaUploadRollbackIT extends AbstractIntegrationTest {

    private static final String TOO_LONG_STORAGE_KEY = "images/" + "x".repeat(256) + ".png";

    @Autowired
    private ImageService imageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        when(storageService.uploadFile(any(), any(), any(), eq(FolderType.IMAGE)))
                .thenReturn(TOO_LONG_STORAGE_KEY);
    }

    @Test
    void aDatabaseFailureAtCommitThroughTheSpringProxyRemovesTheNewObject() throws IOException {
        assertTrue(AopUtils.isAopProxy(imageService));

        assertThrows(DataIntegrityViolationException.class, () -> imageService.uploadImageDto(png()));

        verify(storageService).deleteFile(TOO_LONG_STORAGE_KEY);
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from media where file_url = ?", Integer.class, TOO_LONG_STORAGE_KEY));
    }

    private static MockMultipartFile png() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("image", "rollback.png", "image/png", output.toByteArray());
    }
}
