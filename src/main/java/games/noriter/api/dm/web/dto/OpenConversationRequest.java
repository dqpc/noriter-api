package games.noriter.api.dm.web.dto;

import jakarta.validation.constraints.NotNull;

public record OpenConversationRequest(@NotNull Long userId) {}
