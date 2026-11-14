package com.example.reservation;

import com.example.reservation.domain.Room;
import com.example.reservation.repository.RoomRepository;
import com.example.reservation.service.ReservationService;
import com.example.reservation.service.exception.ConflictException;
import com.example.reservation.web.dto.CreateReservationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dos peticiones simultaneas por el mismo horario en la misma sala: solo una debe ganar.
 * Esto ejercita el bloqueo pesimista de la sala (SELECT ... FOR UPDATE): la segunda
 * transaccion espera el turno, ve la reserva ya confirmada y responde 409.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:reservationconcurrency;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@DisplayName("Concurrencia - dos reservas simultaneas por el mismo horario")
class ReservationConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    @DisplayName("solo una de dos reservas concurrentes se crea; la otra recibe 409")
    void concurrentReservationsOnSameSlot() throws Exception {
        Room room = roomRepository.save(new Room("Sala Concurrencia", 10, "Piso 9"));
        LocalDateTime start = LocalDateTime.of(2031, 9, 9, 10, 0);
        LocalDateTime end = LocalDateTime.of(2031, 9, 9, 11, 0);

        CreateReservationRequest request =
                new CreateReservationRequest(room.getId(), "Concurrente", null, start, end);

        int hilos = 2;
        ExecutorService executor = Executors.newFixedThreadPool(hilos);
        CountDownLatch ready = new CountDownLatch(hilos);
        CountDownLatch go = new CountDownLatch(1);

        List<Callable<Object>> tareas = java.util.stream.IntStream.range(0, hilos)
                .mapToObj(i -> (Callable<Object>) () -> {
                    ready.countDown();
                    go.await();
                    try {
                        return reservationService.create(request);
                    } catch (ConflictException ex) {
                        return ex;
                    }
                })
                .toList();

        List<Future<Object>> futuros = tareas.stream().map(executor::submit).toList();
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        List<Object> resultados = new java.util.ArrayList<>();
        for (Future<Object> futuro : futuros) {
            resultados.add(futuro.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();

        long creadas = resultados.stream().filter(r -> !(r instanceof ConflictException)).count();
        long conflictos = resultados.stream().filter(ConflictException.class::isInstance).count();

        assertThat(creadas).as("solo una reserva debe crearse").isEqualTo(1);
        assertThat(conflictos).as("la otra debe recibir 409").isEqualTo(1);
    }
}
