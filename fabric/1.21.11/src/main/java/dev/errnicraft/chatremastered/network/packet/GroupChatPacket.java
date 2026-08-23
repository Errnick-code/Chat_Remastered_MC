package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GroupChatPacket(String text, boolean inGroup, String groupName) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "group_chat");
    public static final CustomPacketPayload.Type<GroupChatPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, GroupChatPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.text, 256);
                buf.writeBoolean(pkt.inGroup);
                buf.writeUtf(pkt.groupName == null ? "" : pkt.groupName, 32);
            },
            buf -> new GroupChatPacket(buf.readUtf(256), buf.readBoolean(), buf.readUtf(32))
    );

    public static GroupChatPacket c2s(String text) {
        return new GroupChatPacket(text, true, "");
    }

    public static GroupChatPacket state(boolean inGroup, String groupName) {
        return new GroupChatPacket("", inGroup, groupName == null ? "" : groupName);
    }

    @Override
    public CustomPacketPayload.Type<GroupChatPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
    }
}
