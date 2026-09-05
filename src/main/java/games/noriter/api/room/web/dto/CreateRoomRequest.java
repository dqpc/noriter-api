package games.noriter.api.room.web.dto;

import games.noriter.api.game.GameMode;
import jakarta.validation.constraints.NotBlank;

public record CreateRoomRequest(@NotBlank String gameId, GameMode mode) {

    public GameMode modeOrDefault() {
        return mode == null ? GameMode.VERSUS : mode;
    }
}
