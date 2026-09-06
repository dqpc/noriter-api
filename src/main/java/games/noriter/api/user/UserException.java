package games.noriter.api.user;

public class UserException extends RuntimeException {

    public enum Kind { INVALID, DUPLICATE, UNAUTHORIZED, NOT_FOUND }

    private final Kind kind;

    public UserException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
