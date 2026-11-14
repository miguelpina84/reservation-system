package com.example.reservation.web;

import com.example.reservation.domain.Room;
import com.example.reservation.service.RoomService;
import com.example.reservation.web.dto.CreateRoomRequest;
import com.example.reservation.web.dto.RoomResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        Room room = roomService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.from(room));
    }

    @GetMapping
    public List<RoomResponse> list() {
        return roomService.list().stream().map(RoomResponse::from).toList();
    }

    @GetMapping("/{id}")
    public RoomResponse get(@PathVariable Long id) {
        return RoomResponse.from(roomService.get(id));
    }
}
