package com.example.notification.api.ratelimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

/**
 * Sliding window rate limiter using an atomic Redis Lua script.
 * INCR+EXPIRE are replaced with a single Lua execution to prevent
 * race conditions and keys left without expiry on crash.
 */
@Service
public class RatelimiterService {

    private static final Logger log = LoggerFactory.getLogger(RatelimiterService.class);

    // Lua script: atomically maintains a sliding window using a sorted set.
    // Members are unique request IDs; scores are millisecond timestamps.
    private static final String SLIDING_WINDOW_SCRIPT =
        "local key        = KEYS[1]\n" +
        "local now        = tonumber(ARGV[1])\n" +
        "local windowMs   = tonumber(ARGV[2])\n" +
        "local limit      = tonumber(ARGV[3])\n" +
        "local ttl        = tonumber(ARGV[4])\n" +
        "local requestId  = ARGV[5]\n" +
        "local clearBefore = now - windowMs\n" +
        "redis.call('ZREMRANGEBYSCORE', key, '-inf', clearBefore)\n" +
        "local count = redis.call('ZCARD', key)\n" +
        "if count < limit then\n" +
        "  redis.call('ZADD', key, now, requestId)\n" +
        "  redis.call('EXPIRE', key, ttl)\n" +
        "  return 1\n" +
        "else\n" +
        "  return 0\n" +
        "end";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> slidingWindowScript;

    @Value("${rate.limit.max-requests}")
    private int maxRequests;

    @Value("${rate.limit.window-seconds}")
    private int windowSeconds;

    public RatelimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setScriptText(SLIDING_WINDOW_SCRIPT);
        this.slidingWindowScript.setResultType(Long.class);
    }

    public boolean isAllowed(String clientKey) {
        String redisKey = "rate_limit:" + clientKey;
        long nowMs = System.currentTimeMillis();
        long windowMs = (long) windowSeconds * 1000;
        String requestId = UUID.randomUUID().toString();

        Long result = redisTemplate.execute(
                slidingWindowScript,
                Collections.singletonList(redisKey),
                String.valueOf(nowMs),
                String.valueOf(windowMs),
                String.valueOf(maxRequests),
                String.valueOf(windowSeconds),
                requestId
        );

        boolean allowed = Long.valueOf(1L).equals(result);
        if (!allowed) {
            log.warn("Rate limit exceeded for client: {}", clientKey);
        }
        return allowed;
    }
}
