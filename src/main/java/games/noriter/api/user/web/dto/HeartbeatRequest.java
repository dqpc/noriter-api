package games.noriter.api.user.web.dto;

import games.noriter.api.user.Activity;

public record HeartbeatRequest(Activity activity, String gameId, String roomId) {}
