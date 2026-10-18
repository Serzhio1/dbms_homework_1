local key = 'logins:' .. ARGV[1]
local cnt = redis.call('INCR', key)
redis.call('EXPIRE', key, 86400)
return cnt
