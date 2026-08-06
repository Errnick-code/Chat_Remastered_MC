package dev.errnicraft.chatremastered.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

public final class NetworkComponentJson {

    private static final Gson GSON = new Gson();

    private NetworkComponentJson() {
    }

    public static String toJson(Component component) {
        JsonElement json = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, component).getOrThrow();
        return GSON.toJson(json);
    }

    public static Component fromJson(String json) {
        JsonElement element = GSON.fromJson(json, JsonElement.class);
        return ComponentSerialization.CODEC.decode(JsonOps.INSTANCE, element).getOrThrow().getFirst();
    }
}
