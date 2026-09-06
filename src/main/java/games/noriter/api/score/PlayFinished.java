package games.noriter.api.score;

/** 혼자 하기 종료. score 는 서버가 확정한 점수, adjusted 는 클라이언트가 보낸 값과 달라졌는지 */
public record PlayFinished(long score, boolean adjusted) {}
