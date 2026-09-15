package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.helpers.UploadValidator;
import com.exskylab.koala.core.mappers.ImageMapper;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.dataAccess.ImageDao;
import com.exskylab.koala.entities.Image;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageOrientationTest {

    private static final int STORED_WIDTH = 48;
    private static final int STORED_HEIGHT = 24;

    private StorageService storageService;
    private ImageManager imageManager;

    @BeforeEach
    void setUp() {
        ImageDao imageDao = mock(ImageDao.class);
        storageService = mock(StorageService.class);
        imageManager = new ImageManager(imageDao, storageService, mock(SecurityService.class),
                mock(ImageMapper.class), mock(UploadValidator.class));
        when(storageService.uploadFile(any(), any(), any(), eq(FolderType.IMAGE)))
                .thenReturn("images/photo.jpg");
        when(imageDao.save(any(Image.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aJpegWithExifOrientationSixIsStoredUpright() throws IOException {
        byte[] fixture = jpegWithExifOrientation(STORED_WIDTH, STORED_HEIGHT, 6);
        BufferedImage asImageIoSeesIt = ImageIO.read(new ByteArrayInputStream(fixture));
        assertEquals(STORED_WIDTH, asImageIoSeesIt.getWidth());
        assertEquals(STORED_HEIGHT, asImageIoSeesIt.getHeight());

        BufferedImage written = uploadAndRead(fixture, "photo.jpg", "image/jpeg");

        assertEquals(STORED_HEIGHT, written.getWidth());
        assertEquals(STORED_WIDTH, written.getHeight());
        assertTrue(isMostlyRed(written.getRGB(written.getWidth() / 2, 4)));
    }

    @Test
    void aJpegWithExifOrientationEightIsStoredUpright() throws IOException {
        byte[] fixture = jpegWithExifOrientation(STORED_WIDTH, STORED_HEIGHT, 8);

        BufferedImage written = uploadAndRead(fixture, "photo.jpg", "image/jpeg");

        assertEquals(STORED_HEIGHT, written.getWidth());
        assertEquals(STORED_WIDTH, written.getHeight());
        assertTrue(isMostlyRed(written.getRGB(written.getWidth() / 2, written.getHeight() - 5)));
    }

    @Test
    void anAlreadyUprightJpegKeepsItsDimensions() throws IOException {
        byte[] fixture = jpegWithExifOrientation(STORED_WIDTH, STORED_HEIGHT, 1);

        BufferedImage written = uploadAndRead(fixture, "photo.jpg", "image/jpeg");

        assertEquals(STORED_WIDTH, written.getWidth());
        assertEquals(STORED_HEIGHT, written.getHeight());
        assertTrue(isMostlyRed(written.getRGB(4, written.getHeight() / 2)));
    }

    @Test
    void aPngWithoutExifKeepsItsDimensions() throws IOException {
        BufferedImage source = landscapeWithRedOnTheLeft(STORED_WIDTH, STORED_HEIGHT);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(source, "png", png);

        BufferedImage written = uploadAndRead(png.toByteArray(), "photo.png", "image/png");

        assertEquals(STORED_WIDTH, written.getWidth());
        assertEquals(STORED_HEIGHT, written.getHeight());
        assertTrue(isMostlyRed(written.getRGB(4, written.getHeight() / 2)));
    }

    private BufferedImage uploadAndRead(byte[] bytes, String filename, String contentType) throws IOException {
        MockMultipartFile file = new MockMultipartFile("image", filename, contentType, bytes);
        imageManager.uploadImage(file);

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        verify(storageService).uploadFile(stored.capture(), any(), any(), eq(FolderType.IMAGE));
        return ImageIO.read(new ByteArrayInputStream(stored.getValue()));
    }

    static byte[] jpegWithExifOrientation(int width, int height, int orientation) throws IOException {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(landscapeWithRedOnTheLeft(width, height), "jpg", jpeg);
        return injectExifOrientation(jpeg.toByteArray(), orientation);
    }

    private static BufferedImage landscapeWithRedOnTheLeft(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width / 2, height);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(width / 2, 0, width - width / 2, height);
        graphics.dispose();
        return image;
    }

    private static boolean isMostlyRed(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int blue = rgb & 0xFF;
        return red > 180 && blue < 80;
    }

    private static byte[] injectExifOrientation(byte[] jpeg, int orientation) {
        if (jpeg.length < 2 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            throw new IllegalArgumentException("ImageIO did not produce a JPEG");
        }
        byte[] app1 = exifApp1(orientation);
        byte[] withExif = new byte[jpeg.length + app1.length];
        withExif[0] = jpeg[0];
        withExif[1] = jpeg[1];
        System.arraycopy(app1, 0, withExif, 2, app1.length);
        System.arraycopy(jpeg, 2, withExif, 2 + app1.length, jpeg.length - 2);
        return withExif;
    }

    private static byte[] exifApp1(int orientation) {
        byte[] tiff = {
                'I', 'I',
                0x2A, 0x00,
                0x08, 0x00, 0x00, 0x00,
                0x01, 0x00,
                0x12, 0x01,
                0x03, 0x00,
                0x01, 0x00, 0x00, 0x00,
                (byte) orientation, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };
        byte[] identifier = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int payloadLength = 2 + identifier.length + tiff.length;
        byte[] app1 = new byte[2 + payloadLength];
        app1[0] = (byte) 0xFF;
        app1[1] = (byte) 0xE1;
        app1[2] = (byte) (payloadLength >> 8);
        app1[3] = (byte) payloadLength;
        System.arraycopy(identifier, 0, app1, 4, identifier.length);
        System.arraycopy(tiff, 0, app1, 4 + identifier.length, tiff.length);
        return app1;
    }
}
