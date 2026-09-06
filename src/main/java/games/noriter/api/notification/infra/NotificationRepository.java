package games.noriter.api.notification.infra;

import games.noriter.api.notification.domain.Notification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<Notification> findByUserIdAndReadAtIsNull(Long userId);
    long countByUserIdAndReadAtIsNull(Long userId);
}
