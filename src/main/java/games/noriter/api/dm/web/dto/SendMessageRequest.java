package games.noriter.api.dm.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(@NotBlank String text) {}
