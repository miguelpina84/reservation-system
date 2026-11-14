package com.example.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "responsable", nullable = false, length = 120)
    private String responsable;

    @Column(name = "responsable_email", length = 160)
    private String responsableEmail;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.ACTIVA;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Reservation() {
        // JPA
    }

    public Reservation(Room room, String responsable, String responsableEmail,
                       LocalDateTime startTime, LocalDateTime endTime) {
        this.room = room;
        this.responsable = responsable;
        this.responsableEmail = responsableEmail;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = ReservationStatus.ACTIVA;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELADA;
    }

    public Long getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public String getResponsable() {
        return responsable;
    }

    public String getResponsableEmail() {
        return responsableEmail;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
