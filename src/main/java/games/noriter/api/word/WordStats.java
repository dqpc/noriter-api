package games.noriter.api.word;

import java.util.List;

/** 한 사용자의 오늘의 단어 전적. distribution 은 1~6번째에 맞힌 횟수. */
public record WordStats(int played, int won, int winRate, int currentStreak, int maxStreak, List<Integer> distribution) {}
