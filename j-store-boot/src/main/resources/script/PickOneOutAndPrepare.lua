
local zseta_key = KEYS[1]
local zsetb_key = KEYS[2]
local current_time_ms = tonumber(ARGV[1])

-- 从zseta中取出一条score小于等于当前时间的member
local result = redis.call('ZRANGEBYSCORE', zseta_key, '-inf', current_time_ms, 'WITHSCORES', 'LIMIT', 0, 1)

-- 如果没有找到符合条件的数据，返回nil
if #result == 0 then
    return {}
end


local member = result[1]
local original_ttl = tonumber(result[2])
local ttl = 0

-- 根据原始score的大小判断类型并计算新score

if original_ttl and original_ttl < 16 then
    ttl = original_ttl
else
    ttl = 16
end

-- 获取当前时间的秒级时间戳，然后转为毫秒
local current_time_sec_as_ms = math.floor(current_time_ms / 1000) * 1000
local new_score = current_time_sec_as_ms + ttl

-- 将member添加到zsetb中
redis.call('ZADD', zsetb_key, new_score, member)

-- 从zseta中删除该member
redis.call('ZREM', zseta_key, member)


-- 返回该member的数据
return {member, tonumber(ttl)}