package games.noriter.api.user.web.dto;

import games.noriter.api.user.AuthResult;

public record AuthResponse(String token, MeResponse user) {

    public static AuthResponse from(AuthResult r) {
        return new AuthResponse(r.token(), MeResponse.from(r.user()));
    }
}
