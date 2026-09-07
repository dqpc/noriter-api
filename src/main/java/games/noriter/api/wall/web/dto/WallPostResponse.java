package games.noriter.api.wall.web.dto;

import games.noriter.api.wall.WallPostView;
import java.time.Instant;

public record WallPostResponse(Long id, String nickname, boolean guest, String characterId, String content,
                               Instant createdAt, Instant updatedAt, boolean mine) {

    public static WallPostResponse from(WallPostView v) {
        return new WallPostResponse(v.id(), v.nickname(), v.guest(), v.characterId(), v.content(), v.createdAt(), v.updatedAt(), v.mine());
    }
}
