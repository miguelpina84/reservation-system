package com.example.reservation.web;

import com.example.reservation.service.ReservationService;
import com.example.reservation.web.dto.ApiError;
import com.example.reservation.web.dto.CreateReservationRequest;
import com.example.reservation.web.dto.PageResponse;
import com.example.reservation.web.dto.ReservationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservas", description = "Reservas de salas: crear, listar con filtros y cancelar")
public class ReservationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @Operation(summary = "Crear reserva",
            description = "Requiere credenciales. Si la sala ya esta ocupada en ese horario responde 409. "
                    + "El solapamiento es estricto: los bordes que se tocan no chocan.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reserva creada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos o rango horario incorrecto",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Sin credenciales"),
            @ApiResponse(responseCode = "404", description = "La sala no existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "La sala ya esta ocupada en ese horario",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse created = reservationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Listar reservas",
            description = "Publico. Filtra por sala y/o por dia, con paginacion.")
    public PageResponse<ReservationResponse> list(
            @Parameter(description = "Filtra por id de sala")
            @RequestParam(required = false) Long roomId,
            @Parameter(description = "Filtra por dia (YYYY-MM-DD); incluye las reservas que se solapan con ese dia")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Pagina (base 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamano de pagina (maximo 100)") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Campo de ordenamiento") @RequestParam(defaultValue = "startTime") String sortBy) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Sort sort = Sort.by(Sort.Direction.ASC, sortBy);

        return PageResponse.from(reservationService.search(roomId, date, PageRequest.of(safePage, safeSize, sort)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar una reserva",
            description = "Requiere credenciales. La cancelacion es logica: el horario queda libre y "
                    + "cancelar dos veces la misma reserva responde 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva cancelada"),
            @ApiResponse(responseCode = "401", description = "Sin credenciales"),
            @ApiResponse(responseCode = "404", description = "La reserva no existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "La reserva ya estaba cancelada",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ReservationResponse cancel(@PathVariable Long id) {
        return reservationService.cancel(id);
    }
}
