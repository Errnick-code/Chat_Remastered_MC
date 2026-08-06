package dev.errnicraft.chatremastered;

import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ChatRemasteredStore {

    public static final class ImageMessage {
        public final String imageId;
        public final String sender;
        public final String caption;
        public final int addedTime;
        public boolean dismissed = false;

        public final Component senderComponent;

        public String replyToSender = "";
        public String replyToText = "";
        public String replyToImageId = "";

        public int boundsX0 = 0;
        public int boundsY0 = 0;
        public int boundsX1 = 0;
        public int boundsY1 = 0;
        private boolean boundsSet = false;

        public final List<String> groupImageIds = new ArrayList<>();

        private int stripScrollOffset = 0;

        public final Map<String, int[]> rowCardBounds = new java.util.HashMap<>();

        private int rowScrollX = 0;

        public int getRowScrollX() {
            return rowScrollX;
        }

        public void setRowScrollX(int value) {
            rowScrollX = Math.max(0, value);
        }

        public void setRowCardBounds(String imageId, int x0, int y0, int x1, int y1) {
            rowCardBounds.put(imageId, new int[]{x0, y0, x1, y1});
        }

        public void clearRowCardBounds() {
            rowCardBounds.clear();
        }

        public Map<String, int[]> getRowCardBounds() {
            return rowCardBounds;
        }

        public ImageMessage(String imageId, String sender, String caption, int addedTime, Component senderComponent) {
            this.imageId = imageId;
            this.sender = sender;
            this.caption = caption;
            this.addedTime = addedTime;
            this.senderComponent = senderComponent;
        }

        public void attachGroupedImage(String otherImageId) {
            if (otherImageId != null && !otherImageId.isEmpty() && !otherImageId.equals(imageId)) {
                groupImageIds.add(otherImageId);
            }
        }

        public List<String> getGroupImageIds() {
            return groupImageIds;
        }

        public int getGroupSize() {
            return 1 + groupImageIds.size();
        }

        public boolean isGroup() {
            return !groupImageIds.isEmpty();
        }

        public String getActiveStripImageId() {
            if (stripScrollOffset <= 0 || stripScrollOffset > groupImageIds.size()) {
                return imageId;
            }
            return groupImageIds.get(stripScrollOffset - 1);
        }

        public int getStripScrollOffset() {
            return stripScrollOffset;
        }

        public void scrollStrip(int delta) {
            int max = groupImageIds.size();
            stripScrollOffset = Math.max(0, Math.min(max, stripScrollOffset + delta));
        }

        public void setScreenBounds(int x0, int y0, int x1, int y1) {
            boundsX0 = x0;
            boundsY0 = y0;
            boundsX1 = x1;
            boundsY1 = y1;
            boundsSet = true;
        }

        public boolean hasScreenBounds() {
            return boundsSet;
        }

        public int getBoundsX0() {
            return boundsX0;
        }

        public int getBoundsY0() {
            return boundsY0;
        }

        public int getBoundsX1() {
            return boundsX1;
        }

        public int getBoundsY1() {
            return boundsY1;
        }

        public String getImageId() {
            return imageId;
        }

        public String getSender() {
            return sender;
        }

        public String getCaption() {
            return caption;
        }

        public int getAddedTime() {
            return addedTime;
        }

        public boolean getDismissed() {
            return dismissed;
        }

        public void setDismissed(boolean value) {
            dismissed = value;
        }

        public Component getSenderComponent() {
            return senderComponent;
        }

        public String getReplyToSender() {
            return replyToSender;
        }

        public void setReplyToSender(String value) {
            replyToSender = value;
        }

        public String getReplyToText() {
            return replyToText;
        }

        public void setReplyToText(String value) {
            replyToText = value;
        }

        public String getReplyToImageId() {
            return replyToImageId;
        }

        public void setReplyToImageId(String value) {
            replyToImageId = value;
        }
    }

    public static final class ReplyMessage {
        private static final AtomicLong SEQ_COUNTER = new AtomicLong(0);

        public final String senderName;
        public final String text;
        public final String replyToSender;
        public final String replyToText;
        public final String replyToImageId;
        public int addedTime = -1;
        public int replyToAddedTime = -1;
        public boolean consumed = false;
        public int expectedAddedTime = -1;
        public final long createdAtMs = System.currentTimeMillis();

        public final Component senderComponent;

        public final long seq = SEQ_COUNTER.incrementAndGet();

        public ReplyMessage(String senderName, String text, String replyToSender, String replyToText,
                             String replyToImageId, Component senderComponent) {
            this.senderName = senderName;
            this.text = text;
            this.replyToSender = replyToSender;
            this.replyToText = replyToText;
            this.replyToImageId = replyToImageId;
            this.senderComponent = senderComponent;
        }

        public String getSenderName() {
            return senderName;
        }

        public String getText() {
            return text;
        }

        public String getReplyToSender() {
            return replyToSender;
        }

        public String getReplyToText() {
            return replyToText;
        }

        public String getReplyToImageId() {
            return replyToImageId;
        }

        public int getAddedTime() {
            return addedTime;
        }

        public void setAddedTime(int value) {
            addedTime = value;
        }

        public int getReplyToAddedTime() {
            return replyToAddedTime;
        }

        public void setReplyToAddedTime(int value) {
            replyToAddedTime = value;
        }

        public boolean getConsumed() {
            return consumed;
        }

        public void setConsumed(boolean value) {
            consumed = value;
        }

        public int getExpectedAddedTime() {
            return expectedAddedTime;
        }

        public void setExpectedAddedTime(int value) {
            expectedAddedTime = value;
        }

        public long getCreatedAtMs() {
            return createdAtMs;
        }

        public Component getSenderComponent() {
            return senderComponent;
        }

        public long getSeq() {
            return seq;
        }
    }

    public static final class EntityMessage {
        public final String sender;
        public final Component senderComponent;
        public final String targetPlayerName;
        public final String entityNamespace;
        public final String entityPath;
        public final String entityNbt;

        public final int customSize;
        public final int offsetX;
        public final int offsetY;
        public final String behavior;
        public final String caption;
        public final int addedTime;
        public boolean dismissed = false;

        public final String itemNamespace;
        public final String itemPath;
        public final String itemNbt;

        public transient Object cachedItemStack = null;

        public float rotateAngleDeg = 0.0f;
        public transient long lastRotateFrameNanos = 0L;

        public transient Object cachedPlayerEntity = null;
        public transient String cachedForUuid = null;

        public transient boolean skinResolvePending = false;
        public transient long lastSkinResolveAttemptMs = 0L;

        public int boundsX0 = 0;
        public int boundsY0 = 0;
        public int boundsX1 = 0;
        public int boundsY1 = 0;
        private boolean boundsSet = false;

        public EntityMessage(String sender, Component senderComponent, String targetPlayerName,
                              String behavior, String caption, int addedTime) {
            this(sender, senderComponent, targetPlayerName, null, null, null, behavior,
                    -1, 0, 0, caption, addedTime);
        }

        public EntityMessage(String sender, Component senderComponent, String targetPlayerName,
                              String entityNamespace, String entityPath, String entityNbt,
                              String behavior, int customSize, int offsetX, int offsetY,
                              String caption, int addedTime) {
            this(sender, senderComponent, targetPlayerName, entityNamespace, entityPath, entityNbt,
                    behavior, customSize, offsetX, offsetY, caption, addedTime, null, null, null);
        }

        public EntityMessage(String sender, Component senderComponent,
                              String itemNamespace, String itemPath, String itemNbt,
                              String caption, int addedTime) {
            this(sender, senderComponent, null, null, null, null,
                    "rotate", -1, 0, 0, caption, addedTime, itemNamespace, itemPath, itemNbt);
        }

        private EntityMessage(String sender, Component senderComponent, String targetPlayerName,
                               String entityNamespace, String entityPath, String entityNbt,
                               String behavior, int customSize, int offsetX, int offsetY,
                               String caption, int addedTime,
                               String itemNamespace, String itemPath, String itemNbt) {
            this.sender = sender;
            this.senderComponent = senderComponent;
            this.targetPlayerName = targetPlayerName;
            this.entityNamespace = entityNamespace;
            this.entityPath = entityPath;
            this.entityNbt = entityNbt;
            this.behavior = behavior;
            this.customSize = customSize;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.caption = caption;
            this.addedTime = addedTime;
            this.itemNamespace = itemNamespace;
            this.itemPath = itemPath;
            this.itemNbt = itemNbt;
        }

        public boolean isEntityMode() {
            return entityNamespace != null && !entityNamespace.isEmpty()
                    && entityPath != null && !entityPath.isEmpty();
        }

        public boolean isItemMode() {
            return itemNamespace != null && !itemNamespace.isEmpty()
                    && itemPath != null && !itemPath.isEmpty();
        }

        public void setScreenBounds(int x0, int y0, int x1, int y1) {
            boundsX0 = x0;
            boundsY0 = y0;
            boundsX1 = x1;
            boundsY1 = y1;
            boundsSet = true;
        }

        public boolean hasScreenBounds() {
            return boundsSet;
        }

        public String getSender() {
            return sender;
        }

        public Component getSenderComponent() {
            return senderComponent;
        }

        public String getTargetPlayerName() {
            return targetPlayerName;
        }

        public String getBehavior() {
            return behavior;
        }

        public String getCaption() {
            return caption;
        }

        public int getAddedTime() {
            return addedTime;
        }

        public boolean getDismissed() {
            return dismissed;
        }

        public void setDismissed(boolean value) {
            dismissed = value;
        }

        public String buildFullCode() {
            if (isItemMode()) {
                String nbt = itemNbt != null ? itemNbt : "";
                return "<chat_remastered:item:" + itemNamespace + ":" + itemPath + nbt + ">"
                        + (caption != null && !caption.isEmpty() ? " " + caption : "");
            }
            if (isEntityMode()) {
                String nbt = entityNbt != null ? entityNbt : "";
                StringBuilder sb = new StringBuilder("<chat_remastered:entity:")
                        .append(entityNamespace).append(":").append(entityPath).append(nbt)
                        .append(":").append(behavior);
                if (offsetX != 0 || offsetY != 0) {
                    sb.append(":").append(offsetX).append(":").append(offsetY);
                }
                if (customSize >= 0) {
                    if (offsetX == 0 && offsetY == 0) {
                        sb.append(":0:0");
                    }
                    sb.append(":").append(customSize / 1000f);
                }
                sb.append(">");
                if (caption != null && !caption.isEmpty()) sb.append(" ").append(caption);
                return sb.toString();
            }
            String sb = "<chat_remastered:player:" + targetPlayerName + ":" + behavior + ">"
                    + (caption != null && !caption.isEmpty() ? " " + caption : "");
            return sb;
        }
    }

    private static final int MAX_MESSAGES = 50;
    private static final int MAX_REPLIES = 200;
    private static final int MAX_ENTITY_MESSAGES = 50;

    private static final ArrayDeque<ImageMessage> messages = new ArrayDeque<>(50);
    private static final ArrayDeque<ReplyMessage> replies = new ArrayDeque<>(200);
    private static final ArrayDeque<EntityMessage> entityMessages = new ArrayDeque<>(50);

    private static final Map<String, ImageMessage> groupHeadByGroupId = new ConcurrentHashMap<>();

    private static final Map<String, Map.Entry<String, Long>> suppressNextPhotoMessage = new ConcurrentHashMap<>();

    private static final Map<String, java.util.Deque<Map.Entry<String, Long>>> suppressNextReplyMessage = new ConcurrentHashMap<>();

    private static final Map<String, File> originalFileMap = new ConcurrentHashMap<>();

    private static final Set<String> uploadErrorShown = ConcurrentHashMap.newKeySet();

    private ChatRemasteredStore() {
    }

    public static void markSuppressPhotoMessage(String senderName, String caption) {
        String text = (caption != null && !caption.isEmpty()) ? caption : "[photo]";
        suppressNextPhotoMessage.put(senderName, Map.entry(text, System.currentTimeMillis()));
    }

    public static boolean shouldSuppressMessage(String senderName, String text) {
        long now = System.currentTimeMillis();
        suppressNextPhotoMessage.entrySet().removeIf(e -> now - e.getValue().getValue() > 5000);
        Map.Entry<String, Long> entry = suppressNextPhotoMessage.get(senderName);
        if (entry == null) {
            return false;
        }
        String expected = entry.getKey();
        boolean matches = text.equals(expected) || text.startsWith(expected);
        if (matches) {
            suppressNextPhotoMessage.remove(senderName);
            return true;
        }
        return false;
    }

    public static boolean shouldSuppressMessageFuzzy(String raw) {
        long now = System.currentTimeMillis();
        suppressNextPhotoMessage.entrySet().removeIf(e -> now - e.getValue().getValue() > 5000);
        var iter = suppressNextPhotoMessage.entrySet().iterator();
        while (iter.hasNext()) {
            var e = iter.next();
            String senderName = e.getKey();
            String expected = e.getValue().getKey();

            if (raw.contains(senderName) && (raw.contains(expected) || raw.endsWith(expected))) {
                iter.remove();
                return true;
            }
        }
        return false;
    }

    public static void markSuppressReplyMessage(String senderName, String text) {
        suppressNextReplyMessage
                .computeIfAbsent(senderName, k -> new java.util.concurrent.ConcurrentLinkedDeque<>())
                .addLast(Map.entry(text, System.currentTimeMillis()));
    }

    public static boolean shouldSuppressReplyMessage(String senderName, String text) {
        long now = System.currentTimeMillis();
        java.util.Deque<Map.Entry<String, Long>> queue = suppressNextReplyMessage.get(senderName);
        if (queue == null) {
            return false;
        }

        while (true) {
            Map.Entry<String, Long> head = queue.peekFirst();
            if (head == null || now - head.getValue() <= 10_000L) {
                break;
            }
            queue.pollFirst();
        }

        java.util.Iterator<Map.Entry<String, Long>> it = queue.iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            String expected = entry.getKey();
            boolean matches = text.equals(expected) || text.startsWith(expected) || expected.startsWith(text);
            if (matches) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public static void markUploadErrorShown(String imageId) {
        uploadErrorShown.add(imageId);
    }

    public static boolean shouldSuppressImageErrorPacket(String imageId) {
        return uploadErrorShown.contains(imageId);
    }

    public static synchronized void addMessage(String imageId, String sender, String caption, int addedTime, Component senderComponent) {
        addMessageAndGet(imageId, sender, caption, addedTime, senderComponent);
    }

    public static synchronized ImageMessage addMessageAndGet(String imageId, String sender, String caption, int addedTime, Component senderComponent) {
        ImageMessage msg = new ImageMessage(imageId, sender, caption, addedTime, senderComponent);
        messages.addLast(msg);
        if (messages.size() > MAX_MESSAGES) {
            ImageMessage removed = messages.removeFirst();
            originalFileMap.remove(removed.imageId);
            groupHeadByGroupId.values().remove(removed);
        }
        return msg;
    }

    public static void registerGroupHead(String groupId, ImageMessage head) {
        if (groupId != null && !groupId.isEmpty() && head != null) {
            groupHeadByGroupId.put(groupId, head);
        }
    }

    public static synchronized boolean attachToGroup(String groupId, String imageId) {
        if (groupId == null || groupId.isEmpty()) {
            return false;
        }
        ImageMessage head = groupHeadByGroupId.get(groupId);
        if (head == null) {
            return false;
        }
        head.attachGroupedImage(imageId);
        return true;
    }

    public static synchronized void addReply(String senderName, String text, String replyToSender,
                                              String replyToText, String replyToImageId, Component senderComponent, int addedTime) {
        ReplyMessage reply = new ReplyMessage(senderName, text, replyToSender, replyToText, replyToImageId, senderComponent);
        if (addedTime >= 0) {
            reply.addedTime = addedTime;
            reply.expectedAddedTime = addedTime;
        }
        replies.addLast(reply);
        if (replies.size() > MAX_REPLIES) {
            replies.removeFirst();
        }
    }

    public static synchronized ReplyMessage getReplyForAddedTime(int addedTime) {
        ReplyMessage result = null;
        for (ReplyMessage r : replies) {
            if (r.addedTime == addedTime) {
                result = r;
            }
        }
        return result;
    }

    public static synchronized List<ReplyMessage> getPendingReplies() {
        List<ReplyMessage> result = new ArrayList<>();
        for (ReplyMessage r : replies) {
            if (r.addedTime < 0 && !r.consumed) {
                result.add(r);
            }
        }
        return result;
    }

    private static void pruneStaleReplies() {
        long now = System.currentTimeMillis();
        replies.removeIf(r -> r.addedTime < 0 && now - r.createdAtMs > 10_000L);
    }

    public static synchronized boolean consumePendingReply(String senderName, String text) {
        pruneStaleReplies();
        long now = System.currentTimeMillis();

        ReplyMessage best = null;
        for (ReplyMessage r : replies) {
            if (r.consumed || r.addedTime >= 0 || !r.senderName.equals(senderName)) {
                continue;
            }
            if (now - r.createdAtMs >= 10_000L) {
                continue;
            }
            if (best == null || r.seq < best.seq) {
                boolean candidateOk;
                if (text.isEmpty()) {
                    candidateOk = true;
                } else {

                    String itBody;
                    if (r.text.startsWith("<") && r.text.contains("> ")) {
                        itBody = r.text.substring(r.text.indexOf("> ") + 2);
                    } else {
                        itBody = r.text;
                    }

                    candidateOk = itBody.equals(text) || itBody.startsWith(text) || text.startsWith(itBody);
                }
                if (candidateOk) {
                    best = r;
                }
            }
        }
        if (best != null) {
            best.consumed = true;
            return true;
        }
        return false;
    }

    public static synchronized List<ReplyMessage> getRepliesList() {
        return new ArrayList<>(replies);
    }

    public static void storeOriginalFile(String imageId, File file) {
        originalFileMap.put(imageId, file);
    }

    public static File getOriginalFile(String imageId) {
        return originalFileMap.get(imageId);
    }

    public static synchronized void dismiss(String imageId) {
        for (ImageMessage m : messages) {
            if (m.imageId.equals(imageId)) {
                m.dismissed = true;
                break;
            }
        }
    }

    public static void dismissMessage(String imageId) {
        dismiss(imageId);
    }

    public static synchronized List<ImageMessage> getMessageList() {
        return new ArrayList<>(messages);
    }

    public static synchronized void addEntityMessage(String sender, Component senderComponent, String targetPlayerName,
                                                       String behavior, String caption, int addedTime) {
        entityMessages.addLast(new EntityMessage(sender, senderComponent, targetPlayerName, behavior, caption, addedTime));
        if (entityMessages.size() > MAX_ENTITY_MESSAGES) {
            entityMessages.removeFirst();
        }
    }

    public static synchronized void addEntityMobMessage(String sender, Component senderComponent,
                                                          String entityNamespace, String entityPath, String entityNbt,
                                                          String behavior, int customSize, int offsetX, int offsetY,
                                                          String caption, int addedTime) {
        entityMessages.addLast(new EntityMessage(sender, senderComponent, null,
                entityNamespace, entityPath, entityNbt, behavior, customSize, offsetX, offsetY, caption, addedTime));
        if (entityMessages.size() > MAX_ENTITY_MESSAGES) {
            entityMessages.removeFirst();
        }
    }

    public static synchronized void addItemMessage(String sender, Component senderComponent,
                                                     String itemNamespace, String itemPath, String itemNbt,
                                                     String caption, int addedTime) {
        entityMessages.addLast(new EntityMessage(sender, senderComponent,
                itemNamespace, itemPath, itemNbt, caption, addedTime));
        if (entityMessages.size() > MAX_ENTITY_MESSAGES) {
            entityMessages.removeFirst();
        }
    }

    public static synchronized List<EntityMessage> getEntityMessageList() {
        return new ArrayList<>(entityMessages);
    }

    public static synchronized void dismissEntityMessage(int addedTime) {
        for (EntityMessage m : entityMessages) {
            if (m.addedTime == addedTime) {
                m.dismissed = true;
                break;
            }
        }
    }

    public static synchronized void clear() {
        messages.clear();
        replies.clear();
        entityMessages.clear();
        originalFileMap.clear();
        uploadErrorShown.clear();
    }
}
