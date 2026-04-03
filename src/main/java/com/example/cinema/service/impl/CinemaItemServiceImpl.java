package com.example.cinema.service.impl;

import com.example.cinema.dto.CinemaItemRequest;
import com.example.cinema.entity.Cinema;
import com.example.cinema.entity.CinemaItem;
import com.example.cinema.exception.ResourceNotFoundException;
import com.example.cinema.repository.CinemaItemRepository;
import com.example.cinema.repository.CinemaRepository;
import com.example.cinema.service.CinemaItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CinemaItemServiceImpl implements CinemaItemService {
    private final CinemaItemRepository itemRepository;
    private final CinemaRepository cinemaRepository;

    @Override
    public List<CinemaItem> getAllItems() { return itemRepository.findAll(); }

    @Override
    public List<CinemaItem> getByCity(String city) { return itemRepository.findByCity(city); }

    @Override
    public List<CinemaItem> getByCinema(Long cinemaId) { return itemRepository.findByCinemaId(cinemaId); }

    @Override
    @Transactional
    public CinemaItem createItem(CinemaItemRequest request) {
        Cinema cinema = cinemaRepository.findById(request.getCinemaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cụm rạp không tồn tại"));
        
        CinemaItem item = new CinemaItem();
        mapRequestToEntity(request, item, cinema);
        return itemRepository.save(item);
    }

    @Override
    @Transactional
    public CinemaItem updateItem(Long id, CinemaItemRequest request) {
        CinemaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh không tồn tại"));
        Cinema cinema = cinemaRepository.findById(request.getCinemaId())
                .orElseThrow(() -> new ResourceNotFoundException("C cụm rạp không tồn tại"));
        
        mapRequestToEntity(request, item, cinema);
        return itemRepository.save(item);
    }

    @Override
    @Transactional
    public void deleteItem(Long id) { itemRepository.deleteById(id); }

    private void mapRequestToEntity(CinemaItemRequest request, CinemaItem item, Cinema cinema) {
        item.setName(request.getName());
        item.setAddress(request.getAddress());
        item.setCity(request.getCity());
        item.setHoursPerRoom(request.getHoursPerRoom());
        item.setCinema(cinema);
    }
}