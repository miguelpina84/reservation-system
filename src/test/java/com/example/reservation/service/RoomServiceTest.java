package com.example.reservation.service;

import com.example.reservation.domain.Room;
import com.example.reservation.repository.RoomRepository;
import com.example.reservation.service.exception.BusinessRuleException;
import com.example.reservation.web.dto.CreateRoomRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomService - reglas de salas")
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    private RoomService service;

    @BeforeEach
    void setUp() {
        service = new RoomService(roomRepository);
    }

    @Test
    @DisplayName("rechaza nombres de sala duplicados")
    void shouldRejectDuplicateName() {
        when(roomRepository.existsByNameIgnoreCase("Sala Ada")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateRoomRequest("Sala Ada", 8, "Piso 1")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe una sala");
    }

    @Test
    @DisplayName("guarda la sala normalizando nombre y ubicación")
    void shouldCreateRoom() {
        when(roomRepository.existsByNameIgnoreCase("Sala Ada")).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Room room = service.create(new CreateRoomRequest("  Sala Ada  ", 8, "  Piso 1  "));

        assertThat(room.getName()).isEqualTo("Sala Ada");
        assertThat(room.getLocation()).isEqualTo("Piso 1");
        assertThat(room.getCapacity()).isEqualTo(8);
    }
}
