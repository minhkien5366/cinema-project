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
                .orElseThrow(() -> new ResourceNotFoundException("Cụm rạp không tồn tại"));
        cinema.setName(request.getName());
        return cinemaRepository.save(cinema);
    }

    @Override
    @Transactional
    public void deleteCinema(Long id) {
        cinemaRepository.deleteById(id);
    }
}