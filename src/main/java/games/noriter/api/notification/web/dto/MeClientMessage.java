package games.noriter.api.notification.web.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import games.noriter.api.user.Activity;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MeClientMessage.ActivityUpdate.class, name = "activity"),
        @JsonSubTypes.Type(value = MeClientMessage.Ping.class, name = "ping")
})
public sealed interface MeClientMessage {

    /** 지금 어느 화면에 있는지. 연결 직후와 바뀔 때마다 */
    record ActivityUpdate(Activity activity, String gameId, String roomId) implements MeClientMessage {}

    record Ping() implements MeClientMessage {}
}
