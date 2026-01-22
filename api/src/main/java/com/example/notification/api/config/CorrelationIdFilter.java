package com.example.notification.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter {

    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
        throws IOException, ServletException {

        String correlationId = UUID.randomUUID().toString();

        MDC.put("correlationId", correlationId);
        response.setHeader("X-Correlation-ID", correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }

    }

}
