package com.example.cinema.service.impl;

import com.example.cinema.dto.RoomRequest;
import com.example.cinema.entity.CinemaItem;
import com.example.cinema.entity.Room;
import com.example.cinema.entity.User;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.CinemaItemRepository;
import com.example.cinema.repository.RoomRepository;
import com.example.cinema.repository.UserRepository;
import com.example.cinema.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final CinemaItemRepository cinemaItemRepository;
    private final UserRepository userRepository;

    @Override
    public List<Room> getAllRooms() {
        User currentUser = getCurrentUser();
        boolean isSuperAdmin = currentUser.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equals("SUPER_ADMIN"));

        if (isSuperAdmin) {
            return roomRepository.findAll();
        }

        // Admin thường: Chỉ lấy phòng thuộc rạp mình được gán quản lý
        Long managedId = currentUser.getManagedCinemaItemId();
        if (managedId == null) {
            throw new RuntimeException("Tài khoản Admin này chưa được gán chi nhánh rạp cụ thể!");
        }
        return roomRepository.findByCinemaItem_Id(managedId);
    }

    @Override
    public List<Room> getRoomsByCinemaItem(Long cinemaItemId) {
        return roomRepository.findByCinemaItem_Id(cinemaItemId);
    }

    @Override
    public Room getRoomById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng với ID: " + id));
    }

    @Override
    @Transactional
    public Room createRoom(RoomRequest request) {
        // Kiểm tra quyền: Admin rạp này không được tạo phòng cho rạp khác
        validateAdminPermission(request.getCinemaItemId());

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
        // Kiểm tra quyền quản lý rạp trước khi cho sửa phòng
        validateAdminPermission(request.getCinemaItemId());

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
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng để xóa"));
        
        // Kiểm tra quyền: Phải là Admin của rạp chứa phòng này mới được xóa
        validateAdminPermission(room.getCinemaItem().getId());
        
        roomRepository.deleteById(id);
    }

    // --- HELPER METHODS ---

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Phiên đăng nhập hết hạn"));
    }

    private void validateAdminPermission(Long targetCinemaId) {
        User user = getCurrentUser();
        boolean isSuperAdmin = user.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equals("SUPER_ADMIN"));
        
        // Nếu không phải Super Admin, ID rạp thao tác phải khớp với managedCinemaItemId
        if (!isSuperAdmin) {
            if (user.getManagedCinemaItemId() == null || !user.getManagedCinemaItemId().equals(targetCinemaId)) {
                throw new RuntimeException("Bạn không có quyền thao tác trên chi nhánh rạp này!");
            }
        }
    }
}