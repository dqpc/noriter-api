package games.noriter.api.user.web;

import games.noriter.api.user.UserException;
import games.noriter.api.user.UserService;
import games.noriter.api.user.web.dto.AuthResponse;
import games.noriter.api.user.web.dto.ErrorResponse;
import games.noriter.api.user.web.dto.FriendResponse;
import games.noriter.api.user.web.dto.HeartbeatRequest;
import games.noriter.api.user.web.dto.LoginRequest;
import games.noriter.api.user.web.dto.MeResponse;
import games.noriter.api.user.web.dto.ProfileResponse;
import games.noriter.api.user.web.dto.RegisterRequest;
import games.noriter.api.user.web.dto.UpdateMeRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class UserController {

    private final UserService users;

    /** 닉네임으로 계정 찾기. 없으면 빈 목록 — 가입 가능 여부 확인용 */
    @GetMapping("/users")
    List<ProfileResponse> lookup(@RequestParam String nickname) {
        return users.findByNickname(nickname).map(ProfileResponse::from).map(List::of).orElse(List.of());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@RequestBody @Validated RegisterRequest req) {
        return AuthResponse.from(users.register(req.nickname(), req.password(), req.email(), req.characterId()));
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse login(@RequestBody @Validated LoginRequest req) {
        return AuthResponse.from(users.login(req.nickname(), req.password()));
    }

    @GetMapping("/users/me")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return MeResponse.from(users.me(Principal.userId(jwt)));
    }

    @PatchMapping("/users/me")
    MeResponse update(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateMeRequest req) {
        return MeResponse.from(users.update(Principal.userId(jwt), req.presence(), req.characterId()));
    }

    @PutMapping("/users/me/presence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void heartbeat(@AuthenticationPrincipal Jwt jwt, @RequestBody HeartbeatRequest req) {
        users.heartbeat(Principal.userId(jwt), req.activity(), req.gameId(), req.roomId());
    }

    @DeleteMapping("/users/me/presence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void offline(@AuthenticationPrincipal Jwt jwt) {
        users.offline(Principal.userId(jwt));
    }

    @GetMapping("/users/me/friends")
    List<FriendResponse> friends(@AuthenticationPrincipal Jwt jwt) {
        return users.friends(Principal.userId(jwt)).stream().map(FriendResponse::from).toList();
    }

    @PutMapping("/users/me/friends/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void addFriend(@AuthenticationPrincipal Jwt jwt, @PathVariable Long friendId) {
        users.addFriend(Principal.userId(jwt), friendId);
    }

    @DeleteMapping("/users/me/friends/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeFriend(@AuthenticationPrincipal Jwt jwt, @PathVariable Long friendId) {
        users.removeFriend(Principal.userId(jwt), friendId);
    }

    @GetMapping("/users/{userId}")
    ResponseEntity<ProfileResponse> profile(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return users.profile(userId, Principal.userIdOrNull(jwt)).map(ProfileResponse::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(UserException.class)
    ResponseEntity<ErrorResponse> onUserException(UserException e) {
        var status = switch (e.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case DUPLICATE -> HttpStatus.CONFLICT;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(e.getMessage()));
    }
}
