package dev.errnicraft.chatremastered.client;

import dev.errnicraft.chatremastered.ChatRemasteredConfig;
import dev.errnicraft.chatremastered.ChatTimeHolder;
import dev.errnicraft.chatremastered.ChatRemasteredStore;
import dev.errnicraft.chatremastered.GifDecoder;
import dev.errnicraft.chatremastered.ImageCache;
import dev.errnicraft.chatremastered.IntPair;
import dev.errnicraft.chatremastered.ImageDiskCache;
import dev.errnicraft.chatremastered.TcpImageClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.function.Consumer;

public final class ChatMessageRenderer {

    private static final int FETCH_MAX_ATTEMPTS = 4;
    private static final long FETCH_RETRY_DELAY_MS = 4000L;

    private ChatMessageRenderer() {
    }

    private static Component resolveSenderComponent(Minecraft mc, String sender, Component senderComponent) {
        Component raw = null;
        if (senderComponent != null && !senderComponent.getString().isEmpty()) {
            raw = senderComponent;
        }
        if (raw == null && mc.getConnection() != null) {
            for (var info : mc.getConnection().getOnlinePlayers()) {
                if (info.getProfile().getName().equals(sender)) {
                    raw = info.getTabListDisplayName();
                    break;
                }
            }
        }
        if (raw == null) {
            raw = Component.literal(sender);
        }
        return NickFormatting.parseLegacyNick(raw);
    }

    private static net.minecraft.network.chat.Style resolveBracketStyle(Component senderComp) {
        var rootStyle = senderComp.getStyle();
        if (rootStyle.getColor() != null) {
            return rootStyle;
        }
        for (Component sib : senderComp.getSiblings()) {
            var sibStyle = sib.getStyle();
            if (sibStyle.getColor() != null) {
                return sibStyle;
            }
        }
        return rootStyle;
    }

    public static void addReplyToChat(Minecraft mc, String sender, String text, Component senderComponent,
                                       String replyToSender, String replyToText, String replyToImageId) {
        Component senderComp = resolveSenderComponent(mc, sender, senderComponent);
        var bracketStyle = resolveBracketStyle(senderComp);

        MutableComponent msgText = MutableComponent.create(PlainTextContents.EMPTY);
        msgText.append(Component.literal("\n"));
        msgText.append(Component.literal("<").withStyle(bracketStyle));
        msgText.append(senderComp);
        msgText.append(Component.literal(">").withStyle(bracketStyle));
        msgText.append(Component.literal(" " + text));

        mc.gui.getChat().addMessage(msgText);
        int addedTime = ChatTimeHolder.lastAddedTime;

        ChatRemasteredStore.markSuppressReplyMessage(sender, text);
        ChatRemasteredStore.addReply(sender, text, replyToSender, replyToText, replyToImageId, senderComp, addedTime);
    }

    public static void addImageReplyToChat(Minecraft mc, String imageId, String sender, String caption,
                                            int width, int height, Component senderComponent,
                                            String replyToSender, String replyToText, String replyToImageId) {
        addImageReplyToChat(mc, imageId, sender, caption, width, height, senderComponent,
                replyToSender, replyToText, replyToImageId, "");
    }

    public static void addImageReplyToChat(Minecraft mc, String imageId, String sender, String caption,
                                            int width, int height, Component senderComponent,
                                            String replyToSender, String replyToText, String replyToImageId,
                                            String groupId) {
        Component senderComp = resolveSenderComponent(mc, sender, senderComponent);
        String captionSafe = caption != null ? caption : "";

        addImageToChat(mc, imageId, sender, caption, width, height, senderComponent, true, groupId);
        int addedTime = -1;
        for (var m : ChatRemasteredStore.getMessageList()) {
            if (m.getImageId().equals(imageId)) {
                addedTime = m.getAddedTime();
            }
        }

        ChatRemasteredStore.addReply(sender, captionSafe, replyToSender, replyToText, replyToImageId, senderComp, addedTime);
    }

