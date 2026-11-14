package com.example.reservation.web.dto;

import com.example.reservation.domain.Reservation;
import com.example.reservation.domain.ReservationStatus;

import java.time.Instant;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long roomId,
        String roomName,
        String responsable,
        String responsableEmail,
        LocalDateTime startTime,
        LocalDateTime endTime,
        ReservationStatus status,
        Instant createdAt
) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getRoom().getId(),
                reservation.getRoom().getName(),
                reservation.getResponsable(),
                reservation.getResponsableEmail(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.getCreatedAt());
    }
}
