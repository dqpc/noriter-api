package games.noriter.api.config;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * 시각 정책: DB 와 서버 안에서는 UTC(Instant), 클라이언트로 나갈 때는 KST 오프셋 문자열.
 * 예) 2026-09-06T16:12:34.123+09:00. 브라우저의 Date.parse 가 그대로 읽는다.
 */
@Configuration
public class JacksonConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final DateTimeFormatter KST_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Bean
    SimpleModule kstInstantModule() {
        var module = new SimpleModule("kst-instant");
        module.addSerializer(Instant.class, new StdSerializer<>(Instant.class) {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(KST_FORMAT.format(value.atZone(KST)));
            }
        });
        return module;
    }
}
