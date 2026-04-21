package com.example.cinema.repository;

import com.example.cinema.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    Optional<Voucher> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * DÀNH CHO CLIENT: Lấy mã còn hạn, còn lượt dùng
     */
    @Query("SELECT v FROM Voucher v WHERE v.promotion.id = :promotionId " +
           "AND v.startDate <= :now AND v.endDate >= :now " +
           "AND v.usedCount < v.usageLimit")
    List<Voucher> findActiveVouchersByPromotionId(@Param("promotionId") Long promotionId, 
                                                 @Param("now") LocalDateTime now);

    /**
     * DÀNH CHO ADMIN/TEST: Lấy tất cả mã của 1 sự kiện (không quan tâm ngày giờ)
     * Dùng cái này để debug khi Swagger báo mảng rỗng []
     */
    List<Voucher> findByPromotionId(Long promotionId, LocalDateTime now);

    /**
     * Lấy voucher khả dụng cho rạp hoặc dùng chung toàn hệ thống
     */
    @Query("SELECT v FROM Voucher v WHERE (v.cinemaItem.id = :cinemaItemId OR v.cinemaItem IS NULL) " +
           "AND v.startDate <= :now AND v.endDate >= :now " +
           "AND v.usedCount < v.usageLimit")
    List<Voucher> findAvailableVouchers(@Param("cinemaItemId") Long cinemaItemId, 
                                        @Param("now") LocalDateTime now);

    List<Voucher> findByCinemaItem_Id(Long cinemaItemId);

    List<Voucher> findByCinemaItemIsNull();
}