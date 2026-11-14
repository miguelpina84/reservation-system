package com.example.reservation.repository;

import com.example.reservation.domain.Reservation;
import com.example.reservation.domain.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    /**
     * Cuenta reservas activas de la sala que se solapan con el intervalo [start, end).
     * Dos intervalos se solapan si start < endExistente && end > startExistente (bordes tocantes no se solapan).
     */
    @Query("""
            select count(r) from Reservation r
            where r.room.id = :roomId
              and r.status = :status
              and r.startTime < :end
              and r.endTime > :start
            """)
    long countOverlapping(@Param("roomId") Long roomId,
                          @Param("status") ReservationStatus status,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);
}
