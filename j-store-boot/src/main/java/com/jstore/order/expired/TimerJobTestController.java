package com.jstore.order.expired;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.security.InvalidParameterException;
import java.util.Date;

@RestController
@RequestMapping("/timer/job")
public class TimerJobTestController {

    private final TimerJobRepository timerJobRepository;
    private final TimerJobCoordinator coordinator;
    private final JobLoader jobLoader;

    public TimerJobTestController(TimerJobRepository timerJobRepository, TimerJobCoordinator coordinator, JobLoader jobLoader) {
        this.timerJobRepository = timerJobRepository;
        this.coordinator = coordinator;
        this.jobLoader = jobLoader;
    }

    @GetMapping("/start")
    public boolean start() {
        coordinator.start();
        return true;
    }

    @GetMapping("/stop")
    public boolean stop() {
        coordinator.stop();
        return true;
    }

    @PostMapping("/add/new")
    public TimerJob addNew(@RequestBody JobCreateParam jobCreateParam) {
        TimerJob timerJob = jobCreateParam.toTimerJob();
        long slot = jobLoader.slot(timerJob);
        return timerJobRepository.addNewJobAndEnqueue(timerJob, slot);
    }

    @Data
    public static class JobCreateParam {
        private String topic;
        private String content;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date executeTime;

        public TimerJob toTimerJob() {
            if (null == topic || topic.isEmpty()) {
                throw new InvalidParameterException("topic不能为空");
            }
            if (null == this.executeTime) {
                throw new InvalidParameterException("执行时间不能为空");
            }

            return new TimerJob()
                    .setTopic(this.topic)
                    .setExecuteTime(this.executeTime)
                    .setContent(this.content);

        }
    }

}
