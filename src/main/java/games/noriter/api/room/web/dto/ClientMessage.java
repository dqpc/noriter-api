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
        @JsonSubTypes.Type(value = ClientMessage.Character.class, name = "character"),
        @JsonSubTypes.Type(value = ClientMessage.Ping.class, name = "ping"),
        @JsonSubTypes.Type(value = ClientMessage.Rematch.class, name = "rematch"),
        @JsonSubTypes.Type(value = ClientMessage.State.class, name = "state"),
        @JsonSubTypes.Type(value = ClientMessage.Action.class, name = "action"),
        @JsonSubTypes.Type(value = ClientMessage.Host.class, name = "host")
})
public sealed interface ClientMessage {

    record Join(String nickname, String character, String playerId, String token) implements ClientMessage {}

    record Settings(Integer maxPlayers, Map<String, Object> options) implements ClientMessage {}

    record Start() implements ClientMessage {}

    record Score(long score) implements ClientMessage {}

    /** moves: seed 로 시작한 게임의 입력 로그. 서버가 재생해 점수를 검증한다. 없으면 점수를 그대로 믿는다 */
    record Finish(long score, String moves) implements ClientMessage {}

    record Chat(String text) implements ClientMessage {}

    record Character(String character) implements ClientMessage {}

    record Ping() implements ClientMessage {}

    record Rematch() implements ClientMessage {}

    record State(Map<String, Object> state) implements ClientMessage {}

    record Action(Map<String, Object> action) implements ClientMessage {}

    /** 방장 넘기기. 방장만, 대기·종료 중에 */
    record Host(String playerId) implements ClientMessage {}
}
