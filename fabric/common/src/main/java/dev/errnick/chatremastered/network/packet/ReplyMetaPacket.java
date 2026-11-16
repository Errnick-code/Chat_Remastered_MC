package dev.errnick.chatremastered.network.packet;

import dev.errnick.chatremastered.network.NetworkComponentJson;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ReplyMetaPacket(
        String text,
        String replyToSender,
        String replyToText,
        String replyToImageId
) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "reply_meta");
    public static final CustomPacketPayload.Type<ReplyMetaPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ReplyMetaPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(NetworkComponentJson.trunc(pkt.text, 256), 256);
                buf.writeUtf(NetworkComponentJson.trunc(pkt.replyToSender, 64), 64);
                buf.writeUtf(NetworkComponentJson.trunc(pkt.replyToText, 256), 256);
                buf.writeUtf(NetworkComponentJson.trunc(pkt.replyToImageId, 64), 64);
            },
            buf -> new ReplyMetaPacket(
                    buf.readUtf(256),
                    buf.readUtf(64),
                    buf.readUtf(256),
                    buf.readUtf(64)
            )
    );

    @Override
    public CustomPacketPayload.Type<ReplyMetaPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);
    }
}
