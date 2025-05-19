package common;

public enum MessageType {
    // Client -> Server messages
    LOGIN_REQUEST,
    REGISTER_REQUEST,
    JOIN_ROOM,
    LEAVE_ROOM,
    LIST_ROOMS,
    SEND_MESSAGE,
    LIST_CUR_ROOM,
    LIST_CMDS,
    QUIT,

    // Server -> Client messages
    LOGIN_RESPONSE,
    REGISTER_RESPONSE,
    ROOM_JOINED,
    ROOM_LEFT,
    ROOM_LIST,
    ROOM,
    CMDS,
    USER_JOINED,
    USER_LEFT,
    MESSAGE_RECEIVED,
    ERROR,
}