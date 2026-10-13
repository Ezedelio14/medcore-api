package com.medcore.api.config;

import com.medcore.api.audit.AuditInterceptor;
import com.medcore.api.common.web.RequestLoggingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestLoggingInterceptor requestLoggingInterceptor;
    private final AuditInterceptor auditInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLoggingInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(auditInterceptor).addPathPatterns("/api/**").excludePathPatterns("/api/v1/auth/**");
    }
}
