package games.noriter.api.game;

public class UnknownGameException extends RuntimeException {
    public UnknownGameException(String id) {
        super("unknown game: " + id);
    }
}
