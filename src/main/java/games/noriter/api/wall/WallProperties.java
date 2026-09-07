package games.noriter.api.wall;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** guestWrite 를 끄면 계정만 낙서할 수 있다 (낯선 사람이 늘어 문제가 생길 때 배포 없이 전환) */
@ConfigurationProperties(prefix = "noriter.wall")
public record WallProperties(@DefaultValue("true") boolean guestWrite) {}
