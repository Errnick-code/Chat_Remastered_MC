package dev.errnicraft.chatremastered.client;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ScreenshotMetadataWriter {

    private ScreenshotMetadataWriter() {
    }

    public static void writeMetadata(File pngFile, Map<String, String> fields) throws IOException {
        BufferedImage image = ImageIO.read(pngFile);
        if (image == null) {
            throw new IOException("Не удалось прочитать PNG для вшивания метаданных: " + pngFile);
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            throw new IOException("Нет доступного PNG writer'а в ImageIO");
        }
        ImageWriter writer = writers.next();

        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        IIOMetadata metadata = writer.getDefaultImageMetadata(
                new javax.imageio.ImageTypeSpecifier(image), writeParam);

        String nativeFormat = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = new IIOMetadataNode(nativeFormat);
        IIOMetadataNode textNode = new IIOMetadataNode("tEXt");

        for (Map.Entry<String, String> field : fields.entrySet()) {
            IIOMetadataNode entry = new IIOMetadataNode("tEXtEntry");
            entry.setAttribute("keyword", field.getKey());
            entry.setAttribute("value", field.getValue());
            textNode.appendChild(entry);
        }
        root.appendChild(textNode);
        metadata.mergeTree(nativeFormat, root);

        File tmpFile = File.createTempFile("cr_screenshot_", ".png", pngFile.getParentFile());
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(tmpFile)) {
            writer.setOutput(ios);
            writer.write(metadata, new IIOImage(image, null, metadata), writeParam);
        } finally {
            writer.dispose();
        }

        if (!tmpFile.renameTo(pngFile)) {

            java.nio.file.Files.copy(tmpFile.toPath(), pngFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            tmpFile.delete();
        }
    }

    public static Map<String, String> readMetadata(byte[] pngBytes) {
        Map<String, String> fields = new LinkedHashMap<>();
        try (ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(pngBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return fields;
            }
            ImageReader reader = readers.next();
            reader.setInput(iis);
            IIOMetadata metadata = reader.getImageMetadata(0);
            String nativeFormat = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(nativeFormat);
            NodeList textNodes = root.getElementsByTagName("tEXtEntry");
            for (int i = 0; i < textNodes.getLength(); i++) {
                org.w3c.dom.Node node = textNodes.item(i);
                NamedNodeMap attrs = node.getAttributes();
                if (attrs == null) continue;
                org.w3c.dom.Node keywordAttr = attrs.getNamedItem("keyword");
                org.w3c.dom.Node valueAttr = attrs.getNamedItem("value");
                if (keywordAttr != null && valueAttr != null) {
                    fields.put(keywordAttr.getNodeValue(), valueAttr.getNodeValue());
                }
            }
            reader.dispose();
        } catch (Exception e) {

        }
        return fields;
    }

    public static byte[] writeMetadata(byte[] pngBytes, Map<String, String> fields) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (image == null) {
            throw new IOException("Не удалось прочитать PNG для вшивания метаданных");
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            throw new IOException("Нет доступного PNG writer'а в ImageIO");
        }
        ImageWriter writer = writers.next();

        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        IIOMetadata metadata = writer.getDefaultImageMetadata(
                new javax.imageio.ImageTypeSpecifier(image), writeParam);

        String nativeFormat = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = new IIOMetadataNode(nativeFormat);
        IIOMetadataNode textNode = new IIOMetadataNode("tEXt");

        for (Map.Entry<String, String> field : fields.entrySet()) {
            IIOMetadataNode entry = new IIOMetadataNode("tEXtEntry");
            entry.setAttribute("keyword", field.getKey());
            entry.setAttribute("value", field.getValue());
            textNode.appendChild(entry);
        }
        root.appendChild(textNode);
        metadata.mergeTree(nativeFormat, root);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(metadata, new IIOImage(image, null, metadata), writeParam);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    public static Map<String, String> buildFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Author", ScreenshotMetadataCollector.getAuthorName());
        fields.put("Created", ScreenshotMetadataCollector.getTimestamp());

        String shader = ScreenshotMetadataCollector.getShaderPackName();
        fields.put("Shaderpack", shader != null ? shader : "none");

        java.util.List<String> packIds = ScreenshotMetadataCollector.getActiveResourcePackIds();
        fields.put("ResourcePacks", ScreenshotMetadataCollector.formatResourcePacksList(packIds));

        return fields;
    }
}
