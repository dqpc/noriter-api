package games.noriter.api.wall.web;

import games.noriter.api.wall.WallException;
import games.noriter.api.wall.WallService;
import games.noriter.api.wall.web.dto.ErrorResponse;
import games.noriter.api.wall.web.dto.WallPostRequest;
import games.noriter.api.wall.web.dto.WallPostResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wall/posts")
@RequiredArgsConstructor
class WallController {

    private final WallService wall;

    private static Long userId(Jwt jwt) {
        return jwt == null ? null : Long.parseLong(jwt.getSubject());
    }

    @GetMapping
    List<WallPostResponse> today(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String guestToken) {
        return wall.today(userId(jwt), guestToken).stream().map(WallPostResponse::from).toList();
    }

    @PutMapping("/today")
    WallPostResponse write(@AuthenticationPrincipal Jwt jwt, @RequestBody @Validated WallPostRequest req, HttpServletRequest http) {
        var visitorKey = clientIp(http) + "|" + http.getHeader("User-Agent");
        return WallPostResponse.from(wall.write(userId(jwt), req.guestToken(), req.guestName(), req.characterId(), req.content(), visitorKey));
    }

    @DeleteMapping("/today")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String guestToken) {
        wall.deleteToday(userId(jwt), guestToken);
    }

    /** Cloudflare 워커를 거쳐 오므로 원 IP 는 헤더에 있다 */
    private static String clientIp(HttpServletRequest request) {
        var cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) return cf.trim();
        var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErrorResponse> onBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID", "요청 형식이 잘못되었습니다"));
    }

    @ExceptionHandler(WallException.class)
    ResponseEntity<ErrorResponse> onWallException(WallException e) {
        var status = switch (e.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NICKNAME_TAKEN, ALREADY_POSTED -> HttpStatus.CONFLICT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(e.kind().name(), e.getMessage()));
    }
}
