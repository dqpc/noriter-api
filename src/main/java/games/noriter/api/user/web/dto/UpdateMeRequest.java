package games.noriter.api.user.web.dto;

import games.noriter.api.user.Presence;

public record UpdateMeRequest(Presence presence, String characterId) {}
