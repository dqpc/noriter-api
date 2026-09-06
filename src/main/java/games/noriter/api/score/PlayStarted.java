package games.noriter.api.score;

/** 혼자 하기 시작. playId 는 종료 때 돌려줄 세션 식별자, seed 는 클라이언트가 판을 만들 난수 씨앗 */
public record PlayStarted(String playId, long seed) {}
