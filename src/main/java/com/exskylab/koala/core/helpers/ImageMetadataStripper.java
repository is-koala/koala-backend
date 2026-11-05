package com.exskylab.koala.core.helpers;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

public final class ImageMetadataStripper {

    private ImageMetadataStripper() {
    }

    public static byte[] strip(byte[] original, String format) throws IOException {
        if (original == null || original.length == 0) {
            throw new IOException("Image is empty");
        }
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(original));
        if (decoded == null) {
            throw new IOException("Image could not be decoded");
        }
        BufferedImage upright = ExifOrientation.applyToPixels(decoded, ExifOrientation.read(original));
        String formatName = format.toLowerCase(Locale.ROOT);
        BufferedImage encodable = isJpeg(formatName) ? withoutAlpha(upright) : upright;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(encodable, formatName, out)) {
            throw new IOException("No image writer for " + formatName);
        }
        return out.toByteArray();
    }

    public static String formatOfContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpeg";
            case "image/png" -> "png";
            default -> null;
        };
    }

    private static boolean isJpeg(String formatName) {
        return "jpg".equals(formatName) || "jpeg".equals(formatName);
    }

    private static BufferedImage withoutAlpha(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.drawImage(image, 0, 0, Color.WHITE, null);
        graphics.dispose();
        return rgb;
    }
}
