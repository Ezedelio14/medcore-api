package com.medcore.api.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Logs one line per API call with method, path, status and latency. */
@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String START_ATTRIBUTE = RequestLoggingInterceptor.class.getName() + ".start";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_ATTRIBUTE, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object start = request.getAttribute(START_ATTRIBUTE);
        long elapsedMs = start instanceof Long s ? (System.nanoTime() - s) / 1_000_000 : -1;
        log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
    }
}
