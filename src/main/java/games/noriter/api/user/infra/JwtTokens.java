package games.noriter.api.user.infra;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokens {

    static final Duration LIFETIME = Duration.ofDays(30);

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;

    public String issue(Long userId, String nickname) {
        var now = Instant.now(clock);
        var claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(LIFETIME))
                .claim("nickname", nickname)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public Optional<Long> subject(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(decoder.decode(token).getSubject()));
        } catch (JwtException | NumberFormatException e) {
            return Optional.empty();
        }
    }
}
