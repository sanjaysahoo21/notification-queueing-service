package com.example.notification.api.ratelimiter;

import com.example.notification.api.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RatelimiterService ratelimiterService;

    public RateLimitInterceptor(RatelimiterService ratelimiterService) {
        this.ratelimiterService = ratelimiterService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,Object handler) {

        String clientIp = request.getRemoteAddr();

        boolean allowed = ratelimiterService.isAllowed(clientIp);

        if(!allowed) {
            throw new RateLimitExceededException("Too many request");
        }

        return true;

    }

}
