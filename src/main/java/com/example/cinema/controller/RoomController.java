package com.example.cinema.controller;

import com.example.cinema.dto.ApiResponse;
import com.example.cinema.dto.RoomRequest;
import com.example.cinema.entity.Room;
import com.example.cinema.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    // Lấy tất cả phòng chiếu (Dùng cho Admin)
    @GetMapping
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Room>>> getAllRooms() {
        return ResponseEntity.ok(ApiResponse.<List<Room>>builder()
                .status(200)
                .message("Lấy danh sách phòng thành công")
                .data(roomService.getAllRooms())
                .build());
    }

    // Lấy danh sách phòng theo chi nhánh rạp
    @GetMapping("/cinema-item/{cinemaItemId}")
    public ResponseEntity<ApiResponse<List<Room>>> getRoomsByCinemaItem(@PathVariable Long cinemaItemId) {
        return ResponseEntity.ok(ApiResponse.<List<Room>>builder()
                .status(200)
                .message("Lấy danh sách phòng của chi nhánh thành công")
                .data(roomService.getRoomsByCinemaItem(cinemaItemId))
                .build());
    }

    @PostMapping
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Room>> createRoom(@RequestBody RoomRequest request) {
        return ResponseEntity.ok(ApiResponse.<Room>builder()
                .status(201)
                .message("Tạo phòng thành công")
                .data(roomService.createRoom(request))
                .build());
    }

    @PutMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Room>> updateRoom(@PathVariable Long id, @RequestBody RoomRequest request) {
        return ResponseEntity.ok(ApiResponse.<Room>builder()
                .status(200)
                .message("Cập nhật phòng thành công")
                .data(roomService.updateRoom(id, request))
                .build());
    }

    @DeleteMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .status(200)
                .message("Xóa phòng thành công")
                .build());
    }
}