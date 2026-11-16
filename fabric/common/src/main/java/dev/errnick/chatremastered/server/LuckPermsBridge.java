package dev.errnick.chatremastered.server;

import java.util.UUID;

public final class LuckPermsBridge {

    private static volatile Object api;
    private static volatile boolean resolved;

    private LuckPermsBridge() {
    }

    private static synchronized Object api() {
        if (!resolved) {
            try {
                api = Class.forName("net.luckperms.api.LuckPermsProvider")
                        .getMethod("get").invoke(null);
            } catch (Throwable t) {
                api = null;
            }
            resolved = true;
        }
        return api;
    }

    public static boolean check(UUID uuid, String node, boolean defaultValue) {
        if (uuid == null) {
            return defaultValue;
        }
        try {
            Object lp = api();
            if (lp == null) {
                return defaultValue;
            }
            Object userManager = lp.getClass().getMethod("getUserManager").invoke(lp);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, uuid);
            if (user == null) {
                return defaultValue;
            }
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            Object result = permissionData.getClass().getMethod("checkPermission", String.class)
                    .invoke(permissionData, node);
            return (Boolean) result.getClass().getMethod("allowed").invoke(result);
        } catch (Throwable t) {
            return defaultValue;
        }
    }
}
