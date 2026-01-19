package com.example.notification.api.ratelimiter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RatelimiterService {

    private final StringRedisTemplate redisTemplate;

    @Value( "${rate.limit.max-requests}")
    private int maxRequests;

    @Value("${rate.limit.window-seconds}")
    private int windowSeconds;

    public RatelimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String clientKey) {
        String redisKey = "rate_limit:" + clientKey;

        // Use increment. If key is missing, it creates it with value 1.
        Long requestCount = redisTemplate.opsForValue().increment(redisKey);

        if(requestCount != null && requestCount == 1) {
            // First request: Set expiration window
            redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
        }

        return requestCount != null && requestCount <= maxRequests;

    }
}
