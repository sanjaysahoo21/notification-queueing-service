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

        Long requestCount = redisTemplate.opsForValue().decrement(redisKey);

        if(requestCount == null) {
            return false;
        }

        if(requestCount == 1) {
            redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
        }

        return requestCount <= maxRequests;

    }
}
