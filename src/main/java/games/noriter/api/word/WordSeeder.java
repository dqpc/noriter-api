package games.noriter.api.word;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 단어·정답 목록을 TSV(단어, 자모6, 뜻풀이)에서 읽어 비어 있는 테이블에 넣는다. 3만 건이라 배치로. */
@Component
@RequiredArgsConstructor
public class WordSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WordSeeder.class);
    private static final int BATCH = 1000;

    private final WordProperties props;
    private final ResourceLoader resources;
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        if (props.seed()) seed();
    }

    @Transactional
    public void seed() {
        if (count("word_dictionary") == 0) {
            var rows = read(props.dictionary());
            insert("insert into word_dictionary (jamo, word, meaning) values (?, ?, ?)", rows, false);
            log.info("word_dictionary seeded: {} words", rows.size());
        }
        if (count("word_puzzle") == 0) {
            var rows = read(props.answers());
            insert("insert into word_puzzle (number, jamo, word, meaning) values (?, ?, ?, ?)", rows, true);
            log.info("word_puzzle seeded: {} answers", rows.size());
        }
    }

    private long count(String table) {
        Long n = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return n == null ? 0 : n;
    }

    /** 같은 자모로 풀리는 단어가 몇 개 있어(예: 쌍자음과 겹친 자음) 먼저 나온 것만 남긴다. */
    private List<String[]> read(String location) {
        var byJamo = new LinkedHashMap<String, String[]>();
        try (var in = new BufferedReader(new InputStreamReader(resources.getResource(location).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                var cols = line.split("\t", -1);
                if (cols.length < 2 || !WordJamo.isValid(cols[1])) continue;
                byJamo.putIfAbsent(cols[1], new String[] {cols[0], cols[1], cols.length > 2 ? cols[2] : ""});
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + location, e);
        }
        return new ArrayList<>(byJamo.values());
    }

    private void insert(String sql, List<String[]> rows, boolean numbered) {
        for (int from = 0; from < rows.size(); from += BATCH) {
            var chunk = rows.subList(from, Math.min(rows.size(), from + BATCH));
            var args = new ArrayList<Object[]>(chunk.size());
            for (int i = 0; i < chunk.size(); i++) {
                var r = chunk.get(i);
                args.add(numbered ? new Object[] {from + i + 1, r[1], r[0], r[2]} : new Object[] {r[1], r[0], r[2]});
            }
            jdbc.batchUpdate(sql, args);
        }
    }
}
