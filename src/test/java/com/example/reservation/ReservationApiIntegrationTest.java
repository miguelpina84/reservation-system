package com.example.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:reservationtest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@DisplayName("API de reservas - integración con MockMvc")
class ReservationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Test
    @DisplayName("crear sala requiere autenticación")
    void createRoomRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sala Tesla\",\"capacity\":6}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("crear sala autenticado responde 201 y la lista es pública")
    void createRoomAndList() throws Exception {
        String body = mockMvc.perform(post("/api/rooms")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sala Tesla\",\"capacity\":6,\"location\":\"Piso 3\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sala Tesla"))
                .andExpect(jsonPath("$.capacity").value(6))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).get("id").asLong()).isPositive();

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(3)));
    }

    @Test
    @DisplayName("segunda reserva sobre el mismo horario devuelve 409")
    void overlappingReservationReturns409() throws Exception {
        LocalDateTime start = LocalDateTime.of(2030, 3, 10, 9, 0);
        LocalDateTime end = LocalDateTime.of(2030, 3, 10, 10, 0);

        String payload = objectMapper.writeValueAsString(Map.of(
                "roomId", 1,
                "responsable", "Miguel Piña",
                "responsableEmail", "mpina@btech.com.mx",
                "startTime", start.format(ISO),
                "endTime", end.format(ISO)));

        mockMvc.perform(post("/api/reservations")
                        .with(httpBasic("user", "user123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(1));

        mockMvc.perform(post("/api/reservations")
                        .with(httpBasic("user", "user123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ya está ocupada")));
    }

    @Test
    @DisplayName("datos inválidos devuelven 400 con el detalle por campo")
    void invalidPayloadReturns400() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .with(httpBasic("user", "user123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":null,\"responsable\":\"\",\"startTime\":null,\"endTime\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.roomId").exists())
                .andExpect(jsonPath("$.fieldErrors.responsable").exists())
                .andExpect(jsonPath("$.fieldErrors.startTime").exists());
    }

    @Test
    @DisplayName("el listado filtra por sala y fecha, con paginación")
    void listWithFiltersAndPagination() throws Exception {
        LocalDateTime start = LocalDateTime.of(2030, 5, 20, 9, 0);
        LocalDateTime end = LocalDateTime.of(2030, 5, 20, 10, 0);
        String payload = objectMapper.writeValueAsString(Map.of(
                "roomId", 2,
                "responsable", "Ana",
                "startTime", start.format(ISO),
                "endTime", end.format(ISO)));

        mockMvc.perform(post("/api/reservations")
                        .with(httpBasic("user", "user123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reservations")
                        .param("roomId", "2")
                        .param("date", "2030-05-20")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));

        mockMvc.perform(get("/api/reservations").param("date", "2030-05-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("cancelar reserva requiere autenticación y libera el horario")
    void cancelReservation() throws Exception {
        LocalDateTime start = LocalDateTime.of(2030, 7, 1, 15, 0);
        LocalDateTime end = LocalDateTime.of(2030, 7, 1, 16, 0);
        String payload = objectMapper.writeValueAsString(Map.of(
                "roomId", 3,
                "responsable", "Patricia",
                "startTime", start.format(ISO),
                "endTime", end.format(ISO)));

        MvcResult created = mockMvc.perform(post("/api/reservations")
                        .with(httpBasic("user", "user123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/reservations/" + id))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/reservations/" + id).with(httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));

        // el horario queda libre otra vez
        mockMvc.perform(post("/api/reservations")
                        .with(httpBasic("user", "user123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }
}
