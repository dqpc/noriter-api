package games.noriter.api.dm;

public class DmException extends RuntimeException {

    public enum Kind { FORBIDDEN, NOT_FOUND, INVALID }

    private final Kind kind;

    public DmException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
