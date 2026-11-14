package com.example.reservation.service;

import com.example.reservation.domain.Room;
import com.example.reservation.repository.RoomRepository;
import com.example.reservation.service.exception.BusinessRuleException;
import com.example.reservation.service.exception.ResourceNotFoundException;
import com.example.reservation.web.dto.CreateRoomRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public Room create(CreateRoomRequest request) {
        if (roomRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new BusinessRuleException("Ya existe una sala con el nombre '" + request.name().trim() + "'");
        }
        Room room = new Room(request.name().trim(), request.capacity(), normalize(request.location()));
        return roomRepository.save(room);
    }

    @Transactional(readOnly = true)
    public List<Room> list() {
        return roomRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Room get(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la sala con id " + id));
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
