package com.example.cinema.service.impl;

import com.example.cinema.dto.RoomRequest;
import com.example.cinema.entity.CinemaItem;
import com.example.cinema.entity.Room;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.CinemaItemRepository;
import com.example.cinema.repository.RoomRepository;
import com.example.cinema.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final CinemaItemRepository cinemaItemRepository;

    @Override
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    @Override
    public List<Room> getRoomsByCinemaItem(Long cinemaItemId) {
        return roomRepository.findByCinemaItemId(cinemaItemId);
    }

    @Override
    public Room getRoomById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng với ID: " + id));
    }

    @Override
    @Transactional
    public Room createRoom(RoomRequest request) {
        CinemaItem cinemaItem = cinemaItemRepository.findById(request.getCinemaItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh rạp không tồn tại"));

        Room room = new Room();
        room.setName(request.getName());
        room.setTotalSeats(request.getTotalSeats());
        room.setCinemaItem(cinemaItem);
        
        return roomRepository.save(room);
    }

    @Override
    @Transactional
    public Room updateRoom(Long id, RoomRequest request) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));
        
        CinemaItem cinemaItem = cinemaItemRepository.findById(request.getCinemaItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh rạp không tồn tại"));

        room.setName(request.getName());
        room.setTotalSeats(request.getTotalSeats());
        room.setCinemaItem(cinemaItem);
        
        return roomRepository.save(room);
    }

    @Override
    @Transactional
    public void deleteRoom(Long id) {
        if (!roomRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy phòng để xóa");
        }
        roomRepository.deleteById(id);
    }
}