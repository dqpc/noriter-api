package games.noriter.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "noriter")
public record NoriterProperties(Cors cors, Game game, Auth auth) {

    public record Cors(List<String> allowedOrigins) {}

    public record Game(boolean devOptions) {}

    /** JWT 서명 비밀. HS256 이라 32바이트 이상. */
    public record Auth(String secret) {}
}
