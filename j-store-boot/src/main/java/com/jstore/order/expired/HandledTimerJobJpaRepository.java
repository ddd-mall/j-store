package com.jstore.order.expired;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HandledTimerJobJpaRepository extends JpaRepository<HandledTimerJobJpaPO, Long> {}
