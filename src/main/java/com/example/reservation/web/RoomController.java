package com.example.reservation.web;

import com.example.reservation.domain.Room;
import com.example.reservation.service.RoomService;
import com.example.reservation.web.dto.ApiError;
import com.example.reservation.web.dto.CreateRoomRequest;
import com.example.reservation.web.dto.RoomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Salas", description = "Catalogo de salas de reuniones")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    @Operation(summary = "Crear sala", description = "Requiere credenciales. El nombre de la sala es unico.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sala creada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos o nombre duplicado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Sin credenciales")
    })
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        Room room = roomService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.from(room));
    }

    @GetMapping
    @Operation(summary = "Listar salas", description = "Publico, no requiere credenciales.")
    public List<RoomResponse> list() {
        return roomService.list().stream().map(RoomResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una sala por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sala encontrada"),
            @ApiResponse(responseCode = "404", description = "La sala no existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public RoomResponse get(@PathVariable Long id) {
        return RoomResponse.from(roomService.get(id));
    }
}
