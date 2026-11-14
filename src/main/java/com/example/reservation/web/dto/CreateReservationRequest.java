package com.example.reservation.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateReservationRequest(

        @NotNull(message = "El id de la sala es obligatorio")
        Long roomId,

        @NotBlank(message = "El responsable es obligatorio")
        @Size(max = 120, message = "El responsable no puede exceder 120 caracteres")
        String responsable,

        @Email(message = "El correo del responsable no es válido")
        @Size(max = 160, message = "El correo no puede exceder 160 caracteres")
        String responsableEmail,

        @NotNull(message = "La fecha/hora de inicio es obligatoria")
        LocalDateTime startTime,

        @NotNull(message = "La fecha/hora de fin es obligatoria")
        LocalDateTime endTime
) {
}
