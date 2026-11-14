package com.example.reservation.service;

import com.example.reservation.domain.Reservation;
import com.example.reservation.domain.ReservationStatus;
import com.example.reservation.domain.Room;
import com.example.reservation.repository.ReservationRepository;
import com.example.reservation.repository.RoomRepository;
import com.example.reservation.service.exception.BusinessRuleException;
import com.example.reservation.service.exception.ConflictException;
import com.example.reservation.service.exception.ResourceNotFoundException;
import com.example.reservation.web.dto.CreateReservationRequest;
import com.example.reservation.web.dto.ReservationResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;

    public ReservationService(ReservationRepository reservationRepository, RoomRepository roomRepository) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessRuleException("La fecha/hora de inicio debe ser anterior a la de fin");
        }

        Room room = roomRepository.findByIdForUpdate(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("No existe la sala con id " + request.roomId()));

        long solapadas = reservationRepository.countOverlapping(
                room.getId(), ReservationStatus.ACTIVA, request.startTime(), request.endTime());
        if (solapadas > 0) {
            throw new ConflictException("La sala '" + room.getName()
                    + "' ya está ocupada en el horario solicitado");
        }

        Reservation reservation = new Reservation(
                room,
                request.responsable().trim(),
                normalize(request.responsableEmail()),
                request.startTime(),
                request.endTime());
        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationResponse cancel(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la reserva con id " + id));
        if (reservation.getStatus() == ReservationStatus.CANCELADA) {
            throw new ConflictException("La reserva con id " + id + " ya estaba cancelada");
        }
        reservation.cancel();
        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> search(Long roomId, LocalDate date, Pageable pageable) {
        Specification<Reservation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (roomId != null) {
                predicates.add(cb.equal(root.get("room").get("id"), roomId));
            }
            if (date != null) {
                LocalDateTime dayStart = date.atStartOfDay();
                LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
                predicates.add(cb.lessThan(root.get("startTime"), dayEnd));
                predicates.add(cb.greaterThan(root.get("endTime"), dayStart));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return reservationRepository.findAll(spec, pageable).map(ReservationResponse::from);
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
