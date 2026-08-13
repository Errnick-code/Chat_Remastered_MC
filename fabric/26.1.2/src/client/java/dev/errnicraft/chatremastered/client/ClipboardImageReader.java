package dev.errnicraft.chatremastered.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

public final class ClipboardImageReader {

    private ClipboardImageReader() {
    }

    public static byte[] readImageFromClipboardNative() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return readClipboardWindows();
        } else if (os.contains("mac")) {
            return readClipboardMac();
        } else {
            return readClipboardLinux();
        }
    }

    private static byte[] readClipboardWindows() {
        try {

            File tmp = File.createTempFile("chat-remastered-clip-", ".png");
            tmp.deleteOnExit();
            String escapedPath = tmp.getAbsolutePath().replace("\\", "\\\\");
            String script = "Add-Type -AssemblyName System.Windows.Forms;\n"
                    + "$img = [System.Windows.Forms.Clipboard]::GetImage();\n"
                    + "if ($img -eq $null) { exit 1 }\n"
                    + "$img.Save('" + escapedPath + "', [System.Drawing.Imaging.ImageFormat]::Png);\n"
                    + "exit 0";
            Process proc = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(true).start();
            int exit = proc.waitFor();
            if (exit != 0 || !tmp.exists() || tmp.length() == 0L) {
                return null;
            }
            return Files.readAllBytes(tmp.toPath());
        } catch (Exception e) {
            return null;
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

    private static byte[] readClipboardLinux() {

        List<List<String>> tools = List.of(
                List.of("xclip", "-selection", "clipboard", "-t", "image/png", "-o"),
                List.of("xsel", "--clipboard", "--output")
        );
        for (List<String> cmd : tools) {
            try {
                Process proc = new ProcessBuilder(cmd).redirectErrorStream(false).start();
                byte[] bytes = proc.getInputStream().readAllBytes();
                proc.waitFor();
                if (bytes.length > 4 && bytes[0] == (byte) 0x89 && bytes[1] == 'P') {
                    return bytes;
                }
            } catch (IOException | InterruptedException ignored) {
            }
        }
        return null;
    }
}
