-- Redis Lua脚本：Rollback - 将member从zsetb移动回zseta/并更新ttl
-- KEYS[1]: zsetb的key
-- KEYS[2]: zseta的key
-- ARGV[1]: 要移动的member
--
-- 逻辑说明：
-- 1. 从zsetb中取出指定的member
-- 2. 计算新ttl
-- 3. 将member添加到zseta中，使用新ttl作为score
-- 4. 从zsetb中删除该member
-- from
local zsetb_key = KEYS[1]
-- to
local zseta_key = KEYS[2]
local target_member = ARGV[1]

-- 检查member是否存在于zsetb中
local score = redis.call('ZSCORE', zsetb_key, target_member)

-- 如果member不存在，返回nil
if score == nil then
    return false
end

-- 将score转换为数字
score = tonumber(score)

-- 计算ttl (zsetb 中 的score10进制中的最后3位表示ttl，高位表示存放进来的时候的秒级时间戳)
local ttl = (score % 1000) - 1
if ttl < 0 then
    ttl = 0
end

-- 将member添加到zseta中
redis.call('ZADD', zseta_key, ttl, target_member)

-- 从zsetb中删除该member
redis.call('ZREM', zsetb_key, target_member)

return true