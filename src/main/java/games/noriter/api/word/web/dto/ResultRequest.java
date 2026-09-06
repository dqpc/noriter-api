package games.noriter.api.word.web.dto;

import jakarta.validation.constraints.NotNull;

/** attempts 는 1~6, 못 맞혔으면 null */
public record ResultRequest(@NotNull Integer number, Integer attempts, Boolean hard) {}
