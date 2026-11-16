package dev.errnick.chatremastered.client.chat;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LinkVisitedStore {

    private static final int MAX_ENTRIES = 512;

    private static final Map<String, Boolean> VISITED = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private LinkVisitedStore() {
    }

    public static synchronized boolean isVisited(String link) {
        return link != null && VISITED.containsKey(link);
    }

    public static synchronized boolean markVisited(String link) {
        if (link == null || link.isEmpty()) {
            return false;
        }
        return VISITED.put(link, Boolean.TRUE) == null;
    }
}
