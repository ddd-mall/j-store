-- from
local zsetb_key = KEYS[1]
-- to
local zseta_key = KEYS[2]
-- expire_time
local expire_time = tonumber(ARGV[1])


local result = redis.call('ZRANGEBYSCORE', zsetb_key, '-inf', expire_time, 'WITHSCORES', 'LIMIT', 0, 1)
if #result == 0 then
    return false
end


local member = result[1]
local original_score = tonumber(result[2])

-- 计算新ttl：取1000的余数并减去1
local ttl = (original_score % 1000) - 1

if ttl < 0 then
    ttl = 0  -- 确保新score不为负数
end

-- 将member添加到zseta中
redis.call('ZADD', zseta_key, ttl, member)

-- 从zsetb中删除该member
redis.call('ZREM', zsetb_key, member)

return true