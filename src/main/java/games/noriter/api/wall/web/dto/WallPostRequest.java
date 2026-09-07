package games.noriter.api.wall.web.dto;

import jakarta.validation.constraints.NotNull;

/** JWT 가 있으면 guest 필드는 무시된다 */
public record WallPostRequest(@NotNull String content, String guestToken, String guestName, String characterId) {}
