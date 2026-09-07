package games.noriter.api.wall;

public class WallException extends RuntimeException {

    public enum Kind { INVALID, NICKNAME_TAKEN, ALREADY_POSTED, NOT_FOUND, FORBIDDEN }

    private final Kind kind;

    public WallException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
