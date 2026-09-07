package games.noriter.api.wall.infra;

import games.noriter.api.wall.domain.WallPost;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WallPostRepository extends JpaRepository<WallPost, Long> {

    Optional<WallPost> findByUserIdAndPostDay(Long userId, LocalDate day);

    Optional<WallPost> findByGuestTokenAndPostDay(String guestToken, LocalDate day);

    List<WallPost> findByPostDayAndDeletedAtIsNullOrderByCreatedAtDesc(LocalDate day, Pageable pageable);

    /** 같은 기기(IP+브라우저 해시)의 다른 게스트 토큰이 오늘 살아 있는 글을 남겼는지 — 저장소를 지우고 다시 쓰는 우회를 막는다 */
    @Query("select count(p) > 0 from WallPost p where p.postDay = :day and p.visitorHash = :hash and p.deletedAt is null"
            + " and p.guestToken is not null and p.guestToken <> :token")
    boolean existsOtherGuestToday(LocalDate day, String hash, String token);
}
