package com.example.reservation.service;

import com.example.reservation.domain.Reservation;
import com.example.reservation.domain.ReservationStatus;
import com.example.reservation.domain.Room;
import com.example.reservation.repository.ReservationRepository;
import com.example.reservation.repository.RoomRepository;
import com.example.reservation.service.exception.ConflictException;
import com.example.reservation.web.dto.CreateReservationRequest;
import com.example.reservation.web.dto.ReservationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationService - reglas de solapamiento")
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RoomRepository roomRepository;

    private ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationRepository, roomRepository);
    }

    @Test
    @DisplayName("lanza ConflictException cuando la sala ya está ocupada en el horario")
    void shouldRejectOverlappingReservation() {
        Room room = new Room("Sala Ada", 8, "Piso 1");
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 1, 11, 0);

        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(reservationRepository.countOverlapping(any(), any(), any(), any())).thenReturn(1L);

        CreateReservationRequest request = new CreateReservationRequest(1L, "Miguel", null, start, end);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya está ocupada");
    }

    @Test
    @DisplayName("crea la reserva cuando no hay solapamiento")
    void shouldCreateReservationWhenNoOverlap() {
        Room room = new Room("Sala Ada", 8, "Piso 1");
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 1, 11, 0);

        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(reservationRepository.countOverlapping(any(), any(), any(), any())).thenReturn(0L);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response =
                service.create(new CreateReservationRequest(1L, "Miguel", "mpina@x.com", start, end));

        assertThat(response.status()).isEqualTo(ReservationStatus.ACTIVA);
        assertThat(response.roomName()).isEqualTo("Sala Ada");
        assertThat(response.startTime()).isEqualTo(start);
    }
}
