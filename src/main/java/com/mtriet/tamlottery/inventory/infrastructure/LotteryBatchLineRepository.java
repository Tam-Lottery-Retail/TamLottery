package com.mtriet.tamlottery.inventory.infrastructure;

import com.mtriet.tamlottery.inventory.domain.LotteryBatchLine;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LotteryBatchLineRepository extends JpaRepository<LotteryBatchLine, Long> {

    Optional<LotteryBatchLine> findByIdAndBatchStoreId(Long id, Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LotteryBatchLine l join fetch l.batch b where l.id in :ids and b.store.id = :storeId order by l.id")
    List<LotteryBatchLine> findAllForUpdate(@Param("ids") Collection<Long> ids, @Param("storeId") Long storeId);
}

