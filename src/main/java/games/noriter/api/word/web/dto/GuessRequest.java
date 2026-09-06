package games.noriter.api.word.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GuessRequest(@NotNull Integer number, @NotBlank String jamo) {}
