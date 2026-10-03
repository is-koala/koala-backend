package com.exskylab.koala.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

public final class ImageMetadataFixtures {

    public static final String CAMERA_MAKE = "KoalaCam";
    public static final String BODY_SERIAL = "BODY-SN-4711";
    public static final String LENS_SERIAL = "LENS-SN-0815";
    public static final String IMAGE_UNIQUE_ID = "UNIQUE-ID-31337";
    public static final String PNG_TEXT = "Ev adresi Moda Caddesi 12";

    private static final int ASCII = 2;
    private static final int LONG = 4;
    private static final int RATIONAL = 5;

    private ImageMetadataFixtures() {
    }

    public static byte[] jpegWithLocationAndSerials(int width, int height) throws IOException {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(picture(width, height, BufferedImage.TYPE_INT_RGB), "jpg", jpeg);
        byte[] plain = jpeg.toByteArray();
        byte[] app1 = app1(exifTiff());
        byte[] withExif = new byte[plain.length + app1.length];
        withExif[0] = plain[0];
        withExif[1] = plain[1];
        System.arraycopy(app1, 0, withExif, 2, app1.length);
        System.arraycopy(plain, 2, withExif, 2 + app1.length, plain.length - 2);
        return withExif;
    }

    public static byte[] pngWithTextChunk(int width, int height) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(picture(width, height, BufferedImage.TYPE_INT_RGB), "png", png);
        byte[] plain = png.toByteArray();
        int afterHeader = 8 + 4 + 4 + 13 + 4;
        byte[] text = chunk("tEXt", ("Comment\0" + PNG_TEXT).getBytes(StandardCharsets.ISO_8859_1));
        byte[] withText = new byte[plain.length + text.length];
        System.arraycopy(plain, 0, withText, 0, afterHeader);
        System.arraycopy(text, 0, withText, afterHeader, text.length);
        System.arraycopy(plain, afterHeader, withText, afterHeader + text.length, plain.length - afterHeader);
        return withText;
    }

    public static byte[] pngWithAlpha(int width, int height) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(picture(width, height, BufferedImage.TYPE_INT_ARGB), "png", png);
        return png.toByteArray();
    }

    public static boolean containsAscii(byte[] haystack, String needle) {
        byte[] target = needle.getBytes(StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i <= haystack.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (haystack[i + j] != target[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    public static List<String> pngChunkTypes(byte[] png) {
        List<String> types = new ArrayList<>();
        int offset = 8;
        while (offset + 8 <= png.length) {
            int length = ByteBuffer.wrap(png, offset, 4).getInt();
            types.add(new String(png, offset + 4, 4, StandardCharsets.ISO_8859_1));
            offset += 12 + length;
        }
        return types;
    }

    private static BufferedImage picture(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(30, 140, 90, type == BufferedImage.TYPE_INT_ARGB ? 120 : 255));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private static byte[] exifTiff() {
        int ifd0At = 8;
        List<Entry> exifEntries = List.of(
                ascii(0xA420, IMAGE_UNIQUE_ID), ascii(0xA431, BODY_SERIAL), ascii(0xA435, LENS_SERIAL));
        List<Entry> gpsEntries = List.of(
                ascii(0x0001, "N"), rationals(0x0002, 41, 0, 30), ascii(0x0003, "E"), rationals(0x0004, 29, 1, 45));
        int ifd0Length = ifd(List.of(ascii(0x010F, CAMERA_MAKE), longValue(0x8769, 0), longValue(0x8825, 0)),
                ifd0At).length;
        int exifAt = ifd0At + ifd0Length;
        int exifLength = ifd(exifEntries, exifAt).length;
        int gpsAt = exifAt + exifLength;

        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.writeBytes(new byte[]{'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00});
        tiff.writeBytes(ifd(List.of(ascii(0x010F, CAMERA_MAKE), longValue(0x8769, exifAt),
                longValue(0x8825, gpsAt)), ifd0At));
        tiff.writeBytes(ifd(exifEntries, exifAt));
        tiff.writeBytes(ifd(gpsEntries, gpsAt));
        return tiff.toByteArray();
    }

    private record Entry(int tag, int type, int count, byte[] value) {
    }

    private static Entry ascii(int tag, String text) {
        byte[] value = (text + "\0").getBytes(StandardCharsets.US_ASCII);
        return new Entry(tag, ASCII, value.length, value);
    }

    private static Entry longValue(int tag, int value) {
        return new Entry(tag, LONG, 1, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }

    private static Entry rationals(int tag, int... numerators) {
        ByteBuffer buffer = ByteBuffer.allocate(numerators.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (int numerator : numerators) {
            buffer.putInt(numerator).putInt(1);
        }
        return new Entry(tag, RATIONAL, numerators.length, buffer.array());
    }

    private static byte[] ifd(List<Entry> entries, int start) {
        int dataAt = start + 2 + entries.size() * 12 + 4;
        ByteBuffer directory = ByteBuffer.allocate(2 + entries.size() * 12 + 4).order(ByteOrder.LITTLE_ENDIAN);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        directory.putShort((short) entries.size());
        for (Entry entry : entries) {
            directory.putShort((short) entry.tag()).putShort((short) entry.type()).putInt(entry.count());
            if (entry.value().length <= 4) {
                directory.put(Arrays.copyOf(entry.value(), 4));
            } else {
                directory.putInt(dataAt + data.size());
                data.writeBytes(entry.value());
                if (data.size() % 2 == 1) {
                    data.write(0);
                }
            }
        }
        directory.putInt(0);
        ByteArrayOutputStream ifd = new ByteArrayOutputStream();
        ifd.writeBytes(directory.array());
        ifd.writeBytes(data.toByteArray());
        return ifd.toByteArray();
    }

    private static byte[] app1(byte[] tiff) {
        byte[] identifier = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int length = 2 + identifier.length + tiff.length;
        ByteBuffer segment = ByteBuffer.allocate(2 + length);
        segment.put((byte) 0xFF).put((byte) 0xE1).putShort((short) length).put(identifier).put(tiff);
        return segment.array();
    }

    private static byte[] chunk(String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        ByteBuffer chunk = ByteBuffer.allocate(12 + data.length);
        chunk.putInt(data.length).put(typeBytes).put(data).putInt((int) crc.getValue());
        return chunk.array();
    }
}
