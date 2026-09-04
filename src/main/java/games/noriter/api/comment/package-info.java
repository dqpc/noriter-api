/**
 * 댓글 모듈 (예약). 게임별 댓글, 신고, 숨김을 담당할 예정.
 * user 모듈의 UserService 만 참조하고, 점수 모듈과는 이벤트로만 상호작용한다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Comment")
package games.noriter.api.comment;
