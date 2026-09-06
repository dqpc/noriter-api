package games.noriter.api.user;

public record FriendView(Long id, String nickname, String characterId, PresenceView presence) {}
