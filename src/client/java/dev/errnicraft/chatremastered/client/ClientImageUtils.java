package dev.errnicraft.chatremastered.client;

import dev.errnicraft.chatremastered.GifDecoder;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public final class ClientImageUtils {

    private ClientImageUtils() {
    }

    public static BufferedImage scaleImage(BufferedImage src, double scale, int type) {
        if (scale >= 1.0) {
            if (src.getType() == type) {
                return src;
            }
            BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), type);
            var g = c.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
            return c;
        }
        int nw = Math.max((int) (src.getWidth() * scale), 1);
        int nh = Math.max((int) (src.getHeight() * scale), 1);
        BufferedImage out = new BufferedImage(nw, nh, type);
        var g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    public static byte[] toPng(BufferedImage image) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        BufferedImage img = image;
        if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage c = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = c.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            img = c;
        }
        try {
            ImageIO.write(img, "png", bos);
        } catch (IOException ignored) {
        }
        return bos.toByteArray();
    }

    public static byte[] scaleGifBytes(byte[] data, int maxDim) {
        try {
            var frames = GifDecoder.decode(data);
            if (frames.isEmpty()) {
                return null;
            }
            int srcW = frames.get(0).getImage().getWidth();
            int srcH = frames.get(0).getImage().getHeight();
            if (srcW <= maxDim && srcH <= maxDim) {
                return null;
            }

            double scale = Math.min((double) maxDim / srcW, (double) maxDim / srcH);
            int targetW = Math.max((int) (srcW * scale), 1);
            int targetH = Math.max((int) (srcH * scale), 1);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
            if (!writers.hasNext()) {
                return null;
            }
            ImageWriter writer = writers.next();

            MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos);
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);

            for (GifDecoder.GifFrame frame : frames) {
                BufferedImage scaled = frame.getImage();
                if (scaled.getWidth() != targetW || scaled.getHeight() != targetH) {
                    BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
                    var g = out.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(frame.getImage(), 0, 0, targetW, targetH, null);
                    g.dispose();
                    scaled = out;
                }
                var writeParam = writer.getDefaultWriteParam();
                IIOImage iio = new IIOImage(scaled, null, null);
                writer.writeToSequence(iio, writeParam);
            }

            writer.endWriteSequence();
            ios.close();
            writer.dispose();

            return bos.toByteArray();
        } catch (Exception e) {
            System.out.println("[Chat Remastered] scaleGifBytes error: " + e.getMessage());
            return null;
        }
    }

    public static int[] readImageSizeFromHeader(File file) {
        try (InputStream stream = new FileInputStream(file)) {
            byte[] header = stream.readNBytes(26);
            if (header.length < 8) {
                return null;
            }

            if (header[0] == (byte) 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                if (header.length < 24) {
                    return null;
                }
                int w = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16) | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
                int h = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16) | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
                return (w > 0 && h > 0) ? new int[]{w, h} : null;
            }

            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8) {
                byte[] buf = readAllBytes(file);
                int[] result = scanJpegSof(buf);
                if (result != null) {
                    return result;
                }
            }

            if (header.length >= 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                byte[] buf = readAllBytes(file);
                int[] result = scanWebp(buf);
                if (result != null) {
                    return result;
                }
            }

            if (header.length >= 26 && header[0] == 'B' && header[1] == 'M') {
                int w = (header[18] & 0xFF) | ((header[19] & 0xFF) << 8) | ((header[20] & 0xFF) << 16) | ((header[21] & 0xFF) << 24);
                int h = Math.abs((header[22] & 0xFF) | ((header[23] & 0xFF) << 8) | ((header[24] & 0xFF) << 16) | ((header[25] & 0xFF) << 24));
                return (w > 0 && h > 0) ? new int[]{w, h} : null;
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static int[] readImageSizeFromBytes(byte[] bytes) {
        if (bytes.length < 8) {
            return null;
        }

        if (bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            if (bytes.length < 24) {
                return null;
            }
            int w = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16) | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
            int h = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16) | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
            return (w > 0 && h > 0) ? new int[]{w, h} : null;
        }

        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return scanJpegSof(bytes);
        }
        return null;
    }

    private static int[] scanJpegSof(byte[] buf) {
        int i = 2;
        while (i + 3 < buf.length) {
            if (buf[i] != (byte) 0xFF) {
                break;
            }
            int marker = buf[i + 1] & 0xFF;
            int segLen = ((buf[i + 2] & 0xFF) << 8) | (buf[i + 3] & 0xFF);
            if (isSofMarker(marker)) {
                if (i + 8 < buf.length) {
                    int h = ((buf[i + 5] & 0xFF) << 8) | (buf[i + 6] & 0xFF);
                    int w = ((buf[i + 7] & 0xFF) << 8) | (buf[i + 8] & 0xFF);
                    return (w > 0 && h > 0) ? new int[]{w, h} : null;
                }
            }
            i += 2 + segLen;
        }
        return null;
    }

    private static boolean isSofMarker(int marker) {
        return marker == 0xC0 || marker == 0xC1 || marker == 0xC2 || marker == 0xC3
                || marker == 0xC5 || marker == 0xC6 || marker == 0xC7
                || marker == 0xC9 || marker == 0xCA || marker == 0xCB;
    }

    private static int[] scanWebp(byte[] buf) {
        if (buf.length >= 30 && buf[12] == 'V' && buf[13] == 'P' && buf[14] == '8') {
            char sub = (char) (buf[15] & 0xFF);
            if (sub == ' ') {
                if (buf.length >= 30) {
                    int w = (buf[26] & 0xFF) | ((buf[27] & 0x3F) << 8);
                    int h = (buf[28] & 0xFF) | ((buf[29] & 0x3F) << 8);
                    return (w > 0 && h > 0) ? new int[]{w, h} : null;
                }
            } else if (sub == 'L') {
                if (buf.length >= 25) {
                    long bits = (buf[21] & 0xFFL) | ((buf[22] & 0xFFL) << 8) | ((buf[23] & 0xFFL) << 16) | ((buf[24] & 0xFFL) << 24);
                    int w = (int) ((bits & 0x3FFF) + 1);
                    int h = (int) (((bits >> 14) & 0x3FFF) + 1);
                    return (w > 0 && h > 0) ? new int[]{w, h} : null;
                }
            } else if (sub == 'X') {
                if (buf.length >= 30) {
                    int w = ((buf[24] & 0xFF) | ((buf[25] & 0xFF) << 8) | ((buf[26] & 0xFF) << 16)) + 1;
                    int h = ((buf[27] & 0xFF) | ((buf[28] & 0xFF) << 8) | ((buf[29] & 0xFF) << 16)) + 1;
                    return (w > 0 && h > 0) ? new int[]{w, h} : null;
                }
            }
        }
        return null;
    }

    private static byte[] readAllBytes(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return in.readAllBytes();
        }
    }

    public static BufferedImage readPng(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }
}
