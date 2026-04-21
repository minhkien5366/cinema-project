package com.example.cinema.service.impl;

import com.example.cinema.dto.ShowtimeRequest;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.ShowtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowtimeServiceImpl implements ShowtimeService {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final CinemaItemRepository cinemaItemRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @Override
    public List<Showtime> getAll() {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) return showtimeRepository.findAll();
        return showtimeRepository.findByCinemaItem_Id(user.getManagedCinemaItemId());
    }

    @Override
    public Showtime getById(Long id) {
        return showtimeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));
    }

    @Override
    public List<Showtime> getByMovie(Long movieId) { return showtimeRepository.findByMovieId(movieId); }

    @Override
    public List<Showtime> getByCinemaItem(Long cinemaItemId) { return showtimeRepository.findByCinemaItem_Id(cinemaItemId); }

    @Override
    @Transactional
    public Showtime createShowtime(ShowtimeRequest request) {
        validateBranchAccess(request.getCinemaItemId());

        // RÀNG BUỘC 1: Không tạo suất chiếu trong quá khứ
        if (request.getStartTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Thời gian bắt đầu không thể ở quá khứ!");
        }

        Movie movie = movieRepository.findById(request.getMovieId()).orElseThrow(() -> new ResourceNotFoundException("Phim không tồn tại"));
        Room room = roomRepository.findById(request.getRoomId()).orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

        // RÀNG BUỘC 2: Tự động tính thời gian kết thúc dựa trên thời lượng phim
        LocalDateTime endTime = request.getStartTime().plusMinutes(movie.getDuration());

        // RÀNG BUỘC 3: Kiểm tra trùng lịch và 20 phút dọn phòng
        checkShowtimeOverlapWithBuffer(room.getId(), request.getStartTime(), endTime, null);

        Showtime showtime = new Showtime();
        showtime.setStartTime(request.getStartTime());
        showtime.setEndTime(endTime);
        showtime.setMovie(movie);
        showtime.setCinemaItem(cinemaItemRepository.findById(request.getCinemaItemId()).get());
        showtime.setRoom(room);

        return showtimeRepository.save(showtime);
    }

    @Override
    @Transactional
    public Showtime updateShowtime(Long id, ShowtimeRequest request) {
        Showtime showtime = showtimeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));
        validateBranchAccess(showtime.getCinemaItem().getId());

        Movie movie = movieRepository.findById(request.getMovieId()).orElseThrow(() -> new ResourceNotFoundException("Phim không tồn tại"));
        Room room = roomRepository.findById(request.getRoomId()).orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

        LocalDateTime endTime = request.getStartTime().plusMinutes(movie.getDuration());
        checkShowtimeOverlapWithBuffer(room.getId(), request.getStartTime(), endTime, id);

        showtime.setStartTime(request.getStartTime());
        showtime.setEndTime(endTime);
        showtime.setMovie(movie);
        showtime.setRoom(room);
        
        return showtimeRepository.save(showtime);
    }

    @Override
    public List<Showtime> getByMovieAndDate(Long movieId, String dateStr) {
        return showtimeRepository.findByMovieIdAndDate(movieId, LocalDate.parse(dateStr));
    }

    @Override
    @Transactional
    public void deleteShowtime(Long id) {
        Showtime showtime = showtimeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu"));
        validateBranchAccess(showtime.getCinemaItem().getId());
        showtimeRepository.deleteById(id);
    }

    // LOGIC KIỂM TRA TRÙNG LỊCH + 20 PHÚT DỌN PHÒNG
    private void checkShowtimeOverlapWithBuffer(Long roomId, LocalDateTime newStart, LocalDateTime newEnd, Long excludeId) {
        List<Showtime> existing = showtimeRepository.findByRoomId(roomId);
        int buffer = 20; // 20 phút dọn dẹp

        for (Showtime s : existing) {
            if (excludeId != null && s.getId().equals(excludeId)) continue;

            // Suất chiếu mới phải kết thúc TRƯỚC khi suất cũ bắt đầu 20p 
            // HOẶC suất chiếu mới phải bắt đầu SAU khi suất cũ kết thúc 20p
            LocalDateTime sStartBuffer = s.getStartTime().minusMinutes(buffer);
            LocalDateTime sEndBuffer = s.getEndTime().plusMinutes(buffer);

            if (newStart.isBefore(sEndBuffer) && newEnd.isAfter(sStartBuffer)) {
                throw new RuntimeException("Trùng lịch hoặc quá sát suất chiếu khác (Cần 20p dọn phòng)! Đang có suất chiếu từ " 
                        + s.getStartTime().toLocalTime() + " đến " + s.getEndTime().toLocalTime());
            }
        }
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> r.getRoleName().equalsIgnoreCase("SUPER_ADMIN") || r.getRoleName().equalsIgnoreCase("ROLE_SUPER_ADMIN"));
    }

    private void validateBranchAccess(Long cinemaItemId) {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) return;
        if (user.getManagedCinemaItemId() == null || !user.getManagedCinemaItemId().equals(cinemaItemId)) {
            throw new RuntimeException("Bạn không có quyền quản lý tại chi nhánh rạp này!");
        }
    }
}