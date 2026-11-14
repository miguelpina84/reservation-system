package com.example.reservation.web.dto;

import com.example.reservation.domain.Room;

public record RoomResponse(Long id, String name, int capacity, String location) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getCapacity(), room.getLocation());
    }
}
