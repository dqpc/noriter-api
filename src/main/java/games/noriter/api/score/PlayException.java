package games.noriter.api.score;

/** 혼자 하기 세션 오류. 웹 계층이 kind 로 HTTP 상태를 고른다 */
public class PlayException extends RuntimeException {

    public enum Kind { NOT_FOUND, ALREADY_FINISHED, INVALID }

    private final Kind kind;

    public PlayException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
