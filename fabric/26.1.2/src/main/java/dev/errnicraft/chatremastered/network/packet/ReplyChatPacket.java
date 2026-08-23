package dev.errnicraft.chatremastered.network.packet;

import dev.errnicraft.chatremastered.network.NetworkComponentJson;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ReplyChatPacket(
        String sender,
        Component senderComponent,
        String text,
        String replyToSender,
        String replyToText,
        String replyToImageId,
        String groupLabel,
        int bodyRgb
) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "reply_chat");
    public static final CustomPacketPayload.Type<ReplyChatPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ReplyChatPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(NetworkComponentJson.trunc(pkt.sender, 64), 64);
                buf.writeUtf(NetworkComponentJson.toJson(pkt.senderComponent));
                buf.writeUtf(NetworkComponentJson.trunc(pkt.text, 256), 256);
                buf.writeUtf(NetworkComponentJson.trunc(pkt.replyToSender, 64), 64);
                buf.writeUtf(NetworkComponentJson.trunc(pkt.replyToText, 256), 256);
                buf.writeUtf(NetworkComponentJson.trunc(pkt.replyToImageId, 64), 64);
                buf.writeUtf(NetworkComponentJson.trunc(pkt.groupLabel, 48), 48);
                buf.writeInt(pkt.bodyRgb);
            },
            buf -> new ReplyChatPacket(
                    buf.readUtf(64),
                    NetworkComponentJson.fromJson(buf.readUtf()),
                    buf.readUtf(256),
                    buf.readUtf(64),
                    buf.readUtf(256),
                    buf.readUtf(64),
                    buf.readUtf(48),
                    buf.readInt()
            )
    );

    public ReplyChatPacket(String sender, Component senderComponent, String text,
                           String replyToSender, String replyToText, String replyToImageId) {
        this(sender, senderComponent, text, replyToSender, replyToText, replyToImageId, "", 0);
    }

    @Override
    public CustomPacketPayload.Type<ReplyChatPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TYPE, STREAM_CODEC);
    }
}
