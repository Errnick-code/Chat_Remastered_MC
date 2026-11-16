package dev.errnick.chatremastered.client;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Locale;

import javax.imageio.ImageIO;

public final class ClipboardImageReader {

    private ClipboardImageReader() {
    }

    public static boolean hasImage() {
        if (isMac()) {
            return macHasImage();
        }
        try {
            return Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(DataFlavor.imageFlavor);
        } catch (Throwable t) {
            return false;
        }
    }

    public static byte[] readImageFromClipboardNative() {
        if (isMac()) {
            return readClipboardMac();
        }
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (!clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                return null;
            }
            Object data = clipboard.getData(DataFlavor.imageFlavor);
            if (!(data instanceof BufferedImage image)) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static boolean macHasImage() {
        try {
            String script = "try\n  set x to the clipboard as «class PNGf»\n  return \"ok\"\non error\n  return \"no\"\nend try";
            Process proc = new ProcessBuilder("osascript", "-e", script).redirectErrorStream(true).start();
            String result = new String(proc.getInputStream().readAllBytes()).trim();
            proc.waitFor();
            return result.equals("ok");
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] readClipboardMac() {
        try {
            File tmp = File.createTempFile("chat-remastered-clip-", ".png");
            tmp.deleteOnExit();
            String script = "set filePath to \"" + tmp.getAbsolutePath() + "\"\n"
                    + "try\n"
                    + "    set theImage to the clipboard as «class PNGf»\n"
                    + "    set fileRef to open for access POSIX file filePath with write permission\n"
                    + "    write theImage to fileRef\n"
                    + "    close access fileRef\n"
                    + "on error\n"
                    + "    error \"no image\"\n"
                    + "end try";
            Process proc = new ProcessBuilder("osascript", "-e", script).redirectErrorStream(true).start();
            int exit = proc.waitFor();
            if (exit != 0 || !tmp.exists() || tmp.length() == 0L) {
                return null;
            }
            return Files.readAllBytes(tmp.toPath());
        } catch (Exception e) {
            return null;
        }
    }
}
