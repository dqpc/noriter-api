package games.noriter.api.room;

public record RoomInvited(String roomId, String gameId, String gameName, Long fromUserId, String fromNickname, Long toUserId) {}
