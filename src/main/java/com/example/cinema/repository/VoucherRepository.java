package com.example.cinema.repository;

import com.example.cinema.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    /**
     * Tìm Voucher bằng mã code (Ví dụ: KM20K)
     * Dùng khi khách hàng nhập mã ở bước thanh toán
     */
    Optional<Voucher> findByCode(String code);

    /**
     * Kiểm tra xem mã code đã tồn tại chưa
     * Dùng để tránh tạo trùng mã khi Admin thêm Voucher mới
     */
    boolean existsByCode(String code);

    /**
     * Lấy danh sách Voucher khả dụng cho một rạp cụ thể
     * Bao gồm: Voucher dùng chung (cinemaItem IS NULL) + Voucher riêng của rạp đó
     */
    @Query("SELECT v FROM Voucher v WHERE v.cinemaItem IS NULL OR v.cinemaItem.id = :cinemaItemId")
    List<Voucher> findAvailableVouchers(@Param("cinemaItemId") Long cinemaItemId);
    
    /**
     * Lấy danh sách Voucher do chính Admin của rạp đó tạo ra
     * Dùng cho trang quản trị của Admin chi nhánh
     */
    List<Voucher> findByCinemaItem_Id(Long cinemaItemId);

    /**
     * Lấy danh sách các mã Voucher dùng chung toàn quốc
     * Dùng cho trang chủ hoặc trang ưu đãi tổng của hệ thống
     */
    List<Voucher> findByCinemaItemIsNull();
}