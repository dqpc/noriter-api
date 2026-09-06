package games.noriter.api.user.web;

import org.springframework.security.oauth2.jwt.Jwt;

final class Principal {

    static Long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    static Long userIdOrNull(Jwt jwt) {
        return jwt == null ? null : userId(jwt);
    }

    private Principal() {}
}
