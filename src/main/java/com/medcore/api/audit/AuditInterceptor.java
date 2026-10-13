package com.medcore.api.audit;

import com.medcore.api.common.web.CorrelationIdFilter;
import com.medcore.api.security.CurrentUser;
import com.medcore.api.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Records an audit trail entry for every state-changing API call (POST/PUT/PATCH/DELETE):
 * who, which endpoint, the target entity id (from the {@code id} path variable) and the outcome.
 * Read access to medical data is audited too, as required for health records.
 */
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditService auditService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(handler instanceof HandlerMethod method) || !shouldAudit(request)) {
            return;
        }
        UUID userId = CurrentUser.find().map(UserPrincipal::id).orElse(null);
        String action = method.getBeanType().getSimpleName().replace("Controller", "") + "." + method.getMethod().getName();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("httpMethod", request.getMethod());
        metadata.put("path", request.getRequestURI());
        metadata.put("status", response.getStatus());
        metadata.put("ip", request.getRemoteAddr());
        metadata.put("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));

        auditService.record(userId, action, entityType(request), entityId(request), metadata);
    }

    private static boolean shouldAudit(HttpServletRequest request) {
        String path = request.getRequestURI();
        return MUTATING_METHODS.contains(request.getMethod()) || path.contains("/medical-records");
    }

    private static String entityType(HttpServletRequest request) {
        String[] segments = request.getRequestURI().split("/");
        // /api/v1/{resource}/...
        return segments.length > 3 ? segments[3] : null;
    }

    @SuppressWarnings("unchecked")
    private static UUID entityId(HttpServletRequest request) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof Map<?, ?> map && map.get("id") instanceof String id) {
            try {
                return UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
