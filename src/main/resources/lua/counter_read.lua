local function read32be(s, off)
    local b1, b2, b3, b4 = string.byte(s, off + 1, off + 4)
    return ((b1 * 256 + b2) * 256 + b3) * 256 + b4
end

local raw = redis.call('GET', KEYS[1])
if not raw then return {-1, -1} end
if string.len(raw) ~= 20 then return {-2, -2} end

local like = read32be(raw, 4)
local fav = read32be(raw, 8)
local likeDelta = tonumber(redis.call('HGET', KEYS[2], '1') or '0')
local favDelta = tonumber(redis.call('HGET', KEYS[2], '2') or '0')

like = like + likeDelta
fav = fav + favDelta
if like < 0 then like = 0 end
if fav < 0 then fav = 0 end
return {like, fav}
