package com.jstore.order.expired;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface TimerJobJpaRepository extends JpaRepository<TimerJobJpaPO, Long> {


    Page<TimerJobJpaPO> findAllByExecuteTimeBeforeAndStatus(Date executeTimeBefore, String status, Pageable pageable);

    @Modifying
    @Query("UPDATE TimerJobJpaPO t SET t.status = :status WHERE t.id IN (:ids)")
    void updateStatusToHandlingByIds(List<Long> ids, String status);
}