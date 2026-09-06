package games.noriter.api.user.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(@NotBlank String nickname, @NotBlank String password, String email, String characterId) {}
