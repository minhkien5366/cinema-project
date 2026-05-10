package com.example.cinema.service.impl;

import com.example.cinema.dto.SeatPriceConfigRequest;
import com.example.cinema.entity.SeatPriceConfig;
import com.example.cinema.entity.CinemaItem;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.SeatPriceConfigRepository;
import com.example.cinema.repository.CinemaItemRepository;
import com.example.cinema.service.SeatPriceConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatPriceConfigServiceImpl implements SeatPriceConfigService {

    private final SeatPriceConfigRepository repository;
    private final CinemaItemRepository cinemaItemRepository;

    @Override
    public List<SeatPriceConfig> getAllConfigs() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public SeatPriceConfig createConfig(SeatPriceConfigRequest request) {
        // Kiểm tra xem cấu hình cho loại ghế/thứ này tại rạp này đã có chưa
        repository.findBySeatTypeAndDayOfWeekAndCinemaItem_Id(
                request.getSeatType().toUpperCase(), 
                request.getDayOfWeek(), 
                request.getCinemaItemId()
        ).ifPresent(c -> {
            throw new RuntimeException("Cấu hình giá cho loại ghế này vào ngày này đã tồn tại!");
        });

        CinemaItem cinemaItem = cinemaItemRepository.findById(request.getCinemaItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Rạp không tồn tại"));

        SeatPriceConfig config = SeatPriceConfig.builder()
                .seatType(request.getSeatType().toUpperCase())
                .dayOfWeek(request.getDayOfWeek())
                .price(request.getPrice())
                .cinemaItem(cinemaItem)
                .build();

        return repository.save(config);
    }

    @Override
    @Transactional
    public SeatPriceConfig updateConfig(Long id, SeatPriceConfigRequest request) {
        SeatPriceConfig config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình giá"));

        config.setPrice(request.getPrice());
        config.setSeatType(request.getSeatType().toUpperCase());
        config.setDayOfWeek(request.getDayOfWeek());
        
        return repository.save(config);
    }

    @Override
    @Transactional
    public void deleteConfig(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy cấu hình để xoá");
        }
        repository.deleteById(id);
    }
}