package games.noriter.api.room.web.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ClientMessage.Join.class, name = "join"),
        @JsonSubTypes.Type(value = ClientMessage.Settings.class, name = "settings"),
        @JsonSubTypes.Type(value = ClientMessage.Start.class, name = "start"),
        @JsonSubTypes.Type(value = ClientMessage.Score.class, name = "score"),
        @JsonSubTypes.Type(value = ClientMessage.Finish.class, name = "finish"),
        @JsonSubTypes.Type(value = ClientMessage.Chat.class, name = "chat"),
        @JsonSubTypes.Type(value = ClientMessage.Input.class, name = "input")
})
public sealed interface ClientMessage {

    record Join(String nickname) implements ClientMessage {}

    record Settings(Integer maxPlayers, Map<String, Object> options) implements ClientMessage {}

    record Start() implements ClientMessage {}

    record Score(long score) implements ClientMessage {}

    record Finish(long score) implements ClientMessage {}

    record Chat(String text) implements ClientMessage {}

    record Input(Map<String, Object> input) implements ClientMessage {}
}