    public static void addImageToChat(Minecraft mc, String imageId, String sender, String caption, int width, int height) {
        addImageToChat(mc, imageId, sender, caption, width, height, null, false);
    }

    public static void addImageToChat(Minecraft mc, String imageId, String sender, String caption, int width, int height,
                                       Component senderComponent) {
        addImageToChat(mc, imageId, sender, caption, width, height, senderComponent, false);
    }

    public static void addImageToChat(Minecraft mc, String imageId, String sender, String caption, int width, int height,
                                       Component senderComponent, boolean extraReplyLine) {
        addImageToChat(mc, imageId, sender, caption, width, height, senderComponent, extraReplyLine, "");
    }

    public static void addImageToChat(Minecraft mc, String imageId, String sender, String caption, int width, int height,
                                       Component senderComponent, boolean extraReplyLine, String groupId) {

        Component senderComp = resolveSenderComponent(mc, sender, senderComponent);
        var bracketStyle = resolveBracketStyle(senderComp);

        int maxW = ChatRemasteredConfig.getPreviewMaxW();
        int maxH = ChatRemasteredConfig.getPreviewMaxH();
        double aspect = height > 0 ? (double) width / height : 16.0 / 9.0;
        int dispW;
        int dispH;
        if (aspect >= (double) maxW / maxH) {
            dispW = maxW;
            dispH = Math.max((int) (dispW / aspect), 1);
        } else {
            dispH = maxH;
            dispW = Math.max((int) (dispH * aspect), 1);
        }

        double chatLineSpacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int) (9.0 * (chatLineSpacing + 1.0));
        int extraLines = (int) Math.ceil((double) dispH / entryHeight);

        MutableComponent msgText = MutableComponent.create(PlainTextContents.EMPTY);
        msgText.append(Component.literal("<").withStyle(bracketStyle));
        msgText.append(senderComp);
        msgText.append(Component.literal(">").withStyle(bracketStyle));
        if (caption != null && !caption.isEmpty()) {
            msgText.append(Component.literal(" " + caption));
        }
        msgText.append(Component.literal(" §7[📷]"));

        int totalLines = extraReplyLine ? extraLines + 1 : extraLines;
        for (int i = 0; i < totalLines; i++) {
            msgText.append(Component.literal("\n"));
        }

        ImageCache.registerPlaceholder(imageId, dispW, dispH);
        mc.gui.getChat().addMessage(msgText);

        int addedTime = ChatTimeHolder.lastAddedTime;
        ChatRemasteredStore.ImageMessage created = ChatRemasteredStore.addMessageAndGet(
                imageId, sender, caption != null ? caption : "", addedTime, senderComp);
        if (groupId != null && !groupId.isEmpty()) {
            ChatRemasteredStore.registerGroupHead(groupId, created);
        }

        boolean isDebugImage = imageId.startsWith("debug_");
        if (isDebugImage) {
            return;
        }

        int finalDispW = dispW;
        int finalDispH = dispH;

