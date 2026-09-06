package games.noriter.api.score.web.dto;

/** moves: seed 로 재생할 수 있는 게임(2048)의 입력 로그. 없으면 경과 시간 대비 상한으로만 본다 */
public record FinishPlayRequest(Long score, String moves) {}
