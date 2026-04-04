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
    public List<CinemaItem> getAllItems() { 
        return itemRepository.findAll(); 
    }

    @Override
    public List<CinemaItem> getByCity(String city) { 
        return itemRepository.findByCity(city); 
    }

    @Override
    public List<CinemaItem> getByCinema(Long cinemaId) { 
        return itemRepository.findByCinemaId(cinemaId); 
    }

    // Triển khai hàm lấy chi tiết theo ID
    @Override
    public CinemaItem getItemById(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh rạp không tồn tại với id: " + id));
    }

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
                .orElseThrow(() -> new ResourceNotFoundException("Cụm rạp không tồn tại"));
        
        mapRequestToEntity(request, item, cinema);
        return itemRepository.save(item);
    }

    @Override
    @Transactional
    public void deleteItem(Long id) { 
        if (!itemRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy chi nhánh để xóa");
        }
        itemRepository.deleteById(id); 
    }

    private void mapRequestToEntity(CinemaItemRequest request, CinemaItem item, Cinema cinema) {
        item.setName(request.getName());
        item.setAddress(request.getAddress());
        item.setCity(request.getCity());
        item.setHoursPerRoom(request.getHoursPerRoom());
        item.setCinema(cinema);
    }
}