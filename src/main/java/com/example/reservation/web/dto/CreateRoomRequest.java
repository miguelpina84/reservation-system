package com.example.reservation.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(

        @NotBlank(message = "El nombre de la sala es obligatorio")
        @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
        String name,

        @Positive(message = "La capacidad debe ser mayor que 0")
        int capacity,

        @Size(max = 200, message = "La ubicación no puede exceder 200 caracteres")
        String location
) {
}
