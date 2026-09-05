package games.noriter.api.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(NoriterProperties.class)
public class SchedulingConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
