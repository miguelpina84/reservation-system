package com.example.reservation.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Respuesta paginada autocontenida (evita exponer la estructura interna de Spring Data). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
