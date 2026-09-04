package games.noriter.api.room;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/rooms")
@Validated
class RoomController {

    record CreateRequest(@NotBlank String gameId) {}

    private final RoomService rooms;

    RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    RoomSnapshot create(@RequestBody @Validated CreateRequest req) {
        return rooms.create(req.gameId());
    }

    @GetMapping("/{roomId}")
    ResponseEntity<RoomSnapshot> get(@PathVariable String roomId) {
        return rooms.find(roomId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(games.noriter.api.game.UnknownGameException.class)
    ResponseEntity<String> unknownGame(games.noriter.api.game.UnknownGameException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
