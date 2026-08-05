package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ReplyChatPacket(
        String sender,
        Component senderComponent,
        String text,
        String replyToSender,
        String replyToText,
        String replyToImageId
) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "reply_chat");
    public static final CustomPacketPayload.Type<ReplyChatPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ReplyChatPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.sender, 64);
                ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, pkt.senderComponent);
                buf.writeUtf(pkt.text, 256);
                buf.writeUtf(pkt.replyToSender, 64);
                buf.writeUtf(pkt.replyToText, 256);
                buf.writeUtf(pkt.replyToImageId, 64);
            },
            buf -> new ReplyChatPacket(
                    buf.readUtf(64),
                    ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf),
                    buf.readUtf(256),
                    buf.readUtf(64),
                    buf.readUtf(256),
                    buf.readUtf(64)
            )
    );

    @Override
    public CustomPacketPayload.Type<ReplyChatPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
    }
}
