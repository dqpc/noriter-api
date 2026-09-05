package games.noriter.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "noriter")
public record NoriterProperties(Cors cors, Game game) {

    public record Cors(List<String> allowedOrigins) {}

    public record Game(boolean devOptions) {}
}
