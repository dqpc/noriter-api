package games.noriter.api.notification.web;

import games.noriter.api.notification.NotificationService;
import games.noriter.api.notification.web.dto.NotificationListResponse;
import games.noriter.api.notification.web.dto.ReadRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/notifications")
@RequiredArgsConstructor
class NotificationController {

    private final NotificationService notifications;

    @GetMapping
    NotificationListResponse list(@AuthenticationPrincipal Jwt jwt) {
        var userId = Long.parseLong(jwt.getSubject());
        return NotificationListResponse.from(notifications.unreadCount(userId), notifications.list(userId));
    }

    /** `{read: true}` 로 전부 읽음 처리 */
    @PatchMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void readAll(@AuthenticationPrincipal Jwt jwt, @RequestBody ReadRequest req) {
        if (req.read()) notifications.markAllRead(Long.parseLong(jwt.getSubject()));
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody ReadRequest req) {
        if (req.read()) notifications.markRead(Long.parseLong(jwt.getSubject()), id);
    }
}
