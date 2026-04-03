package com.example.cinema.repository;

import com.example.cinema.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    // Lấy menu cha (gốc)
    List<Menu> findByParentIsNullOrderBySortOrderAsc();
    
    // Lấy menu con của một menu cụ thể
    List<Menu> findByParentId(Long parentId);
    
    // Tìm theo trạng thái (Active/Inactive)
    List<Menu> findByStatus(String status);
}