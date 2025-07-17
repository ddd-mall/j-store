package com.jstore.order.expired;


import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class TimerJobPODAO {
    private final TimerJobPOMapper timerJobJAPRepository;


    public TimerJobPODAO(TimerJobPOMapper timerJobJAPRepository) {
        this.timerJobJAPRepository = timerJobJAPRepository;
    }

    public void markAsHandled(TimerJob timerJob) {
        TimerJobPO timerJobPO = new TimerJobPO(timerJob, TimerJob.TimerJobStatus.HANDLED.name());
        timerJobJAPRepository.save(timerJobPO);

    }

    public boolean saveOrUpdate(TimerJobPO timerJob) {
        timerJobJAPRepository.save(timerJob);
        return true;
    }

    public Iterator<List<TimerJob>> getIteratorOfUnhandledAndBefore(Date date, int batchSize) {
        return new Iterator<>() {
            boolean hasNext = true;

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            @Transactional(rollbackOn =  Exception.class)
            public List<TimerJob> next() {
                Page<TimerJobPO> result = timerJobJAPRepository.findAllByExecuteTimeBeforeAndStatus(
                        date,
                        TimerJob.TimerJobStatus.UNHANDLED.name(),
                        Pageable.ofSize(batchSize)
                );
                if (!result.getContent().isEmpty()) {
                    List<Long> ids = result.getContent().stream().map(TimerJobPO::getId).collect(Collectors.toList());
                    timerJobJAPRepository.updateStatusToHandlingByIds(ids, TimerJob.TimerJobStatus.HANDLING.name());
                }
                hasNext = !(result.getContent().size() < batchSize);
                return result.get().map(TimerJob::new).collect(Collectors.toList());
            }
        };
    }


}