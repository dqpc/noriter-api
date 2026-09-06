package games.noriter.api.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** 모듈 테스트가 같은 H2 를 공유하므로, 사용자 관련 테이블을 FK 순서대로 비운다. */
public final class Tables {

    public static void clearUsers(JdbcTemplate jdbc) {
        jdbc.update("delete from message");
        jdbc.update("delete from conversation_member");
        jdbc.update("delete from conversation");
        jdbc.update("delete from notification");
        jdbc.update("delete from game_play");
        jdbc.update("delete from game_score");
        jdbc.update("delete from friend");
        jdbc.update("delete from app_user");
    }

    public static void insertUser(JdbcTemplate jdbc, long id, String nickname) {
        jdbc.update("insert into app_user (id, provider, provider_id, nickname, created_at) values (?, 'local', ?, ?, now())",
                id, nickname.toLowerCase(), nickname);
    }

    private Tables() {}
}
