package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GroupModePacket(boolean enabled) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "group_mode");
    public static final CustomPacketPayload.Type<GroupModePacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, GroupModePacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeBoolean(pkt.enabled()),
            buf -> new GroupModePacket(buf.readBoolean())
    );

    @Override
    public CustomPacketPayload.Type<GroupModePacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);
    }
}
