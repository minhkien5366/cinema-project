package com.example.cinema.service.impl;

import com.example.cinema.dto.ShowtimeRequest;
import com.example.cinema.entity.*;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.*;
import com.example.cinema.service.ShowtimeService;
import lombok.RequiredArgsConstructor;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Service
@RequiredArgsConstructor
public class ShowtimeServiceImpl implements ShowtimeService {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final CinemaItemRepository cinemaItemRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private String readStringCell(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING)
            return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC)
            return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }

    private LocalDateTime readDateCell(Cell cell,DateTimeFormatter formatter) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        return LocalDateTime.parse(
                cell.getStringCellValue().trim(),
                formatter
        );
    }
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
public void importExcel(MultipartFile file) {
    User user = getCurrentUser();
    
    // Kiểm tra quyền quản lý chi nhánh rạp của User trước khi xử lý file
    Long managedCinemaId = user.getManagedCinemaItemId();
    if (managedCinemaId == null) {
        throw new RuntimeException("Tài khoản của bạn không được phân quyền quản lý chi nhánh nào!");
    }

    try (InputStream is = file.getInputStream();
         Workbook workbook = new XSSFWorkbook(is)) {
        
        Sheet sheet = workbook.getSheetAt(0);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            try {
                String movieName = readStringCell(row.getCell(0));
                String roomName = readStringCell(row.getCell(1));
                LocalDateTime startTime = readDateCell(row.getCell(2), formatter);
                
                if (movieName == null || roomName == null || startTime == null) {
                    throw new RuntimeException("Thiếu dữ liệu bắt buộc (Tên phim, Tên phòng hoặc Giờ khởi chiếu)");
                }
                
                if (startTime.isBefore(LocalDateTime.now())) {
                    throw new RuntimeException("Thời gian bắt đầu suất chiếu không được ở quá khứ");
                }
                
                // 1. Tìm bộ phim đầu tiên khớp tên
                Movie movie = movieRepository.findFirstByTitle(movieName)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy phim: " + movieName));
                
                // 2. Tìm phòng thuộc chi nhánh quản lý của Admin
                Room room = roomRepository.findByNameAndCinemaItem_Id(roomName, managedCinemaId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng '" + roomName + "' thuộc chi nhánh của bạn"));
                
                CinemaItem cinemaItem = room.getCinemaItem();
                
                // Kiểm tra quyền hạn an toàn hệ thống
                validateBranchAccess(cinemaItem.getId());
                
                // 3. TỰ ĐỘNG TÍNH TOÁN GIỜ KẾ THÚC (Ép buộc hệ thống tự tính 100%)
                LocalDateTime endTime = startTime.plusMinutes(movie.getDuration());
                
                // Kiểm tra trùng lịch và khoảng nghỉ (Buffer)
                checkShowtimeOverlapWithBuffer(
                        room.getId(),
                        startTime,
                        endTime,
                        null
                );
                
                // Tạo mới và lưu Suất chiếu
                Showtime showtime = Showtime.builder()
                        .movie(movie)
                        .room(room)
                        .cinemaItem(cinemaItem)
                        .startTime(startTime)
                        .endTime(endTime)
                        .build();
                        
                showtimeRepository.save(showtime);
                
            } catch (Exception rowError) {
                // Giữ nguyên cơ chế bọc lỗi từng dòng để rollback toàn bộ nếu có lỗi dữ liệu
                throw new RuntimeException(
                        "Lỗi dòng " + (i + 1) + ": " + rowError.getMessage()
                );
            }
        }
    } catch (Exception e) {
        throw new RuntimeException("Import thất bại: " + e.getMessage());
    }
}
   
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