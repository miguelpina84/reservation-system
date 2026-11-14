package com.example.reservation.service.exception;

/** Conflicto de estado: la sala ya está ocupada en ese horario (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
