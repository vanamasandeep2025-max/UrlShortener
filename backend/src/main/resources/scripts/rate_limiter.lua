-- Atomic fixed-window rate limiter.
-- KEYS[1] = bucket key
-- ARGV[1] = capacity (max requests allowed in the window)
-- ARGV[2] = window length in seconds
-- Returns { allowed (1/0), retry_after_seconds (-1 if allowed) }
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end

local capacity = tonumber(ARGV[1])
if current > capacity then
    local ttl = redis.call('TTL', KEYS[1])
    if ttl < 0 then
        ttl = tonumber(ARGV[2])
    end
    return { 0, ttl }
end

return { 1, -1 }
