package com.medcore.api.security;

import com.medcore.api.common.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** Writes 401/403 responses raised inside the security filter chain as RFC 9457 problem details. */
@Component
@RequiredArgsConstructor
public class ProblemDetailsAuthenticationHandlers {

    private final JsonMapper jsonMapper;

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) ->
                write(response, GlobalExceptionHandler.problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) ->
                write(response, GlobalExceptionHandler.problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                        "You do not have permission to perform this action"));
    }

    private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
