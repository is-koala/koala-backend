package com.exskylab.koala.core.helpers;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.exskylab.koala.support.ImageMetadataFixtures.BODY_SERIAL;
import static com.exskylab.koala.support.ImageMetadataFixtures.CAMERA_MAKE;
import static com.exskylab.koala.support.ImageMetadataFixtures.IMAGE_UNIQUE_ID;
import static com.exskylab.koala.support.ImageMetadataFixtures.LENS_SERIAL;
import static com.exskylab.koala.support.ImageMetadataFixtures.PNG_TEXT;
import static com.exskylab.koala.support.ImageMetadataFixtures.containsAscii;
import static com.exskylab.koala.support.ImageMetadataFixtures.jpegWithLocationAndSerials;
import static com.exskylab.koala.support.ImageMetadataFixtures.pngChunkTypes;
import static com.exskylab.koala.support.ImageMetadataFixtures.pngWithAlpha;
import static com.exskylab.koala.support.ImageMetadataFixtures.pngWithTextChunk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageMetadataStripperTest {

    @Test
    void aJpegLosesItsLocationCameraAndSerialNumbersButKeepsItsPicture() throws IOException {
        byte[] original = jpegWithLocationAndSerials(64, 48);
        assertTrue(containsAscii(original, BODY_SERIAL), "the fixture must carry what the stripper has to remove");

        byte[] stripped = ImageMetadataStripper.strip(original, "jpg");

        for (String leaked : List.of("Exif\0\0", CAMERA_MAKE, BODY_SERIAL, LENS_SERIAL, IMAGE_UNIQUE_ID)) {
            assertFalse(containsAscii(stripped, leaked), "the stored copy still carries " + leaked.trim());
        }
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(stripped));
        assertNotNull(decoded);
        assertEquals(64, decoded.getWidth());
        assertEquals(48, decoded.getHeight());
    }

    @Test
    void aPngLosesItsTextChunks() throws IOException {
        byte[] original = pngWithTextChunk(20, 10);
        assertTrue(pngChunkTypes(original).contains("tEXt"));

        byte[] stripped = ImageMetadataStripper.strip(original, "png");

        List<String> chunks = pngChunkTypes(stripped);
        for (String textChunk : List.of("tEXt", "zTXt", "iTXt", "eXIf")) {
            assertFalse(chunks.contains(textChunk), "the stored copy still carries a " + textChunk + " chunk: " + chunks);
        }
        assertFalse(containsAscii(stripped, PNG_TEXT));
        assertNotNull(ImageIO.read(new ByteArrayInputStream(stripped)));
    }

    @Test
    void aTransparentImageSavedAsJpegIsFlattenedInsteadOfStoredEmpty() throws IOException {
        byte[] stripped = ImageMetadataStripper.strip(pngWithAlpha(30, 20), "jpeg");

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(stripped));
        assertNotNull(decoded, "an empty byte array would be stored as the evidence");
        assertEquals(30, decoded.getWidth());
    }

    @Test
    void bytesThatAreNotAnImageAreRefused() {
        assertThrows(IOException.class,
                () -> ImageMetadataStripper.strip("not an image".getBytes(StandardCharsets.US_ASCII), "png"));
        assertThrows(IOException.class, () -> ImageMetadataStripper.strip(new byte[0], "png"));
    }

    @Test
    void onlyJpegAndPngHaveAFormatTheStripperCanWrite() {
        assertEquals("jpeg", ImageMetadataStripper.formatOfContentType("image/jpeg"));
        assertEquals("png", ImageMetadataStripper.formatOfContentType("IMAGE/PNG"));
        assertNull(ImageMetadataStripper.formatOfContentType("image/webp"));
        assertNull(ImageMetadataStripper.formatOfContentType("application/pdf"));
        assertNull(ImageMetadataStripper.formatOfContentType(null));
    }
}
