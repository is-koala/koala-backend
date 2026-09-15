package com.exskylab.koala.core.helpers;

import java.awt.Graphics;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

public final class ExifOrientation {

    private static final byte[] EXIF_HEADER = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
    private static final int TAG_ORIENTATION = 0x0112;
    private static final int TYPE_SHORT = 3;
    private static final int TYPE_LONG = 4;

    private ExifOrientation() {
    }

    public static int read(byte[] jpeg) {
        if (jpeg == null || jpeg.length < 4) {
            return 1;
        }
        if ((jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            return 1;
        }

        int index = 2;
        while (index + 4 <= jpeg.length) {
            if ((jpeg[index] & 0xFF) != 0xFF) {
                index++;
                continue;
            }
            while (index < jpeg.length && (jpeg[index] & 0xFF) == 0xFF) {
                index++;
            }
            if (index >= jpeg.length) {
                break;
            }
            int marker = jpeg[index] & 0xFF;
            index++;
            if (marker == 0xD8 || marker == 0xD9 || (marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
                continue;
            }
            if (index + 2 > jpeg.length) {
                break;
            }
            int length = ((jpeg[index] & 0xFF) << 8) | (jpeg[index + 1] & 0xFF);
            if (length < 2 || index + length > jpeg.length) {
                break;
            }
            if (marker == 0xE1) {
                int orientation = readApp1(jpeg, index + 2, length - 2);
                if (orientation != 1) {
                    return orientation;
                }
            }
            if (marker == 0xDA) {
                break;
            }
            index += length;
        }
        return 1;
    }

    public static BufferedImage applyToPixels(BufferedImage source, int orientation) {
        if (source == null || orientation < 2 || orientation > 8) {
            return source;
        }

        BufferedImage rgb = toRgb(source);
        int width = rgb.getWidth();
        int height = rgb.getHeight();
        boolean swapped = orientation >= 5;
        BufferedImage destination = new BufferedImage(
                swapped ? height : width,
                swapped ? width : height,
                BufferedImage.TYPE_INT_RGB);
        AffineTransform transform = transformFor(orientation, width, height);
        AffineTransformOp operation = new AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return operation.filter(rgb, destination);
    }

    private static int readApp1(byte[] jpeg, int payloadStart, int payloadLength) {
        if (payloadLength < EXIF_HEADER.length + 8) {
            return 1;
        }
        for (int i = 0; i < EXIF_HEADER.length; i++) {
            if (jpeg[payloadStart + i] != EXIF_HEADER[i]) {
                return 1;
            }
        }
        return readTiffOrientation(jpeg, payloadStart + EXIF_HEADER.length, payloadLength - EXIF_HEADER.length);
    }

    private static int readTiffOrientation(byte[] data, int tiffStart, int tiffLength) {
        if (tiffLength < 8) {
            return 1;
        }
        int b0 = data[tiffStart] & 0xFF;
        int b1 = data[tiffStart + 1] & 0xFF;
        boolean littleEndian;
        if (b0 == 'I' && b1 == 'I') {
            littleEndian = true;
        } else if (b0 == 'M' && b1 == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }
        int magic = u16(data, tiffStart + 2, littleEndian);
        if (magic != 42) {
            return 1;
        }
        long ifdOffset = u32(data, tiffStart + 4, littleEndian);
        if (ifdOffset < 8 || ifdOffset > tiffLength - 2) {
            return 1;
        }
        int ifd = tiffStart + (int) ifdOffset;
        int entries = u16(data, ifd, littleEndian);
        int entryStart = ifd + 2;
        for (int i = 0; i < entries; i++) {
            int entry = entryStart + i * 12;
            if (entry + 12 > tiffStart + tiffLength) {
                break;
            }
            int tag = u16(data, entry, littleEndian);
            if (tag != TAG_ORIENTATION) {
                continue;
            }
            int type = u16(data, entry + 2, littleEndian);
            long count = u32(data, entry + 4, littleEndian);
            if (count != 1) {
                return 1;
            }
            int value;
            if (type == TYPE_SHORT) {
                value = u16(data, entry + 8, littleEndian);
            } else if (type == TYPE_LONG) {
                value = (int) u32(data, entry + 8, littleEndian);
            } else {
                return 1;
            }
            if (value >= 1 && value <= 8) {
                return value;
            }
            return 1;
        }
        return 1;
    }

    private static AffineTransform transformFor(int orientation, int width, int height) {
        AffineTransform transform = new AffineTransform();
        switch (orientation) {
            case 2 -> {
                transform.scale(-1.0, 1.0);
                transform.translate(-width, 0);
            }
            case 3 -> {
                transform.translate(width, height);
                transform.rotate(Math.PI);
            }
            case 4 -> {
                transform.scale(1.0, -1.0);
                transform.translate(0, -height);
            }
            case 5 -> {
                transform.rotate(-Math.PI / 2);
                transform.scale(-1.0, 1.0);
            }
            case 6 -> {
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
            }
            case 7 -> {
                transform.scale(-1.0, 1.0);
                transform.translate(-height, 0);
                transform.translate(0, width);
                transform.rotate(3 * Math.PI / 2);
            }
            case 8 -> {
                transform.translate(0, width);
                transform.rotate(3 * Math.PI / 2);
            }
            default -> {
            }
        }
        return transform;
    }

    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics graphics = rgb.getGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return rgb;
    }

    private static int u16(byte[] data, int offset, boolean littleEndian) {
        int a = data[offset] & 0xFF;
        int b = data[offset + 1] & 0xFF;
        return littleEndian ? a | (b << 8) : (a << 8) | b;
    }

    private static long u32(byte[] data, int offset, boolean littleEndian) {
        long a = data[offset] & 0xFF;
        long b = data[offset + 1] & 0xFF;
        long c = data[offset + 2] & 0xFF;
        long d = data[offset + 3] & 0xFF;
        return littleEndian ? a | (b << 8) | (c << 16) | (d << 24)
                : (a << 24) | (b << 16) | (c << 8) | d;
    }
}
