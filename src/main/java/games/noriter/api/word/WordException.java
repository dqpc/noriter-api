package games.noriter.api.word;

public class WordException extends RuntimeException {

    public enum Kind { INVALID, NOT_IN_DICTIONARY, NOT_FOUND, UNAUTHORIZED }

    private final Kind kind;

    public WordException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
