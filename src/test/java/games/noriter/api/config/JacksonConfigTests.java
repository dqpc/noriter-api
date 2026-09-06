package games.noriter.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class JacksonConfigTests {

    @Autowired ObjectMapper json;

    record Sample(Instant at) {}

    @Test
    void instantsGoOutAsKstOffsetStrings() {
        var out = json.writeValueAsString(new Sample(Instant.parse("2026-09-06T07:12:34.123Z")));
        assertThat(out).isEqualTo("{\"at\":\"2026-09-06T16:12:34.123+09:00\"}");
        assertThat(json.readValue(out, Sample.class).at()).isEqualTo(Instant.parse("2026-09-06T07:12:34.123Z"));
    }
}
