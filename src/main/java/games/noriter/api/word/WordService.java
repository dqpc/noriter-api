package games.noriter.api.word;

import games.noriter.api.score.ScoreService;
import games.noriter.api.word.domain.WordPuzzle;
import games.noriter.api.word.domain.WordResult;
import games.noriter.api.word.infra.WordDictionaryRepository;
import games.noriter.api.word.infra.WordPuzzleRepository;
import games.noriter.api.word.infra.WordResultRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 글딱지. 정답은 서버만 알고, 클라이언트는 추측을 보내 자모별 판정을 받는다. */
@Service
@RequiredArgsConstructor
public class WordService {

    public static final String GAME_ID = "word";
    public static final int TRIES = 6;

    private final WordPuzzleRepository puzzles;
    private final WordDictionaryRepository dictionary;
    private final WordResultRepository results;
    private final ScoreService scores;
    private final Clock clock;

    @Transactional(readOnly = true)
    public WordToday today() {
        var date = WordCalendar.today(clock);
        return new WordToday(WordCalendar.numberOf(date), date, TRIES, WordJamo.LENGTH, WordCalendar.resetAt(date));
    }

    @Transactional(readOnly = true)
    public List<WordJudge.Status> guess(int number, String jamo) {
        requireOpen(number);
        if (!WordJamo.isValid(jamo)) throw new WordException(WordException.Kind.INVALID, "자모 6개로 풀어 쓴 단어여야 합니다");
        if (!dictionary.existsById(jamo)) throw new WordException(WordException.Kind.NOT_IN_DICTIONARY, "아, 목록에 단어가 없네요.");
        return WordJudge.judge(answerOf(number).jamo(), jamo);
    }

    @Transactional(readOnly = true)
    public boolean isWord(String jamo) {
        return WordJamo.isValid(jamo) && dictionary.existsById(jamo);
    }

    /** 지난 문제의 정답만 공개한다. 오늘 것은 결과를 보내야 알 수 있다. */
    @Transactional(readOnly = true)
    public WordAnswer pastAnswer(int number) {
        if (number < 1 || number >= WordCalendar.numberOf(WordCalendar.today(clock))) {
            throw new WordException(WordException.Kind.NOT_FOUND, "아직 공개되지 않은 문제입니다");
        }
        return answerOf(number);
    }

    /** 한 판 종료. 계정이면 하루 한 번만 기록하고(재제출은 무시) 이용 기록도 남긴다. */
    @Transactional
    public WordAnswer finish(int number, Long userId, Integer attempts, boolean hard) {
        requireOpen(number);
        if (attempts != null && (attempts < 1 || attempts > TRIES)) {
            throw new WordException(WordException.Kind.INVALID, "attempts 는 1~6 이거나 비어 있어야 합니다");
        }
        if (userId != null && results.findByUserIdAndNumber(userId, number).isEmpty()) {
            results.save(new WordResult(userId, number, attempts, hard, Instant.now(clock)));
            scores.recordSolo(GAME_ID, userId, attempts == null ? 0L : (long) (TRIES + 1 - attempts));
        }
        return answerOf(number);
    }

    @Transactional(readOnly = true)
    public WordStats stats(Long userId) {
        var all = results.findByUserIdOrderByNumberAsc(userId);
        var won = new HashSet<Integer>();
        var distribution = new ArrayList<>(Collections.nCopies(TRIES, 0));
        for (var r : all) {
            if (!r.won()) continue;
            won.add(r.getNumber());
            distribution.set(r.getAttempts() - 1, distribution.get(r.getAttempts() - 1) + 1);
        }
        int max = 0;
        int run = 0;
        Integer prev = null;
        for (var r : all) {
            if (r.won()) {
                run = prev != null && prev == r.getNumber() - 1 && won.contains(prev) ? run + 1 : 1;
                max = Math.max(max, run);
            } else run = 0;
            prev = r.getNumber();
        }
        // 오늘 아직 안 풀었으면 어제까지의 연속을 살려 둔다
        int cursor = WordCalendar.numberOf(WordCalendar.today(clock));
        if (!won.contains(cursor)) cursor--;
        int current = 0;
        while (won.contains(cursor)) {
            current++;
            cursor--;
        }
        int played = all.size();
        int winRate = played == 0 ? 0 : (int) Math.round(100.0 * won.size() / played);
        return new WordStats(played, won.size(), winRate, current, max, distribution);
    }

    private void requireOpen(int number) {
        int today = WordCalendar.numberOf(WordCalendar.today(clock));
        if (number != today && number != today - 1) {
            throw new WordException(WordException.Kind.INVALID, "오늘이나 어제 문제만 풀 수 있습니다");
        }
    }

    private WordAnswer answerOf(int number) {
        long count = puzzles.count();
        if (count == 0) throw new WordException(WordException.Kind.NOT_FOUND, "문제가 준비되지 않았습니다");
        int index = (int) ((number - 1) % count) + 1;
        WordPuzzle p = puzzles.findById(index)
                .orElseThrow(() -> new WordException(WordException.Kind.NOT_FOUND, "문제가 준비되지 않았습니다"));
        return new WordAnswer(p.getJamo(), p.getWord(), p.getMeaning());
    }
}
