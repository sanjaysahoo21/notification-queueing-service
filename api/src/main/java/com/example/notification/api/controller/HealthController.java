package com.example.notification.api.controller;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    public HealthController(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate, RabbitTemplate rabbitTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    @GetMapping("/health")
    public Map<String, String> health() {

        // db check
        jdbcTemplate.execute("SELECT 1");

        // redis check
        redisTemplate.hasKey("health-check");

        // rabbitmq check
        rabbitTemplate.execute(channel -> true);

        return Map.of("status", "UP");

    }

}
