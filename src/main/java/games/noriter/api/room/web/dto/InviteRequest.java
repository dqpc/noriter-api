package games.noriter.api.room.web.dto;

import jakarta.validation.constraints.NotNull;

public record InviteRequest(@NotNull Long userId) {}
