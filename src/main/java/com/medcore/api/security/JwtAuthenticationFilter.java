package com.medcore.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <jwt>}. Invalid or revoked tokens leave the
 * request anonymous, and Spring Security's entry point answers 401 for protected endpoints.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String MDC_USER_KEY = "userId";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenStore tokenStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parse(header.substring(BEARER_PREFIX.length()))
                    .filter(principal -> !tokenStore.isAccessTokenRevoked(principal.tokenId()))
                    .ifPresent(principal -> authenticate(request, principal));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER_KEY);
        }
    }

    private void authenticate(HttpServletRequest request, UserPrincipal principal) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MDC.put(MDC_USER_KEY, principal.id().toString());
    }
}
