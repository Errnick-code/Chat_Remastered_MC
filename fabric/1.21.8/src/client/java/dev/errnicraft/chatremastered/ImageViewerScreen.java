package dev.errnicraft.chatremastered;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ImageViewerScreen extends Screen {

    private java.util.List<String> groupImageIds = null;
    private int groupIndex = 0;

    private ResourceLocation previewTextureId;
    private String imageId;
    private int previewW;
    private int previewH;
    private File originalFile;

    private ResourceLocation hiResTextureId = null;
    private int hiResW;
    private int hiResH;

    private volatile boolean isLoadingFull = false;
    private volatile boolean loadError = false;
    private volatile boolean isSaving = false;
    private boolean hiResTransferredToCache = false;

    private final java.util.concurrent.atomic.AtomicInteger loadGeneration = new java.util.concurrent.atomic.AtomicInteger(0);

    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 4.0f;
    private float zoom = 1.0f;
    private float panX = 0.0f;
    private float panY = 0.0f;
    private boolean isDragging = false;
    private boolean cursorIsHand = false;
    private double dragLastMouseX = 0.0;
    private double dragLastMouseY = 0.0;
    private static final float DRAG_SPEED_FACTOR = 0.8f;
    private static final int ARROW_WIDTH = 20;

    public ImageViewerScreen(ResourceLocation previewTextureId, String imageId, int previewW, int previewH, File originalFile) {
        super(Component.literal("Image Viewer"));
        this.previewTextureId = previewTextureId;
        this.imageId = imageId;
        this.previewW = previewW;
        this.previewH = previewH;
        this.originalFile = originalFile;
        this.hiResW = previewW;
        this.hiResH = previewH;
    }

    public ImageViewerScreen(ResourceLocation previewTextureId, String imageId, int previewW, int previewH) {
        this(previewTextureId, imageId, previewW, previewH, null);
    }

    public void setGroupContext(java.util.List<String> groupImageIds, int groupIndex) {
        this.groupImageIds = groupImageIds;
        this.groupIndex = groupIndex;
    }

    private void cr$switchToGroupIndex(int newIndex) {
        if (groupImageIds == null || newIndex < 0 || newIndex >= groupImageIds.size()) {
            return;
        }
        if (!hiResTransferredToCache && hiResTextureId != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(hiResTextureId);
            } catch (Exception ignored) {
            }
        }
        groupIndex = newIndex;
        this.imageId = groupImageIds.get(newIndex);
        loadGeneration.incrementAndGet();
        ResourceLocation cachedTex = ImageCache.getTexture(imageId);
        IntPair cachedSize = ImageCache.getSize(imageId);
        this.previewTextureId = cachedTex != null ? cachedTex : previewTextureId;
        this.previewW = cachedSize != null ? cachedSize.getFirst() : previewW;
        this.previewH = cachedSize != null ? cachedSize.getSecond() : previewH;
        this.originalFile = ChatRemasteredStore.getOriginalFile(imageId);
        this.hiResTextureId = null;
        this.hiResW = previewW;
        this.hiResH = previewH;
        this.hiResTransferredToCache = false;
        this.isLoadingFull = false;
        this.loadError = false;
        this.zoom = 1.0f;
        this.panX = 0.0f;
        this.panY = 0.0f;
        loadFullImage();
    }

    public void cr$onImageDeletedExternally(String deletedImageId) {
        if (deletedImageId.equals(this.imageId) && hiResTextureId != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(hiResTextureId);
            } catch (Exception ignored) {
            }
            hiResTextureId = null;
            hiResTransferredToCache = false;
        }
    }

    @Override
    protected void init() {
        super.init();
        org.lwjgl.glfw.GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), 0L);
        loadFullImage();
    }

    private void loadFullImage() {
        if (isLoadingFull) {
            return;
        }

        final String targetImageId = this.imageId;
        final int myGeneration = loadGeneration.get();

        ResourceLocation cachedTex = ImageCache.getTexture(targetImageId);
        IntPair cachedTexSize = ImageCache.getTexSize(targetImageId);
        if (cachedTex != null && cachedTexSize != null && ImageCache.isLoaded(targetImageId)) {
            IntPair origSize = ImageCache.getOrigSize(targetImageId);
            hiResTextureId = cachedTex;
            hiResW = origSize != null ? origSize.getFirst() : cachedTexSize.getFirst();
            hiResH = origSize != null ? origSize.getSecond() : cachedTexSize.getSecond();
            hiResTransferredToCache = true;
            return;
        }

        isLoadingFull = true;

        byte[] cachedFull = ImageCache.getFullData(targetImageId);
        if (cachedFull != null) {
            loadHiResFromBytes(targetImageId, myGeneration, cachedFull);
            return;
        }

        if (originalFile != null && originalFile.exists()) {
            File targetFile = originalFile;
            Thread thread = new Thread(() -> {
                try {
                    byte[] bytes = Files.readAllBytes(targetFile.toPath());
                    ImageCache.storeFullData(targetImageId, bytes);
                    loadHiResFromBytes(targetImageId, myGeneration, bytes);
                } catch (Exception e) {
                    if (myGeneration == loadGeneration.get()) {
                        loadError = true;
                        isLoadingFull = false;
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
            return;
        }

        byte[] diskBytes = ImageDiskCache.load(targetImageId);
        if (diskBytes != null) {
            ImageCache.storeFullData(targetImageId, diskBytes);
            loadHiResFromBytes(targetImageId, myGeneration, diskBytes);
            return;
        }

        ChatRemasteredClient.fetchFullImage(targetImageId, bytes -> loadHiResFromBytes(targetImageId, myGeneration, bytes));
    }

    private void loadHiResFromBytes(String forImageId, int forGeneration, byte[] bytes) {
        ImageCache.loadAndUpgradeHiRes(forImageId, bytes, (loc, w, h) -> {
            if (forGeneration != loadGeneration.get() || !forImageId.equals(this.imageId)) {

                return;
            }
            hiResTextureId = loc;
            hiResW = w;
            hiResH = h;
            isLoadingFull = false;
            hiResTransferredToCache = true;
        });
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (isDragging != cursorIsHand) {
            cursorIsHand = isDragging;
            GLFW.glfwSetCursor(minecraft.getWindow().getWindow(),
                    cursorIsHand ? GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR) : 0L);
        }

        guiGraphics.fill(0, 0, width, height, (int) 0xFF000000);

        int padding = 8;
        int barH = 22;
        int maxW = width - padding * 2;
        int maxH = height - padding * 2 - barH;

        boolean currentIsDeleted = ImageCache.isDeleted(imageId);
        if (currentIsDeleted) {
            String deletedText = ImageCache.isDeletedByAdmin(imageId)
                    ? ChatRemasteredConfig.tr("Фото удалено администратором", "Photo deleted by admin")
                    : ChatRemasteredConfig.tr("Фото удалено автором", "Photo deleted by author");
            guiGraphics.drawCenteredString(minecraft.font, deletedText, width / 2, padding + maxH / 2, (int) 0xFFAAAAAA);
            renderGroupNav(guiGraphics, mouseX, mouseY, padding, maxH);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        ImageCache.GifFrameEntry gifFrame = ImageCache.isGif(imageId) ? ImageCache.getCurrentGifFrame(imageId) : null;

        ResourceLocation texId;
        int texW;
        int texH;

        if (gifFrame != null) {
            texId = gifFrame.getTextureId();
            texW = gifFrame.getWidth();
            texH = gifFrame.getHeight();
        } else {
            texId = hiResTextureId != null ? hiResTextureId : previewTextureId;
            texW = hiResTextureId != null ? hiResW : previewW;
            texH = hiResTextureId != null ? hiResH : previewH;
        }

        float baseS = Math.min((float) maxW / texW, (float) maxH / texH);
        float s = baseS * zoom;
        int dispW = Math.max((int) (texW * s), 1);
        int dispH = Math.max((int) (texH * s), 1);

        int baseX = (width - dispW) / 2;
        int baseY = padding + (maxH - dispH) / 2;

        float maxPanX = width / 2.0f + dispW / 2.0f;
        float maxPanY = maxH / 2.0f + dispH / 2.0f;
        panX = Mth.clamp(panX, -maxPanX, maxPanX);
        panY = Mth.clamp(panY, -maxPanY, maxPanY);

        int x = baseX + Math.round(panX);
        int y = baseY + Math.round(panY);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) x, (float) y);
        guiGraphics.pose().scale(s, s);
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texId,
                0, 0,
                0.0f, 0.0f,
                texW, texH,
                texW, texH,
                -1
        );
        guiGraphics.pose().popMatrix();

        int barY = height - barH;
        guiGraphics.fill(0, barY, width, height, (int) 0xBB000000);

        boolean canSave = originalFile != null || ImageCache.getFullData(imageId) != null;

        StringBuilder hint = new StringBuilder();
        hint.append("§7[ ESC — ");
        hint.append(ChatRemasteredConfig.tr("закрыть", "close"));
        if (canSave) {
            hint.append(" | §eS §7— ");
            hint.append(ChatRemasteredConfig.tr("сохранить", "save"));
        }
        if (isSaving) {
            hint.append(" | §7");
            hint.append(ChatRemasteredConfig.tr("Сохраняю...", "Saving..."));
        } else if (loadError) {
            hint.append(" | §c");
            hint.append(ChatRemasteredConfig.tr("Ошибка загрузки", "Load error"));
        }
        hint.append(" ]");

        guiGraphics.drawCenteredString(minecraft.font, hint.toString(), width / 2, barY + (barH - minecraft.font.lineHeight) / 2, (int) 0xFFAAAAAA);

        String zoomText = Math.round(zoom * 100) + "%";
        int zoomTextW = minecraft.font.width(zoomText);
        int zoomMargin = 8;
        guiGraphics.drawString(
                minecraft.font, zoomText,
                width - zoomMargin - zoomTextW,
                barY + (barH - minecraft.font.lineHeight) / 2,
                (int) 0xFFAAAAAA
        );

        renderGroupNav(guiGraphics, mouseX, mouseY, padding, maxH);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderGroupNav(GuiGraphics guiGraphics, int mouseX, int mouseY, int padding, int maxH) {
        if (groupImageIds != null && groupImageIds.size() > 1) {
            boolean hasPrev = groupIndex > 0;
            boolean hasNext = groupIndex < groupImageIds.size() - 1;
            int arrowAreaH = maxH;
            int arrowY = padding;

            if (hasPrev) {
                int ax0 = 0;
                boolean hover = mouseX >= ax0 && mouseX < ax0 + ARROW_WIDTH
                        && mouseY >= arrowY && mouseY < arrowY + arrowAreaH;
                int col = hover ? 0xFFFFFFFF : 0xAAAAAAAA;
                guiGraphics.fill(ax0, arrowY, ax0 + ARROW_WIDTH, arrowY + arrowAreaH, hover ? 0x55000000 : 0x22000000);
                guiGraphics.drawCenteredString(minecraft.font, "◀", ax0 + ARROW_WIDTH / 2, arrowY + arrowAreaH / 2 - minecraft.font.lineHeight / 2, col);
            }
            if (hasNext) {
                int ax0 = width - ARROW_WIDTH;
                boolean hover = mouseX >= ax0 && mouseX < ax0 + ARROW_WIDTH
                        && mouseY >= arrowY && mouseY < arrowY + arrowAreaH;
                int col = hover ? 0xFFFFFFFF : 0xAAAAAAAA;
                guiGraphics.fill(ax0, arrowY, ax0 + ARROW_WIDTH, arrowY + arrowAreaH, hover ? 0x55000000 : 0x22000000);
                guiGraphics.drawCenteredString(minecraft.font, "▶", ax0 + ARROW_WIDTH / 2, arrowY + arrowAreaH / 2 - minecraft.font.lineHeight / 2, col);
            }

            String counter = (groupIndex + 1) + " / " + groupImageIds.size();
            int counterTextW = minecraft.font.width(counter);
            int counterPadX = 6;
            int counterPadY = 3;
            int counterW = counterTextW + counterPadX * 2;
            int counterH = minecraft.font.lineHeight + counterPadY * 2;
            int counterX = 4;
            int counterY = height - 4 - counterH;
            guiGraphics.fill(counterX, counterY, counterX + counterW, counterY + counterH, 0xAA000000);
            guiGraphics.drawString(minecraft.font, counter, counterX + counterPadX, counterY + counterPadY, (int) 0xFFFFFFFF);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
            if (!isSaving) {
                saveImage();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void mouseMoved(double x, double y) {
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && groupImageIds != null && groupImageIds.size() > 1) {
            int padding = 8;
            int barH = 22;
            int maxH = height - padding * 2 - barH;
            int arrowY = padding;
            double mx = mouseX;
            double my = mouseY;
            boolean inRow = my >= arrowY && my < arrowY + maxH;
            if (inRow) {
                if (groupIndex > 0) {
                    int ax0 = 0;
                    if (mx >= ax0 && mx < ax0 + ARROW_WIDTH) {
                        cr$switchToGroupIndex(groupIndex - 1);
                        return true;
                    }
                }
                if (groupIndex < groupImageIds.size() - 1) {
                    int ax0 = width - ARROW_WIDTH;
                    if (mx >= ax0 && mx < ax0 + ARROW_WIDTH) {
                        cr$switchToGroupIndex(groupIndex + 1);
                        return true;
                    }
                }
            }
        }
        if (button == 0) {
            isDragging = true;
            dragLastMouseX = mouseX;
            dragLastMouseY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDragging) {
            isDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && isDragging) {
            panX += (float) (dx * DRAG_SPEED_FACTOR);
            panY += (float) (dy * DRAG_SPEED_FACTOR);
            dragLastMouseX = mouseX;
            dragLastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float oldZoom = zoom;
        float newZoom = Mth.clamp((float) (zoom + scrollY * 0.1), MIN_ZOOM, MAX_ZOOM);
        zoom = newZoom;
        if (oldZoom != 0f) {
            float ratio = newZoom / oldZoom;
            panX *= ratio;
            panY *= ratio;
        }
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), 0L);
        if (!hiResTransferredToCache && hiResTextureId != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(hiResTextureId);
            } catch (Exception ignored) {
            }
        }
        super.onClose();
    }

    private void saveImage() {
        Minecraft mc = Minecraft.getInstance();
        isSaving = true;
        Thread thread = new Thread(() -> {
            try {
                Path dir = mc.gameDirectory.toPath().resolve("screenshots").resolve("chat-remastered");
                Files.createDirectories(dir);
                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                File outFile = dir.resolve("chat-remastered_" + timestamp + ".png").toFile();

                if (originalFile != null && originalFile.exists()) {
                    Files.copy(originalFile.toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else if (ImageCache.getFullData(imageId) != null) {
                    byte[] raw = ImageCache.getFullData(imageId);
                    Files.write(outFile.toPath(), raw);
                } else {
                    isSaving = false;
                    return;
                }

                mc.execute(() -> {
                    isSaving = false;
                    mc.gui.getChat().addMessage(
                            Component.literal(
                                    "§a[Chat Remastered] " + ChatRemasteredConfig.tr(
                                            "Сохранено: §fscreenshots/chat-remastered/chat-remastered_" + timestamp + ".png",
                                            "Saved: §fscreenshots/chat-remastered/chat-remastered_" + timestamp + ".png"
                                    )
                            )
                    );
                });
            } catch (Exception e) {
                mc.execute(() -> {
                    isSaving = false;
                    mc.gui.getChat().addMessage(
                            Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr(
                                    "Ошибка сохранения: " + e.getMessage(),
                                    "Save error: " + e.getMessage()
                            ))
                    );
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
}
