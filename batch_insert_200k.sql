-- 批量插入20万条记录到 develop.timer_job 表
-- 方案1：使用PostgreSQL的generate_series函数（最高效）

INSERT INTO develop.timer_job(execute_time, content, status, topic)
SELECT
    current_timestamp,
    'this is a test B - record ' || generate_series,
    'UNHANDLED',
    'TEST'
FROM generate_series(1, 200000);

alter table if exists develop.timer_job add column version integer not null default 1;

CREATE INDEX idx_execute_time_topic ON handled_timer_job (execute_time, topic);

ALTER TABLE handled_timer_job
    ADD CONSTRAINT uk_job_id UNIQUE (timer_job_id);

ALTER TABLE timer_job_dead_queue
    ADD CONSTRAINT uk_job_id UNIQUE (timer_job_id);

-- ===================


select * from handled_timer_job order by develop.handled_timer_job.timer_job_id desc;
select count(*) as total from handled_timer_job order by total desc;

select status, count(*) as total from timer_job group by timer_job.status order by total desc;
select * from timer_job where id = 1400021;
select * from timer_job;

select * from timer_job_dead_queue;

truncate timer_job;
truncate timer_job_dead_queue;
truncate handled_timer_job;

-- 执行完成后可以验证插入的记录数
-- SELECT COUNT(*) FROM develop.timer_job WHERE content LIKE 'this is a test B - record %';
