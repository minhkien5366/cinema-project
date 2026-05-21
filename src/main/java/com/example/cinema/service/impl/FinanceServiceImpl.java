package com.example.cinema.service.impl;

import com.example.cinema.repository.OrderRepository;
import com.example.cinema.service.FinanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class FinanceServiceImpl implements FinanceService {

    @Autowired
    private OrderRepository orderRepository;

    private static final double VAT_RATE = 0.10;

    @Override
    public Map<String, Object> getMonthlyFinance(String month) {

        LocalDate startDate = LocalDate.parse(month + "-01");
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = start.plusMonths(1);

        Double gross = orderRepository.sumRevenue(start, end);
        gross = gross == null ? 0 : gross;

        double tax = gross * VAT_RATE;
        double profit = gross - tax;

        Long orderCount = orderRepository
                .countByCreatedAtBetweenAndStatus(start, end, "PAID");

        Map<String, Object> res = new HashMap<>();
        res.put("grossRevenue", gross);
        res.put("tax", tax);
        res.put("profit", profit);
        res.put("orderCount", orderCount);

        return res;
    }
}