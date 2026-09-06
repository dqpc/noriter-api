package games.noriter.api.word;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 시작 시 단어 테이블이 비어 있으면 이 TSV 로 채운다. 테스트는 작은 픽스처를 가리킨다. */
@ConfigurationProperties(prefix = "noriter.word")
public record WordProperties(
        @DefaultValue("true") boolean seed,
        @DefaultValue("classpath:word/answers.tsv") String answers,
        @DefaultValue("classpath:word/dictionary.tsv") String dictionary) {}
