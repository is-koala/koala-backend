package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.properties.UploadProperties;
import com.exskylab.koala.core.utilities.storage.FolderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class UploadValidatorTest {

    private UploadProperties properties;
    private UploadValidator validator;

    @BeforeEach
    void setUp() {
        properties = new UploadProperties();
        validator = new UploadValidator(properties);
    }

    private static MockMultipartFile png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("image", "photo.png", "image/png", out.toByteArray());
    }

    @Test
    void anOrdinaryPhotoPasses() throws IOException {
        assertDoesNotThrow(() -> validator.validate(png(800, 600), FolderType.IMAGE, "{e}", "{s}"));
    }

    @Test
    void aCanvasBeyondTheMegapixelLimitIsRejectedBeforeDecoding() throws IOException {
        properties.setMaxImageMegapixels(1);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> validator.validate(png(2000, 2000), FolderType.IMAGE, "{e}", "{s}"));

        assertEquals("{storage.image.too.large}", thrown.getMessage());
    }

    @Test
    void aFileThatIsNotReallyAnImageIsRejected() {
        MockMultipartFile fake = new MockMultipartFile("image", "photo.png", "image/png",
                "bu bir png degil".getBytes());

        assertThrows(ValidationException.class,
                () -> validator.validate(fake, FolderType.IMAGE, "{e}", "{s}"));
    }

    @Test
    void theCanvasCheckOnlyAppliesToImages() {
        MockMultipartFile document = new MockMultipartFile("file", "cv.pdf", "application/pdf",
                new byte[]{1, 2, 3});

        assertDoesNotThrow(() -> validator.validate(document, FolderType.FILE, "{e}", "{s}"));
    }

    @Test
    void anUnsupportedExtensionIsStillRejected() {
        MockMultipartFile script = new MockMultipartFile("image", "evil.svg", "image/svg+xml",
                "<svg/>".getBytes());

        assertThrows(ValidationException.class,
                () -> validator.validate(script, FolderType.IMAGE, "{e}", "{s}"));
    }
}
