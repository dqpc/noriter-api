package games.noriter.api.dm;

/** 개인 채널로 나가는 새 쪽지 */
public record DmPushed(String type, MessageView message, long unread) {
    public DmPushed(MessageView message, long unread) { this("dm", message, unread); }
}
