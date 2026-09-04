/**
 * 점수 모듈. 게임별 점수 제출과 리더보드를 담당한다.
 * user 모듈의 {@link games.noriter.api.user.UserService} 만 사용하고, 점수 제출 시
 * {@link games.noriter.api.score.ScoreSubmitted} 이벤트를 발행한다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Score")
package games.noriter.api.score;
