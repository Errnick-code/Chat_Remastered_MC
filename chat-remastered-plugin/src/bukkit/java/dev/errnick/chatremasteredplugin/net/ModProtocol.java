package dev.errnick.chatremasteredplugin.net;

public final class ModProtocol {

    public static final String MOD_PROTOCOL_VERSION = "1.161126";
    public static final String NAMESPACE = "chat-remastered";

    public static final String CH_ADMIN_DELETE_IMAGE = NAMESPACE + ":admin_delete_image";
    public static final String CH_CLIENT_HELLO = NAMESPACE + ":client_hello";
    public static final String CH_DELETE_IMAGE = NAMESPACE + ":delete_image";
    public static final String CH_ENTITY_BY_UUID_CHAT = NAMESPACE + ":entity_by_uuid_chat";
    public static final String CH_ENTITY_CHAT = NAMESPACE + ":entity_chat";
    public static final String CH_ENTITY_MOB_CHAT = NAMESPACE + ":entity_mob_chat";
    public static final String CH_HANDSHAKE_ERROR = NAMESPACE + ":handshake_error";
    public static final String CH_IMAGE_CHAT = NAMESPACE + ":image_chat";
    public static final String CH_IMAGE_DELETED = NAMESPACE + ":image_deleted";
    public static final String CH_IMAGE_ERROR = NAMESPACE + ":image_error";
    public static final String CH_IMAGE_UPLOADED = NAMESPACE + ":image_uploaded";
    public static final String CH_ITEM_CHAT = NAMESPACE + ":item_chat";
    public static final String CH_BLOCK_CHAT = NAMESPACE + ":block_chat";
    public static final String CH_PHOTO_DENIED = NAMESPACE + ":photo_denied";
    public static final String CH_PHOTO_UNBANNED = NAMESPACE + ":photo_unbanned";
    public static final String CH_REPLY_CHAT = NAMESPACE + ":reply_chat";
    public static final String CH_REPLY_META = NAMESPACE + ":reply_meta";
    public static final String CH_REPLY = NAMESPACE + ":reply";
    public static final String CH_SERVER_CONFIG = NAMESPACE + ":server_config";
    public static final String CH_SERVER_HELLO = NAMESPACE + ":server_hello";
    public static final String CH_GROUP_MODE = NAMESPACE + ":group_mode";
    public static final String CH_GROUP_CHAT = NAMESPACE + ":group_chat";

    public static final String[] ALL_CHANNELS = {
            CH_ADMIN_DELETE_IMAGE, CH_CLIENT_HELLO, CH_DELETE_IMAGE, CH_ENTITY_BY_UUID_CHAT,
            CH_ENTITY_CHAT, CH_ENTITY_MOB_CHAT, CH_HANDSHAKE_ERROR, CH_IMAGE_CHAT, CH_IMAGE_DELETED,
            CH_IMAGE_ERROR, CH_IMAGE_UPLOADED, CH_ITEM_CHAT, CH_BLOCK_CHAT, CH_PHOTO_DENIED, CH_PHOTO_UNBANNED,
            CH_REPLY_CHAT, CH_REPLY_META, CH_REPLY, CH_SERVER_CONFIG, CH_SERVER_HELLO,
            CH_GROUP_MODE, CH_GROUP_CHAT
    };

    private ModProtocol() {
    }
}
