-- 批量插入20万条记录到 develop.timer_job 表
-- 方案1：使用PostgreSQL的generate_series函数（最高效）

INSERT INTO develop.timer_job(execute_time, content, status, topic)
SELECT
    current_timestamp,
    'this is a test B - record ' || generate_series,
    'UNHANDLED',
    'TEST'
FROM generate_series(1, 200000);

-- 执行完成后可以验证插入的记录数
-- SELECT COUNT(*) FROM develop.timer_job WHERE content LIKE 'this is a test B - record %';