        Thread thread = new Thread(() -> fetchFullImage(imageId, bytes -> {
            if (GifDecoder.isGif(bytes)) {
                int gifMaxDim = ChatRemasteredConfig.getGifMaxDimServer();
                byte[] scaledBytes = ClientImageUtils.scaleGifBytes(bytes, gifMaxDim);
                byte[] finalBytes = scaledBytes != null ? scaledBytes : bytes;
                ImageCache.storeFullData(imageId, finalBytes);
                IntPair size = ImageCache.getSize(imageId);
                int dw = size != null ? size.getFirst() : finalDispW;
                int dh = size != null ? size.getSecond() : finalDispH;
                ImageCache.loadGif(imageId, finalBytes, dw, dh);
            } else {
                ImageCache.loadAndUpgradeHiRes(imageId, bytes, null);
            }
        }));
        thread.setDaemon(true);
        thread.start();
    }

    public static void registerAndDownloadGroupedImage(String imageId, int width, int height) {
        int maxW = ChatRemasteredConfig.getPreviewMaxW();
        int maxH = ChatRemasteredConfig.getPreviewMaxH();
        double aspect = height > 0 ? (double) width / height : 16.0 / 9.0;
        int dispW;
        int dispH;
        if (aspect >= (double) maxW / maxH) {
            dispW = maxW;
            dispH = Math.max((int) (dispW / aspect), 1);
        } else {
            dispH = maxH;
            dispW = Math.max((int) (dispH * aspect), 1);
        }
        ImageCache.registerPlaceholder(imageId, dispW, dispH);

        if (imageId.startsWith("debug_")) {
            return;
        }
        Thread thread = new Thread(() -> fetchFullImage(imageId, bytes -> {
            if (GifDecoder.isGif(bytes)) {
                int gifMaxDim = ChatRemasteredConfig.getGifMaxDimServer();
                byte[] scaledBytes = ClientImageUtils.scaleGifBytes(bytes, gifMaxDim);
                byte[] finalBytes = scaledBytes != null ? scaledBytes : bytes;
                ImageCache.storeFullData(imageId, finalBytes);
                IntPair size = ImageCache.getSize(imageId);
                int dw = size != null ? size.getFirst() : dispW;
                int dh = size != null ? size.getSecond() : dispH;
                ImageCache.loadGif(imageId, finalBytes, dw, dh);
            } else {
                ImageCache.loadAndUpgradeHiRes(imageId, bytes, null);
            }
        }));
        thread.setDaemon(true);
        thread.start();
    }

    public static void fetchFullImage(String imageId, Consumer<byte[]> onReady) {
        fetchFullImage(imageId, onReady, 1);
    }

    private static void fetchFullImage(String imageId, Consumer<byte[]> onReady, int attempt) {
        if (imageId.startsWith("debug_")) {
            return;
        }
        if (ImageCache.isDeleted(imageId)) {
            return;
        }
        byte[] diskCached = ImageDiskCache.load(imageId);
        if (diskCached != null) {
            ImageCache.storeFullData(imageId, diskCached);
            onReady.accept(diskCached);
            return;
        }
        byte[] cached = ImageCache.getFullData(imageId);
        if (cached != null) {
            onReady.accept(cached);
            return;
        }
        Thread thread = new Thread(() -> {
            ImageCache.startDownload(imageId);
            long downloadStart = System.currentTimeMillis();
            byte[] bytes = TcpImageClient.getFull(imageId, p -> ImageCache.setDownloadProgress(imageId, p));
            if (bytes != null) {
                long elapsed = System.currentTimeMillis() - downloadStart;
                long minVisibleMs = 500L;
                if (elapsed < minVisibleMs) {
                    try {
                        Thread.sleep(minVisibleMs - elapsed);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                ImageCache.storeFullData(imageId, bytes);
                ImageDiskCache.save(imageId, bytes);
                ImageCache.finishDownload(imageId);
                onReady.accept(bytes);
                return;
            }
            ImageCache.resetDownload(imageId);
            if (attempt < FETCH_MAX_ATTEMPTS && !ImageCache.isDeleted(imageId)) {

                ImageCache.startDownload(imageId);
                System.out.println("[Chat Remastered] Download failed for " + imageId
                        + " (attempt " + attempt + "/" + FETCH_MAX_ATTEMPTS + ") — retrying in "
                        + FETCH_RETRY_DELAY_MS + "ms");
                try {
                    Thread.sleep(FETCH_RETRY_DELAY_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
                fetchFullImage(imageId, onReady, attempt + 1);
            } else {
                System.out.println("[Chat Remastered] Download failed for " + imageId + " after all retries — marking as error");
                Minecraft.getInstance().execute(() -> ImageCache.markError(imageId));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

}
