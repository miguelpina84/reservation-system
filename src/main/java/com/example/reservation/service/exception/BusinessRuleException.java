package com.example.reservation.service.exception;

/** Regla de negocio incumplida en los datos enviados (HTTP 400). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
