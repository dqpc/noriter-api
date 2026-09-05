package games.noriter.api.room.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRoomRequest(@NotBlank String gameId) {}
