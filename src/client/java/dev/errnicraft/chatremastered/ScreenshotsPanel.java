package dev.errnicraft.chatremastered;

import com.mojang.blaze3d.platform.NativeImage;
import dev.errnicraft.chatremastered.client.ClientImageUtils;
import dev.errnicraft.chatremastered.client.ScreenshotMetadataWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScreenshotsPanel {

    private static final int PANEL_W = 260;
    private static final int PADDING = 8;
    private static final int HEADER_H = 20;
    private static final int CLOSE_SIZE = 14;
    private static final int GAP = 8;
    private static final int CAPTION_H = 12;
    private static final int SCROLLBAR_W = 4;
    private static final float ANIM_SPEED = 0.055f;

    private static final int FOLDER_BAR_H = 20;
    private static final int FOLDER_BTN_SIZE = 20;
    private static final int FOLDER_BTN_GAP = 4;

    private static final ExecutorService IO_POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Chat Remastered-ScreenshotsPanel");
        t.setDaemon(true);
        return t;
    });

    private static final Identifier SCREENSHOTS_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/gui/screenshots.png");
    private static final Identifier SCREENSHOTS_ACTIVE_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/gui/screenshots_active.png");
    private static final Identifier FOLDER_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/gui/folder.png");
    private static final Identifier FOLDER_ACTIVE_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/gui/folder_active.png");
    private static final Identifier ADD_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/gui/add.png");
    private static final Identifier ADD_ACTIVE_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/gui/add_active.png");

    private static final class Entry {
        final File file;
        final long lastModified;
        volatile String caption;
        volatile boolean metaLoaded = false;
        volatile boolean metaLoading = false;

        volatile Identifier textureId = null;
        volatile int texW, texH;
        volatile boolean loading = false;
        volatile boolean loadFailed = false;

        Entry(File file) {
            this.file = file;
            this.lastModified = file.lastModified();
        }
    }

    private boolean open = false;
    private float openAnim = 0f;
    private List<Entry> entries = new ArrayList<>();
    private boolean scanned = false;
    private boolean scanning = false;

    private int activeFolderIndex = 0;

    private int scrollY = 0;
    private boolean draggingScrollbar = false;
    private boolean draggingContent = false;
    private double dragLastY = 0.0;

    private int folderBarScrollX = 0;
    private boolean draggingFolderBar = false;
    private double dragLastX = 0.0;

    private AtomicBoolean scanCancel = new AtomicBoolean(false);
    private volatile boolean folderDialogOpen = false;

    public boolean isOpen() {
        return open || openAnim > 0.001f;
    }

    public void open() {
        if (open) return;
        open = true;
        scrollY = 0;
        activeFolderIndex = Mth.clamp(ChatRemasteredConfig.getLastScreenshotFolderIndex(), 0, ChatRemasteredConfig.getScreenshotFolders().size());
        if (!scanned && !scanning) {
            scan();
        }
    }

    public void close() {
        open = false;
        draggingScrollbar = false;
        draggingContent = false;
        draggingFolderBar = false;
    }

    public void toggle() {
        if (open) close(); else open();
    }

    private File screenshotsDir() {
        if (activeFolderIndex == 0) {
            return Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("screenshots").toFile();
        }
        List<String> folders = ChatRemasteredConfig.getScreenshotFolders();
        int i = activeFolderIndex - 1;
        if (i < 0 || i >= folders.size()) {
            return Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("screenshots").toFile();
        }
        return new File(folders.get(i));
    }

    private String currentFolderTitle() {
        if (activeFolderIndex == 0) {
            return ChatRemasteredConfig.tr("chat-remastered.screenshots_title");
        }
        return screenshotsDir().getName();
    }

    private void switchFolder(int index) {
        if (index == activeFolderIndex) {
            return;
        }
        activeFolderIndex = index;
        ChatRemasteredConfig.setLastScreenshotFolderIndex(index);
        scrollY = 0;
        scanned = false;
        releaseAll();
        entries = new ArrayList<>();
        if (!scanning) {
            scan();
        }
    }

    private void scan() {
        scanning = true;
        scanCancel.set(true);
        AtomicBoolean myCancel = new AtomicBoolean(false);
        scanCancel = myCancel;
        IO_POOL.submit(() -> {
            List<Entry> found = new ArrayList<>();
            File dir = screenshotsDir();
            File[] files = dir.isDirectory() ? dir.listFiles() : null;
            if (files != null) {
                for (File f : files) {
                    if (myCancel.get()) return;
                    String name = f.getName().toLowerCase(java.util.Locale.ROOT);
                    if (f.isFile() && name.endsWith(".png")) {
                        found.add(new Entry(f));
                    }
                }
            }
            found.sort(Comparator.comparingLong((Entry e) -> e.lastModified).reversed());
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (myCancel.get()) return;
                entries = found;
                scanned = true;
                scanning = false;
            });
        });
    }

    public void releaseAll() {
        Minecraft mc = Minecraft.getInstance();
        for (Entry e : entries) {
            if (e.textureId != null) {
                try {
                    mc.getTextureManager().release(e.textureId);
                } catch (Exception ignored) {
                }
                e.textureId = null;
                e.loading = false;
            }
        }
    }

    public void tick() {
        float target = open ? 1f : 0f;
        if (openAnim < target) {
            openAnim = Math.min(target, openAnim + ANIM_SPEED);
        } else if (openAnim > target) {
            openAnim = Math.max(target, openAnim - ANIM_SPEED);
        }
    }

    private int panelX(int screenW, int screenH) {
        float eased = Mth.clamp(openAnim, 0f, 1f);
        eased = eased * eased * (3f - 2f * eased);
        boolean onLeft = ChatRemasteredConfig.getScreenshotsPanelOnLeft();
        if (onLeft) {
            return Math.round(-PANEL_W + PANEL_W * eased);
        } else {
            return Math.round(screenW - PANEL_W * eased);
        }
    }

    private int thumbW(int panelWidth) {
        return (panelWidth - PADDING * 2 - GAP) / 2;
    }

    private int tileHeight(int panelWidth) {
        int tw = thumbW(panelWidth);
        int th = Math.max(1, Math.round(tw * 9f / 16f));
        return th + CAPTION_H;
    }

    public void render(GuiGraphics graphics, int screenW, int screenH, int mouseX, int mouseY) {
        tick();
        if (!isOpen()) return;

        cr$pollDrag(screenW, screenH, mouseX, mouseY);

        int px = panelX(screenW, screenH);
        int pw = PANEL_W;

        graphics.fill(px, 0, px + pw, screenH, 0xE60A0A0A);
        boolean onLeft = ChatRemasteredConfig.getScreenshotsPanelOnLeft();
        int edgeX = onLeft ? px + pw - 1 : px;
        graphics.fill(edgeX, 0, edgeX + 1, screenH, 0x55FFFFFF);

        graphics.fill(px, 0, px + pw, HEADER_H, 0xF0050505);
        graphics.fill(px, HEADER_H - 1, px + pw, HEADER_H, 0x33FFFFFF);

        Minecraft mc = Minecraft.getInstance();

        String title = currentFolderTitle();
        int titleY = (HEADER_H - mc.font.lineHeight) / 2;
        int maxTitleW = pw - PADDING - CLOSE_SIZE - 8;
        if (mc.font.width(title) > maxTitleW) {
            title = mc.font.plainSubstrByWidth(title, maxTitleW - mc.font.width("…")) + "…";
        }
        graphics.drawString(mc.font, title, px + PADDING, titleY, 0xFFFFFFFF, false);

        int closeX = px + pw - CLOSE_SIZE - 4;
        int closeY = (HEADER_H - CLOSE_SIZE) / 2;
        boolean hoverClose = mouseX >= closeX && mouseX < closeX + CLOSE_SIZE
                && mouseY >= closeY && mouseY < closeY + CLOSE_SIZE;
        int closeColor = hoverClose ? 0xFFFF5555 : 0xAAFFFFFF;
        graphics.drawCenteredString(mc.font, "✕", closeX + CLOSE_SIZE / 2, closeY + (CLOSE_SIZE - mc.font.lineHeight) / 2, closeColor);

        int listTop = HEADER_H + PADDING;
        int listBottom = screenH - PADDING - FOLDER_BAR_H - GAP;
        int listLeft = px + PADDING;
        int listRight = px + pw - PADDING;
        int listW = listRight - listLeft;
        int tw = thumbW(pw);
        int tileH = tileHeight(pw);

        if (!scanned && scanning) {
            graphics.drawString(mc.font, ChatRemasteredConfig.tr("chat-remastered.screenshots_loading"),
                    listLeft, listTop, 0xFFAAAAAA, false);
        } else if (scanned && entries.isEmpty()) {
            graphics.drawString(mc.font, ChatRemasteredConfig.tr("chat-remastered.screenshots_empty"),
                    listLeft, listTop, 0xFFAAAAAA, false);
        } else {
            int rows = (entries.size() + 1) / 2;
            int contentH = rows * tileH + Math.max(0, rows - 1) * GAP;
            int viewH = listBottom - listTop;
            int maxScroll = Math.max(0, contentH - viewH);
            scrollY = Mth.clamp(scrollY, 0, maxScroll);

            boolean needsScrollbar = contentH > viewH;
            graphics.enableScissor(listLeft, listTop, listRight + 1, listBottom + 1);

            for (int i = 0; i < entries.size(); i++) {
                int row = i / 2;
                int col = i % 2;
                int tileY = listTop + row * (tileH + GAP) - scrollY;
                if (tileY + tileH < listTop || tileY > listBottom) {
                    continue;
                }
                int tileX = listLeft + col * (tw + GAP);
                renderTile(graphics, mc, entries.get(i), tileX, tileY, tw, mouseX, mouseY);
            }

            graphics.disableScissor();

            if (needsScrollbar) {
                int trackX = listRight + 2;
                int trackTop = listTop;
                int trackH = viewH;
                graphics.fill(trackX, trackTop, trackX + SCROLLBAR_W, trackTop + trackH, 0x33FFFFFF);
                int thumbH = Math.max(16, (int) ((long) viewH * viewH / contentH));
                int thumbY = trackTop + (int) ((long) (viewH - thumbH) * scrollY / Math.max(1, maxScroll));
                graphics.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, 0xAAFFFFFF);
            }
        }

        cr$renderFolderBar(graphics, mc, px, pw, screenW, screenH, mouseX, mouseY);

        cr$renderContextMenu(graphics, screenW, screenH, mouseX, mouseY);
        cr$renderConfirmModal(graphics, screenW, screenH, mouseX, mouseY);
    }

    private int cr$folderCount() {
        return 1 + ChatRemasteredConfig.getScreenshotFolders().size();
    }

    private int cr$folderBarContentW() {
        int count = cr$folderCount();

        return count * FOLDER_BTN_SIZE + count * FOLDER_BTN_GAP;
    }

    private void cr$renderFolderBar(GuiGraphics graphics, Minecraft mc, int px, int pw, int screenW, int screenH, int mouseX, int mouseY) {
        int barTop = screenH - PADDING - FOLDER_BAR_H;
        int barLeft = px + PADDING;
        int barRight = px + pw - PADDING;
        int barW = barRight - barLeft;

        graphics.fill(barLeft, barTop, barRight, barTop + FOLDER_BAR_H, 0xCC101010);

        int contentW = cr$folderBarContentW();
        int maxScrollX = Math.max(0, contentW - barW);
        folderBarScrollX = Mth.clamp(folderBarScrollX, 0, maxScrollX);

        graphics.enableScissor(barLeft, barTop, barRight, barTop + FOLDER_BAR_H);

        int x = barLeft - folderBarScrollX;
        List<String> folders = ChatRemasteredConfig.getScreenshotFolders();
        int count = cr$folderCount();

        for (int i = 0; i < count; i++) {
            boolean active = i == activeFolderIndex;
            boolean hover = mouseX >= x && mouseX < x + FOLDER_BTN_SIZE
                    && mouseY >= barTop && mouseY < barTop + FOLDER_BTN_SIZE;
            Identifier tex = i == 0
                    ? (active || hover ? SCREENSHOTS_ACTIVE_TEX : SCREENSHOTS_TEX)
                    : (active || hover ? FOLDER_ACTIVE_TEX : FOLDER_TEX);
            if (x + FOLDER_BTN_SIZE >= barLeft && x <= barRight) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, tex, x, barTop, 0f, 0f, FOLDER_BTN_SIZE, FOLDER_BTN_SIZE, FOLDER_BTN_SIZE, FOLDER_BTN_SIZE);
            }
            x += FOLDER_BTN_SIZE + FOLDER_BTN_GAP;
        }

        boolean hoverAdd = mouseX >= x && mouseX < x + FOLDER_BTN_SIZE
                && mouseY >= barTop && mouseY < barTop + FOLDER_BTN_SIZE;
        if (x + FOLDER_BTN_SIZE >= barLeft && x <= barRight) {
            Identifier addTex = hoverAdd ? ADD_ACTIVE_TEX : ADD_TEX;
            graphics.blit(RenderPipelines.GUI_TEXTURED, addTex, x, barTop, 0f, 0f, FOLDER_BTN_SIZE, FOLDER_BTN_SIZE, FOLDER_BTN_SIZE, FOLDER_BTN_SIZE);
        }

        graphics.disableScissor();
    }

    private void renderTile(GuiGraphics graphics, Minecraft mc, Entry e, int x, int y, int w, int mouseX, int mouseY) {
        int th = Math.max(1, Math.round(w * 9f / 16f));

        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + th;

        graphics.fill(x, y, x + w, y + th, 0xFF1E1E1E);

        ensureThumbLoading(e);

        if (e.textureId != null) {
            float scaleX = (float) w / e.texW;
            float scaleY = (float) th / e.texH;
            float s = Math.min(scaleX, scaleY);
            int fitW = Math.max(1, Math.round(e.texW * s));
            int fitH = Math.max(1, Math.round(e.texH * s));
            int offX = (w - fitW) / 2;
            int offY = (th - fitH) / 2;
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + offX, y + offY);
            graphics.pose().scale(s, s);
            graphics.blit(RenderPipelines.GUI_TEXTURED, e.textureId, 0, 0, 0f, 0f, e.texW, e.texH, e.texW, e.texH, -1);
            graphics.pose().popMatrix();
        } else if (e.loadFailed) {
            graphics.drawCenteredString(mc.font, "?", x + w / 2, y + th / 2 - mc.font.lineHeight / 2, 0xFF888888);
        } else {

            graphics.drawCenteredString(mc.font, "🖼", x + w / 2, y + th / 2 - mc.font.lineHeight / 2, 0xFF666666);
        }

        if (hover) {
            graphics.fill(x, y, x + w, y + th, 0x22FFFFFF);
        }
        int borderColor = hover ? 0x88FFFFFF : 0x44FFFFFF;
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + th - 1, x + w, y + th, borderColor);
        graphics.fill(x, y + 1, x + 1, y + th - 1, borderColor);
        graphics.fill(x + w - 1, y + 1, x + w, y + th - 1, borderColor);

        ensureMetaLoading(e);
        String caption = e.caption != null ? e.caption : "";
        if (mc.font.width(caption) > w) {
            caption = mc.font.plainSubstrByWidth(caption, w - mc.font.width("…")) + "…";
        }
        graphics.drawString(mc.font, caption, x, y + th + 2, 0xFF999999, false);
    }

    private void ensureThumbLoading(Entry e) {
        if (e.textureId != null || e.loading || e.loadFailed) {
            return;
        }
        e.loading = true;
        IO_POOL.submit(() -> {
            try {
                byte[] bytes = Files.readAllBytes(e.file.toPath());
                BufferedImage src = ClientImageUtils.readPng(bytes);
                if (src == null) {
                    Minecraft.getInstance().execute(() -> {
                        e.loading = false;
                        e.loadFailed = true;
                    });
                    return;
                }
                int maxDim = 256;
                double scale = Math.min(1.0, Math.min((double) maxDim / src.getWidth(), (double) maxDim / src.getHeight()));
                BufferedImage scaled = ClientImageUtils.scaleImage(src, scale, BufferedImage.TYPE_INT_ARGB);
                byte[] pngBytes = ClientImageUtils.toPng(scaled);
                Minecraft.getInstance().execute(() -> {
                    try {
                        NativeImage nativeImage = NativeImage.read(pngBytes);
                        Identifier texId = Identifier.fromNamespaceAndPath(
                                "chat-remastered", "screenshots_panel_" + Integer.toHexString(System.identityHashCode(e)) + "_" + System.nanoTime());
                        Minecraft.getInstance().getTextureManager().register(texId,
                                new SmoothDynamicTexture(() -> "chat-remastered-screenshots-panel", nativeImage));
                        e.textureId = texId;
                        e.texW = scaled.getWidth();
                        e.texH = scaled.getHeight();
                        e.loading = false;
                    } catch (Exception ex) {
                        e.loading = false;
                        e.loadFailed = true;
                    }
                });
            } catch (Exception ex) {
                Minecraft.getInstance().execute(() -> {
                    e.loading = false;
                    e.loadFailed = true;
                });
            }
        });
    }

    private void ensureMetaLoading(Entry e) {
        if (e.metaLoaded || e.metaLoading) {
            return;
        }
        e.metaLoading = true;
        IO_POOL.submit(() -> {
            String caption;
            try {
                byte[] bytes = Files.readAllBytes(e.file.toPath());
                Map<String, String> fields = ScreenshotMetadataWriter.readMetadata(bytes);
                String created = fields.get("Created");
                if (created != null && !created.isBlank()) {
                    caption = created;
                } else {
                    caption = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(e.lastModified));
                }
            } catch (Exception ex) {
                caption = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(e.lastModified));
            }
            String finalCaption = caption;
            Minecraft.getInstance().execute(() -> {
                e.caption = finalCaption;
                e.metaLoaded = true;
                e.metaLoading = false;
            });
        });
    }

    public boolean mouseClicked(int screenW, int screenH, double mx, double my, int button) {
        if (!isOpen()) {
            return false;
        }

        if (confirmDeleteEntry != null || confirmDeleteFolderIndex != -1) {
            return cr$handleConfirmClick(screenW, screenH, mx, my, button);
        }

        if (ctxMenuEntry != null || ctxMenuFolderIndex != -1) {
            return cr$handleContextMenuClick(mx, my, button);
        }

        if (button != 0 && button != 1) {
            return true;
        }

        int px = panelX(screenW, screenH);
        int pw = PANEL_W;

        boolean insidePanel = mx >= px && mx < px + pw && my >= 0 && my < screenH;

        if (button == 0) {
            int closeX = px + pw - CLOSE_SIZE - 4;
            int closeY = (HEADER_H - CLOSE_SIZE) / 2;
            if (mx >= closeX && mx < closeX + CLOSE_SIZE && my >= closeY && my < closeY + CLOSE_SIZE) {
                close();
                return true;
            }
        }

        if (!insidePanel) {

            close();
            return true;
        }

        if (cr$handleFolderBarClick(px, pw, screenH, mx, my, button)) {
            return true;
        }

        int listTop = HEADER_H + PADDING;
        int listBottom = screenH - PADDING - FOLDER_BAR_H - GAP;
        int listLeft = px + PADDING;
        int listRight = px + pw - PADDING;
        int tw = thumbW(pw);
        int tileH = tileHeight(pw);

        int rows = (entries.size() + 1) / 2;
        int contentH = rows * tileH + Math.max(0, rows - 1) * GAP;
        int viewH = listBottom - listTop;
        int maxScroll = Math.max(0, contentH - viewH);

        if (button == 0 && maxScroll > 0) {
            int trackX = listRight + 2;
            if (mx >= trackX - 2 && mx < trackX + SCROLLBAR_W + 2 && my >= listTop && my < listBottom) {
                draggingScrollbar = true;
                dragLastY = my;
                return true;
            }
        }

        if (my >= listTop && my < listBottom && mx >= listLeft && mx < listRight) {
            for (int i = 0; i < entries.size(); i++) {
                int row = i / 2;
                int col = i % 2;
                int tileY = listTop + row * (tileH + GAP) - scrollY;
                int thH = Math.max(1, Math.round(tw * 9f / 16f));
                if (my >= tileY && my < tileY + thH) {
                    int tileX = listLeft + col * (tw + GAP);
                    if (mx >= tileX && mx < tileX + tw) {
                        Entry entry = entries.get(i);
                        if (button == 0) {
                            onTileClicked(entry);
                        } else {
                            cr$openContextMenu(entry, mx, my);
                        }
                        return true;
                    }
                }
            }
            if (button == 0) {
                draggingContent = true;
                dragLastY = my;
            }
        }

        return true;
    }

    private boolean cr$handleFolderBarClick(int px, int pw, int screenH, double mx, double my, int button) {
        int barTop = screenH - PADDING - FOLDER_BAR_H;
        int barLeft = px + PADDING;
        int barRight = px + pw - PADDING;
        if (my < barTop || my >= barTop + FOLDER_BAR_H || mx < barLeft || mx >= barRight) {
            return false;
        }

        int count = cr$folderCount();
        int x = barLeft - folderBarScrollX;
        for (int i = 0; i < count; i++) {
            if (mx >= x && mx < x + FOLDER_BTN_SIZE) {
                if (button == 0) {
                    switchFolder(i);
                } else if (button == 1) {
                    cr$openFolderContextMenu(i, mx, my);
                }
                return true;
            }
            x += FOLDER_BTN_SIZE + FOLDER_BTN_GAP;
        }

        if (mx >= x && mx < x + FOLDER_BTN_SIZE) {
            if (button == 0) {
                cr$openAddFolderDialog();
            }
            return true;
        }

        if (button == 0) {
            int barW = barRight - barLeft;
            int contentW = cr$folderBarContentW();
            if (contentW > barW) {
                draggingFolderBar = true;
                dragLastX = mx;
            }
        }
        return true;
    }

    private void cr$openAddFolderDialog() {
        if (folderDialogOpen) {
            return;
        }
        folderDialogOpen = true;
        Minecraft mc = Minecraft.getInstance();

        Thread t = new Thread(() -> {
            String path = null;
            try {
                path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                        ChatRemasteredConfig.tr("chat-remastered.select_screenshots_folder"),
                        "", null, null, false);
            } catch (Exception ignored) {
            }
            String finalPath = path;
            mc.execute(() -> {
                folderDialogOpen = false;
                if (finalPath != null && !finalPath.isBlank()) {
                    File picked = new File(finalPath);
                    File folder = picked.isDirectory() ? picked : picked.getParentFile();
                    String folderPath = folder != null ? folder.getAbsolutePath() : null;
                    if (folderPath != null) {
                        boolean added = ChatRemasteredConfig.addScreenshotFolder(folderPath);
                        if (added) {
                            int newIndex = ChatRemasteredConfig.getScreenshotFolders().size();
                            switchFolder(newIndex);
                        }
                    }
                }
            });
        });
        t.setDaemon(true);
        t.setName("Chat Remastered-SelectFolder");
        t.start();
    }

    private void onTileClicked(Entry e) {

        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ChatRemasteredClient.stageImage(e.file));
    }

    private Entry ctxMenuEntry = null;
    private int ctxMenuFolderIndex = -1;
    private int ctxMenuX, ctxMenuY;
    private static final int CTX_ITEM_H = 14;
    private static final int CTX_PAD = 4;
    private static final int CTX_ICON_COL = 11;
    private static final int CTX_ANIM_MS = 100;
    private long ctxMenuOpenTime = 0L;
    private float[] ctxMenuItemAnim = new float[0];

    private void cr$openContextMenu(Entry e, double mx, double my) {
        ctxMenuEntry = e;
        ctxMenuFolderIndex = -1;
        ctxMenuX = (int) mx;
        ctxMenuY = (int) my;
        ctxMenuOpenTime = System.currentTimeMillis();
        ctxMenuItemAnim = new float[cr$contextMenuHasDelete() ? 2 : 1];
    }

    private void cr$openFolderContextMenu(int index, double mx, double my) {
        ctxMenuEntry = null;
        ctxMenuFolderIndex = index;
        ctxMenuX = (int) mx;
        ctxMenuY = (int) my;
        ctxMenuOpenTime = System.currentTimeMillis();
        ctxMenuItemAnim = new float[cr$contextMenuHasDelete() ? 2 : 1];
    }

    private boolean cr$contextMenuHasDelete() {
        if (ctxMenuEntry != null) return true;
        return ctxMenuFolderIndex > 0;
    }

    private int cr$contextMenuWidth() {
        Minecraft mc = Minecraft.getInstance();
        String openLabel = ChatRemasteredConfig.tr("chat-remastered.ctx_open_folder");
        int w = mc.font.width(openLabel);
        if (cr$contextMenuHasDelete()) {
            String delLabel = ctxMenuEntry != null
                    ? ChatRemasteredConfig.tr("chat-remastered.ctx_delete_screenshot")
                    : ChatRemasteredConfig.tr("chat-remastered.ctx_remove_folder");
            w = Math.max(w, mc.font.width(delLabel));
        }
        return w + CTX_PAD * 2 + 4 + CTX_ICON_COL;
    }

    private boolean cr$handleContextMenuClick(double mx, double my, int button) {
        boolean hasDelete = cr$contextMenuHasDelete();
        int menuW = cr$contextMenuWidth();
        int itemCount = hasDelete ? 2 : 1;
        int menuH = CTX_PAD * 2 + CTX_ITEM_H * itemCount + (itemCount - 1) * 2;
        boolean inside = mx >= ctxMenuX && mx < ctxMenuX + menuW && my >= ctxMenuY && my < ctxMenuY + menuH;

        if (button == 0 && inside) {
            int idx = (int) ((my - ctxMenuY - CTX_PAD) / (CTX_ITEM_H + 2));
            if (ctxMenuEntry != null) {
                Entry target = ctxMenuEntry;
                if (idx == 0) {
                    cr$openFolder(target);
                } else if (idx == 1) {
                    confirmDeleteEntry = target;
                }
            } else if (ctxMenuFolderIndex != -1) {
                int index = ctxMenuFolderIndex;
                if (idx == 0) {
                    cr$openFolderInExplorer(index);
                } else if (idx == 1 && hasDelete) {
                    confirmDeleteFolderIndex = index;
                }
            }
        }
        ctxMenuEntry = null;
        ctxMenuFolderIndex = -1;
        return true;
    }

    private void cr$openFolder(Entry e) {
        if (e == null) return;
        Minecraft mc = Minecraft.getInstance();
        File parent = e.file.getParentFile();
        if (parent == null) return;
        Thread t = new Thread(() -> {
            try {
                Util.getPlatform().openPath(parent.toPath());
            } catch (Exception ignored) {
            }
        });
        t.setDaemon(true);
        t.setName("Chat Remastered-OpenFolder");
        t.start();
    }

    private void cr$openFolderInExplorer(int index) {
        int savedIndex = activeFolderIndex;
        activeFolderIndex = index;
        File dir = screenshotsDir();
        activeFolderIndex = savedIndex;
        if (dir == null) return;
        Thread t = new Thread(() -> {
            try {
                Util.getPlatform().openPath(dir.toPath());
            } catch (Exception ignored) {
            }
        });
        t.setDaemon(true);
        t.setName("Chat Remastered-OpenFolder");
        t.start();
    }

    private void cr$renderContextMenu(GuiGraphics graphics, int screenW, int screenH, int mouseX, int mouseY) {
        if (ctxMenuEntry == null && ctxMenuFolderIndex == -1) return;
        Minecraft mc = Minecraft.getInstance();
        boolean hasDelete = cr$contextMenuHasDelete();
        int menuW = cr$contextMenuWidth();
        int itemCount = hasDelete ? 2 : 1;
        int menuH = CTX_PAD * 2 + CTX_ITEM_H * itemCount + (itemCount - 1) * 2;
        int mxPos = Math.min(ctxMenuX, screenW - menuW - 2);
        int myPos = Math.min(ctxMenuY, screenH - menuH - 2);
        ctxMenuX = mxPos;
        ctxMenuY = myPos;

        if (ctxMenuItemAnim.length != itemCount) {
            ctxMenuItemAnim = new float[itemCount];
        }

        float elapsed = System.currentTimeMillis() - ctxMenuOpenTime;
        float t = Math.min(1f, elapsed / CTX_ANIM_MS);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        int visibleH = Math.round(menuH * ease);
        float alpha = Math.min(1f, t * 2f);

        int mx0 = mxPos, my0 = myPos;
        int mx1 = mx0 + menuW, my1 = my0 + menuH;
        int clipBot = my0 + visibleH;

        int shadowAlpha = Math.round(0x55 * alpha);
        for (int s = 4; s >= 1; s--) {
            int sa = shadowAlpha / s;
            cr$ctxFillClipped(graphics, mx0 + s, my0 + s, mx1 + s, Math.min(clipBot + s, my1 + s),
                    (sa << 24), my0, clipBot + s);
        }

        int bgAlpha = Math.round(0xEC * alpha);
        cr$ctxFillClipped(graphics, mx0, my0, mx1, clipBot, (bgAlpha << 24) | 0x1E1E1E, my0, clipBot);

        int borderAlpha = Math.round(0xFF * alpha);
        int borderColor = (borderAlpha << 24) | 0x3A3A3A;
        cr$ctxFillClipped(graphics, mx0, my0, mx1, my0 + 1, borderColor, my0, clipBot);
        cr$ctxFillClipped(graphics, mx0, my1 - 1, mx1, my1, borderColor, my0, clipBot);
        cr$ctxFillClipped(graphics, mx0, my0, mx0 + 1, my1, borderColor, my0, clipBot);
        cr$ctxFillClipped(graphics, mx1 - 1, my0, mx1, my1, borderColor, my0, clipBot);
        graphics.fill(mx0, my0, mx0 + 3, my0 + 1, 0); graphics.fill(mx0, my0, mx0 + 1, my0 + 3, 0);
        graphics.fill(mx1 - 3, my0, mx1, my0 + 1, 0); graphics.fill(mx1 - 1, my0, mx1, my0 + 3, 0);
        if (clipBot >= my1) {
            graphics.fill(mx0, my1 - 1, mx0 + 3, my1, 0); graphics.fill(mx0, my1 - 3, mx0 + 1, my1, 0);
            graphics.fill(mx1 - 3, my1 - 1, mx1, my1, 0); graphics.fill(mx1 - 1, my1 - 3, mx1, my1, 0);
        }

        String openLabel = ChatRemasteredConfig.tr("chat-remastered.ctx_open_folder");
        String delLabel = ctxMenuEntry != null
                ? ChatRemasteredConfig.tr("chat-remastered.ctx_delete_screenshot")
                : ChatRemasteredConfig.tr("chat-remastered.ctx_remove_folder");
        String[] labels = hasDelete ? new String[] { openLabel, delLabel } : new String[] { openLabel };
        String[] icons = hasDelete ? new String[] { "📁", "🗑" } : new String[] { "📁" };

        for (int i = 0; i < labels.length; i++) {
            int iy = my0 + CTX_PAD + i * (CTX_ITEM_H + 2);
            int iy2 = iy + CTX_ITEM_H;
            if (iy >= clipBot) break;

            boolean hovered = mouseX >= mx0 + 2 && mouseX < mx1 - 2 && mouseY >= iy && mouseY < iy2;
            float target = hovered ? 1f : 0f;
            ctxMenuItemAnim[i] += (target - ctxMenuItemAnim[i]) * 0.3f;
            float a = ctxMenuItemAnim[i];
            if (a > 0.01f) {
                int hAlpha = Math.round(0xFF * a * alpha);
                boolean isRedItem = hasDelete && i == 1;
                int hR, hG, hB;
                if (isRedItem) {
                    hR = (int) (0x2B + (0xC0 - 0x2B) * a);
                    hG = (int) (0x2B + (0x39 - 0x2B) * a);
                    hB = (int) (0x2B + (0x2B - 0x2B) * a);
                } else {
                    hR = (int) (0x2B + (0x00 - 0x2B) * a);
                    hG = (int) (0x2B + (0x78 - 0x2B) * a);
                    hB = (int) (0x2B + (0xD4 - 0x2B) * a);
                }
                int hoverColor = (hAlpha << 24) | (hR << 16) | (hG << 8) | hB;
                int hx0 = mx0 + 3, hx1 = mx1 - 3;
                int clipIy2 = Math.min(iy2, clipBot);
                cr$ctxFillClipped(graphics, hx0, iy, hx1, clipIy2, hoverColor, my0, clipBot);
                cr$ctxFillClipped(graphics, hx0 + 1, iy - 1, hx1 - 1, iy, hoverColor, my0, clipBot);
                cr$ctxFillClipped(graphics, hx0 + 1, iy2, hx1 - 1, iy2 + 1, hoverColor, my0, clipBot);
                cr$ctxFillClipped(graphics, hx0 - 1, iy + 1, hx0, iy2 - 1, hoverColor, my0, clipBot);
                cr$ctxFillClipped(graphics, hx1, iy + 1, hx1 + 1, iy2 - 1, hoverColor, my0, clipBot);
            }

            if (iy2 > clipBot) continue;

            int textAlpha = Math.round(0xFF * alpha);
            int baseColor = (hasDelete && i == 1) ? 0xFF6B6B : 0xE0E0E0;
            int textColor = hovered ? (textAlpha << 24 | 0xFFFFFF) : (textAlpha << 24 | baseColor);
            int textY = iy + (CTX_ITEM_H - mc.font.lineHeight) / 2 + 1;
            graphics.drawString(mc.font, icons[i], mx0 + CTX_PAD + 2, textY, textColor, false);
            graphics.drawString(mc.font, labels[i], mx0 + CTX_PAD + CTX_ICON_COL, textY, textColor, false);
        }
    }

    private void cr$ctxFillClipped(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int clipTop, int clipBot) {
        y0 = Math.max(y0, clipTop);
        y1 = Math.min(y1, clipBot);
        if (y0 >= y1 || x0 >= x1) return;
        g.fill(x0, y0, x1, y1, color);
    }

    private Entry confirmDeleteEntry = null;
    private int confirmDeleteFolderIndex = -1;
    private static final int CONFIRM_W = 200;
    private static final int CONFIRM_H = 76;
    private static final int CONFIRM_BTN_H = 18;

    private boolean cr$handleConfirmClick(int screenW, int screenH, double mx, double my, int button) {
        if (button != 0) {
            return true;
        }
        int cx = (screenW - CONFIRM_W) / 2;
        int cy = (screenH - CONFIRM_H) / 2;
        int btnW = (CONFIRM_W - CTX_PAD * 3) / 2;
        int btnY = cy + CONFIRM_H - CONFIRM_BTN_H - CTX_PAD;
        int yesX = cx + CTX_PAD;
        int noX = cx + CTX_PAD * 2 + btnW;

        if (my >= btnY && my < btnY + CONFIRM_BTN_H) {
            if (mx >= yesX && mx < yesX + btnW) {
                if (confirmDeleteEntry != null) {
                    cr$deleteConfirmed();
                } else {
                    cr$deleteFolderConfirmed();
                }
                return true;
            }
            if (mx >= noX && mx < noX + btnW) {
                confirmDeleteEntry = null;
                confirmDeleteFolderIndex = -1;
                return true;
            }
        }

        boolean insideModal = mx >= cx && mx < cx + CONFIRM_W && my >= cy && my < cy + CONFIRM_H;
        if (!insideModal) {
            confirmDeleteEntry = null;
            confirmDeleteFolderIndex = -1;
        }
        return true;
    }

    private void cr$deleteConfirmed() {
        Entry e = confirmDeleteEntry;
        confirmDeleteEntry = null;
        if (e == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (e.textureId != null) {
            try {
                mc.getTextureManager().release(e.textureId);
            } catch (Exception ignored) {
            }
        }
        entries = new ArrayList<>(entries);
        entries.remove(e);
        Thread t = new Thread(() -> {
            try {
                Files.deleteIfExists(e.file.toPath());
            } catch (Exception ignored) {
            }
        });
        t.setDaemon(true);
        t.setName("Chat Remastered-DeleteScreenshot");
        t.start();
    }

    private void cr$deleteFolderConfirmed() {
        int index = confirmDeleteFolderIndex;
        confirmDeleteFolderIndex = -1;
        if (index <= 0) return;
        List<String> folders = ChatRemasteredConfig.getScreenshotFolders();
        int i = index - 1;
        if (i < 0 || i >= folders.size()) return;
        String path = folders.get(i);
        ChatRemasteredConfig.removeScreenshotFolder(path);
        if (activeFolderIndex == index) {
            switchFolder(0);
        } else if (activeFolderIndex > index) {
            activeFolderIndex--;
            ChatRemasteredConfig.setLastScreenshotFolderIndex(activeFolderIndex);
        }
    }

    private void cr$renderConfirmModal(GuiGraphics graphics, int screenW, int screenH, int mouseX, int mouseY) {
        if (confirmDeleteEntry == null && confirmDeleteFolderIndex == -1) return;
        Minecraft mc = Minecraft.getInstance();

        graphics.fill(0, 0, screenW, screenH, 0x99000000);

        int cx = (screenW - CONFIRM_W) / 2;
        int cy = (screenH - CONFIRM_H) / 2;
        graphics.fill(cx, cy, cx + CONFIRM_W, cy + CONFIRM_H, 0xF0181818);
        graphics.fill(cx, cy, cx + CONFIRM_W, cy + 1, 0x55FFFFFF);
        graphics.fill(cx, cy + CONFIRM_H - 1, cx + CONFIRM_W, cy + CONFIRM_H, 0x55FFFFFF);
        graphics.fill(cx, cy + 1, cx + 1, cy + CONFIRM_H - 1, 0x55FFFFFF);
        graphics.fill(cx + CONFIRM_W - 1, cy + 1, cx + CONFIRM_W, cy + CONFIRM_H - 1, 0x55FFFFFF);

        boolean isFolder = confirmDeleteEntry == null;
        String title = isFolder
                ? ChatRemasteredConfig.tr("chat-remastered.remove_folder_confirm_title")
                : ChatRemasteredConfig.tr("chat-remastered.delete_screenshot_confirm_title");
        graphics.drawCenteredString(mc.font, title, cx + CONFIRM_W / 2, cy + CTX_PAD + 2, 0xFFFFFFFF);

        String text = isFolder
                ? ChatRemasteredConfig.tr("chat-remastered.remove_folder_confirm_text")
                : ChatRemasteredConfig.tr("chat-remastered.delete_screenshot_confirm_text");
        int textY = cy + CTX_PAD + mc.font.lineHeight + 6;
        List<FormattedCharSequence> lines = mc.font.split(Component.literal(text), CONFIRM_W - CTX_PAD * 2);
        for (FormattedCharSequence line : lines) {
            graphics.drawCenteredString(mc.font, line, cx + CONFIRM_W / 2, textY, 0xFFAAAAAA);
            textY += mc.font.lineHeight + 1;
        }

        int btnW = (CONFIRM_W - CTX_PAD * 3) / 2;
        int btnY = cy + CONFIRM_H - CONFIRM_BTN_H - CTX_PAD;
        int yesX = cx + CTX_PAD;
        int noX = cx + CTX_PAD * 2 + btnW;

        boolean hoverYes = mouseX >= yesX && mouseX < yesX + btnW && mouseY >= btnY && mouseY < btnY + CONFIRM_BTN_H;
        boolean hoverNo = mouseX >= noX && mouseX < noX + btnW && mouseY >= btnY && mouseY < btnY + CONFIRM_BTN_H;

        graphics.fill(yesX, btnY, yesX + btnW, btnY + CONFIRM_BTN_H, hoverYes ? 0xFFAA3333 : 0xFF7A2323);
        graphics.fill(noX, btnY, noX + btnW, btnY + CONFIRM_BTN_H, hoverNo ? 0xFF444444 : 0xFF2E2E2E);

        String yesLabel = ChatRemasteredConfig.tr("chat-remastered.delete_screenshot_confirm_yes");
        String noLabel = ChatRemasteredConfig.tr("chat-remastered.delete_screenshot_confirm_no");
        graphics.drawCenteredString(mc.font, yesLabel, yesX + btnW / 2, btnY + (CONFIRM_BTN_H - mc.font.lineHeight) / 2, 0xFFFFFFFF);
        graphics.drawCenteredString(mc.font, noLabel, noX + btnW / 2, btnY + (CONFIRM_BTN_H - mc.font.lineHeight) / 2, 0xFFFFFFFF);
    }

    private void cr$pollDrag(int screenW, int screenH, int mouseX, int mouseY) {
        if (!draggingScrollbar && !draggingContent && !draggingFolderBar) {
            return;
        }
        boolean pressed;
        try {
            long handle = Minecraft.getInstance().getWindow().handle();
            pressed = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        } catch (Exception ex) {
            pressed = false;
        }
        if (!pressed) {
            draggingScrollbar = false;
            draggingContent = false;
            draggingFolderBar = false;
            return;
        }
        if (draggingFolderBar) {
            double dx = mouseX - dragLastX;
            folderBarScrollX -= (int) Math.round(dx);
            if (folderBarScrollX < 0) folderBarScrollX = 0;
            dragLastX = mouseX;
            return;
        }
        double dy = mouseY - dragLastY;
        if (draggingScrollbar) {
            int pw = PANEL_W;
            int listTop = HEADER_H + PADDING;
            int listBottom = screenH - PADDING - FOLDER_BAR_H - GAP;
            int tileH = tileHeight(pw);
            int rows = (entries.size() + 1) / 2;
            int contentH = rows * tileH + Math.max(0, rows - 1) * GAP;
            int viewH = listBottom - listTop;
            int maxScroll = Math.max(0, contentH - viewH);
            if (maxScroll > 0 && viewH > 0) {
                double ratio = (double) contentH / viewH;
                scrollY = Mth.clamp((int) Math.round(scrollY + dy * ratio), 0, maxScroll);
            }
        } else {
            scrollY -= (int) Math.round(dy);
            if (scrollY < 0) scrollY = 0;
        }
        dragLastY = mouseY;
    }

    public boolean mouseScrolled(int screenW, int screenH, double mx, double my, double scrollDelta) {
        if (!isOpen()) return false;
        if (confirmDeleteEntry != null || ctxMenuEntry != null || confirmDeleteFolderIndex != -1 || ctxMenuFolderIndex != -1) {
            return true;
        }
        int px = panelX(screenW, screenH);
        int pw = PANEL_W;
        if (mx < px || mx >= px + pw) {
            return true;
        }
        int barTop = screenH - PADDING - FOLDER_BAR_H;
        if (my >= barTop && my < barTop + FOLDER_BAR_H) {
            int step = 20;
            folderBarScrollX -= (int) Math.round(scrollDelta * step);
            if (folderBarScrollX < 0) folderBarScrollX = 0;
            return true;
        }
        int step = 30;
        scrollY -= (int) Math.round(scrollDelta * step);
        if (scrollY < 0) scrollY = 0;
        return true;
    }
}
