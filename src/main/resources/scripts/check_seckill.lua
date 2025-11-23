-- 参数：voucherId, userId, orderId
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

-- key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 判断库存
if tonumber(redis.call('get', stockKey)) <= 0 then
    return 1    -- 库存不足
end

-- 一人一单判断
if redis.call('sismember', orderKey, userId) == 1 then
    return 2    -- 重复下单
end

-- 扣库存
redis.call('incrby', stockKey, -1)

-- 记录用户
redis.call('sadd', orderKey, userId)

return 0        -- 成功
