package games.noriter.api.room.web;

import games.noriter.api.game.UnknownGameException;
import games.noriter.api.room.RoomException;
import games.noriter.api.room.RoomService;
import games.noriter.api.room.web.dto.CreateRoomRequest;
import games.noriter.api.room.web.dto.RoomResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
class RoomController {

    private final RoomService rooms;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RoomResponse create(@RequestBody @Validated CreateRoomRequest req) {
        return RoomResponse.from(rooms.create(req.gameId(), req.modeOrDefault()));
    }

    @GetMapping("/{roomId}")
    ResponseEntity<RoomResponse> get(@PathVariable String roomId) {
        return rooms.find(roomId).map(RoomResponse::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler({UnknownGameException.class, RoomException.class})
    ResponseEntity<String> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
