package com.example.cinema.service.impl;

import com.example.cinema.dto.CinemaRequest;
import com.example.cinema.entity.Cinema;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.CinemaRepository;
import com.example.cinema.service.CinemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CinemaServiceImpl implements CinemaService {
    private final CinemaRepository cinemaRepository;

    @Override
    public List<Cinema> getAllCinemas() {
        return cinemaRepository.findAll();
    }

    // Triển khai hàm lấy chi tiết cụm rạp
    @Override
    public Cinema getCinemaById(Long id) {
        return cinemaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cụm rạp không tồn tại với id: " + id));
    }

    @Override
    @Transactional
    public Cinema createCinema(CinemaRequest request) {
        Cinema cinema = new Cinema();
        cinema.setName(request.getName());
        return cinemaRepository.save(cinema);
    }

    @Override
    @Transactional
    public Cinema updateCinema(Long id, CinemaRequest request) {
        Cinema cinema = cinemaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cụm rạp không tồn tại để cập nhật"));
        cinema.setName(request.getName());
        return cinemaRepository.save(cinema);
    }

    @Override
    @Transactional
    public void deleteCinema(Long id) {
        // Kiểm tra tồn tại trước khi xóa để tránh lỗi 500 ngầm
        if (!cinemaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không thể xóa vì cụm rạp không tồn tại");
        }
        cinemaRepository.deleteById(id);
    }
}