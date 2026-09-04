package games.noriter.api.user;

/** 다른 모듈에 노출하는 유저 정보. 엔티티는 모듈 밖으로 나가지 않는다. */
public record UserSummary(Long id, String nickname) {}
