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
        if (isSuperAdmin(user)) {
            return showtimeRepository.findAll();
        }
        // Admin thường: Chỉ thấy suất chiếu của chi nhánh mình quản lý
        return showtimeRepository.findByCinemaItem_Id(user.getManagedCinemaItemId());
    }

    @Override
    public Showtime getById(Long id) {
        return showtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));
    }

    @Override
    public List<Showtime> getByMovie(Long movieId) { 
        return showtimeRepository.findByMovieId(movieId); 
    }

    @Override
    public List<Showtime> getByCinemaItem(Long cinemaItemId) { 
        return showtimeRepository.findByCinemaItem_Id(cinemaItemId); 
    }

    @Override
    @Transactional
    public Showtime createShowtime(ShowtimeRequest request) {
        validateBranchAccess(request.getCinemaItemId());

        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Phim không tồn tại"));
        CinemaItem cinemaItem = cinemaItemRepository.findById(request.getCinemaItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh rạp không tồn tại"));
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

        // Tính thời gian kết thúc = Bắt đầu + Thời lượng phim + 15 phút dọn phòng
        LocalDateTime endTime = request.getStartTime().plusMinutes(movie.getDuration() + 15);

        // Kiểm tra trùng lịch tại phòng này
        checkShowtimeOverlap(room.getId(), request.getStartTime(), endTime, null);

        Showtime showtime = new Showtime();
        showtime.setStartTime(request.getStartTime());
        showtime.setEndTime(endTime);
        showtime.setMovie(movie);
        showtime.setCinemaItem(cinemaItem);
        showtime.setRoom(room);

        return showtimeRepository.save(showtime);
    }

    @Override
    @Transactional
    public Showtime updateShowtime(Long id, ShowtimeRequest request) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Suất chiếu không tồn tại"));
        
        validateBranchAccess(showtime.getCinemaItem().getId()); // Check quyền rạp cũ
        validateBranchAccess(request.getCinemaItemId());       // Check quyền rạp mới (nếu đổi rạp)

        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Phim không tồn tại"));
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));
        CinemaItem cinemaItem = cinemaItemRepository.findById(request.getCinemaItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh không tồn tại"));

        LocalDateTime endTime = request.getStartTime().plusMinutes(movie.getDuration() + 15);
        checkShowtimeOverlap(room.getId(), request.getStartTime(), endTime, id);

        showtime.setStartTime(request.getStartTime());
        showtime.setEndTime(endTime);
        showtime.setMovie(movie);
        showtime.setRoom(room);
        showtime.setCinemaItem(cinemaItem);
        
        return showtimeRepository.save(showtime);
    }

    @Override
    public List<Showtime> getByMovieAndDate(Long movieId, String dateStr) {
        LocalDate date = LocalDate.parse(dateStr); 
        return showtimeRepository.findByMovieIdAndDate(movieId, date);
    }

    @Override
    @Transactional
    public void deleteShowtime(Long id) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu"));
        validateBranchAccess(showtime.getCinemaItem().getId());
        showtimeRepository.deleteById(id);
    }

    // --- HELPER METHODS BẢO MẬT ---
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> r.getRoleName().equals("SUPER_ADMIN"));
    }

    private void validateBranchAccess(Long cinemaItemId) {
        User user = getCurrentUser();
        if (isSuperAdmin(user)) return;
        if (user.getManagedCinemaItemId() == null || !user.getManagedCinemaItemId().equals(cinemaItemId)) {
            throw new RuntimeException("Bạn không có quyền quản lý suất chiếu tại chi nhánh rạp này!");
        }
    }

    private void checkShowtimeOverlap(Long roomId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        List<Showtime> existing = showtimeRepository.findByRoomId(roomId); 
        for (Showtime s : existing) {
            if (excludeId != null && s.getId().equals(excludeId)) continue;
            if (start.isBefore(s.getEndTime()) && end.isAfter(s.getStartTime())) {
                throw new RuntimeException("Lịch bị trùng! Phòng đang có phim chiếu từ " 
                        + s.getStartTime().toLocalTime() + " đến " + s.getEndTime().toLocalTime());
            }
        }
    }
}