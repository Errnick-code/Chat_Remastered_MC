package dev.errnicraft.chatremastered.tcp;

public final class ImageTcpProtocol {

    public static final byte CMD_PING = 0x01;
    public static final byte CMD_UPLOAD = 0x02;
    public static final byte CMD_GET_FULL = 0x03;
    public static final byte CMD_GET_THUMB = 0x04;

    public static final byte RES_OK = 0x00;
    public static final byte RES_NOT_FOUND = 0x01;
    public static final byte RES_FORBIDDEN = 0x01;
    public static final byte RES_TOO_LARGE = 0x02;

    public static final String PING_BODY = "chatmedia-ok";

    private ImageTcpProtocol() {
    }
}
