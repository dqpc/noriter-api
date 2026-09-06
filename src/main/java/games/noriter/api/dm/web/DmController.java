package games.noriter.api.dm.web;

import games.noriter.api.dm.DmException;
import games.noriter.api.dm.DmService;
import games.noriter.api.dm.web.dto.ConversationResponse;
import games.noriter.api.dm.web.dto.MessageResponse;
import games.noriter.api.dm.web.dto.OpenConversationRequest;
import games.noriter.api.dm.web.dto.ReadRequest;
import games.noriter.api.dm.web.dto.SendMessageRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
class DmController {

    private final DmService dm;

    private static Long me(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    @GetMapping
    List<ConversationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return dm.list(me(jwt)).stream().map(ConversationResponse::from).toList();
    }

    /** 상대와의 대화를 열기. 있으면 그것, 없으면 새로 */
    @PostMapping
    ConversationResponse open(@AuthenticationPrincipal Jwt jwt, @RequestBody @Validated OpenConversationRequest req) {
        return ConversationResponse.from(dm.open(me(jwt), req.userId()));
    }

    @GetMapping("/{id}/messages")
    List<MessageResponse> messages(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestParam(required = false) Long before) {
        return dm.messages(me(jwt), id, before).stream().map(MessageResponse::from).toList();
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse send(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody @Validated SendMessageRequest req) {
        return MessageResponse.from(dm.send(me(jwt), id, req.text()));
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody ReadRequest req) {
        dm.markRead(me(jwt), id, req.lastReadMessageId());
    }

    @ExceptionHandler(DmException.class)
    ResponseEntity<Map<String, String>> onDmException(DmException e) {
        var status = switch (e.kind()) {
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
    }
}
