package dev.errnicraft.chatremastered.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.errnicraft.chatremastered.ChatRemasteredConfig;
import dev.errnicraft.chatremastered.ChatRemasteredStore;
import dev.errnicraft.chatremastered.GifDecoder;
import dev.errnicraft.chatremastered.ImageCache;
import dev.errnicraft.chatremastered.ImageDiskCache;
import dev.errnicraft.chatremastered.PendingImageState;
import dev.errnicraft.chatremastered.SmoothDynamicTexture;
import dev.errnicraft.chatremastered.TcpImageClient;
import dev.errnicraft.chatremastered.network.packet.ImageUploadedPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ImageUploadFlow {

    private static final long MAX_FULL_BYTES = 8L * 1024 * 1024;

    private ImageUploadFlow() {
    }

    public static boolean canSendPhoto(Minecraft mc) {
        if (ChatRemasteredConfig.getServerHasModVersion() == null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.no_server_mod")));
            return false;
        }
        if (ChatRemasteredConfig.getBanned()) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.banned")));
            return false;
        }
        if (ChatRemasteredConfig.getMuted()) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.muted")));
            return false;
        }
        long cooldownMs = ChatRemasteredConfig.cooldownRemainingMs();
        if (cooldownMs > 0L) {
            long totalSec = (cooldownMs + 999L) / 1000L;
            String cooldownMsg;
            if (totalSec >= 60L) {
                long m = totalSec / 60L;
                long s = totalSec % 60L;
                cooldownMsg = ChatRemasteredConfig.tr("chat-remastered.cooldown_minutes", m, s);
            } else {
                cooldownMsg = ChatRemasteredConfig.tr("chat-remastered.cooldown_seconds", totalSec);
            }
            mc.gui.getChat().addClientSystemMessage(Component.literal("§e[Chat Remastered] " + cooldownMsg));
            return false;
        }
        if (ChatRemasteredConfig.getUploadToken().isEmpty()) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§e[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.handshake_wait")));
            return false;
        }
        if (!ChatRemasteredConfig.getServerReachable()) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.no_tcp")));
            return false;
        }
        return true;
    }

    public static void stageImage(File file) {
        Minecraft mc = Minecraft.getInstance();

        if (!canSendPhoto(mc)) {
            return;
        }

        int maxPhotos = Math.max(1, ChatRemasteredConfig.getMaxPhotosPerMessage());
        if (PendingImageState.size() >= maxPhotos) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§e[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.max_photos_reached", maxPhotos)));
            return;
        }

        int maxPW = ChatRemasteredConfig.getInputPreviewMaxW();
        int maxPH = ChatRemasteredConfig.getInputPreviewMaxH();

        int dispW0;
        int dispH0;
        if (maxPW * 9 <= maxPH * 16) {
            dispW0 = maxPW;
            dispH0 = Math.max((int) (maxPW * 9.0 / 16.0), 1);
        } else {
            dispH0 = maxPH;
            dispW0 = Math.max((int) (maxPH * 16.0 / 9.0), 1);
        }

        AtomicBoolean cancelToken = PendingImageState.newCancelToken();
        PendingImageState.PendingImage newItem = new PendingImageState.PendingImage(
                file, null, dispW0, dispH0, dispW0, dispH0,
                new byte[0], new byte[0], false, 0, 0, false);
        boolean added = PendingImageState.addPending(newItem, maxPhotos);
        if (!added) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§e[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.max_photos_reached", maxPhotos)));
            return;
        }
        long uid = newItem.getUid();
        PendingImageState.setProgressForUid(uid, -1f);

        Thread thread = new Thread(() -> {
            try {
                if (PendingImageState.isCancelled(cancelToken)) {
                    return;
                }

                int[] headerSize = ClientImageUtils.readImageSizeFromHeader(file);
                int origW = headerSize != null && headerSize[0] > 0 ? headerSize[0] : 1280;
                int origH = headerSize != null && headerSize[1] > 0 ? headerSize[1] : 720;
                double aspect = (double) origW / origH;
                int dispW;
                int dispH;
                if (aspect >= (double) maxPW / maxPH) {
                    dispW = maxPW;
                    dispH = Math.max((int) (maxPW / aspect), 1);
                } else {
                    dispH = maxPH;
                    dispW = Math.max((int) (maxPH * aspect), 1);
                }

                if (!PendingImageState.isCancelled(cancelToken)) {
                    int fDispW = dispW, fDispH = dispH, fOrigW = origW, fOrigH = origH;
                    mc.execute(() -> {
                        PendingImageState.PendingImage cur = PendingImageState.getByUid(uid);
                        if (cur != null) {
                            PendingImageState.replaceByUid(uid, withSize(cur, fDispW, fDispH, fOrigW, fOrigH, true));
                        }
                    });
                }
                PendingImageState.setProgressForUid(uid, 0.05f);

                if (PendingImageState.isCancelled(cancelToken)) {
                    return;
                }

                byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
                if (PendingImageState.isCancelled(cancelToken)) {
                    return;
                }
                PendingImageState.setProgressForUid(uid, 0.2f);

                boolean isGif = GifDecoder.isGif(fileBytes);

                if (isGif) {
                    handleGifStaging(mc, uid, fileBytes, dispW0, dispH0, cancelToken);
                    return;
                }

                handleImageStaging(mc, uid, file, fileBytes, dispW0, dispH0, cancelToken);
            } catch (Exception e) {
                mc.execute(() -> {
                    PendingImageState.removeByUid(uid);
                    mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.error", (Object) (e.getMessage() != null ? e.getMessage() : "?"))));
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static void handleGifStaging(Minecraft mc, long uid, byte[] fileBytes, int dispW0, int dispH0, AtomicBoolean cancelToken) {
        if (!ChatRemasteredConfig.getGifEnabled()) {
            mc.execute(() -> {
                PendingImageState.removeByUid(uid);
                mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.gif_disabled_server")));
            });
            return;
        }

        int sendMaxDim = ChatRemasteredConfig.getGifMaxDimServer();
        byte[] scaledGifBytes = ClientImageUtils.scaleGifBytes(fileBytes, sendMaxDim);
        byte[] rawBytes = scaledGifBytes != null ? scaledGifBytes : fileBytes;

        if (PendingImageState.isCancelled(cancelToken)) {
            return;
        }

        if (rawBytes.length > MAX_FULL_BYTES) {
            mc.execute(() -> {
                PendingImageState.removeByUid(uid);
                mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.file_too_large_compress")));
            });
            return;
        }

        mc.execute(() -> {
            PendingImageState.PendingImage cur = PendingImageState.getByUid(uid);
            if (cur != null) {
                PendingImageState.replaceByUid(uid, withRawBytes(cur, rawBytes, true));
            }
        });
        PendingImageState.setProgressForUid(uid, 0.5f);

        if (PendingImageState.isCancelled(cancelToken)) {
            return;
        }
        var frames = GifDecoder.decode(rawBytes);
        GifDecoder.GifFrame firstFrame = frames.isEmpty() ? null : frames.get(0);
        byte[] previewBytes;
        int previewTexW;
        int previewTexH;

        if (firstFrame != null) {
            float guiScale;
            try {
                guiScale = Math.max((float) mc.getWindow().getGuiScale(), 1f);
            } catch (Exception e) {
                guiScale = 1f;
            }
            float chatScaleVal = clamp((float) (double) mc.options.chatScale().get(), 0.01f, 1f);
            PendingImageState.PendingImage cur = PendingImageState.getByUid(uid);
            int texDispW = cur != null ? cur.getWidth() : dispW0;
            int texDispH = cur != null ? cur.getHeight() : dispH0;
            int targetTexW = Math.max((int) (texDispW * guiScale * chatScaleVal), 1);
            int targetTexH = Math.max((int) (texDispH * guiScale * chatScaleVal), 1);
            float s = Math.min((float) targetTexW / firstFrame.getImage().getWidth(),
                    (float) targetTexH / firstFrame.getImage().getHeight());
            s = Math.min(s, 1f);
            int fw = Math.max((int) (firstFrame.getImage().getWidth() * s), 1);
            int fh = Math.max((int) (firstFrame.getImage().getHeight() * s), 1);
            BufferedImage scaled = ClientImageUtils.scaleImage(firstFrame.getImage(), s, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                ImageIO.write(scaled, "png", bos);
            } catch (Exception ignored) {
            }
            previewBytes = bos.toByteArray();
            previewTexW = fw;
            previewTexH = fh;
        } else {
            previewBytes = new byte[0];
            previewTexW = dispW0;
            previewTexH = dispH0;
        }

        PendingImageState.setProgressForUid(uid, 0.9f);
        if (PendingImageState.isCancelled(cancelToken)) {
            return;
        }

        byte[] finalPreviewBytes = previewBytes;
        int finalPreviewTexW = previewTexW;
        int finalPreviewTexH = previewTexH;
        mc.execute(() -> {
            PendingImageState.PendingImage cur = PendingImageState.getByUid(uid);
            if (cur == null) {
                return;
            }
            try {
                if (finalPreviewBytes.length > 0) {
                    NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(finalPreviewBytes));
                    Identifier previewId = Identifier.fromNamespaceAndPath("chat-remastered", "preview_" + System.currentTimeMillis());
                    mc.getTextureManager().register(previewId, new SmoothDynamicTexture(() -> "chat-remastered-preview", nativeImage));
                    PendingImageState.updateTextureForUid(uid, previewId, finalPreviewTexW, finalPreviewTexH, finalPreviewBytes, cur.getRawBytes());
                } else {
                    PendingImageState.updateTextureForUid(uid,
                            Identifier.fromNamespaceAndPath("chat-remastered", "gif_pending"),
                            dispW0, dispH0, new byte[]{0}, cur.getRawBytes());
                }
                PendingImageState.setProgressForUid(uid, 1f);
            } catch (Exception e) {
                System.out.println("[Chat Remastered] GIF preview texture error: " + e.getMessage());
                PendingImageState.setProgressForUid(uid, 1f);
            }
        });
    }

    private static void handleImageStaging(Minecraft mc, long uid, File file, byte[] fileBytes, int dispW0, int dispH0, AtomicBoolean cancelToken) throws Exception {
        PendingImageState.setProgressForUid(uid, 0.3f);
        java.util.Map<String, String> sourceMetadata = ScreenshotMetadataWriter.readMetadata(fileBytes);
        BufferedImage original = ImageIO.read(file);
        if (original == null) {
            mc.execute(() -> {
                PendingImageState.removeByUid(uid);
                mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.cannot_read")));
            });
            return;
        }

        if (PendingImageState.isCancelled(cancelToken)) {
            return;
        }
        PendingImageState.setProgressForUid(uid, 0.4f);

        int maxDim = ChatRemasteredConfig.getMaxDim();
        double fullScale = (original.getWidth() > maxDim || original.getHeight() > maxDim)
                ? Math.min((double) maxDim / original.getWidth(), (double) maxDim / original.getHeight())
                : 1.0;

        BufferedImage fullScaled = ClientImageUtils.scaleImage(original, fullScale, BufferedImage.TYPE_INT_RGB);
        byte[] fullBytes = ClientImageUtils.toPng(fullScaled);

        java.util.Map<String, String> metaToWrite = sourceMetadata.isEmpty()
                ? java.util.Map.of("Author", ScreenshotMetadataCollector.getAuthorName(), "Created", ScreenshotMetadataCollector.getTimestamp())
                : sourceMetadata;
        try {
            fullBytes = ScreenshotMetadataWriter.writeMetadata(fullBytes, metaToWrite);
        } catch (Exception ignored) {

        }

        if (fullBytes.length > MAX_FULL_BYTES) {
            mc.execute(() -> {
                PendingImageState.removeByUid(uid);
                mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.file_too_large_compress")));
            });
            return;
        }

        if (PendingImageState.isCancelled(cancelToken)) {
            return;
        }
        PendingImageState.setProgressForUid(uid, 0.6f);

        int scaledOrigW = fullScaled.getWidth();
        int scaledOrigH = fullScaled.getHeight();
        byte[] finalFullBytes = fullBytes;
        mc.execute(() -> {
            PendingImageState.PendingImage cur = PendingImageState.getByUid(uid);
            if (cur != null) {
                PendingImageState.replaceByUid(uid, withRawBytesAndOrig(cur, finalFullBytes, true, scaledOrigW, scaledOrigH));
            }
        });

        float guiScale;
        try {
            guiScale = Math.max((float) mc.getWindow().getGuiScale(), 1f);
        } catch (Exception e) {
            guiScale = 1f;
        }
        float chatScaleVal = clamp((float) (double) mc.options.chatScale().get(), 0.01f, 1f);
        PendingImageState.PendingImage cur2 = PendingImageState.getByUid(uid);
        int texDispW = cur2 != null ? cur2.getWidth() : dispW0;
        int texDispH = cur2 != null ? cur2.getHeight() : dispH0;
        int targetTexW = Math.max((int) (texDispW * guiScale * chatScaleVal), 1);
        int targetTexH = Math.max((int) (texDispH * guiScale * chatScaleVal), 1);
        double previewScale = Math.min(
                (double) targetTexW / fullScaled.getWidth(),
                (double) targetTexH / fullScaled.getHeight());
        previewScale = Math.min(previewScale, 1.0);

        BufferedImage previewScaled = ClientImageUtils.scaleImage(fullScaled, previewScale, BufferedImage.TYPE_INT_ARGB);
        byte[] previewBytes = ClientImageUtils.toPng(previewScaled);

        PendingImageState.setProgressForUid(uid, 0.9f);
        if (PendingImageState.isCancelled(cancelToken)) {
            return;
        }

        mc.execute(() -> {
            PendingImageState.PendingImage cur = PendingImageState.getByUid(uid);
            if (cur == null) {
                return;
            }
            try {
                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(previewBytes));
                Identifier previewId = Identifier.fromNamespaceAndPath("chat-remastered", "preview_" + System.currentTimeMillis());
                mc.getTextureManager().register(previewId, new SmoothDynamicTexture(() -> "chat-remastered-preview", nativeImage));
                PendingImageState.updateTextureForUid(uid, previewId, previewScaled.getWidth(), previewScaled.getHeight(), previewBytes, cur.getRawBytes());
                PendingImageState.setProgressForUid(uid, 1f);
            } catch (Exception e) {
                System.out.println("[Chat Remastered] preview texture error: " + e.getMessage());
                PendingImageState.setProgressForUid(uid, 1f);
            }
        });
    }

    public static void sendPendingImage() {
        sendPendingImageWithCaption(null);
    }

    public static void sendPendingImageWithCaption(String caption) {
        sendPendingImageWithCaptionAndReply(caption, "", "", "");
    }

    public static void sendPendingImageWithCaptionAndReply(String caption, String replyToSender, String replyToText, String replyToImageId) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) {
            return;
        }
        List<PendingImageState.PendingImage> queue = PendingImageState.getAll();
        if (queue.isEmpty()) {
            return;
        }
        if (!canSendPhoto(mc)) {
            return;
        }

        PendingImageState.PendingImage lastImg = queue.get(queue.size() - 1);
        if (!lastImg.canSend()) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§e[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.image_loading_wait")));
            return;
        }
        for (PendingImageState.PendingImage img : queue) {
            if (!img.canSend()) {
                mc.gui.getChat().addClientSystemMessage(Component.literal("§e[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.image_loading_wait")));
                return;
            }
        }

        String sender = player.getGameProfile().name();
        Component rawComp = player.getDisplayName() != null ? player.getDisplayName() : Component.literal(sender);
        Component senderComp = NickFormatting.parseLegacyNick(rawComp);
        String token = ChatRemasteredConfig.getUploadToken();
        String captionSafe = caption != null ? caption : "";
        boolean isReply = !replyToSender.isEmpty() || !replyToImageId.isEmpty();

        PendingImageState.setPending(null);
        ChatRemasteredConfig.startCooldown();

        int groupCount = queue.size();
        String groupId = groupCount > 1
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 10)
                : "";

        for (int i = 0; i < queue.size(); i++) {
            PendingImageState.PendingImage pending = queue.get(i);
            String imageId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            byte[] fullBytes = pending.getRawBytes();
            int sendW = pending.getOrigWidth() > 0 ? pending.getOrigWidth() : 1280;
            int sendH = pending.getOrigHeight() > 0 ? pending.getOrigHeight() : 720;

            String itemCaption = (i == 0) ? captionSafe : "";
            String itemReplySender = (i == 0) ? replyToSender : "";
            String itemReplyText = (i == 0) ? replyToText : "";
            String itemReplyImageId = (i == 0) ? replyToImageId : "";

            ChatRemasteredStore.storeOriginalFile(imageId, pending.getFile());
            ImageCache.storeFullData(imageId, fullBytes);
            if (i == 0) {
                ChatRemasteredStore.markSuppressPhotoMessage(sender, itemCaption.isEmpty() ? null : itemCaption);
                ChatMessageRenderer.addImageToChat(mc, imageId, sender, itemCaption, sendW, sendH, senderComp, isReply, groupId);
                if (isReply) {
                    int addedTime = -1;
                    for (var m : ChatRemasteredStore.getMessageList()) {
                        if (m.getImageId().equals(imageId)) {
                            addedTime = m.getAddedTime();
                        }
                    }
                    ChatRemasteredStore.addReply(sender, itemCaption, replyToSender, replyToText, replyToImageId, senderComp, addedTime);
                }
            } else {

                ChatRemasteredStore.attachToGroup(groupId, imageId);
                ChatMessageRenderer.registerAndDownloadGroupedImage(imageId, sendW, sendH);
            }

            ClientPlayNetworking.send(new ImageUploadedPacket(imageId, sender, itemCaption, sendW, sendH,
                    itemReplySender, itemReplyText, itemReplyImageId, groupId, i, groupCount));

            uploadImageAsync(mc, imageId, token, fullBytes);
        }
    }

    private static void uploadImageAsync(Minecraft mc, String imageId, String token, byte[] fullBytes) {
        Thread thread = new Thread(() -> {
            ImageDiskCache.save(imageId, fullBytes);
            ImageCache.startUpload(imageId);
            long uploadStart = System.currentTimeMillis();
            String result = TcpImageClient.upload(imageId, token, fullBytes, p -> ImageCache.setUploadProgress(imageId, p));
            long elapsed = System.currentTimeMillis() - uploadStart;
            long minVisibleMs = 500L;
            if (elapsed < minVisibleMs) {
                try {
                    Thread.sleep(minVisibleMs - elapsed);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            ImageCache.finishUpload(imageId);
            switch (result) {
                case "ok" -> {  }
                case "forbidden" -> mc.execute(() ->
                        mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.banned"))));
                case "too_large" -> mc.execute(() ->
                        mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.too_large_upload"))));
                default -> mc.execute(() -> {
                    ChatRemasteredStore.markUploadErrorShown(imageId);
                    mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.upload_error", (Object) result)));
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public static void pasteImageFromClipboard() {
        Minecraft mc = Minecraft.getInstance();

        if (!canSendPhoto(mc)) {
            return;
        }

        int maxPhotos = Math.max(1, ChatRemasteredConfig.getMaxPhotosPerMessage());
        if (PendingImageState.size() >= maxPhotos) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§e[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.max_photos_reached", maxPhotos)));
            return;
        }

        int maxPW = ChatRemasteredConfig.getInputPreviewMaxW();
        int maxPH = ChatRemasteredConfig.getInputPreviewMaxH();
        int dispW0;
        int dispH0;
        if (maxPW * 9 <= maxPH * 16) {
            dispW0 = maxPW;
            dispH0 = Math.max((int) (maxPW * 9.0 / 16.0), 1);
        } else {
            dispH0 = maxPH;
            dispW0 = Math.max((int) (maxPH * 16.0 / 9.0), 1);
        }

        File placeholderFile = new File(System.getProperty("java.io.tmpdir"),
                "chat-remastered-clipboard-pending-" + System.nanoTime() + ".png");
        AtomicBoolean cancelToken = PendingImageState.newCancelToken();
        PendingImageState.PendingImage newItem = new PendingImageState.PendingImage(
                placeholderFile, null, dispW0, dispH0, dispW0, dispH0,
                new byte[0], new byte[0], false, 0, 0, false);
        boolean added = PendingImageState.addPending(newItem, maxPhotos);
        if (!added) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§e[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.max_photos_reached", maxPhotos)));
            return;
        }
        long uid = newItem.getUid();
        PendingImageState.setProgressForUid(uid, -1f);

        Thread thread = new Thread(() -> {
            try {
                if (PendingImageState.isCancelled(cancelToken)) {
                    return;
                }

                byte[] bytes = ClipboardImageReader.readImageFromClipboardNative();
                if (bytes == null || bytes.length == 0) {
                    mc.execute(() -> {
                        PendingImageState.removeByUid(uid);
                        mc.gui.getChat().addClientSystemMessage(Component.literal("§7[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.clipboard_empty")));
                    });
                    return;
                }

                if (PendingImageState.isCancelled(cancelToken)) {
                    return;
                }
                PendingImageState.setProgressForUid(uid, 0.15f);

                int[] headerSize = ClientImageUtils.readImageSizeFromBytes(bytes);
                int origW = headerSize != null && headerSize[0] > 0 ? headerSize[0] : 1280;
                int origH = headerSize != null && headerSize[1] > 0 ? headerSize[1] : 720;
                double aspect = (double) origW / origH;
                int dispW;
                int dispH;
                if (aspect >= (double) maxPW / maxPH) {
                    dispW = maxPW;
                    dispH = Math.max((int) (maxPW / aspect), 1);
                } else {
                    dispH = maxPH;
                    dispW = Math.max((int) (maxPH * aspect), 1);
                }

                int fDispW = dispW, fDispH = dispH, fOrigW = origW, fOrigH = origH;
                mc.execute(() -> {
                    PendingImageState.PendingImage cur = PendingImageState.getByUid(uid);
                    if (cur != null) {
                        PendingImageState.replaceByUid(uid, withSize(cur, fDispW, fDispH, fOrigW, fOrigH, true));
                    }
                });

                File tmpFile = File.createTempFile("chat-remastered-paste-", ".png");
                tmpFile.deleteOnExit();
                java.nio.file.Files.write(tmpFile.toPath(), bytes);

                mc.execute(() -> {
                    PendingImageState.PendingImage cur = PendingImageState.getByUid(uid);
                    if (cur != null) {
                        PendingImageState.replaceByUid(uid, withFileAndOrig(cur, tmpFile, fOrigW, fOrigH));
                    }
                });

                if (PendingImageState.isCancelled(cancelToken)) {
                    return;
                }
                PendingImageState.setProgressForUid(uid, 0.2f);

                processBytesForPending(mc, uid, bytes, dispW, dispH, cancelToken);

            } catch (Exception e) {
                System.out.println("[Chat Remastered] Clipboard paste error: " + e.getMessage());
                mc.execute(() -> {
                    PendingImageState.removeByUid(uid);
                    mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.clipboard_error", (Object) (e.getMessage() != null ? e.getMessage() : "?"))));
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static void processBytesForPending(Minecraft mc, long uid, byte[] bytes, int dispW, int dispH, AtomicBoolean cancelToken) {
        if (PendingImageState.isCancelled(cancelToken)) {
            return;
        }

        boolean isGif = GifDecoder.isGif(bytes);
        if (isGif) {
            if (!ChatRemasteredConfig.getGifEnabled()) {
                mc.execute(() -> {
                    PendingImageState.removeByUid(uid);
                    mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.gif_disabled_server")));
                });
                return;
            }
            int sendMaxDim = ChatRemasteredConfig.getGifMaxDimServer();
            byte[] scaledBytes = ClientImageUtils.scaleGifBytes(bytes, sendMaxDim);
            byte[] rawBytes = scaledBytes != null ? scaledBytes : bytes;
            if (rawBytes.length > MAX_FULL_BYTES) {
                mc.execute(() -> {
                    PendingImageState.removeByUid(uid);
                    mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.file_too_large_compress")));
                });
                return;
            }
            mc.execute(() -> {
                PendingImageState.PendingImage cur = PendingImageState.getByUid(uid);
                if (cur != null) {
                    PendingImageState.replaceByUid(uid, withRawBytes(cur, rawBytes, true));
                }
            });
            PendingImageState.setProgressForUid(uid, 0.5f);
            if (PendingImageState.isCancelled(cancelToken)) {
                return;
            }
            var frames = GifDecoder.decode(rawBytes);
            GifDecoder.GifFrame firstFrame = frames.isEmpty() ? null : frames.get(0);
            if (firstFrame != null) {
                float guiScale;
                try {
                    guiScale = Math.max((float) mc.getWindow().getGuiScale(), 1f);
                } catch (Exception e) {
                    guiScale = 1f;
                }
                float chatScaleVal = clamp((float) (double) mc.options.chatScale().get(), 0.01f, 1f);
                int targetTexW = Math.max((int) (dispW * guiScale * chatScaleVal), 1);
                int targetTexH = Math.max((int) (dispH * guiScale * chatScaleVal), 1);
                float s = Math.min((float) targetTexW / firstFrame.getImage().getWidth(),
                        (float) targetTexH / firstFrame.getImage().getHeight());
                s = Math.min(s, 1f);
                int fw = Math.max((int) (firstFrame.getImage().getWidth() * s), 1);
                int fh = Math.max((int) (firstFrame.getImage().getHeight() * s), 1);
                BufferedImage scaled = ClientImageUtils.scaleImage(firstFrame.getImage(), s, BufferedImage.TYPE_INT_ARGB);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try {
                    ImageIO.write(scaled, "png", bos);
                } catch (Exception ignored) {
                }
                byte[] previewBytes = bos.toByteArray();
                if (PendingImageState.isCancelled(cancelToken)) {
                    return;
                }
                mc.execute(() -> {
                    PendingImageState.PendingImage cur = PendingImageState.getByUid(uid);
                    if (cur == null) {
                        return;
                    }
                    try {
                        NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(previewBytes));
                        Identifier previewId = Identifier.fromNamespaceAndPath("chat-remastered", "preview_" + System.currentTimeMillis());
                        mc.getTextureManager().register(previewId, new SmoothDynamicTexture(() -> "chat-remastered-preview", nativeImage));
                        PendingImageState.updateTextureForUid(uid, previewId, fw, fh, previewBytes, rawBytes);
                        PendingImageState.setProgressForUid(uid, 1f);
                    } catch (Exception e) {
                        PendingImageState.setProgressForUid(uid, 1f);
                    }
                });
            } else {
                PendingImageState.setProgressForUid(uid, 1f);
            }
            return;
        }

        PendingImageState.setProgressForUid(uid, 0.3f);
        BufferedImage original;
        try {
            original = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            original = null;
        }
        if (original == null) {
            mc.execute(() -> {
                PendingImageState.removeByUid(uid);
                mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.cannot_read")));
            });
            return;
        }

        if (PendingImageState.isCancelled(cancelToken)) {
            return;
        }
        PendingImageState.setProgressForUid(uid, 0.45f);

        int maxDim = ChatRemasteredConfig.getMaxDim();
        double fullScale = (original.getWidth() > maxDim || original.getHeight() > maxDim)
                ? Math.min((double) maxDim / original.getWidth(), (double) maxDim / original.getHeight())
                : 1.0;
        BufferedImage fullScaled = ClientImageUtils.scaleImage(original, fullScale, BufferedImage.TYPE_INT_RGB);
        byte[] fullBytes = ClientImageUtils.toPng(fullScaled);
        try {
            fullBytes = ScreenshotMetadataWriter.writeMetadata(fullBytes, java.util.Map.of(
                    "Author", ScreenshotMetadataCollector.getAuthorName(),
                    "Created", ScreenshotMetadataCollector.getTimestamp()));
        } catch (Exception ignored) {

        }

        if (fullBytes.length > MAX_FULL_BYTES) {
            mc.execute(() -> {
                PendingImageState.removeByUid(uid);
                mc.gui.getChat().addClientSystemMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.file_too_large_compress")));
            });
            return;
        }

        if (PendingImageState.isCancelled(cancelToken)) {
            return;
        }
        PendingImageState.setProgressForUid(uid, 0.6f);

        int scaledW = fullScaled.getWidth();
        int scaledH = fullScaled.getHeight();
        byte[] finalFullBytes2 = fullBytes;
        mc.execute(() -> {
            PendingImageState.PendingImage cur = PendingImageState.getByUid(uid);
            if (cur != null) {
                PendingImageState.replaceByUid(uid, withRawBytesAndOrig(cur, finalFullBytes2, true, scaledW, scaledH));
            }
        });

        float guiScale;
        try {
            guiScale = Math.max((float) mc.getWindow().getGuiScale(), 1f);
        } catch (Exception e) {
            guiScale = 1f;
        }
        float chatScaleVal = clamp((float) (double) mc.options.chatScale().get(), 0.01f, 1f);
        int targetTexW = Math.max((int) (dispW * guiScale * chatScaleVal), 1);
        int targetTexH = Math.max((int) (dispH * guiScale * chatScaleVal), 1);
        double previewScale = Math.min(
                (double) targetTexW / fullScaled.getWidth(),
                (double) targetTexH / fullScaled.getHeight());
        previewScale = Math.min(previewScale, 1.0);
        BufferedImage previewScaled = ClientImageUtils.scaleImage(fullScaled, previewScale, BufferedImage.TYPE_INT_ARGB);
        byte[] previewBytes = ClientImageUtils.toPng(previewScaled);

        PendingImageState.setProgressForUid(uid, 0.9f);
        if (PendingImageState.isCancelled(cancelToken)) {
            return;
        }

        mc.execute(() -> {
            PendingImageState.PendingImage cur = PendingImageState.getByUid(uid);
            if (cur == null) {
                return;
            }
            try {
                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(previewBytes));
                Identifier previewId = Identifier.fromNamespaceAndPath("chat-remastered", "preview_" + System.currentTimeMillis());
                mc.getTextureManager().register(previewId, new SmoothDynamicTexture(() -> "chat-remastered-preview", nativeImage));
                PendingImageState.updateTextureForUid(uid, previewId, previewScaled.getWidth(), previewScaled.getHeight(), previewBytes, finalFullBytes2);
                PendingImageState.setProgressForUid(uid, 1f);
            } catch (Exception e) {
                PendingImageState.setProgressForUid(uid, 1f);
            }
        });
    }

    public static void saveImageAs(String imageId) {
        if (ImageCache.isDeleted(imageId)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ImageCache.DownloadState dlState = ImageCache.getDownloadState(imageId);

        if (dlState == ImageCache.DownloadState.DONE) {
            byte[] bytes = ImageDiskCache.load(imageId);
            if (bytes == null) {
                bytes = ImageCache.getFullData(imageId);
            }
            if (bytes != null) {
                doSaveDialog(imageId, bytes);
                return;
            }
        }

        ChatMessageRenderer.fetchFullImage(imageId, bytes -> mc.execute(() -> doSaveDialog(imageId, bytes)));
    }

    private static void doSaveDialog(String imageId, byte[] bytes) {
        Thread thread = new Thread(() -> {
            try {
                boolean isGif = GifDecoder.isGif(bytes);
                String ext = isGif ? "gif" : "png";
                String defaultName = imageId + "." + ext;
                String path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_saveFileDialog(
                        ChatRemasteredConfig.tr("chat-remastered.save_as_title"), defaultName, null, null);
                if (path == null) {
                    return;
                }
                File dest = new File(path.contains(".") ? path : path + "." + ext);
                java.nio.file.Files.write(dest.toPath(), bytes);
            } catch (Exception e) {
                System.out.println("[Chat Remastered] saveImageAs error: " + e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.setName("Chat Remastered-SaveAs");
        thread.start();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static PendingImageState.PendingImage withSize(PendingImageState.PendingImage cur, int w, int h, int origW, int origH, boolean sizeKnown) {
        return cur.withSize(w, h, origW, origH, sizeKnown);
    }

    private static PendingImageState.PendingImage withRawBytes(PendingImageState.PendingImage cur, byte[] rawBytes, boolean rawReady) {
        return cur.withRawBytesOnly(rawBytes, rawReady);
    }

    private static PendingImageState.PendingImage withRawBytesAndOrig(PendingImageState.PendingImage cur, byte[] rawBytes, boolean rawReady, int origW, int origH) {
        return cur.withRawBytesAndOrig(rawBytes, rawReady, origW, origH);
    }

    private static PendingImageState.PendingImage withFileAndOrig(PendingImageState.PendingImage cur, File file, int origW, int origH) {
        return cur.withFile(file, origW, origH);
    }
}
