package com.mtriet.tamlottery.masterdata.infrastructure;

import com.mtriet.tamlottery.masterdata.domain.LotteryDraw;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface LotteryDrawRepository extends JpaRepository<LotteryDraw, Long> {
    Optional<LotteryDraw> findByIdAndStoreId(Long id, Long storeId);
    Page<LotteryDraw> findAllByStoreId(Long storeId, Pageable pageable);
    boolean existsByStoreIdAndProvinceCodeIgnoreCaseAndDrawDate(Long storeId, String provinceCode, LocalDate drawDate);
}

