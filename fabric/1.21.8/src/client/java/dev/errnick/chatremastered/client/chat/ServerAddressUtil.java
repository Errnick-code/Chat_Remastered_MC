package dev.errnick.chatremastered.client.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerAddressUtil {

    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24}(?::\\d{1,5})?"
                    + "|(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?");

    private ServerAddressUtil() {
    }

    public static String findAddress(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = ADDRESS_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            int start = matcher.start();
            if (start > 0) {
                char prev = text.charAt(start - 1);
                if (Character.isLetterOrDigit(prev) || prev == '.' || prev == '-' || prev == '@' || prev == '/') {
                    continue;
                }
            }
            if (isValidAddress(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean isValidAddress(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        String host = address;
        String port = null;
        int colon = address.lastIndexOf(':');
        if (colon >= 0) {
            host = address.substring(0, colon);
            port = address.substring(colon + 1);
        }
        if (port != null) {
            if (port.isEmpty() || port.length() > 5) {
                return false;
            }
            for (int i = 0; i < port.length(); i++) {
                if (!Character.isDigit(port.charAt(i))) {
                    return false;
                }
            }
            int portValue = Integer.parseInt(port);
            if (portValue < 1 || portValue > 65535) {
                return false;
            }
        }
        return isValidIpv4(host) || isValidHostname(host);
    }

    public static void connect(Minecraft minecraft, Screen parent, String address) {
        ServerAddress serverAddress = ServerAddress.parseString(address);
        ServerData data = new ServerData(address, address, ServerData.Type.OTHER);
        Screen safeParent = parent;
        if (parent instanceof ChatScreen) {
            safeParent = new JoinMultiplayerScreen(new TitleScreen());
        }
        if (minecraft.level != null) {
            if (minecraft.getSingleplayerServer() != null) {
                minecraft.getSingleplayerServer().halt(false);
            }
        }
        ConnectScreen.startConnecting(safeParent, minecraft, serverAddress, data, false, null);
    }

    private static boolean isValidIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidHostname(String host) {
        if (host.isEmpty() || host.length() > 253) {
            return false;
        }
        String[] labels = host.split("\\.");
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63) {
                return false;
            }
            if (label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
                return false;
            }
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '-') {
                    return false;
                }
            }
        }
        String tld = labels[labels.length - 1];
        if (tld.length() < 2) {
            return false;
        }
        for (int i = 0; i < tld.length(); i++) {
            char c = tld.charAt(i);
            if (c < 'a' || c > 'z') {
                return false;
            }
        }
        return true;
    }
}
