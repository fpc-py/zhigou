local current = redis.call('get', KEYS[1])
if not current then return -1 end
local count = tonumber(current)
local need = tonumber(ARGV[1])
if count >= need then
    return redis.call('decrby', KEYS[1], need)
else
    return -1
end