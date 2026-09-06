package games.noriter.api.user;

/** 남에게 보이는 접속 상태. state 가 OFFLINE 이면 나머지는 null. */
public record PresenceView(State state, Activity activity, String gameId, String roomId) {

    public enum State { ONLINE, AWAY, BUSY, OFFLINE }

    public static final PresenceView OFFLINE = new PresenceView(State.OFFLINE, null, null, null);

    /** 초대를 받을 수 있는 상태. 바쁨·오프라인은 아니다. */
    public boolean invitable() {
        return state == State.ONLINE || state == State.AWAY;
    }
}
