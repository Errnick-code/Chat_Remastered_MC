package dev.errnicraft.chatremastered;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class GifDecoder {

    public static final class GifFrame {
        private final BufferedImage image;
        private final int delayMs;

        public GifFrame(BufferedImage image, int delayMs) {
            this.image = image;
            this.delayMs = delayMs;
        }

        public BufferedImage getImage() {
            return image;
        }

        public int getDelayMs() {
            return delayMs;
        }
    }

    private GifDecoder() {
    }

    public static List<GifFrame> decode(byte[] data) {
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return Collections.emptyList();
            }
            ImageReader reader = readers.next();

            MemoryCacheImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(data));
            reader.setInput(iis);

            int count = reader.getNumImages(true);
            if (count == 0) {
                return Collections.emptyList();
            }

            List<GifFrame> frames = new ArrayList<>();

            BufferedImage firstRaw = reader.read(0);
            int gifW = firstRaw.getWidth();
            int gifH = firstRaw.getHeight();

            BufferedImage canvas = new BufferedImage(gifW, gifH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D canvasG = canvas.createGraphics();

            for (int i = 0; i < count; i++) {
                BufferedImage rawFrame = (i == 0) ? firstRaw : reader.read(i);

                IIOMetadata meta = reader.getImageMetadata(i);
                Node root = meta.getAsTree("javax_imageio_gif_image_1.0");

                int delayMs = 100;
                String disposalMethod = "doNotDispose";
                int frameX = 0;
                int frameY = 0;

                NodeList children = root.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node node = children.item(j);
                    switch (node.getNodeName()) {
                        case "GraphicControlExtension" -> {
                            Node delayAttr = node.getAttributes().getNamedItem("delayTime");
                            if (delayAttr != null) {
                                int raw = parseIntOrDefault(delayAttr.getNodeValue(), 10);
                                delayMs = (raw < 2 ? 10 : raw) * 10;
                            }
                            Node dispAttr = node.getAttributes().getNamedItem("disposalMethod");
                            if (dispAttr != null) {
                                disposalMethod = dispAttr.getNodeValue();
                            }
                        }
                        case "ImageDescriptor" -> {
                            Node leftAttr = node.getAttributes().getNamedItem("imageLeftPosition");
                            Node topAttr = node.getAttributes().getNamedItem("imageTopPosition");
                            frameX = leftAttr != null ? parseIntOrDefault(leftAttr.getNodeValue(), 0) : 0;
                            frameY = topAttr != null ? parseIntOrDefault(topAttr.getNodeValue(), 0) : 0;
                        }
                        default -> {
                        }
                    }
                }

                canvasG.drawImage(rawFrame, frameX, frameY, null);

                BufferedImage snapshot = new BufferedImage(gifW, gifH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D snapG = snapshot.createGraphics();
                snapG.drawImage(canvas, 0, 0, null);
                snapG.dispose();

                frames.add(new GifFrame(snapshot, delayMs));

                if ("restoreToBackgroundColor".equals(disposalMethod)) {

                    Composite composite = canvasG.getComposite();
                    canvasG.setComposite(AlphaComposite.Clear);
                    canvasG.fillRect(frameX, frameY, rawFrame.getWidth(), rawFrame.getHeight());
                    canvasG.setComposite(composite);
                }

            }

            canvasG.dispose();
            reader.dispose();
            return frames;
        } catch (Exception e) {
            System.out.println("[Chat Remastered] GifDecoder error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private static int parseIntOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean isGif(byte[] data) {
        return data.length >= 6
                && data[0] == (byte) 'G'
                && data[1] == (byte) 'I'
                && data[2] == (byte) 'F'
                && data[3] == (byte) '8';
    }
}
